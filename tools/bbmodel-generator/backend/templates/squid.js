// SquidTemplate: tapering mantle chain with fins, head with huge eyes and beak, a crown of
// segmented arms (suckers on the inner face) and two long feeding tentacles with clubs.
// The squid swims horizontally with the arm crown forward (north, -Z).

import { ModelBuilder } from '../generator/builder.js';
import { clamp } from '../generator/util.js';
import { CreatureTemplate } from './base.js';
import { addChain, addEyes, taper } from './parts.js';
import { splitLength } from './shared.js';


export class SquidTemplate extends CreatureTemplate {
	static id = 'squid';
	static label = 'Squid';
	static description = 'Mantle chain with fins, head with huge eyes, a ring of segmented sucker arms and two long club tentacles.';

	defaults() {
		return {
			template: 'squid',
			category: 'cephalopod',
			body: { length: 20, width: 8, height: 8, taper: 0.45, segments: 3 },
			parts: [],
			params: {
				head: { length: 5, width: 7, height: 7 },
				eye: { size: 3, height: 0.55, forward: 0.5, protrude: 0.4 },
				arms: { count: 8, length: 16, segments: 3, width: 2, taper: 0.5, splay: 12, ring: 0.7 },
				tentacles: { count: 2, length: 26, segments: 3, width: 1, club: 5, club_width: 2 },
				fins: { length: 7, width: 6, position: 1, shape: 'sail' },
				beak: { enabled: true, size: 2 },
			},
			palette: {},
			texture: { pattern: 'spots', pattern_strength: 0.6 },
			glow: [],
			animations: ['idle', 'swim', 'tentacle_move', 'grab', 'hurt', 'death'],
			animation: { speed: 1, amplitude: 1 },
			variation: { body: 0.08, head: 0.08, arms: 0.1, tentacles: 0.12, fin: 0.12, eye: 0.1, hue: 6, lightness: 0.03 },
		};
	}

	editorSchema() {
		return [
			{
				section: 'Body Size',
				fields: [
					{ path: 'body.length', label: 'Mantle length', type: 'int', min: 4, max: 96 },
					{ path: 'body.width', label: 'Mantle width', type: 'int', min: 2, max: 32 },
					{ path: 'body.height', label: 'Mantle height', type: 'int', min: 2, max: 32 },
					{ path: 'body.taper', label: 'Taper', type: 'range', min: 0.2, max: 1, step: 0.05 },
				],
			},
			{
				section: 'Head Size',
				fields: [
					{ path: 'params.head.length', label: 'Length', type: 'int', min: 2, max: 24 },
					{ path: 'params.head.width', label: 'Width', type: 'int', min: 2, max: 24 },
					{ path: 'params.head.height', label: 'Height', type: 'int', min: 2, max: 24 },
					{ path: 'params.beak.size', label: 'Beak', type: 'int', min: 1, max: 4 },
				],
			},
			{
				section: 'Eye Size',
				fields: [
					{ path: 'params.eye.size', label: 'Size', type: 'int', min: 1, max: 8 },
					{ path: 'params.eye.forward', label: 'Forward', type: 'range', min: 0, max: 1, step: 0.05 },
				],
			},
			{
				section: 'Tentacle Count',
				fields: [
					{ path: 'params.arms.count', label: 'Arms', type: 'int', min: 0, max: 12 },
					{ path: 'params.arms.length', label: 'Arm length', type: 'int', min: 2, max: 64 },
					{ path: 'params.arms.segments', label: 'Arm segments', type: 'int', min: 1, max: 5 },
					{ path: 'params.arms.splay', label: 'Arm splay (°)', type: 'range', min: 0, max: 45, step: 1 },
					{ path: 'params.tentacles.count', label: 'Tentacles', type: 'int', min: 0, max: 2 },
					{ path: 'params.tentacles.length', label: 'Tentacle length', type: 'int', min: 4, max: 96 },
					{ path: 'params.tentacles.club', label: 'Club length', type: 'int', min: 1, max: 12 },
				],
			},
			{
				section: 'Fin Size',
				fields: [
					{ path: 'params.fins.length', label: 'Fin length', type: 'int', min: 1, max: 32 },
					{ path: 'params.fins.width', label: 'Fin span', type: 'int', min: 1, max: 32 },
					{ path: 'params.fins.shape', label: 'Fin shape', type: 'select', options: ['sail', 'round', 'fan', 'rect'] },
				],
			},
		];
	}

	variationTargets() {
		return {
			body: ['body.length', 'body.width', 'body.height'],
			head: ['params.head.length', 'params.head.width', 'params.head.height'],
			arms: ['params.arms.length'],
			tentacles: ['params.tentacles.length', 'params.tentacles.club'],
			fin: ['params.fins.length', 'params.fins.width'],
			eye: ['params.eye.size'],
		};
	}

	exaggerationTargets() {
		return {
			head: ['params.head.length', 'params.head.width', 'params.head.height'],
			eye: ['params.eye.size'],
			fin: ['params.fins.length', 'params.fins.width'],
			limb: ['params.arms.length', 'params.tentacles.length'],
		};
	}

	semanticParts(def, ctx) {
		const P = def.params;
		const has = (...n) => this.partEnabled(def, ...n);
		const list = [];
		const add = (p) => (list.push(p), p);
		const g = ctx.grid;
		const nMantle = Math.max(1, ctx.lod([1, 2, def.body.segments || 3]));
		const mLen = splitLength(ctx.sz(def.body.length), nMantle, g);
		const tp = clamp(def.body.taper ?? 0.45, 0.1, 1);
		const S = { id: def.id, list };
		S.mantle = add({
			id: 'body',
			kind: 'mantle',
			sections: mLen.map((length, i) => ({ length, width: taper(def.body.width * ctx.scale[0], def.body.width * ctx.scale[0] * tp, i, nMantle, g), height: taper(def.body.height * ctx.scale[1], def.body.height * ctx.scale[1] * tp, i, nMantle, g) })),
		});
		S.head = add({ id: 'head', kind: 'head', size: ctx.size([P.head.width, P.head.height, P.head.length]) });
		if (has('eye', 'eyes')) S.eye = add({ id: 'eye', kind: 'eye', size: ctx.s(P.eye.size), height: P.eye.height ?? 0.55, forward: P.eye.forward ?? 0.5, protrude: P.eye.protrude ?? 0.4 });
		if (has('arms', 'tentacle', 'tentacles') && P.arms?.count > 0) {
			const count = Math.max(1, Math.round(P.arms.count * ctx.lod([0.5, 1, 1])));
			const segs = Math.max(1, ctx.lod([1, Math.min(2, P.arms.segments), P.arms.segments]));
			S.arms = add({ id: 'arms', kind: 'arm_crown', count, lengths: splitLength(ctx.s(P.arms.length), segs, g), width: ctx.s(P.arms.width), taper: P.arms.taper ?? 0.5, splay: P.arms.splay ?? 12, ring: P.arms.ring ?? 0.7 });
		}
		if (has('tentacles', 'tentacle') && P.tentacles?.count > 0 && ctx.atLeast('medium')) {
			const segs = Math.max(1, ctx.lod([1, 2, P.tentacles.segments]));
			S.tentacles = add({ id: 'tentacles', kind: 'feeding_tentacles', count: Math.min(2, P.tentacles.count), lengths: splitLength(ctx.s(P.tentacles.length), segs, g), width: Math.max(g, ctx.s(P.tentacles.width)), club: ctx.s(P.tentacles.club), clubWidth: ctx.s(P.tentacles.club_width) });
		}
		if (has('fin', 'fins') && P.fins) S.fins = add({ id: 'fin', kind: 'mantle_fins', length: ctx.sz(P.fins.length), width: ctx.sx(P.fins.width), shape: P.fins.shape || 'sail' });
		if (has('beak', 'head') && P.beak?.enabled && ctx.atLeast('medium')) S.beak = add({ id: 'beak', kind: 'beak', size: ctx.s(P.beak.size) });
		return S;
	}

	build(S, ctx) {
		const b = new ModelBuilder();
		const g = ctx.grid;
		const root = S.id;
		const y0 = 32;
		const [hw, hh, hl] = S.head.size;
		b.bone(root, { pivot: [0, 0, 0], role: 'root' });

		// Mantle: body (first section) -> mantle -> mantle_tip ...
		let parent = root;
		let z = 0;
		const sections = [];
		S.mantle.sections.forEach((sec, i) => {
			const name = i === 0 ? 'body' : i === S.mantle.sections.length - 1 ? 'mantle_tip' : `mantle${i === 1 ? '' : '_' + i}`;
			b.bone(name, { parent, pivot: [0, y0, z], role: i === 0 ? 'body' : 'mantle', meta: { index: i } });
			const bottom = y0 - Math.floor(sec.height / 2 / g) * g;
			b.box(name, name, { from: [-sec.width / 2, bottom, z - (i ? g : 0)], to: [sec.width / 2, bottom + sec.height, z + sec.length], material: 'skin', faces: { down: 'belly' } });
			sections.push({ name, z0: z, z1: z + sec.length, sec, bottom });
			parent = name;
			z += sec.length;
		});

		if (S.fins) {
			const tip = sections[sections.length - 1];
			const { length: fl, width: fw, shape } = S.fins;
			const fz = Math.max(tip.z0, tip.z1 - fl);
			for (const [side, sign] of [['right', 1], ['left', -1]]) {
				const x = (sign * tip.sec.width) / 2;
				const name = `${side}_fin`;
				b.bone(name, { parent: tip.name, pivot: [x, y0, fz + fl / 2], role: 'fin', side });
				b.box(name, name, {
					from: [Math.min(x - sign * g, x + sign * fw), y0, fz],
					to: [Math.max(x - sign * g, x + sign * fw), y0, fz + fl],
					material: 'fin',
					shape: { type: shape, axis: 'x', dir: sign, across: 'z' },
				});
			}
		}

		// Head in front of the mantle
		const hb = y0 - Math.floor(hh / 2 / g) * g;
		const zHF = -hl;
		b.bone('head', { parent: 'body', pivot: [0, y0, 0], role: 'head' });
		b.box('head', 'head', { from: [-hw / 2, hb, zHF], to: [hw / 2, hb + hh, g], material: 'skin', faces: { down: 'belly' } });
		if (S.eye) addEyes(b, { parent: 'head', halfWidth: hw / 2, y: hb + hh * S.eye.height, z: zHF + hl * S.eye.forward, size: S.eye.size, protrude: S.eye.protrude, g });
		if (S.beak) {
			const e = S.beak.size;
			b.box('head', 'beak', { from: [-e / 2, y0 - e / 2, zHF - e], to: [e / 2, y0 + e / 2, zHF + g], material: 'mouth', priority: 3 });
		}

		// Arm crown on the head front
		if (S.arms) {
			const A = S.arms;
			const rx = Math.max(g, (hw / 2 - A.width / 2) * A.ring);
			const ry = Math.max(g, (hh / 2 - A.width / 2) * A.ring);
			for (let k = 0; k < A.count; k++) {
				const theta = (2 * Math.PI * (k + 0.5)) / A.count + Math.PI / 2;
				const cx = Math.round((Math.cos(theta) * rx) / (g / 2)) * (g / 2);
				const cy = y0 + Math.round((Math.sin(theta) * ry) / (g / 2)) * (g / 2);
				const splay = A.splay;
				const rotation = [Math.round(splay * Math.sin(theta) * 10) / 10, Math.round(-splay * Math.cos(theta) * 10) / 10, 0];
				const inner = innerFace(theta);
				const names = A.lengths.map((_, i) => (i === 0 ? `arm_${k + 1}` : `arm_${k + 1}_${i + 1}`));
				addChain(b, {
					parent: 'head',
					names,
					start: [cx, cy, zHF + g],
					lengths: A.lengths.map((l, i) => l + (i === 0 ? g : 0)),
					widths: A.lengths.map((_, i) => taper(A.width, A.width * A.taper, i, A.lengths.length, g)),
					axis: [0, 0, -1],
					rotations: A.lengths.map((_, i) => (i === 0 ? rotation : [0, 0, 0])),
					role: 'arm',
					side: cx > 0.01 ? 'right' : cx < -0.01 ? 'left' : null,
					faces: () => ({ [inner]: 'sucker' }),
					boneMeta: () => ({ arm: k, theta }),
				});
			}
		}

		// Feeding tentacles: from the lower crown, longer and thinner, ending in a sucker club
		if (S.tentacles) {
			const T = S.tentacles;
			for (let t = 0; t < T.count; t++) {
				const sign = T.count === 1 ? 0 : t === 0 ? 1 : -1;
				const sideName = sign > 0 ? 'right' : sign < 0 ? 'left' : 'center';
				const x = sign * Math.max(g / 2, hw / 6);
				const y = hb + Math.max(g, Math.round(hh * 0.25));
				const names = T.lengths.map((_, i) => (i === 0 ? `${sideName}_tentacle` : `${sideName}_tentacle_${i + 1}`));
				const chain = addChain(b, {
					parent: 'head',
					names,
					start: [x, y, zHF + g],
					lengths: T.lengths.map((l, i) => l + (i === 0 ? g : 0)),
					widths: T.lengths.map(() => T.width),
					axis: [0, 0, -1],
					rotations: T.lengths.map((_, i) => (i === 0 ? [-6, sign * -8, 0] : [3, 0, 0])),
					role: 'tentacle',
					side: sign > 0 ? 'right' : sign < 0 ? 'left' : null,
					faces: () => ({ down: 'sucker' }),
				});
				const tipZ = zHF + g - T.lengths.reduce((a, l, i) => a + l + (i === 0 ? g : 0), 0);
				const club = `${sideName}_club`;
				b.bone(club, { parent: chain[chain.length - 1], pivot: [x, y, tipZ], role: 'club', side: sign > 0 ? 'right' : sign < 0 ? 'left' : null });
				b.box(club, club, { from: [x - T.clubWidth / 2, y - T.clubWidth / 2, tipZ - T.club], to: [x + T.clubWidth / 2, y + T.clubWidth / 2, tipZ + g], material: 'skin', faces: { down: 'sucker', west: sign > 0 ? 'sucker' : undefined, east: sign < 0 ? 'sucker' : undefined } });
			}
		}

		b.ground({ centerFilter: (c) => c.bone === 'head' || c.bone === 'body' || c.bone.startsWith('mantle'), snap: g });
		const geometry = b.build();
		geometry.root = root;
		return geometry;
	}

	animationCatalog() {
		return SQUID_ANIMATIONS;
	}
}

/** Face of an arm segment that looks toward the centre of the arm ring. */
function innerFace(theta) {
	const x = Math.cos(theta), y = Math.sin(theta);
	if (Math.abs(y) >= Math.abs(x)) return y > 0 ? 'down' : 'up';
	return x > 0 ? 'west' : 'east';
}

function armGroups(roles) {
	const arms = new Map();
	for (const bone of roles.all('arm')) {
		const k = bone.meta.arm;
		if (!arms.has(k)) arms.set(k, []);
		arms.get(k).push(bone);
	}
	return [...arms.values()].map((list) => list.sort((a, b) => a.meta.index - b.meta.index));
}

function tentacleGroups(roles) {
	const groups = { left: [], right: [], center: [] };
	for (const bone of roles.all('tentacle')) groups[bone.side || 'center'].push(bone);
	return Object.values(groups).filter((l) => l.length).map((l) => l.sort((a, b) => a.meta.index - b.meta.index));
}

export const SQUID_ANIMATIONS = {
	idle({ amp, make, roles }) {
		const c = make(4, 'loop', 'Hovering: arms curl slowly out of phase, fins ripple, mantle breathes');
		c.wave('body', 'scale', { amplitude: [0.04, 0.04, 0], cycles: 1 });
		c.wave('body', 'position', { amplitude: [0, 0.4 * amp, 0], cycles: 1, phase: 0.25 });
		for (const fin of roles.all('fin')) c.wave(fin.name, 'rotation', { amplitude: [0, 0, (fin.side === 'left' ? -1 : 1) * 12 * amp], cycles: 2 });
		armGroups(roles).forEach((arm, k, all) => {
			arm.forEach((bone, i) => {
				const a = (3 + i * 4) * amp;
				c.wave(bone.name, 'rotation', { amplitude: [a, a * 0.6, 0], cycles: 1, phase: k / all.length + i * 0.12 });
			});
		});
		tentacleGroups(roles).forEach((t, k) => t.forEach((bone, i) => c.wave(bone.name, 'rotation', { amplitude: [3 * amp, (4 + i * 3) * amp, 0], cycles: 1, phase: 0.5 * k + i * 0.15 })));
		return c.build();
	},
	swim({ amp, make, roles }) {
		const c = make(1.6, 'loop', 'Jet swimming: mantle pumps, arms gather and trail, fins beat');
		c.wave('body', 'scale', { amplitude: [-0.08, -0.08, 0.04], cycles: 1, shape: 'cos1' });
		for (const m of roles.all('mantle')) c.wave(m.name, 'scale', { amplitude: [-0.06, -0.06, 0], cycles: 1, shape: 'cos1', phase: -0.05 });
		for (const fin of roles.all('fin')) c.wave(fin.name, 'rotation', { amplitude: [0, 0, (fin.side === 'left' ? -1 : 1) * 25 * amp], cycles: 2 });
		armGroups(roles).forEach((arm) => {
			arm.forEach((bone, i) => {
				const theta = bone.meta.theta ?? 0;
				const s = (i === 0 ? 10 : 4) * amp;
				// Gather: rotate each arm back toward the crown axis (inverse of its splay)
				c.wave(bone.name, 'rotation', { amplitude: [-s * Math.sin(theta), s * Math.cos(theta), 0], cycles: 1, shape: 'cos1', phase: 0.05 * i });
			});
		});
		tentacleGroups(roles).forEach((t) => t.forEach((bone, i) => c.wave(bone.name, 'rotation', { amplitude: [2 * amp, 6 * amp, 0], cycles: 1, phase: -0.2 * i })));
		return c.build();
	},
	tentacle_move({ amp, make, roles }) {
		const tents = tentacleGroups(roles);
		if (!tents.length) return null;
		const c = make(3, 'loop', 'Feeding tentacles reach out, curl and flick their clubs');
		tents.forEach((t, k) => {
			const s = k === 0 ? 1 : -1;
			t.forEach((bone, i) => c.wave(bone.name, 'rotation', { amplitude: [(8 + i * 8) * amp, s * (6 + i * 6) * amp, 0], cycles: 1, phase: 0.25 * k + i * 0.12 }));
		});
		for (const club of roles.all('club')) c.wave(club.name, 'rotation', { amplitude: [25 * amp, 0, 0], cycles: 2, phase: 0.3 });
		armGroups(roles).forEach((arm, k, all) => arm.forEach((bone, i) => c.wave(bone.name, 'rotation', { amplitude: [2 * amp, 2 * amp, 0], cycles: 1, phase: k / all.length + i * 0.1 })));
		return c.build();
	},
	grab({ amp, make, roles }) {
		const tents = tentacleGroups(roles);
		if (!tents.length) return null;
		const c = make(1.2, 'once', 'Strike: tentacles shoot forward together and snap back');
		tents.forEach((t, k) => {
			const s = k === 0 ? 1 : -1;
			t.forEach((bone, i) => {
				c.keys(bone.name, 'rotation', [[0, [0, 0, 0]], [0.25, [-6 * amp, s * 6 * amp, 0]], [0.45, [0, 0, 0]], [0.8, [(10 + i * 10) * amp, -s * 8 * amp, 0]], [1.2, [0, 0, 0]]]);
			});
		});
		for (const club of roles.all('club')) c.keys(club.name, 'rotation', [[0, [0, 0, 0]], [0.45, [-30 * amp, 0, 0]], [0.8, [40 * amp, 0, 0]], [1.2, [0, 0, 0]]]);
		armGroups(roles).forEach((arm) => {
			const theta = arm[0].meta.theta ?? 0;
			const s = 14 * amp;
			c.keys(arm[0].name, 'rotation', [[0, [0, 0, 0]], [0.45, [s * Math.sin(theta), -s * Math.cos(theta), 0]], [0.9, [-s * 0.5 * Math.sin(theta), s * 0.5 * Math.cos(theta), 0]], [1.2, [0, 0, 0]]]);
		});
		return c.build();
	},
	hurt({ amp, make, roles }) {
		const c = make(0.6, 'once', 'Flinch: arms flare, mantle jerks');
		c.keys('body', 'rotation', [[0, [0, 0, 0]], [0.12, [8 * amp, 0, 10 * amp]], [0.3, [-4 * amp, 0, -6 * amp]], [0.6, [0, 0, 0]]]);
		armGroups(roles).forEach((arm) => {
			const theta = arm[0].meta.theta ?? 0;
			const s = 18 * amp;
			c.keys(arm[0].name, 'rotation', [[0, [0, 0, 0]], [0.15, [s * Math.sin(theta), -s * Math.cos(theta), 0]], [0.6, [0, 0, 0]]]);
		});
		return c.build();
	},
	death({ amp, make, roles }) {
		const c = make(2.2, 'hold', 'Arms and tentacles go limp and droop, the squid rolls and sinks');
		c.keys('body', 'rotation', [[0, [0, 0, 0]], [1.4, [-15, 0, 35]], [2.2, [-18, 0, 40]]]);
		c.keys('body', 'position', [[0, [0, 0, 0]], [2.2, [0, -3, 0]]], 'linear');
		c.keys('body', 'scale', [[0, [1, 1, 1]], [2.2, [0.92, 0.9, 1]]], 'linear');
		armGroups(roles).forEach((arm) => arm.forEach((bone, i) => c.keys(bone.name, 'rotation', [[0, [0, 0, 0]], [1.6, [-(10 + i * 8) * amp, 0, 0]], [2.2, [-(12 + i * 8) * amp, 0, 0]]])));
		tentacleGroups(roles).forEach((t) => t.forEach((bone, i) => c.keys(bone.name, 'rotation', [[0, [0, 0, 0]], [1.8, [-(8 + i * 6) * amp, 0, 0]], [2.2, [-(9 + i * 6) * amp, 0, 0]]])));
		return c.build();
	},
};
