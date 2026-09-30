// Silhouettes cut into the texture alpha of flat parts (fins, veils, plumes).
// A shape is evaluated at every texel in normalised part space:
//   s = 0 at the attached base -> 1 at the free tip (along `axis`, direction `dir`)
//   u = 0 -> 1 across the part (along `across`)
// Minecraft renders entity textures with alpha cutout, so the result is strictly in/out.

import { hash01 } from '../generator/random.js';
import { smoothstep } from '../generator/util.js';

const AXIS = { x: 0, y: 1, z: 2 };

export const SHAPES = {
	rect: () => true,
	/** Rounded fan, e.g. the anglerfish caudal fin. */
	round(s, u) {
		const c = Math.abs(u - 0.5);
		let hw = 0.2 + 0.3 * Math.sin((Math.PI / 2) * Math.min(1, s / 0.7));
		if (s > 0.72) hw *= Math.sqrt(Math.max(0, 1 - ((s - 0.72) / 0.3) ** 2));
		return c <= hw + 1e-6;
	},
	/** Pectoral fan, narrow at the root. */
	fan(s, u) {
		const c = Math.abs(u - 0.5);
		let hw = 0.22 + 0.28 * Math.pow(s, 0.6);
		if (s > 0.8) hw *= Math.sqrt(Math.max(0, 1 - ((s - 0.8) / 0.22) ** 2));
		return c <= hw + 1e-6;
	},
	/** Forked tail. */
	fork(s, u) {
		const c = Math.abs(u - 0.5);
		const hw = 0.18 + 0.32 * Math.pow(s, 0.55);
		const notch = 0.36 * smoothstep(0.4, 1.02, s);
		return c <= hw + 1e-6 && c >= notch;
	},
	/** Crescent (lunate) tail. */
	lunate(s, u) {
		const c = Math.abs(u - 0.5);
		const hw = 0.14 + 0.36 * Math.pow(s, 0.45);
		const notch = 0.44 * Math.pow(smoothstep(0.2, 1.02, s), 0.75);
		return c <= hw + 1e-6 && c >= notch;
	},
	/** Shark tail: long upper lobe (u = 1 is up), short lower lobe. */
	heterocercal(s, u) {
		const c = u - 0.5;
		if (c >= 0) {
			const top = 0.5 * Math.min(1, 0.45 + 0.7 * s);
			const bottom = Math.max(0, (s - 0.25) * 0.55);
			return c <= top + 1e-6 && c >= bottom - 1e-6;
		}
		if (s > 0.62) return false;
		const reach = 0.5 * (0.3 + 1.1 * s) * (1 - smoothstep(0.38, 0.62, s));
		return -c <= reach + 1e-6;
	},
	/** Swept-back triangle (dorsal / anal fins). `apex` = where the tip sits across the base (0..1). */
	triangle(s, u, shape) {
		const apex = shape.apex ?? 0.75;
		if (s > 0.97) return Math.abs(u - apex) < 0.12;
		return u >= apex * s - 1e-6 && u <= 1 - (1 - apex) * s + 1e-6;
	},
	/** Rounded sail / veil. */
	sail(s, u) {
		const c = (u - 0.5) * 2;
		return s <= 1 - 0.7 * c * c + 1e-6;
	},
	/** Leaflet / plume filament. */
	leaf(s, u) {
		const c = Math.abs(u - 0.5);
		return c <= 0.5 * Math.pow(Math.sin(Math.PI * Math.min(0.999, 0.12 + 0.88 * s)), 0.7) + 1e-6;
	},
	/** Tapering tip (tentacles, antennae). */
	taper(s, u) {
		return Math.abs(u - 0.5) <= 0.5 * (1 - 0.75 * s) + 1e-6;
	},
	/** Hair / setae fringe: one strand per column (`strands` across), random lengths, some gaps. */
	hair(s, u, shape) {
		const strands = Math.max(1, shape.strands ?? 6);
		const i = Math.min(strands - 1, Math.floor(u * strands));
		if (hash01(i, 11, 5, shape.seed ?? 7) < (shape.gaps ?? 0.2)) return false;
		return s <= 0.35 + 0.65 * hash01(i, 3, 9, shape.seed ?? 7) + 1e-6;
	},
	/** Frilly edge: wavy free edge (gill frills, sea cucumber veil). */
	frill(s, u, shape) {
		const waves = shape.waves ?? 3;
		const edge = 0.78 + 0.22 * Math.cos(u * Math.PI * 2 * waves);
		return s <= edge + 1e-6;
	},
};

/**
 * @param {object} shape  {type, axis, dir, across, ...}
 * @param {number[]} rel  normalised position inside the cube (0..1 per axis)
 */
export function shapeContains(shape, rel) {
	const fn = SHAPES[shape.type] || SHAPES.rect;
	const a = AXIS[shape.axis || 'z'];
	const b = AXIS[shape.across || 'y'];
	const s = (shape.dir ?? 1) > 0 ? rel[a] : 1 - rel[a];
	const u = (shape.flip ? 1 - rel[b] : rel[b]);
	return fn(Math.min(1, Math.max(0, s)), Math.min(1, Math.max(0, u)), shape);
}

/** Normalised coordinates (s along, u across) for pattern painting on shaped parts. */
export function shapeCoords(shape, rel) {
	const a = AXIS[shape.axis || 'z'];
	const b = AXIS[shape.across || 'y'];
	const s = (shape.dir ?? 1) > 0 ? rel[a] : 1 - rel[a];
	const u = shape.flip ? 1 - rel[b] : rel[b];
	return { s, u, lengthAxis: a, acrossAxis: b };
}
