// Right panel: Model Settings, statistics and the Generate / Export buttons.

import { FORMAT_VERSIONS, MODEL_FORMATS } from '/backend/generator/context.js';
import { button, checkbox, clear, field, h, numberInput, section, selectInput } from './dom.js';

export class SettingsPanel {
	constructor(el, store, actions) {
		this.el = el;
		this.store = store;
		this.actions = actions;
		this.statsEl = h('div', { class: 'stats' });
		this.sizeInputs = {};
	}

	#set(key, value, reason = 'settings') {
		this.store.update((s) => {
			s.settings[key] = value;
		}, reason);
		this.actions.regenerate();
	}

	render() {
		const s = this.store.get().settings;
		const size = (axis, label) => {
			const input = numberInput({
				value: s.size[axis] || null,
				min: 1,
				max: 512,
				step: 1,
				placeholder: '—',
				onChange: (v) => {
					this.store.update((st) => {
						st.settings.size[axis] = v && v > 0 ? v : 0;
					}, 'settings');
					this.actions.regenerate();
				},
			});
			this.sizeInputs[axis] = input;
			return field(label, input, { hint: 'Overall model size in pixels (1/16 block). Empty = natural size from the definition.' });
		};
		const resetSize = button('↺', () => {
			this.store.update((st) => (st.settings.size = { width: 0, height: 0, length: 0 }), 'settings');
			for (const i of Object.values(this.sizeInputs)) i.value = '';
			this.actions.regenerate();
		}, { kind: 'small ghost', title: 'Natural size' });

		clear(
			this.el,
			h('div', { class: 'panel-title' }, 'Model Settings'),
			section('Size', [
				h('div', { class: 'size-grid' }, size('width', 'Width'), size('height', 'Height'), size('length', 'Length'), resetSize),
				field('Pixel Density', selectInput({ value: s.pixelScale, options: [{ value: 8, label: '8 px / block (chunky)' }, { value: 16, label: '16 px / block (vanilla)' }, { value: 32, label: '32 px / block (HD)' }], onChange: (v) => this.#set('pixelScale', Number(v)) }), { hint: '1 pixel = 1/16 block is standard. 32 = 2 texels per model pixel; 8 = geometry snapped to 2 px.' }),
				field('Block Count', numberInput({ value: s.blockCount || null, min: 1, max: 999, placeholder: '∞', onChange: (v) => this.#set('blockCount', v && v > 0 ? Math.round(v) : 0) }), { hint: 'Maximum number of cubes. Fine details are dropped first.' }),
				field('Detail Level', selectInput({ value: s.detail, options: [{ value: 'high', label: 'High' }, { value: 'medium', label: 'Medium' }, { value: 'low', label: 'Low' }], onChange: (v) => this.#set('detail', v) }), { hint: 'LOD: number of teeth, segments, legs and small parts.' }),
			]),
			section('Texture & UV', [
				field('Texture Size', selectInput({ value: s.textureSize, options: ['auto', 16, 32, 64, 128, 256].map((v) => ({ value: v, label: v === 'auto' ? 'Auto (smallest fit)' : `${v} × ${v}` })), onChange: (v) => this.#set('textureSize', v === 'auto' ? 'auto' : Number(v)) })),
				field('UV Mode', selectInput({ value: s.uvMode, options: [{ value: 'box', label: 'Box UV' }, { value: 'per_face', label: 'Per Face UV' }], onChange: (v) => this.#set('uvMode', v) })),
				checkbox({ checked: s.mirrorUV, label: 'Mirror UV for left/right parts', hint: 'Share one UV region between symmetric parts (overlapping by design).', onChange: (v) => this.#set('mirrorUV', v) }),
			]),
			section('Blockbench', [
				field('Format', selectInput({ value: s.modelFormat, options: Object.entries(MODEL_FORMATS).map(([value, label]) => ({ value, label })), onChange: (v) => this.#set('modelFormat', v) })),
				field('Project Version', selectInput({ value: s.formatVersion, options: FORMAT_VERSIONS.map((v) => ({ value: v, label: v === '5.0' ? '5.0 (Blockbench 5)' : '4.10 (Blockbench 4 compatible)' })), onChange: (v) => this.#set('formatVersion', v) })),
				field('Anim. names', selectInput({ value: s.animationNaming, options: [{ value: 'auto', label: 'Auto' }, { value: 'plain', label: 'idle' }, { value: 'bedrock', label: 'animation.<id>.idle' }], onChange: (v) => this.#set('animationNaming', v) })),
			]),
			section('Generation', [
				checkbox({ checked: s.animationReady, label: 'Animation Ready', hint: 'Bone hierarchy with pivots at joints. Off = static model with one bone.', onChange: (v) => this.#set('animationReady', v) }),
				checkbox({ checked: s.generateTexture, label: 'Generate Texture', hint: 'Off = flat colour template texture.', onChange: (v) => this.#set('generateTexture', v) }),
				checkbox({ checked: s.generateUV, label: 'Generate UV', hint: 'Off = all cubes at UV 0,0 for manual layout.', onChange: (v) => this.#set('generateUV', v) }),
				checkbox({ checked: s.generateAnimation, label: 'Generate Animation', onChange: (v) => this.#set('generateAnimation', v) }),
				checkbox({ checked: s.glowLayer, label: 'Glow Texture Layer', hint: 'Bioluminescent organs go to <id>_glow.png', onChange: (v) => this.#set('glowLayer', v) }),
			]),
			section('Result', [this.statsEl]),
			h(
				'div',
				{ class: 'actions' },
				h('div', { class: 'row' }, button('Generate Model', () => this.actions.generateModel(), { kind: 'primary', title: 'Rebuild geometry, UV, texture and animations (F5)' }), checkbox({ checked: this.store.get().autoUpdate, label: 'Auto', onChange: (v) => this.store.update((st) => (st.autoUpdate = v), 'ui') })),
				button('Generate Texture', () => this.actions.generateTexture(), { title: 'Repaint with a new texture seed (keeps the model)' }),
				button('Generate BBModel', () => this.actions.downloadBBModel(), { kind: 'accent', title: 'Validate and download <id>.bbmodel' }),
				h('div', { class: 'row' }, button('Export ZIP', () => this.actions.exportZip()), button('Save to output/', () => this.actions.saveOutput(), { title: 'Write output/<Name>/ on disk' })),
			),
		);
		this.renderStats();
	}

	renderStats() {
		const { model, report } = this.store.get();
		if (!model) return clear(this.statsEl, h('p', { class: 'muted' }, 'No model yet'));
		const st = model.stats;
		const b = model.bounds.size;
		for (const [axis, i] of [['width', 0], ['height', 1], ['length', 2]]) {
			if (this.sizeInputs[axis]) this.sizeInputs[axis].placeholder = String(Math.round(b[i] * 10) / 10);
		}
		const rep = report || model.validation;
		const row = (k, v) => h('div', { class: 'stat' }, h('span', {}, k), h('b', {}, v));
		clear(
			this.statsEl,
			row('Cubes', `${st.cubes}${this.store.get().settings.blockCount ? ` / ${this.store.get().settings.blockCount}` : ''}${st.removedByBudget ? ` (−${st.removedByBudget})` : ''}`),
			row('Bones', st.bones),
			row('Size', `${b.map((v) => Math.round(v * 10) / 10).join(' × ')} px`),
			row('Blocks', st.sizeBlocks.join(' × ')),
			row('Texture', `${st.texture}${st.texture !== st.uvResolution ? ` (UV ${st.uvResolution})` : ''}`),
			row('UV usage', `${st.uvUsage}%`),
			row('Glow px', st.glowPixels),
			row('Animations', st.animations),
			row('Time', `${Object.values(model.timings).reduce((a, v) => a + v, 0).toFixed(0)} ms`),
			h('div', { class: `validation-badge ${rep.errors ? 'bad' : rep.warnings ? 'warn' : 'ok'}` }, rep.errors ? `${rep.errors} error(s)` : rep.warnings ? `${rep.warnings} warning(s)` : 'Validation OK'),
		);
	}
}
