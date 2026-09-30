// Texture atlas viewer with UV overlay (per face), base / glow toggle and hover info.

import { FACE_KEYS, uvRect } from '/backend/uv/box_uv.js';
import { button, clear, download, h } from './dom.js';
import { encodePNG } from '/backend/texture/png.js';

const FACE_COLORS = { north: '#5ad1ff', south: '#8a7bff', east: '#ff8a5a', west: '#ffd05a', up: '#6bff9a', down: '#ff5a8a' };

export class TextureView {
	constructor(el) {
		this.el = el;
		this.layer = 'base';
		this.overlay = true;
		this.model = null;
		this.canvas = h('canvas', { class: 'atlas' });
		this.info = h('div', { class: 'atlas-info muted' }, 'Hover the atlas to inspect UV faces');
		this.canvas.addEventListener('pointermove', (e) => this.#hover(e));
		this.canvas.addEventListener('pointerleave', () => {
			this.hot = null;
			this.draw();
		});
		this.onSelect = null;
		this.canvas.addEventListener('click', () => this.hot && this.onSelect?.(this.hot.bone));
	}

	render(model) {
		this.model = model;
		const toggle = (label, key, value) =>
			button(label, () => {
				this[key] = value;
				this.render(this.model);
			}, { kind: `small ${this[key] === value ? 'on' : 'ghost'}` });
		const dl = (which) =>
			button(`${which === 'base' ? 'Base' : 'Glow'} PNG`, () => {
				const img = model.textures[which];
				if (img) download(which === 'base' ? `${model.id}.png` : `${model.id}_glow.png`, encodePNG(img), 'image/png');
			}, { kind: 'small ghost' });
		clear(
			this.el,
			h(
				'div',
				{ class: 'atlas-toolbar' },
				toggle('Base', 'layer', 'base'),
				model.textures.glow ? toggle('Glow', 'layer', 'glow') : null,
				model.textures.glow ? toggle('Both', 'layer', 'both') : null,
				button('UV overlay', () => {
					this.overlay = !this.overlay;
					this.render(this.model);
				}, { kind: `small ${this.overlay ? 'on' : 'ghost'}` }),
				h('span', { class: 'spacer' }),
				h('span', { class: 'muted' }, `${model.uv.imageWidth}×${model.uv.imageHeight} px · UV ${model.uv.width}×${model.uv.height} · ${model.uv.mode === 'box' ? 'Box UV' : 'Per Face UV'}`),
				dl('base'),
				model.textures.glow ? dl('glow') : null,
			),
			h('div', { class: 'atlas-wrap' }, this.canvas),
			this.info,
		);
		requestAnimationFrame(() => this.draw());
	}

	#scale() {
		const wrap = this.canvas.parentElement;
		if (!wrap || !this.model) return 4;
		const { width, height } = this.model.uv;
		const avail = [Math.max(64, wrap.clientWidth - 16), Math.max(64, wrap.clientHeight - 16)];
		return Math.max(1, Math.floor(Math.min(avail[0] / width, avail[1] / height)));
	}

	draw() {
		const m = this.model;
		if (!m) return;
		const s = this.#scale();
		const { width, height } = m.uv;
		this.canvas.width = width * s;
		this.canvas.height = height * s;
		const ctx = this.canvas.getContext('2d');
		ctx.imageSmoothingEnabled = false;
		for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
			ctx.fillStyle = (x + y) % 2 ? '#1a2029' : '#141920';
			ctx.fillRect(x * s, y * s, s, s);
		}
		const drawImg = (img, alpha = 1) => {
			const c = document.createElement('canvas');
			c.width = img.width;
			c.height = img.height;
			c.getContext('2d').putImageData(new ImageData(new Uint8ClampedArray(img.data), img.width, img.height), 0, 0);
			ctx.globalAlpha = alpha;
			ctx.drawImage(c, 0, 0, width * s, height * s);
			ctx.globalAlpha = 1;
		};
		if (this.layer !== 'glow') drawImg(m.textures.base);
		if (this.layer !== 'base' && m.textures.glow) {
			if (this.layer === 'both') ctx.globalCompositeOperation = 'lighter';
			drawImg(m.textures.glow);
			ctx.globalCompositeOperation = 'source-over';
		}
		if (this.overlay) {
			ctx.lineWidth = 1;
			for (const cube of m.geometry.cubes) {
				const cuv = m.uv.cubes[cube.name];
				for (const face of FACE_KEYS) {
					if (!cuv.enabled[face]) continue;
					const r = uvRect(cuv.faces[face]);
					if (r.w <= 0 || r.h <= 0) continue;
					const hot = this.hot && this.hot.cube === cube.name;
					ctx.strokeStyle = hot ? '#ffffff' : FACE_COLORS[face] + (hot ? 'ff' : '88');
					ctx.lineWidth = hot ? 2 : 1;
					ctx.strokeRect(r.x * s + 0.5, r.y * s + 0.5, r.w * s - 1, r.h * s - 1);
				}
			}
		}
	}

	#hover(e) {
		const m = this.model;
		if (!m) return;
		const s = this.#scale();
		const rect = this.canvas.getBoundingClientRect();
		const u = ((e.clientX - rect.left) / rect.width) * m.uv.width;
		const v = ((e.clientY - rect.top) / rect.height) * m.uv.height;
		let hit = null;
		for (const cube of m.geometry.cubes) {
			const cuv = m.uv.cubes[cube.name];
			for (const face of FACE_KEYS) {
				if (!cuv.enabled[face]) continue;
				const r = uvRect(cuv.faces[face]);
				if (u >= r.x && u < r.x + r.w && v >= r.y && v < r.y + r.h) hit = { cube: cube.name, bone: cube.bone, face, material: cube.faceMaterials[face] || cube.material, offset: cuv.offset };
			}
		}
		const px = [Math.floor(u * m.uv.texelsPerUnit), Math.floor(v * m.uv.texelsPerUnit)];
		this.hot = hit;
		this.info.textContent = hit ? `${hit.cube}.${hit.face} · bone ${hit.bone} · ${hit.material} · uv_offset [${hit.offset.join(', ')}] · texel ${px.join(', ')}` : `texel ${px.join(', ')}`;
		void s;
		this.draw();
	}
}
