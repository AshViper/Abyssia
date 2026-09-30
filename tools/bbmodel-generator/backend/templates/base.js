// Base class for creature templates.
//
// A template turns a Creature Definition into model data in three explicit steps:
//   semanticParts(def, ctx) -> named parts with final (scaled, quantised) dimensions
//   build(parts, ctx)       -> geometry (bones + cubes) through ModelBuilder
//   animations(geo, def)    -> animation clips generated from the bone structure
// Templates are generic (Fish, Eel, Squid, ...); a creature is only data.

import { ClipBuilder } from '../animation/clip.js';
import { cubeCorners } from '../generator/builder.js';
import { boneWorldTransforms } from '../generator/math3d.js';
import { clamp, deepMerge, getPath, setPath } from '../generator/util.js';

/**
 * How far the model must be raised so its lowest point sits on the ground in `pose`
 * ({bone: {rotation, position}} with Blockbench additive semantics). Used by curl / flip clips.
 */
export function groundLift(geometry, pose) {
	const worlds = boneWorldTransforms(geometry, pose);
	const bones = new Map(geometry.bones.map((b) => [b.name, b]));
	let min = Infinity;
	for (const cube of geometry.cubes) {
		for (const p of cubeCorners(cube, bones.get(cube.bone), worlds.get(cube.bone))) min = Math.min(min, p[1]);
	}
	return Number.isFinite(min) ? Math.max(0, Math.round(-min * 100) / 100) : 0;
}

export class CreatureTemplate {
	static id = 'base';
	static label = 'Base';
	static description = '';

	/** Default definition for a new creature of this type (also fills missing keys). */
	defaults() {
		return {};
	}

	/** Definition editor fields, grouped by section. */
	editorSchema() {
		return [];
	}

	/** Variation keys -> numeric definition paths scaled together by one random factor. */
	variationTargets() {
		return {};
	}

	/** Preset exaggeration keys ("head", "eye", "teeth", "fin", "glow", "limb") -> paths. */
	exaggerationTargets() {
		return {};
	}

	/** Animations this template can generate. */
	animationCatalog() {
		return {};
	}

	/** Parts that exist unless the definition's `parts` list leaves them out. */
	partEnabled(def, ...names) {
		if (!Array.isArray(def.parts) || !def.parts.length) return true;
		return names.some((n) => def.parts.includes(n));
	}

	// eslint-disable-next-line no-unused-vars
	semanticParts(def, ctx) {
		throw new Error(`${this.constructor.name}.semanticParts not implemented`);
	}

	// eslint-disable-next-line no-unused-vars
	build(parts, ctx) {
		throw new Error(`${this.constructor.name}.build not implemented`);
	}

	/** Generates the clips listed in def.animations using the template's catalog. */
	animations(geometry, def, ctx) {
		const catalog = this.animationCatalog();
		const speed = Math.max(0.05, (def.animation?.speed ?? 1) * (ctx.animStyle?.speed ?? 1));
		const amp = Math.max(0, (def.animation?.amplitude ?? 1) * (ctx.animStyle?.amplitude ?? 1));
		const names = def.animations?.length ? def.animations : Object.keys(catalog);
		const clips = [];
		for (const name of names) {
			const gen = catalog[name];
			if (!gen) continue;
			const make = (length, loop = 'loop', description = '') =>
				new ClipBuilder(name, { length: Math.round((length / speed) * 100) / 100, loop, geometry, description });
			const clip = gen.call(this, { geometry, def, ctx, amp, make, roles: roleIndex(geometry) });
			if (clip && Object.keys(clip.tracks).length) clips.push(clip);
		}
		return clips;
	}
}

/** Lookup helpers for animation generators. */
export function roleIndex(geometry) {
	const byRole = new Map();
	for (const bone of geometry.bones) {
		if (!byRole.has(bone.role)) byRole.set(bone.role, []);
		byRole.get(bone.role).push(bone);
	}
	for (const list of byRole.values()) list.sort((a, b) => (a.meta.index ?? 0) - (b.meta.index ?? 0));
	return {
		all: (role) => byRole.get(role) || [],
		one: (role) => (byRole.get(role) || [])[0] || null,
		name: (role) => (byRole.get(role) || [])[0]?.name || null,
	};
}

/** Clamp for variation factors so individuals stay biologically plausible. */
const MAX_VARIATION = 0.25;

/**
 * Template defaults + definition + preset exaggeration + seeded individual variation.
 * The returned object is the "resolved definition" every later stage reads.
 */
export function resolveDefinition(definition, template, ctx) {
	const def = deepMerge(template.defaults(), definition || {});
	def.template = template.constructor.id;

	for (const [key, paths] of Object.entries(template.exaggerationTargets())) {
		const f = ctx.ex(key);
		if (f === 1) continue;
		for (const path of paths) {
			const v = getPath(def, path);
			if (typeof v === 'number') setPath(def, path, v * f);
		}
	}

	const spec = def.variation || {};
	const amount = ctx.settings.variation;
	const applied = { factors: {}, color: { hue: 0, lightness: 0 } };
	if (amount > 0) {
		const rng = ctx.rng.fork('variation');
		for (const [key, paths] of Object.entries(template.variationTargets())) {
			const range = clamp((spec[key] ?? 0) * amount, 0, MAX_VARIATION);
			const f = 1 + rng.fork(key).signed(range);
			if (range === 0) continue;
			applied.factors[key] = f;
			for (const path of paths) {
				const v = getPath(def, path);
				if (typeof v === 'number') setPath(def, path, v * f);
			}
		}
		applied.color.hue = rng.fork('hue').signed(clamp(spec.hue ?? 0, 0, 30) * amount);
		applied.color.lightness = rng.fork('lightness').signed(clamp(spec.lightness ?? 0, 0, 0.1) * amount);
	}
	def._variation = applied;
	return def;
}
