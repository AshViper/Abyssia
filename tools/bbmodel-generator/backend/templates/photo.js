// PhotoTemplate: a model built from a photo (see backend/image/). The definition carries a
// silhouette grid (params.photo.grid, one text row per model unit, palette index per cell,
// "." = empty) produced from a side view of the subject.
//
// Mode "lathe" (radially symmetric, upright subjects: stalked sea squirts, sponges, anemones,
// sea pens, jellyfish): every grid row's span becomes a disc approximated by a box (depth =
// width × depth_ratio). Rows with similar spans are merged by an optimal segmentation to the
// detail level's cube budget; each row keeps its centre, so leaning or bent stalks survive.
// The stack is split into a bone chain at abrupt width changes (stalk -> head) so it can sway.
// The texture is projected back from the grid by the "photo" material.

import { ModelBuilder } from '../generator/builder.js';
import { CreatureTemplate } from './base.js';
import { alignCenter } from './shared.js';

/** Small built-in silhouette used by "New Creature" (a stalked bulb). */
const DEFAULT_GRID = [
	'..2332..',
	'.233332.',
	'23333332',
	'23333332',
	'.222222.',
	'...11...',
	'...11...',
	'...11...',
	'...00...',
	'..0000..',
];

/** Parses the grid into per-row spans: [{r, l, w, c}] (c = span centre in cell units). */
export function gridRows(grid) {
	const out = [];
	(grid || []).forEach((line, r) => {
		const l = line.search(/[^.]/);
		if (l < 0) return;
		let rr = line.length - 1;
		while (line[rr] === '.') rr--;
		out.push({ r, l, w: rr - l + 1, c: (l + rr + 1) / 2 });
	});
	return out;
}

/**
 * Optimal partition of `items` (bottom -> top) into at most `k` runs minimising the squared
 * deviation of width and centre inside each run.
 */
export function segmentRows(items, k) {
	const n = items.length;
	if (!n) return [];
	k = Math.max(1, Math.min(k, n));
	const pw = [0], pw2 = [0], pc = [0], pc2 = [0];
	for (const it of items) {
		pw.push(pw.at(-1) + it.w);
		pw2.push(pw2.at(-1) + it.w * it.w);
		pc.push(pc.at(-1) + it.c);
		pc2.push(pc2.at(-1) + it.c * it.c);
	}
	const cost = (i, j) => {
		// items i..j-1
		const m = j - i;
		const vw = pw2[j] - pw2[i] - (pw[j] - pw[i]) ** 2 / m;
		const vc = pc2[j] - pc2[i] - (pc[j] - pc[i]) ** 2 / m;
		return vw + 0.5 * vc;
	};
	const dp = Array.from({ length: k + 1 }, () => new Array(n + 1).fill(Infinity));
	const cut = Array.from({ length: k + 1 }, () => new Array(n + 1).fill(0));
	dp[0][0] = 0;
	for (let s = 1; s <= k; s++) {
		for (let j = s; j <= n; j++) {
			for (let i = s - 1; i < j; i++) {
				const v = dp[s - 1][i] + cost(i, j);
				if (v < dp[s][j]) {
					dp[s][j] = v;
					cut[s][j] = i;
				}
			}
		}
	}
	const runs = [];
	let j = n;
	for (let s = k; s > 0; s--) {
		const i = cut[s][j];
		runs.unshift([i, j]);
		j = i;
	}
	return runs;
}

export class PhotoTemplate extends CreatureTemplate {
	static id = 'photo';
	static label = 'Photo (from image)';
	static description = 'Built from a photo silhouette grid (Import Image): radially symmetric stack with a swaying bone chain and a texture projected from the photo.';

	defaults() {
		return {
			template: 'photo',
			category: 'photo',
			body: { length: 8, width: 8, height: 10 },
			parts: [],
			params: {
				photo: { mode: 'lathe', height: 10, width_scale: 1, depth_ratio: 1, segments: 0, round: true, grid: DEFAULT_GRID },
			},
			palette: { p0: '#6b6358', p1: '#b9c2c6', p2: '#9fb3c2', p3: '#d4e2ec', body: '#b9c2c6' },
			texture: { pattern: 'plain' },
			glow: [],
			animations: ['idle', 'sway', 'contract', 'hurt', 'death'],
			animation: { speed: 1, amplitude: 1 },
			variation: { body: 0.06, height: 0.08, hue: 4, lightness: 0.03 },
		};
	}

	editorSchema() {
		return [
			{
				section: 'Body Size (photo)',
				fields: [
					{ path: 'params.photo.height', label: 'Height (units)', type: 'int', min: 2, max: 128 },
					{ path: 'params.photo.width_scale', label: 'Width scale', type: 'range', min: 0.4, max: 2.5, step: 0.05 },
					{ path: 'params.photo.depth_ratio', label: 'Depth / width', type: 'range', min: 0.2, max: 2, step: 0.05 },
				],
			},
			{
				section: 'Shape (photo)',
				fields: [
					{ path: 'params.photo.segments', label: 'Max slices (0 = auto)', type: 'int', min: 0, max: 32 },
					{ path: 'params.photo.round', label: 'Rounded slices', type: 'bool' },
				],
			},
		];
	}

	variationTargets() {
		return { body: ['params.photo.width_scale'], height: ['params.photo.height'] };
	}

	semanticParts(def, ctx) {
		const P = def.params.photo;
		const g = ctx.grid;
		const grid = Array.isArray(P.grid) && P.grid.length ? P.grid : DEFAULT_GRID;
		const rows = gridRows(grid);
		if (!rows.length) throw new Error('Photo grid is empty');
		const f = Math.max(0.05, (P.height || grid.length) / grid.length);
		const ws = Math.max(0.1, P.width_scale ?? 1);
		const map = {
			uL: f * ctx.scale[1],
			uS: f * ws * ctx.scale[0],
			uD: f * ws * Math.max(0.05, P.depth_ratio ?? 1) * ctx.scale[2],
			rb: rows[rows.length - 1].r,
		};
		// Overall axis: width-weighted mean centre -> x = 0
		const wsum = rows.reduce((a, it) => a + it.w, 0);
		map.cx0 = rows.reduce((a, it) => a + it.c * it.w, 0) / wsum;

		const items = rows.slice().reverse(); // bottom -> top
		let budget = P.segments > 0 ? P.segments : ctx.lod([5, 8, 12]);
		if (ctx.settings.blockCount > 0) budget = Math.min(budget, ctx.settings.blockCount);
		const runs = segmentRows(items, budget);
		const yOf = (r) => Math.round(((map.rb - r) * map.uL) / g) * g; // bottom edge of grid row r
		const slices = [];
		for (const [i, j] of runs) {
			const run = items.slice(i, j);
			const y0 = yOf(run[0].r);
			const y1 = yOf(run[run.length - 1].r - 1);
			const mw = run.reduce((a, it) => a + it.w, 0) / run.length;
			const mc = run.reduce((a, it) => a + it.c, 0) / run.length;
			const width = ctx.q(mw * map.uS);
			const depth = ctx.q(mw * map.uD);
			const x = alignCenter((map.cx0 - mc) * map.uS, width, g);
			const prev = slices[slices.length - 1];
			if (y1 <= y0 || (prev && prev.width === width && prev.depth === depth && prev.x === x)) {
				if (prev) prev.y1 = Math.max(prev.y1, y1);
				continue;
			}
			slices.push({ y0: prev ? prev.y1 : y0, y1, width, depth, x });
		}
		const { groups, head } = this.#groups(slices, ctx); // may cut slices
		const round = P.round !== false && ctx.atLeast('medium');
		return { id: def.id, list: slices.map((s, i) => ({ id: `slice_${i + 1}`, kind: 'slice', ...s })), slices, groups, head, map, round };
	}

	/**
	 * Splits the slice stack into bone groups: at abrupt width changes first, then the tallest runs
	 * below the head (a single tall slice is cut in two). The head starts at the strongest widening
	 * (stalk -> bulb) and is never split. Mutates `slices` when a slice is cut.
	 */
	#groups(slices, ctx) {
		const g = ctx.grid;
		const maxBones = Math.min(ctx.lod([2, 3, 4]), Math.max(1, slices.reduce((n, s) => n + Math.max(1, Math.floor((s.y1 - s.y0) / (2 * g))), 0)));
		const joints = [];
		for (let i = 1; i < slices.length; i++) {
			const a = slices[i - 1].width, b = slices[i].width;
			const ratio = Math.max(a, b) / Math.max(1, Math.min(a, b));
			if (ratio >= 1.6) joints.push({ slice: slices[i], ratio, widens: b > a });
		}
		const headJoint = maxBones > 1 ? joints.filter((j) => j.widens).sort((p, q) => q.ratio - p.ratio)[0] : null;
		const head = headJoint?.slice || null;
		const below = (s) => !head || slices.indexOf(s) < slices.indexOf(head);
		const cuts = new Set(head ? [head] : []);
		for (const j of joints.filter((x) => x !== headJoint && below(x.slice)).sort((p, q) => q.ratio - p.ratio)) {
			if (cuts.size >= maxBones - 1) break;
			cuts.add(j.slice);
		}
		const groupsOf = () => {
			const out = [];
			let start = 0;
			for (let i = 1; i <= slices.length; i++) {
				if (i === slices.length || cuts.has(slices[i])) {
					out.push([start, i]);
					start = i;
				}
			}
			return out;
		};
		const h = ([a, b]) => slices[b - 1].y1 - slices[a].y0;
		let groups = groupsOf();
		while (groups.length < maxBones) {
			const tall = groups.filter(([a, b]) => below(slices[a]) && h([a, b]) >= 4 * g).sort((p, q) => h(q) - h(p))[0];
			if (!tall) break;
			const mid = slices[tall[0]].y0 + h(tall) / 2;
			if (tall[1] - tall[0] === 1) {
				if (ctx.settings.blockCount > 0 && slices.length >= ctx.settings.blockCount) break; // no extra cube over budget
				// One tall slice: cut it at mid height so the stalk can bend
				const s = slices[tall[0]];
				const y = Math.round(mid / g) * g;
				const upper = { ...s, y0: y };
				s.y1 = y;
				slices.splice(tall[0] + 1, 0, upper);
				cuts.add(upper);
			} else {
				let best = tall[0] + 1;
				for (let i = tall[0] + 1; i < tall[1]; i++) if (Math.abs(slices[i].y0 - mid) < Math.abs(slices[best].y0 - mid)) best = i;
				cuts.add(slices[best]);
			}
			groups = groupsOf();
		}
		return { groups, head: head ? slices.indexOf(head) : -1 };
	}

	build(S, ctx) {
		const b = new ModelBuilder();
		const g = ctx.grid;
		const root = S.id;
		b.bone(root, { pivot: [0, 0, 0], role: 'root' });
		let parent = root;
		S.groups.forEach(([a, c], gi) => {
			const first = S.slices[a];
			const isHead = gi > 0 && a === S.head;
			const name = gi === 0 ? 'base' : isHead ? 'head' : `part_${gi + 1}`;
			const role = gi === 0 ? 'body' : isHead ? 'head' : 'segment';
			b.bone(name, { parent, pivot: [first.x, first.y0, 0], role, meta: { index: gi, count: S.groups.length } });
			for (let i = a; i < c; i++) {
				const s = S.slices[i];
				const meta = { photo: { ...S.map, cc: S.map.cx0 - s.x / S.map.uS } };
				const z = alignCenter(0, s.depth, g);
				const k = Math.max(g, ctx.q(Math.min(s.width, s.depth) / 5));
				const rounded = S.round && s.width >= 4 * g && s.depth >= 4 * g;
				const d = rounded ? s.depth - 2 * k : s.depth;
				b.box(name, `${name}_${i - a + 1}`, { center: [s.x, (s.y0 + s.y1) / 2, z], size: [s.width, s.y1 - s.y0, d], material: 'photo', meta });
				if (rounded) {
					b.box(name, `${name}_${i - a + 1}_side`, { center: [s.x, (s.y0 + s.y1) / 2, z], size: [s.width - 2 * k, s.y1 - s.y0, s.depth], material: 'photo', priority: 2, meta });
				}
			}
			parent = name;
		});
		const geometry = b.build();
		geometry.root = root;
		return geometry;
	}

	animationCatalog() {
		return PHOTO_ANIMATIONS;
	}
}

/** Bones above the base, bottom -> top. */
const chain = (roles) => [...roles.all('segment'), ...roles.all('head')].sort((a, b) => a.meta.index - b.meta.index);

export const PHOTO_ANIMATIONS = {
	idle({ amp, make, roles }) {
		const c = make(4, 'loop', 'Barely sways in the current; the top slowly breathes');
		for (const bone of chain(roles)) c.wave(bone.name, 'rotation', { amplitude: [1.5 * amp, 0, 1 * amp], cycles: 1, phase: 0.08 * bone.meta.index });
		const head = roles.one('head');
		if (head) c.wave(head.name, 'scale', { amplitude: [0.04 * amp, 0.03 * amp, 0.04 * amp], cycles: 1 });
		return c.build();
	},
	sway({ amp, make, roles }) {
		const c = make(5, 'loop', 'Current sway: the stalk bends, the top follows with a delay');
		const base = roles.one('body');
		if (base) c.wave(base.name, 'rotation', { amplitude: [1.5 * amp, 0, 1 * amp], cycles: 1 });
		for (const bone of chain(roles)) c.wave(bone.name, 'rotation', { amplitude: [(3 + 2 * bone.meta.index) * amp, 0, (1.5 + bone.meta.index) * amp], cycles: 1, phase: 0.1 * bone.meta.index });
		return c.build();
	},
	contract({ amp, make, roles }) {
		const top = roles.one('head') || chain(roles).at(-1) || roles.one('body');
		if (!top) return null;
		const c = make(1.6, 'once', 'Squeezes the top (siphon contraction) and slowly refills');
		const s = 1 - 0.18 * Math.min(1.5, amp);
		c.keys(top.name, 'scale', [[0, [1, 1, 1]], [0.25, [s + 0.06, s, s + 0.06]], [0.5, [s + 0.04, s - 0.02, s + 0.04]], [1.6, [1, 1, 1]]]);
		return c.build();
	},
	hurt({ amp, make, roles }) {
		const c = make(0.8, 'once', 'Flinch: the top squeezes, the stalk jerks back');
		const top = roles.one('head') || chain(roles).at(-1);
		if (top) c.keys(top.name, 'scale', [[0, [1, 1, 1]], [0.12, [0.88, 0.8, 0.88]], [0.8, [1, 1, 1]]]);
		for (const bone of chain(roles)) c.keys(bone.name, 'rotation', [[0, [0, 0, 0]], [0.12, [5 * amp, 0, -3 * amp]], [0.8, [0, 0, 0]]]);
		return c.build();
	},
	death({ make, roles }) {
		const c = make(2, 'hold', 'Wilts: the stalk bends over and the top deflates');
		for (const bone of chain(roles)) c.keys(bone.name, 'rotation', [[0, [0, 0, 0]], [2, [12 + 6 * bone.meta.index, 0, 6]]], 'linear');
		const top = roles.one('head') || chain(roles).at(-1);
		if (top) c.keys(top.name, 'scale', [[0, [1, 1, 1]], [2, [0.85, 0.7, 0.85]]], 'linear');
		return c.build();
	},
};
