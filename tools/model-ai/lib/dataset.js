// Synthetic dataset: body-plan samplers -> validated specs -> captions (text2spec) + mutations (edit).
// Output (in --out):
//   train.jsonl / val.jsonl / test.jsonl   records (task, prompt, input_spec, target_spec, target_patch)
//   sft/<split>.jsonl                      chat messages for fine-tuning ({id, messages})
//   real.jsonl + sft/real.jsonl            hand-written reference models from eval/real/ (held out)
//   images/<id>.png                        4-view sheets for the first --images specs (photo path, phase 5)
//   stats.json, build.log

import fs from 'node:fs';
import path from 'node:path';
import { makeCaption } from './captions.js';
import { specToDefinition, tryGenerate } from './gen.js';
import { mutate } from './mutate.js';
import { applyPatch, diffSpecs } from './patch.js';
import { editMessages, PROMPT_VERSION, text2specMessages } from './prompts.js';
import { encodePNG, renderSheet } from './render.js';
import { createRng, hashString } from './rng.js';
import { ARCHETYPES, sampleArchetype } from './archetypes/index.js';
import { canonicalSpec, sameValue, serializeSpec } from './spec.js';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), '..');

function splitOf(key) {
	const h = hashString(key) % 100;
	return h < 90 ? 'train' : h < 95 ? 'val' : 'test';
}

function quantiles(values) {
	const v = values.slice().sort((a, b) => a - b);
	const q = (p) => v[Math.min(v.length - 1, Math.floor(p * v.length))] ?? 0;
	return { p50: q(0.5), p95: q(0.95), max: v.at(-1) ?? 0 };
}

/** Hand-written references: eval/real/*.json = {name, captions: {en: [...], ja: [...]}, spec} */
export function loadRealSet(dir = path.join(ROOT, 'eval', 'real')) {
	if (!fs.existsSync(dir)) return [];
	const out = [];
	for (const f of fs.readdirSync(dir).filter((x) => x.endsWith('.json')).sort()) {
		const item = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
		const id = path.basename(f, '.json');
		const r = tryGenerate(specToDefinition(item.spec, { id }));
		if (!r.ok) throw new Error(`eval/real/${f}: ${r.problems.join('; ')}`);
		for (const lang of ['en', 'ja'])
			(item.captions?.[lang] || []).forEach((prompt, k) => out.push({ id: `real_${id}_${lang}${k}`, task: 'text2spec', lang, archetype: `real:${item.archetype || id}`, prompt, target_spec: canonicalSpec(item.spec) }));
	}
	return out;
}

export async function buildDataset(args = {}) {
	const n = Number(args.n ?? 2000);
	const seed = String(args.seed ?? 1);
	const editsPer = Number(args.edits ?? 2);
	const images = Number(args.images ?? 0);
	const outDir = path.resolve(args.out || path.join(ROOT, 'data', `v${PROMPT_VERSION}`));
	const dry = !!args['dry-run'];
	const weights = Object.entries(ARCHETYPES).map(([id, a]) => [id, a.weight]);
	const only = args.archetypes ? args.archetypes.split(',') : null;

	const splits = { train: [], val: [], test: [] };
	const stats = { prompt_version: PROMPT_VERSION, seed, requested: n, specs: 0, duplicates: 0, rejected: {}, records: {}, by_archetype: {}, by_op: {}, target_chars: {} };
	const seen = new Set();
	const lens = { text2spec: [], edit: [] };
	const log = [];
	const reject = (why) => (stats.rejected[why] = (stats.rejected[why] || 0) + 1);
	if (!dry) fs.mkdirSync(path.join(outDir, 'sft'), { recursive: true });
	if (!dry && images) fs.mkdirSync(path.join(outDir, 'images'), { recursive: true });

	for (let i = 0; i < n; i++) {
		const rng = createRng(`${seed}:${i}`);
		const arch = only ? rng.pick(only) : rng.weighted(weights);
		let sample;
		try {
			sample = sampleArchetype(arch, rng);
		} catch (err) {
			reject(`sampler:${arch}`);
			log.push(`sampler ${arch} #${i}: ${err.message}`);
			continue;
		}
		const spec = canonicalSpec(sample.spec);
		const key = JSON.stringify(spec);
		if (seen.has(key)) {
			stats.duplicates++;
			continue;
		}
		seen.add(key);
		const gen = tryGenerate(specToDefinition(spec, { id: arch }));
		if (!gen.ok) {
			reject(`invalid:${arch}`);
			log.push(`invalid ${arch} #${i}: ${gen.problems.join('; ')}`);
			continue;
		}
		stats.specs++;
		stats.by_archetype[arch] = (stats.by_archetype[arch] || 0) + 1;
		const split = splitOf(key);
		const base = `s${i}`;
		let image;
		if (images && stats.specs <= images) {
			image = `images/${base}.png`;
			if (!dry) fs.writeFileSync(path.join(outDir, image), encodePNG(renderSheet(gen.model, ['front', 'right', 'top', 'iso'], 128)));
		}
		for (const lang of ['en', 'ja']) {
			const prompt = makeCaption(sample.facts, spec, gen.model, rng, lang);
			splits[split].push({ id: `${base}_${lang}`, task: 'text2spec', lang, archetype: arch, prompt, target_spec: spec, image });
			lens.text2spec.push(serializeSpec(spec).length);
		}
		for (let j = 0; j < editsPer; j++) {
			const m = mutate(spec, rng);
			if (!m) {
				reject('no-edit');
				continue;
			}
			const target = canonicalSpec(m.spec);
			const g2 = tryGenerate(specToDefinition(target, { id: arch }));
			if (!g2.ok) {
				reject(`edit-invalid:${m.op}`);
				log.push(`edit ${m.op} on ${arch} #${i}: ${g2.problems.join('; ')}`);
				continue;
			}
			const patch = diffSpecs(spec, target);
			if (!patch.length) {
				reject('edit-noop');
				continue;
			}
			if (!sameValue(canonicalSpec(applyPatch(spec, patch)), target)) {
				reject('patch-roundtrip');
				log.push(`patch roundtrip ${m.op} on ${arch} #${i}`);
				continue;
			}
			const lang = rng.chance(0.5) ? 'en' : 'ja';
			splits[split].push({ id: `${base}_e${j}`, task: 'edit', lang, archetype: arch, op: m.op, prompt: m[lang], input_spec: spec, target_spec: target, target_patch: patch });
			stats.by_op[m.op.split('+').length > 1 ? 'combined' : m.op] = (stats.by_op[m.op.split('+').length > 1 ? 'combined' : m.op] || 0) + 1;
			lens.edit.push(serializeSpec(spec).length + JSON.stringify(patch).length);
		}
		if (i && i % 1000 === 0) process.stderr.write(`  ${i}/${n} specs\n`);
	}

	const real = loadRealSet();
	const toSft = (r) => ({ id: r.id, task: r.task, messages: r.task === 'edit' ? editMessages(r.input_spec, r.prompt, r.target_patch) : text2specMessages(r.prompt, r.target_spec) });
	const all = { ...splits, real };
	for (const [name, rows] of Object.entries(all)) {
		stats.records[name] = rows.length;
		if (dry) continue;
		fs.writeFileSync(path.join(outDir, `${name}.jsonl`), rows.map((r) => JSON.stringify(r)).join('\n') + (rows.length ? '\n' : ''));
		fs.writeFileSync(path.join(outDir, 'sft', `${name}.jsonl`), rows.map((r) => JSON.stringify(toSft(r))).join('\n') + (rows.length ? '\n' : ''));
	}
	stats.target_chars = { text2spec: quantiles(lens.text2spec), edit_input_plus_patch: quantiles(lens.edit) };
	if (!dry) {
		fs.writeFileSync(path.join(outDir, 'stats.json'), JSON.stringify(stats, null, 2) + '\n');
		fs.writeFileSync(path.join(outDir, 'build.log'), log.join('\n') + '\n');
	}
	return { out: dry ? null : outDir, dry_run: dry, ...stats, log_lines: log.length };
}
