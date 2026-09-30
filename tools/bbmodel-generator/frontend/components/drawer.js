// Bottom drawer: Texture / Outliner / Parts / Animations / Validation / JSON tabs.

import { animationName, buildBBModel, creatureManifest } from '/backend/index.js';
import { button, clear, h } from './dom.js';
import { TextureView } from './texture_view.js';

const TABS = ['Texture', 'Outliner', 'Parts', 'Animations', 'Validation', 'JSON'];

export class Drawer {
	constructor(el, store, actions) {
		this.el = el;
		this.store = store;
		this.actions = actions;
		this.tab = 'Texture';
		this.jsonView = 'bbmodel';
		this.tabBar = h('div', { class: 'tabs' });
		this.body = h('div', { class: 'drawer-body' });
		this.textureView = new TextureView(h('div', { class: 'texture-view' }));
		this.textureView.onSelect = (bone) => actions.selectBone(bone);
		el.append(this.tabBar, this.body);
		new ResizeObserver(() => this.tab === 'Texture' && this.textureView.draw()).observe(this.body);
	}

	render() {
		const { model, report } = this.store.get();
		const rep = report || model?.validation;
		clear(
			this.tabBar,
			TABS.map((t) => {
				let badge = null;
				if (t === 'Validation' && rep) badge = h('span', { class: `badge ${rep.errors ? 'bad' : rep.warnings ? 'warn' : 'ok'}` }, rep.errors || rep.warnings || '✓');
				if (t === 'Animations' && model) badge = h('span', { class: 'badge' }, model.animations.length);
				return h('button', { class: `tab ${this.tab === t ? 'active' : ''}`, onClick: () => ((this.tab = t), this.render()) }, t, badge);
			}),
		);
		if (!model) return clear(this.body, h('p', { class: 'muted' }, 'Generating…'));
		switch (this.tab) {
			case 'Texture':
				clear(this.body, this.textureView.el);
				this.textureView.render(model);
				break;
			case 'Outliner':
				clear(this.body, this.#outliner(model));
				break;
			case 'Parts':
				clear(this.body, this.#parts(model));
				break;
			case 'Animations':
				clear(this.body, this.#animations(model));
				break;
			case 'Validation':
				clear(this.body, this.#validation(rep));
				break;
			default:
				clear(this.body, this.#json(model));
		}
	}

	#outliner(model) {
		const selected = this.store.get().selectedBone;
		const node = (bone, depth) => {
			const cubes = model.geometry.cubes.filter((c) => c.bone === bone.name);
			const kids = model.geometry.bones.filter((b) => b.parent === bone.name);
			const rot = bone.rotation.some((v) => v) ? ` rot [${bone.rotation.join(', ')}]` : '';
			return h(
				'div',
				{ class: 'tree-node' },
				h(
					'div',
					{ class: `tree-row bone ${selected === bone.name ? 'selected' : ''}`, style: { paddingLeft: `${depth * 16 + 6}px` }, onClick: () => this.actions.selectBone(bone.name) },
					h('span', { class: 'tree-icon' }, '▾'),
					h('b', {}, bone.name),
					h('span', { class: 'muted' }, ` ${bone.role}${bone.side ? ` · ${bone.side}` : ''} · pivot [${bone.pivot.join(', ')}]${rot}`),
				),
				cubes.map((c) =>
					h(
						'div',
						{ class: 'tree-row cube', style: { paddingLeft: `${depth * 16 + 24}px` } },
						h('span', { class: 'tree-icon cube' }, '▪'),
						c.name,
						h('span', { class: 'muted' }, ` ${c.material} · ${c.to.map((v, i) => Math.round((v - c.from[i]) * 100) / 100).join('×')}${c.rotation.some((v) => v) ? ` · rot [${c.rotation.join(', ')}]` : ''}`),
					),
				),
				kids.map((k) => node(k, depth + 1)),
			);
		};
		const root = model.geometry.bones.find((b) => !b.parent);
		return h('div', { class: 'tree' }, node(root, 0));
	}

	#parts(model) {
		const rows = model.semantic.list.map((p) => {
			const { id, kind, ...rest } = p;
			return h('tr', {}, h('td', {}, h('b', {}, id)), h('td', { class: 'muted' }, kind), h('td', { class: 'mono' }, compact(rest)));
		});
		return h(
			'div',
			{ class: 'table-wrap' },
			h('p', { class: 'muted' }, `Semantic parts produced by the ${model.template} template from the definition (after preset, scale and variation). The geometry is built from these values.`),
			h('table', { class: 'grid-table' }, h('thead', {}, h('tr', {}, h('th', {}, 'Part'), h('th', {}, 'Kind'), h('th', {}, 'Parameters'))), h('tbody', {}, rows)),
		);
	}

	#animations(model) {
		const current = this.store.get().animation;
		if (!model.animations.length) return h('p', { class: 'muted' }, 'No animations (Generate Animation off, or Animation Ready off).');
		return h(
			'div',
			{ class: 'anim-list' },
			model.animations.map((a) =>
				h(
					'div',
					{ class: `anim-row ${current === a.name ? 'active' : ''}`, onClick: () => this.actions.playAnimation(a.name) },
					h('span', { class: 'play' }, current === a.name ? '■' : '▶'),
					h('b', {}, animationName(model, a.name)),
					h('span', { class: `loop ${a.loop}` }, a.loop),
					h('span', { class: 'muted' }, `${a.length}s · ${Object.keys(a.tracks).length} bones`),
					h('span', { class: 'desc muted' }, a.description || ''),
				),
			),
		);
	}

	#validation(rep) {
		if (!rep) return h('p', { class: 'muted' }, 'Not validated yet');
		const order = { error: 0, warning: 1, info: 2 };
		const items = rep.items.slice().sort((a, b) => order[a.level] - order[b.level]);
		return h(
			'div',
			{ class: 'validation' },
			h('div', { class: `validation-summary ${rep.errors ? 'bad' : rep.warnings ? 'warn' : 'ok'}` }, rep.errors ? `✖ ${rep.errors} error(s), ${rep.warnings} warning(s)` : rep.warnings ? `▲ ${rep.warnings} warning(s) — the model can be exported` : '✔ All checks passed — ready for Blockbench'),
			h(
				'p',
				{ class: 'muted' },
				'Checks: bone / cube name duplicates, UV range and overlap, texture references and usage, empty bones, pivots, extreme coordinates, animation targets and values, JSON validity, UUIDs and outliner references.',
			),
			items.map((i) => h('div', { class: `vitem ${i.level}` }, h('span', { class: 'lvl' }, i.level), h('code', {}, i.check), h('span', {}, i.message))),
		);
	}

	#json(model) {
		const pick = (key, label) => button(label, () => ((this.jsonView = key), this.render()), { kind: `small ${this.jsonView === key ? 'on' : 'ghost'}` });
		let data;
		if (this.jsonView === 'definition') {
			data = { ...model.definition };
			delete data._variation;
		} else if (this.jsonView === 'manifest') data = creatureManifest(model);
		else {
			data = buildBBModel(model);
			data.textures = data.textures.map((t) => ({ ...t, source: `${t.source.slice(0, 48)}… (${t.source.length} chars)` }));
		}
		const text = JSON.stringify(data, null, 2);
		const pre = h('pre', { class: 'json' }, text.length > 400000 ? text.slice(0, 400000) + '\n…' : text);
		return h(
			'div',
			{ class: 'json-view' },
			h('div', { class: 'atlas-toolbar' }, pick('bbmodel', '.bbmodel'), pick('definition', 'Resolved Definition'), pick('manifest', 'creature.json'), h('span', { class: 'spacer' }), button('Copy', () => navigator.clipboard?.writeText(text), { kind: 'small ghost' })),
			pre,
		);
	}
}

function compact(obj) {
	return JSON.stringify(obj, (k, v) => (typeof v === 'number' ? Math.round(v * 100) / 100 : v)).replace(/"(\w+)":/g, '$1: ').replace(/,/g, ', ');
}
