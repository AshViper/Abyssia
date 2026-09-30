// Deterministic randomness shared by the whole pipeline.
// Everything here is pure integer / float math so the browser and Node produce
// bit-identical models, textures and animations for the same seed.

/** FNV-1a 32-bit hash of a string. */
export function hashString(str) {
	let h = 0x811c9dc5;
	for (let i = 0; i < str.length; i++) {
		h ^= str.charCodeAt(i);
		h = Math.imul(h, 0x01000193);
	}
	return h >>> 0;
}

/** Mixes any number of 32-bit integers into one well distributed 32-bit hash (murmur3 style). */
export function hashInts(...values) {
	let h = 0x9e3779b9;
	for (const value of values) {
		let k = Math.imul(value | 0, 0xcc9e2d51);
		k = (k << 15) | (k >>> 17);
		k = Math.imul(k, 0x1b873593);
		h ^= k;
		h = (h << 13) | (h >>> 19);
		h = (Math.imul(h, 5) + 0xe6546b64) | 0;
	}
	h ^= values.length;
	h ^= h >>> 16;
	h = Math.imul(h, 0x85ebca6b);
	h ^= h >>> 13;
	h = Math.imul(h, 0xc2b2ae35);
	h ^= h >>> 16;
	return h >>> 0;
}

/** Normalises a user seed (number or text) to a 32-bit unsigned integer. */
export function toSeed(seed) {
	if (typeof seed === 'number' && Number.isFinite(seed)) return (Math.floor(seed) >>> 0);
	return hashString(String(seed ?? ''));
}

/** Small, fast, seedable PRNG (mulberry32) with helpers. */
export class Random {
	constructor(seed) {
		this.seed = toSeed(seed);
		this.state = this.seed || 0x6d2b79f5;
	}
	next() {
		let t = (this.state = (this.state + 0x6d2b79f5) >>> 0);
		t = Math.imul(t ^ (t >>> 15), t | 1);
		t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
		return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
	}
	range(min, max) {
		return min + (max - min) * this.next();
	}
	/** Integer in [min, max] inclusive. */
	int(min, max) {
		return Math.floor(this.range(min, max + 1 - 1e-9));
	}
	/** Symmetric value in [-amount, amount]. */
	signed(amount = 1) {
		return (this.next() * 2 - 1) * amount;
	}
	chance(p) {
		return this.next() < p;
	}
	pick(list) {
		return list[Math.floor(this.next() * list.length) % list.length];
	}
	/** Independent stream derived from this seed, stable regardless of how many numbers were drawn before. */
	fork(label) {
		return new Random(hashInts(this.seed, hashString(String(label))));
	}
}

/** Uniform hash of an integer lattice point to [0, 1). */
export function hash01(x, y, z, seed) {
	return hashInts(x, y, z, seed) / 4294967296;
}

function fade(t) {
	return t * t * t * (t * (t * 6 - 15) + 10);
}
function lerp(a, b, t) {
	return a + (b - a) * t;
}

/** Smooth 3D value noise in [0, 1). */
export function valueNoise3(x, y, z, seed) {
	const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z);
	const u = fade(x - xi), v = fade(y - yi), w = fade(z - zi);
	const c000 = hash01(xi, yi, zi, seed), c100 = hash01(xi + 1, yi, zi, seed);
	const c010 = hash01(xi, yi + 1, zi, seed), c110 = hash01(xi + 1, yi + 1, zi, seed);
	const c001 = hash01(xi, yi, zi + 1, seed), c101 = hash01(xi + 1, yi, zi + 1, seed);
	const c011 = hash01(xi, yi + 1, zi + 1, seed), c111 = hash01(xi + 1, yi + 1, zi + 1, seed);
	return lerp(
		lerp(lerp(c000, c100, u), lerp(c010, c110, u), v),
		lerp(lerp(c001, c101, u), lerp(c011, c111, u), v),
		w,
	);
}

/** Fractal value noise, normalised to [0, 1). */
export function fbm3(x, y, z, seed, octaves = 3) {
	let amplitude = 1, frequency = 1, sum = 0, norm = 0;
	for (let i = 0; i < octaves; i++) {
		sum += valueNoise3(x * frequency, y * frequency, z * frequency, seed + i * 1013) * amplitude;
		norm += amplitude;
		amplitude *= 0.5;
		frequency *= 2;
	}
	return sum / norm;
}

/**
 * Cellular "spot" field: returns the distance (in noise-cell units) from p to the nearest
 * randomly placed feature point, and that point's hash. Cells without a feature are skipped
 * according to `density`, which keeps speckles sparse and irregular.
 */
export function spots3(x, y, z, seed, density = 0.5) {
	const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z);
	let best = Infinity, bestHash = 0;
	for (let dz = -1; dz <= 1; dz++) {
		for (let dy = -1; dy <= 1; dy++) {
			for (let dx = -1; dx <= 1; dx++) {
				const cx = xi + dx, cy = yi + dy, cz = zi + dz;
				const h = hashInts(cx, cy, cz, seed);
				if ((h & 0xffff) / 65536 >= density) continue;
				const fx = cx + hash01(cx, cy, cz, seed + 1);
				const fy = cy + hash01(cx, cy, cz, seed + 2);
				const fz = cz + hash01(cx, cy, cz, seed + 3);
				const d = Math.hypot(x - fx, y - fy, z - fz);
				if (d < best) {
					best = d;
					bestHash = h;
				}
			}
		}
	}
	return { distance: best, hash: bestHash / 4294967296 };
}
