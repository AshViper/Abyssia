import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { ARCHETYPES, sampleArchetype } from '../lib/archetypes/index.js';
import { buildDataset, loadRealSet } from '../lib/dataset.js';
import { specToDefinition, tryGenerate } from '../lib/gen.js';
import { scorePair, scoreRecord } from '../lib/metrics.js';
import { mutate } from '../lib/mutate.js';
import { applyPatch, diffSpecs, PatchError } from '../lib/patch.js';
import { renderView } from '../lib/render.js';
import { createRng } from '../lib/rng.js';
import { canonicalSpec, parseJsonLoose, sameValue, serializeSpec } from '../lib/spec.js';

const SPEC = {
	orientation: 'upright',
	parts: [
		{ name: 'bell', shape: 'dome', size: [12, 6, 12], color: '#e0e0ff' },
		{ name: 'tentacle', shape: 'cylinder', parent: 'bell', at: 'bottom', axis: '-y', size: [1, 10, 1], count: 8, arrange: 'ring', radius: 5, segments: 3, color: '#c0c0ff' },
		{ name: 'lure', shape: 'cylinder', parent: 'bell', at: 'top', size: [1, 4, 1] },
		{ name: 'lure_bulb', shape: 'ellipsoid', parent: 'lure', at: 'tip', size: [2, 2, 2], glow: 1 },
	],
};

test('every archetype produces valid specs', () => {
	for (const id of Object.keys(ARCHETYPES))
		for (let s = 0; s < 15; s++) {
			const { spec } = sampleArchetype(id, createRng(`t:${id}:${s}`));
			const r = tryGenerate(specToDefinition(spec, { id }));
			assert.ok(r.ok, `${id} #${s}: ${r.problems.join('; ')}`);
		}
});

test('serializeSpec round-trips through parseJsonLoose', () => {
	const text = serializeSpec(SPEC);
	assert.ok(sameValue(parseJsonLoose(text), canonicalSpec(SPEC)));
	assert.ok(sameValue(parseJsonLoose('```json\n' + text + '\n``` trailing words'), canonicalSpec(SPEC)));
	assert.throws(() => parseJsonLoose('{"parts": ['));
});

test('part-addressed patch: replace, add, remove with descendants', () => {
	const out = applyPatch(SPEC, [
		{ op: 'replace', path: '/parts/tentacle/count', value: 12 },
		{ op: 'add', path: '/parts/bell/glow', value: 1 },
		{ op: 'remove', path: '/parts/lure' },
		{ op: 'add', path: '/parts/-', value: { name: 'eye', shape: 'eye', parent: 'bell', at: [1, 0.5, 0.5], size: 1, mirror: true } },
	]);
	assert.equal(out.parts.find((p) => p.name === 'tentacle').count, 12);
	assert.equal(out.parts.find((p) => p.name === 'bell').glow, 1);
	assert.ok(!out.parts.some((p) => p.name === 'lure' || p.name === 'lure_bulb'));
	assert.ok(out.parts.some((p) => p.name === 'eye'));
	assert.equal(SPEC.parts.length, 4, 'input not mutated');
	assert.throws(() => applyPatch(SPEC, [{ op: 'replace', path: '/parts/nope/size', value: [1, 1, 1] }]), PatchError);
	assert.throws(() => applyPatch(SPEC, [{ op: 'replace', path: '/parts/bell/glow', value: 1 }]), /use add/);
});

test('diffSpecs + applyPatch reproduce every mutation', () => {
	for (let s = 0; s < 200; s++) {
		const rng = createRng(`m:${s}`);
		const ids = Object.keys(ARCHETYPES);
		const spec = canonicalSpec(sampleArchetype(ids[s % ids.length], rng).spec);
		const m = mutate(spec, rng);
		if (!m) continue;
		const patch = diffSpecs(spec, m.spec);
		assert.ok(sameValue(canonicalSpec(applyPatch(spec, patch)), canonicalSpec(m.spec)), `${m.op} #${s}`);
	}
});

test('renderer draws a silhouette', () => {
	const { model } = tryGenerate(specToDefinition(SPEC));
	for (const view of ['front', 'right', 'top', 'iso']) assert.ok(renderView(model, { view, size: 64, mode: 'mask' }).countOpaque() > 100, view);
});

test('metrics: identical = 1, different < 1, invalid = 0', () => {
	assert.equal(scorePair(SPEC, SPEC).score, 1);
	const other = { orientation: 'upright', parts: [{ name: 'body', shape: 'box', size: [8, 8, 8], color: '#808080' }] };
	const s = scorePair(other, SPEC).score;
	assert.ok(s > 0 && s < 0.8, `box vs jelly ${s}`);
	const bad = { orientation: 'upright', parts: [{ name: 'x', shape: 'blob', size: [1, 1, 1] }] };
	assert.equal(scorePair(bad, SPEC).score, 0);
	const rec = { id: 'e', task: 'edit', input_spec: SPEC, target_spec: applyPatch(SPEC, [{ op: 'replace', path: '/parts/tentacle/count', value: 12 }]), target_patch: [{ op: 'replace', path: '/parts/tentacle/count', value: 12 }] };
	const exact = scoreRecord(rec, JSON.stringify(rec.target_patch));
	assert.ok(exact.exact && exact.touched_ok && exact.gain === 1);
	assert.equal(scoreRecord(rec, '[]').gain, 0);
	assert.equal(scoreRecord(rec, 'no json').parsed, false);
});

test('real reference set loads', () => {
	assert.ok(loadRealSet().length >= 40);
});

test('dataset builder writes splits and sft files', async () => {
	const out = fs.mkdtempSync(path.join(os.tmpdir(), 'model-ai-'));
	const r = await buildDataset({ n: 40, seed: 't', out, images: 2 });
	assert.equal(r.specs + r.duplicates + Object.values(r.rejected).reduce((a, b) => a + b, 0) >= 40, true);
	for (const f of ['train.jsonl', 'val.jsonl', 'test.jsonl', 'real.jsonl', 'sft/train.jsonl', 'stats.json', 'images/s0.png']) assert.ok(fs.existsSync(path.join(out, f)), f);
	const first = JSON.parse(fs.readFileSync(path.join(out, 'sft', 'train.jsonl'), 'utf8').split('\n')[0]);
	assert.deepEqual(first.messages.map((m) => m.role), ['system', 'user', 'assistant']);
	fs.rmSync(out, { recursive: true, force: true });
});
