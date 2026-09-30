// WormTemplate: a colony of tube-dwelling worms on a rock crust. Each worm is a segmented
// chitin tube (bone chain, so the colony can sway) with a white collar and a red feathery
// plume built from crossed planes. Positions and heights come from the seed.
// Covers the giant tubeworm (Riftia pachyptila) and similar tube colonies.

import { ModelBuilder } from '../generator/builder.js';
import { clamp } from '../generator/util.js';
import { CreatureTemplate } from './base.js';
import { roundedBox, splitLength } from './shared.js';

export class WormTemplate extends CreatureTemplate {
	static id = 'worm';
	static label = 'Worm (tube colony)';
	static description = 'Colony of chitin tubes on a rock base, each with a collar and a feathery plume; sways, retracts and extends.';

	defaults() {
		return {
			template: 'worm',
			category: 'worm',
			body: { length: 12, width: 12, height: 16 },
			parts: [],
			params: {
				colony: { count: 5, spread: 1, base: true, base_height: 2 },
				tube: { width: 2, height: 16, variance: 0.35, segments: 3, tilt: 8 },
				plume: { length: 5, width: 4, filaments: 2, collar: true },
			},
			palette: {},
			texture: { pattern: 'plain' },
			glow: [],
			animations: ['idle', 'sway', 'retract', 'extend', 'hurt', 'death'],
			animation: { speed: 1, amplitude: 1 },
			variation: { body: 0.08, tube: 0.12, plume: 0.12, hue: 5, lightness: 0.03 },
		};
	}

	editorSchema() {
		return [
			{
				section: 'Body Size (colony)',
				fields: [
					{ path: 'body.width', label: 'Base width', type: 'int', min: 4, max: 48 },
					{ path: 'body.length', label: 'Base length', type: 'int', min: 4, max: 48 },
					{ path: 'params.colony.count', label: 'Worms', type: 'int', min: 1, max: 16 },
					{ path: 'params.colony.base', label: 'Rock base', type: 'bool' },
				],
			},
			{
				section: 'Tail Length (tube)',
				fields: [
					{ path: 'params.tube.height', label: 'Tube height', type: 'int', min: 4, max: 64 },
					{ path: 'params.tube.variance', label: 'Height variance', type: 'range', min: 0, max: 0.6, step: 0.05 },
					{ path: 'params.tube.width', label: 'Tube width', type: 'int', min: 1, max: 6 },
					{ path: 'params.tube.segments', label: 'Segments', type: 'int', min: 1, max: 5 },
					{ path: 'params.tube.tilt', label: 'Tilt (°)', type: 'range', min: 0, max: 30, step: 1 },
				],
			},
			{
				section: 'Fin Size (plume)',
				fields: [
					{ path: 'params.plume.length', label: 'Plume height', type: 'int', min: 1, max: 16 },
					{ path: 'params.plume.width', label: 'Plume width', type: 'int', min: 1, max: 12 },
					{ path: 'params.plume.filaments', label: 'Crossed planes', type: 'int', min: 1, max: 4 },
					{ path: 'params.plume.collar', label: 'Collar', type: 'bool' },
				],
			},
		];
	}

	variationTargets() {
		return { body: ['body.width', 'body.length'], tube: ['params.tube.height'], plume: ['params.plume.length', 'params.plume.width'] };
	}

	exaggerationTargets() {
		return { fin: ['params.plume.length', 'params.plume.width'], limb: ['params.tube.height'] };
	}

	semanticParts(def, ctx) {
		const P = def.params;
		const has = (...n) => this.partEnabled(def, ...n);
		const g = ctx.grid;
		const list = [];
		const add = (p) => (list.push(p), p);
		const count = Math.max(1, Math.round(P.colony.count * ctx.lod([0.5, 0.75, 1])));
		const rng = ctx.rng.fork('colony');
		const baseW = ctx.sx(def.body.width), baseL = ctx.sz(def.body.length);
		const tw = ctx.s(P.tube.width);
		// Deterministic scattered positions (best-candidate sampling) inside the base
		const pts = [];
		for (let i = 0; i < count; i++) {
			let best = null;
			for (let k = 0; k < 16; k++) {
				const x = (rng.next() - 0.5) * (baseW - tw - 2 * g) * P.colony.spread;
				const z = (rng.next() - 0.5) * (baseL - tw - 2 * g) * P.colony.spread;
				const d = pts.reduce((m, p) => Math.min(m, Math.hypot(p.x - x, p.z - z)), Infinity);
				if (!best || d > best.d) best = { x, z, d };
			}
			pts.push({ x: Math.round(best.x / g) * g + (tw % (2 * g) ? g / 2 : 0), z: Math.round(best.z / g) * g + (tw % (2 * g) ? g / 2 : 0) });
		}
		const worms = pts.map((p, i) => {
			const r = rng.fork(`worm${i}`);
			const h = Math.max(3 * g, ctx.sy(P.tube.height * (1 + r.signed(clamp(P.tube.variance, 0, 0.6)))));
			const segs = Math.max(1, ctx.lod([1, Math.min(2, P.tube.segments), P.tube.segments]));
			return {
				index: i,
				x: p.x,
				z: p.z,
				height: h,
				segments: splitLength(h, segs, g),
				tilt: [r.signed(P.tube.tilt), 0, r.signed(P.tube.tilt)].map((v) => Math.round(v * 10) / 10),
				phase: r.next(),
			};
		});
		const S = { id: def.id, list };
		if (has('base', 'body') && P.colony.base) S.base = add({ id: 'base', kind: 'rock_base', size: [baseW, ctx.sy(P.colony.base_height || 2), baseL] });
		S.colony = add({ id: 'colony', kind: 'tube_colony', count, tubeWidth: tw, worms });
		if (has('plume', 'plumes')) S.plume = add({ id: 'plume', kind: 'branchial_plume', length: ctx.sy(P.plume.length), width: ctx.sx(P.plume.width), planes: Math.max(1, ctx.lod([1, 2, P.plume.filaments || 2])), collar: P.plume.collar && ctx.atLeast('medium') });
		return S;
	}

	build(S, ctx) {
		const b = new ModelBuilder();
		const g = ctx.grid;
		const root = S.id;
		b.bone(root, { pivot: [0, 0, 0], role: 'root' });
		b.bone('body', { parent: root, pivot: [0, 0, 0], role: 'body' });
		let yTop = 0;
		if (S.base) {
			const [w, h, l] = S.base.size;
			roundedBox(b, 'body', 'base', { from: [-w / 2, 0, -l / 2], to: [w / 2, h, l / 2], round: { top: g, x: g, front: g, back: g }, material: 'rock' });
			yTop = h;
		}
		const tw = S.colony.tubeWidth;
		for (const worm of S.colony.worms) {
			const id = worm.index + 1;
			let parent = 'body';
			let y = yTop - g;
			worm.segments.forEach((len, i) => {
				const name = i === 0 ? `worm_${id}` : `worm_${id}_${i + 1}`;
				b.bone(name, { parent, pivot: [worm.x, y, worm.z], rotation: i === 0 ? worm.tilt : [0, 0, 0], role: i === 0 ? 'tube' : 'tube_segment', meta: { worm: worm.index, index: i, phase: worm.phase } });
				b.box(name, name, { from: [worm.x - tw / 2, y - (i ? g : 0), worm.z - tw / 2], to: [worm.x + tw / 2, y + len + (i === 0 ? g : 0), worm.z + tw / 2], material: 'tube', priority: 1 });
				parent = name;
				y += len + (i === 0 ? g : 0);
			});
			if (S.plume) {
				const P = S.plume;
				const name = `plume_${id}`;
				b.bone(name, { parent, pivot: [worm.x, y - g, worm.z], role: 'plume', meta: { worm: worm.index, phase: worm.phase } });
				if (P.collar) b.box(name, `collar_${id}`, { from: [worm.x - tw / 2 - g / 2, y - g, worm.z - tw / 2 - g / 2], to: [worm.x + tw / 2 + g / 2, y, worm.z + tw / 2 + g / 2], material: 'tube', faces: { up: 'plume' }, priority: 2 });
				// Crossed feathery planes (like vanilla plants) around a solid core
				b.box(name, `plume_core_${id}`, { from: [worm.x - g / 2, y - g, worm.z - g / 2], to: [worm.x + g / 2, y + Math.max(g, P.length - g), worm.z + g / 2], material: 'plume', priority: 2 });
				for (let k = 0; k < P.planes; k++) {
					const angle = (180 / P.planes) * k + 45 * (worm.index % 2);
					b.box(name, `plume_${id}_${k + 1}`, {
						from: [worm.x - P.width / 2, y - g, worm.z],
						to: [worm.x + P.width / 2, y - g + P.length, worm.z],
						origin: [worm.x, y, worm.z],
						rotation: [0, angle, 0],
						material: 'plume',
						shape: { type: 'leaf', axis: 'y', dir: 1, across: 'x' },
						priority: k === 0 ? 1 : 2,
					});
				}
			}
		}
		b.ground({ centerFilter: (c) => c.bone === 'body', snap: g });
		const geometry = b.build();
		geometry.root = root;
		return geometry;
	}

	animationCatalog() {
		return WORM_ANIMATIONS;
	}
}

const tubes = (roles) => [...roles.all('tube'), ...roles.all('tube_segment')];

export const WORM_ANIMATIONS = {
	idle({ amp, make, roles }) {
		const c = make(4, 'loop', 'Plumes breathe and sway gently, tubes barely move');
		for (const p of roles.all('plume')) {
			c.wave(p.name, 'rotation', { amplitude: [5 * amp, 0, 4 * amp], cycles: 1, phase: p.meta.phase });
			c.wave(p.name, 'scale', { amplitude: [0.06, 0.08, 0.06], cycles: 2, phase: p.meta.phase });
		}
		for (const t of tubes(roles)) c.wave(t.name, 'rotation', { amplitude: [1.2 * amp, 0, 1.2 * amp], cycles: 1, phase: t.meta.phase + 0.1 * t.meta.index });
		return c.build();
	},
	sway({ amp, make, roles }) {
		const c = make(5, 'loop', 'Current sway: tubes bend together with a delay across the colony, plumes stream');
		for (const t of tubes(roles)) c.wave(t.name, 'rotation', { amplitude: [(3 + t.meta.index * 2) * amp, 0, (1.5 + t.meta.index) * amp], cycles: 1, phase: t.meta.phase * 0.35 + 0.08 * t.meta.index });
		for (const p of roles.all('plume')) c.wave(p.name, 'rotation', { amplitude: [12 * amp, 0, 6 * amp], cycles: 1, phase: p.meta.phase * 0.35 + 0.25 });
		return c.build();
	},
	retract({ make, roles, geometry }) {
		const plumes = roles.all('plume');
		if (!plumes.length) return null;
		const c = make(0.5, 'hold', 'Plumes snap back into the tubes');
		for (const p of plumes) {
			const depth = plumeDepth(geometry, p.name);
			c.keys(p.name, 'scale', [[0, [1, 1, 1]], [0.35, [0.3, 0.15, 0.3]], [0.5, [0.25, 0.1, 0.25]]]);
			c.keys(p.name, 'position', [[0, [0, 0, 0]], [0.35, [0, -depth, 0]], [0.5, [0, -depth, 0]]]);
		}
		return c.build();
	},
	extend({ make, roles, geometry }) {
		const plumes = roles.all('plume');
		if (!plumes.length) return null;
		const c = make(1.2, 'once', 'Plumes slowly unfurl out of the tubes');
		for (const p of plumes) {
			const depth = plumeDepth(geometry, p.name);
			const d = p.meta.phase * 0.3;
			c.keys(p.name, 'scale', [[0, [0.25, 0.1, 0.25]], [d, [0.25, 0.1, 0.25]], [Math.min(1.2, d + 0.8), [1.05, 1.08, 1.05]], [1.2, [1, 1, 1]]]);
			c.keys(p.name, 'position', [[0, [0, -depth, 0]], [d, [0, -depth, 0]], [Math.min(1.2, d + 0.8), [0, 0, 0]], [1.2, [0, 0, 0]]]);
		}
		return c.build();
	},
	hurt({ amp, make, roles, geometry }) {
		const c = make(0.8, 'once', 'Flinch: plumes duck and pop back out');
		for (const p of roles.all('plume')) {
			const depth = plumeDepth(geometry, p.name);
			c.keys(p.name, 'scale', [[0, [1, 1, 1]], [0.15, [0.35, 0.2, 0.35]], [0.8, [1, 1, 1]]]);
			c.keys(p.name, 'position', [[0, [0, 0, 0]], [0.15, [0, -depth * 0.8, 0]], [0.8, [0, 0, 0]]]);
		}
		for (const t of roles.all('tube')) c.keys(t.name, 'rotation', [[0, [0, 0, 0]], [0.15, [4 * amp, 0, -3 * amp]], [0.8, [0, 0, 0]]]);
		return c.build();
	},
	death({ make, roles }) {
		const c = make(2, 'hold', 'Plumes wilt and droop, tubes lean');
		for (const p of roles.all('plume')) c.keys(p.name, 'rotation', [[0, [0, 0, 0]], [2, [35 * (p.meta.phase > 0.5 ? 1 : -1), 0, 20]]], 'linear');
		for (const p of roles.all('plume')) c.keys(p.name, 'scale', [[0, [1, 1, 1]], [2, [0.8, 0.6, 0.8]]], 'linear');
		for (const t of roles.all('tube')) c.keys(t.name, 'rotation', [[0, [0, 0, 0]], [2, [6 * (t.meta.phase - 0.5), 0, 8 * (t.meta.phase - 0.5)]]], 'linear');
		return c.build();
	},
};

/** How far a plume must sink to disappear into its tube (its own height). */
function plumeDepth(geometry, boneName) {
	let max = 0;
	for (const c of geometry.cubes) if (c.bone === boneName) max = Math.max(max, c.to[1] - c.from[1]);
	return Math.max(1, max - 1);
}
