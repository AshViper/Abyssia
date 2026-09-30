// Viewport toolbar (display toggles, camera views) and the animation timeline.

import { animationName } from '/backend/index.js';
import { button, clear, h, selectInput } from './dom.js';

const TOGGLES = [
	['texture', 'Texture', 'Texture ON/OFF (off = colour by bone role)'],
	['glow', 'Glow', 'Emissive glow layer'],
	['wireframe', 'Wireframe', 'Cube edges'],
	['bones', 'Bones', 'Bone hierarchy lines'],
	['pivots', 'Pivots', 'Pivot points with local axes'],
	['grid', 'Grid', 'Floor grid (1 block = 16 px)'],
	['dark', 'Dark water', 'Dim lighting to judge bioluminescence'],
];

export function renderToolbar(el, viewer, onChange) {
	const o = viewer.options;
	clear(
		el,
		TOGGLES.map(([key, label, title]) =>
			button(label, () => {
				viewer.setOption(key, !viewer.options[key]);
				onChange?.();
				renderToolbar(el, viewer, onChange);
			}, { kind: `tool ${o[key] ? 'on' : ''}`, title }),
		),
		button(o.lighting === 'minecraft' ? 'Light: Minecraft' : 'Light: Blockbench', () => {
			viewer.setOption('lighting', viewer.options.lighting === 'minecraft' ? 'blockbench' : 'minecraft');
			renderToolbar(el, viewer, onChange);
		}, { kind: 'tool', title: 'Shading: Blockbench viewport or Minecraft entity lighting' }),
		h('span', { class: 'spacer' }),
		['front', 'right', 'left', 'top', 'iso'].map((v) => button(v[0].toUpperCase() + v.slice(1), () => viewer.setView(v), { kind: 'tool view', title: `${v} view` })),
		button('Fit', () => viewer.frame(), { kind: 'tool view', title: 'Frame the model' }),
	);
}

export class Timeline {
	constructor(el, viewer, store, actions) {
		this.el = el;
		this.viewer = viewer;
		this.store = store;
		this.actions = actions;
		this.slider = h('input', { type: 'range', min: 0, max: 1, step: 0.001, value: 0, class: 'time' });
		this.timeLabel = h('span', { class: 'time-label mono' }, '0.00 s');
		this.slider.addEventListener('input', () => {
			this.viewer.animator.playing = false;
			this.viewer.animator.seek(Number(this.slider.value) * this.viewer.animator.length);
			this.#labels();
			this.renderButtons();
		});
		this.viewer.animator.onTime = () => this.#labels();
		this.playBtn = h('button', { class: 'btn tool', onClick: () => this.toggle() });
		this.buttonsHost = h('span');
	}

	toggle() {
		const a = this.viewer.animator;
		if (!a.clip) return;
		a.playing = !a.playing;
		this.renderButtons();
	}

	#labels() {
		const a = this.viewer.animator;
		const len = a.length || 1;
		this.slider.value = String(a.time / len);
		this.timeLabel.textContent = `${a.time.toFixed(2)} / ${(a.length || 0).toFixed(2)} s`;
	}

	renderButtons() {
		this.playBtn.textContent = this.viewer.animator.playing ? '❚❚' : '▶';
	}

	render() {
		const { model, animation } = this.store.get();
		const clips = model?.animations || [];
		const speed = selectInput({
			value: this.viewer.animator.speed,
			options: [0.25, 0.5, 1, 1.5, 2].map((v) => ({ value: v, label: `${v}×` })),
			onChange: (v) => (this.viewer.animator.speed = Number(v)),
		});
		clear(
			this.el,
			h('span', { class: 'label' }, 'Animation'),
			selectInput({
				value: animation || '',
				options: [{ value: '', label: '— rest pose —' }, ...clips.map((c) => ({ value: c.name, label: `${model ? animationName(model, c.name) : c.name} (${c.loop}, ${c.length}s)` }))],
				onChange: (name) => this.actions.playAnimation(name || null),
			}),
			this.playBtn,
			speed,
			this.slider,
			this.timeLabel,
		);
		this.renderButtons();
		this.#labels();
	}
}
