// Geometry helpers shared by the templates.

/**
 * Centre for a cube of `size` near `v` so that its faces sit on the model grid
 * (keeps pixels aligned between neighbouring cubes).
 */
export function alignCenter(v, size, g = 1) {
	const from = Math.round((v - size / 2) / g) * g;
	return from + size / 2;
}

/**
 * Centres of `count` one-pixel-wide items spread symmetrically across `width`
 * (centred on x = 0) and aligned to the same grid as the edges of that width.
 */
export function spread(count, width, g = 1) {
	if (count <= 0) return [];
	if (count === 1) return [lattice(0, width, g)];
	const span = Math.max(0, width - g);
	const out = new Array(count);
	for (let i = 0; i < Math.ceil(count / 2); i++) {
		const c = -span / 2 + (span * i) / (count - 1);
		const snapped = lattice(c, width, g);
		out[i] = snapped;
		out[count - 1 - i] = -snapped;
	}
	if (count % 2 === 1) out[(count - 1) / 2] = lattice(0, width, g);
	// Remove accidental duplicates after snapping (narrow widths)
	return [...new Set(out.map((v) => Math.round(v * 1000) / 1000))];
}

function lattice(c, width, g) {
	// left edge of a g-wide item must be at -width/2 + m*g
	const base = -width / 2;
	const m = Math.round((c - g / 2 - base) / g);
	return base + m * g + g / 2;
}

/** Splits `total` into `n` grid-aligned lengths (each >= g) that sum to `total`. */
export function splitLength(total, n, g = 1) {
	const steps = Math.max(1, Math.round(total / g));
	const count = Math.max(1, Math.min(n, steps));
	const out = [];
	let remaining = steps;
	for (let i = 0; i < count; i++) {
		const part = Math.round(remaining / (count - i));
		out.push(part * g);
		remaining -= part;
	}
	return out;
}

/** Evenly spaced values between a and b (inclusive). */
export function linspace(a, b, n) {
	if (n <= 1) return [(a + b) / 2];
	return Array.from({ length: n }, (_, i) => a + ((b - a) * i) / (n - 1));
}

export const SIDES = [
	['right', 1],
	['left', -1],
];

/**
 * Box with stepped ("rounded") edges, the usual Minecraft trick for organic silhouettes:
 * the main block is shortened and thin slabs, inset on x / z, cap its top and / or bottom.
 * @param {object} opts.round {top, bottom, x, front, back} step sizes in units (front = -Z side)
 * @returns {object} the main cube
 */
export function roundedBox(b, bone, name, opts) {
	const { from, to, round: r = {}, ...rest } = opts;
	const top = r.top || 0;
	const bottom = r.bottom || 0;
	const ix = r.x ?? Math.max(top, bottom);
	const front = r.front ?? Math.max(top, bottom);
	const back = r.back ?? Math.max(top, bottom);
	const main = b.box(bone, name, { ...rest, from: [from[0], from[1] + bottom, from[2]], to: [to[0], to[1] - top, to[2]] });
	const slab = (suffix, y0, y1) => {
		if (to[0] - from[0] - 2 * ix <= 0 || to[2] - from[2] - front - back <= 0) return;
		b.box(bone, `${name}_${suffix}`, {
			...rest,
			from: [from[0] + ix, y0, from[2] + front],
			to: [to[0] - ix, y1, to[2] - back],
			priority: Math.max(2, rest.priority ?? 1),
			meta: { ...(rest.meta || {}), slab: suffix },
		});
	};
	if (top > 0) slab('top', to[1] - top, to[1]);
	if (bottom > 0) slab('bottom', from[1], from[1] + bottom);
	return main;
}
