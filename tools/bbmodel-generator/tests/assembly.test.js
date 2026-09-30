// "parts" template: validation messages, repetition, chains, eyes, planes, both orientations.
import assert from 'node:assert/strict';
import test from 'node:test';
import { exportFiles, generateModel } from '../backend/index.js';
import { checkDefinition } from '../backend/creatures/schema.js';
import { validateParts } from '../backend/templates/assembly.js';

const errorsOf = (report) => report.items.filter((i) => i.level === 'error').map((i) => `${i.check}: ${i.message}`);

const jelly = {
	id: 'test_jelly',
	name: 'Test Jelly',
	template: 'parts',
	params: {
		orientation: 'upright',
		parts: [
			{ name: 'bell', shape: 'dome', size: [12, 7, 12], color: '#c8a0d8', glow: 0.4 },
			{ name: 'tentacle', parent: 'bell', at: 'bottom', shape: 'cylinder', size: [1, 12, 1], count: 8, arrange: 'ring', radius: 5, tilt: 10, segments: 3, curl: 8 },
			{ name: 'oral_arm', parent: 'bell', at: 'bottom', shape: 'plane', size: [3, 10, 0], outline: 'frill', count: 4, arrange: 'ring', radius: 1 },
		],
	},
};

const fish = {
	id: 'test_fish',
	name: 'Test Fish',
	template: 'parts',
	params: {
		orientation: 'horizontal',
		parts: [
			{ name: 'body', shape: 'ellipsoid', axis: 'z', center: true, size: [6, 8, 16], segments: 2, color: '#404858' },
			{ name: 'tail', parent: 'body', at: 'back', shape: 'plane', axis: 'z', size: [0, 8, 6], outline: 'fork', color: '#303848' },
			{ name: 'pectoral_fin', parent: 'body', at: [1, 0.4, 0.3], shape: 'plane', axis: 'x', size: [4, 0, 3], outline: 'fan', mirror: true },
			{ name: 'eye', parent: 'body', at: [1, 0.65, 0.15], shape: 'eye', size: 2, mirror: true, color: '#d0b040' },
		],
	},
};

test('validation explains mistakes', () => {
	const problems = validateParts([{ name: 'Bad Name', size: [1, 2] }, { name: 'x', parent: 'nope', shape: 'blob', size: [1, 1, 1] }]);
	assert.ok(problems.some((p) => /lower_snake_case/.test(p)));
	assert.ok(problems.some((p) => /size must be/.test(p)));
	assert.ok(problems.some((p) => /unknown parent "nope"/.test(p)));
	assert.ok(problems.some((p) => /unknown shape "blob"/.test(p)));
	assert.ok(checkDefinition({ id: 'a', name: 'A', template: 'parts', params: { parts: [] } }).some((p) => p.level === 'error'));
	assert.throws(() => generateModel({ id: 'a', name: 'A', template: 'parts', params: { parts: [{ name: 'a', size: [1, 1] }] } }), /Invalid parts/);
});

test('ring of tentacle chains + frill planes (upright jelly)', () => {
	const model = generateModel(jelly, {});
	assert.deepEqual(errorsOf(exportFiles(model).report), []);
	const tentacles = model.geometry.bones.filter((b) => b.role === 'tentacle');
	assert.equal(tentacles.length, 8 * 3 + 4, '8 tentacles × 3 segments + 4 oral arms');
	assert.ok(model.geometry.cubes.some((c) => c.shape?.type === 'frill'));
	assert.ok(model.animations.some((a) => a.name === 'tentacle_move'));
	assert.ok(model.stats.glowPixels > 0);
});

test('mirrored fins / eyes, horizontal swim (fish)', () => {
	for (const settings of [{}, { detail: 'low' }, { pixelScale: 8 }, { pixelScale: 32 }, { blockCount: 6 }, { variation: 1, seed: 5 }]) {
		const model = generateModel(fish, settings);
		assert.deepEqual(errorsOf(exportFiles(model).report), [], JSON.stringify(settings));
	}
	const model = generateModel(fish, {});
	const names = model.geometry.bones.map((b) => b.name);
	for (const n of ['eye_right', 'eye_left', 'pectoral_fin_right', 'pectoral_fin_left']) assert.ok(names.includes(n), n);
	const eyes = model.geometry.cubes.filter((c) => c.material === 'eye');
	assert.equal(eyes.length, 2);
	assert.ok(Math.sign(eyes[0].from[0] + eyes[0].to[0]) === -Math.sign(eyes[1].from[0] + eyes[1].to[0]), 'eyes on opposite sides');
	assert.deepEqual(new Set(eyes.map((c) => c.meta.eyeFace)), new Set(['east', 'west']));
	const fin = (n) => model.geometry.cubes.find((c) => c.bone === n);
	const [r, l] = [fin('pectoral_fin_right'), fin('pectoral_fin_left')];
	assert.deepEqual([l.from[0], l.to[0]], [-r.to[0], -r.from[0]], 'left fin is the mirror image of the right fin');
	assert.ok(model.animations.some((a) => a.name === 'swim'));
	assert.ok(!model.animations.some((a) => a.name === 'sway'));
});
