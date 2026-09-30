// Colour utilities: hex <-> sRGB <-> OKLab/OKLCH, preset colour grading and pixel-art ramps.
// Ramps follow the usual Minecraft pixel-art rule: shading moves along a fixed ramp whose
// shadows drift toward a cool hue and highlights toward the preset's highlight hue.

export function hexToRgb(hex) {
	let h = String(hex || '#000000').trim().replace(/^#/, '');
	if (h.length === 3) h = h.split('').map((c) => c + c).join('');
	const n = parseInt(h.slice(0, 6), 16);
	if (Number.isNaN(n)) return [0, 0, 0];
	return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

export function rgbToHex(rgb) {
	return '#' + rgb.slice(0, 3).map((v) => Math.round(Math.min(255, Math.max(0, v))).toString(16).padStart(2, '0')).join('');
}

const toLinear = (c) => {
	c /= 255;
	return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
};
const toSrgb = (c) => {
	c = Math.max(0, c);
	const v = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
	return Math.min(255, Math.max(0, v * 255));
};

export function rgbToOklab([r, g, b]) {
	const lr = toLinear(r), lg = toLinear(g), lb = toLinear(b);
	const l = Math.cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb);
	const m = Math.cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb);
	const s = Math.cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb);
	return [
		0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
		1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
		0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
	];
}

export function oklabToRgb([L, a, b]) {
	const l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3;
	const m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3;
	const s = (L - 0.0894841775 * a - 1.291485548 * b) ** 3;
	return [
		toSrgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
		toSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
		toSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s),
	];
}

export function rgbToOklch(rgb) {
	const [L, a, b] = rgbToOklab(rgb);
	const C = Math.hypot(a, b);
	let h = (Math.atan2(b, a) * 180) / Math.PI;
	if (h < 0) h += 360;
	return [L, C, h];
}

export function oklchToRgb([L, C, h]) {
	const r = (h * Math.PI) / 180;
	return oklabToRgb([L, C * Math.cos(r), C * Math.sin(r)]);
}

export function mixRgb(a, b, t) {
	return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

/** Rotates hue `h` toward `target` by at most `amount` degrees along the shortest path. */
export function hueToward(h, target, amount) {
	let d = ((target - h + 540) % 360) - 180;
	const step = Math.sign(d) * Math.min(Math.abs(d), amount);
	return (h + step + 360) % 360;
}

/**
 * Applies the preset colour grade (deep sea = desaturated and slightly darker) and the
 * individual's variation (hue / lightness offsets).
 */
export function gradeColor(rgb, { saturation = 1, brightness = 1, hue = 0, lightness = 0, chroma = 0 } = {}) {
	let [L, C, h] = rgbToOklch(rgb);
	C = Math.max(0, C * saturation + chroma);
	L = Math.min(0.98, Math.max(0.02, L * brightness + lightness));
	h = (h + hue + 360) % 360;
	return oklchToRgb([L, C, h]);
}

/**
 * Builds an odd-length ramp (dark -> light) whose middle entry is `base`.
 * @param {number[]} base rgb
 * @param {{steps?:number, contrast?:number, hueShift?:number, shadowHue?:number, highlightHue?:number}} style
 */
export function makeRamp(base, style = {}) {
	const steps = Math.max(3, (style.steps ?? 7) | 1);
	const contrast = style.contrast ?? 1;
	const hueShift = style.hueShift ?? 1;
	const shadowHue = style.shadowHue ?? 265;
	const highlightHue = style.highlightHue ?? 195;
	const mid = (steps - 1) / 2;
	const [L, C, h] = rgbToOklch(base);
	const dL = 0.052 * contrast;
	const ramp = [];
	for (let i = 0; i < steps; i++) {
		const k = i - mid;
		const t = Math.abs(k) / mid;
		const Li = Math.min(0.97, Math.max(0.035, L + k * dL));
		const Ci = Math.max(0, C * (k < 0 ? 1 + 0.06 * -k : 1 - 0.1 * k));
		const hi = k < 0 ? hueToward(h, shadowHue, 16 * hueShift * t) : k > 0 ? hueToward(h, highlightHue, 12 * hueShift * t) : h;
		ramp.push(k === 0 ? base.slice(0, 3) : oklchToRgb([Li, Ci, hi]));
	}
	return ramp;
}

/** Picks a ramp entry for a shading value v (0 = base, ±1 ≈ one ramp step per unit). */
export function rampColor(ramp, v) {
	const mid = (ramp.length - 1) / 2;
	const idx = Math.round(mid + v);
	return ramp[Math.min(ramp.length - 1, Math.max(0, idx))];
}
