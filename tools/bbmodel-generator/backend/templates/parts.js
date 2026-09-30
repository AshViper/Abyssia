// Reusable part builders shared by several templates (eyes, tooth rows, flat fins, chains).

import { eulerZYXFromMatrix, mat3Apply, mat3Mul, round, rotationZYX } from '../generator/math3d.js';
import { alignCenter } from './shared.js';

/** Cube rotation (Blockbench ZYX Euler, degrees) equal to "splay about Z, then fan about Y". */
export function splayFan(splay, fan) {
	const m = mat3Mul(rotationZYX([0, fan, 0]), rotationZYX([0, 0, splay]));
	return { m, euler: eulerZYXFromMatrix(m).map((v) => round(v, 3)) };
}

/**
 * Jointed walking leg: an un-rotated hip bone (so animation rotations swing about world axes)
 * holding a rotated upper segment, and a foot bone at the knee holding the lower segment.
 * Right legs splay toward +X; `fan` > 0 points the leg forward (-Z).
 * @returns {{hip: string, foot: string, knee: number[], tip: number[]}}
 */
export function addJointedLeg(b, o) {
	const { parent, name, side, hip, upper, lower, splay, kneeSplay = 10, fan = 0, width = 1, role = 'leg', material = 'leg', index = 0, priority = 1, colorKey } = o;
	const sign = side === 'left' ? -1 : 1;
	const up = splayFan(sign * splay, sign * fan);
	const lo = splayFan(sign * kneeSplay, sign * fan);
	b.bone(name, { parent, pivot: hip, role, side, meta: { index, splay, fan } });
	b.box(name, name, {
		from: [hip[0] - width / 2, hip[1] - upper, hip[2] - width / 2],
		to: [hip[0] + width / 2, hip[1], hip[2] + width / 2],
		origin: hip,
		rotation: up.euler,
		material,
		meta: { legAxis: 1, colorKey },
		priority,
	});
	const kv = mat3Apply(up.m, [0, -upper, 0]);
	const knee = hip.map((v, i) => round(v + kv[i], 4));
	const foot = `${name}_foot`;
	b.bone(foot, { parent: name, pivot: knee, role: `${role}_foot`, side, meta: { index } });
	b.box(foot, foot, {
		from: [knee[0] - width / 2, knee[1] - lower, knee[2] - width / 2],
		to: [knee[0] + width / 2, knee[1], knee[2] + width / 2],
		origin: knee,
		rotation: lo.euler,
		material,
		meta: { legAxis: 1, colorKey },
		priority: Math.max(2, priority),
	});
	const tv = mat3Apply(lo.m, [0, -lower, 0]);
	return { hip: name, foot, knee, tip: knee.map((v, i) => round(v + tv[i], 4)) };
}

/**
 * A pair of eyes on the sides of a part.
 * @param {object} o {parent, halfWidth (x of the surface), y, z, size, protrude (0..1), prefix}
 */
export function addEyes(b, o) {
	const { parent, halfWidth, y, z, size, protrude = 0.5, g = 1, prefix = '' } = o;
	const ey = alignCenter(y, size, g);
	const ez = alignCenter(z, size, g);
	const out = [];
	for (const [side, sign] of [['right', 1], ['left', -1]]) {
		const name = `${prefix}${side}_eye`;
		const cx = sign * (halfWidth + size * (protrude - 0.5));
		b.bone(name, { parent, pivot: [cx, ey, ez], role: 'eye', side });
		out.push(b.box(name, name, { center: [cx, ey, ez], size: [size, size, size], material: 'eye', meta: { eyeFace: sign > 0 ? 'east' : 'west', glintLeft: sign < 0 } }));
	}
	return out;
}

/**
 * Row of 1-pixel teeth.
 * @param {object} o {bone, name, xs, y, z, length, dir (+1 up / -1 down), vary, g, priority}
 */
export function addToothRow(b, o) {
	const { bone, name, xs, y, z, length, dir = 1, vary = 0, g = 1, priority = 2, offset = 0 } = o;
	xs.forEach((x, i) => {
		const l = length + (vary && (i + offset) % 2 === 1 ? g : 0);
		const from = dir > 0 ? [x - g / 2, y, z] : [x - g / 2, y - l, z];
		b.box(bone, name, { from, size: [g, l, g], material: 'teeth', meta: { toothDir: dir }, priority });
	});
}

/**
 * Chain of bones (tentacles, antennae, arms, legs): each segment is a straight box along
 * local `axis` in its own frame, rotated at its pivot by `rotations[i]` (relative to the parent).
 * @returns {string[]} bone names
 */
export function addChain(b, o) {
	const {
		parent,
		names,
		start,
		lengths,
		widths,
		axis = [0, 0, -1],
		rotations = [],
		role = 'chain',
		side = null,
		material = 'skin',
		faces = null,
		meta = () => ({}),
		boneMeta = () => ({}),
		overlap = 0,
		priority = (i) => (i === 0 ? 1 : 2),
		shape = null,
	} = o;
	let p = start.slice();
	let prev = parent;
	const out = [];
	lengths.forEach((len, i) => {
		const name = names[i];
		b.bone(name, { parent: prev, pivot: p, rotation: rotations[i] || [0, 0, 0], role, side, meta: { index: i, count: lengths.length, ...boneMeta(i) } });
		const w = widths[i];
		const [ax, ay, az] = axis;
		const end = [p[0] + ax * len, p[1] + ay * len, p[2] + az * len];
		const back = [p[0] - ax * overlap, p[1] - ay * overlap, p[2] - az * overlap];
		const from = [0, 1, 2].map((k) => (axis[k] !== 0 ? Math.min(back[k], end[k]) : p[k] - (Array.isArray(w) ? w[k] : w) / 2));
		const to = [0, 1, 2].map((k) => (axis[k] !== 0 ? Math.max(back[k], end[k]) : p[k] + (Array.isArray(w) ? w[k] : w) / 2));
		b.box(name, name, { from, to, material: typeof material === 'function' ? material(i) : material, faces: typeof faces === 'function' ? faces(i) : faces, meta: meta(i), priority: priority(i), shape: typeof shape === 'function' ? shape(i) : shape });
		out.push(name);
		prev = name;
		p = end;
	});
	return out;
}

/** Linear taper helper: value at index i of n between a and b, quantised to the grid (>= min). */
export function taper(a, b, i, n, g = 1, min = g) {
	const t = n <= 1 ? 0 : i / (n - 1);
	return Math.max(min, Math.round((a + (b - a) * t) / g) * g);
}
