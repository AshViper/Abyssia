// Reference predictors that bracket the metrics:
//   oracle   the target itself (should score 1.0)
//   empty    text2spec: one grey box / edit: no-op patch (the floor)
//   keyword  text2spec: first archetype whose kind word appears in the prompt, sampled with a fixed seed
//            edit: no-op patch. A trained model must beat this clearly.

import { ARCHETYPES, sampleArchetype } from './archetypes/index.js';
import { createRng } from './rng.js';
import { serializeSpec } from './spec.js';

const BOX = { orientation: 'upright', parts: [{ name: 'body', shape: 'box', size: [8, 8, 8], color: '#808080' }] };

let kindIndex = null;
function kinds() {
	if (kindIndex) return kindIndex;
	kindIndex = [];
	for (const id of Object.keys(ARCHETYPES))
		for (let s = 0; s < 40; s++) {
			const { facts } = sampleArchetype(id, createRng(`kw:${id}:${s}`));
			for (const w of [facts.kind.en, facts.kind.ja]) if (!kindIndex.some((k) => k.word === w)) kindIndex.push({ word: w, id });
		}
	kindIndex.sort((a, b) => b.word.length - a.word.length);
	return kindIndex;
}

export function predict(rec, mode) {
	if (mode === 'oracle') return rec.task === 'edit' ? JSON.stringify(rec.target_patch) : serializeSpec(rec.target_spec);
	if (rec.task === 'edit') return '[]';
	if (mode === 'empty') return serializeSpec(BOX);
	const hit = kinds().find((k) => rec.prompt.toLowerCase().includes(k.word.toLowerCase()));
	return serializeSpec(hit ? sampleArchetype(hit.id, createRng(`kw:${hit.id}:0`)).spec : BOX);
}
