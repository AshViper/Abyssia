// Helpers shared by the body-plan samplers.
// A sampler returns {spec, facts}; facts drive captions (kind + traits + colours).

import { colorByName, colorName, hexToRgb, rgbToHex } from '../vocab.js';

const TIP_WORDS = { '-z': ['the head', '頭'], z: ['the tail', '尾'], y: ['the top', '上'], '-y': ['the tips', '先'], x: ['the tips', '先'], '-x': ['the tips', '先'] };
const GLOW_WORDS = {
	spots: (c) => [`${c.en} glowing spots`, `${c.ja}に光る斑点`],
	stripes: (c) => [`${c.en} glowing stripes`, `${c.ja}に光る縞`],
	edges: (c) => [`a ${c.en} glowing ridge along its back`, `背に沿って${c.ja}に光る筋`],
	rim: (c) => [`a glowing ${c.en} rim`, `${c.ja}に光る縁`],
};

export function kit(rng) {
	const facts = { kind: null, traits: [], colors: {} };
	return {
		rng,
		facts,
		/** Jittered named colour -> hex. */
		col(en, jitter = 16) {
			const c = colorByName(en);
			if (!c) throw new Error(`unknown colour ${en}`);
			return rgbToHex(hexToRgb(c[2]).map((v) => v + rng.float(-jitter, jitter)));
		},
		pickCol(names, jitter) {
			return this.col(rng.pick(names), jitter);
		},
		/** Snap to the 0.5 unit grid, at least `min`. */
		u(v, min = 1) {
			return Math.max(min, Math.round(v * 2) / 2);
		},
		kind(en, ja) {
			facts.kind = { en, ja };
		},
		trait(en, ja) {
			facts.traits.push({ en, ja, style: true, glow: true });
		},
		color(role, hex) {
			facts.colors[role] = hex;
			return hex;
		},
		// Style keys of the parts template (belly / color_to / glow_pattern) with matching traits
		belly(part, names = ['white', 'cream', 'light gray', 'beige']) {
			part.belly = this.pickCol(names, 10);
			const c = colorName(part.belly);
			facts.traits.push({ en: `a ${c.en} belly`, ja: `${c.ja}の腹`, style: true });
			return part;
		},
		gradient(part, names, what = null) {
			part.color_to = this.pickCol(names, 12);
			const c = colorName(part.color_to);
			const [en, ja] = TIP_WORDS[part.axis || (/tentacle|arm/.test(part.name) ? '-y' : 'y')];
			facts.traits.push({ ...(what ? { en: `${what[0]} fading to ${c.en}`, ja: `${c.ja}に変わっていく${what[1]}` } : { en: `a body fading to ${c.en} toward ${en}`, ja: `${ja}に向かって${c.ja}になる体` }), style: true });
			return part;
		},
		glow(part, pattern, names = ['cyan', 'light blue', 'lime', 'magenta', 'yellow']) {
			const g = this.color('glow', this.pickCol(names, 8));
			Object.assign(part, { glow: 1, glow_color: g, glow_pattern: pattern });
			const c = colorName(g);
			const [en, ja] = GLOW_WORDS[pattern](c);
			facts.traits.push({ en, ja, style: true, glow: true });
			return part;
		},
	};
}

export const TAIL_OUTLINES = ['fan', 'fork', 'lunate', 'heterocercal', 'round'];
export const FIN_OUTLINES = ['sail', 'triangle', 'round', 'frill', 'leaf'];
