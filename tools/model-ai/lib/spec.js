// Canonical form of a model "spec" (the text the AI reads and writes).
// Fixed key order + rounded numbers make targets deterministic and short.

export const SPEC_KEYS = ['orientation', 'scale', 'texture', 'parts'];
export const PART_KEYS = [
	'name', 'shape', 'parent', 'at', 'axis', 'center', 'offset', 'rotate', 'size',
	'taper', 'tip', 'profile', 'outline', 'segments', 'curl',
	'count', 'arrange', 'radius', 'tilt', 'start_angle', 'spacing', 'mirror',
	'color', 'color_to', 'belly', 'material', 'glow', 'glow_color', 'glow_pattern', 'role', 'detail', 'steps', 'round',
];
export const TEXTURE_PATTERNS = ['plain', 'speckle', 'spots', 'mottled', 'stripes', 'scales', 'bands'];

export const roundNum = (v, d = 2) => {
	const f = 10 ** d;
	const r = Math.round(v * f) / f;
	return Object.is(r, -0) ? 0 : r;
};

function roundDeep(v) {
	if (typeof v === 'number') return roundNum(v);
	if (Array.isArray(v)) return v.map(roundDeep);
	if (v && typeof v === 'object') return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, roundDeep(x)]));
	return v;
}

function ordered(obj, keys) {
	const out = {};
	for (const k of keys) if (obj[k] !== undefined && obj[k] !== null) out[k] = obj[k];
	for (const k of Object.keys(obj).sort()) if (!(k in out) && obj[k] !== undefined && obj[k] !== null) out[k] = obj[k];
	return out;
}

export function canonicalPart(p) {
	const q = roundDeep(p);
	if (q.color) q.color = q.color.toLowerCase();
	if (q.glow_color) q.glow_color = q.glow_color.toLowerCase();
	return ordered(q, PART_KEYS);
}

export function canonicalSpec(spec) {
	const s = { ...spec };
	if (s.scale === 1) delete s.scale;
	if (s.texture && (!s.texture.pattern || s.texture.pattern === 'plain') && Object.keys(s.texture).length <= 1) delete s.texture;
	if (s.texture) s.texture = ordered(roundDeep(s.texture), ['pattern', 'pattern_strength', 'pattern_scale']);
	s.parts = (s.parts || []).map(canonicalPart);
	return ordered(roundDeep(s), SPEC_KEYS);
}

/** Compact text: one part per line (easy to read, diff and tokenise). */
export function serializeSpec(spec) {
	const s = canonicalSpec(spec);
	const head = { ...s };
	delete head.parts;
	const headText = JSON.stringify(head).slice(1, -1);
	const parts = s.parts.map((p) => JSON.stringify(p)).join(',\n');
	return `{${headText}${headText ? ',' : ''}"parts":[\n${parts}\n]}`;
}

/** Tolerant parse of model output: first JSON value in the text. */
export function parseJsonLoose(text) {
	const t = String(text).trim().replace(/^```(?:json)?\s*/i, '').replace(/```\s*$/, '');
	const start = t.search(/[[{]/);
	if (start < 0) throw new Error('no JSON value found');
	const open = t[start], close = open === '{' ? '}' : ']';
	let depth = 0, inStr = false, esc = false;
	for (let i = start; i < t.length; i++) {
		const c = t[i];
		if (inStr) {
			if (esc) esc = false;
			else if (c === '\\') esc = true;
			else if (c === '"') inStr = false;
		} else if (c === '"') inStr = true;
		else if (c === '{' || c === '[') depth++;
		else if (c === '}' || c === ']') {
			depth--;
			if (depth === 0) return JSON.parse(t.slice(start, i + 1));
		}
	}
	throw new Error(`unterminated JSON (expected "${close}")`);
}

export const clone = (v) => (v === undefined ? v : JSON.parse(JSON.stringify(v)));

export function sameValue(a, b) {
	return JSON.stringify(roundDeep(a)) === JSON.stringify(roundDeep(b));
}

/** Children map name -> [names]. */
export function childrenOf(spec) {
	const m = new Map();
	for (const p of spec.parts) if (p.parent) (m.get(p.parent) || m.set(p.parent, []).get(p.parent)).push(p.name);
	return m;
}

export function descendants(spec, name) {
	const kids = childrenOf(spec);
	const out = [];
	const walk = (n) => (kids.get(n) || []).forEach((c) => (out.push(c), walk(c)));
	walk(name);
	return out;
}
