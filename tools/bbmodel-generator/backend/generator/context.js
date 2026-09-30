// Generation settings, presets and the per-run context handed to templates.

import { Random, hashInts, hashString, toSeed } from './random.js';
import { deepMerge } from './util.js';

export const DETAIL_LEVELS = ['low', 'medium', 'high'];
export const PIXEL_SCALES = [8, 16, 32];
export const TEXTURE_SIZES = ['auto', 16, 32, 64, 128, 256];
export const UV_MODES = ['box', 'per_face'];
export const MODEL_FORMATS = {
	modded_entity: 'Modded Entity (Java, built-in)',
	geckolib_model: 'GeckoLib Animated Model (plugin)',
	bedrock: 'Bedrock Entity',
	free: 'Generic Model',
};
export const FORMAT_VERSIONS = ['5.0', '4.10'];

export const DEFAULT_SETTINGS = Object.freeze({
	seed: 1,
	/** Extra seed for the texture only ("Generate Texture" repaints without touching the model). */
	textureSeed: 0,
	/** 0 = the canonical individual, 1 = full variation range of the definition. */
	variation: 0,
	preset: 'minecraft_deep_sea',
	detail: 'high',
	/** Pixels per block. 16 = vanilla (1 texel per model unit). */
	pixelScale: 16,
	/** Maximum number of cubes (0 = unlimited). Lowest-priority details are dropped first. */
	blockCount: 0,
	textureSize: 'auto',
	uvMode: 'box',
	modelFormat: 'modded_entity',
	formatVersion: '5.0',
	animationReady: true,
	generateTexture: true,
	generateUV: true,
	generateAnimation: true,
	glowLayer: true,
	/** Share UV space between mirrored left/right parts (Box UV mirror). Off = no overlapping UVs. */
	mirrorUV: false,
	/** Target overall size in model units; 0 keeps the definition's natural size. */
	size: { width: 0, height: 0, length: 0 },
	/** 'auto' = plain names for Modded Entity (valid Java fields), "animation.<id>.<name>" otherwise. */
	animationNaming: 'auto',
	modId: 'abyssia',
});

/** Built-in fallback that mirrors presets/minecraft_deep_sea.json. */
export const DEFAULT_PRESET = Object.freeze({
	id: 'minecraft_deep_sea',
	name: 'Minecraft Deep Sea',
	settings: { detail: 'high', pixelScale: 16, textureSize: 'auto', uvMode: 'box' },
	geometry: { exaggeration: { head: 1, eye: 1, teeth: 1, fin: 1, glow: 1, limb: 1 }, finThickness: 0 },
	texture: {
		saturation: 0.84,
		brightness: 0.97,
		contrast: 1.05,
		rampSteps: 7,
		hueShift: 1,
		shadowHue: 265,
		highlightHue: 195,
		noise: 0.26,
		pixelNoise: 0.2,
		speckle: 0.3,
		countershade: 0.4,
		faceShade: 0.5,
		edge: 0.12,
		pattern: 1,
		glowStrength: 1,
		glowBleed: 0.3,
		finAlpha: 255,
	},
	animation: { amplitude: 1, speed: 1 },
});

export function normalizeSettings(settings = {}) {
	const s = deepMerge(DEFAULT_SETTINGS, settings || {});
	s.detail = DETAIL_LEVELS.includes(s.detail) ? s.detail : 'high';
	s.pixelScale = PIXEL_SCALES.includes(Number(s.pixelScale)) ? Number(s.pixelScale) : 16;
	s.textureSize = s.textureSize === 'auto' ? 'auto' : TEXTURE_SIZES.includes(Number(s.textureSize)) ? Number(s.textureSize) : 'auto';
	s.uvMode = UV_MODES.includes(s.uvMode) ? s.uvMode : 'box';
	s.modelFormat = s.modelFormat in MODEL_FORMATS ? s.modelFormat : 'modded_entity';
	s.formatVersion = FORMAT_VERSIONS.includes(String(s.formatVersion)) ? String(s.formatVersion) : '5.0';
	s.blockCount = Math.max(0, Math.floor(Number(s.blockCount) || 0));
	s.variation = Math.min(1, Math.max(0, Number(s.variation) || 0));
	s.textureSeed = Math.max(0, Math.floor(Number(s.textureSeed) || 0));
	for (const key of ['width', 'height', 'length']) s.size[key] = Math.max(0, Number(s.size?.[key]) || 0);
	return s;
}

/**
 * Per-run context: settings, style, seeded randomness and quantisation helpers.
 * `scale` lets the pipeline resize a creature to a target width / height / length.
 */
export function createContext(definition, settings, preset, { scale = [1, 1, 1] } = {}) {
	const s = normalizeSettings(settings);
	const p = deepMerge(DEFAULT_PRESET, preset || {});
	const detail = DETAIL_LEVELS.indexOf(s.detail);
	const grid = s.pixelScale <= 8 ? 2 : 1;
	const seed = hashInts(toSeed(s.seed), hashString(definition?.id || 'creature'));
	const ex = p.geometry.exaggeration || {};
	const ctx = {
		settings: s,
		preset: p,
		style: p.texture,
		animStyle: p.animation,
		detail,
		detailName: s.detail,
		grid,
		texelsPerUnit: s.pixelScale / 16,
		seed,
		rng: new Random(seed),
		scale,
		/** Exaggeration factor for a feature ("eye", "head", "teeth"...), from the preset. */
		ex: (key) => (typeof ex[key] === 'number' ? ex[key] : 1),
		// Modelling rule: fins are single planes (zero thickness) regardless of the preset
		finThickness: 0,
		/** Picks a value by detail level from [low, medium, high]. */
		lod: (values) => (Array.isArray(values) ? values[Math.min(detail, values.length - 1)] : values),
		atLeast: (level) => detail >= DETAIL_LEVELS.indexOf(level),
		/** Quantises a size to the model grid (at least `min`, 0 stays 0 when allowed). */
		q(value, min = grid) {
			if (min === 0 && Math.abs(value) < grid / 2) return 0;
			return Math.max(min, Math.round(value / grid) * grid);
		},
		/** Quantises a position to the grid (half units allowed only when grid is 1 and `half` is set). */
		qp(value, half = false) {
			const step = half && grid === 1 ? 0.5 : grid;
			return Math.round(value / step) * step;
		},
		/** Scaled + quantised size vector [w, h, l]. */
		size(dims, min = grid) {
			return [ctx.q(dims[0] * scale[0], min), ctx.q(dims[1] * scale[1], min), ctx.q(dims[2] * scale[2], min)];
		},
		sx: (v, min = grid) => ctx.q(v * scale[0], min),
		sy: (v, min = grid) => ctx.q(v * scale[1], min),
		sz: (v, min = grid) => ctx.q(v * scale[2], min),
		/** Uniformly scaled length (average of the axes). */
		s: (v, min = grid) => ctx.q(v * (scale[0] + scale[1] + scale[2]) / 3, min),
	};
	return ctx;
}
