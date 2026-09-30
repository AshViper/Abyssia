// UV layout for a generated geometry: Box UV (default, Minecraft style) or Per-face UV.
// Produces non-overlapping regions, picks/grows the texture atlas and returns the exact
// face UV rectangles Blockbench will use.

import { FACE_KEYS, boxFaceUVs, boxFootprint, cubeSize, faceDims, faceHasArea } from './box_uv.js';
import { atlasCandidates, packIntoAtlas } from './packer.js';

/**
 * @typedef {Object} CubeUV
 * @property {number[]} offset             Box UV offset (box mode)
 * @property {boolean} mirror
 * @property {Record<string, number[]>} faces  face -> [u1, v1, u2, v2]
 * @property {Record<string, boolean>} enabled face has a texture (false = hidden zero-area face in per-face mode)
 * @property {string|null} sharedFrom       cube whose region is re-used (mirrored), not painted again
 */

/**
 * @param {{cubes: Array}} geometry
 * @param {object} ctx generation context
 */
export function layoutUV(geometry, ctx) {
	const s = ctx.settings;
	const mode = s.uvMode === 'per_face' ? 'per_face' : 'box';
	const align = ctx.grid;
	const warnings = [];
	const cubes = {};
	const items = [];
	const byName = new Map(geometry.cubes.map((c) => [c.name, c]));

	// Mirrored parts can share one region (optional, off by default: no overlapping UVs)
	const shareOf = new Map();
	if (mode === 'box' && s.mirrorUV) {
		for (const cube of geometry.cubes) {
			const src = cube.uvShare && byName.get(cube.uvShare);
			if (src && sameSize(cubeSize(src), cubeSize(cube)) && !src.uvShare) shareOf.set(cube.name, src.name);
		}
	}

	if (mode === 'box') {
		for (const cube of geometry.cubes) {
			if (shareOf.has(cube.name)) continue;
			const fp = boxFootprint(cubeSize(cube));
			items.push({ key: cube.name, w: fp.w, h: fp.h, minX: fp.x, minY: fp.y, fp });
		}
	} else {
		for (const cube of geometry.cubes) {
			const size = cubeSize(cube);
			for (const face of FACE_KEYS) {
				const [w, h] = faceDims(size, face);
				if (w > 0 && h > 0) items.push({ key: `${cube.name}#${face}`, w, h });
			}
		}
	}

	// Box items are packed by their tight footprint; the uv_offset is the footprint position minus
	// the footprint's own offset, so the footprint may not start before (fp.x, fp.y).
	const totalArea = items.reduce((a, it) => a + it.w * it.h, 0);
	const maxW = Math.max(0, ...items.map((it) => it.w + (it.minX || 0)));
	const maxH = Math.max(0, ...items.map((it) => it.h + (it.minY || 0)));
	const fits = ([w, h]) => w * h >= totalArea && w >= maxW && h >= maxH;
	const all = atlasCandidates(16, 2048);
	const requested = s.textureSize === 'auto' ? null : Number(s.textureSize);
	let candidates = all.filter(fits);
	if (requested) {
		candidates = [[requested, requested], ...candidates.filter(([w, h]) => w * h > requested * requested)];
	}

	let packed = null;
	if (s.generateUV) packed = packIntoAtlas(items, candidates, { align });

	let width, height;
	if (packed) {
		width = packed.width;
		height = packed.height;
		if (requested && (width !== requested || height !== requested)) {
			warnings.push(`Requested texture ${requested}×${requested} is too small for the UV layout; expanded to ${width}×${height}.`);
		}
	} else {
		// UV generation disabled: everything at offset 0, the user lays out UVs in Blockbench.
		const side = requested || candidates[0]?.[0] || 64;
		width = Math.max(side, pow2(maxW));
		height = Math.max(requested || 16, pow2(maxH));
		if (!s.generateUV) warnings.push('UV generation is disabled: all cubes share UV offset 0,0 (overlapping).');
		else warnings.push('UV layout did not fit any atlas size; cubes overlap at 0,0.');
	}

	if (mode === 'box') {
		for (const cube of geometry.cubes) {
			const size = cubeSize(cube);
			const shared = shareOf.get(cube.name);
			let offset = [0, 0];
			let mirror = !!cube.mirror;
			if (shared) {
				offset = cubes[shared]?.offset || positionToOffset(packed, shared, boxFootprint(cubeSize(byName.get(shared))));
				mirror = !byName.get(shared).mirror;
			} else if (packed) {
				offset = positionToOffset(packed, cube.name, boxFootprint(size));
			}
			const faces = boxFaceUVs(size, offset, mirror);
			const enabled = {};
			for (const f of FACE_KEYS) enabled[f] = true;
			cubes[cube.name] = { offset, mirror, faces, enabled, sharedFrom: shared || null };
		}
	} else {
		for (const cube of geometry.cubes) {
			const size = cubeSize(cube);
			const faces = {};
			const enabled = {};
			for (const face of FACE_KEYS) {
				const [w, h] = faceDims(size, face);
				const pos = packed?.positions.get(`${cube.name}#${face}`) || { x: 0, y: 0 };
				faces[face] = orientFace(face, pos.x, pos.y, w, h);
				enabled[face] = w > 0 && h > 0;
			}
			cubes[cube.name] = { offset: [0, 0], mirror: false, faces, enabled, sharedFrom: null };
		}
	}

	const k = ctx.texelsPerUnit;
	return {
		mode,
		width,
		height,
		texelsPerUnit: k,
		imageWidth: Math.max(1, Math.round(width * k)),
		imageHeight: Math.max(1, Math.round(height * k)),
		cubes,
		requested: requested || 'auto',
		used: totalArea,
		warnings,
	};
}

function positionToOffset(packed, key, fp) {
	const pos = packed?.positions.get(key);
	if (!pos) return [0, 0];
	return [pos.x - fp.x, pos.y - fp.y];
}

// Same orientation as Box UV: side faces upright, up flipped on both axes, down flipped horizontally.
function orientFace(face, x, y, w, h) {
	if (face === 'up') return [x + w, y + h, x, y];
	if (face === 'down') return [x + w, y, x, y + h];
	return [x, y, x + w, y + h];
}

function sameSize(a, b) {
	return Math.abs(a[0] - b[0]) < 1e-6 && Math.abs(a[1] - b[1]) < 1e-6 && Math.abs(a[2] - b[2]) < 1e-6;
}

function pow2(v) {
	let p = 16;
	while (p < v) p *= 2;
	return p;
}

/** Texel rectangles actually painted for a cube (non-empty faces), in UV units. */
export function paintedFaces(cubeUV) {
	const out = [];
	for (const face of FACE_KEYS) {
		const uv = cubeUV.faces[face];
		if (cubeUV.enabled[face] && faceHasArea(uv)) out.push({ face, uv });
	}
	return out;
}
