// Public API of the generator core (isomorphic: imported by the browser UI, the CLI and tests).

import { buildBBModel, stringifyBBModel } from './bbmodel/writer.js';
import { mergeReports, validateBBModel } from './bbmodel/validator.js';
import { encodePNG } from './texture/png.js';
import { pascalCase } from './generator/util.js';
import { javaAnimations, javaGeometry } from './bbmodel/java_export.js';

export { generateModel } from './generator/pipeline.js';
export { buildBBModel, stringifyBBModel, animationName } from './bbmodel/writer.js';
export { validateModel, validateBBModel, mergeReports } from './bbmodel/validator.js';
export { createZip } from './bbmodel/zip.js';
export { javaGeometry, javaAnimations } from './bbmodel/java_export.js';
export { encodePNG, pngDataURL, bytesToBase64 } from './texture/png.js';
export { listTemplates, getTemplate, inferTemplate } from './templates/index.js';
export { checkDefinition } from './creatures/schema.js';
export { samplePose, sampleChannel } from './animation/clip.js';
export {
	DEFAULT_SETTINGS,
	DEFAULT_PRESET,
	DETAIL_LEVELS,
	PIXEL_SCALES,
	TEXTURE_SIZES,
	UV_MODES,
	MODEL_FORMATS,
	FORMAT_VERSIONS,
	normalizeSettings,
} from './generator/context.js';

/** Description written next to the model: how it was generated and what it contains. */
export function creatureManifest(model) {
	const def = { ...model.definition };
	delete def._variation;
	return {
		generator: 'bbmodel-generator 1.0',
		id: model.id,
		name: model.name,
		template: model.template,
		preset: model.preset?.id,
		seed: model.settings.seed,
		variation: model.settings.variation,
		settings: model.settings,
		model: {
			format: model.settings.modelFormat,
			format_version: model.settings.formatVersion,
			uv_mode: model.uv.mode,
			texture_resolution: [model.uv.width, model.uv.height],
			texture_image: [model.textures.base.width, model.textures.base.height],
			glow_texture: !!model.textures.glow,
			size_units: model.bounds.size.map((v) => Math.round(v * 100) / 100),
			size_blocks: model.stats.sizeBlocks,
			core_size_blocks: model.stats.coreSizeBlocks,
			cubes: model.stats.cubes,
		},
		bones: model.geometry.bones.map((b) => ({ name: b.name, parent: b.parent, role: b.role, pivot: b.pivot, rotation: b.rotation })),
		animations: model.animations.map((a) => ({ name: a.name, loop: a.loop, length: a.length, bones: Object.keys(a.tracks), description: a.description })),
		definition: def,
	};
}

/**
 * Everything that goes into the export folder:
 *   <Name>/<id>.bbmodel, <id>.png, <id>_glow.png, <id>.json
 *   (+ <Name>Geometry.java / <Name>Animations.java for Forge 1.20.1 when the model is Box UV)
 */
export function exportFiles(model) {
	const bbmodel = buildBBModel(model);
	const text = stringifyBBModel(bbmodel) + '\n';
	const report = mergeReports(model.validation, validateBBModel(bbmodel, text));
	const files = [
		{ name: `${model.id}.bbmodel`, data: text },
		{ name: `${model.id}.png`, data: encodePNG(model.textures.base) },
	];
	if (model.textures.glow) files.push({ name: `${model.id}_glow.png`, data: encodePNG(model.textures.glow) });
	files.push({ name: `${model.id}.json`, data: JSON.stringify(creatureManifest(model), null, 2) + '\n' });
	if (model.uv.mode === 'box') {
		const base = pascalCase(model.id);
		files.push({ name: `${base}Geometry.java`, data: javaGeometry(model) });
		if (model.animations.length) files.push({ name: `${base}Animations.java`, data: javaAnimations(model) });
	}
	return { folder: pascalCase(model.name), files, bbmodel, text, report };
}
