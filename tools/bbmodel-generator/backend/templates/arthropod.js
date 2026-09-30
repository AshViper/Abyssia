// ArthropodTemplate: flattened segmented body (a bone chain that can curl), head with
// compound eyes and antennae, one pair of jointed legs per segment, tail shield and uropods.
// Covers the giant isopod (Bathynomus) and similar segmented crawlers.

import { ModelBuilder } from '../generator/builder.js';
import { clamp } from '../generator/util.js';
import { CreatureTemplate, groundLift } from './base.js';
import { addChain, addJointedLeg } from './parts.js';
import { roundedBox } from './shared.js';

export class ArthropodTemplate extends CreatureTemplate {
	static id = 'arthropod';
	static label = 'Arthropod';
	static description = 'Segmented armoured body chain (can curl), head with compound eyes and antennae, jointed legs per segment, tail shield with uropods.';

	defaults() {
		return {
			template: 'arthropod',
			category: 'isopod',
			body: { length: 18, width: 10, height: 4, segments: 7 },
			parts: [],
			params: {
				head: { length: 3, width: 7, height: 3 },
				eye: { size: 2, forward: 0.2 },
				antennae: { length: 12, segments: 3, spread: 40, antennules: true },
				legs: { pairs: 7, upper: 3, lower: 3, splay: 60, fan: 30, width: 1 },
				tail: { pleon: 2, length: 4, width: 0.7, uropods: 3 },
			},
			palette: {},
			texture: { pattern: 'speckle', pattern_strength: 0.4 },
			glow: [],
			animations: ['idle', 'walk', 'curl', 'uncurl', 'hurt', 'death'],
			animation: { speed: 1, amplitude: 1 },
			variation: { body: 0.08, head: 0.08, legs: 0.1, antennae: 0.12, tail: 0.1, eye: 0.1, hue: 6, lightness: 0.03 },
		};
	}

	editorSchema() {
		return [
			{
				section: 'Body Size',
				fields: [
					{ path: 'body.length', label: 'Length', type: 'int', min: 4, max: 64 },
					{ path: 'body.width', label: 'Width', type: 'int', min: 2, max: 32 },
					{ path: 'body.height', label: 'Height', type: 'int', min: 1, max: 16 },
					{ path: 'body.segments', label: 'Segments', type: 'int', min: 2, max: 12 },
				],
			},
			{
				section: 'Head Size',
				fields: [
					{ path: 'params.head.length', label: 'Length', type: 'int', min: 1, max: 12 },
					{ path: 'params.head.width', label: 'Width', type: 'int', min: 2, max: 24 },
					{ path: 'params.head.height', label: 'Height', type: 'int', min: 1, max: 12 },
					{ path: 'params.antennae.length', label: 'Antenna length', type: 'int', min: 2, max: 40 },
				],
			},
			{ section: 'Eye Size', fields: [{ path: 'params.eye.size', label: 'Size', type: 'int', min: 1, max: 4 }] },
			{
				section: 'Leg Count',
				fields: [
					{ path: 'params.legs.pairs', label: 'Leg pairs', type: 'int', min: 0, max: 12 },
					{ path: 'params.legs.upper', label: 'Upper length', type: 'int', min: 1, max: 12 },
					{ path: 'params.legs.lower', label: 'Lower length', type: 'int', min: 1, max: 12 },
					{ path: 'params.legs.splay', label: 'Splay (°)', type: 'range', min: 10, max: 85, step: 1 },
					{ path: 'params.legs.fan', label: 'Fan (°)', type: 'range', min: 0, max: 60, step: 1 },
				],
			},
			{
				section: 'Tail Length',
				fields: [
					{ path: 'params.tail.length', label: 'Tail shield', type: 'int', min: 1, max: 16 },
					{ path: 'params.tail.uropods', label: 'Uropods', type: 'int', min: 0, max: 8 },
				],
			},
		];
	}

	variationTargets() {
		return {
			body: ['body.length', 'body.width', 'body.height'],
			head: ['params.head.length', 'params.head.width', 'params.head.height'],
			legs: ['params.legs.upper', 'params.legs.lower'],
			antennae: ['params.antennae.length'],
			tail: ['params.tail.length', 'params.tail.uropods'],
			eye: ['params.eye.size'],
		};
	}

	exaggerationTargets() {
		return { head: ['params.head.width', 'params.head.height'], eye: ['params.eye.size'], limb: ['params.legs.upper', 'params.legs.lower', 'params.antennae.length'] };
	}

	semanticParts(def, ctx) {
		const P = def.params;
		const has = (...n) => this.partEnabled(def, ...n);
		const list = [];
		const add = (p) => (list.push(p), p);
		const g = ctx.grid;
		const n = Math.max(2, ctx.lod([Math.min(4, def.body.segments), Math.min(5, def.body.segments), def.body.segments]));
		const W = ctx.sx(def.body.width), H = ctx.sy(def.body.height);
		const segLen = Math.max(g, ctx.q((def.body.length * ctx.scale[2]) / (n + 1)));
		const S = { id: def.id, list };
		S.body = add({
			id: 'body',
			kind: 'segment_chain',
			segments: Array.from({ length: n }, (_, i) => ({ length: segLen, width: Math.max(2 * g, ctx.q(W * (0.78 + 0.22 * Math.sin((Math.PI * (i + 0.5)) / n)))), height: H })),
		});
		S.head = add({ id: 'head', kind: 'cephalon', size: ctx.size([P.head.width, P.head.height, P.head.length]) });
		if (has('eye', 'eyes')) S.eye = add({ id: 'eye', kind: 'compound_eye', size: ctx.s(P.eye.size) });
		if (has('antennae', 'antenna') && P.antennae) {
			const segs = Math.max(1, ctx.lod([1, 2, P.antennae.segments || 3]));
			const len = ctx.s(P.antennae.length);
			S.antennae = add({ id: 'antennae', kind: 'antennae', lengths: Array.from({ length: segs }, (_, i) => Math.max(g, ctx.q(len / segs * (1.15 - 0.15 * i)))), spread: P.antennae.spread ?? 40, antennules: P.antennae.antennules && ctx.atLeast('high') });
		}
		if (has('legs', 'leg') && P.legs?.pairs > 0) {
			const pairs = Math.max(1, Math.min(n, Math.round(P.legs.pairs * ctx.lod([0.45, 0.75, 1]))));
			S.legs = add({ id: 'legs', kind: 'walking_legs', pairs, upper: ctx.s(P.legs.upper), lower: ctx.s(P.legs.lower), splay: P.legs.splay ?? 60, fan: P.legs.fan ?? 30, width: ctx.s(P.legs.width || 1) });
		}
		if (has('tail') && P.tail) {
			S.tail = add({ id: 'tail', kind: 'pleotelson', pleon: ctx.atLeast('medium') ? P.tail.pleon ?? 2 : 1, length: ctx.sz(P.tail.length), width: clamp(P.tail.width ?? 0.7, 0.2, 1), uropods: ctx.s(P.tail.uropods || 0, 0) });
		}
		return S;
	}

	build(S, ctx) {
		const b = new ModelBuilder();
		const g = ctx.grid;
		const root = S.id;
		const segs = S.body.segments;
		const H = segs[0].height;
		const legRoom = S.legs ? S.legs.lower : 0;
		const yBottom = 16 + legRoom; // underside of the shell; legs reach down to the ground
		b.bone(root, { pivot: [0, 0, 0], role: 'root' });

		// Segment chain: body -> segment_2 -> ... (pivot at each segment's front underside so it can curl)
		let parent = root;
		let z = 0;
		const chain = [];
		segs.forEach((seg, i) => {
			const name = i === 0 ? 'body' : `segment_${i + 1}`;
			b.bone(name, { parent, pivot: [0, yBottom, z], role: i === 0 ? 'body' : 'segment', meta: { index: i, count: segs.length } });
			roundedBox(b, name, name, {
				from: [-seg.width / 2, yBottom, z - (i ? g : 0)],
				to: [seg.width / 2, yBottom + seg.height, z + seg.length],
				round: { top: g, x: g, front: 0, back: 0 },
				material: 'shell',
				faces: { down: 'belly' },
			});
			chain.push({ name, z0: z, z1: z + seg.length, seg });
			parent = name;
			z += seg.length;
		});

		// Head (cephalon) in front of the first segment
		const [hw, hh, hl] = S.head.size;
		b.bone('head', { parent: 'body', pivot: [0, yBottom, 0], role: 'head' });
		roundedBox(b, 'head', 'head', { from: [-hw / 2, yBottom, -hl], to: [hw / 2, yBottom + hh, g], round: { top: g, x: g, front: g, back: 0 }, material: 'shell', faces: { down: 'belly' } });
		if (S.eye) {
			const e = S.eye.size;
			for (const [side, sign] of [['right', 1], ['left', -1]]) {
				const name = `${side}_eye`;
				const cx = sign * (hw / 2 - e / 2 + g / 2);
				const cy = yBottom + hh - e / 2;
				const cz = -hl + e / 2 + g / 2;
				b.bone(name, { parent: 'head', pivot: [cx, cy, cz], role: 'eye', side });
				b.box(name, name, { center: [cx, cy, cz], size: [e, e, e], material: 'eye', meta: { eyeFace: sign > 0 ? 'east' : 'west', eyeFace2: 'north', glintLeft: sign < 0 } });
			}
		}
		if (S.antennae) {
			const A = S.antennae;
			for (const [side, sign] of [['right', 1], ['left', -1]]) {
				const rot = A.lengths.map((_, i) => [i === 0 ? 12 : -4, -sign * (i === 0 ? A.spread : A.spread * 0.45), 0]);
				addChain(b, {
					parent: 'head',
					names: A.lengths.map((_, i) => (i === 0 ? `${side}_antenna` : `${side}_antenna_${i + 1}`)),
					start: [sign * (hw / 2 - 1.5 * g), yBottom + g, -hl + g],
					lengths: A.lengths.map((l, i) => l + (i === 0 ? g : 0)),
					widths: A.lengths.map(() => g),
					axis: [0, 0, -1],
					rotations: rot,
					role: 'antenna',
					side,
					material: 'leg',
					meta: () => ({ legAxis: 2 }),
					priority: (i) => (i === 0 ? 1 : 2),
				});
				if (A.antennules) {
					addChain(b, {
						parent: 'head',
						names: [`${side}_antennule`],
						start: [sign * g / 2, yBottom + hh - g, -hl + g],
						lengths: [Math.max(2 * g, Math.round(A.lengths[0] * 0.6))],
						widths: [g],
						axis: [0, 0, -1],
						rotations: [[20, -sign * 20, 0]],
						role: 'antenna',
						side,
						material: 'leg',
						meta: () => ({ legAxis: 2 }),
						priority: () => 3,
					});
				}
			}
		}

		// Legs: one pair per segment (front segments first), fanned forward at the front and back at the rear
		if (S.legs) {
			const L = S.legs;
			const used = chain.slice(0, L.pairs);
			used.forEach((c, i) => {
				const t = used.length > 1 ? i / (used.length - 1) : 0.5;
				const fan = L.fan * (1 - 2 * t);
				for (const side of ['right', 'left']) {
					const sign = side === 'right' ? 1 : -1;
					addJointedLeg(b, {
						parent: c.name,
						name: `${side}_leg_${i + 1}`,
						side,
						hip: [sign * (c.seg.width / 2 - g), yBottom + g, (c.z0 + c.z1) / 2],
						upper: L.upper,
						lower: L.lower,
						splay: L.splay,
						kneeSplay: 12,
						fan,
						width: L.width,
						index: i,
						priority: i < 3 ? 1 : 2,
					});
				}
			});
		}

		// Pleon plates, tail shield (pleotelson) and uropods
		if (S.tail) {
			const T = S.tail;
			const last = chain[chain.length - 1];
			let tz = last.z1;
			let tailParent = last.name;
			const baseW = last.seg.width;
			for (let i = 0; i < T.pleon; i++) {
				const w = Math.max(2 * g, Math.round((baseW * (0.92 - 0.06 * i)) / g) * g);
				const name = i === 0 ? 'pleon' : `pleon_${i + 1}`;
				b.bone(name, { parent: tailParent, pivot: [0, yBottom, tz], role: 'segment', meta: { index: chain.length + i, pleon: true } });
				b.box(name, name, { from: [-w / 2, yBottom, tz - g], to: [w / 2, yBottom + H - g, tz + g], material: 'shell', faces: { down: 'belly' }, priority: 2 });
				tailParent = name;
				tz += g;
			}
			const tw = Math.max(2 * g, Math.round((baseW * T.width) / g) * g);
			b.bone('tail', { parent: tailParent, pivot: [0, yBottom, tz], role: 'tail' });
			roundedBox(b, 'tail', 'tail', { from: [-tw / 2, yBottom, tz - g], to: [tw / 2, yBottom + H - g, tz + T.length], round: { top: g, x: g, front: 0, back: g }, material: 'shell', faces: { down: 'belly' } });
			if (T.uropods > 0) {
				for (const [side, sign] of [['right', 1], ['left', -1]]) {
					const name = `${side}_uropod`;
					const x = sign * (tw / 2);
					b.bone(name, { parent: 'tail', pivot: [x, yBottom + g, tz], rotation: [0, sign * 18, 0], role: 'uropod', side });
					b.box(name, name, { from: [Math.min(x, x + sign * 2 * g), yBottom, tz], to: [Math.max(x, x + sign * 2 * g), yBottom + g, tz + T.uropods + T.length - 2 * g], plane: 'y', material: 'fin', shape: { type: 'round', axis: 'z', dir: 1, across: 'x' }, priority: 2 });
				}
			}
		}

		b.ground({ centerFilter: (c) => c.bone === 'body' || c.bone.startsWith('segment') || c.bone === 'head' || c.bone === 'tail', snap: g });
		const geometry = b.build();
		geometry.root = root;
		return geometry;
	}

	animationCatalog() {
		return ARTHROPOD_ANIMATIONS;
	}
}

const sideSign = (bone) => (bone.side === 'left' ? -1 : 1);

export function legBones(roles) {
	return roles.all('leg').sort((a, b) => a.meta.index - b.meta.index || (a.side < b.side ? -1 : 1));
}

/** Metachronal walking gait: waves of steps from front to back, left side half a cycle behind. */
export function walkLegs(c, roles, { amp = 1, swing = 20, lift = 12, phaseStep = null } = {}) {
	const legs = legBones(roles);
	const pairs = Math.max(1, legs.length / 2);
	const step = phaseStep ?? 1 / Math.max(2, pairs);
	for (const leg of legs) {
		const s = sideSign(leg);
		const phase = leg.meta.index * step + (s < 0 ? 0.5 : 0);
		c.wave(leg.name, 'rotation', { amplitude: [0, s * swing * amp, 0], cycles: 1, phase });
		c.wave(leg.name, 'rotation', { amplitude: [0, 0, s * lift * amp], cycles: 1, phase: phase + 0.25, shape: 'pulse' });
	}
}

/** Rolled-up pose: every body joint bends ventrally so the chain closes into a ball. */
function curlPose(roles, amp) {
	const joints = [...roles.all('segment'), ...roles.all('tail')];
	const per = Math.min(40, 300 / (joints.length + 1)) * amp;
	const pose = {};
	for (const j of joints) pose[j.name] = { rotation: [per, 0, 0] };
	if (roles.one('head')) pose.head = { rotation: [-25 * amp, 0, 0] };
	for (const leg of legBones(roles)) pose[leg.name] = { rotation: [0, 0, -sideSign(leg) * 50] };
	for (const a of roles.all('antenna')) if (!a.meta.index) pose[a.name] = { rotation: [-20, sideSign(a) * 30, 0] };
	return pose;
}

export const ARTHROPOD_ANIMATIONS = {
	idle({ amp, make, roles }) {
		const c = make(4, 'loop', 'Resting: antennae sweep, legs shuffle, body breathes');
		c.wave('body', 'scale', { amplitude: [0, 0.03, 0], cycles: 1 });
		for (const a of roles.all('antenna')) c.wave(a.name, 'rotation', { amplitude: [4 * amp, sideSign(a) * 8 * amp, 0], cycles: a.meta.index ? 2 : 1, phase: 0.2 * a.meta.index + (a.side === 'left' ? 0.3 : 0) });
		for (const leg of legBones(roles)) c.wave(leg.name, 'rotation', { amplitude: [0, sideSign(leg) * 3 * amp, 0], cycles: 1, phase: leg.meta.index * 0.13 + (leg.side === 'left' ? 0.5 : 0) });
		for (const u of roles.all('uropod')) c.wave(u.name, 'rotation', { amplitude: [0, sideSign(u) * 5 * amp, 0], cycles: 2 });
		return c.build();
	},
	walk({ amp, make, roles }) {
		const c = make(1, 'loop', 'Walking: metachronal leg waves, alternating sides, slight body sway');
		walkLegs(c, roles, { amp });
		c.wave('body', 'rotation', { amplitude: [0, 2 * amp, 1.5 * amp], cycles: 2 });
		c.wave('body', 'position', { amplitude: [0, 0.25 * amp, 0], cycles: 2, shape: 'pulse' });
		for (const a of roles.all('antenna')) c.wave(a.name, 'rotation', { amplitude: [2 * amp, sideSign(a) * 6 * amp, 0], cycles: 2, phase: 0.1 * a.meta.index });
		return c.build();
	},
	curl({ amp, make, roles, geometry }) {
		const segs = roles.all('segment');
		if (!segs.length) return null;
		const c = make(1.2, 'hold', 'Defensive roll: segments curl the tail under the body into a ball, legs tuck in');
		const pose = curlPose(roles, amp);
		const lift = groundLift(geometry, pose);
		for (const [bone, p] of Object.entries(pose)) {
			if (bone === 'body') continue;
			c.keys(bone, 'rotation', [[0, [0, 0, 0]], [0.9, p.rotation.map((v) => v * 1.04)], [1.2, p.rotation]]);
		}
		c.keys('body', 'position', [[0, [0, 0, 0]], [1.2, [0, lift, 0]]]);
		return c.build();
	},
	uncurl({ amp, make, roles, geometry }) {
		const segs = roles.all('segment');
		if (!segs.length) return null;
		const c = make(1, 'once', 'Unrolls from the defensive ball');
		const pose = curlPose(roles, amp);
		const lift = groundLift(geometry, pose);
		for (const [bone, p] of Object.entries(pose)) {
			if (bone === 'body') continue;
			c.keys(bone, 'rotation', [[0, p.rotation], [1, [0, 0, 0]]], 'linear');
		}
		c.keys('body', 'position', [[0, [0, lift, 0]], [1, [0, 0, 0]]], 'linear');
		return c.build();
	},
	hurt({ amp, make, roles }) {
		const c = make(0.5, 'once', 'Jolt: shell twitches, legs flail');
		c.keys('body', 'rotation', [[0, [0, 0, 0]], [0.1, [-6 * amp, 5 * amp, 0]], [0.25, [3 * amp, -3 * amp, 0]], [0.5, [0, 0, 0]]]);
		for (const leg of legBones(roles)) c.keys(leg.name, 'rotation', [[0, [0, 0, 0]], [0.12, [0, sideSign(leg) * 15 * amp, sideSign(leg) * 20 * amp]], [0.5, [0, 0, 0]]]);
		return c.build();
	},
	death({ make, roles, geometry }) {
		const c = make(2, 'hold', 'Flips onto its back, legs curl up');
		const legs = legBones(roles);
		const pose = { body: { rotation: [0, 0, 180] } };
		for (const leg of legs) pose[leg.name] = { rotation: [0, sideSign(leg) * 8, -sideSign(leg) * 45] };
		const lift = groundLift(geometry, pose);
		c.keys('body', 'rotation', [[0, [0, 0, 0]], [0.8, [0, 0, 120]], [1.2, [0, 0, 180]], [2, [0, 0, 180]]]);
		c.keys('body', 'position', [[0, [0, 0, 0]], [0.8, [0, lift * 0.8, 0]], [1.2, [0, lift, 0]], [2, [0, lift, 0]]]);
		for (const leg of legs) c.keys(leg.name, 'rotation', [[0, [0, 0, 0]], [1.2, [0, 0, 0]], [1.6, [0, sideSign(leg) * 10, -sideSign(leg) * 40]], [2, pose[leg.name].rotation]]);
		for (const a of roles.all('antenna')) c.keys(a.name, 'rotation', [[0, [0, 0, 0]], [2, [-15, 0, 0]]], 'linear');
		return c.build();
	},
};
