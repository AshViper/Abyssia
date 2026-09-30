// Import Image: photo -> "photo" template definition.
// Load a picture (file / drop / Ctrl+V), drag a rectangle around the subject (a click sets the
// seed point on it), tune the segmentation, touch up the silhouette grid by hand, then create
// the creature. Analysis runs in the browser with the same backend code as `node photo.js`.

import { DEFAULT_THRESHOLD, GRID_CHARS, definitionFromImage, maskPreview, trimGrid } from '/backend/image/index.js';
import { button, checkbox, field, h, rangeInput, toast } from '../components/dom.js';

const MAX_SOURCE = 1600;
const VIEW = 440;

async function decodeImage(blob) {
	const bmp = await createImageBitmap(blob);
	const k = Math.min(1, MAX_SOURCE / Math.max(bmp.width, bmp.height));
	const canvas = document.createElement('canvas');
	canvas.width = Math.round(bmp.width * k);
	canvas.height = Math.round(bmp.height * k);
	const g = canvas.getContext('2d', { willReadFrequently: true });
	g.drawImage(bmp, 0, 0, canvas.width, canvas.height);
	return { image: g.getImageData(0, 0, canvas.width, canvas.height), canvas };
}

function putImage(canvas, img, maxW, maxH) {
	const k = Math.min(maxW / img.width, maxH / img.height);
	canvas.width = Math.max(1, Math.round(img.width * k));
	canvas.height = Math.max(1, Math.round(img.height * k));
	const tmp = document.createElement('canvas');
	tmp.width = img.width;
	tmp.height = img.height;
	tmp.getContext('2d').putImageData(new ImageData(new Uint8ClampedArray(img.data), img.width, img.height), 0, 0);
	const g = canvas.getContext('2d');
	g.imageSmoothingEnabled = true;
	g.drawImage(tmp, 0, 0, canvas.width, canvas.height);
	return k;
}

/**
 * @param {{definitions: Record<string, object>, onCreate: (def: object) => void}} opts
 */
export function openImageImport({ definitions, onCreate }) {
	const st = {
		image: null,
		source: null, // canvas with the decoded picture
		fileName: 'image',
		crop: null,
		seed: null,
		opts: { rows: 24, colors: 8, threshold: DEFAULT_THRESHOLD, ground: true, smooth: 1, depthRatio: 1 },
		result: null,
		grid: null,
		edited: false,
		paint: 0,
	};

	const srcCanvas = h('canvas', { class: 'img-src' });
	const maskCanvas = h('canvas', { class: 'img-mask' });
	const gridCanvas = h('canvas', { class: 'img-grid' });
	const info = h('div', { class: 'img-info muted' }, 'Drop, paste (Ctrl+V) or open an image.');
	const swatches = h('div', { class: 'img-swatches' });
	const fileInput = h('input', { type: 'file', accept: 'image/*', style: { display: 'none' } });
	const idInput = h('input', { type: 'text', class: 'img-id', value: 'photo_creature', spellcheck: 'false' });
	const nameInput = h('input', { type: 'text', class: 'img-name', value: '', placeholder: 'Display name', spellcheck: 'false' });
	let srcScale = 1;

	// ---- source view: drag = crop rectangle, click = seed point ----
	function drawSource() {
		if (!st.source) {
			srcCanvas.width = VIEW;
			srcCanvas.height = 260;
			const g = srcCanvas.getContext('2d');
			g.fillStyle = '#0a0f15';
			g.fillRect(0, 0, VIEW, 260);
			g.fillStyle = '#7b8898';
			g.font = '13px sans-serif';
			g.textAlign = 'center';
			g.fillText('Drop an image here, paste it (Ctrl+V) or click Open…', VIEW / 2, 130);
			return;
		}
		srcScale = putImage(srcCanvas, st.image, VIEW, VIEW);
		const g = srcCanvas.getContext('2d');
		if (st.crop) {
			const { x, y, w, h: ch } = st.crop;
			g.fillStyle = 'rgba(0,0,0,0.45)';
			g.fillRect(0, 0, srcCanvas.width, y * srcScale);
			g.fillRect(0, (y + ch) * srcScale, srcCanvas.width, srcCanvas.height);
			g.fillRect(0, y * srcScale, x * srcScale, ch * srcScale);
			g.fillRect((x + w) * srcScale, y * srcScale, srcCanvas.width, ch * srcScale);
			g.strokeStyle = '#3fd0c9';
			g.setLineDash([5, 4]);
			g.lineWidth = 1.5;
			g.strokeRect(x * srcScale + 0.5, y * srcScale + 0.5, w * srcScale, ch * srcScale);
			g.setLineDash([]);
		}
		if (st.seed) {
			const c = st.crop || { x: 0, y: 0, w: st.image.width, h: st.image.height };
			const sx = (c.x + st.seed.x * c.w) * srcScale, sy = (c.y + st.seed.y * c.h) * srcScale;
			g.strokeStyle = '#e8615b';
			g.lineWidth = 2;
			g.beginPath();
			g.moveTo(sx - 6, sy);
			g.lineTo(sx + 6, sy);
			g.moveTo(sx, sy - 6);
			g.lineTo(sx, sy + 6);
			g.stroke();
		}
	}
	let drag = null;
	const toImage = (e) => {
		const r = srcCanvas.getBoundingClientRect();
		return [((e.clientX - r.left) * (srcCanvas.width / r.width)) / srcScale, ((e.clientY - r.top) * (srcCanvas.height / r.height)) / srcScale];
	};
	srcCanvas.addEventListener('pointerdown', (e) => {
		if (!st.image) return fileInput.click();
		srcCanvas.setPointerCapture(e.pointerId);
		drag = { start: toImage(e), moved: false };
	});
	srcCanvas.addEventListener('pointermove', (e) => {
		if (!drag) return;
		const [x, y] = toImage(e);
		const [x0, y0] = drag.start;
		if (Math.abs(x - x0) * srcScale + Math.abs(y - y0) * srcScale < 5 && !drag.moved) return;
		drag.moved = true;
		const cx = Math.max(0, Math.min(x, x0)), cy = Math.max(0, Math.min(y, y0));
		st.crop = { x: Math.round(cx), y: Math.round(cy), w: Math.round(Math.min(st.image.width, Math.max(x, x0)) - cx), h: Math.round(Math.min(st.image.height, Math.max(y, y0)) - cy) };
		st.seed = null;
		drawSource();
	});
	srcCanvas.addEventListener('pointerup', (e) => {
		if (!drag) return;
		const d = drag;
		drag = null;
		if (d.moved) {
			if (st.crop.w < 8 || st.crop.h < 8) st.crop = null;
		} else {
			const [x, y] = toImage(e);
			const c = st.crop || { x: 0, y: 0, w: st.image.width, h: st.image.height };
			if (x >= c.x && y >= c.y && x <= c.x + c.w && y <= c.y + c.h) st.seed = { x: (x - c.x) / c.w, y: (y - c.y) / c.h };
		}
		drawSource();
		analyse();
	});

	// ---- analysis ----
	let timer = null;
	function analyse(delay = 60) {
		clearTimeout(timer);
		timer = setTimeout(run, delay);
	}
	function run() {
		if (!st.image) return;
		const t0 = performance.now();
		try {
			st.result = definitionFromImage(st.image, {
				id: 'photo_creature',
				crop: st.crop,
				seed: st.seed || undefined,
				rows: st.opts.rows,
				colors: st.opts.colors,
				threshold: st.opts.threshold,
				ground: st.opts.ground,
				smooth: st.opts.smooth,
				depthRatio: st.opts.depthRatio,
				source: st.fileName,
			});
			st.grid = st.result.definition.params.photo.grid.slice();
			st.edited = false;
			const g = st.grid;
			info.textContent = `${g.length} × ${g[0]?.length || 0} cells · ${st.result.profile.palette.length} colours · subject ${Math.round((st.result.segmentation.area / (st.result.segmentation.width * st.result.segmentation.height)) * 100)}% of crop · ${Math.round(performance.now() - t0)} ms`;
			info.className = 'img-info muted';
		} catch (err) {
			st.result = null;
			st.grid = null;
			info.textContent = err.message;
			info.className = 'img-info bad';
		}
		drawMask();
		drawGrid();
		drawSwatches();
	}

	function drawMask() {
		if (!st.result) {
			maskCanvas.width = maskCanvas.height = 1;
			return;
		}
		putImage(maskCanvas, maskPreview(st.result.segmentation), 220, VIEW);
	}

	// ---- grid editor: left = paint selected colour, right = erase ----
	let cellPx = 8;
	function drawGrid() {
		const grid = st.grid;
		if (!grid?.length) {
			gridCanvas.width = gridCanvas.height = 1;
			return;
		}
		const rows = grid.length, cols = grid[0].length;
		cellPx = Math.max(3, Math.floor(Math.min(260 / cols, VIEW / rows)));
		gridCanvas.width = cols * cellPx;
		gridCanvas.height = rows * cellPx;
		const g = gridCanvas.getContext('2d');
		const pal = st.result.profile.palette;
		for (let r = 0; r < rows; r++) {
			for (let c = 0; c < cols; c++) {
				const ch = grid[r][c];
				g.fillStyle = ch === '.' ? ((r + c) % 2 ? '#0c1219' : '#101821') : pal[GRID_CHARS.indexOf(ch)] || '#f0f';
				g.fillRect(c * cellPx, r * cellPx, cellPx, cellPx);
			}
		}
	}
	function paintAt(e, erase) {
		const r0 = gridCanvas.getBoundingClientRect();
		const c = Math.floor(((e.clientX - r0.left) * (gridCanvas.width / r0.width)) / cellPx);
		const r = Math.floor(((e.clientY - r0.top) * (gridCanvas.height / r0.height)) / cellPx);
		const line = st.grid?.[r];
		if (!line || c < 0 || c >= line.length) return;
		const ch = erase ? '.' : GRID_CHARS[st.paint];
		if (line[c] === ch) return;
		st.grid[r] = line.slice(0, c) + ch + line.slice(c + 1);
		st.edited = true;
		drawGrid();
	}
	let painting = null;
	gridCanvas.addEventListener('contextmenu', (e) => e.preventDefault());
	gridCanvas.addEventListener('pointerdown', (e) => {
		if (!st.grid) return;
		gridCanvas.setPointerCapture(e.pointerId);
		painting = e.button === 2 || e.shiftKey ? 'erase' : 'paint';
		paintAt(e, painting === 'erase');
	});
	gridCanvas.addEventListener('pointermove', (e) => painting && paintAt(e, painting === 'erase'));
	gridCanvas.addEventListener('pointerup', () => (painting = null));

	function drawSwatches() {
		const pal = st.result?.profile.palette || [];
		st.paint = Math.min(st.paint, Math.max(0, pal.length - 1));
		swatches.replaceChildren(
			...pal.map((hex, i) =>
				h('button', {
					class: `img-swatch ${i === st.paint ? 'on' : ''}`,
					style: { background: hex },
					title: `${GRID_CHARS[i]}: ${hex}`,
					onClick: () => {
						st.paint = i;
						drawSwatches();
					},
				}),
			),
		);
	}

	// ---- loading ----
	async function load(blob, name = 'image') {
		try {
			const { image, canvas } = await decodeImage(blob);
			st.image = image;
			st.source = canvas;
			st.fileName = name;
			st.crop = null;
			st.seed = null;
			const base = name.replace(/\.[^.]+$/, '').toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
			idInput.value = /^[a-z]/.test(base) ? base : `photo_${base || 'creature'}`;
			drawSource();
			analyse(0);
		} catch (err) {
			toast(`Could not read image: ${err.message}`, 'bad');
		}
	}
	fileInput.addEventListener('change', () => fileInput.files[0] && load(fileInput.files[0], fileInput.files[0].name));
	const onPaste = (e) => {
		const item = [...(e.clipboardData?.items || [])].find((i) => i.type.startsWith('image/'));
		if (item) {
			e.preventDefault();
			load(item.getAsFile(), 'pasted.png');
		}
	};
	document.addEventListener('paste', onPaste);

	const opt = (key, v, reanalyse = true) => {
		st.opts[key] = v;
		if (reanalyse) analyse();
	};
	const controls = h(
		'div',
		{ class: 'img-controls' },
		field('Height (units)', rangeInput({ value: st.opts.rows, min: 6, max: 64, step: 1, onChange: (v) => opt('rows', v) }), { hint: 'Model height in model units (16 = 1 block) = grid rows' }),
		field('Colours', rangeInput({ value: st.opts.colors, min: 1, max: 16, step: 1, onChange: (v) => opt('colors', v) })),
		field('Threshold', rangeInput({ value: st.opts.threshold, min: 1, max: 16, step: 0.5, onChange: (v) => opt('threshold', v) }), { hint: 'Lower = more of the picture counts as subject (background-noise units)' }),
		field('Smoothing', rangeInput({ value: st.opts.smooth, min: 0, max: 3, step: 0.5, onChange: (v) => opt('smooth', v) })),
		field('Depth / width', rangeInput({ value: st.opts.depthRatio, min: 0.2, max: 2, step: 0.05, onChange: (v) => opt('depthRatio', v, false) }), { hint: 'Slice depth relative to its width (1 = round)' }),
		checkbox({ checked: st.opts.ground, label: 'Stands on the crop bottom', hint: 'Follow a faint stalk / base down to the bottom edge of the crop', onChange: (v) => opt('ground', v) }),
		h('div', { class: 'img-help muted' }, 'Drag on the photo to crop the subject; click to mark a point on it. Paint the grid with the selected colour (right-click / Shift = erase).'),
	);

	const close = () => {
		document.removeEventListener('paste', onPaste);
		overlay.remove();
	};
	const create = () => {
		if (!st.result || !st.grid) return toast('Load an image first', 'bad');
		const id = idInput.value.trim();
		if (!/^[a-z][a-z0-9_]*$/.test(id)) return toast('id must be lower_snake_case', 'bad');
		const existing = definitions[id];
		if (existing && !confirm(`"${id}" already exists (${existing.template || existing.category}). Replace it in the editor? (nothing is written until Save Definition)`)) return;
		const grid = trimGrid(st.grid);
		if (!grid.length) return toast('The grid is empty', 'bad');
		const def = structuredClone(st.result.definition);
		def.id = id;
		def.name = nameInput.value.trim() || id.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
		def.params.photo.grid = grid;
		def.params.photo.height = grid.length;
		def.params.photo.depth_ratio = st.opts.depthRatio;
		def.params.photo.source.edited = st.edited;
		def.body = { height: grid.length, width: grid[0].length, length: grid[0].length };
		close();
		onCreate(def);
	};

	const overlay = h(
		'div',
		{
			class: 'modal-overlay',
			onClick: (e) => e.target === overlay && close(),
			onDragover: (e) => e.preventDefault(),
			onDrop: (e) => {
				e.preventDefault();
				const f = [...(e.dataTransfer?.files || [])].find((x) => x.type.startsWith('image/'));
				if (f) load(f, f.name);
			},
		},
		h(
			'div',
			{ class: 'modal img-modal' },
			h('div', { class: 'modal-title' }, 'Import Image → Photo model'),
			h(
				'div',
				{ class: 'img-body' },
				h('div', { class: 'img-col' }, h('div', { class: 'img-label' }, 'Photo', button('Open…', () => fileInput.click(), { kind: 'small' })), srcCanvas, fileInput),
				h('div', { class: 'img-col' }, h('div', { class: 'img-label' }, 'Subject'), maskCanvas),
				h('div', { class: 'img-col' }, h('div', { class: 'img-label' }, 'Grid'), gridCanvas, swatches, button('Reset edits', () => run(), { kind: 'small ghost' })),
				controls,
			),
			info,
			h(
				'div',
				{ class: 'row end' },
				field('id', idInput),
				nameInput,
				button('Cancel', close, { kind: 'ghost' }),
				button('Create Model', create, { kind: 'primary' }),
			),
		),
	);
	document.body.append(overlay);
	drawSource();
	return { load, close };
}
