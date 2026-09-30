// Definition Editor: controls generated from the template's editor schema plus the shared
// Palette / Glow / Animation / Parts sections. Every change edits the working definition
// and triggers a live regeneration.

import { getTemplate } from '/backend/templates/index.js';
import { getPath, setPath } from '/backend/generator/util.js';
import { DEFAULT_COLORS } from '/backend/texture/materials.js';
import { button, checkbox, clear, colorInput, field, h, numberInput, rangeInput, section, selectInput } from '../components/dom.js';

const COMMON_PALETTE = ['body', 'belly', 'fin', 'glow', 'eye', 'eye_iris'];

export class DefinitionEditor {
	constructor(el, store, { onChange }) {
		this.el = el;
		this.store = store;
		this.onChange = onChange;
		this.openSections = new Set(['Body Size', 'Head Size', 'Palette']);
	}

	#set(path, value) {
		this.store.update((s) => {
			setPath(s.definition, path, value);
			s.dirty = true;
		}, 'definition');
		this.onChange?.();
	}

	render() {
		const { definition, model, baseDefinition } = this.store.get();
		let template;
		try {
			template = getTemplate(definition.template || 'fish');
		} catch {
			template = getTemplate('fish');
		}
		const merged = template.defaults();
		const get = (path) => {
			const v = getPath(definition, path);
			return v !== undefined ? v : getPath(merged, path);
		};
		const sections = [];

		for (const sec of template.editorSchema()) {
			const rows = sec.fields.map((f) => this.#control(f, get(f.path)));
			sections.push(this.#section(sec.section, rows));
		}
		sections.push(this.#paletteSection(definition));
		sections.push(this.#glowSection(definition, model));
		sections.push(this.#animationSection(definition, template, get));
		sections.push(this.#partsSection(definition, baseDefinition));
		clear(this.el, sections);
	}

	#section(title, rows) {
		const s = section(title, rows, { open: this.openSections.has(title) });
		s.addEventListener('toggle', () => (s.open ? this.openSections.add(title) : this.openSections.delete(title)));
		return s;
	}

	#control(f, value) {
		const onChange = (v) => this.#set(f.path, v);
		switch (f.type) {
			case 'bool':
				return checkbox({ checked: !!value, label: f.label, onChange });
			case 'range':
				return field(f.label, rangeInput({ value, min: f.min, max: f.max, step: f.step ?? 0.01, onChange, format: (v) => (f.step >= 1 ? v : Number(v).toFixed(2)) }));
			case 'select':
				return field(f.label, selectInput({ value, options: f.options, onChange }));
			case 'float':
				return field(f.label, numberInput({ value, min: f.min, max: f.max, step: f.step ?? 0.1, onChange: (v) => v != null && onChange(v) }));
			default:
				return field(f.label, numberInput({ value, min: f.min, max: f.max, step: 1, onChange: (v) => v != null && onChange(Math.round(v)) }));
		}
	}

	#paletteSection(def) {
		const palette = def.palette || {};
		// Photo models only use their own photo palette (p0..pN + body)
		const keys = def.template === 'photo' ? Object.keys(palette) : [...new Set([...COMMON_PALETTE, ...Object.keys(palette)])];
		const rows = keys.map((key) =>
			h(
				'div',
				{ class: 'palette-row' },
				h('span', { class: 'field-label' }, key),
				colorInput({ value: palette[key] || DEFAULT_COLORS[key] || '#808080', onChange: (v) => this.#set(`palette.${key}`, v) }),
			),
		);
		const missing = Object.keys(DEFAULT_COLORS).filter((k) => !keys.includes(k));
		const add = selectInput({
			value: '',
			options: [{ value: '', label: '+ add colour…' }, ...missing.map((k) => ({ value: k, label: k }))],
			onChange: (k) => {
				if (!k) return;
				this.#set(`palette.${k}`, DEFAULT_COLORS[k]);
				this.render();
			},
		});
		return this.#section('Palette', [...rows, add]);
	}

	#glowSection(def, model) {
		const entries = def.glow || [];
		const parts = [...new Set(['eyes', ...(model?.geometry.bones.map((b) => b.role) || []), ...(model?.geometry.bones.map((b) => b.name) || [])])];
		const paletteKeys = Object.keys(def.palette || {}).filter((k) => k.startsWith('glow'));
		const colorKeys = [...new Set(['glow', ...paletteKeys])];
		const write = (next) => {
			this.#set('glow', next);
			this.render();
		};
		const rows = entries.map((entry, i) => {
			const update = (patch, rerender = false) => {
				const current = this.store.get().definition.glow || [];
				const next = current.map((e, j) => (j === i ? { ...e, ...patch } : e));
				if (rerender) write(next);
				else this.#set('glow', next);
			};
			const body = [
				field('Part', selectInput({ value: entry.part, options: parts.includes(entry.part) ? parts : [entry.part, ...parts], onChange: (v) => update({ part: v }) })),
				field('Intensity', rangeInput({ value: entry.intensity ?? 1, min: 0, max: 2, step: 0.05, onChange: (v) => update({ intensity: v }), format: (v) => Number(v).toFixed(2) })),
				field('Colour', selectInput({ value: entry.color || 'glow', options: colorKeys, onChange: (v) => update({ color: v }) })),
				checkbox({ checked: !!entry.anchor, label: 'Spot (Glow Position)', onChange: (on) => update(on ? { anchor: [0.5, 0.5, 0.5], radius: 0.8, scatter: undefined } : { anchor: undefined }, true) }),
				field('Scatter', numberInput({ value: entry.scatter || null, min: 0, max: 64, placeholder: '—', onChange: (v) => update(v > 0 ? { scatter: Math.round(v), anchor: undefined, radius: entry.radius ?? 0.5 } : { scatter: undefined }, true) }), { hint: 'Random light spots on the part surface (seeded)' }),
			];
			if (entry.scatter > 0) body.push(field('Glow size', rangeInput({ value: entry.radius ?? 0.5, min: 0.3, max: 3, step: 0.05, onChange: (v) => update({ radius: v }), format: (v) => Number(v).toFixed(2) })));
			if (entry.anchor) {
				['X', 'Y', 'Z'].forEach((axis, a) => {
					body.push(field(`Position ${axis}`, rangeInput({ value: entry.anchor[a], min: 0, max: 1, step: 0.01, onChange: (v) => update({ anchor: entry.anchor.map((x, k) => (k === a ? v : x)) }), format: (v) => Number(v).toFixed(2) })));
				});
				body.push(field('Glow size', rangeInput({ value: entry.radius ?? 0.8, min: 0.3, max: 4, step: 0.05, onChange: (v) => update({ radius: v }), format: (v) => Number(v).toFixed(2) })));
				body.push(field('Count', numberInput({ value: entry.count ?? 1, min: 1, max: 32, onChange: (v) => v && update({ count: Math.round(v) }) })));
				body.push(field('Step Z', numberInput({ value: entry.step?.[2] ?? 0, min: -16, max: 16, step: 0.5, onChange: (v) => update({ step: [0, 0, v ?? 0] }) })));
				body.push(checkbox({ checked: !!entry.mirror, label: 'Mirror left/right', onChange: (v) => update({ mirror: v }) }));
			}
			body.push(button('Remove', () => write((this.store.get().definition.glow || []).filter((_, j) => j !== i)), { kind: 'small ghost' }));
			return h('div', { class: 'glow-entry' }, body);
		});
		rows.push(button('+ Glow entry', () => write([...(this.store.get().definition.glow || []), { part: parts[0] || 'body', intensity: 1 }]), { kind: 'small' }));
		return this.#section('Glow (Position / Size / Colour)', rows);
	}

	#animationSection(def, template, get) {
		const catalog = Object.keys(template.animationCatalog());
		const enabled = new Set(def.animations?.length ? def.animations : catalog);
		const toggles = catalog.map((name) =>
			checkbox({
				checked: enabled.has(name),
				label: name,
				onChange: (on) => {
					const cur = this.store.get().definition.animations;
					const now = new Set(cur?.length ? cur : catalog);
					this.#set('animations', catalog.filter((n) => (n === name ? on : now.has(n))));
				},
			}),
		);
		return this.#section('Animation', [
			field('Speed', rangeInput({ value: get('animation.speed') ?? 1, min: 0.25, max: 3, step: 0.05, onChange: (v) => this.#set('animation.speed', v), format: (v) => `${Number(v).toFixed(2)}×` })),
			field('Amplitude', rangeInput({ value: get('animation.amplitude') ?? 1, min: 0, max: 2, step: 0.05, onChange: (v) => this.#set('animation.amplitude', v), format: (v) => `${Number(v).toFixed(2)}×` })),
			h('div', { class: 'check-grid' }, toggles),
		]);
	}

	#partsSection(def, base) {
		const parts = def.parts || [];
		const all = [...new Set([...(base?.parts || []), ...parts])];
		if (!all.length) return this.#section('Parts', [h('p', { class: 'muted' }, 'All template parts are enabled (no "parts" list).')]);
		return this.#section('Parts', [
			h('p', { class: 'muted' }, 'Semantic parts of the definition. Unchecked parts are left out of the model.'),
			h(
				'div',
				{ class: 'check-grid' },
				all.map((p) =>
					checkbox({
						checked: parts.includes(p),
						label: p,
						onChange: (on) => {
							const cur = this.store.get().definition.parts || [];
							const next = all.filter((x) => (x === p ? on : cur.includes(x)));
							this.#set('parts', next.length ? next : ['body']);
						},
					}),
				),
			),
		]);
	}
}
