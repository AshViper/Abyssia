// Headless software renderer for generated models (no WebGL): orthographic views with the
// same placement, box-UV mapping and Blockbench viewport shading as the GUI preview
// (bbmodel-generator/frontend/preview/model_mesh.js). Used for dataset images and metrics.

import { RGBAImage } from '../../bbmodel-generator/backend/texture/image.js';
import { encodePNG } from '../../bbmodel-generator/backend/texture/png.js';
import { applyAffine, boneWorldTransforms, cubeWorldTransform, mat3Apply } from '../../bbmodel-generator/backend/generator/math3d.js';

export { encodePNG };

/** Camera basis per view: forward (into the screen) and up. right = forward × up. */
export const VIEWS = {
	front: { f: [0, 0, 1], u: [0, 1, 0] }, // looking at the -Z (north) face
	back: { f: [0, 0, -1], u: [0, 1, 0] },
	right: { f: [-1, 0, 0], u: [0, 1, 0] }, // from +X; the front of the model points right
	left: { f: [1, 0, 0], u: [0, 1, 0] },
	top: { f: [0, -1, 0], u: [0, 0, -1] }, // front of the model at the top of the image
	iso: yawPitch(-35, 30),
};

function yawPitch(yawDeg, pitchDeg) {
	const y = (yawDeg * Math.PI) / 180, p = (pitchDeg * Math.PI) / 180;
	// Start from the front view, rotate the camera around the model
	const f = [-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)];
	const u = [-Math.sin(y) * Math.sin(p), Math.cos(p), Math.cos(y) * Math.sin(p)];
	return { f, u };
}

const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];

// three.js BoxGeometry planes in Blockbench face order: [u, v, w, udir, vdir, widthAxis, heightAxis, depthAxis, depthSign], normal
const FACES = {
	east: { u: 2, v: 1, w: 0, ud: -1, vd: -1, W: 2, H: 1, D: 0, ds: 1, n: [1, 0, 0] },
	west: { u: 2, v: 1, w: 0, ud: 1, vd: -1, W: 2, H: 1, D: 0, ds: -1, n: [-1, 0, 0] },
	up: { u: 0, v: 2, w: 1, ud: 1, vd: 1, W: 0, H: 2, D: 1, ds: 1, n: [0, 1, 0] },
	down: { u: 0, v: 2, w: 1, ud: 1, vd: -1, W: 0, H: 2, D: 1, ds: -1, n: [0, -1, 0] },
	south: { u: 0, v: 1, w: 2, ud: 1, vd: -1, W: 0, H: 1, D: 2, ds: 1, n: [0, 0, 1] },
	north: { u: 0, v: 1, w: 2, ud: -1, vd: -1, W: 0, H: 1, D: 2, ds: -1, n: [0, 0, -1] },
};

const TRANSLUCENT = new Set(['dome', 'flesh', 'membrane']);

/**
 * World-space quads of every visible cube face with texture coordinates in image pixels.
 * @returns {Array<{p: number[][], uv: number[][], n: number[], cube: object}>}
 */
export function modelQuads(model, pose = null) {
	const { geometry, uv } = model;
	const bones = new Map(geometry.bones.map((b) => [b.name, b]));
	const world = boneWorldTransforms(geometry, pose);
	const sx = model.textures.base.width / uv.width, sy = model.textures.base.height / uv.height;
	const quads = [];
	for (const cube of geometry.cubes) {
		const cuv = uv.cubes[cube.name];
		if (!cuv) continue;
		const T = cubeWorldTransform(cube, bones.get(cube.bone), world.get(cube.bone));
		const size = [0, 1, 2].map((i) => cube.to[i] - cube.from[i]);
		const center = [0, 1, 2].map((i) => (cube.from[i] + cube.to[i]) / 2);
		const box = cuv.offset !== undefined;
		for (const [face, F] of Object.entries(FACES)) {
			if (!cuv.enabled[face]) continue;
			const w = size[F.W], h = size[F.H];
			if (w <= 0 || h <= 0) continue;
			const f = cuv.faces[face].slice();
			if (box) {
				for (let si = 0; si < 2; si++) {
					let m = 1 / 64;
					if (f[si] > f[si + 2]) m = -m;
					f[si] += m;
					f[si + 2] -= m;
				}
			}
			// BoxGeometry vertex order: (ix0,iy0) (ix1,iy0) (ix0,iy1) (ix1,iy1)
			const corners = [[0, 0], [1, 0], [0, 1], [1, 1]];
			const uvs = [[f[0], f[1]], [f[2], f[1]], [f[0], f[3]], [f[2], f[3]]];
			const p = corners.map(([ix, iy]) => {
				const v = [0, 0, 0];
				v[F.u] = (ix * w - w / 2) * F.ud;
				v[F.v] = (iy * h - h / 2) * F.vd;
				v[F.w] = (F.ds * size[F.D]) / 2;
				return applyAffine(T, [v[0] + center[0], v[1] + center[1], v[2] + center[2]]);
			});
			quads.push({ p, uv: uvs.map(([u, v]) => [u * sx, v * sy]), n: mat3Apply(T.m, F.n), cube });
		}
	}
	return quads;
}

/** Projected bounds of a model in a view: {minX, maxX, minY, maxY} in model units. */
export function viewBounds(quads, view) {
	const V = typeof view === 'string' ? VIEWS[view] : view;
	const r = cross(V.f, V.u);
	const b = { minX: Infinity, maxX: -Infinity, minY: Infinity, maxY: -Infinity };
	for (const q of quads)
		for (const p of q.p) {
			const x = dot(p, r), y = dot(p, V.u);
			b.minX = Math.min(b.minX, x);
			b.maxX = Math.max(b.maxX, x);
			b.minY = Math.min(b.minY, y);
			b.maxY = Math.max(b.maxY, y);
		}
	return b;
}

/** Frame that fits `bounds` (+margin) into a square image. */
export function fitFrame(bounds, margin = 0.06) {
	const cx = (bounds.minX + bounds.maxX) / 2, cy = (bounds.minY + bounds.maxY) / 2;
	const half = Math.max(bounds.maxX - bounds.minX, bounds.maxY - bounds.minY, 1) / 2;
	return { cx, cy, half: half * (1 + margin * 2) };
}

export function unionBounds(a, b) {
	return { minX: Math.min(a.minX, b.minX), maxX: Math.max(a.maxX, b.maxX), minY: Math.min(a.minY, b.minY), maxY: Math.max(a.maxY, b.maxY) };
}

/**
 * Renders one orthographic view.
 * @param {object} model generated model (generateModel result)
 * @param {object} o {view: name|{f,u}, size: px, frame?: {cx, cy, half}, background?: [r,g,b,a], mode?: 'texture'|'mask', quads?}
 * @returns {RGBAImage} alpha 0 = empty (unless a background is given)
 */
export function renderView(model, o = {}) {
	const V = typeof o.view === 'string' || !o.view ? VIEWS[o.view || 'front'] : o.view;
	const size = o.size || 256;
	const quads = o.quads || modelQuads(model);
	const frame = o.frame || fitFrame(viewBounds(quads, V));
	const r = cross(V.f, V.u);
	const img = new RGBAImage(size, size);
	const depth = new Float32Array(size * size).fill(Infinity);
	const k = size / (2 * frame.half);
	const tex = model.textures.base, glow = model.textures.glow;
	const toScreen = (p) => [(dot(p, r) - frame.cx) * k + size / 2, size / 2 - (dot(p, V.u) - frame.cy) * k, dot(p, V.f)];

	for (const q of quads) {
		const facing = dot(q.n, V.f);
		if (facing >= -1e-6) continue; // back face (FrontSide)
		const s = q.p.map(toScreen);
		const N = q.n;
		const light = (((1 + N[1]) / 2) * 0.5 + N[0] * N[0] * -0.15 + N[2] * N[2] * 0.05 + 0.5);
		const alphaTest = TRANSLUCENT.has(q.cube.material) ? 0.02 : 0.1;
		for (const [a, b, c] of [[0, 2, 1], [2, 3, 1]]) raster(s[a], s[b], s[c], q.uv[a], q.uv[b], q.uv[c], (px, py, z, u, v) => {
			const i = py * size + px;
			if (z >= depth[i]) return;
			let rgba;
			if (o.mode === 'mask') rgba = [255, 255, 255, 255];
			else {
				const tx = Math.min(tex.width - 1, Math.max(0, Math.floor(u)));
				const ty = Math.min(tex.height - 1, Math.max(0, Math.floor(v)));
				const t = tex.get(tx, ty);
				if (t[3] / 255 < alphaTest) return;
				rgba = [t[0] * light, t[1] * light, t[2] * light, 255];
				if (glow) {
					const g = glow.get(tx, ty);
					const ga = g[3] / 255;
					for (let ch = 0; ch < 3; ch++) rgba[ch] += g[ch] * ga;
				}
				for (let ch = 0; ch < 3; ch++) rgba[ch] = Math.min(255, Math.round(rgba[ch]));
			}
			depth[i] = z;
			img.set(px, py, rgba);
		});
	}
	if (o.background) {
		const bg = o.background;
		for (let i = 0; i < img.data.length; i += 4) if (img.data[i + 3] === 0) img.data.set([bg[0], bg[1], bg[2], bg[3] ?? 255], i);
	}
	img.frame = frame;
	return img;
}

// Triangle rasterizer (pixel centres, top-left-ish fill), affine attribute interpolation (orthographic)
function raster(A, B, C, ta, tb, tc, plot) {
	const minX = Math.max(0, Math.floor(Math.min(A[0], B[0], C[0])));
	const maxX = Math.ceil(Math.max(A[0], B[0], C[0]));
	const minY = Math.max(0, Math.floor(Math.min(A[1], B[1], C[1])));
	const maxY = Math.ceil(Math.max(A[1], B[1], C[1]));
	const area = (B[0] - A[0]) * (C[1] - A[1]) - (B[1] - A[1]) * (C[0] - A[0]);
	if (Math.abs(area) < 1e-9) return;
	for (let y = minY; y < maxY; y++) {
		for (let x = minX; x < maxX; x++) {
			const px = x + 0.5, py = y + 0.5;
			const w0 = ((B[0] - px) * (C[1] - py) - (B[1] - py) * (C[0] - px)) / area;
			const w1 = ((C[0] - px) * (A[1] - py) - (C[1] - py) * (A[0] - px)) / area;
			const w2 = 1 - w0 - w1;
			if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) continue;
			plot(x, y, w0 * A[2] + w1 * B[2] + w2 * C[2], w0 * ta[0] + w1 * tb[0] + w2 * tc[0], w0 * ta[1] + w1 * tb[1] + w2 * tc[1]);
		}
	}
}

/** Several views side by side in one image (for previews / VLM training). */
export function renderSheet(model, views = ['front', 'right', 'top', 'iso'], size = 192, background = [36, 44, 56, 255]) {
	const quads = modelQuads(model);
	const sheet = new RGBAImage(size * views.length, size);
	views.forEach((view, vi) => {
		const img = renderView(model, { view, size, quads, background });
		for (let y = 0; y < size; y++) sheet.data.set(img.data.subarray(y * size * 4, (y + 1) * size * 4), (y * sheet.width + vi * size) * 4);
	});
	return sheet;
}
