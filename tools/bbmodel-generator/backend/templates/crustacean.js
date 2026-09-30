// CrustaceanTemplate: decapods with two body plans sharing the same parts library.
//   plan "crab":   wide carapace, eyestalks, chelipeds with a movable finger, splayed walking legs
//   plan "shrimp": laterally compressed carapace with rostrum, long antennae, a curved abdomen
//                  chain with swimmerets and a tail fan, thin walking legs
// Covers the vent crab (Gandalfa yunohana) and deep-sea shrimps.

import { ModelBuilder } from '../generator/builder.js';
import { CreatureTemplate, groundLift } from './base.js';
import { addChain, addJointedLeg, taper } from './parts.js';
import { roundedBox, splitLength } from './shared.js';
import { legBones, walkLegs } from './arthropod.js';

export class CrustaceanTemplate extends CreatureTemplate {
	static id = 'crustacean';
	static label = 'Crustacean';
	static description = 'Decapod crustaceans: crab plan (carapace, claws, splayed legs) or shrimp plan (rostrum, antennae, curved abdomen with tail fan).';

	defaults() {
		return {
			template: 'crustacean',
			category: 'crustacean',
			plan: 'crab',
			body: { length: 8, width: 10, height: 4 },
			parts: [],
			params: {
				eye: { size: 1, stalk: 1 },
				rostrum: { length: 0, angle: 12 },
				antennae: { length: 8, segments: 3, sweep: 150, lift: 10 },
				claws: { enabled: true, arm: 4, hand: 4, hand_size: 3, finger: 3, spread: 40 },
				setae: { length: 0 },
				legs: { pairs: 4, upper: 5, lower: 5, splay: 70, fan: 40, width: 1 },
				abdomen: { segments: 0, length: 12, width: 4, height: 4, taper: 0.55, curve: 8, swimmerets: true },
				tail_fan: { length: 4, width: 6 },
			},
			palette: {},
			texture: { pattern: 'speckle', pattern_strength: 0.45 },
			glow: [],
			animations: ['idle', 'walk', 'claw_snap', 'hurt', 'death'],
			animation: { speed: 1, amplitude: 1 },
			variation: { body: 0.08, claws: 0.12, legs: 0.1, antennae: 0.12, abdomen: 0.08, eye: 0.1, hue: 6, lightness: 0.03 },
		};
	}

	editorSchema() {
		return [
			{ section: 'Body Plan', fields: [{ path: 'plan', label: 'Plan', type: 'select', options: ['crab', 'shrimp'] }] },
			{
				section: 'Body Size',
				fields: [
					{ path: 'body.length', label: 'Carapace length', type: 'int', min: 2, max: 32 },
					{ path: 'body.width', label: 'Carapace width', type: 'int', min: 2, max: 32 },
					{ path: 'body.height', label: 'Carapace height', type: 'int', min: 1, max: 16 },
					{ path: 'params.rostrum.length', label: 'Rostrum', type: 'int', min: 0, max: 16 },
				],
			},
			{
				section: 'Tail Length',
				fields: [
					{ path: 'params.abdomen.segments', label: 'Abdomen segments', type: 'int', min: 0, max: 8 },
					{ path: 'params.abdomen.length', label: 'Abdomen length', type: 'int', min: 2, max: 48 },
					{ path: 'params.abdomen.curve', label: 'Curve (°/seg)', type: 'range', min: -10, max: 25, step: 1 },
					{ path: 'params.tail_fan.length', label: 'Tail fan length', type: 'int', min: 1, max: 12 },
				],
			},
			{
				section: 'Claw Size',
				fields: [
					{ path: 'params.claws.enabled', label: 'Claws', type: 'bool' },
					{ path: 'params.claws.hand_size', label: 'Claw size', type: 'int', min: 1, max: 8 },
					{ path: 'params.claws.hand', label: 'Claw length', type: 'int', min: 1, max: 12 },
					{ path: 'params.claws.arm', label: 'Arm length', type: 'int', min: 1, max: 12 },
				],
			},
			{
				section: 'Leg Count',
				fields: [
					{ path: 'params.legs.pairs', label: 'Leg pairs', type: 'int', min: 0, max: 8 },
					{ path: 'params.legs.upper', label: 'Upper length', type: 'int', min: 1, max: 16 },
					{ path: 'params.legs.lower', label: 'Lower length', type: 'int', min: 1, max: 16 },
					{ path: 'params.legs.splay', label: 'Splay (°)', type: 'range', min: 5, max: 85, step: 1 },
				],
			},
			{
				section: 'Eye Size / Antennae',
				fields: [
					{ path: 'params.eye.size', label: 'Eye size', type: 'int', min: 1, max: 4 },
					{ path: 'params.eye.stalk', label: 'Eye stalk', type: 'int', min: 0, max: 4 },
					{ path: 'params.antennae.length', label: 'Antenna length', type: 'int', min: 0, max: 64 },
				],
			},
		];
	}

	variationTargets() {
		return {
			body: ['body.length', 'body.width', 'body.height'],
			claws: ['params.claws.hand', 'params.claws.hand_size', 'params.claws.arm'],
			legs: ['params.legs.upper', 'params.legs.lower'],
			antennae: ['params.antennae.length'],
			abdomen: ['params.abdomen.length'],
			eye: ['params.eye.size'],
		};
	}

	exaggerationTargets() {
		return { eye: ['params.eye.size'], limb: ['params.legs.upper', 'params.legs.lower', 'params.antennae.length'], teeth: ['params.claws.hand_size'] };
	}

	semanticParts(def, ctx) {
		const P = def.params;
		const has = (...n) => this.partEnabled(def, ...n);
		const list = [];
		const add = (p) => (list.push(p), p);
		const g = ctx.grid;
		const S = { id: def.id, list, plan: def.plan === 'shrimp' ? 'shrimp' : 'crab' };
		S.body = add({ id: 'body', kind: 'carapace', plan: S.plan, size: ctx.size([def.body.width, def.body.height, def.body.length]) });
		if (has('eye', 'eyes')) S.eye = add({ id: 'eye', kind: 'stalked_eye', size: ctx.s(P.eye.size), stalk: ctx.s(P.eye.stalk || 0, 0) });
		if (has('rostrum') && P.rostrum?.length > 0) S.rostrum = add({ id: 'rostrum', kind: 'rostrum', length: ctx.sz(P.rostrum.length), angle: P.rostrum.angle ?? 12 });
		if (has('antennae', 'antenna') && P.antennae?.length > 0) {
			const segs = Math.max(1, ctx.lod([1, 2, P.antennae.segments || 3]));
			S.antennae = add({ id: 'antennae', kind: 'antennae', lengths: splitLength(ctx.s(P.antennae.length), segs, g), sweep: P.antennae.sweep ?? 150, lift: P.antennae.lift ?? 10 });
		}
		if (has('claws', 'claw') && P.claws?.enabled) {
			S.claws = add({ id: 'claws', kind: 'chelipeds', arm: ctx.s(P.claws.arm), hand: ctx.s(P.claws.hand), size: ctx.s(P.claws.hand_size), finger: ctx.s(P.claws.finger), spread: P.claws.spread ?? 40, finger_bone: ctx.atLeast('medium') });
		}
		if (has('setae', 'hair') && P.setae?.length > 0 && ctx.atLeast('medium')) S.setae = add({ id: 'setae', kind: 'setae_fringe', length: ctx.s(P.setae.length) });
		if (has('legs', 'leg') && P.legs?.pairs > 0) {
			S.legs = add({ id: 'legs', kind: 'walking_legs', pairs: Math.max(1, Math.round(P.legs.pairs * ctx.lod([0.5, 0.75, 1]))), upper: ctx.s(P.legs.upper), lower: ctx.s(P.legs.lower), splay: P.legs.splay ?? 70, fan: P.legs.fan ?? 40, width: ctx.s(P.legs.width || 1) });
		}
		if (has('abdomen', 'tail') && P.abdomen?.segments > 0) {
			const n = Math.max(1, ctx.lod([2, Math.min(4, P.abdomen.segments), P.abdomen.segments]));
			const A = P.abdomen;
			S.abdomen = add({
				id: 'abdomen',
				kind: 'abdomen_chain',
				segments: splitLength(ctx.sz(A.length), n, g).map((length, i) => ({ length, width: taper(A.width * ctx.scale[0], A.width * ctx.scale[0] * (A.taper ?? 0.55), i, n, g), height: taper(A.height * ctx.scale[1], A.height * ctx.scale[1] * (A.taper ?? 0.55), i, n, g) })),
				curve: A.curve ?? 8,
				swimmerets: A.swimmerets && ctx.atLeast('high'),
			});
			if (P.tail_fan) S.fan = add({ id: 'tail_fan', kind: 'tail_fan', length: ctx.sz(P.tail_fan.length), width: ctx.sx(P.tail_fan.width) });
		}
		return S;
	}

	build(S, ctx) {
		const b = new ModelBuilder();
		const g = ctx.grid;
		const root = S.id;
		const [cw, ch, cl] = S.body.size;
		const legDrop = S.legs ? S.legs.lower + g : 0;
		const yb = 24 + legDrop; // carapace underside
		b.bone(root, { pivot: [0, 0, 0], role: 'root' });
		b.bone('body', { parent: root, pivot: [0, yb + ch / 2, 0], role: 'body' });
		const z0 = -cl / 2, z1 = cl / 2;
		const crab = S.plan === 'crab';
		roundedBox(b, 'body', 'body', {
			from: [-cw / 2, yb, z0],
			to: [cw / 2, yb + ch, z1],
			round: crab ? { top: g, x: g, front: g, back: g } : { top: g, x: 0, front: 0, back: 0 },
			material: 'shell',
			faces: { down: 'belly' },
		});

		// Eyes (on stalks at the front corners)
		if (S.eye) {
			const e = S.eye.size, st = S.eye.stalk;
			for (const [side, sign] of [['right', 1], ['left', -1]]) {
				const name = `${side}_eye`;
				const x = sign * Math.max(e / 2, cw / 2 - (crab ? 1.5 * g : e / 2));
				const y = yb + ch - (crab ? g : e);
				const z = z0 + (crab ? g / 2 : 0);
				b.bone(name, { parent: 'body', pivot: [x, y, z], rotation: [crab ? 10 : 0, -sign * (crab ? 10 : 20), 0], role: 'eye', side });
				if (st > 0) b.box(name, `${side}_eye_stalk`, { from: [x - g / 2, y - g / 2, z - st], to: [x + g / 2, y + g / 2, z + g], material: 'leg', meta: { legAxis: 2 }, priority: 2 });
				b.box(name, name, { center: [x, y, z - st - e / 2 + g / 2], size: [e, e, e], material: 'eye', meta: { eyeFace: sign > 0 ? 'east' : 'west', eyeFace2: 'north', glintLeft: sign < 0 } });
			}
		}

		if (S.rostrum) {
			b.bone('rostrum', { parent: 'body', pivot: [0, yb + ch - g, z0], rotation: [S.rostrum.angle, 0, 0], role: 'rostrum' });
			b.box('rostrum', 'rostrum', { from: [-g / 2, yb + ch - 2 * g, z0 - S.rostrum.length], to: [g / 2, yb + ch - g, z0 + g], material: 'shell', shape: null, priority: 1 });
		}

		// Antennae: long whips sweeping back over the body (shrimp) or short and forward (crab)
		if (S.antennae) {
			const A = S.antennae;
			for (const [side, sign] of [['right', 1], ['left', -1]]) {
				const n = A.lengths.length;
				const first = crab ? 25 : Math.min(40, A.sweep / 3);
				const rest = n > 1 ? (A.sweep - first) / (n - 1) : 0;
				const rot = A.lengths.map((_, i) => [i === 0 ? A.lift : -A.lift * 0.4, -sign * (i === 0 ? first : rest), 0]);
				addChain(b, {
					parent: 'body',
					names: A.lengths.map((_, i) => (i === 0 ? `${side}_antenna` : `${side}_antenna_${i + 1}`)),
					start: [sign * (crab ? g : g / 2), yb + ch - (crab ? 2 * g : 1.5 * g), z0 + g],
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
			}
		}

		// Chelipeds: arm -> hand (fixed finger) -> movable finger
		if (S.claws) {
			const C = S.claws;
			for (const [side, sign] of [['right', 1], ['left', -1]]) {
				const shoulder = [sign * (cw / 2 - g), yb + g, z0 + g];
				const arm = `${side}_claw_arm`;
				b.bone(arm, { parent: 'body', pivot: shoulder, rotation: [-8, -sign * C.spread, 0], role: 'claw_arm', side });
				b.box(arm, arm, { from: [shoulder[0] - g, shoulder[1] - g, shoulder[2] - C.arm], to: [shoulder[0] + g, shoulder[1] + g, shoulder[2] + g], material: 'claw', priority: 1 });
				const wrist = [shoulder[0], shoulder[1], shoulder[2] - C.arm];
				const hand = `${side}_claw`;
				b.bone(hand, { parent: arm, pivot: wrist, rotation: [4, sign * (C.spread + 25), 0], role: 'claw', side });
				const hs = C.size;
				b.box(hand, hand, { from: [wrist[0] - hs / 2, wrist[1] - hs / 2, wrist[2] - C.hand], to: [wrist[0] + hs / 2, wrist[1] + hs / 2, wrist[2] + g], material: 'claw', faces: { north: 'claw' } });
				// Fixed finger extends forward from the lower half of the hand; the movable finger (dactyl) sits on top
				const tip = wrist[2] - C.hand;
				b.box(hand, `${side}_claw_fixed`, { from: [wrist[0] - g / 2, wrist[1] - hs / 2, tip - C.finger], to: [wrist[0] + g / 2, wrist[1] - hs / 2 + g, tip + g], material: 'leg', meta: { legAxis: 2, colorKey: 'claw_tip' }, priority: 2 });
				const finger = `${side}_claw_finger`;
				const fp = [wrist[0], wrist[1] + hs / 2 - g, tip + g];
				if (C.finger_bone) b.bone(finger, { parent: hand, pivot: fp, rotation: [6, 0, 0], role: 'claw_finger', side });
				b.box(C.finger_bone ? finger : hand, finger, { from: [fp[0] - g / 2, fp[1] - g, fp[2] - C.finger - g], to: [fp[0] + g / 2, fp[1], fp[2]], material: 'leg', meta: { legAxis: 2, colorKey: 'claw_tip' }, priority: 2 });
			}
		}

		// Setae ("beard"): hair fringes hanging under the carapace, each a single plane with cut-out strands
		if (S.setae) {
			const L = S.setae.length;
			const hair = (strands, seed) => ({ type: 'hair', axis: 'y', dir: -1, across: strands.axis, strands: strands.n, seed });
			b.box('body', 'setae_front', { from: [-(cw / 2 - g), yb - L, z0 + g], to: [cw / 2 - g, yb + g, z0 + g], material: 'setae', plane: 'z', shape: hair({ axis: 'x', n: cw - 2 * g }, 3), priority: 2 });
			for (const sign of [1, -1]) {
				b.box('body', 'setae_side', { from: [sign * (cw / 2 - 1.5 * g), yb - L, z0 + 2 * g], to: [sign * (cw / 2 - 1.5 * g), yb + g, z1 - g], material: 'setae', plane: 'x', shape: hair({ axis: 'z', n: cl - 3 * g }, sign > 0 ? 5 : 9), priority: 3 });
			}
		}

		// Walking legs from the carapace sides
		if (S.legs) {
			const L = S.legs;
			const span = cl - 2 * g;
			for (let i = 0; i < L.pairs; i++) {
				const t = L.pairs > 1 ? i / (L.pairs - 1) : 0.5;
				const z = z0 + g + (crab ? span * 0.25 : 0) + (crab ? span * 0.7 : span) * t;
				for (const side of ['right', 'left']) {
					const sign = side === 'right' ? 1 : -1;
					addJointedLeg(b, {
						parent: 'body',
						name: `${side}_leg_${i + 1}`,
						side,
						hip: [sign * (cw / 2 - g), yb + g, Math.round(z * 2) / 2],
						upper: L.upper,
						lower: L.lower,
						splay: L.splay,
						kneeSplay: crab ? -20 : 5,
						fan: L.fan * (1 - 2 * t),
						width: L.width,
						index: i,
						priority: i < 2 ? 1 : 2,
					});
				}
			}
		}

		// Abdomen chain (shrimp): curved segments with swimmerets, ending in a tail fan
		if (S.abdomen) {
			let parent = 'body';
			let z = z1 - g;
			const yTop = yb + ch;
			S.abdomen.segments.forEach((seg, i) => {
				const name = i === 0 ? 'abdomen' : `abdomen_${i + 1}`;
				b.bone(name, { parent, pivot: [0, yTop - seg.height / 2, z], rotation: [S.abdomen.curve, 0, 0], role: 'abdomen', meta: { index: i } });
				b.box(name, name, { from: [-seg.width / 2, yTop - seg.height, z], to: [seg.width / 2, yTop, z + seg.length + g], material: 'shell', faces: { down: 'belly' } });
				if (S.abdomen.swimmerets) {
					b.box(name, `${name}_swimmeret`, { from: [-seg.width / 2 + g / 2, yTop - seg.height - 2 * g, z + g], to: [seg.width / 2 - g / 2, yTop - seg.height + g, z + g], material: 'membrane', shape: { type: 'leaf', axis: 'y', dir: -1, across: 'x' }, meta: { alpha: 255 }, priority: 3 });
				}
				parent = name;
				z += seg.length;
			});
			if (S.fan) {
				const last = S.abdomen.segments[S.abdomen.segments.length - 1];
				const yc = yTop - last.height / 2;
				b.bone('tail_fan', { parent, pivot: [0, yc, z], rotation: [S.abdomen.curve * 0.5, 0, 0], role: 'tail_fin' });
				b.box('tail_fan', 'telson', { from: [-g, yc, z - g], to: [g, yc, z + S.fan.length], material: 'fin', shape: { type: 'taper', axis: 'z', dir: 1, across: 'x' } });
				for (const [side, sign] of [['right', 1], ['left', -1]]) {
					const name = `${side}_uropod`;
					b.bone(name, { parent: 'tail_fan', pivot: [sign * g, yc, z], rotation: [0, sign * 22, 0], role: 'uropod', side });
					const uw = Math.max(g, Math.round((S.fan.width / 2 - g) / g) * g);
					b.box(name, name, { from: [Math.min(sign * g, sign * (g + uw)), yc, z - g], to: [Math.max(sign * g, sign * (g + uw)), yc, z + S.fan.length], material: 'fin', shape: { type: 'round', axis: 'z', dir: 1, across: 'x' }, priority: 2 });
				}
			}
		}

		b.ground({ centerFilter: (c) => c.bone === 'body' || c.bone.startsWith('abdomen'), snap: g });
		const geometry = b.build();
		geometry.root = root;
		return geometry;
	}

	animationCatalog() {
		return CRUSTACEAN_ANIMATIONS;
	}
}

const sideSign = (bone) => (bone.side === 'left' ? -1 : 1);

export const CRUSTACEAN_ANIMATIONS = {
	idle({ amp, make, roles }) {
		const c = make(3, 'loop', 'Resting: claws flex, antennae sweep, swimmerets paddle, legs fidget');
		c.wave('body', 'position', { amplitude: [0, 0.2 * amp, 0], cycles: 1 });
		for (const f of roles.all('claw_finger')) c.wave(f.name, 'rotation', { amplitude: [10 * amp, 0, 0], cycles: 1, shape: 'cos1', phase: f.side === 'left' ? 0.35 : 0 });
		for (const a of roles.all('claw_arm')) c.wave(a.name, 'rotation', { amplitude: [3 * amp, sideSign(a) * 4 * amp, 0], cycles: 1, phase: a.side === 'left' ? 0.5 : 0 });
		for (const a of roles.all('antenna')) c.wave(a.name, 'rotation', { amplitude: [3 * amp, sideSign(a) * 6 * amp, 0], cycles: a.meta.index ? 2 : 1, phase: 0.15 * a.meta.index + (a.side === 'left' ? 0.4 : 0) });
		for (const e of roles.all('eye')) c.wave(e.name, 'rotation', { amplitude: [0, sideSign(e) * 6 * amp, 0], cycles: 1, phase: 0.2 });
		roles.all('abdomen').forEach((s, i) => c.wave(s.name, 'rotation', { amplitude: [2 * amp, 0, 0], cycles: 1, phase: -0.1 * i }));
		for (const leg of legBones(roles)) c.wave(leg.name, 'rotation', { amplitude: [0, sideSign(leg) * 3 * amp, 0], cycles: 1, phase: leg.meta.index * 0.2 + (leg.side === 'left' ? 0.5 : 0) });
		return c.build();
	},
	walk({ amp, make, roles }) {
		const c = make(0.9, 'loop', 'Walking: alternating leg waves with a slight body sway');
		walkLegs(c, roles, { amp, swing: 18, lift: 14, phaseStep: 0.25 });
		c.wave('body', 'rotation', { amplitude: [0, 0, 2 * amp], cycles: 2 });
		c.wave('body', 'position', { amplitude: [0, 0.3 * amp, 0], cycles: 2, shape: 'pulse' });
		for (const a of roles.all('claw_arm')) c.wave(a.name, 'rotation', { amplitude: [4 * amp, 0, 0], cycles: 1, phase: a.side === 'left' ? 0.5 : 0 });
		return c.build();
	},
	swim({ amp, make, roles }) {
		if (!roles.all('abdomen').length) return null;
		const c = make(0.8, 'loop', 'Swimming: swimmerets beat in a wave, abdomen flexes, antennae stream back');
		roles.all('abdomen').forEach((s, i) => c.wave(s.name, 'rotation', { amplitude: [5 * amp, 0, 0], cycles: 1, phase: -0.12 * i }));
		if (roles.one('tail_fin')) c.wave('tail_fan', 'rotation', { amplitude: [8 * amp, 0, 0], cycles: 1, phase: -0.6 });
		for (const a of roles.all('antenna')) c.wave(a.name, 'rotation', { amplitude: [2 * amp, sideSign(a) * 3 * amp, 0], cycles: 2, phase: 0.1 * a.meta.index });
		for (const leg of legBones(roles)) c.wave(leg.name, 'rotation', { amplitude: [0, 0, sideSign(leg) * -6 * amp], cycles: 2, phase: leg.meta.index * 0.15 });
		c.wave('body', 'position', { amplitude: [0, 0.4 * amp, 0], cycles: 1 });
		return c.build();
	},
	flick({ amp, make, roles }) {
		const segs = roles.all('abdomen');
		if (!segs.length) return null;
		const c = make(0.5, 'once', 'Escape tail-flip: the abdomen snaps under the body and back');
		segs.forEach((s) => c.keys(s.name, 'rotation', [[0, [0, 0, 0]], [0.12, [28 * amp, 0, 0]], [0.3, [-4 * amp, 0, 0]], [0.5, [0, 0, 0]]]));
		if (roles.one('tail_fin')) c.keys('tail_fan', 'rotation', [[0, [0, 0, 0]], [0.12, [35 * amp, 0, 0]], [0.5, [0, 0, 0]]]);
		c.keys('body', 'position', [[0, [0, 0, 0]], [0.2, [0, 1.5 * amp, 3 * amp]], [0.5, [0, 0, 0]]]);
		return c.build();
	},
	claw_snap({ amp, make, roles }) {
		const fingers = roles.all('claw_finger');
		if (!fingers.length) return null;
		const c = make(0.6, 'once', 'Raises the claws and snaps them shut');
		for (const f of fingers) c.keys(f.name, 'rotation', [[0, [0, 0, 0]], [0.25, [35 * amp, 0, 0]], [0.35, [-4, 0, 0]], [0.6, [0, 0, 0]]]);
		for (const a of roles.all('claw_arm')) c.keys(a.name, 'rotation', [[0, [0, 0, 0]], [0.25, [18 * amp, sideSign(a) * -10 * amp, 0]], [0.6, [0, 0, 0]]]);
		return c.build();
	},
	hurt({ amp, make, roles }) {
		const c = make(0.5, 'once', 'Jolt: shell twitches, legs and claws flinch');
		c.keys('body', 'rotation', [[0, [0, 0, 0]], [0.1, [-8 * amp, 6 * amp, 0]], [0.25, [3 * amp, -3 * amp, 0]], [0.5, [0, 0, 0]]]);
		for (const a of roles.all('claw_arm')) c.keys(a.name, 'rotation', [[0, [0, 0, 0]], [0.12, [12 * amp, sideSign(a) * 12 * amp, 0]], [0.5, [0, 0, 0]]]);
		for (const leg of legBones(roles)) c.keys(leg.name, 'rotation', [[0, [0, 0, 0]], [0.12, [0, 0, sideSign(leg) * 15 * amp]], [0.5, [0, 0, 0]]]);
		roles.all('abdomen').forEach((s) => c.keys(s.name, 'rotation', [[0, [0, 0, 0]], [0.12, [10 * amp, 0, 0]], [0.5, [0, 0, 0]]]));
		return c.build();
	},
	death({ make, roles, geometry }) {
		const c = make(2, 'hold', 'Rolls over, legs and claws fold up');
		const legs = legBones(roles);
		const pose = { body: { rotation: [0, 0, 180] } };
		for (const leg of legs) pose[leg.name] = { rotation: [0, sideSign(leg) * 8, -sideSign(leg) * 45] };
		for (const a of roles.all('claw_arm')) pose[a.name] = { rotation: [-20, sideSign(a) * 20, 0] };
		roles.all('abdomen').forEach((s) => (pose[s.name] = { rotation: [14, 0, 0] }));
		const lift = groundLift(geometry, pose);
		c.keys('body', 'rotation', [[0, [0, 0, 0]], [1, [0, 0, 150]], [1.4, [0, 0, 180]], [2, [0, 0, 180]]]);
		c.keys('body', 'position', [[0, [0, 0, 0]], [1, [0, lift * 0.85, 0]], [1.4, [0, lift, 0]], [2, [0, lift, 0]]]);
		for (const leg of legs) c.keys(leg.name, 'rotation', [[0, [0, 0, 0]], [1.4, [0, 0, 0]], [2, pose[leg.name].rotation]]);
		for (const a of roles.all('claw_arm')) c.keys(a.name, 'rotation', [[0, [0, 0, 0]], [2, pose[a.name].rotation]], 'linear');
		roles.all('abdomen').forEach((s) => c.keys(s.name, 'rotation', [[0, [0, 0, 0]], [2, [14, 0, 0]]], 'linear'));
		return c.build();
	},
};

