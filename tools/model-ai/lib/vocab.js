// Words shared by the samplers, captions and edit instructions (English / Japanese).

/** Named colours: [en, ja, hex]. Captions name a colour by the nearest entry. */
export const COLORS = [
	['white', '白', '#eeeeee'],
	['cream', 'クリーム色', '#eee2c0'],
	['light gray', '明るい灰色', '#b4b8bc'],
	['gray', '灰色', '#80848a'],
	['dark gray', '濃い灰色', '#484c52'],
	['black', '黒', '#1e1e22'],
	['red', '赤', '#c83232'],
	['dark red', '暗い赤', '#761c20'],
	['pink', 'ピンク', '#e892b2'],
	['orange', 'オレンジ', '#e27a30'],
	['brown', '茶色', '#7a5232'],
	['beige', 'ベージュ', '#cfb98e'],
	['yellow', '黄色', '#e2c832'],
	['lime', '黄緑', '#92d042'],
	['green', '緑', '#3a9a42'],
	['dark green', '深緑', '#1f5a2c'],
	['teal', '青緑', '#2a968e'],
	['cyan', '水色', '#52d0e2'],
	['light blue', '薄い青', '#92b8e8'],
	['blue', '青', '#3262c2'],
	['navy', '紺色', '#1e2a62'],
	['purple', '紫', '#7a42b2'],
	['lavender', '薄紫', '#b8a2e2'],
	['magenta', '赤紫', '#c242a2'],
];

export const hexToRgb = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
export const rgbToHex = (c) => '#' + c.map((v) => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')).join('');

/** Perceptually weighted RGB distance (redmean). */
export function colorDistance(a, b) {
	const [r1, g1, b1] = typeof a === 'string' ? hexToRgb(a) : a;
	const [r2, g2, b2] = typeof b === 'string' ? hexToRgb(b) : b;
	const rm = (r1 + r2) / 2;
	return Math.sqrt((2 + rm / 256) * (r1 - r2) ** 2 + 4 * (g1 - g2) ** 2 + (2 + (255 - rm) / 256) * (b1 - b2) ** 2);
}

export function colorName(hex) {
	let best = COLORS[0], d = Infinity;
	for (const c of COLORS) {
		const dc = colorDistance(hex, c[2]);
		if (dc < d) (d = dc), (best = c);
	}
	return { en: best[0], ja: best[1], hex: best[2] };
}

export const colorByName = (en) => COLORS.find((c) => c[0] === en);

/** Part name tokens -> Japanese. Names are lower_snake_case: modifiers first, base word last. */
const PART_JA = {
	body: '胴体', head: '頭', tail: '尾', tail_fin: '尾びれ', dorsal_fin: '背びれ', pectoral_fin: '胸びれ', pelvic_fin: '腹びれ',
	anal_fin: '尻びれ', fin: 'ひれ', eye: '目', jaw: 'あご', lure: '誘引突起', lure_bulb: '提灯', barbel: 'ひげ', bell: '傘',
	tentacle: '触手', oral_arm: '口腕', mantle: '外套膜', arm: '腕', leg: '脚', claw: 'はさみ', pincer: 'はさみ',
	eye_stalk: '眼柄', antenna: '触角', shell: '殻', carapace: '甲羅', foot: '腹足', stalk: '柄', stem: '茎', cap: 'かさ',
	plume: '鰓冠', tube: '棲管', disc: '盤', papilla: '突起', spike: 'トゲ', horn: '角', ear: '耳', wing: '翼', beak: 'くちばし',
	neck: '首', snout: '鼻先', tongue: '舌', torso: '胴', abdomen: '腹部', thorax: '胸部', branch: '枝', polyp: 'ポリプ',
	leaf: '葉', blade: '葉', rim: '縁', siphon: '水管', gill: 'えら', rhinophore: '触角', skirt: 'ひだ', frill: 'フリル',
	whisker: 'ひげ', mane: 'たてがみ', crest: 'とさか', stinger: '毒針', mandible: '大あご', trunk: '幹', root: '根',
	base: '土台', bulb: '球部', web: '膜', ear_fin: '耳状のひれ', rostrum: '額角', segment: '節', cheliped: 'はさみ脚',
	hand: '手', toe: '指', hood: 'フード', sail: '帆', knob: 'こぶ', spot: '斑点', light: '発光器', photophore: '発光器',
	flower: '花', petal: '花びら', frond: '葉状体', crown: '冠', nose: '鼻', mouth: '口', teeth: '歯', fang: '牙',
	scale: '鱗', plate: '板', ridge: '隆起', seed: '種', pod: 'さや', cone: '錐体', core: '核', orb: '球', ring: '輪',
};
const MOD_JA = {
	front: '前', back: '後ろ', rear: '後ろ', upper: '上', lower: '下', side: '横', long: '長い', short: '短い', small: '小さな',
	big: '大きな', top: '上', bottom: '下', inner: '内側の', outer: '外側の', second: '第二', main: '主', left: '左', right: '右',
	head: '頭の', tail: '尾の', walking: '歩脚の', oral: '口の', dorsal: '背の', glow: '光る', chin: 'あごの',
};

export function partLabel(name, lang = 'en') {
	const base = name.replace(/_\d+$/, '');
	if (lang === 'en') return base.replace(/_/g, ' ');
	if (PART_JA[base]) return PART_JA[base];
	const toks = base.split('_');
	for (let k = 1; k < toks.length; k++) {
		const tail = toks.slice(k).join('_');
		if (PART_JA[tail]) return toks.slice(0, k).map((t) => MOD_JA[t] ?? PART_JA[t] ?? t).join('') + PART_JA[tail];
	}
	return base.replace(/_/g, ' ');
}

export const PATTERN_WORDS = {
	plain: ['plain', '無地'],
	speckle: ['speckled', '細かい斑点'],
	spots: ['spotted', '水玉模様'],
	mottled: ['mottled', 'まだら模様'],
	stripes: ['striped', '縞模様'],
	scales: ['scaly', '鱗模様'],
	bands: ['banded', '帯模様'],
};
