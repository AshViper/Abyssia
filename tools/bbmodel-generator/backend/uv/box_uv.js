// Box UV exactly as Blockbench computes it (js/outliner/types/cube.js, updateUV) and as
// Minecraft's ModelPart.Cube lays it out: a (2d+2w) x (d+h) strip per cube where
//   row 0:   [ d gap ][ up (w×d) ][ down (w×d) ]
//   row d:   [ east (d×h) ][ north (w×h) ][ west (d×h) ][ south (w×h) ]
// The creature faces north, so east is its right side and north its front.

export const FACE_KEYS = ['north', 'east', 'south', 'west', 'up', 'down'];

export const FACE_NORMALS = {
	north: [0, 0, -1],
	south: [0, 0, 1],
	east: [1, 0, 0],
	west: [-1, 0, 0],
	up: [0, 1, 0],
	down: [0, -1, 0],
};

/** Cube size, rounded so float noise in from/to (e.g. 2.9999999) never leaks into UV maths. */
export function cubeSize(cube) {
	const r = (v) => Math.round(v * 10000) / 10000;
	return [r(cube.to[0] - cube.from[0]), r(cube.to[1] - cube.from[1]), r(cube.to[2] - cube.from[2])];
}

/** Face UV rectangles [u1, v1, u2, v2] for a cube of `size` at `offset` (Blockbench semantics). */
export function boxFaceUVs(size, offset, mirror = false) {
	const [w, h, d] = size;
	const list = [
		{ face: 'east', from: [0, d], size: [d, h] },
		{ face: 'west', from: [d + w, d], size: [d, h] },
		{ face: 'up', from: [d + w, d], size: [-w, -d] },
		{ face: 'down', from: [d + w * 2, 0], size: [-w, d] },
		{ face: 'south', from: [d * 2 + w, d], size: [w, h] },
		{ face: 'north', from: [d, d], size: [w, h] },
	];
	if (mirror) {
		for (const f of list) {
			f.from[0] += f.size[0];
			f.size[0] *= -1;
		}
		const east = { from: list[0].from.slice(), size: list[0].size.slice() };
		list[0].from = list[1].from.slice();
		list[0].size = list[1].size.slice();
		list[1].from = east.from;
		list[1].size = east.size;
	}
	const faces = {};
	for (const f of list) {
		faces[f.face] = [
			f.from[0] + offset[0],
			f.from[1] + offset[1],
			f.from[0] + f.size[0] + offset[0],
			f.from[1] + f.size[1] + offset[1],
		];
	}
	return faces;
}

/** Face sizes (width, height in UV units) for per-face UV. */
export function faceDims(size, face) {
	const [w, h, d] = size;
	switch (face) {
		case 'north':
		case 'south':
			return [w, h];
		case 'east':
		case 'west':
			return [d, h];
		default:
			return [w, d];
	}
}

/** Axis-aligned bounds {x, y, w, h} (min corner) of a UV rectangle that may be flipped. */
export function uvRect(uv) {
	const x = Math.min(uv[0], uv[2]);
	const y = Math.min(uv[1], uv[3]);
	return { x, y, w: Math.abs(uv[2] - uv[0]), h: Math.abs(uv[3] - uv[1]) };
}

/**
 * Tight rectangle (relative to the uv offset) actually covered by the non-empty faces of a
 * Box UV cube. Flat cubes (thickness 0, e.g. fins) only use part of the nominal strip.
 */
export function boxFootprint(size) {
	const faces = boxFaceUVs(size, [0, 0]);
	let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
	for (const key of FACE_KEYS) {
		const r = uvRect(faces[key]);
		if (r.w <= 0 || r.h <= 0) continue;
		minX = Math.min(minX, r.x);
		minY = Math.min(minY, r.y);
		maxX = Math.max(maxX, r.x + r.w);
		maxY = Math.max(maxY, r.y + r.h);
	}
	if (minX === Infinity) return { x: 0, y: 0, w: 0, h: 0 };
	return { x: minX, y: minY, w: maxX - minX, h: maxY - minY };
}

/** True when the face has a visible (non-zero) area. */
export function faceHasArea(uv) {
	return Math.abs(uv[2] - uv[0]) > 1e-6 && Math.abs(uv[3] - uv[1]) > 1e-6;
}
