// Texture painter. For every texel of every UV face it reconstructs the 3D point on the
// cube surface (Blockbench's UVToLocal), transforms it with the rest pose and lets the
// material shader colour it. Bioluminescent parts are written to a separate glow layer.

import { geometryBounds } from '../generator/builder.js';
import { applyAffine, boneWorldTransforms, cubeWorldTransform, mat3Apply, norm3, sub3 } from '../generator/math3d.js';
import { hash01, hashInts, hashString, spots3 } from '../generator/random.js';
import { clamp } from '../generator/util.js';
import { FACE_NORMALS, cubeSize, uvRect } from '../uv/box_uv.js';
import { paintedFaces } from '../uv/layout.js';
import { RGBAImage } from './image.js';
import { DEFAULT_COLORS, MATERIALS, PALETTE_FALLBACK, flatMaterialKey, spanT } from './materials.js';
import { gradeColor, hexToRgb, makeRamp, mixRgb } from './palette.js';
import { shapeContains } from './shapes.js';

/** Main palette key of each material (the one a per-cube colorKey replaces). */
const MAIN_KEYS = { skin: 'body', glow: 'lure', eye: 'eye_iris' };

/** Point on a cube face for face-relative lerp factors (Blockbench CubeFace.UVToLocal). */
export function facePoint(cube, face, lx, ly) {
	const f = cube.from, t = cube.to;
	const L = (a, b, k) => a + (b - a) * k;
	switch (face) {
		case 'east':
			return [t[0], L(t[1], f[1], ly), L(t[2], f[2], lx)];
		case 'west':
			return [f[0], L(t[1], f[1], ly), L(f[2], t[2], lx)];
		case 'up':
			return [L(f[0], t[0], lx), t[1], L(f[2], t[2], ly)];
		case 'down':
			return [L(f[0], t[0], lx), f[1], L(t[2], f[2], ly)];
		case 'south':
			return [L(f[0], t[0], lx), L(t[1], f[1], ly), t[2]];
		default: // north
			return [L(t[0], f[0], lx), L(t[1], f[1], ly), f[2]];
	}
}

/** Palette with preset grading, individual variation, fallbacks and cached ramps. */
export class PaletteBank {
	constructor(palette, ctx, variation = {}) {
		this.src = palette || {};
		this.ctx = ctx;
		this.variation = variation;
		this.cache = new Map();
		this.rampCache = new Map();
	}
	raw(key) {
		let k = key;
		const seen = new Set();
		while (k && !seen.has(k)) {
			seen.add(k);
			if (this.src[k]) return hexToRgb(this.src[k]);
			k = PALETTE_FALLBACK[k];
		}
		return hexToRgb(DEFAULT_COLORS[key] || DEFAULT_COLORS.body);
	}
	color(key, fallback = null) {
		if (this.cache.has(key)) return this.cache.get(key);
		if (fallback && !this.src[key] && !(key in DEFAULT_COLORS)) return fallback;
		const st = this.ctx.style;
		const isGlow = key.startsWith('glow');
		const graded = gradeColor(this.raw(key), {
			saturation: isGlow ? 1 : st.saturation,
			brightness: isGlow ? 1 : st.brightness,
			hue: this.variation.hue || 0,
			lightness: isGlow ? 0 : this.variation.lightness || 0,
		});
		this.cache.set(key, graded);
		return graded;
	}
	ramp(key) {
		if (this.rampCache.has(key)) return this.rampCache.get(key);
		const st = this.ctx.style;
		const r = makeRamp(this.color(key), {
			steps: st.rampSteps,
			contrast: st.contrast,
			hueShift: st.hueShift,
			shadowHue: st.shadowHue,
			highlightHue: st.highlightHue,
		});
		this.rampCache.set(key, r);
		return r;
	}
}

/**
 * Does a texel light up under a glow pattern (parts template `glow_pattern`)? 0..1.
 *   spots   scattered round photophores      stripes  bands across the part's long axis
 *   edges   1-texel outline of each face       rim      lower band (bell rims, lips)
 */
export function glowPatternHit(pattern, s, seed) {
	const [x, y, z] = s.world;
	switch (pattern) {
		case 'spots':
			return spots3(x * 0.55, y * 0.55, z * 0.55, seed + 41, 0.4).distance < 0.28 ? 1 : 0;
		case 'stripes': {
			// bands across the part, counted along its whole length
			const sp = s.cube.meta.span;
			const u = sp ? (spanT(s) * sp.length) / 2.5 : s.local[s.size.indexOf(Math.max(...s.size))] / 2.5;
			return u - Math.floor(u) < 0.34 ? 1 : 0;
		}
		case 'edges': {
			// broken glowing ridge: upper edge of the side faces and the rim of the top face
			if (s.face === 'down' || hash01(s.tx, s.ty, 43, seed) < 0.3) return 0;
			const [w, h] = s.faceTexels;
			const fx = s.lx * w, fy = s.ly * h;
			if (s.face === 'up') return (w >= 3 && (fx < 1 || fx > w - 1)) || (h >= 3 && (fy < 1 || fy > h - 1)) ? 1 : 0;
			return h >= 3 && fy < 1 ? 1 : 0;
		}
		case 'rim':
			// band at the base of the whole part (bell rims, lips)
			return s.face !== 'up' && s.face !== 'down' && spanT(s) < 0.14 ? 1 : 0;
		default:
			return 0;
	}
}

/** Resolves glow entries of the definition into cube intensities and surface spots. */
function resolveGlow(geometry, definition, ctx, worlds) {
	const exGlow = ctx.ex('glow');
	const cubeGlow = new Map(); // cube name -> {intensity, key}
	const spots = [];
	const matches = (bone, part) => bone.name === part || bone.role === part || (part === 'eyes' && bone.role === 'eye');

	// Template-level glow (esca, photophores placed by the template)
	for (const cube of geometry.cubes) {
		if (cube.glow) cubeGlow.set(cube.name, { intensity: cube.glow.intensity ?? 1, key: cube.glow.color || 'glow', uniform: !!cube.glow.uniform, pattern: cube.glow.pattern || null });
	}
	const boneByName = new Map(geometry.bones.map((b) => [b.name, b]));
	for (const spot of geometry.glowSpots || []) {
		const bone = boneByName.get(spot.bone);
		const pos = bone ? applyAffine(worlds.get(bone.name), sub3(spot.pos, bone.pivot)) : spot.pos;
		spots.push({ ...spot, pos, radius: spot.radius * Math.sqrt(exGlow), key: spot.color || 'glow' });
	}

	for (const entry of definition.glow || []) {
		if (entry.enabled === false) continue;
		const intensity = clamp(entry.intensity ?? 1, 0, 2);
		const key = entry.color || 'glow';
		const bones = geometry.bones.filter((b) => matches(b, entry.part));
		if (!bones.length) continue;
		const boneNames = new Set(bones.map((b) => b.name));
		if (!entry.anchor) {
			for (const cube of geometry.cubes) {
				if (boneNames.has(cube.bone)) cubeGlow.set(cube.name, { intensity, key, uniform: cube.material !== 'glow' && cube.material !== 'eye' });
			}
			continue;
		}
		const radiusOf = (r) => (r ?? 0.7) * (entry.size ?? 1) * Math.sqrt(exGlow);
		if (entry.scatter > 0) {
			// Random points on the part's surface (deterministic per seed): speckled bioluminescence
			const rng = ctx.rng.fork(`glow:${entry.part}:${entry.scatter}`);
			const cubes = geometry.cubes.filter((c) => boneNames.has(c.bone));
			const faces = [];
			for (const cube of cubes) {
				const sz = cubeSize(cube);
				for (const [face, area] of [['up', sz[0] * sz[2]], ['north', sz[0] * sz[1]], ['south', sz[0] * sz[1]], ['east', sz[2] * sz[1]], ['west', sz[2] * sz[1]]]) {
					if (area > 0 && !(entry.faces && !entry.faces.includes(face))) faces.push({ cube, face, area });
				}
			}
			const total = faces.reduce((a, f) => a + f.area, 0);
			for (let i = 0; i < Math.min(128, entry.scatter) && total > 0; i++) {
				let pick = rng.next() * total;
				const f = faces.find((x) => (pick -= x.area) <= 0) || faces[faces.length - 1];
				const bone = boneByName.get(f.cube.bone);
				const W = cubeWorldTransform(f.cube, bone, worlds.get(bone.name));
				const pos = applyAffine(W, facePoint(f.cube, f.face, 0.1 + 0.8 * rng.next(), 0.1 + 0.8 * rng.next()));
				spots.push({ pos, radius: radiusOf(entry.radius) * (0.75 + 0.5 * rng.next()), intensity: intensity * (0.7 + 0.3 * rng.next()), key });
			}
			continue;
		}
		const b = geometryBounds(geometry, (c) => boneNames.has(c.bone));
		const count = Math.max(1, Math.min(64, Math.round(entry.count ?? 1)));
		const step = entry.step || [0, 0, 0];
		const radius = radiusOf(entry.radius);
		for (let i = 0; i < count; i++) {
			const pos = [0, 1, 2].map((a) => b.min[a] + clamp(entry.anchor[a], 0, 1) * b.size[a] + step[a] * i);
			spots.push({ pos, radius, intensity, key });
			if (entry.mirror && Math.abs(pos[0]) > 0.01) spots.push({ pos: [-pos[0], pos[1], pos[2]], radius, intensity, key });
		}
	}
	return { cubeGlow, spots };
}

/**
 * Paints the base texture and the glow layer.
 * @returns {{base: RGBAImage, glow: RGBAImage|null, glowPixels: number, palette: PaletteBank}}
 */
export function paintTextures({ geometry, uv, definition }, ctx) {
	const k = uv.texelsPerUnit;
	const base = new RGBAImage(uv.imageWidth, uv.imageHeight);
	const glow = new RGBAImage(uv.imageWidth, uv.imageHeight);
	const worlds = boneWorldTransforms(geometry);
	const bones = new Map(geometry.bones.map((b) => [b.name, b]));
	const bounds = geometryBounds(geometry);
	const variation = definition._variation?.color || {};
	const palette = new PaletteBank(definition.palette, ctx, variation);
	const style = { faceShade: 0.5, shadowHue: 265, highlightHue: 195, ...ctx.style };
	const tex = { countershade: 1, pattern: 'speckle', pattern_strength: 0.6, pattern_scale: 1, fin_ray_spacing: 1.5, ...(definition.texture || {}) };
	const seed = hashInts(ctx.seed, hashString('texture'), ctx.settings.textureSeed || 0);
	const glowRgb = palette.color('glow');
	const glowMuted = gradeColor(glowRgb, { saturation: 0.55, brightness: 0.62 });
	const flat = !ctx.settings.generateTexture;
	const { cubeGlow, spots } = resolveGlow(geometry, definition, ctx, worlds);
	const glowStrength = ctx.style.glowStrength ?? 1;

	const env = {
		definition,
		seed,
		style,
		tex,
		palette,
		glowMuted,
		ramp: (key) => palette.ramp(key),
		color: (key, fallback) => palette.color(key, fallback),
		heightFrac: (y) => (bounds.size[1] > 0 ? (y - bounds.min[1]) / bounds.size[1] : 0.5),
	};

	const glowColorAt = (key, intensity) => {
		const c = palette.color(key);
		const i = clamp(intensity * glowStrength, 0, 1);
		const base = mixRgb([0, 0, 0], c, 0.35 + 0.65 * i);
		return i > 0.8 ? mixRgb(base, [255, 255, 255], (i - 0.8) * 1.5) : base;
	};

	let glowPixels = 0;
	for (const cube of geometry.cubes) {
		const cuv = uv.cubes[cube.name];
		if (!cuv || cuv.sharedFrom) continue;
		const bone = bones.get(cube.bone);
		const W = cubeWorldTransform(cube, bone, worlds.get(cube.bone));
		const size = cubeSize(cube);
		const material = cube.material;
		const cg = cubeGlow.get(cube.name);
		for (const { face, uv: faceUV } of paintedFaces(cuv)) {
			const rect = uvRect(faceUV);
			const x0 = Math.round(rect.x * k), y0 = Math.round(rect.y * k);
			const x1 = Math.round((rect.x + rect.w) * k), y1 = Math.round((rect.y + rect.h) * k);
			const faceTexels = [x1 - x0, y1 - y0];
			const normal = norm3(mat3Apply(W.m, FACE_NORMALS[face]));
			const faceMaterial = cube.faceMaterials[face] || material;
			const shader = MATERIALS[faceMaterial] || MATERIALS.skin;
			// meta.colorKey recolours the material's main colour (e.g. a "parts" template part colour)
			const override = cube.meta.colorKey;
			const main = override ? MAIN_KEYS[faceMaterial] || flatMaterialKey(faceMaterial) : null;
			const faceEnv = override
				? { ...env, ramp: (key) => palette.ramp(key === main ? override : key), color: (key, fb) => palette.color(key === main ? override : key, fb) }
				: env;
			for (let ty = y0; ty < y1; ty++) {
				for (let tx = x0; tx < x1; tx++) {
					const U = (tx + 0.5) / k, V = (ty + 0.5) / k;
					const lx = clamp((U - faceUV[0]) / (faceUV[2] - faceUV[0]), 0, 1);
					const ly = clamp((V - faceUV[1]) / (faceUV[3] - faceUV[1]), 0, 1);
					const local = facePoint(cube, face, lx, ly);
					const rel = [0, 1, 2].map((a) => (size[a] > 0 ? (local[a] - cube.from[a]) / size[a] : 0.5));
					const world = applyAffine(W, local);
					const sample = { cube, bone, face, lx, ly, local, rel, world, normal, size, tx, ty, faceTexels, texel: 1 / k };
					if (cube.shape && !shapeContains(cube.shape, rel)) continue;

					let rgb, alpha, glowFactor = 0;
					if (flat) {
						rgb = palette.color(override || flatMaterialKey(faceMaterial));
						alpha = faceMaterial === 'dome' ? 96 : 255;
						glowFactor = faceMaterial === 'glow' ? 1 : 0;
					} else {
						const out = shader(sample, faceEnv);
						rgb = out.rgb;
						alpha = out.alpha ?? 255;
						glowFactor = out.glow ?? 0;
					}

					// Glow: whole cube (esca, configured parts) and surface spots (photophores)
					let gI = 0, gKey = 'glow';
					if (cg) {
						gI = cg.pattern ? cg.intensity * glowPatternHit(cg.pattern, sample, seed) : cg.intensity * (cg.uniform ? 1 : glowFactor || (faceMaterial === 'glow' ? 1 : 0));
						gKey = cg.key;
					}
					for (const spot of spots) {
						const d = Math.hypot(world[0] - spot.pos[0], world[1] - spot.pos[1], world[2] - spot.pos[2]);
						if (d < spot.radius) {
							const i = spot.intensity * (1 - (d / spot.radius) ** 2 * 0.6);
							if (i > gI) {
								gI = i;
								gKey = spot.key;
							}
						}
					}
					if (gI > 0.04 && ctx.settings.glowLayer) {
						if (!flat && faceMaterial !== 'glow') rgb = mixRgb(rgb, gradeColor(palette.color(gKey), { saturation: 0.55, brightness: 0.62 }), clamp(style.glowBleed * 1.7 * gI, 0, 0.8));
						const g = flat ? palette.color(gKey) : glowColorAt(gKey, gI);
						glow.set(tx, ty, [g[0], g[1], g[2], 255]);
						glowPixels++;
					} else if (gI > 0.04 && !flat) {
						// No glow layer: bake a brighter (but still muted) organ into the base texture
						rgb = mixRgb(rgb, palette.color(gKey), clamp(0.55 * gI, 0, 0.7));
					}
					base.set(tx, ty, [rgb[0], rgb[1], rgb[2], alpha]);
				}
			}
		}
	}
	return { base, glow: glowPixels > 0 && ctx.settings.glowLayer ? glow : null, glowPixels, palette, spots };
}

