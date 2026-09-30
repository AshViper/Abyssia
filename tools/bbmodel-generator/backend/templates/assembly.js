// AssemblyTemplate ("parts"): a model assembled from a declarative list of parts.
//
// Written by hand or by an AI looking at a photo: each part names a shape (ellipsoid, dome,
// cylinder, cone, profile, box, plane, eye), a size in model units, where it attaches on its
// parent and which way it grows. Round shapes become stacked slabs (the usual Minecraft
// stepping); parts can be bone chains (tentacles, stalks, tails) and can repeat (ring / row /
// mirrored pair). Roles inferred from the names drive generic animations.
// Spec and examples: README "parts Template（AI が部位を組む）".

import { ModelBuilder } from '../generator/builder.js';
import { eulerZYXFromMatrix, mat3Apply, mat3Mul, round, rotationZYX } from '../generator/math3d.js';
import { MATERIALS } from '../texture/materials.js';
import { SHAPES } from '../texture/shapes.js';
import { CreatureTemplate } from './base.js';
import { alignCenter, splitLength } from './shared.js';

export const PART_SHAPES = ['box', 'ellipsoid', 'dome', 'cylinder', 'cone', 'profile', 'plane', 'eye'];
/** glow_pattern: where a glowing part lights up (the painter's glowPatternHit). */
export const GLOW_PATTERNS = ['spots', 'stripes', 'edges', 'rim'];
const AXES = { x: [0, 1], '-x': [0, -1], y: [1, 1], '-y': [1, -1], z: [2, 1], '-z': [2, -1] };
const LETTER = ['x', 'y', 'z'];
/** Attach keywords -> fractions of the parent's box (x: left(-X) 0 .. right(+X) 1, y: bottom 0 .. top 1, z: front(-Z) 0 .. back 1). */
const AT = {
	center: [0.5, 0.5, 0.5],
	top: [0.5, 1, 0.5],
	bottom: [0.5, 0, 0.5],
	front: [0.5, 0.5, 0],
	back: [0.5, 0.5, 1],
	left: [0, 0.5, 0.5],
	right: [1, 0.5, 0.5],
};
const ROLE_RULES = [
	[/eye/, 'eye'],
	[/tentacle|arm|tendril|oral|filament/, 'tentacle'],
	[/fin|frill|veil|wing|skirt/, 'fin'],
	[/tail|caudal/, 'tail'],
	[/leg|foot/, 'leg'],
	[/head|bell|cap|bulb|crown|dome|hood/, 'head'],
	[/stalk|stem|peduncle|neck|stipe/, 'segment'],
	[/body|trunk|torso|mantle|base|disc/, 'body'],
	[/plume|gill|tuft/, 'plume'],
];
const HEX_RE = /^#[0-9a-fA-F]{6}$/;
const NAME_RE = /^[a-z][a-z0-9_]*$/;

/** Returns human/AI readable problems of a parts list (empty = OK). */
export function validateParts(parts) {
	const out = [];
	if (!Array.isArray(parts) || !parts.length) return ['params.parts must be a non-empty list of parts'];
	const names = new Set();
	parts.forEach((p, i) => {
		const at = `parts[${i}]${p?.name ? ` "${p.name}"` : ''}`;
		if (!p || typeof p !== 'object') return out.push(`${at}: must be an object`);
		if (typeof p.name !== 'string' || !NAME_RE.test(p.name)) out.push(`${at}: name must be lower_snake_case`);
		else if (names.has(p.name)) out.push(`${at}: duplicate name`);
		names.add(p.name);
		if (p.shape && !PART_SHAPES.includes(p.shape)) out.push(`${at}: unknown shape "${p.shape}" (${PART_SHAPES.join(', ')})`);
		if (p.shape !== 'eye' && !(Array.isArray(p.size) && p.size.length === 3 && p.size.every((v) => typeof v === 'number' && v >= 0 && v <= 256))) out.push(`${at}: size must be [x, y, z] model units (0..256)`);
		if (p.shape === 'eye' && !(typeof p.size === 'number' || Array.isArray(p.size))) out.push(`${at}: eye size must be a number`);
		if (p.axis && !(p.axis in AXES)) out.push(`${at}: axis must be one of ${Object.keys(AXES).join(', ')}`);
		if (p.material && !(p.material in MATERIALS)) out.push(`${at}: unknown material "${p.material}" (${Object.keys(MATERIALS).join(', ')})`);
		if (p.color && typeof p.color !== 'string') out.push(`${at}: color must be "#rrggbb" or a palette key`);
		for (const k of ['color_to', 'belly', 'glow_color']) if (p[k] !== undefined && !HEX_RE.test(p[k])) out.push(`${at}: ${k} must be "#rrggbb"`);
		if (p.glow_pattern !== undefined) {
			if (!GLOW_PATTERNS.includes(p.glow_pattern)) out.push(`${at}: glow_pattern must be ${GLOW_PATTERNS.join(' / ')}`);
			else if (!p.glow) out.push(`${at}: glow_pattern needs "glow" (intensity)`);
		}
		if (typeof p.at === 'string' && !(p.at in AT) && p.at !== 'tip' && p.at !== 'base') out.push(`${at}: at must be ${[...Object.keys(AT), 'tip', 'base'].join(' / ')} or [fx, fy, fz]`);
		if (Array.isArray(p.at) && p.at.length !== 3) out.push(`${at}: at must be [fx, fy, fz] (0..1 of the parent box)`);
		if (p.shape === 'profile' && !(Array.isArray(p.profile) && p.profile.length >= 2)) out.push(`${at}: profile shape needs "profile": [widths 0..1 from base to tip]`);
		if (p.arrange && !['ring', 'row'].includes(p.arrange)) out.push(`${at}: arrange must be ring or row`);
		if (p.outline && !(p.outline in SHAPES)) out.push(`${at}: unknown outline "${p.outline}" (${Object.keys(SHAPES).join(', ')})`);
		const n = (p.count ?? 1) * (p.mirror ? 2 : 1);
		if (!(Number.isInteger(p.count ?? 1) && n >= 1 && n <= 32)) out.push(`${at}: count must be an integer (count × mirror ≤ 32)`);
		if (p.segments !== undefined && !(Number.isInteger(p.segments) && p.segments >= 1 && p.segments <= 8)) out.push(`${at}: segments must be 1..8`);
	});
	parts.forEach((p, i) => {
		if (p?.parent && !names.has(p.parent)) out.push(`parts[${i}] "${p.name}": unknown parent "${p.parent}"`);
		// Cycle check
		const seen = new Set();
		let q = p;
		while (q?.parent) {
			if (seen.has(q.name)) {
				out.push(`parts[${i}] "${p.name}": parent cycle`);
				break;
			}
			seen.add(q.name);
			q = parts.find((x) => x.name === q.parent);
		}
	});
	return out;
}

/** Width factor (0..1) of a round shape at t (0 = base, 1 = tip). */
function profileAt(p, t) {
	switch (p.shape) {
		case 'ellipsoid':
			return Math.sqrt(Math.max(0, 1 - (2 * t - 1) ** 2));
		case 'dome':
			return Math.sqrt(Math.max(0, 1 - t * t));
		case 'cone':
			return 1 + ((p.tip ?? 0.15) - 1) * t;
		case 'cylinder':
			return 1 + ((p.taper ?? 1) - 1) * t;
		case 'profile': {
			const k = p.profile;
			const x = t * (k.length - 1);
			const i = Math.min(k.length - 2, Math.floor(x));
			return k[i] + (k[i + 1] - k[i]) * (x - i);
		}
		default:
			return 1;
	}
}

const inferRole = (name) => ROLE_RULES.find(([re]) => re.test(name))?.[1] || 'part';
const rotM = (r) => rotationZYX(r || [0, 0, 0]);
const eul = (m) => eulerZYXFromMatrix(m).map((v) => round(v, 3));

export class AssemblyTemplate extends CreatureTemplate {
	static id = 'parts';
	static label = 'Parts (AI / hand assembled)';
	static description = 'Assembled from a declarative part list (shape, size, attach point, axis, chains, rings, mirrored pairs); written by hand or by an AI from a photo.';

	defaults() {
		return {
			template: 'parts',
			category: 'parts',
			body: { length: 10, width: 10, height: 16 },
			parts: [],
			params: {
				orientation: 'upright',
				parts: [
					{ name: 'stalk', shape: 'cylinder', size: [3, 10, 3], segments: 2, taper: 0.8, color: '#a8b0b4' },
					{ name: 'head', shape: 'ellipsoid', parent: 'stalk', at: 'tip', size: [10, 8, 10], color: '#c9dbe6' },
				],
			},
			palette: {},
			texture: { pattern: 'plain' },
			glow: [],
			animation: { speed: 1, amplitude: 1 },
			variation: { body: 0.06, hue: 4, lightness: 0.03 },
		};
	}

	editorSchema() {
		return [
			{
				section: 'Body Size (parts)',
				fields: [
					{ path: 'params.orientation', label: 'Orientation', type: 'select', options: ['upright', 'horizontal'] },
					{ path: 'params.scale', label: 'Scale', type: 'range', min: 0.25, max: 3, step: 0.05 },
				],
			},
		];
	}

	variationTargets() {
		return { body: ['params.scale'] };
	}

	semanticParts(def, ctx) {
		const specs = def.params?.parts;
		const problems = validateParts(specs);
		if (problems.length) throw new Error(`Invalid parts: ${problems.join('; ')}`);
		const scale = def.params.scale ?? 1;
		def.palette = { ...(def.palette || {}) };
		const list = specs.map((p) => {
			let colorKey = null;
			if (p.color && HEX_RE.test(p.color)) {
				colorKey = `part_${p.name}`;
				def.palette[colorKey] = p.color.toLowerCase();
			} else if (p.color) colorKey = p.color;
			let glowKey = 'glow';
			if (p.glow_color && HEX_RE.test(p.glow_color)) {
				glowKey = `glow_${p.name}`;
				def.palette[glowKey] = p.glow_color.toLowerCase();
			}
			// Optional style colours: gradient end (base -> tip) and light belly
			let gradKey = null, bellyKey = null;
			if (p.color_to && HEX_RE.test(p.color_to)) def.palette[(gradKey = `grad_${p.name}`)] = p.color_to.toLowerCase();
			if (p.belly && HEX_RE.test(p.belly)) def.palette[(bellyKey = `belly_${p.name}`)] = p.belly.toLowerCase();
			return { ...p, role: p.role || inferRole(p.name), colorKey, glowKey, gradKey, bellyKey, scale };
		});
		// First root part is the body unless roles say otherwise
		const firstRoot = list.find((p) => !p.parent);
		if (firstRoot && !specs.find((p) => p.name === firstRoot.name).role && !['head', 'segment'].includes(firstRoot.role)) firstRoot.role = 'body';
		return { id: def.id, list, orientation: def.params.orientation || 'upright' };
	}

	build(S, ctx) {
		const b = new ModelBuilder();
		const g = ctx.grid;
		const root = S.id;
		b.bone(root, { pivot: [0, 0, 0], role: 'root' });
		const byParent = new Map();
		for (const p of S.list) {
			const k = p.parent || '';
			if (!byParent.has(k)) byParent.set(k, []);
			byParent.get(k).push(p);
		}
		const sc = (v, a) => v * S.list[0].scale * ctx.scale[a];
		let chainId = 0;

		const buildPart = (p, parentInst) => {
			for (const inst of this.#instances(p)) {
				const tag = `${parentInst?.tag || ''}${inst.tag}`;
				const name = `${p.name}${tag}`;
				// Attach point and parent bone
				let P, parentBone = root;
				const at = inst.at;
				if (!parentInst) {
					P = [0, 0, 0];
				} else if (at === 'tip' || at === 'base') {
					P = at === 'tip' ? parentInst.tip : parentInst.base;
					parentBone = at === 'tip' ? parentInst.bones.at(-1).name : parentInst.bones[0].name;
				} else {
					const f = Array.isArray(at) ? at : AT[at || 'center'];
					P = [0, 1, 2].map((a) => parentInst.min[a] + f[a] * (parentInst.max[a] - parentInst.min[a]));
					// Bone of the chain segment nearest to the point
					const ai = parentInst.axis[0];
					parentBone = parentInst.bones.reduce((best, bn) => (Math.abs(bn.mid[ai] - P[ai]) < Math.abs(best.mid[ai] - P[ai]) ? bn : best)).name;
				}
				const off = (p.offset || [0, 0, 0]).map((v, a) => sc(v, a) * (a === 0 && inst.mirrored ? -1 : 1));
				P = [0, 1, 2].map((a) => ctx.qp(P[a] + off[a] + inst.shift[a], true));
				const record = this.#emit(b, ctx, p, { name, P, parentBone, inst, sc, chain: chainId++ });
				record.tag = tag;
				for (const child of byParent.get(p.name) || []) buildPart(child, record);
			}
		};
		for (const p of byParent.get('') || []) buildPart(p, null);

		b.ground({ centerFilter: (c) => ['body', 'head', 'segment'].includes(b.getBone(c.bone)?.role), snap: g });
		const geometry = b.build();
		geometry.root = root;
		geometry.orientation = S.orientation;
		return geometry;
	}

	/** Expands count / arrange / mirror into instances: {tag, side, at, shift, rot (matrix), mirrored}. */
	#instances(p) {
		const count = p.count ?? 1;
		const base = [];
		for (let i = 0; i < count; i++) {
			const tag = count > 1 ? `_${i + 1}` : '';
			let rot = rotM(p.rotate);
			let shift = [0, 0, 0];
			if (p.arrange === 'ring' && count > 1) {
				const yaw = (p.start_angle ?? 0) + (360 * i) / count;
				const Ry = rotM([0, yaw, 0]);
				const radius = p.radius ?? 0;
				shift = mat3Apply(Ry, [0, 0, -radius]);
				// Tilt away from the ring centre (sign depends on which way the part grows)
				const [ai, sg] = AXES[p.axis || 'y'];
				const tilt = (p.tilt ?? 0) * (ai === 1 && sg > 0 ? -1 : 1);
				rot = mat3Mul(Ry, mat3Mul(rotM([tilt, 0, 0]), rot));
			} else if (p.arrange === 'row' && count > 1) {
				const sp = p.spacing || [0, 0, 2];
				shift = sp.map((v) => v * (i - (count - 1) / 2));
			}
			base.push({ tag, rot, shift, at: p.at, mirrored: false, side: null });
		}
		if (!p.mirror) return base;
		// The spec describes one side as given; the mirrored copy is flipped across X (M R M, M = diag(-1, 1, 1))
		const fx = Array.isArray(p.at) ? p.at[0] : p.at === 'left' ? 0 : p.at === 'right' ? 1 : 0.5;
		const given = fx > 0.5 || (fx === 0.5 && (p.offset?.[0] ?? 0) >= 0) ? 'right' : 'left';
		const other = given === 'right' ? 'left' : 'right';
		const at = Array.isArray(p.at) ? [1 - p.at[0], p.at[1], p.at[2]] : p.at === 'left' ? 'right' : p.at === 'right' ? 'left' : p.at;
		const out = [];
		for (const inst of base) {
			const m = inst.rot;
			out.push({ ...inst, tag: `${inst.tag}_${given}`, side: given });
			out.push({ tag: `${inst.tag}_${other}`, side: other, rot: [m[0], -m[1], -m[2], -m[3], m[4], m[5], -m[6], m[7], m[8]], shift: [-inst.shift[0], inst.shift[1], inst.shift[2]], at, mirrored: true });
		}
		return out;
	}

	/** Emits the bones and cubes of one part instance; returns its placement record for children. */
	#emit(b, ctx, p, { name, P, parentBone, inst, sc, chain }) {
		const g = ctx.grid;
		const shape = p.shape || (Array.isArray(p.size) && p.size.includes(0) ? 'plane' : 'box');
		const [ai, sg0] = AXES[p.axis || (p.role === 'tentacle' ? '-y' : 'y')];
		const sg = inst.mirrored && ai === 0 ? -sg0 : sg0; // mirrored copies grow the other way along X
		const axis = [ai, sg];
		const cross = [0, 1, 2].filter((a) => a !== ai);
		const priority = p.detail ?? (p.parent ? 2 : 1);
		const meta = { colorKey: p.colorKey, part: p.name };
		const glow = p.glow ? { intensity: Math.min(2, Math.max(0, p.glow)), color: p.glowKey, uniform: !p.glow_pattern, pattern: p.glow_pattern || null } : null;
		if (p.bellyKey) meta.bellyKey = p.bellyKey;
		const material = p.material || (shape === 'eye' ? 'eye' : shape === 'plane' ? (p.role === 'tentacle' || p.role === 'plume' ? 'plume' : 'fin') : 'skin');

		// Sizes (quantised). Eyes are cubes; planes keep their zero dimension.
		let size;
		if (shape === 'eye') {
			const e = Math.max(g, ctx.q(sc(Array.isArray(p.size) ? p.size[0] : p.size, 0)));
			size = [e, e, e];
		} else size = p.size.map((v, a) => (v === 0 ? 0 : ctx.q(sc(v, a))));
		const L = size[ai];
		const base = P.slice();
		if (p.center) base[ai] -= (sg * L) / 2;
		// Extent of the whole part along its axis (all chain segments; cubes keep unrotated absolute
		// coordinates): the painter's colour gradient and glow rim / stripes run base (0) -> tip (1)
		meta.span = { axis: ai, sign: sg, start: base[ai], length: Math.max(g, L) };
		if (p.gradKey) meta.gradKey = p.gradKey;
		const alongPt = (d) => {
			const q = base.slice();
			q[ai] += sg * d;
			return q;
		};
		// Chain segments along the axis
		const segs = Math.max(1, Math.min(p.segments ?? 1, Math.floor(L / g) || 1));
		const segLens = splitLength(Math.max(g, L), segs, g);
		const curl = typeof p.curl === 'number' ? (ai === 0 ? [0, 0, p.curl] : [p.curl, 0, 0]) : p.curl || [0, 0, 0];
		const curlM = inst.mirrored ? [curl[0], -curl[1], -curl[2]] : curl;
		const bones = [];
		let d0 = 0;
		const phase = ((chain * 0.6180339) % 1) || 0;
		segLens.forEach((len, k) => {
			const bn = k === 0 ? name : `${name}_${k + 1}`;
			const pivot = alongPt(d0).map((v) => round(v, 4));
			const rotation = k === 0 ? eul(inst.rot) : curlM;
			b.bone(bn, { parent: k === 0 ? parentBone : bones[k - 1].name, pivot, rotation, role: p.role, side: inst.side, meta: { index: k, count: segs, chain, phase, part: p.name } });
			bones.push({ name: bn, d0, d1: d0 + len, mid: alongPt(d0 + len / 2) });
			d0 += len;
		});

		const cubeFor = (bn, from, to, extra = {}) => b.box(bn, `${bn}`, { from, to, material, meta: { ...meta, ...(extra.meta || {}) }, glow, priority: extra.priority ?? priority, shape: extra.shape || null });
		const crossBox = (d1, d2, wa, wb) => {
			const from = [0, 0, 0], to = [0, 0, 0];
			const a0 = base[ai] + sg * d1, a1 = base[ai] + sg * d2;
			from[ai] = Math.min(a0, a1);
			to[ai] = Math.max(a0, a1);
			[wa, wb].forEach((w, k) => {
				const a = cross[k];
				const c = w === 0 ? P[a] : alignCenter(P[a], w, g);
				from[a] = c - w / 2;
				to[a] = c + w / 2;
			});
			return [from, to];
		};

		if (shape === 'eye') {
			const [from, to] = crossBox(0, L, size[cross[0]], size[cross[1]]);
			// Pupil faces away from the body centre line
			const eyeFace = Math.abs(P[0]) >= 1 ? (P[0] > 0 ? 'east' : 'west') : 'north';
			b.box(bones[0].name, bones[0].name, { from, to, material, meta: { ...meta, eyeFace, glintLeft: P[0] < 0 }, glow, priority: p.detail ?? 1 });
		} else if (shape === 'plane' || shape === 'box') {
			for (const bn of bones) {
				const [from, to] = crossBox(bn.d0, bn.d1, size[cross[0]], size[cross[1]]);
				const zero = [0, 1, 2].find((a) => size[a] === 0);
				const outline = p.outline && zero !== undefined ? { type: p.outline, axis: LETTER[ai], dir: sg, across: LETTER[cross.find((a) => a !== zero)] } : null;
				cubeFor(bn.name, from, to, { priority: bn === bones[0] ? priority : Math.max(2, priority), shape: outline });
			}
		} else {
			// Round shapes: slabs with the profile, merged when equal, split at segment boundaries
			const total = Math.max(g, L);
			// Preset geometry.roundSteps / roundCorners give a chunkier style (fewer, bigger boxes)
			const pg = ctx.preset.geometry || {};
			const maxSlabs = p.steps ?? (pg.roundSteps ? Math.min(pg.roundSteps, ctx.lod([3, 5, 8])) : ctx.lod([3, 5, 8]));
			const rounded = p.round !== false && pg.roundCorners !== false && ctx.atLeast('medium');
			for (const bn of bones) {
				const segLen = bn.d1 - bn.d0;
				const n = Math.max(1, Math.min(Math.round(segLen / g), Math.round((maxSlabs * segLen) / total) || 1));
				const lens = splitLength(segLen, n, g);
				const slabs = [];
				let d = bn.d0;
				for (const len of lens) {
					const f = Math.max(0.05, profileAt(p, (d + len / 2) / total));
					const wa = Math.max(g, ctx.q(size[cross[0]] * f));
					const wb = Math.max(g, ctx.q(size[cross[1]] * f));
					const prev = slabs.at(-1);
					if (prev && prev.wa === wa && prev.wb === wb) prev.d2 = d + len;
					else slabs.push({ d1: d, d2: d + len, wa, wb });
					d += len;
				}
				slabs.forEach((s, i) => {
					const pr = bn === bones[0] && i === 0 ? priority : Math.max(priority, 1);
					const k = Math.max(g, ctx.q(Math.min(s.wa, s.wb) / 5));
					if (rounded && s.wa >= 4 * g && s.wb >= 4 * g) {
						const [f1, t1] = crossBox(s.d1, s.d2, s.wa, s.wb - 2 * k);
						cubeFor(bn.name, f1, t1, { priority: pr });
						const [f2, t2] = crossBox(s.d1, s.d2, s.wa - 2 * k, s.wb);
						cubeFor(bn.name, f2, t2, { priority: Math.max(2, pr) });
					} else {
						const [f, t] = crossBox(s.d1, s.d2, s.wa, s.wb);
						cubeFor(bn.name, f, t, { priority: pr });
					}
				});
			}
		}

		// Placement record (unrotated bounds of this instance's own cubes)
		const own = new Set(bones.map((x) => x.name));
		const min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
		for (const c of b.cubes) {
			if (!own.has(c.bone)) continue;
			for (let a = 0; a < 3; a++) {
				min[a] = Math.min(min[a], c.from[a]);
				max[a] = Math.max(max[a], c.to[a]);
			}
		}
		const center = [0, 1, 2].map((a) => (min[a] + max[a]) / 2);
		const tipPt = alongPt(L);
		const basePt = alongPt(0);
		for (const a of cross) (tipPt[a] = center[a]), (basePt[a] = center[a]);
		return { min, max, bones, axis, tip: tipPt, base: basePt };
	}

	animationCatalog() {
		return ASSEMBLY_ANIMATIONS;
	}
}

/** Chains grouped by part instance, segments in order. */
function chains(geometry, roles) {
	const map = new Map();
	for (const bone of geometry.bones) {
		if (bone.meta.chain === undefined || (roles && !roles.includes(bone.role))) continue;
		if (!map.has(bone.meta.chain)) map.set(bone.meta.chain, []);
		map.get(bone.meta.chain).push(bone);
	}
	for (const list of map.values()) list.sort((a, b) => a.meta.index - b.meta.index);
	return [...map.values()];
}
const upright = (geometry) => geometry.orientation !== 'horizontal';

export const ASSEMBLY_ANIMATIONS = {
	idle({ amp, make, geometry, roles }) {
		const c = make(4, 'loop', 'Breathing body / head, appendages drift');
		for (const bone of [...roles.all('head'), ...roles.all('body')].filter((x) => x.meta.index === 0)) c.wave(bone.name, 'scale', { amplitude: [0.03 * amp, 0.025 * amp, 0.03 * amp], cycles: 1 });
		for (const ch of chains(geometry, ['tentacle', 'fin', 'tail', 'plume', 'segment'])) {
			ch.forEach((bone, i) => c.wave(bone.name, 'rotation', { amplitude: [(2 + 2 * i) * amp, 0, (1 + i) * amp], cycles: 1, phase: bone.meta.phase + 0.1 * i }));
		}
		return c.build();
	},
	sway({ amp, make, geometry }) {
		if (!upright(geometry)) return null;
		const stalks = chains(geometry, ['segment', 'body']).filter((ch) => ch.length > 1 || ch[0].role === 'segment');
		if (!stalks.length) return null;
		const c = make(5, 'loop', 'Current sway: stalks bend, everything above follows');
		for (const ch of stalks) ch.forEach((bone, i) => c.wave(bone.name, 'rotation', { amplitude: [(3 + 2 * i) * amp, 0, (1.5 + i) * amp], cycles: 1, phase: 0.1 * i }));
		return c.build();
	},
	swim({ amp, make, geometry, roles }) {
		if (upright(geometry)) return null;
		const c = make(1.6, 'loop', 'Body and tail undulate, fins paddle');
		const spine = chains(geometry, ['body', 'tail', 'segment']);
		for (const ch of spine) ch.forEach((bone, i) => c.wave(bone.name, 'rotation', { amplitude: [0, (4 + 5 * i) * amp * (bone.role === 'tail' ? 1.6 : 1), 0], cycles: 1, phase: 0.12 * i + (bone.role === 'tail' ? 0.15 : 0) }));
		for (const fin of roles.all('fin').filter((x) => x.meta.index === 0)) c.wave(fin.name, 'rotation', { amplitude: [0, 0, 14 * amp * (fin.side === 'left' ? -1 : 1)], cycles: 1, phase: fin.meta.phase });
		return c.build();
	},
	tentacle_move({ amp, make, geometry }) {
		const list = chains(geometry, ['tentacle']);
		if (!list.length) return null;
		const c = make(3, 'loop', 'Tentacles curl and uncurl in a travelling wave');
		for (const ch of list) ch.forEach((bone, i) => c.wave(bone.name, 'rotation', { amplitude: [(6 + 5 * i) * amp, 0, (3 + 2 * i) * amp], cycles: 1, phase: bone.meta.phase + 0.15 * i }));
		return c.build();
	},
	pulse({ amp, make, roles }) {
		const head = roles.all('head').find((x) => x.meta.index === 0);
		if (!head) return null;
		const c = make(1.6, 'loop', 'The head / bell contracts and relaxes');
		const s = 0.12 * Math.min(1.5, amp);
		c.keys(head.name, 'scale', [[0, [1, 1, 1]], [0.4, [1 - s, 1 + s * 0.6, 1 - s]], [1.6, [1, 1, 1]]]);
		return c.build();
	},
	hurt({ amp, make, geometry, roles }) {
		const c = make(0.8, 'once', 'Flinch: squeeze and jerk');
		const main = roles.all('head').find((x) => x.meta.index === 0) || roles.all('body').find((x) => x.meta.index === 0);
		if (main) c.keys(main.name, 'scale', [[0, [1, 1, 1]], [0.12, [0.88, 0.82, 0.88]], [0.8, [1, 1, 1]]]);
		for (const ch of chains(geometry, ['tentacle', 'segment', 'tail'])) c.keys(ch[0].name, 'rotation', [[0, [0, 0, 0]], [0.12, [8 * amp, 0, -4 * amp]], [0.8, [0, 0, 0]]]);
		return c.build();
	},
	death({ make, geometry, roles }) {
		const c = make(2, 'hold', 'Collapses: appendages droop, the body deflates');
		for (const ch of chains(geometry, ['tentacle', 'segment', 'tail', 'fin'])) ch.forEach((bone, i) => c.keys(bone.name, 'rotation', [[0, [0, 0, 0]], [2, [10 + 6 * i, 0, 5]]], 'linear'));
		const main = roles.all('head').find((x) => x.meta.index === 0) || roles.all('body').find((x) => x.meta.index === 0);
		if (main) c.keys(main.name, 'scale', [[0, [1, 1, 1]], [2, [0.85, 0.7, 0.85]]], 'linear');
		return c.build();
	},
};
