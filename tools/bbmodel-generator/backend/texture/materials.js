// Material shaders: turn a texel's 3D sample (position, face, part) into a colour on a
// pixel-art ramp. Every material works in model space, so patterns continue across faces
// and cubes instead of restarting per UV island.

import { fbm3, hash01, spots3 } from '../generator/random.js';
import { clamp, smoothstep } from '../generator/util.js';
import { mixRgb, rampColor } from './palette.js';
import { shapeCoords } from './shapes.js';
import { GRID_CHARS } from '../image/profile.js';

/** Default colours used when a definition's palette lacks a key (after the fallback chain). */
export const DEFAULT_COLORS = {
	body: '#34394a',
	belly: '#434a5a',
	fin: '#2c3140',
	lure: '#3a4252',
	mouth: '#3a1f2b',
	lip: '#2a2f3c',
	teeth: '#d6d0bd',
	eye: '#101218',
	eye_iris: '#56657a',
	eye_ring: '#262b36',
	glow: '#86f4e6',
	shell: '#6f6a7d',
	leg: '#625d70',
	plume: '#9a2e38',
	tube: '#d4cdbd',
	dome: '#a4dccb',
	membrane: '#3a3346',
	gill: '#7c3a44',
	sucker: '#b99a8e',
	flesh: '#b86b7c',
	accent: '#5a6478',
	claw: '#e0dccf',
	claw_tip: '#7a7068',
	rock: '#3b3533',
	scale: '#3a3b40',
	setae: '#e4dac4',
};

/** Palette fallback chain: key -> next key to try. */
export const PALETTE_FALLBACK = {
	belly: 'body',
	fin: 'body',
	lure: 'body',
	lip: 'body',
	eye_ring: 'body',
	shell: 'body',
	leg: 'shell',
	claw: 'shell',
	claw_tip: 'claw',
	scale: 'shell',
	setae: 'leg',
	membrane: 'fin',
	sucker: 'belly',
	accent: 'body',
	flesh: 'body',
	gill: 'mouth',
};

const faceShade = { up: 0.35, down: -0.85 };

function texelNoise(s, env, salt = 0) {
	return (hash01(s.tx, s.ty, salt, env.seed) - 0.5) * 2;
}

/** Common value (ramp offset) for organic surfaces. */
function organicValue(s, env, { countershade = 1, noise = 1, pattern = true, faces = 1 } = {}) {
	const st = env.style;
	let v = 0;
	const h = env.heightFrac(s.world[1]);
	v += st.countershade * env.tex.countershade * countershade * (0.5 - h) * 1.8;
	v += (faceShade[s.face] || 0) * st.faceShade * faces;
	const f = st.noiseScale ?? 0.3; // higher = finer mottling
	const n = fbm3(s.world[0] * f, s.world[1] * f, s.world[2] * f, env.seed, 3);
	v += (n - 0.5) * 3.4 * st.noise * noise;
	if (pattern) v += patternValue(s, env);
	v += texelNoise(s, env) * st.pixelNoise;
	return v;
}

/** Skin patterns selected by the definition (texture.pattern). */
export function patternValue(s, env) {
	const t = env.tex;
	const strength = (t.pattern_strength ?? 0.6) * env.style.pattern;
	const scale = t.pattern_scale ?? 1;
	const [x, y, z] = s.world;
	switch (t.pattern) {
		case 'speckle': {
			const sp = spots3(x * 0.9 / scale, y * 0.9 / scale, z * 0.9 / scale, env.seed + 11, 0.32 * (env.style.speckle / 0.3));
			return sp.distance < 0.3 ? 1.3 * strength : 0;
		}
		case 'spots': {
			const sp = spots3(x * 0.42 / scale, y * 0.42 / scale, z * 0.42 / scale, env.seed + 13, 0.45);
			return sp.distance < 0.42 ? -1.4 * strength : sp.distance < 0.52 ? -0.5 * strength : 0;
		}
		case 'mottled': {
			const n = fbm3(x * 0.16 / scale, y * 0.16 / scale, z * 0.16 / scale, env.seed + 17, 2);
			return (n - 0.5) * 4 * strength;
		}
		case 'stripes': {
			const band = Math.sin((z / scale) * 1.4 + fbm3(x * 0.2, y * 0.2, z * 0.2, env.seed + 19, 2) * 2);
			return band > 0.55 ? -1.2 * strength : 0;
		}
		case 'scales': {
			const row = Math.floor(y / (1.5 * scale));
			const zz = z / (1.5 * scale) + (row % 2) * 0.5;
			const edge = zz - Math.floor(zz) < 0.28 || y / (1.5 * scale) - row < 0.22;
			return edge ? -0.9 * strength : 0.2 * strength;
		}
		case 'bands': {
			const band = Math.sin((z + y) / scale * 1.1);
			return band > 0.6 ? 1.1 * strength : 0;
		}
		default:
			return 0;
	}
}

function edgeTexel(s) {
	const w = s.faceTexels[0], h = s.faceTexels[1];
	const fx = s.lx * w, fy = s.ly * h;
	const onX = w >= 3 && (fx < 1 || fx > w - 1);
	const onY = h >= 3 && (fy < 1 || fy > h - 1);
	return onX || onY;
}

/**
 * Hard countershading (style.bellyLine > 0): the lower part of a lying / horizontal cube gets a
 * lighter belly with a ragged pixel boundary. Uses palette "belly" when the definition sets one.
 */
function isBelly(s, env) {
	// A part with its own belly colour always shows it; otherwise the preset decides
	const line = env.style.bellyLine || (s.cube.meta.bellyKey ? 0.36 : 0);
	if (line <= 0 || s.face === 'up') return false;
	if (s.size[1] > Math.max(s.size[0], s.size[2]) * 1.2) return false; // upright stalks, tentacles
	if (s.face === 'down') return true;
	const ragged = (hash01(s.tx, s.ty, 29, env.seed) - 0.5) * 0.16 + (fbm3(s.world[0] * 0.5, 0, s.world[2] * 0.5, env.seed + 31, 2) - 0.5) * 0.3;
	return s.rel[1] < line + ragged;
}

/**
 * Ramp colour of a part texel, blended toward meta.grad's colour from the part's base to its tip
 * (parts template `color_to`); the blend edge is dithered so it reads as pixel art.
 */
function partColor(s, env, key, v) {
	const rgb = rampColor(env.ramp(key), v);
	const { gradKey, span } = s.cube.meta;
	if (!gradKey || !span) return rgb;
	const t = clamp(spanT(s) + (hash01(s.tx, s.ty, 37, env.seed) - 0.5) * 0.18, 0, 1);
	return mixRgb(rgb, rampColor(env.ramp(gradKey), v), smoothstep(0.15, 0.95, t));
}

/** Position of a texel along its whole part (meta.span): 0 at the base, 1 at the tip. */
export function spanT(s) {
	const sp = s.cube.meta.span;
	return sp ? clamp((sp.sign * (s.local[sp.axis] - sp.start)) / sp.length, 0, 1) : s.rel[1];
}

function skin(s, env, key = 'body', opts = {}) {
	let v = organicValue(s, env, opts);
	let belly = false;
	if (opts.countershade !== 0 && isBelly(s, env)) {
		belly = true;
		if (s.cube.meta.bellyKey) key = s.cube.meta.bellyKey; // parts template `belly`
		else if (env.definition?.palette?.belly && key === 'body' && !s.cube.meta.colorKey) key = 'belly';
		else v += env.style.bellyLift ?? 2.4;
		v -= texelNoise(s, env, 5) * env.style.pixelNoise * 0.4; // calmer belly
	}
	if (env.style.edge > 0 && edgeTexel(s)) v -= env.style.edge * 1.4;
	const slits = s.cube.meta.slits;
	if (slits && (s.face === 'east' || s.face === 'west') && s.rel[1] > 0.22 && s.rel[1] < 0.82) {
		for (let k = 0; k < slits.count; k++) {
			if (Math.abs(s.local[2] - (slits.start + k * slits.spacing)) < s.texel * 0.55) v -= 1.8;
		}
	}
	const vc = v * env.style.contrast;
	return { rgb: belly ? rampColor(env.ramp(key), vc) : partColor(s, env, key, vc), alpha: 255 };
}

export const MATERIALS = {
	skin: (s, env) => skin(s, env, 'body'),
	belly: (s, env) => skin(s, env, 'belly', { countershade: 0.4 }),
	lure: (s, env) => {
		const r = skin(s, env, 'lure', { pattern: false });
		// Stalk brightens toward the glowing tip
		const t = s.cube.meta.tipFactor ?? 0;
		if (t > 0) r.rgb = mixRgb(r.rgb, env.glowMuted, 0.25 * t * env.style.glowBleed / 0.3);
		return r;
	},
	fin: (s, env) => {
		const shape = s.cube.shape;
		let v = organicValue(s, env, { countershade: 0.3, noise: 0.6, pattern: false, faces: 0 });
		if (shape) {
			const { s: along, u, acrossAxis } = shapeCoords(shape, s.rel);
			const acrossUnits = u * s.size[acrossAxis];
			const spacing = env.tex.fin_ray_spacing ?? 1.5;
			const phase = acrossUnits / spacing;
			if (phase - Math.floor(phase) < 0.45 && along < 0.9) v -= 1.0 * (env.style.finRays ?? 1);
			v += smoothstep(0.55, 1, along) * 1.2;
		}
		return { rgb: partColor(s, env, 'fin', v * env.style.contrast), alpha: env.style.finAlpha };
	},
	membrane: (s, env) => {
		let v = organicValue(s, env, { countershade: 0.2, noise: 0.8, pattern: false, faces: 0.3 });
		const shape = s.cube.shape;
		if (shape) v += smoothstep(0.6, 1, shapeCoords(shape, s.rel).s) * 1.3;
		return { rgb: rampColor(env.ramp('membrane'), v * env.style.contrast), alpha: s.cube.meta.alpha ?? env.style.finAlpha };
	},
	mouth: (s, env) => {
		// Lip ring along the open edges, getting darker toward the throat.
		const lipWidth = s.cube.meta.lip ?? 0;
		if (lipWidth > 0 && (s.face === 'up' || s.face === 'down')) {
			const [w, , d] = s.size;
			const ex = Math.min(s.rel[0] * w, (1 - s.rel[0]) * w);
			const ez = s.rel[2] * d; // front edge (north) at rel z = 0
			if (ex < lipWidth || ez < lipWidth) return skin(s, env, 'lip', { pattern: false });
		}
		const depth = s.cube.meta.depthAxis === 'z' || !s.cube.meta.depthAxis ? s.rel[2] : s.rel[1];
		let v = 0.8 - depth * 2.4 + texelNoise(s, env, 3) * 0.35;
		const n = fbm3(s.world[0] * 0.5, s.world[1] * 0.5, s.world[2] * 0.5, env.seed + 5, 2);
		v += (n - 0.5) * 1.5;
		return { rgb: rampColor(env.ramp('mouth'), v), alpha: 255 };
	},
	teeth: (s, env) => {
		const dir = s.cube.meta.toothDir ?? 1; // +1 points up (+Y), -1 down, 2/-2 along z
		const axis = Math.abs(dir) === 2 ? 2 : 1;
		const t = dir > 0 ? s.rel[axis] : 1 - s.rel[axis];
		let v = -1.4 + 2.6 * t + texelNoise(s, env, 7) * 0.25;
		if (s.face === 'down' && dir > 0) v -= 1;
		return { rgb: rampColor(env.ramp('teeth'), v), alpha: 255 };
	},
	eye: (s, env) => {
		const front = s.cube.meta.eyeFace;
		const ramp = env.ramp('eye');
		if (s.face !== front && s.face !== s.cube.meta.eyeFace2) {
			return skin(s, env, 'eye_ring', { pattern: false });
		}
		const fw = s.faceTexels[0], fh = s.faceTexels[1];
		if (fw <= 1.01 || fh <= 1.01) return { rgb: rampColor(ramp, 0.5), alpha: 255, glow: 1 };
		const dx = s.lx - 0.5, dy = s.ly - 0.5;
		const r = Math.hypot(dx, dy) * 2;
		const pupil = s.cube.meta.pupil ?? 0.5;
		const px = Math.floor(s.lx * fw), py = Math.floor(s.ly * fh);
		// Specular glint in the upper corner toward the creature's front (set per eye by the template)
		const gx = s.cube.meta.glintLeft ? (fw >= 4 ? 1 : 0) : fw >= 4 ? fw - 2 : fw - 1;
		const gy = fh >= 4 ? 1 : 0;
		if (fw >= 2 && fh >= 2 && px === gx && py === gy) {
			return { rgb: env.color('eye_glint', [220, 235, 238]), alpha: 255, glow: 0.8 };
		}
		if (r < pupil || fw <= 2) return { rgb: rampColor(ramp, -2 + texelNoise(s, env, 9) * 0.3), alpha: 255, glow: 1 };
		const iris = env.ramp('eye_iris');
		return { rgb: rampColor(iris, 1 - r * 1.5), alpha: 255, glow: 0.45 };
	},
	/** Whole-cube light organ (esca, photophore bulbs, tail lure). */
	glow: (s, env) => {
		const c = Math.max(Math.abs(s.rel[0] - 0.5), Math.abs(s.rel[1] - 0.5), Math.abs(s.rel[2] - 0.5)) * 2;
		const base = env.ramp('lure');
		const muted = mixRgb(rampColor(base, 1), env.glowMuted, clamp(0.35 + env.style.glowBleed, 0, 0.9));
		return { rgb: rampColor([mixRgb(muted, [0, 0, 0], 0.2), muted, mixRgb(muted, [255, 255, 255], 0.12)], 1 - c * 1.6), alpha: 255, glow: 1 - 0.3 * c };
	},
	shell: (s, env) => {
		let v = organicValue(s, env, { countershade: -0.4, noise: 0.8 });
		const [w, h, d] = s.size;
		const seam = Math.min(s.rel[2] * d, (1 - s.rel[2]) * d);
		if (seam < s.texel * 0.99 && (s.face === 'up' || s.face === 'east' || s.face === 'west')) v -= 1.3;
		if ((s.face === 'east' || s.face === 'west' || s.face === 'north' || s.face === 'south') && (1 - s.rel[1]) * h < s.texel * 0.99) v += 0.9;
		if (s.face === 'up') {
			const rim = Math.min(s.rel[0] * w, (1 - s.rel[0]) * w);
			if (rim < s.texel * 0.99) v -= 0.6;
		}
		return { rgb: rampColor(env.ramp('shell'), v * env.style.contrast), alpha: 255 };
	},
	leg: (s, env) => {
		let v = organicValue(s, env, { countershade: 0.2, noise: 0.5, pattern: false });
		const axis = s.cube.meta.legAxis ?? 1;
		const end = Math.min(s.rel[axis], 1 - s.rel[axis]) * s.size[axis];
		if (end < s.texel * 0.99) v -= 1.1;
		return { rgb: rampColor(env.ramp(s.cube.meta.colorKey || 'leg'), v * env.style.contrast), alpha: 255 };
	},
	plume: (s, env) => {
		let v = organicValue(s, env, { countershade: 0.3, noise: 0.5, pattern: false, faces: 0.4 });
		const shape = s.cube.shape;
		const along = shape ? shapeCoords(shape, s.rel).s : s.rel[1];
		const rings = Math.sin(along * s.size[1] * 2.6);
		v += rings > 0.3 ? 0.9 : -0.4;
		v += smoothstep(0.6, 1, along) * 1.1;
		return { rgb: rampColor(env.ramp('plume'), v), alpha: 255 };
	},
	tube: (s, env) => {
		let v = organicValue(s, env, { countershade: 0, noise: 0.7, pattern: false, faces: 0.6 });
		const ring = s.world[1] * 0.9 + fbm3(s.world[0] * 0.3, 0, s.world[2] * 0.3, env.seed + 23, 2) * 1.5;
		if (ring - Math.floor(ring) < 0.22) v -= 1.1;
		v += (s.rel[1] - 0.5) * 1.4;
		if (s.face === 'up') v -= 2.5; // tube opening
		return { rgb: rampColor(env.ramp('tube'), v), alpha: 255 };
	},
	dome: (s, env) => {
		const edge = edgeTexel(s);
		const n = fbm3(s.world[0] * 0.4, s.world[1] * 0.4, s.world[2] * 0.4, env.seed + 29, 2);
		const v = (edge ? 1.5 : 0) + (n - 0.5) * 1.2;
		return { rgb: rampColor(env.ramp('dome'), v), alpha: edge ? Math.max(150, s.cube.meta.alpha ?? 0) : (s.cube.meta.alpha ?? 88) };
	},
	flesh: (s, env) => {
		const r = skin(s, env, 'flesh', { countershade: 0.5, noise: 1.2 });
		r.alpha = s.cube.meta.alpha ?? 255;
		return r;
	},
	gill: (s, env) => {
		let v = organicValue(s, env, { countershade: 0.2, noise: 0.5, pattern: false });
		const shape = s.cube.shape;
		if (shape) v += smoothstep(0.5, 1, shapeCoords(shape, s.rel).s) * 1.4 - 0.4;
		return { rgb: rampColor(env.ramp('gill'), v), alpha: 255 };
	},
	sucker: (s, env) => {
		let v = organicValue(s, env, { countershade: 0, noise: 0.5, pattern: false });
		const [w, , d] = s.size;
		const col = Math.floor(s.rel[0] * Math.max(1, Math.round(w / 1.5)));
		const zz = (s.rel[2] * d) / 1.5 + (col % 2) * 0.5;
		const f = zz - Math.floor(zz);
		if (f > 0.25 && f < 0.75) v += 1.2;
		return { rgb: rampColor(env.ramp('sucker'), v), alpha: 255 };
	},
	claw: (s, env) => skin(s, env, 'claw', { countershade: -0.3, pattern: false }),
	setae: (s, env) => {
		const along = s.cube.shape ? shapeCoords(s.cube.shape, s.rel).s : s.rel[1];
		const v = -1 + 1.8 * along + texelNoise(s, env, 13) * 0.4;
		return { rgb: rampColor(env.ramp('setae'), v), alpha: 255 };
	},
	photo: photoShader,
	rock: (s, env) => {
		let v = organicValue(s, env, { countershade: -0.5, noise: 1.6, pattern: false });
		const sp = spots3(s.world[0] * 0.7, s.world[1] * 0.7, s.world[2] * 0.7, env.seed + 31, 0.5);
		if (sp.distance < 0.35) v += sp.hash > 0.5 ? 1.2 : -1.2;
		return { rgb: rampColor(env.ramp('rock'), v), alpha: 255 };
	},
};

/** Parsed photo grids (params.photo.grid arrays), cached per definition. */
const photoGrids = new WeakMap();

/**
 * Photo projection: maps the texel back onto the silhouette grid of the photo template.
 * Front / back faces read the grid directly; side faces are the same view rotated 90° about
 * the vertical axis (radial symmetry); top / bottom faces read outward from the slice centre.
 */
function photoShader(s, env) {
	const P = env.definition?.params?.photo;
	const m = s.cube.meta.photo;
	if (!P || !m || !Array.isArray(P.grid)) return skin(s, env, 'body');
	let grid = photoGrids.get(P.grid);
	if (!grid) photoGrids.set(P.grid, (grid = P.grid.map((line) => line.split(''))));
	const [x, y, z] = s.local;
	const eps = s.face === 'up' ? -1e-3 : s.face === 'down' ? 1e-3 : 0;
	let row = m.rb - Math.floor((y + eps) / m.uL);
	let col;
	if (s.face === 'north' || s.face === 'south') col = m.cx0 - x / m.uS;
	else if (s.face === 'east') col = m.cc + z / m.uD;
	else if (s.face === 'west') col = m.cc - z / m.uD;
	else {
		const dx = m.cx0 - x / m.uS - m.cc;
		col = m.cc + (dx >= 0 ? 1 : -1) * Math.max(Math.abs(dx), Math.abs(z / m.uD));
	}
	row = Math.min(grid.length - 1, Math.max(0, row));
	// Nearest filled cell: same row outward first, then neighbouring rows
	let ch = null;
	for (let dr = 0; dr < grid.length && !ch; dr++) {
		for (const r of dr ? [row - dr, row + dr] : [row]) {
			const line = grid[r];
			if (!line) continue;
			const c0 = Math.min(line.length - 1, Math.max(0, Math.floor(col)));
			for (let dc = 0; dc < line.length && !ch; dc++) {
				for (const c of dc ? [c0 - dc, c0 + dc] : [c0]) if (line[c] && line[c] !== '.') (ch = ch || line[c]);
			}
			if (ch) break;
		}
	}
	const key = `p${Math.max(0, GRID_CHARS.indexOf(ch || '0'))}`;
	const st = env.style;
	const v = (faceShade[s.face] || 0) * st.faceShade * 0.6 + texelNoise(s, env, 29) * st.pixelNoise * 0.5;
	return { rgb: rampColor(env.ramp(key), v * st.contrast), alpha: 255 };
}

/** Flat colour used when "Generate Texture" is off (texture template). */
export function flatMaterialKey(material) {
	switch (material) {
		case 'skin':
			return 'body';
		case 'glow':
			return 'lure';
		default:
			return material in DEFAULT_COLORS ? material : 'body';
	}
}
