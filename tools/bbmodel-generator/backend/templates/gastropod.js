// GastropodTemplate: snail with a coiled shell (whorl chain), a muscular foot whose flanks can be
// armoured with overlapping scale plates (sclerites), and a snout with two tentacles.
// Covers the scaly-foot snail (Chrysomallon squamiferum) and other vent snails.

import { ModelBuilder } from '../generator/builder.js';
import { CreatureTemplate } from './base.js';
import { addChain } from './parts.js';
import { roundedBox } from './shared.js';

export class GastropodTemplate extends CreatureTemplate {
	static id = 'gastropod';
	static label = 'Gastropod (snail)';
	static description = 'Coiled shell whorl chain on a muscular foot with optional armour scales, snout and tentacles; crawls and retracts.';

	defaults() {
		return {
			template: 'gastropod',
			category: 'gastropod',
			body: { length: 10, width: 5, height: 3 },
			parts: [],
			params: {
				shell: { size: 7, whorls: 3, shrink: 0.68, rise: 0.55, tilt: 18 },
				scales: { rows: 1, count: 5, size: 2 },
				head: { length: 3, width: 3, height: 2 },
				tentacles: { length: 3 },
			},
			palette: {},
			texture: { pattern: 'bands', pattern_strength: 0.5 },
			glow: [],
			animations: ['idle', 'crawl', 'retract', 'hurt', 'death'],
			animation: { speed: 1, amplitude: 1 },
			variation: { body: 0.08, shell: 0.1, scales: 0.1, hue: 5, lightness: 0.03 },
		};
	}

	editorSchema() {
		return [
			{
				section: 'Body Size (foot)',
				fields: [
					{ path: 'body.length', label: 'Length', type: 'int', min: 3, max: 32 },
					{ path: 'body.width', label: 'Width', type: 'int', min: 2, max: 16 },
					{ path: 'body.height', label: 'Height', type: 'int', min: 1, max: 8 },
				],
			},
			{
				section: 'Head / Shell Size',
				fields: [
					{ path: 'params.shell.size', label: 'Shell size', type: 'int', min: 2, max: 24 },
					{ path: 'params.shell.whorls', label: 'Whorls', type: 'int', min: 1, max: 5 },
					{ path: 'params.shell.tilt', label: 'Shell tilt (°)', type: 'range', min: 0, max: 45, step: 1 },
					{ path: 'params.tentacles.length', label: 'Tentacle length', type: 'int', min: 1, max: 8 },
				],
			},
			{
				section: 'Fin Size (scales)',
				fields: [
					{ path: 'params.scales.count', label: 'Scales / side', type: 'int', min: 0, max: 10 },
					{ path: 'params.scales.size', label: 'Scale size', type: 'int', min: 1, max: 4 },
				],
			},
		];
	}

	variationTargets() {
		return { body: ['body.length', 'body.width'], shell: ['params.shell.size'], scales: ['params.scales.size'] };
	}

	exaggerationTargets() {
		return { head: ['params.shell.size'], limb: ['params.tentacles.length'] };
	}

	semanticParts(def, ctx) {
		const P = def.params;
		const has = (...n) => this.partEnabled(def, ...n);
		const g = ctx.grid;
		const list = [];
		const add = (p) => (list.push(p), p);
		const S = { id: def.id, list };
		S.foot = add({ id: 'body', kind: 'foot', size: ctx.size([def.body.width, def.body.height, def.body.length]) });
		if (has('shell')) {
			const n = Math.max(1, ctx.lod([Math.min(2, P.shell.whorls), P.shell.whorls, P.shell.whorls]));
			const whorls = Array.from({ length: n }, (_, i) => Math.max(g, ctx.q(ctx.s(P.shell.size) * Math.pow(P.shell.shrink ?? 0.68, i))));
			S.shell = add({ id: 'shell', kind: 'coiled_shell', whorls, rise: P.shell.rise ?? 0.55, tilt: P.shell.tilt ?? 18 });
		}
		if (has('scales') && P.scales?.count > 0 && ctx.atLeast('medium')) S.scales = add({ id: 'scales', kind: 'sclerites', count: Math.round(P.scales.count * ctx.lod([0.5, 0.7, 1])), size: ctx.s(P.scales.size) });
		if (has('head')) S.head = add({ id: 'head', kind: 'snout', size: ctx.size([P.head.width, P.head.height, P.head.length]) });
		if (has('tentacles') && S.head) S.tentacles = add({ id: 'tentacles', kind: 'cephalic_tentacles', length: ctx.s(P.tentacles.length) });
		return S;
	}

	build(S, ctx) {
		const b = new ModelBuilder();
		const g = ctx.grid;
		const root = S.id;
		const [fw, fh, fl] = S.foot.size;
		const y0 = 8;
		b.bone(root, { pivot: [0, 0, 0], role: 'root' });
		b.bone('body', { parent: root, pivot: [0, y0, 0], role: 'body' });
		roundedBox(b, 'body', 'body', { from: [-fw / 2, y0, -fl / 2], to: [fw / 2, y0 + fh, fl / 2], round: { top: g, x: g, front: g, back: g }, material: 'flesh', faces: { down: 'belly' } });

		if (S.head) {
			const [hw, hh, hl] = S.head.size;
			b.bone('head', { parent: 'body', pivot: [0, y0 + fh / 2, -fl / 2 + g], role: 'head' });
			b.box('head', 'head', { from: [-hw / 2, y0, -fl / 2 - hl + g], to: [hw / 2, y0 + hh, -fl / 2 + g], material: 'flesh' });
			if (S.tentacles) {
				for (const [side, sign] of [['right', 1], ['left', -1]]) {
					addChain(b, {
						parent: 'head',
						names: [`${side}_tentacle`],
						start: [sign * (hw / 2 - g / 2), y0 + hh - g / 2, -fl / 2 - hl + 2 * g],
						lengths: [S.tentacles.length + g],
						widths: [g],
						axis: [0, 0, -1],
						rotations: [[20, -sign * 30, 0]],
						role: 'tentacle',
						side,
						material: 'flesh',
					});
				}
			}
		}

		// Coiled shell: each whorl sits up and back on the previous one and turns a little
		if (S.shell) {
			let parent = 'body';
			let base = [0, y0 + fh - g, g];
			S.shell.whorls.forEach((w, i) => {
				const name = i === 0 ? 'shell' : `shell_${i + 1}`;
				b.bone(name, { parent, pivot: base, rotation: i === 0 ? [-S.shell.tilt, 0, 0] : [-12, 28, 8], role: 'shell', meta: { index: i } });
				const h = Math.max(g, Math.round((w * 0.9) / g) * g);
				roundedBox(b, name, name, { from: [base[0] - w / 2, base[1], base[2] - w / 2], to: [base[0] + w / 2, base[1] + h, base[2] + w / 2], round: w >= 4 * g ? { top: g, x: g, front: g, back: g } : {}, material: 'shell', faces: { down: 'mouth' } });
				parent = name;
				base = [base[0] + Math.round((w * 0.12) / (g / 2)) * (g / 2), base[1] + Math.max(g, Math.round((h * S.shell.rise) / g) * g), base[2] + Math.round((w * 0.25) / g) * g];
			});
		}

		// Sclerites: overlapping scale plates shingled along both flanks of the foot
		if (S.scales) {
			const { count, size } = S.scales;
			for (const [side, sign] of [['right', 1], ['left', -1]]) {
				b.bone(`${side}_scales`, { parent: 'body', pivot: [(sign * fw) / 2, y0 + fh, 0], role: 'scales', side });
				for (let i = 0; i < count; i++) {
					const z = -fl / 2 + g + ((fl - 2 * g) * (i + 0.5)) / count;
					const x = (sign * fw) / 2;
					b.box(`${side}_scales`, `${side}_scale`, {
						from: [x, y0, Math.round(z) - size / 2],
						to: [x, y0 + fh + size - g, Math.round(z) + size / 2],
						origin: [x, y0 + fh, Math.round(z)],
						rotation: [0, 0, -sign * 28],
						material: 'leg',
						meta: { colorKey: 'scale', legAxis: 1 },
						shape: { type: 'round', axis: 'y', dir: 1, across: 'z' },
						priority: i % 2 ? 3 : 2,
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
		return GASTROPOD_ANIMATIONS;
	}
}

const sideSign = (bone) => (bone.side === 'left' ? -1 : 1);

export const GASTROPOD_ANIMATIONS = {
	idle({ amp, make, roles }) {
		const c = make(4, 'loop', 'Resting: tentacles feel around, shell rocks slightly');
		for (const t of roles.all('tentacle')) c.wave(t.name, 'rotation', { amplitude: [8 * amp, sideSign(t) * 10 * amp, 0], cycles: 1, phase: t.side === 'left' ? 0.4 : 0 });
		if (roles.one('shell')) c.wave('shell', 'rotation', { amplitude: [0, 0, 2 * amp], cycles: 1 });
		c.wave('body', 'scale', { amplitude: [0.02, 0.04, 0], cycles: 1 });
		return c.build();
	},
	crawl({ amp, make, roles }) {
		const c = make(2, 'loop', 'Crawling: the foot stretches and contracts, head bobs, shell sways');
		c.wave('body', 'scale', { amplitude: [-0.03, 0, 0.08 * amp], cycles: 1 });
		if (roles.one('head')) c.wave('head', 'position', { amplitude: [0, 0, -0.6 * amp], cycles: 1, phase: 0.1 });
		if (roles.one('shell')) c.wave('shell', 'rotation', { amplitude: [2 * amp, 0, 3 * amp], cycles: 1, phase: 0.25 });
		for (const t of roles.all('tentacle')) c.wave(t.name, 'rotation', { amplitude: [6 * amp, sideSign(t) * 6 * amp, 0], cycles: 2 });
		for (const s of roles.all('scales')) c.wave(s.name, 'rotation', { amplitude: [0, 0, sideSign(s) * 4 * amp], cycles: 1, phase: 0.3 });
		return c.build();
	},
	retract({ make, roles }) {
		const c = make(0.8, 'hold', 'Pulls the head and tentacles in and clamps the shell down');
		if (roles.one('head')) {
			c.keys('head', 'scale', [[0, [1, 1, 1]], [0.6, [0.4, 0.4, 0.3]], [0.8, [0.4, 0.4, 0.3]]]);
			c.keys('head', 'position', [[0, [0, 0, 0]], [0.6, [0, 0, 2]], [0.8, [0, 0, 2]]]);
		}
		for (const t of roles.all('tentacle')) c.keys(t.name, 'scale', [[0, [1, 1, 1]], [0.5, [0.3, 0.3, 0.2]], [0.8, [0.3, 0.3, 0.2]]]);
		if (roles.one('shell')) c.keys('shell', 'position', [[0, [0, 0, 0]], [0.8, [0, -1, 0]]]);
		return c.build();
	},
	hurt({ amp, make, roles }) {
		const c = make(0.5, 'once', 'Flinch: head ducks, shell jerks');
		if (roles.one('shell')) c.keys('shell', 'rotation', [[0, [0, 0, 0]], [0.12, [6 * amp, 0, -5 * amp]], [0.5, [0, 0, 0]]]);
		if (roles.one('head')) c.keys('head', 'position', [[0, [0, 0, 0]], [0.12, [0, 0, 1.5]], [0.5, [0, 0, 0]]]);
		return c.build();
	},
	death({ make, roles }) {
		const c = make(1.6, 'hold', 'Tips over onto the shell, foot goes limp');
		c.keys('body', 'rotation', [[0, [0, 0, 0]], [1.2, [0, 0, 70]], [1.6, [0, 0, 75]]]);
		c.keys('body', 'position', [[0, [0, 0, 0]], [1.6, [0, 1, 0]]], 'linear');
		for (const t of roles.all('tentacle')) c.keys(t.name, 'rotation', [[0, [0, 0, 0]], [1.6, [-35, 0, 0]]], 'linear');
		return c.build();
	},
};
