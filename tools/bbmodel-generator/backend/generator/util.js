// Small data helpers used across the generator.

export function isPlainObject(value) {
	return value !== null && typeof value === 'object' && !Array.isArray(value);
}

/** Deep clone of JSON-like data. */
export function clone(value) {
	if (Array.isArray(value)) return value.map(clone);
	if (isPlainObject(value)) {
		const out = {};
		for (const key of Object.keys(value)) out[key] = clone(value[key]);
		return out;
	}
	return value;
}

/** Recursively merges plain objects (arrays and primitives from `override` replace `base`). */
export function deepMerge(base, override) {
	if (override === undefined) return clone(base);
	if (!isPlainObject(base) || !isPlainObject(override)) return clone(override);
	const out = clone(base);
	for (const key of Object.keys(override)) {
		out[key] = isPlainObject(out[key]) && isPlainObject(override[key]) ? deepMerge(out[key], override[key]) : clone(override[key]);
	}
	return out;
}

/** Reads "a.b.c" from an object. */
export function getPath(obj, path, fallback = undefined) {
	let cur = obj;
	for (const key of path.split('.')) {
		if (cur == null || typeof cur !== 'object' || !(key in cur)) return fallback;
		cur = cur[key];
	}
	return cur;
}

/** Writes "a.b.c" into an object, creating intermediate objects. */
export function setPath(obj, path, value) {
	const keys = path.split('.');
	let cur = obj;
	for (let i = 0; i < keys.length - 1; i++) {
		if (!isPlainObject(cur[keys[i]])) cur[keys[i]] = {};
		cur = cur[keys[i]];
	}
	cur[keys[keys.length - 1]] = value;
}

export const clamp = (v, min, max) => Math.min(max, Math.max(min, v));
export const lerp = (a, b, t) => a + (b - a) * t;
export const smoothstep = (e0, e1, x) => {
	const t = clamp((x - e0) / (e1 - e0 || 1e-9), 0, 1);
	return t * t * (3 - 2 * t);
};

/** Converts "giant_isopod" / "Giant Isopod" to "GiantIsopod". */
export function pascalCase(text) {
	return String(text)
		.replace(/[^A-Za-z0-9]+/g, ' ')
		.trim()
		.split(/\s+/)
		.map((w) => w.charAt(0).toUpperCase() + w.slice(1))
		.join('');
}

export function slug(text) {
	return String(text)
		.toLowerCase()
		.replace(/[^a-z0-9]+/g, '_')
		.replace(/^_+|_+$/g, '') || 'creature';
}
