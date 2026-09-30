// Photo profile: segmented subject -> model-unit grid with a small colour palette, and the
// creature definition for the "photo" template built from it.
//
// The grid is stored in the definition as text (one string per row, top row first; each
// character is a palette index 0-9a-f, "." = empty), so the model stays deterministic and
// diffable and the source image is never needed again.

import { oklabToRgb, rgbToHex, rgbToOklab } from '../texture/palette.js';
import { erode, segmentSubject } from './segment.js';

export const GRID_CHARS = '0123456789abcdef';
export const MAX_GRID = 64;

/**
 * Downsamples the segmented subject into `rows` model-unit rows.
 * @param {object} seg result of segmentSubject
 * @param {{rows?:number, colors?:number, coverage?:number}} opts
 */
export function buildGrid(seg, { rows = 24, colors = 8, coverage = 0.5 } = {}) {
	const { width: W, height: H, mask, rgba } = seg;
	let x0 = W, x1 = -1, y0 = H, y1 = -1;
	for (let y = 0; y < H; y++) {
		for (let x = 0; x < W; x++) {
			if (!mask[y * W + x]) continue;
			if (x < x0) x0 = x;
			if (x > x1) x1 = x;
			if (y < y0) y0 = y;
			if (y > y1) y1 = y;
		}
	}
	if (x1 < 0) throw new Error('No subject found: adjust the crop or lower the threshold');
	const bw = x1 - x0 + 1, bh = y1 - y0 + 1;
	rows = Math.max(2, Math.min(MAX_GRID, Math.round(rows)));
	let cell = bh / rows;
	let cols = Math.ceil(bw / cell - 0.25);
	if (cols > MAX_GRID) {
		// Very wide subject: keep the grid inside the limit, rows shrink with it
		cell = bw / MAX_GRID;
		cols = MAX_GRID;
		rows = Math.max(2, Math.round(bh / cell));
	}
	cols = Math.max(1, cols);
	const gx0 = x0 + bw / 2 - (cols * cell) / 2;
	const gy0 = y0;
	// Colours come from an eroded mask so the rim does not pick up background
	const inner = erode(mask, W, H, Math.max(1, Math.round(cell / 4)));
	const n = Math.max(2, Math.min(6, Math.ceil(cell)));
	const cells = [];
	for (let r = 0; r < rows; r++) {
		const line = [];
		for (let c = 0; c < cols; c++) {
			let hit = 0, total = 0;
			const sum = [0, 0, 0], sumAll = [0, 0, 0];
			let inHit = 0;
			for (let j = 0; j < n; j++) {
				for (let i = 0; i < n; i++) {
					const px = Math.floor(gx0 + (c + (i + 0.5) / n) * cell);
					const py = Math.floor(gy0 + (r + (j + 0.5) / n) * cell);
					total++;
					if (px < 0 || py < 0 || px >= W || py >= H) continue;
					const k = py * W + px;
					if (!mask[k]) continue;
					hit++;
					for (let ch = 0; ch < 3; ch++) sumAll[ch] += rgba[k * 4 + ch];
					if (inner[k]) {
						inHit++;
						for (let ch = 0; ch < 3; ch++) sum[ch] += rgba[k * 4 + ch];
					}
				}
			}
			if (hit / total < coverage) {
				line.push(null);
				continue;
			}
			line.push(inHit ? sum.map((v) => v / inHit) : sumAll.map((v) => v / hit));
		}
		cells.push(line);
	}
	const { palette, index } = quantize(cells.flat().filter(Boolean), Math.max(1, Math.min(16, colors)));
	let p = 0;
	const grid = cells.map((line) => line.map((cellRgb) => (cellRgb ? GRID_CHARS[index[p++]] : '.')).join(''));
	return { grid: trimGrid(grid), palette, cellPx: cell * seg.scale, bbox: { x: x0, y: y0, w: bw, h: bh } };
}

/** Removes empty border rows / columns. */
export function trimGrid(grid) {
	let rows = grid.slice();
	while (rows.length && !/[^.]/.test(rows[0])) rows.shift();
	while (rows.length && !/[^.]/.test(rows[rows.length - 1])) rows.pop();
	if (!rows.length) return rows;
	let left = Infinity, right = -1;
	for (const r of rows) {
		const m = r.match(/[^.]/);
		if (!m) continue;
		left = Math.min(left, m.index);
		right = Math.max(right, r.length - 1 - r.split('').reverse().findIndex((ch) => ch !== '.'));
	}
	return rows.map((r) => r.slice(left, right + 1));
}

/** Deterministic k-means in Oklab (farthest-point init). Palette sorted dark -> light. */
export function quantize(colors, k) {
	if (!colors.length) return { palette: ['#808080'], index: [] };
	const pts = colors.map((c) => rgbToOklab(c));
	const mean = [0, 1, 2].map((a) => pts.reduce((s, p) => s + p[a], 0) / pts.length);
	const d2 = (p, q) => (p[0] - q[0]) ** 2 + (p[1] - q[1]) ** 2 + (p[2] - q[2]) ** 2;
	let centers = [pts.reduce((best, p) => (d2(p, mean) < d2(best, mean) ? p : best))];
	while (centers.length < k) {
		let far = null, farD = 0;
		for (const p of pts) {
			const d = Math.min(...centers.map((c) => d2(p, c)));
			if (d > farD) {
				farD = d;
				far = p;
			}
		}
		if (!far || farD < 1e-4) break;
		centers.push(far);
	}
	let index = new Array(pts.length).fill(0);
	for (let iter = 0; iter < 12; iter++) {
		index = pts.map((p) => centers.reduce((bi, c, i) => (d2(p, c) < d2(p, centers[bi]) ? i : bi), 0));
		const next = centers.map(() => [0, 0, 0, 0]);
		pts.forEach((p, i) => {
			const s = next[index[i]];
			s[0] += p[0];
			s[1] += p[1];
			s[2] += p[2];
			s[3]++;
		});
		centers = centers.map((c, i) => (next[i][3] ? [next[i][0] / next[i][3], next[i][1] / next[i][3], next[i][2] / next[i][3]] : c));
	}
	// Drop unused clusters, sort by lightness
	const used = [...new Set(index)].sort((a, b) => centers[a][0] - centers[b][0]);
	const remap = new Map(used.map((c, i) => [c, i]));
	return { palette: used.map((c) => rgbToHex(oklabToRgb(centers[c]))), index: index.map((i) => remap.get(i)) };
}

/**
 * Image -> creature definition for the photo template.
 * @param {{width,height,data}} img RGBA
 * @param {object} opts {id, name, crop, threshold, seed, rows (model height in units), colors,
 *                       depthRatio, smooth, border, source}
 */
export function definitionFromImage(img, opts = {}) {
	const seg = segmentSubject(img, opts);
	const rows = Math.round(opts.rows || opts.height || 24);
	const profile = buildGrid(seg, { rows, colors: opts.colors || 8 });
	const id = opts.id || 'photo_creature';
	const palette = Object.fromEntries(profile.palette.map((hex, i) => [`p${i}`, hex]));
	// Flat-colour fallback (Generate Texture off): the most common grid colour
	const counts = new Map();
	for (const ch of profile.grid.join('')) if (ch !== '.') counts.set(ch, (counts.get(ch) || 0) + 1);
	const common = [...counts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] || '0';
	palette.body = profile.palette[GRID_CHARS.indexOf(common)] || profile.palette[0];
	const def = {
		id,
		name: opts.name || id.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()),
		template: 'photo',
		category: 'photo',
		body: { height: profile.grid.length, width: profile.grid[0]?.length || 1, length: profile.grid[0]?.length || 1 },
		params: {
			photo: {
				mode: 'lathe',
				height: profile.grid.length,
				width_scale: 1,
				depth_ratio: opts.depthRatio ?? 1,
				segments: 0,
				round: true,
				grid: profile.grid,
				source: {
					file: opts.source || null,
					crop: seg.crop,
					threshold: seg.threshold,
					rows,
					colors: profile.palette.length,
				},
			},
		},
		palette,
		texture: { pattern: 'plain' },
		glow: [],
		animation: { speed: 1, amplitude: 1 },
		variation: { body: 0.06, height: 0.08, hue: 4, lightness: 0.03 },
	};
	return { definition: def, segmentation: seg, profile };
}

/** Debug image: source crop dimmed outside the mask, subject outline in red. */
export function maskPreview(seg) {
	const { width: W, height: H, mask, rgba } = seg;
	const data = new Uint8ClampedArray(W * H * 4);
	for (let i = 0; i < W * H; i++) {
		const x = i % W, y = (i / W) | 0;
		const m = mask[i];
		const edge = m && ((x > 0 && !mask[i - 1]) || (x < W - 1 && !mask[i + 1]) || (y > 0 && !mask[i - W]) || (y < H - 1 && !mask[i + W]));
		const f = m ? 1 : 0.3;
		data[i * 4] = edge ? 255 : rgba[i * 4] * f;
		data[i * 4 + 1] = edge ? 40 : rgba[i * 4 + 1] * f;
		data[i * 4 + 2] = edge ? 40 : rgba[i * 4 + 2] * f;
		data[i * 4 + 3] = 255;
	}
	return { width: W, height: H, data };
}
