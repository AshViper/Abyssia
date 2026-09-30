// SoftBodyTemplate: gelatinous swimming holothurian (sea cucumber) and similar soft bodies:
// a rounded, gently undulating body chain with a webbed veil at the front, a sail-like brim at
// the back, oral tentacles and tube feet. Covers Enypniastes eximia (the "headless chicken
// monster" / ユメナマコ).

import { ModelBuilder } from '../generator/builder.js';
import { clamp } from '../generator/util.js';
import { CreatureTemplate } from './base.js';
import { taper } from './parts.js';
import { roundedBox, splitLength, spread } from './shared.js';

export class SoftBodyTemplate extends CreatureTemplate {
	static id = 'softbody';
	static label = 'Soft Body (sea cucumber)';
	static description = 'Gelatinous body chain with a webbed front veil, rear brim, oral tentacles and tube feet; swims by flapping the veil.';

	defaults() {
		return {
			template: 'softbody',
			category: 'echinoderm',
			body: { length: 14, width: 7, height: 6, segments: 3, taper: 0.75 },
			parts: [],
			params: {
				veil: { enabled: true, width: 10, height: 7, lean: 35 },
				brim: { enabled: true, width: 7, height: 4 },
				tentacles: { count: 6, length: 2 },
				feet: { count: 4 },
				translucent: false,
			},
			palette: {},
			texture: { pattern: 'mottled', pattern_strength: 0.5 },
			glow: [],
			animations: ['idle', 'swim', 'hurt', 'death'],
			animation: { speed: 1, amplitude: 1 },
			variation: { body: 0.08, veil: 0.12, brim: 0.12, hue: 8, lightness: 0.04 },
		};
	}

	editorSchema() {
		return [
			{
				section: 'Body Size',
				fields: [
					{ path: 'body.length', label: 'Length', type: 'int', min: 4, max: 48 },
					{ path: 'body.width', label: 'Width', type: 'int', min: 2, max: 24 },
					{ path: 'body.height', label: 'Height', type: 'int', min: 2, max: 24 },
					{ path: 'body.segments', label: 'Segments', type: 'int', min: 1, max: 6 },
					{ path: 'params.translucent', label: 'Translucent flesh', type: 'bool' },
				],
			},
			{
				section: 'Fin Size (veil / brim)',
				fields: [
					{ path: 'params.veil.width', label: 'Veil width', type: 'int', min: 1, max: 32 },
					{ path: 'params.veil.height', label: 'Veil height', type: 'int', min: 1, max: 24 },
					{ path: 'params.veil.lean', label: 'Veil lean (°)', type: 'range', min: -30, max: 80, step: 1 },
					{ path: 'params.brim.height', label: 'Brim height', type: 'int', min: 1, max: 16 },
				],
			},
			{
				section: 'Tentacle Count',
				fields: [
					{ path: 'params.tentacles.count', label: 'Oral tentacles', type: 'int', min: 0, max: 12 },
					{ path: 'params.tentacles.length', label: 'Tentacle length', type: 'int', min: 1, max: 8 },
					{ path: 'params.feet.count', label: 'Tube feet / side', type: 'int', min: 0, max: 8 },
				],
			},
		];
	}

	variationTargets() {
		return { body: ['body.length', 'body.width', 'body.height'], veil: ['params.veil.width', 'params.veil.height'], brim: ['params.brim.height'] };
	}

	exaggerationTargets() {
		return { fin: ['params.veil.width', 'params.veil.height', 'params.brim.height'], limb: ['params.tentacles.length'] };
	}

	semanticParts(def, ctx) {
		const P = def.params;
		const has = (...n) => this.partEnabled(def, ...n);
		const g = ctx.grid;
		const list = [];
		const add = (p) => (list.push(p), p);
		const n = Math.max(1, ctx.lod([1, Math.min(2, def.body.segments), def.body.segments]));
		const tp = clamp(def.body.taper ?? 0.75, 0.3, 1);
		const S = { id: def.id, list, translucent: !!P.translucent };
		S.body = add({
			id: 'body',
			kind: 'soft_body',
			segments: splitLength(ctx.sz(def.body.length), n, g).map((length, i) => ({ length, width: taper(def.body.width * ctx.scale[0], def.body.width * ctx.scale[0] * tp, i, n, g), height: taper(def.body.height * ctx.scale[1], def.body.height * ctx.scale[1] * tp, i, n, g) })),
		});
		if (has('veil') && P.veil?.enabled) S.veil = add({ id: 'veil', kind: 'velum', width: ctx.sx(P.veil.width), height: ctx.sy(P.veil.height), lean: P.veil.lean ?? 35 });
		if (has('brim') && P.brim?.enabled) S.brim = add({ id: 'brim', kind: 'brim', width: ctx.sx(P.brim.width), height: ctx.sy(P.brim.height) });
		if (has('tentacles') && P.tentacles?.count > 0 && ctx.atLeast('medium')) S.tentacles = add({ id: 'tentacles', kind: 'oral_tentacles', count: Math.round(P.tentacles.count * ctx.lod([0, 0.5, 1])), length: ctx.s(P.tentacles.length) });
		if (has('feet') && P.feet?.count > 0 && ctx.atLeast('high')) S.feet = add({ id: 'feet', kind: 'tube_feet', count: P.feet.count });
		return S;
	}

	build(S, ctx) {
		const b = new ModelBuilder();
		const g = ctx.grid;
		const root = S.id;
		const y0 = 16;
		b.bone(root, { pivot: [0, 0, 0], role: 'root' });
		let parent = root;
		let z = 0;
		const segs = [];
		const flesh = S.translucent ? { alpha: 200 } : {};
		S.body.segments.forEach((seg, i) => {
			const name = i === 0 ? 'body' : `body_${i + 1}`;
			const bottom = y0 - Math.floor(seg.height / 2 / g) * g;
			b.bone(name, { parent, pivot: [0, y0, z], role: i === 0 ? 'body' : 'segment', meta: { index: i } });
			roundedBox(b, name, name, {
				from: [-seg.width / 2, bottom, z - (i ? g : 0)],
				to: [seg.width / 2, bottom + seg.height, z + seg.length],
				round: { top: g, bottom: g, x: g, front: i === 0 ? g : 0, back: i === S.body.segments.length - 1 ? g : 0 },
				material: 'flesh',
				faces: { down: 'flesh' },
				meta: flesh,
			});
			segs.push({ name, z0: z, z1: z + seg.length, seg, bottom, top: bottom + seg.height });
			parent = name;
			z += seg.length;
		});
		const first = segs[0], last = segs[segs.length - 1];

		// Webbed veil: a sail rising from the front top, leaning forward
		if (S.veil) {
			const V = S.veil;
			b.bone('veil', { parent: 'body', pivot: [0, first.top - g, first.z0 + g], rotation: [-V.lean, 0, 0], role: 'veil' });
			b.box('veil', 'veil', {
				from: [-V.width / 2, first.top - g, first.z0 + g],
				to: [V.width / 2, first.top - g + V.height, first.z0 + g],
				material: 'membrane',
				shape: { type: 'sail', axis: 'y', dir: 1, across: 'x' },
				meta: { alpha: S.translucent ? 190 : 255 },
			});
		}
		// Posterior brim: a smaller sail at the back
		if (S.brim) {
			const B = S.brim;
			b.bone('brim', { parent: last.name, pivot: [0, last.top - g, last.z1 - g], rotation: [25, 0, 0], role: 'brim' });
			b.box('brim', 'brim', {
				from: [-B.width / 2, last.top - g, last.z1 - g],
				to: [B.width / 2, last.top - g + B.height, last.z1 - g],
				material: 'membrane',
				shape: { type: 'sail', axis: 'y', dir: 1, across: 'x' },
				meta: { alpha: S.translucent ? 190 : 255 },
			});
		}
		// Oral tentacles fringe under the front
		if (S.tentacles && S.tentacles.count > 0) {
			const T = S.tentacles;
			b.bone('tentacles', { parent: 'body', pivot: [0, first.bottom + g, first.z0 + g], rotation: [45, 0, 0], role: 'tentacles' });
			const xs = spread(T.count, first.seg.width - 2 * g, g);
			xs.forEach((x, i) => {
				b.box('tentacles', 'oral_tentacle', { from: [x - g / 2, first.bottom - T.length + g, first.z0 + (i % 2 ? g : 0)], size: [g, T.length, g], material: 'flesh', meta: flesh, priority: 3 });
			});
		}
		// Tube feet (podia) along the lower flanks
		if (S.feet) {
			for (const s of segs) {
				const count = Math.max(1, Math.round((S.feet.count * s.seg.length) / (last.z1 - first.z0)));
				for (let i = 0; i < count; i++) {
					const fz = s.z0 + ((i + 0.5) * s.seg.length) / count;
					for (const sign of [1, -1]) {
						const x = sign * (s.seg.width / 2 + g / 2);
						b.box(s.name, 'tube_foot', { from: [x - g / 2, s.bottom, Math.round(fz) - g / 2], size: [g, g, g], material: 'flesh', meta: flesh, priority: 3 });
					}
				}
			}
		}
		b.ground({ centerFilter: (c) => c.bone === 'body' || c.bone.startsWith('body_'), snap: g });
		const geometry = b.build();
		geometry.root = root;
		return geometry;
	}

	animationCatalog() {
		return SOFTBODY_ANIMATIONS;
	}
}

const chain = (roles) => [roles.one('body'), ...roles.all('segment')].filter(Boolean);

export const SOFTBODY_ANIMATIONS = {
	idle({ amp, make, roles }) {
		const c = make(4, 'loop', 'Drifting: veil ripples, body pulses, tentacles sift');
		chain(roles).forEach((s, i) => c.wave(s.name, 'scale', { amplitude: [0.04, 0.05, 0], cycles: 1, phase: -0.15 * i }));
		c.wave('body', 'position', { amplitude: [0, 0.6 * amp, 0], cycles: 1 });
		if (roles.one('veil')) c.wave('veil', 'rotation', { amplitude: [8 * amp, 0, 0], cycles: 1, phase: 0.1 });
		if (roles.one('brim')) c.wave('brim', 'rotation', { amplitude: [6 * amp, 0, 0], cycles: 1, phase: 0.4 });
		if (roles.one('tentacles')) c.wave('tentacles', 'rotation', { amplitude: [6 * amp, 0, 0], cycles: 2 });
		return c.build();
	},
	swim({ amp, make, roles }) {
		const c = make(1.6, 'loop', 'Swimming: the veil beats like a wing, the body undulates vertically');
		if (roles.one('veil')) c.wave('veil', 'rotation', { amplitude: [30 * amp, 0, 0], cycles: 1 });
		if (roles.one('brim')) c.wave('brim', 'rotation', { amplitude: [14 * amp, 0, 0], cycles: 1, phase: 0.3 });
		chain(roles).forEach((s, i) => {
			if (i) c.wave(s.name, 'rotation', { amplitude: [8 * amp, 0, 0], cycles: 1, phase: -0.2 * i });
		});
		c.wave('body', 'position', { amplitude: [0, 1 * amp, 0], cycles: 1, phase: 0.25 });
		c.wave('body', 'rotation', { amplitude: [6 * amp, 0, 0], cycles: 1, phase: 0.1 });
		return c.build();
	},
	hurt({ amp, make, roles }) {
		const c = make(0.6, 'once', 'Contracts sharply, veil folds back');
		c.keys('body', 'scale', [[0, [1, 1, 1]], [0.15, [0.85, 0.85, 1.05]], [0.6, [1, 1, 1]]]);
		if (roles.one('veil')) c.keys('veil', 'rotation', [[0, [0, 0, 0]], [0.15, [40 * amp, 0, 0]], [0.6, [0, 0, 0]]]);
		return c.build();
	},
	death({ amp, make, roles }) {
		const c = make(2.4, 'hold', 'Veil collapses, body sags and sinks');
		c.keys('body', 'position', [[0, [0, 0, 0]], [2.4, [0, -2, 0]]], 'linear');
		c.keys('body', 'scale', [[0, [1, 1, 1]], [2.4, [1.05, 0.8, 1]]], 'linear');
		if (roles.one('veil')) c.keys('veil', 'rotation', [[0, [0, 0, 0]], [1.6, [50 * amp, 0, 0]], [2.4, [55 * amp, 0, 0]]]);
		if (roles.one('brim')) c.keys('brim', 'rotation', [[0, [0, 0, 0]], [2.4, [35 * amp, 0, 0]]], 'linear');
		chain(roles).forEach((s, i) => i && c.keys(s.name, 'rotation', [[0, [0, 0, 0]], [2.4, [8, 0, 0]]], 'linear'));
		return c.build();
	},
};
