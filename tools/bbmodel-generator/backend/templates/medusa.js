// MedusaTemplate: the free-swimming medusa of jellyfish and hydromedusae: a stepped, translucent bell
// (dome, flat disc or tall helmet) with the gut showing through, an optional coronal groove split into
// sectors (so a light display can run around the bell), marginal lappets, a ring of marginal tentacles,
// an optional hypertrophied trailing tentacle and ribbon-like oral arms. The bell axis is vertical: the
// animal "faces" up. Covers Colobonema, Atolla, Periphylla and Stygiomedusa.

import { ModelBuilder } from '../generator/builder.js';
import { clamp } from '../generator/util.js';
import { CreatureTemplate } from './base.js';
import { addChain, taper } from './parts.js';
import { splitLength } from './shared.js';

/** Bell profiles: how much narrower the top tier is than the rim, and how fast it narrows. */
const PROFILES = {
	dome: { top: 0.45, power: 1.8 },
	disc: { top: 0.5, power: 1.2 },
	helmet: { top: 0.3, power: 0.9 },
};

export class MedusaTemplate extends CreatureTemplate {
	static id = 'medusa';
	static label = 'Medusa (jellyfish)';
	static description = 'Translucent stepped bell with the gut showing through, coronal groove sectors, lappets, a tentacle ring, a long trailing tentacle and ribbon oral arms; pulses the bell.';

	defaults() {
		return {
			template: 'medusa',
			category: 'jellyfish',
			body: { width: 10, height: 7 },
			parts: [],
			params: {
				bell: { profile: 'dome', tiers: 3, alpha: 120 },
				stomach: { enabled: true, width: 4, height: 3 },
				groove: { enabled: false, sectors: 8, height: 1, at: 0.35 },
				lappets: { enabled: false, height: 2 },
				tentacles: { count: 8, length: 10, segments: 2, width: 1, spread: 20 },
				long_tentacle: { enabled: false, length: 24, segments: 3 },
				oral_arms: { count: 0, length: 16, width: 3, segments: 3 },
			},
			palette: {},
			texture: { pattern: 'none', pattern_strength: 0 },
			glow: [],
			animations: ['idle', 'swim', 'pulse', 'spread', 'hurt', 'death'],
			animation: { speed: 1, amplitude: 1 },
			variation: { bell: 0.08, tentacles: 0.12, hue: 6, lightness: 0.04 },
		};
	}

	editorSchema() {
		return [
			{
				section: 'Body Size',
				fields: [
					{ path: 'body.width', label: 'Bell width', type: 'int', min: 4, max: 48 },
					{ path: 'body.height', label: 'Bell height', type: 'int', min: 2, max: 32 },
					{ path: 'params.bell.tiers', label: 'Bell tiers', type: 'int', min: 1, max: 6 },
					{ path: 'params.bell.alpha', label: 'Bell alpha', type: 'int', min: 40, max: 255 },
				],
			},
			{
				section: 'Tentacle Count',
				fields: [
					{ path: 'params.tentacles.count', label: 'Marginal tentacles', type: 'int', min: 0, max: 32 },
					{ path: 'params.tentacles.length', label: 'Tentacle length', type: 'int', min: 1, max: 64 },
					{ path: 'params.tentacles.spread', label: 'Tentacle spread (°)', type: 'range', min: 0, max: 100, step: 1 },
					{ path: 'params.long_tentacle.length', label: 'Trailing tentacle length', type: 'int', min: 1, max: 96 },
					{ path: 'params.oral_arms.count', label: 'Oral arms', type: 'int', min: 0, max: 8 },
					{ path: 'params.oral_arms.length', label: 'Oral arm length', type: 'int', min: 1, max: 160 },
				],
			},
		];
	}

	variationTargets() {
		return { bell: ['body.width', 'body.height'], tentacles: ['params.tentacles.length', 'params.oral_arms.length'] };
	}

	exaggerationTargets() {
		return { limb: ['params.tentacles.length', 'params.long_tentacle.length'] };
	}

	semanticParts(def, ctx) {
		const P = def.params;
		const has = (...n) => this.partEnabled(def, ...n);
		const g = ctx.grid;
		const S = { id: def.id, list: [] };
		const add = (p) => (S.list.push(p), p);
		// widths stay even multiples of the grid so every tier is centred on the axis
		const even = (v) => Math.max(2 * g, Math.round(v / (2 * g)) * 2 * g);
		const W = even(def.body.width * ctx.scale[0]);
		const H = ctx.sy(def.body.height);
		const profile = PROFILES[P.bell?.profile] || PROFILES.dome;
		const n = Math.max(1, Math.min(ctx.lod([2, 3, P.bell?.tiers ?? 3]), Math.round(H / g)));
		const heights = splitLength(H, n, g);
		S.bell = add({
			id: 'bell',
			kind: 'bell',
			alpha: clamp(P.bell?.alpha ?? 120, 40, 255),
			tiers: heights.map((h, i) => ({ height: h, width: even(W * (1 - (1 - profile.top) * Math.pow(n <= 1 ? 0 : i / (n - 1), profile.power))) })),
		});
		if (has('stomach') && P.stomach?.enabled) S.stomach = add({ id: 'stomach', kind: 'gut', width: even(P.stomach.width * ctx.scale[0]), height: ctx.sy(P.stomach.height) });
		if (has('groove') && P.groove?.enabled) {
			const sectors = Math.max(4, Math.round((P.groove.sectors ?? 8) / 4) * 4);
			S.groove = add({ id: 'groove', kind: 'coronal_groove', sectors, height: ctx.sy(P.groove.height ?? 1), at: clamp(P.groove.at ?? 0.35, 0, 0.9) });
		}
		if (has('lappets') && P.lappets?.enabled && ctx.atLeast('medium')) S.lappets = add({ id: 'lappets', kind: 'lappets', height: ctx.sy(P.lappets.height ?? 2) });
		if (has('tentacles') && P.tentacles?.count > 0) {
			const T = P.tentacles;
			const count = Math.max(4, Math.round((T.count * ctx.lod([0.5, 0.75, 1])) / 4) * 4);
			const segs = Math.max(1, Math.min(ctx.lod([1, 2, T.segments ?? 2]), 4));
			S.tentacles = add({ id: 'tentacles', kind: 'marginal_tentacles', count, width: ctx.s(T.width ?? 1), spread: T.spread ?? 20, lengths: splitLength(ctx.s(T.length), segs, g) });
		}
		if (has('long_tentacle') && P.long_tentacle?.enabled) {
			const L = P.long_tentacle;
			S.longTentacle = add({ id: 'long_tentacle', kind: 'hypertrophied_tentacle', lengths: splitLength(ctx.s(L.length), Math.max(2, ctx.lod([2, 3, L.segments ?? 3])), g) });
		}
		if (has('oral_arms') && P.oral_arms?.count > 0) {
			const A = P.oral_arms;
			S.oralArms = add({ id: 'oral_arms', kind: 'oral_arms', count: A.count, width: ctx.sx(A.width), lengths: splitLength(ctx.s(A.length), Math.max(2, ctx.lod([2, 3, A.segments ?? 3])), g) });
		}
		return S;
	}

	build(S, ctx) {
		const b = new ModelBuilder();
		const g = ctx.grid;
		const root = S.id;
		b.bone(root, { pivot: [0, 0, 0], role: 'root' });

		// Bell: stacked square tiers, narrowing upward; drawn translucent, so the gut shows through
		const rim = S.bell.tiers[0];
		const H = S.bell.tiers.reduce((a, t) => a + t.height, 0);
		b.bone('bell', { parent: root, pivot: [0, 0, 0], role: 'bell' });
		let y = 0;
		S.bell.tiers.forEach((t, i) => {
			b.box('bell', i === 0 ? 'bell' : `bell_${i + 1}`, {
				from: [-t.width / 2, y, -t.width / 2],
				to: [t.width / 2, y + t.height, t.width / 2],
				material: 'dome',
				meta: { alpha: S.bell.alpha },
			});
			y += t.height;
		});

		// Gut (stomach, gonads): opaque, inside the bell above the mouth
		if (S.stomach) {
			const s = S.stomach;
			const w = Math.min(s.width, rim.width - 2 * g);
			const h = Math.min(s.height, H - 2 * g);
			b.bone('stomach', { parent: 'bell', pivot: [0, g, 0], role: 'stomach' });
			b.box('stomach', 'stomach', { from: [-w / 2, g, -w / 2], to: [w / 2, g + h, w / 2], material: 'flesh', priority: 2 });
		}

		// Coronal groove: a band around the rim tier, one bone per sector (clockwise seen from above,
		// starting at the front-right), so the mod can light the sectors one after another
		if (S.groove) {
			const G = S.groove;
			const gy = Math.min(rim.height - G.height, Math.round((rim.height * G.at) / g) * g);
			const half = rim.width / 2 + 0.05;
			const perSide = G.sectors / 4;
			const len = rim.width / perSide;
			// sides in clockwise order seen from above (north = front = -Z): north, east, south, west
			const sides = [
				{ axis: 'z', fixed: -half, along: 0, dir: 1 },
				{ axis: 'x', fixed: half, along: 2, dir: 1 },
				{ axis: 'z', fixed: half, along: 0, dir: -1 },
				{ axis: 'x', fixed: -half, along: 2, dir: -1 },
			];
			let k = 0;
			for (const side of sides) {
				for (let j = 0; j < perSide; j++) {
					const a0 = side.dir > 0 ? -rim.width / 2 + j * len : rim.width / 2 - (j + 1) * len;
					const from = [0, gy, 0], to = [0, gy + G.height, 0];
					const fixedAxis = side.axis === 'z' ? 2 : 0;
					from[fixedAxis] = to[fixedAxis] = side.fixed;
					from[side.along] = a0;
					to[side.along] = a0 + len;
					const name = `groove_${++k}`;
					b.bone(name, { parent: 'bell', pivot: [(from[0] + to[0]) / 2, gy, (from[2] + to[2]) / 2], role: 'groove', meta: { index: k - 1, count: G.sectors } });
					b.box(name, name, { from, to, material: 'membrane', meta: { alpha: 255 } });
				}
			}
		}

		// Lappets: a scalloped skirt of flaps hanging from the rim, flared a little outward
		if (S.lappets) {
			const h = S.lappets.height;
			const half = rim.width / 2 + 0.05;
			for (const [side, axis, sign] of [['front', 'z', -1], ['right', 'x', 1], ['back', 'z', 1], ['left', 'x', -1]]) {
				const name = `lappets_${side}`;
				const pivot = axis === 'z' ? [0, 0, sign * half] : [sign * half, 0, 0];
				const rotation = axis === 'z' ? [sign * -18, 0, 0] : [0, 0, sign * 18];
				b.bone(name, { parent: 'bell', pivot, rotation, role: 'lappet', side: axis === 'x' ? (sign > 0 ? 'right' : 'left') : null });
				const from = axis === 'z' ? [-rim.width / 2, -h, sign * half] : [sign * half, -h, -rim.width / 2];
				const to = axis === 'z' ? [rim.width / 2, 0, sign * half] : [sign * half, 0, rim.width / 2];
				b.box(name, name, {
					from,
					to,
					material: 'membrane',
					shape: { type: 'frill', axis: 'y', dir: -1, across: axis === 'z' ? 'x' : 'z', waves: Math.max(2, Math.round(rim.width / (3 * g))) },
					meta: { alpha: Math.min(255, S.bell.alpha + 60) },
					priority: 3,
				});
			}
		}

		// Marginal tentacles: a ring just inside the rim, hanging down and splayed outward
		if (S.tentacles) {
			const T = S.tentacles;
			const r = rim.width / 2 - T.width / 2;
			for (let k = 0; k < T.count; k++) {
				const theta = (2 * Math.PI * (k + 0.5)) / T.count;
				// round to the square rim: push the point out to the rim's edge
				const cx = Math.cos(theta), cz = Math.sin(theta);
				const m = Math.max(Math.abs(cx), Math.abs(cz));
				const x = Math.round(((cx / m) * r) / (g / 2)) * (g / 2);
				const z = Math.round(((cz / m) * r) / (g / 2)) * (g / 2);
				const names = T.lengths.map((_, i) => (i === 0 ? `tentacle_${k + 1}` : `tentacle_${k + 1}_s${i + 1}`));
				addChain(b, {
					parent: 'bell',
					names,
					start: [x, 0, z],
					lengths: T.lengths,
					widths: T.lengths.map(() => T.width),
					axis: [0, -1, 0],
					rotations: T.lengths.map((_, i) => (i === 0 ? splay(theta, T.spread) : splay(theta, -T.spread * 0.35))),
					role: 'tentacle',
					material: 'flesh',
					meta: () => ({ alpha: Math.min(255, S.bell.alpha + 80) }),
					boneMeta: () => ({ ring: k, theta }),
					priority: (i) => (i === 0 ? 2 : 3),
				});
			}
		}

		// Hypertrophied tentacle (Atolla): one much longer, thicker tentacle trailing from the rim
		if (S.longTentacle) {
			const L = S.longTentacle;
			const names = L.lengths.map((_, i) => (i === 0 ? 'long_tentacle' : `long_tentacle_${i + 1}`));
			addChain(b, {
				parent: 'bell',
				names,
				start: [0, 0, rim.width / 2 - g],
				lengths: L.lengths,
				widths: L.lengths.map((_, i) => taper(2 * g, g, i, L.lengths.length, g)),
				axis: [0, -1, 0],
				rotations: L.lengths.map((_, i) => (i === 0 ? [-25, 0, 0] : [12, 0, 0])),
				role: 'long_tentacle',
				material: 'flesh',
				boneMeta: () => ({ theta: Math.PI / 2 }),
			});
		}

		// Oral arms: broad, thin ribbons (single planes) hanging from the centre of the subumbrella
		if (S.oralArms) {
			const A = S.oralArms;
			const d = Math.max(g, Math.round(rim.width / 6 / g) * g);
			for (let k = 0; k < A.count; k++) {
				const theta = (2 * Math.PI * k) / A.count + Math.PI / 4;
				const x = Math.round((Math.cos(theta) * d) / (g / 2)) * (g / 2);
				const z = Math.round((Math.sin(theta) * d) / (g / 2)) * (g / 2);
				// each ribbon faces outward: a plane across the direction it hangs from
				const plane = Math.abs(Math.cos(theta)) > Math.abs(Math.sin(theta)) ? 'x' : 'z';
				const names = A.lengths.map((_, i) => (i === 0 ? `oral_arm_${k + 1}` : `oral_arm_${k + 1}_s${i + 1}`));
				const n = A.lengths.length;
				addChain(b, {
					parent: 'bell',
					names,
					start: [x, g, z],
					lengths: A.lengths.map((l, i) => l + (i === 0 ? g : 0)),
					widths: A.lengths.map((_, i) => (plane === 'x' ? [0, 0, taper(A.width, A.width * 0.7, i, n, g)] : [taper(A.width, A.width * 0.7, i, n, g), 0, 0])),
					axis: [0, -1, 0],
					rotations: A.lengths.map((_, i) => (i === 0 ? splay(theta, 6) : [0, 0, 0])),
					role: 'oral_arm',
					material: 'membrane',
					meta: () => ({ alpha: Math.min(255, S.bell.alpha + 40) }),
					// one continuous ribbon: only the last segment narrows to a tip
					shape: (i) => (i === n - 1 ? { type: 'taper', axis: 'y', dir: -1, across: plane === 'x' ? 'z' : 'x' } : null),
					boneMeta: () => ({ ring: k, theta }),
				});
			}
		}

		// Stand the bell on the entity origin; tentacles and arms hang below it (outside the hitbox)
		b.ground({ centerFilter: (c) => c.bone === 'bell', snap: g });
		const lift = -b.bounds((c) => c.bone === 'bell').min[1];
		b.translate([0, lift, 0]);
		b.getBone(root).pivot = [0, 0, 0];
		const geometry = b.build();
		geometry.root = root;
		return geometry;
	}

	animationCatalog() {
		return MEDUSA_ANIMATIONS;
	}
}

/**
 * Rest rotation (ZYX degrees) that tips a downward-hanging part `deg` outward, toward the
 * direction `theta` around the bell axis (theta = 0 is +X, pi/2 is +Z).
 */
function splay(theta, deg) {
	return [Math.round(-deg * Math.sin(theta) * 10) / 10, 0, Math.round(deg * Math.cos(theta) * 10) / 10];
}

function chains(roles, role) {
	const groups = new Map();
	for (const bone of roles.all(role)) {
		const k = bone.meta.ring ?? 0;
		if (!groups.has(k)) groups.set(k, []);
		groups.get(k).push(bone);
	}
	return [...groups.values()].map((l) => l.sort((a, b) => a.meta.index - b.meta.index));
}

/** Rotation that swings a hanging chain bone outward (+) / inward (-) by `deg` around the bell axis. */
const outward = (bone, deg) => splay(bone.meta.theta ?? 0, deg);

/** Contraction of the bell: narrower and a little taller, like a real bell stroke. */
function contract(c, t0, t1, t2, amount) {
	c.keys('bell', 'scale', [[t0, [1, 1, 1]], [t1, [1 - amount, 1 + amount * 0.45, 1 - amount]], [t2, [1, 1, 1]]]);
}

export const MEDUSA_ANIMATIONS = {
	idle({ amp, make, roles }) {
		const c = make(6, 'loop', 'Drifting: the bell breathes slowly, tentacles and arms sway out of phase');
		c.wave('bell', 'scale', { amplitude: [-0.04 * amp, 0.02 * amp, -0.04 * amp], cycles: 1, shape: 'cos1' });
		chains(roles, 'tentacle').forEach((t, k, all) => t.forEach((bone, i) => {
			const a = (3 + 3 * i) * amp;
			c.wave(bone.name, 'rotation', { amplitude: outward(bone, a), cycles: 1, phase: k / all.length + 0.1 * i });
		}));
		for (const bone of roles.all('long_tentacle')) c.wave(bone.name, 'rotation', { amplitude: [(4 + 3 * (bone.meta.index ?? 0)) * amp, (3 + 2 * (bone.meta.index ?? 0)) * amp, 0], cycles: 1, phase: 0.15 * (bone.meta.index ?? 0) });
		chains(roles, 'oral_arm').forEach((arm, k) => arm.forEach((bone, i) => c.wave(bone.name, 'rotation', { amplitude: [(2 + 2 * i) * amp, 0, (2 + 2 * i) * amp], cycles: 1, phase: 0.25 * k + 0.18 * i })));
		for (const l of roles.all('lappet')) c.wave(l.name, 'rotation', { amplitude: l.side ? [0, 0, (l.side === 'right' ? 1 : -1) * 3 * amp] : [(l.pivot[2] < 0 ? -1 : 1) * 3 * amp, 0, 0], cycles: 1, phase: 0.2 });
		return c.build();
	},
	swim({ amp, make, roles }) {
		const c = make(0.8, 'loop', 'Escape: rapid bell strokes, tentacles swept back together');
		c.wave('bell', 'scale', { amplitude: [-0.22 * amp, 0.1 * amp, -0.22 * amp], cycles: 1, shape: 'cos1' });
		chains(roles, 'tentacle').forEach((t) => t.forEach((bone, i) => c.wave(bone.name, 'rotation', { amplitude: outward(bone, -(6 + 4 * i) * amp), cycles: 1, shape: 'cos1', phase: -0.1 * (i + 1) })));
		for (const l of roles.all('lappet')) c.wave(l.name, 'rotation', { amplitude: l.side ? [0, 0, (l.side === 'right' ? -1 : 1) * 14 * amp] : [(l.pivot[2] < 0 ? -1 : 1) * 14 * amp, 0, 0], cycles: 1, shape: 'cos1' });
		return c.build();
	},
	pulse({ amp, make, roles }) {
		const c = make(1.4, 'once', 'One bell stroke: a quick contraction, a slow relaxation; tentacles gather then trail');
		contract(c, 0, 0.3, 1.4, 0.2 * amp);
		chains(roles, 'tentacle').forEach((t) => t.forEach((bone, i) => {
			const d = (8 + 4 * i) * amp;
			const o = outward(bone, -d);
			c.keys(bone.name, 'rotation', [[0, [0, 0, 0]], [0.4 + 0.1 * i, o], [1.4, [0, 0, 0]]]);
		}));
		chains(roles, 'oral_arm').forEach((arm) => arm.forEach((bone, i) => c.keys(bone.name, 'rotation', [[0, [0, 0, 0]], [0.5 + 0.15 * i, outward(bone, -(3 + 2 * i) * amp)], [1.4, [0, 0, 0]]])));
		for (const l of roles.all('lappet')) {
			const v = l.side ? [0, 0, (l.side === 'right' ? -1 : 1) * 16 * amp] : [(l.pivot[2] < 0 ? -1 : 1) * 16 * amp, 0, 0];
			c.keys(l.name, 'rotation', [[0, [0, 0, 0]], [0.3, v], [1.4, [0, 0, 0]]]);
		}
		return c.build();
	},
	spread({ amp, make, roles }) {
		if (!roles.all('tentacle').length && !roles.all('long_tentacle').length) return null;
		const c = make(0.8, 'hold', 'Defence: tentacles flare out wide and stiffen, the bell swells');
		c.keys('bell', 'scale', [[0, [1, 1, 1]], [0.8, [1.06, 0.96, 1.06]]]);
		chains(roles, 'tentacle').forEach((t) => t.forEach((bone, i) => c.keys(bone.name, 'rotation', [[0, [0, 0, 0]], [0.8, outward(bone, (i === 0 ? 45 : 10) * amp)]])));
		for (const bone of roles.all('long_tentacle')) c.keys(bone.name, 'rotation', [[0, [0, 0, 0]], [0.8, [(bone.meta.index ? -8 : -25) * amp, 0, 0]]]);
		return c.build();
	},
	hurt({ amp, make, roles }) {
		const c = make(0.6, 'once', 'Flinch: a hard contraction, tentacles snap inward');
		contract(c, 0, 0.12, 0.6, 0.28 * amp);
		chains(roles, 'tentacle').forEach((t) => c.keys(t[0].name, 'rotation', [[0, [0, 0, 0]], [0.12, outward(t[0], -18 * amp)], [0.6, [0, 0, 0]]]));
		return c.build();
	},
	death({ amp, make, roles }) {
		const c = make(2.4, 'hold', 'The bell stops, sags and flattens; tentacles and arms go limp');
		c.keys('bell', 'scale', [[0, [1, 1, 1]], [2.4, [1.12, 0.7, 1.12]]], 'linear');
		c.keys('bell', 'rotation', [[0, [0, 0, 0]], [2.4, [14, 0, 10]]], 'linear');
		chains(roles, 'tentacle').forEach((t) => t.forEach((bone, i) => c.keys(bone.name, 'rotation', [[0, [0, 0, 0]], [2.4, outward(bone, -(6 + 4 * i) * amp)]], 'linear')));
		chains(roles, 'oral_arm').forEach((arm) => arm.forEach((bone, i) => c.keys(bone.name, 'rotation', [[0, [0, 0, 0]], [2.4, [(4 + 3 * i) * amp, 0, 0]]], 'linear')));
		return c.build();
	},
};
