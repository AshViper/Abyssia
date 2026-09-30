// End-to-end generation for every creature definition and preset.
import assert from 'node:assert/strict';
import test from 'node:test';
import { buildBBModel, exportFiles, generateModel, stringifyBBModel, validateBBModel } from '../backend/index.js';
import { loadDefinitions, loadPreset, loadPresets } from '../backend/creatures/node_store.js';
import { boneWorldTransforms, cubeWorldTransform, applyAffine } from '../backend/generator/math3d.js';
import { flattenForStatic } from '../backend/generator/budget.js';
import { samplePose } from '../backend/animation/clip.js';

const definitions = loadDefinitions().filter((d) => !d._error);
const deepSea = loadPreset('minecraft_deep_sea');
const settingsOf = (preset, extra = {}) => ({ ...(preset.settings || {}), preset: preset.id, ...extra });
const errorsOf = (report) => report.items.filter((i) => i.level === 'error').map((i) => `${i.check}: ${i.message}`);

test('definitions are present', () => {
	assert.ok(definitions.length >= 1);
	assert.ok(definitions.some((d) => d.id === 'anglerfish'));
});

for (const def of definitions) {
	test(`${def.id}: generates a valid Blockbench model with every preset`, () => {
		for (const preset of loadPresets()) {
			const model = generateModel(def, settingsOf(preset), preset);
			const { report, bbmodel, text } = exportFiles(model);
			assert.deepEqual(errorsOf(report), [], `${def.id} / ${preset.id}`);
			assert.ok(model.geometry.cubes.length > 0);
			assert.ok(bbmodel.elements.length === model.geometry.cubes.length);
			assert.doesNotThrow(() => JSON.parse(text));
		}
	});

	test(`${def.id}: every settings variant validates`, () => {
		const variants = [
			{ detail: 'low' },
			{ detail: 'medium' },
			{ pixelScale: 8 },
			{ pixelScale: 32 },
			{ uvMode: 'per_face', modelFormat: 'geckolib_model' },
			{ uvMode: 'per_face', modelFormat: 'bedrock' },
			{ formatVersion: '4.10' },
			{ modelFormat: 'free' },
			{ blockCount: 12 },
			{ animationReady: false },
			{ generateTexture: false },
			{ glowLayer: false },
			{ mirrorUV: true },
			{ textureSize: 16 },
			{ size: { width: 0, height: 0, length: 40 } },
			{ seed: 12345, variation: 1 },
		];
		for (const v of variants) {
			const model = generateModel(def, settingsOf(deepSea, v), deepSea);
			const { report } = exportFiles(model);
			const errs = errorsOf(report).filter((e) => !(v.mirrorUV && e.startsWith('uv_overlap')));
			assert.deepEqual(errs, [], `${def.id} ${JSON.stringify(v)}`);
		}
	});

	test(`${def.id}: same seed gives identical output, other seeds vary plausibly`, () => {
		const a = stringifyBBModel(buildBBModel(generateModel(def, settingsOf(deepSea, { seed: 42, variation: 1 }), deepSea)));
		const b = stringifyBBModel(buildBBModel(generateModel(def, settingsOf(deepSea, { seed: 42, variation: 1 }), deepSea)));
		assert.equal(a, b);
		const base = generateModel(def, settingsOf(deepSea), deepSea).bounds.size;
		for (const seed of [1, 2, 3, 4, 5]) {
			const size = generateModel(def, settingsOf(deepSea, { seed, variation: 1 }), deepSea).bounds.size;
			size.forEach((v, i) => {
				const ratio = v / base[i];
				assert.ok(ratio > 0.6 && ratio < 1.5, `${def.id} seed ${seed} axis ${i} ratio ${ratio.toFixed(2)}`);
			});
		}
	});

	test(`${def.id}: loop animations are seamless and target existing bones`, () => {
		const model = generateModel(def, settingsOf(deepSea), deepSea);
		const bones = new Set(model.geometry.bones.map((b) => b.name));
		assert.ok(model.animations.length >= 2, 'has animations');
		for (const clip of model.animations) {
			for (const [bone, channels] of Object.entries(clip.tracks)) {
				assert.ok(bones.has(bone), `${clip.name} -> ${bone}`);
				if (clip.loop !== 'loop') continue;
				const start = samplePose(clip, 0)[bone];
				const end = samplePose(clip, clip.length - 1e-4)[bone];
				for (const ch of Object.keys(channels)) start[ch].forEach((v, i) => assert.ok(Math.abs(v - end[ch][i]) < 0.35, `${clip.name} ${bone}.${ch} seam`));
			}
		}
	});

	test(`${def.id}: Blockbench 4.10 layout converts back to the 5.0 values`, () => {
		const model = generateModel(def, settingsOf(deepSea), deepSea);
		const v5 = buildBBModel(model);
		const v4 = buildBBModel({ ...model, settings: { ...model.settings, formatVersion: '4.10' } });
		assert.equal(v4.meta.format_version, '4.10');
		assert.equal(v4.groups, undefined);
		assert.ok(v4.outliner[0].name, 'nested group objects');
		// Blockbench processCompatibility (< 5.0): invert x of position / rotation and y of rotation
		v4.animations.forEach((anim, ai) => {
			for (const [uuid, animator] of Object.entries(anim.animators)) {
				animator.keyframes.forEach((kf, ki) => {
					const dp = { ...kf.data_points[0] };
					if (kf.channel === 'position' || kf.channel === 'rotation') dp.x = String(-Number(dp.x));
					if (kf.channel === 'rotation') dp.y = String(-Number(dp.y));
					const ref = v5.animations[ai].animators[uuid].keyframes[ki].data_points[0];
					for (const axis of ['x', 'y', 'z']) assert.ok(Math.abs(Number(dp[axis]) - Number(ref[axis])) < 1e-9, `${anim.name} ${animator.name} ${axis}`);
				});
			}
		});
		assert.deepEqual(validateBBModel(v4).items.filter((i) => i.level === 'error'), []);
	});
}

test('static (non animation-ready) model keeps every cube in place', () => {
	const def = definitions.find((d) => d.id === 'anglerfish');
	const model = generateModel(def, settingsOf(deepSea), deepSea);
	const flat = flattenForStatic(model.geometry);
	const w1 = boneWorldTransforms(model.geometry), w2 = boneWorldTransforms(flat);
	const bones1 = new Map(model.geometry.bones.map((b) => [b.name, b])), bones2 = new Map(flat.bones.map((b) => [b.name, b]));
	model.geometry.cubes.forEach((c1, i) => {
		const c2 = flat.cubes[i];
		const W1 = cubeWorldTransform(c1, bones1.get(c1.bone), w1.get(c1.bone));
		const W2 = cubeWorldTransform(c2, bones2.get(c2.bone), w2.get(c2.bone));
		for (const corner of [[0, 0, 0], [1, 1, 1], [1, 0, 1]]) {
			const p1 = applyAffine(W1, corner.map((k, a) => (k ? c1.to[a] : c1.from[a])));
			const p2 = applyAffine(W2, corner.map((k, a) => (k ? c2.to[a] : c2.from[a])));
			p1.forEach((v, a) => assert.ok(Math.abs(v - p2[a]) < 0.01, `${c1.name} corner moved`));
		}
	});
});

test('anglerfish: required structure (bones, glow layer, mouth animation)', () => {
	const def = definitions.find((d) => d.id === 'anglerfish');
	const model = generateModel(def, settingsOf(deepSea), deepSea);
	const names = model.geometry.bones.map((b) => b.name);
	for (const bone of ['anglerfish', 'body', 'head', 'mouth', 'lower_teeth', 'upper_teeth', 'left_eye', 'right_eye', 'illicium', 'esca', 'left_fin', 'right_fin', 'tail', 'tail_fin']) {
		assert.ok(names.includes(bone), `missing bone ${bone}`);
	}
	assert.ok(model.textures.glow, 'glow texture');
	assert.ok(model.textures.glowPixels > 0);
	const clips = model.animations.map((a) => a.name);
	for (const clip of ['idle', 'swim', 'mouth_open', 'mouth_close', 'glow', 'hurt', 'death']) assert.ok(clips.includes(clip), `missing ${clip}`);
	// Jaw opens downward: negative X rotation in Blockbench 5 convention
	const open = model.animations.find((a) => a.name === 'mouth_open');
	const last = open.tracks.mouth.rotation.at(-1).value[0];
	assert.ok(last < -20, `jaw open rotation ${last}`);
	const bb = buildBBModel(model);
	assert.equal(bb.textures.length, 2);
	assert.equal(bb.textures[1].render_mode, 'layered');
	assert.equal(bb.meta.model_format, 'modded_entity');
});

test('modelling rule: teeth, hair (setae) and fins are single planes (one zero-size axis)', () => {
	for (const def of definitions) {
		const model = generateModel(def, settingsOf(deepSea), deepSea);
		for (const c of model.geometry.cubes) {
			if (!['teeth', 'setae', 'fin'].includes(c.material)) continue;
			const zero = [0, 1, 2].filter((i) => Math.abs(c.to[i] - c.from[i]) < 1e-6).length;
			assert.equal(zero, 1, `${def.id}: ${c.name} (${c.material}) must be a plane`);
		}
	}
});
