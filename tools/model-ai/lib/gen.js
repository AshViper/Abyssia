// Bridge to tools/bbmodel-generator: spec / definition -> generated model (+ problems as a list).
// The generator stays the only writer of .bbmodel; this module never duplicates its logic.

import { checkDefinition, exportFiles, generateModel } from '../../bbmodel-generator/backend/index.js';
import { validateParts } from '../../bbmodel-generator/backend/templates/assembly.js';
import { loadDefinition, loadDefinitions, loadPreset } from '../../bbmodel-generator/backend/creatures/node_store.js';

export { exportFiles, loadDefinition, loadDefinitions, validateParts };

const PRESET_ID = 'minecraft_deep_sea';
let presetCache = null;
export function preset() {
	return (presetCache ??= loadPreset(PRESET_ID));
}

/**
 * Model "spec" = what the AI writes: {orientation, scale?, parts, texture?}.
 * Definition = what the generator reads. Metadata (id, name, animations) is filled here.
 */
export function specToDefinition(spec, { id = 'ai_model', name = 'AI Model' } = {}) {
	const def = {
		id,
		name,
		template: 'parts',
		category: 'parts',
		params: { orientation: spec.orientation || 'upright', parts: spec.parts },
		palette: {},
		texture: spec.texture || { pattern: 'plain' },
		glow: [],
	};
	if (spec.scale !== undefined && spec.scale !== 1) def.params.scale = spec.scale;
	return def;
}

export function definitionToSpec(def) {
	const spec = { orientation: def.params?.orientation || 'upright' };
	if (def.params?.scale !== undefined && def.params.scale !== 1) spec.scale = def.params.scale;
	spec.parts = def.params?.parts || [];
	if (def.texture && def.texture.pattern && def.texture.pattern !== 'plain') spec.texture = def.texture;
	return spec;
}

/**
 * Generates a model; never throws.
 * @returns {{ok: boolean, model: object|null, problems: string[]}}
 */
export function tryGenerate(def, settings = {}) {
	const problems = [];
	if (def.template === 'parts') problems.push(...validateParts(def.params?.parts));
	if (problems.length) return { ok: false, model: null, problems };
	try {
		const p = preset();
		const model = generateModel(def, { ...(p.settings || {}), preset: p.id, seed: 1, variation: 0, ...settings }, p);
		for (const item of model.validation.items || []) if (item.level === 'error') problems.push(`${item.check}: ${item.message}`);
		return { ok: problems.length === 0, model, problems };
	} catch (err) {
		return { ok: false, model: null, problems: [err.message] };
	}
}

export { checkDefinition };
