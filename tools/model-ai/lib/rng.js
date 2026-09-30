// Small seeded RNG (mulberry32) with the helpers the samplers need.

export function createRng(seed = 1) {
	let a = typeof seed === 'number' ? seed >>> 0 : hashString(String(seed));
	const next = () => {
		a = (a + 0x6d2b79f5) >>> 0;
		let t = a;
		t = Math.imul(t ^ (t >>> 15), t | 1);
		t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
		return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
	};
	const rng = {
		next,
		float: (lo = 0, hi = 1) => lo + (hi - lo) * next(),
		int: (lo, hi) => Math.floor(lo + (hi - lo + 1) * next()),
		chance: (p) => next() < p,
		pick: (arr) => arr[Math.floor(next() * arr.length)],
		/** pick from [[value, weight], ...] */
		weighted: (pairs) => {
			const total = pairs.reduce((s, [, w]) => s + w, 0);
			let r = next() * total;
			for (const [v, w] of pairs) if ((r -= w) <= 0) return v;
			return pairs.at(-1)[0];
		},
		shuffle: (arr) => {
			const a2 = arr.slice();
			for (let i = a2.length - 1; i > 0; i--) {
				const j = Math.floor(next() * (i + 1));
				[a2[i], a2[j]] = [a2[j], a2[i]];
			}
			return a2;
		},
		sample: (arr, n) => rng.shuffle(arr).slice(0, n),
		fork: (tag) => createRng(hashString(`${a}:${tag}`)),
	};
	return rng;
}

export function hashString(s) {
	let h = 2166136261 >>> 0;
	for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619) >>> 0;
	return h;
}
