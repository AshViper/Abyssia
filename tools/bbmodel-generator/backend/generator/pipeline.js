// The generation pipeline:
//   Creature Definition -> Resolved Definition (preset, variation) -> Semantic Parts
//   -> Template -> Geometry (bones, cubes) -> UV -> Texture (+ glow) -> Animation
//   -> BBModel (see ../bbmodel/writer.js) with Validation in between.
// Every intermediate result is plain data and is returned for inspection / preview.

import { validateModel } from '../bbmodel/validator.js';
import { resolveDefinition } from '../templates/base.js';
import { getTemplate, inferTemplate } from '../templates/index.js';
import { paintTextures } from '../texture/painter.js';
import { layoutUV } from '../uv/layout.js';
import { applyBlockBudget, flattenForStatic } from './budget.js';
import { geometryBounds } from './builder.js';
import { createContext, normalizeSettings } from './context.js';

/** Bone roles that make up the main body (everything else counts as an appendage). */
const CORE_ROLES = new Set(['root', 'body', 'head', 'jaw', 'segment', 'mantle', 'tail', 'abdomen', 'shell', 'tube', 'tube_segment']);

const now = () => (typeof performance !== 'undefined' ? performance.now() : Date.now());

/**
 * Generates a complete model from a creature definition.
 * @param {object} definition creature definition (definitions/*.json)
 * @param {object} [settings] generation settings (see DEFAULT_SETTINGS)
 * @param {object} [preset] preset object (presets/*.json)
 */
export function generateModel(definition, settings = {}, preset = null) {
	const timings = {};
	let t = now();
	const mark = (name) => {
		const n = now();
		timings[name] = Math.round((n - t) * 100) / 100;
		t = n;
	};
	const s = normalizeSettings(settings);
	const templateId = inferTemplate(definition);
	const template = getTemplate(templateId);

	// Optional resize to a target width / height / length: measure the natural size first.
	let scale = [1, 1, 1];
	if (s.size.width || s.size.height || s.size.length) {
		const ctx0 = createContext(definition, { ...s, blockCount: 0 }, preset);
		const geo0 = template.build(template.semanticParts(resolveDefinition(definition, template, ctx0), ctx0), ctx0);
		const nat = geometryBounds(geo0).size;
		scale = [
			s.size.width && nat[0] > 0 ? s.size.width / nat[0] : 1,
			s.size.height && nat[1] > 0 ? s.size.height / nat[1] : 1,
			s.size.length && nat[2] > 0 ? s.size.length / nat[2] : 1,
		].map((v) => Math.min(8, Math.max(0.125, v)));
	}

	const ctx = createContext(definition, s, preset, { scale });
	const resolved = resolveDefinition(definition, template, ctx);
	mark('definition');
	const semantic = template.semanticParts(resolved, ctx);
	mark('semantic');
	let geometry = template.build(semantic, ctx);
	const budget = applyBlockBudget(geometry, s.blockCount);
	geometry = budget.geometry;
	if (!s.animationReady) geometry = flattenForStatic(geometry);
	mark('geometry');
	const uv = layoutUV(geometry, ctx);
	mark('uv');
	const textures = paintTextures({ geometry, uv, definition: resolved }, ctx);
	mark('texture');
	const animations = s.generateAnimation && s.animationReady ? template.animations(geometry, resolved, ctx) : [];
	mark('animation');

	const bounds = geometryBounds(geometry);
	// Body without appendages (antennae, lures, tentacles, legs, fins...) for hitbox sizing
	const roleOf = new Map(geometry.bones.map((b) => [b.name, b.role]));
	const core = geometryBounds(geometry, (c) => CORE_ROLES.has(roleOf.get(c.bone)));
	const coreBounds = core.size.some((v) => v > 0) ? core : bounds;
	const model = {
		id: resolved.id || 'creature',
		name: resolved.name || resolved.id || 'Creature',
		template: templateId,
		definition: resolved,
		semantic,
		geometry,
		uv,
		textures,
		animations,
		settings: s,
		preset: ctx.preset,
		seed: ctx.seed,
		scale,
		bounds,
		warnings: [...budget.warnings, ...uv.warnings],
		stats: {
			bones: geometry.bones.length,
			cubes: geometry.cubes.length,
			removedByBudget: budget.removed.length,
			texture: `${uv.imageWidth}×${uv.imageHeight}`,
			uvResolution: `${uv.width}×${uv.height}`,
			uvUsage: Math.round((uv.used / (uv.width * uv.height)) * 100),
			glowPixels: textures.glowPixels,
			animations: animations.length,
			sizeBlocks: bounds.size.map((v) => Math.round((v / 16) * 100) / 100),
			coreSizeBlocks: coreBounds.size.map((v) => Math.round((v / 16) * 100) / 100),
		},
		timings,
	};
	model.validation = validateModel(model);
	mark('validation');
	return model;
}
