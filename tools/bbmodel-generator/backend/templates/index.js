// Template registry. New body plans register here; creatures pick one via "template".

import { ArthropodTemplate } from './arthropod.js';
import { AssemblyTemplate } from './assembly.js';
import { CrustaceanTemplate } from './crustacean.js';
import { EelTemplate } from './eel.js';
import { FishTemplate } from './fish.js';
import { GastropodTemplate } from './gastropod.js';
import { MedusaTemplate } from './medusa.js';
import { PhotoTemplate } from './photo.js';
import { SoftBodyTemplate } from './softbody.js';
import { SquidTemplate } from './squid.js';
import { WormTemplate } from './worm.js';

const REGISTRY = new Map();

export function registerTemplate(TemplateClass) {
	REGISTRY.set(TemplateClass.id, new TemplateClass());
}

[FishTemplate, EelTemplate, SquidTemplate, ArthropodTemplate, CrustaceanTemplate, WormTemplate, SoftBodyTemplate, GastropodTemplate, MedusaTemplate, PhotoTemplate, AssemblyTemplate].forEach(registerTemplate);

/** Category -> template used when a definition does not name its template. */
const CATEGORY_TEMPLATES = {
	deep_sea_fish: 'fish',
	fish: 'fish',
	shark: 'fish',
	eel: 'eel',
	cephalopod: 'squid',
	squid: 'squid',
	isopod: 'arthropod',
	arthropod: 'arthropod',
	crustacean: 'crustacean',
	crab: 'crustacean',
	shrimp: 'crustacean',
	worm: 'worm',
	tubeworm: 'worm',
	echinoderm: 'softbody',
	sea_cucumber: 'softbody',
	gastropod: 'gastropod',
	snail: 'gastropod',
	jellyfish: 'medusa',
	hydromedusa: 'medusa',
	medusa: 'medusa',
	photo: 'photo',
	parts: 'parts',
};

export function inferTemplate(definition) {
	if (definition?.template && REGISTRY.has(definition.template)) return definition.template;
	const byCategory = CATEGORY_TEMPLATES[definition?.category];
	return byCategory && REGISTRY.has(byCategory) ? byCategory : 'fish';
}

export function getTemplate(id) {
	const t = REGISTRY.get(id);
	if (!t) throw new Error(`Unknown template "${id}". Available: ${[...REGISTRY.keys()].join(', ')}`);
	return t;
}

export function listTemplates() {
	return [...REGISTRY.values()].map((t) => ({ id: t.constructor.id, label: t.constructor.label, description: t.constructor.description }));
}
