// Style keys of the parts template (belly / color_to / glow_pattern) sprinkled over sampled specs,
// per archetype, so the model learns when and how to use them. Applied after the body-plan sampler.
//   rule: [part name regex, probability, colours?, label [en, ja] for gradient traits]

import { kit } from './kit.js';

const PALE = ['white', 'cream', 'light gray', 'beige'];
const WARM = ['red', 'orange', 'dark red', 'yellow'];
const BRIGHT = ['red', 'orange', 'yellow', 'white', 'pink', 'cyan', 'lavender', 'purple', 'teal'];

const STYLE = {
	fish: { belly: [/^body$/, 0.45], gradient: [/^body$/, 0.2, [...WARM, 'teal']], glow: [/^body$/, 0.12, 'spots'] },
	eel: { belly: [/^body$/, 0.4], gradient: [/^body$/, 0.2, [...WARM, 'white']], glow: [/^body$/, 0.18, 'edges'] },
	jellyfish: { glow: [/^bell$/, 0.25, 'rim'], gradient: [/^tentacle$/, 0.25, BRIGHT, ['tentacles', '触手']] },
	squid: { glow: [/^mantle$/, 0.12, 'spots'], gradient: [/^arm$/, 0.2, BRIGHT, ['arms', '腕']] },
	octopus: { glow: [/^mantle$/, 0.1, 'spots'], gradient: [/^arm$/, 0.25, BRIGHT, ['arms', '腕']] },
	crab: { belly: [/^carapace$/, 0.3] },
	shrimp: { gradient: [/^body$/, 0.2, ['white', 'red', 'orange', 'blue']], glow: [/^body$/, 0.08, 'spots'] },
	isopod: { belly: [/^body$/, 0.3] },
	tubeworm: { gradient: [/^plume$/, 0.2, ['white', 'yellow', 'orange'], ['plume', '鰓冠']] },
	snail: { gradient: [/^shell$/, 0.3, ['white', 'black', 'orange', 'brown'], ['shell', '殻']] },
	sea_slug: { belly: [/^body$/, 0.2], gradient: [/^body$/, 0.25, BRIGHT], glow: [/^body$/, 0.15, 'spots'] },
	starfish: { gradient: [/^arm$/, 0.3, ['yellow', 'white', 'red', 'purple'], ['arms', '腕']] },
	sessile: { gradient: [/^(tentacle|branch|arm)$/, 0.3, BRIGHT, ['tips', '先端']], glow: [/^(head|body)$/, 0.1, 'spots'] },
	quadruped: { belly: [/^body$/, 0.5], gradient: [/^tail$/, 0.1, BRIGHT, ['tail', '尾']] },
	bird: { belly: [/^body$/, 0.5], gradient: [/^tail$/, 0.15, BRIGHT, ['tail feathers', '尾羽']] },
	insect: { glow: [/^abdomen$/, 0.1, 'stripes'], gradient: [/^abdomen$/, 0.15, BRIGHT, ['abdomen', '腹部']] },
	spider: { glow: [/^abdomen$/, 0.12, 'spots'] },
	biped: { glow: [/^torso$/, 0.15, 'edges'] },
	slime: { glow: [/^body$/, 0.15, 'spots'] },
	mushroom: { glow: [/^cap$/, 0.25, 'spots'], gradient: [/^cap$/, 0.2, BRIGHT, ['cap', 'かさ']] },
	plant: { gradient: [/^(blade|petal|crown)$/, 0.3, BRIGHT, ['leaves', '葉']] },
};

/** Mutates sample.spec / sample.facts in place. */
export function applyStyle(sample, rng) {
	const rules = STYLE[sample.archetype];
	if (!rules) return sample;
	const k = kit(rng);
	k.facts.traits = sample.facts.traits; // traits go to the sample's facts
	k.facts.colors = sample.facts.colors;
	const find = (re) => sample.spec.parts.find((p) => re.test(p.name) && p.shape !== 'eye');
	if (rules.belly && rng.chance(rules.belly[1])) {
		const p = find(rules.belly[0]);
		if (p) k.belly(p, PALE);
	}
	if (rules.gradient && rng.chance(rules.gradient[1])) {
		const p = find(rules.gradient[0]);
		if (p) k.gradient(p, rules.gradient[2].filter((c) => c !== undefined), rules.gradient[3] || null);
	}
	if (rules.glow && rng.chance(rules.glow[1])) {
		const p = find(rules.glow[0]);
		if (p && !p.glow) k.glow(p, rules.glow[2]);
	}
	return sample;
}
