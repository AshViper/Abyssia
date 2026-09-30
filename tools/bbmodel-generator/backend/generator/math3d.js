// Minimal 3D math that mirrors how Blockbench places bones and cubes.
//
// Blockbench (and this generator) store every group origin and cube from/to/origin in
// "absolute" model units while ignoring parent rotations. A node is displayed at
// (origin - parent.origin) inside its parent, rotated by an Euler rotation in ZYX order
// (three.js convention: M = Rz * Ry * Rx, angles in degrees).

export const DEG = Math.PI / 180;

/** 3x3 rotation matrix (row-major) for Blockbench's ZYX Euler rotation given in degrees. */
export function rotationZYX(rotation) {
	const [rx, ry, rz] = rotation || [0, 0, 0];
	const a = rx * DEG, b = ry * DEG, c = rz * DEG;
	const ca = Math.cos(a), sa = Math.sin(a);
	const cb = Math.cos(b), sb = Math.sin(b);
	const cc = Math.cos(c), sc = Math.sin(c);
	// Rz * Ry * Rx
	return [
		cc * cb, cc * sb * sa - sc * ca, cc * sb * ca + sc * sa,
		sc * cb, sc * sb * sa + cc * ca, sc * sb * ca - cc * sa,
		-sb, cb * sa, cb * ca,
	];
}

/** Decomposes a rotation matrix back into ZYX Euler degrees (same as three.js Euler.setFromRotationMatrix). */
export function eulerZYXFromMatrix(m) {
	const [m11, m12, , m21, m22, , m31, m32, m33] = m;
	const y = Math.asin(-Math.min(1, Math.max(-1, m31)));
	let x, z;
	if (Math.abs(m31) < 0.9999999) {
		x = Math.atan2(m32, m33);
		z = Math.atan2(m21, m11);
	} else {
		x = 0;
		z = Math.atan2(-m12, m22);
	}
	return [x / DEG, y / DEG, z / DEG];
}

export function mat3Mul(a, b) {
	const r = new Array(9);
	for (let i = 0; i < 3; i++) {
		for (let j = 0; j < 3; j++) {
			r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
		}
	}
	return r;
}

export function mat3Apply(m, v) {
	return [
		m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
		m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
		m[6] * v[0] + m[7] * v[1] + m[8] * v[2],
	];
}

export function mat3Transpose(m) {
	return [m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]];
}

export const IDENTITY3 = [1, 0, 0, 0, 1, 0, 0, 0, 1];

/** Affine transform {m: 3x3, t: vec3}. */
export function affine(m = IDENTITY3, t = [0, 0, 0]) {
	return { m, t };
}

/** Returns A∘B (apply B first, then A). */
export function compose(A, B) {
	const t = mat3Apply(A.m, B.t);
	return { m: mat3Mul(A.m, B.m), t: [t[0] + A.t[0], t[1] + A.t[1], t[2] + A.t[2]] };
}

export function applyAffine(A, p) {
	const r = mat3Apply(A.m, p);
	return [r[0] + A.t[0], r[1] + A.t[1], r[2] + A.t[2]];
}

export const add3 = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
export const sub3 = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
export const scale3 = (a, s) => [a[0] * s, a[1] * s, a[2] * s];
export const len3 = (a) => Math.hypot(a[0], a[1], a[2]);
export const norm3 = (a) => {
	const l = len3(a) || 1;
	return [a[0] / l, a[1] / l, a[2] / l];
};

/**
 * World transforms of every bone for the rest pose (or a pose with Blockbench style
 * additive rotation / position and multiplicative scale per bone).
 * @param {{bones: Array}} geometry
 * @param {Record<string, {rotation?:number[], position?:number[], scale?:number[]}>} [pose]
 * @returns {Map<string, {m:number[], t:number[]}>}
 */
export function boneWorldTransforms(geometry, pose = null) {
	const byName = new Map(geometry.bones.map((b) => [b.name, b]));
	const result = new Map();
	const visit = (bone) => {
		if (result.has(bone.name)) return result.get(bone.name);
		const parent = bone.parent ? byName.get(bone.parent) : null;
		const p = pose?.[bone.name];
		const rotation = p?.rotation ? add3(bone.rotation, p.rotation) : bone.rotation;
		let local = parent ? sub3(bone.pivot, parent.pivot) : bone.pivot.slice();
		if (p?.position) local = add3(local, p.position);
		let m = rotationZYX(rotation);
		if (p?.scale) {
			const s = p.scale;
			m = mat3Mul(m, [s[0], 0, 0, 0, s[1], 0, 0, 0, s[2]]);
		}
		const localT = affine(m, local);
		const world = parent ? compose(visit(parent), localT) : localT;
		result.set(bone.name, world);
		return world;
	};
	for (const bone of geometry.bones) visit(bone);
	return result;
}

/**
 * Transform that maps a cube's stored (absolute) coordinates to world space.
 * world(x) = boneWorld ∘ [R_cube | origin - bone.pivot] ∘ (x - origin)
 */
export function cubeWorldTransform(cube, bone, boneWorld) {
	const local = affine(rotationZYX(cube.rotation), sub3(cube.origin, bone.pivot));
	const shift = affine(IDENTITY3, scale3(cube.origin, -1));
	return compose(compose(boneWorld, local), shift);
}

export function round(value, digits = 4) {
	const f = 10 ** digits;
	const r = Math.round(value * f) / f;
	return Object.is(r, -0) ? 0 : r;
}
