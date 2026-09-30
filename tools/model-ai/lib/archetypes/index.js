// Registry of body-plan samplers: id -> {fn, weight}. Weight = share in the dataset.

import * as land from './land.js';
import * as sea from './sea.js';
import { applyStyle } from './style.js';

export const ARCHETYPES = {
	fish: { fn: sea.fish, weight: 4 },
	eel: { fn: sea.eel, weight: 2 },
	jellyfish: { fn: sea.jellyfish, weight: 3 },
	squid: { fn: sea.squid, weight: 2 },
	octopus: { fn: sea.octopus, weight: 2 },
	crab: { fn: sea.crab, weight: 2 },
	shrimp: { fn: sea.shrimp, weight: 2 },
	isopod: { fn: sea.isopod, weight: 1 },
	tubeworm: { fn: sea.tubeworm, weight: 1 },
	snail: { fn: sea.snail, weight: 1 },
	sea_slug: { fn: sea.sea_slug, weight: 2 },
	starfish: { fn: sea.starfish, weight: 1 },
	sessile: { fn: sea.sessile, weight: 3 },
	quadruped: { fn: land.quadruped, weight: 3 },
	bird: { fn: land.bird, weight: 2 },
	insect: { fn: land.insect, weight: 2 },
	spider: { fn: land.spider, weight: 1 },
	biped: { fn: land.biped, weight: 2 },
	slime: { fn: land.slime, weight: 1 },
	mushroom: { fn: land.mushroom, weight: 1 },
	plant: { fn: land.plant, weight: 2 },
};

export function sampleArchetype(id, rng) {
	const a = ARCHETYPES[id];
	if (!a) throw new Error(`unknown archetype "${id}" (${Object.keys(ARCHETYPES).join(', ')})`);
	const out = a.fn(rng);
	out.archetype = id;
	return applyStyle(out, rng);
}
