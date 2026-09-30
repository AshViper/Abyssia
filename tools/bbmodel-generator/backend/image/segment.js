// Subject segmentation for photo-based models.
//
// The subject is separated from its surroundings inside a crop rectangle:
//   crop + downscale -> blur -> per-row background model (median colour and noise of the left /
//   right crop border, so dark water above and sand below are both "background") -> Oklab
//   distance in noise units -> threshold -> open -> component nearest the seed -> close -> fill holes.
// Input and output are plain arrays, so this runs in the browser and in Node alike.

import { rgbToOklab } from '../texture/palette.js';

/**
 * Crops and box-filters an RGBA image so its longer side is at most `maxSide` pixels.
 * @param {{width:number,height:number,data:ArrayLike<number>}} img
 * @param {{x:number,y:number,w:number,h:number}|null} crop in source pixels (null = whole image)
 */
export function cropImage(img, crop, maxSide = 384) {
	const c = normalizeCrop(img, crop);
	const k = Math.max(1, Math.max(c.w, c.h) / maxSide);
	const width = Math.max(1, Math.round(c.w / k));
	const height = Math.max(1, Math.round(c.h / k));
	const data = new Uint8ClampedArray(width * height * 4);
	for (let y = 0; y < height; y++) {
		const sy0 = c.y + y * k, sy1 = Math.min(c.y + c.h, sy0 + k);
		for (let x = 0; x < width; x++) {
			const sx0 = c.x + x * k, sx1 = Math.min(c.x + c.w, sx0 + k);
			let r = 0, g = 0, b = 0, a = 0, n = 0;
			for (let sy = Math.floor(sy0); sy < Math.max(Math.floor(sy0) + 1, Math.ceil(sy1)); sy++) {
				for (let sx = Math.floor(sx0); sx < Math.max(Math.floor(sx0) + 1, Math.ceil(sx1)); sx++) {
					const i = (Math.min(img.height - 1, sy) * img.width + Math.min(img.width - 1, sx)) * 4;
					r += img.data[i];
					g += img.data[i + 1];
					b += img.data[i + 2];
					a += img.data[i + 3];
					n++;
				}
			}
			const o = (y * width + x) * 4;
			data[o] = r / n;
			data[o + 1] = g / n;
			data[o + 2] = b / n;
			data[o + 3] = a / n;
		}
	}
	return { width, height, data, scale: k, crop: c };
}

export function normalizeCrop(img, crop) {
	if (!crop) return { x: 0, y: 0, w: img.width, h: img.height };
	const x = clampInt(crop.x, 0, img.width - 1);
	const y = clampInt(crop.y, 0, img.height - 1);
	return { x, y, w: clampInt(crop.w, 1, img.width - x), h: clampInt(crop.h, 1, img.height - y) };
}

const clampInt = (v, lo, hi) => Math.min(hi, Math.max(lo, Math.round(Number(v) || 0)));

/** Default subject threshold in background-noise units (score = colour distance / row noise). */
export const DEFAULT_THRESHOLD = 4;

/**
 * Segments the subject.
 * @param {object} img RGBA image {width, height, data}
 * @param {object} [opts]
 * @param {{x,y,w,h}} [opts.crop]        subject rectangle in source pixels
 * @param {number|null} [opts.threshold] score above which a pixel is subject (noise units, default 4)
 * @param {{x:number,y:number}} [opts.seed] point on the subject, 0..1 inside the crop (default centre)
 * @param {number} [opts.border]         background band width as a fraction of the crop width
 * @param {number} [opts.smooth]         morphology radius multiplier (0 = none)
 * @param {boolean} [opts.ground]        the subject stands on the crop's bottom edge: follow its base
 *                                       down through low-contrast ground (translucent stalk on sand)
 * @param {number} [opts.maxSide]        working resolution
 */
export function segmentSubject(img, opts = {}) {
	const work = cropImage(img, opts.crop || null, opts.maxSide || 384);
	const { width: W, height: H, data } = work;
	const raw = new Float32Array(W * H * 3);
	for (let i = 0; i < W * H; i++) {
		const [L, a, b] = rgbToOklab([data[i * 4], data[i * 4 + 1], data[i * 4 + 2]]);
		raw[i * 3] = L * 100;
		raw[i * 3 + 1] = a * 100;
		raw[i * 3 + 2] = b * 100;
	}
	// Blur first: grainy backgrounds (sand) average out, faint but systematic differences
	// (a translucent stalk in front of the sand) remain.
	const lab = boxBlur3(raw, W, H, Math.max(1, Math.round(Math.min(W, H) / 100)));
	// Per-row background: median + robust spread of the left and right border bands (7-row window).
	// A band much noisier than the other one contains another object and is ignored.
	const band = Math.max(3, Math.round(W * (opts.border ?? 0.05)));
	const score = new Float32Array(W * H);
	const med = (arr) => {
		arr.sort((p, q) => p - q);
		return arr[arr.length >> 1];
	};
	for (let y = 0; y < H; y++) {
		let sides = [0, W - band].map((x0) => {
			const ch = [[], [], []];
			for (let yy = Math.max(0, y - 3); yy <= Math.min(H - 1, y + 3); yy++) {
				for (let x = x0; x < x0 + band; x++) for (let c = 0; c < 3; c++) ch[c].push(lab[(yy * W + x) * 3 + c]);
			}
			const m = ch.map((v) => med(v.slice()));
			const d = ch[0].map((_, i) => Math.hypot(ch[0][i] - m[0], ch[1][i] - m[1], ch[2][i] - m[2]));
			return { m, sigma: Math.max(0.6, 1.4826 * med(d)) };
		});
		const low = Math.min(sides[0].sigma, sides[1].sigma);
		sides = sides.filter((s) => s.sigma <= low * 3);
		for (let x = 0; x < W; x++) {
			const i = (y * W + x) * 3;
			let d = Infinity;
			for (const s of sides) d = Math.min(d, Math.hypot(lab[i] - s.m[0], lab[i + 1] - s.m[1], lab[i + 2] - s.m[2]) / s.sigma);
			score[y * W + x] = d;
		}
	}
	const threshold = opts.threshold == null || opts.threshold === 'auto' ? DEFAULT_THRESHOLD : Number(opts.threshold);
	let mask = new Uint8Array(W * H);
	for (let i = 0; i < mask.length; i++) mask[i] = score[i] > threshold ? 1 : 0;

	const r = Math.round(Math.max(1, Math.min(W, H) / 160) * (opts.smooth ?? 1));
	if (r > 0) mask = dilate(erode(mask, W, H, r), W, H, r);
	const seed = { x: (opts.seed?.x ?? 0.5) * (W - 1), y: (opts.seed?.y ?? 0.5) * (H - 1) };
	mask = pickComponent(mask, W, H, seed);
	if (opts.ground) extendToGround(mask, score, W, H, threshold);
	if (r > 0) mask = erode(dilate(mask, W, H, r * 2), W, H, r * 2);
	mask = fillHoles(mask, W, H);
	const area = mask.reduce((a, v) => a + v, 0);
	return { width: W, height: H, rgba: data, mask, score, threshold, scale: work.scale, crop: work.crop, area };
}

/**
 * Follows the lowest part of the subject down to the bottom of the crop while the rows below
 * still show weak evidence (score > threshold / 2 inside the current span). Short gaps are
 * bridged with the previous span; the span may drift and change width a little per row.
 */
function extendToGround(mask, score, W, H, threshold) {
	let last = -1;
	for (let y = H - 1; y >= 0 && last < 0; y--) for (let x = 0; x < W; x++) if (mask[y * W + x]) (last = y);
	if (last < 0 || last >= H - 1) return;
	// Reference span: median extent of the lowest few rows
	const spans = [];
	for (let y = last; y >= Math.max(0, last - 6); y--) {
		let l = -1, rr = -1;
		for (let x = 0; x < W; x++) if (mask[y * W + x]) (l < 0 && (l = x), (rr = x));
		if (l >= 0) spans.push([l, rr]);
	}
	const mid = (i) => spans.map((s) => s[i]).sort((p, q) => p - q)[spans.length >> 1];
	let L = mid(0), R = mid(1);
	const w0 = R - L + 1;
	const weak = threshold / 2;
	const maxGap = Math.max(3, Math.round(H * 0.08));
	let gap = 0;
	const filled = [];
	for (let y = last + 1; y < H; y++) {
		const w = R - L + 1;
		const m = Math.max(2, Math.round(w * 0.2));
		let hits = 0, lo = -1, hi = -1;
		for (let x = Math.max(0, L - m); x <= Math.min(W - 1, R + m); x++) {
			let s = 0;
			for (let yy = Math.max(0, y - 3); yy <= Math.min(H - 1, y + 3); yy++) s += score[yy * W + x];
			if (s / (Math.min(H - 1, y + 3) - Math.max(0, y - 3) + 1) > weak) {
				if (x >= L && x <= R) hits++;
				if (lo < 0) lo = x;
				hi = x;
			}
		}
		if (hits / w >= 0.3) {
			gap = 0;
			if (lo >= 0 && hi - lo + 1 >= w * 0.6 && hi - lo + 1 <= w * 1.4) {
				// Drift toward the evidence, but keep the width near the reference (no runaway into the horizon)
				let nl = L * 0.7 + lo * 0.3, nr = R * 0.7 + hi * 0.3;
				const c = (nl + nr) / 2, nw = Math.min(w0 * 1.25, Math.max(w0 * 0.6, nr - nl + 1));
				L = Math.round(c - (nw - 1) / 2);
				R = Math.round(c + (nw - 1) / 2);
			}
		} else if (++gap > maxGap) break;
		filled.push([y, L, R]);
	}
	// Drop a trailing run of bridged rows that never found evidence again
	while (gap > 0 && filled.length && gap--) filled.pop();
	for (const [y, l, rr] of filled) for (let x = l; x <= rr; x++) mask[y * W + x] = 1;
}

/** Separable box blur of an interleaved 3-channel float image. */
function boxBlur3(src, W, H, r) {
	const tmp = new Float32Array(src.length);
	const out = new Float32Array(src.length);
	for (let y = 0; y < H; y++) {
		for (let x = 0; x < W; x++) {
			const a = Math.max(0, x - r), b = Math.min(W - 1, x + r);
			for (let c = 0; c < 3; c++) {
				let s = 0;
				for (let k = a; k <= b; k++) s += src[(y * W + k) * 3 + c];
				tmp[(y * W + x) * 3 + c] = s / (b - a + 1);
			}
		}
	}
	for (let x = 0; x < W; x++) {
		for (let y = 0; y < H; y++) {
			const a = Math.max(0, y - r), b = Math.min(H - 1, y + r);
			for (let c = 0; c < 3; c++) {
				let s = 0;
				for (let k = a; k <= b; k++) s += tmp[(k * W + x) * 3 + c];
				out[(y * W + x) * 3 + c] = s / (b - a + 1);
			}
		}
	}
	return out;
}

function morph(mask, W, H, r, isMax) {
	// Separable square min / max filter
	const tmp = new Uint8Array(W * H);
	const out = new Uint8Array(W * H);
	for (let y = 0; y < H; y++) {
		for (let x = 0; x < W; x++) {
			let v = isMax ? 0 : 1;
			for (let k = Math.max(0, x - r); k <= Math.min(W - 1, x + r); k++) {
				const m = mask[y * W + k];
				if (isMax ? m : !m) {
					v = isMax ? 1 : 0;
					break;
				}
			}
			tmp[y * W + x] = v;
		}
	}
	for (let x = 0; x < W; x++) {
		for (let y = 0; y < H; y++) {
			let v = isMax ? 0 : 1;
			for (let k = Math.max(0, y - r); k <= Math.min(H - 1, y + r); k++) {
				const m = tmp[k * W + x];
				if (isMax ? m : !m) {
					v = isMax ? 1 : 0;
					break;
				}
			}
			out[y * W + x] = v;
		}
	}
	return out;
}

export const erode = (mask, W, H, r) => morph(mask, W, H, r, false);
export const dilate = (mask, W, H, r) => morph(mask, W, H, r, true);

/** 4-connected components; returns the one with the best area / distance-to-seed score. */
function pickComponent(mask, W, H, seed) {
	const label = new Int32Array(W * H).fill(-1);
	const comps = [];
	const stack = [];
	for (let start = 0; start < mask.length; start++) {
		if (!mask[start] || label[start] >= 0) continue;
		const id = comps.length;
		let area = 0, sx = 0, sy = 0, hit = false;
		label[start] = id;
		stack.push(start);
		while (stack.length) {
			const i = stack.pop();
			const x = i % W, y = (i / W) | 0;
			area++;
			sx += x;
			sy += y;
			if (Math.abs(x - seed.x) < 1 && Math.abs(y - seed.y) < 1) hit = true;
			for (const j of [x > 0 ? i - 1 : -1, x < W - 1 ? i + 1 : -1, y > 0 ? i - W : -1, y < H - 1 ? i + W : -1]) {
				if (j >= 0 && mask[j] && label[j] < 0) {
					label[j] = id;
					stack.push(j);
				}
			}
		}
		comps.push({ id, area, cx: sx / area, cy: sy / area, hit });
	}
	if (!comps.length) return new Uint8Array(W * H);
	const diag = Math.hypot(W, H);
	const score = (c) => (c.hit ? Infinity : c.area / (1 + (4 * Math.hypot(c.cx - seed.x, c.cy - seed.y)) / diag));
	const best = comps.reduce((a, c) => (score(c) > score(a) ? c : a));
	const out = new Uint8Array(W * H);
	for (let i = 0; i < out.length; i++) out[i] = label[i] === best.id ? 1 : 0;
	return out;
}

/** Fills enclosed background regions (translucent bodies show the dark water through them). */
function fillHoles(mask, W, H) {
	const outside = new Uint8Array(W * H);
	const stack = [];
	const push = (i) => {
		if (!mask[i] && !outside[i]) {
			outside[i] = 1;
			stack.push(i);
		}
	};
	for (let x = 0; x < W; x++) push(x), push((H - 1) * W + x);
	for (let y = 0; y < H; y++) push(y * W), push(y * W + W - 1);
	while (stack.length) {
		const i = stack.pop();
		const x = i % W, y = (i / W) | 0;
		if (x > 0) push(i - 1);
		if (x < W - 1) push(i + 1);
		if (y > 0) push(i - W);
		if (y < H - 1) push(i + W);
	}
	const out = new Uint8Array(W * H);
	for (let i = 0; i < out.length; i++) out[i] = outside[i] ? 0 : 1;
	return out;
}
