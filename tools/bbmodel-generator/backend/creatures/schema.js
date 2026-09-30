// Creature Definition checks (used by the Definition Editor's JSON view, the loader and the CLI).

import { isPlainObject } from '../generator/util.js';
import { validateParts } from '../templates/assembly.js';

const ID_RE = /^[a-z][a-z0-9_]*$/;
const HEX_RE = /^#[0-9a-fA-F]{6}$/;

/**
 * Returns a list of problems ({level, path, message}); empty when the definition is usable.
 * Unknown keys are allowed: templates ignore what they do not use.
 */
export function checkDefinition(def, templates = null) {
	const out = [];
	const err = (path, message) => out.push({ level: 'error', path, message });
	const warn = (path, message) => out.push({ level: 'warning', path, message });
	if (!isPlainObject(def)) {
		err('', 'Definition must be a JSON object');
		return out;
	}
	if (typeof def.id !== 'string' || !ID_RE.test(def.id)) err('id', 'id must be lower_snake_case (a-z, 0-9, _), starting with a letter');
	if (typeof def.name !== 'string' || !def.name.trim()) err('name', 'name is required');
	if (def.template && templates && !templates.includes(def.template)) err('template', `Unknown template "${def.template}" (${templates.join(', ')})`);
	if (def.body !== undefined) {
		if (!isPlainObject(def.body)) err('body', 'body must be an object {length, width, height}');
		else for (const k of ['length', 'width', 'height']) {
			if (def.body[k] !== undefined && !(typeof def.body[k] === 'number' && def.body[k] > 0 && def.body[k] <= 256)) err(`body.${k}`, `${k} must be a number in (0, 256]`);
		}
	}
	if (def.parts !== undefined && (!Array.isArray(def.parts) || def.parts.some((p) => typeof p !== 'string'))) err('parts', 'parts must be a list of part names');
	if (def.palette !== undefined) {
		if (!isPlainObject(def.palette)) err('palette', 'palette must be an object of "#rrggbb" colours');
		else for (const [k, v] of Object.entries(def.palette)) if (!HEX_RE.test(String(v))) err(`palette.${k}`, `"${v}" is not a #rrggbb colour`);
	}
	if (def.glow !== undefined) {
		if (!Array.isArray(def.glow)) err('glow', 'glow must be a list');
		else def.glow.forEach((g, i) => {
			if (!isPlainObject(g) || typeof g.part !== 'string') err(`glow[${i}]`, 'glow entries need a "part" (bone name or role)');
			else if (g.anchor && (!Array.isArray(g.anchor) || g.anchor.length !== 3)) err(`glow[${i}].anchor`, 'anchor must be [x, y, z] in 0..1');
			else if (g.scatter !== undefined && !(Number.isInteger(g.scatter) && g.scatter >= 0 && g.scatter <= 128)) err(`glow[${i}].scatter`, 'scatter must be an integer 0..128');
		});
	}
	if (def.animations !== undefined && (!Array.isArray(def.animations) || def.animations.some((a) => typeof a !== 'string'))) err('animations', 'animations must be a list of names');
	if (def.animation?.speed !== undefined && !(def.animation.speed > 0)) err('animation.speed', 'speed must be > 0');
	if (def.template === 'parts') for (const message of validateParts(def.params?.parts)) err('params.parts', message);
	if (!def.template && !def.category) warn('template', 'No template / category: the Fish template will be used');
	return out;
}
