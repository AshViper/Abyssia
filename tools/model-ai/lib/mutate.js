// Edit samples: spec + natural-language instruction -> edited spec (the target patch is the diff).
// Each op returns {en, ja, spec} or null when it does not apply to this spec.

import { clone, descendants } from './spec.js';
import { colorName, COLORS, partLabel, PATTERN_WORDS } from './vocab.js';

const AXIS_INDEX = { x: 0, '-x': 0, y: 1, '-y': 1, z: 2, '-z': 2 };
const half = (v, min = 1) => Math.max(min, Math.round(v * 2) / 2);
const counter = (p) => (p.shape === 'plane' ? '枚' : p.shape === 'cylinder' || p.shape === 'cone' ? '本' : '個');
const plural = (w) => (/(s|sh|ch)$/.test(w) ? `${w}es` : /[^aeiou]y$/.test(w) ? `${w.slice(0, -1)}ies` : `${w}s`);

function namedColor(rng, avoidHex) {
	const avoid = avoidHex ? colorName(avoidHex).en : null;
	const c = rng.pick(COLORS.filter((x) => x[0] !== avoid));
	return { en: c[0], ja: c[1], hex: c[2] };
}

const isRoot = (p) => !p.parent;
const pickPart = (rng, spec, filter) => {
	const list = spec.parts.filter(filter);
	return list.length ? rng.pick(list) : null;
};

const OPS = {
	resize(spec, rng) {
		const p = pickPart(rng, spec, () => true);
		if (!p) return null;
		const big = rng.chance(0.5);
		const f = big ? rng.float(1.4, 1.9) : rng.float(0.45, 0.7);
		const s = clone(spec);
		const q = s.parts.find((x) => x.name === p.name);
		if (typeof q.size === 'number') q.size = half(q.size * f);
		else q.size = q.size.map((v) => (v === 0 ? 0 : half(v * f)));
		if (JSON.stringify(q.size) === JSON.stringify(p.size)) return null;
		const en = partLabel(p.name, 'en'), ja = partLabel(p.name, 'ja');
		const much = big ? f > 1.7 : f < 0.55;
		return big
			? { en: rng.pick([`make the ${en} ${much ? 'much ' : ''}bigger`, `enlarge the ${en}`, `bigger ${en}`]), ja: rng.pick([`${ja}を${much ? 'もっと' : ''}大きくして`, `${ja}を大きく`]), spec: s }
			: { en: rng.pick([`make the ${en} ${much ? 'much ' : ''}smaller`, `shrink the ${en}`, `smaller ${en}`]), ja: rng.pick([`${ja}を${much ? 'もっと' : ''}小さくして`, `${ja}を小さく`]), spec: s };
	},
	length(spec, rng) {
		const p = pickPart(rng, spec, (q) => Array.isArray(q.size) && q.shape !== 'eye');
		if (!p) return null;
		const ai = AXIS_INDEX[p.axis || (/tentacle|arm|tendril|oral|filament/.test(p.name) ? '-y' : 'y')];
		if (!p.size[ai]) return null;
		const longer = rng.chance(0.55);
		const s = clone(spec);
		const q = s.parts.find((x) => x.name === p.name);
		q.size[ai] = half(p.size[ai] * (longer ? rng.float(1.5, 2.2) : rng.float(0.4, 0.65)));
		if (q.size[ai] === p.size[ai]) return null;
		const en = partLabel(p.name, 'en'), ja = partLabel(p.name, 'ja');
		return longer ? { en: rng.pick([`make the ${en} longer`, `lengthen the ${en}`]), ja: rng.pick([`${ja}を長くして`, `${ja}をもっと長く`]), spec: s } : { en: rng.pick([`make the ${en} shorter`, `shorten the ${en}`]), ja: rng.pick([`${ja}を短くして`, `${ja}を短く`]), spec: s };
	},
	recolorPart(spec, rng) {
		const p = pickPart(rng, spec, (q) => q.shape !== 'eye');
		if (!p) return null;
		const c = namedColor(rng, p.color);
		const s = clone(spec);
		s.parts.find((x) => x.name === p.name).color = c.hex;
		const en = partLabel(p.name, 'en'), ja = partLabel(p.name, 'ja');
		return { en: rng.pick([`make the ${en} ${c.en}`, `paint the ${en} ${c.en}`, `change the ${en} colour to ${c.en}`]), ja: rng.pick([`${ja}を${c.ja}にして`, `${ja}の色を${c.ja}に変えて`]), spec: s };
	},
	recolorAll(spec, rng) {
		const root = spec.parts.find(isRoot);
		if (!root?.color) return null;
		const c = namedColor(rng, root.color);
		const s = clone(spec);
		for (const q of s.parts) if (q.color === root.color) q.color = c.hex;
		return { en: rng.pick([`make it ${c.en}`, `change the body colour to ${c.en}`, `recolour the whole thing ${c.en}`]), ja: rng.pick([`全体を${c.ja}にして`, `体の色を${c.ja}に変えて`, `${c.ja}色にして`.replace('色色', '色')]), spec: s };
	},
	glow(spec, rng) {
		const lit = spec.parts.filter((q) => q.glow);
		if (lit.length && rng.chance(0.35)) {
			const p = rng.pick(lit);
			const s = clone(spec);
			const q = s.parts.find((x) => x.name === p.name);
			delete q.glow;
			delete q.glow_color;
			delete q.glow_pattern;
			const en = partLabel(p.name, 'en'), ja = partLabel(p.name, 'ja');
			return { en: rng.pick([`stop the ${en} glowing`, `remove the glow from the ${en}`]), ja: rng.pick([`${ja}の発光をなくして`, `${ja}を光らせないで`]), spec: s };
		}
		const p = pickPart(rng, spec, (q) => !q.glow && q.shape !== 'eye');
		if (!p) return null;
		const c = rng.pick([['cyan', '水色'], ['light blue', '薄い青'], ['lime', '黄緑'], ['magenta', '赤紫'], ['yellow', '黄色'], ['blue', '青'], ['red', '赤']]);
		const hex = COLORS.find((x) => x[0] === c[0])[2];
		const s = clone(spec);
		Object.assign(s.parts.find((x) => x.name === p.name), { glow: 1, glow_color: hex });
		const en = partLabel(p.name, 'en'), ja = partLabel(p.name, 'ja');
		return { en: rng.pick([`make the ${en} glow ${c[0]}`, `add a ${c[0]} glow to the ${en}`]), ja: rng.pick([`${ja}を${c[1]}に光らせて`, `${ja}が${c[1]}に光るようにして`]), spec: s };
	},
	count(spec, rng) {
		const p = pickPart(rng, spec, (q) => (q.count ?? 1) >= 2);
		if (!p) return null;
		const mult = p.mirror ? 2 : 1;
		const opts = [2, 3, 4, 5, 6, 8, 10, 12, 16].filter((n) => n !== p.count && n * mult <= 32);
		const n = rng.pick(opts);
		const s = clone(spec);
		s.parts.find((x) => x.name === p.name).count = n;
		const en = partLabel(p.name, 'en'), ja = partLabel(p.name, 'ja');
		const total = n * mult;
		return { en: rng.pick([`give it ${total} ${plural(en)}`, `${n > p.count ? 'more' : 'fewer'} ${plural(en)}: ${total} in total`]), ja: rng.pick([`${ja}を${total}${counter(p)}にして`, `${ja}の数を${total}${counter(p)}に`]), spec: s };
	},
	remove(spec, rng) {
		const p = pickPart(rng, spec, (q) => !isRoot(q) && q.shape !== 'eye' && descendants(spec, q.name).length <= 2);
		if (!p) return null;
		const gone = new Set([p.name, ...descendants(spec, p.name)]);
		const s = clone(spec);
		s.parts = s.parts.filter((q) => !gone.has(q.name));
		const en = partLabel(p.name, 'en'), ja = partLabel(p.name, 'ja');
		return { en: rng.pick([`remove the ${en}`, `delete the ${en}`, `no ${en}`]), ja: rng.pick([`${ja}を取って`, `${ja}をなくして`, `${ja}はいらない`]), spec: s };
	},
	add(spec, rng) {
		const root = spec.parts.find(isRoot);
		const head = spec.parts.find((q) => q.name === 'head') || root;
		const has = (n) => spec.parts.some((q) => q.name === n);
		const col = root.color || '#808080';
		const horizontal = spec.orientation === 'horizontal';
		const lib = [
			!spec.parts.some((q) => q.shape === 'eye') && { en: 'add eyes', ja: '目を付けて', part: { name: 'eye', shape: 'eye', parent: head.name, at: horizontal ? [1, 0.65, 0.2] : [0.7, 0.65, 0], size: 1, mirror: true } },
			!has('horn') && { en: rng.pick(['add horns', 'give it a pair of horns']), ja: rng.pick(['角を付けて', '2本の角を生やして']), part: { name: 'horn', shape: 'cone', parent: head.name, at: [0.75, 1, 0.4], axis: 'y', size: [2, rng.int(3, 6), 2], tip: 0.2, mirror: true, color: COLORS.find((c) => c[0] === 'beige')[2] } },
			!has('spike') && { en: rng.pick(['add spikes on its back', 'put a row of spikes on top']), ja: rng.pick(['背中にトゲを付けて', '上にトゲを並べて']), part: { name: 'spike', shape: 'cone', parent: root.name, at: [0.5, 1, 0.5], axis: 'y', size: [2, rng.int(2, 4), 2], tip: 0.2, count: rng.int(3, 5), arrange: 'row', spacing: horizontal ? [0, 0, 3] : [3, 0, 0], color: col } },
			!has('tail') && horizontal && { en: rng.pick(['add a long tail', 'give it a tail']), ja: rng.pick(['長い尾を付けて', '尻尾を付けて']), part: { name: 'tail', shape: 'cylinder', parent: root.name, at: 'back', axis: 'z', size: [2, 2, rng.int(6, 14)], taper: 0.5, segments: 3, color: col } },
			!has('antenna') && { en: rng.pick(['add antennae', 'give it two antennae']), ja: rng.pick(['触角を付けて', '2本の触角を生やして']), part: { name: 'antenna', shape: 'cylinder', parent: head.name, at: [0.7, 1, 0.2], axis: 'y', rotate: [-20, 0, -15], size: [1, rng.int(4, 8), 1], segments: 2, curl: -15, mirror: true, color: col } },
			!has('wing') && { en: rng.pick(['add wings', 'give it a pair of wings']), ja: rng.pick(['翼を付けて', '羽を生やして']), part: { name: 'wing', shape: 'plane', parent: root.name, at: [1, 0.75, 0.4], axis: 'x', size: [rng.int(6, 12), 0, rng.int(4, 8)], outline: 'leaf', segments: 2, mirror: true, color: col } },
			!has('dorsal_fin') && horizontal && { en: 'add a dorsal fin', ja: '背びれを付けて', part: { name: 'dorsal_fin', shape: 'plane', parent: root.name, at: [0.5, 1, 0.5], axis: 'y', size: [0, rng.int(3, 6), rng.int(4, 8)], outline: rng.pick(['sail', 'triangle']), color: col } },
			!has('crown') && { en: 'put a crown on it', ja: '王冠をかぶせて', part: { name: 'crown', shape: 'cone', parent: head.name, at: 'top', axis: 'y', size: [2, 3, 2], tip: 0.2, count: 4, arrange: 'row', spacing: [2, 0, 0], color: COLORS.find((c) => c[0] === 'yellow')[2] } },
		].filter(Boolean);
		if (!lib.length) return null;
		const pick = rng.pick(lib);
		const s = clone(spec);
		s.parts.push(pick.part);
		return { en: pick.en, ja: pick.ja, spec: s };
	},
	curl(spec, rng) {
		const p = pickPart(rng, spec, (q) => (q.segments ?? 1) >= 2);
		if (!p) return null;
		const s = clone(spec);
		const q = s.parts.find((x) => x.name === p.name);
		const en = partLabel(p.name, 'en'), ja = partLabel(p.name, 'ja');
		if (p.curl && rng.chance(0.4)) {
			delete q.curl;
			return { en: `straighten the ${en}`, ja: `${ja}をまっすぐにして`, spec: s };
		}
		const cur = typeof p.curl === 'number' ? p.curl : 0;
		q.curl = cur + (cur < 0 ? -1 : 1) * rng.int(12, 25);
		return { en: rng.pick([`make the ${en} curl more`, `bend the ${en} more`]), ja: rng.pick([`${ja}をもっと曲げて`, `${ja}を丸めて`]), spec: s };
	},
	pattern(spec, rng) {
		const cur = spec.texture?.pattern || 'plain';
		const s = clone(spec);
		if (cur !== 'plain' && rng.chance(0.3)) {
			delete s.texture;
			return { en: rng.pick(['remove the pattern', 'make it plain']), ja: rng.pick(['模様をなくして', '無地にして']), spec: s };
		}
		const p = rng.pick(Object.keys(PATTERN_WORDS).filter((k) => k !== 'plain' && k !== cur));
		s.texture = { pattern: p };
		const [en, ja] = PATTERN_WORDS[p];
		return { en: rng.pick([`make it ${en}`, `add a ${en} pattern`]), ja: rng.pick([`${ja}にして`, `模様を${ja}に変えて`]), spec: s };
	},
	belly(spec, rng) {
		const p = pickPart(rng, spec, (q) => q.shape !== 'eye' && q.shape !== 'plane' && Array.isArray(q.size) && q.size[1] <= Math.max(q.size[0], q.size[2]) * 1.2);
		if (!p) return null;
		const s = clone(spec);
		const q = s.parts.find((x) => x.name === p.name);
		const en = partLabel(p.name, 'en'), ja = partLabel(p.name, 'ja');
		if (p.belly && rng.chance(0.35)) {
			delete q.belly;
			return { en: `remove the pale belly from the ${en}`, ja: `${ja}の腹の色をなくして`, spec: s };
		}
		const c = rng.pick(COLORS.filter((x) => ['white', 'cream', 'light gray', 'beige', 'yellow', 'pink'].includes(x[0])));
		q.belly = c[2];
		return { en: rng.pick([`give the ${en} a ${c[0]} belly`, `make the underside of the ${en} ${c[0]}`]), ja: rng.pick([`${ja}の腹を${c[1]}にして`, `${ja}の下側を${c[1]}にして`]), spec: s };
	},
	gradient(spec, rng) {
		const p = pickPart(rng, spec, (q) => q.shape !== 'eye');
		if (!p) return null;
		const s = clone(spec);
		const q = s.parts.find((x) => x.name === p.name);
		const en = partLabel(p.name, 'en'), ja = partLabel(p.name, 'ja');
		if (p.color_to && rng.chance(0.35)) {
			delete q.color_to;
			return { en: `make the ${en} one solid colour`, ja: `${ja}のグラデーションをやめて単色にして`, spec: s };
		}
		const c = namedColor(rng, p.color);
		q.color_to = c.hex;
		return { en: rng.pick([`make the ${en} fade to ${c.en} toward the tip`, `add a ${c.en} gradient to the ${en}`]), ja: rng.pick([`${ja}を先に向かって${c.ja}のグラデーションにして`, `${ja}の先を${c.ja}にぼかして`]), spec: s };
	},
	glowPattern(spec, rng) {
		const p = pickPart(rng, spec, (q) => q.shape !== 'eye' && q.shape !== 'plane');
		if (!p) return null;
		const [pat, en0, ja0] = rng.pick([['spots', 'glowing spots', '光る斑点'], ['stripes', 'glowing stripes', '光る縞'], ['edges', 'a glowing ridge', '光る筋'], ['rim', 'a glowing rim', '光る縁']]);
		if (p.glow_pattern === pat) return null;
		const c = rng.pick([['cyan', '水色'], ['light blue', '薄い青'], ['lime', '黄緑'], ['magenta', '赤紫'], ['yellow', '黄色']]);
		const s = clone(spec);
		Object.assign(s.parts.find((x) => x.name === p.name), { glow: p.glow || 1, glow_color: COLORS.find((x) => x[0] === c[0])[2], glow_pattern: pat });
		const en = partLabel(p.name, 'en'), ja = partLabel(p.name, 'ja');
		return { en: `give the ${en} ${c[0]} ${en0.replace(/^a /, '')}`, ja: `${ja}に${c[1]}の${ja0}を付けて`, spec: s };
	},
	scale(spec, rng) {
		const cur = spec.scale ?? 1;
		const [f, en, ja] = rng.pick([[2, 'make it twice as big', '全体を2倍の大きさにして'], [0.5, 'make it half the size', '全体を半分の大きさにして'], [1.25, 'make it a bit bigger', '全体を少し大きくして'], [0.75, 'make it a bit smaller', '全体を少し小さくして'], [1.5, 'make the whole model 1.5x larger', '全体を1.5倍に']]);
		const n = Math.round(cur * f * 100) / 100;
		if (n < 0.25 || n > 3) return null;
		const s = clone(spec);
		s.scale = n;
		return { en, ja, spec: s };
	},
};

const WEIGHTS = [['resize', 4], ['length', 3], ['recolorPart', 3], ['recolorAll', 2], ['glow', 2], ['count', 2], ['remove', 3], ['add', 3], ['curl', 1], ['pattern', 1], ['scale', 1], ['belly', 2], ['gradient', 2], ['glowPattern', 2]];

/** One edit (sometimes two combined). Returns {op, en, ja, spec} or null. */
export function mutate(spec, rng) {
	for (let tries = 0; tries < 6; tries++) {
		const op = rng.weighted(WEIGHTS);
		const a = OPS[op](spec, rng);
		if (!a) continue;
		if (rng.chance(0.15)) {
			const op2 = rng.weighted(WEIGHTS.filter(([o]) => o !== op));
			const b = OPS[op2](a.spec, rng);
			// Removing a part and re-adding one with the same name reorders parts: not expressible as a minimal diff
			const names = (x) => new Set(x.parts.map((p) => p.name));
			const removed = [...names(spec)].filter((n) => !names(a.spec).has(n));
			if (b && removed.some((n) => names(b.spec).has(n))) return { op, ...a };
			if (b) return { op: `${op}+${op2}`, en: `${a.en} and ${b.en}`, ja: `${a.ja.replace(/して$/, 'して、')}${b.ja}`, spec: b.spec };
		}
		return { op, ...a };
	}
	return null;
}

export const EDIT_OPS = Object.keys(OPS);
