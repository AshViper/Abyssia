// Left panel: Creature / Creature Type / Preset, random variation and the definition editor.

import { listTemplates } from '/backend/templates/index.js';
import { button, clear, field, h, rangeInput, section, selectInput } from './dom.js';

export class CreaturePanel {
	constructor(el, store, actions) {
		this.el = el;
		this.store = store;
		this.actions = actions;
		this.editorHost = h('div', { class: 'definition-editor' });
	}

	render() {
		const s = this.store.get();
		const defs = Object.values(s.definitions).sort((a, b) => (a.order ?? 99) - (b.order ?? 99) || a.name.localeCompare(b.name));
		const templates = listTemplates();
		const presets = Object.values(s.presets);
		const seedInput = h('input', { type: 'text', class: 'seed', value: String(s.settings.seed), spellcheck: 'false' });
		seedInput.addEventListener('change', () => {
			const raw = seedInput.value.trim();
			this.actions.setSeed(/^-?\d+$/.test(raw) ? Number(raw) : raw || 1);
		});
		this.seedInput = seedInput;
		const variant = (label) => button(label, () => this.actions.variant(label), { kind: 'small', title: `${s.definition.name} ${label}: fixed seed with full variation` });

		clear(
			this.el,
			h('div', { class: 'panel-title' }, 'Creature'),
			field('Creature', selectInput({
				value: s.creatureId,
				options: [...defs.map((d) => ({ value: d.id, label: d.name_ja ? `${d.name} — ${d.name_ja}` : d.name })), ...(s.definitions[s.creatureId] ? [] : [{ value: s.creatureId, label: `${s.definition.name} (unsaved)` }])],
				onChange: (id) => this.actions.selectCreature(id),
			})),
			field('Creature Type', selectInput({
				value: s.definition.template || 'fish',
				options: templates.map((t) => ({ value: t.id, label: t.label })),
				onChange: (id) => this.actions.setTemplate(id),
			}), { hint: 'Body-plan template used to build the model' }),
			field('Preset', selectInput({
				value: s.presetId,
				options: presets.map((p) => ({ value: p.id, label: p.name })),
				onChange: (id) => this.actions.selectPreset(id),
			})),
			section('Random Variation', [
				h('div', { class: 'seed-row' }, h('span', { class: 'field-label' }, 'Seed'), seedInput, button('🎲', () => this.actions.randomSeed(), { kind: 'small ghost', title: 'Random seed' })),
				h('div', { class: 'variant-row' }, h('span', { class: 'field-label' }, 'Variant'), variant('A'), variant('B'), variant('C')),
				field('Amount', rangeInput({ value: s.settings.variation, min: 0, max: 1, step: 0.05, onChange: (v) => this.actions.setVariation(v), format: (v) => (v === 0 ? 'base' : `${Math.round(v * 100)}%`) }), { hint: '0 = canonical individual; 100% = full natural range of the definition' }),
			], { open: true }),
			h('div', { class: 'panel-title sub' }, 'Definition Editor', h('span', { class: `dirty ${s.dirty ? 'on' : ''}` }, s.dirty ? '● modified' : '')),
			this.editorHost,
			h(
				'div',
				{ class: 'actions' },
				h('div', { class: 'row' }, button('Save Definition', () => this.actions.saveDefinition(), { kind: 'primary', title: `Write definitions/${s.definition.id}.json` }), button('Revert', () => this.actions.revertDefinition(), { kind: 'ghost' })),
				h('div', { class: 'row' }, button('Edit JSON', () => this.actions.editJson()), button('New Creature', () => this.actions.newCreature())),
				h('div', { class: 'row' }, button('Import Image…', () => this.actions.importImage(), { title: 'Build a photo model from a picture (crop, segment, edit the silhouette grid)' })),
			),
		);
	}

	setSeed(seed) {
		if (this.seedInput) this.seedInput.value = String(seed);
	}
}
