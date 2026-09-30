// Marine body plans. Conventions of the parts template: size = [x width, y height, z length],
// -Z is the front, `at` fractions x: left 0..right 1, y: bottom 0..top 1, z: front 0..back 1.

import { FIN_OUTLINES, kit, TAIL_OUTLINES } from './kit.js';

const DEEP = ['black', 'dark gray', 'navy', 'dark red', 'brown', 'gray'];
const BRIGHT = ['red', 'orange', 'yellow', 'blue', 'teal', 'purple', 'pink', 'green', 'light blue', 'white'];
const PALE = ['white', 'cream', 'light blue', 'lavender', 'pink', 'light gray'];
const GLOW = ['cyan', 'light blue', 'lime', 'blue', 'magenta', 'yellow'];

export function fish(rng) {
	const k = kit(rng);
	const { u } = k;
	const deep = rng.chance(0.55);
	const body = k.color('body', k.pickCol(deep ? DEEP : BRIGHT));
	const finCol = rng.chance(0.6) ? body : k.pickCol(deep ? DEEP : BRIGHT);
	const L = rng.int(12, 34);
	const tall = rng.chance(0.2);
	const H = u(L * (tall ? rng.float(0.6, 0.85) : rng.float(0.28, 0.5)), 3);
	const W = u(H * rng.float(0.45, 0.85), 2);
	k.kind('fish', '魚');
	if (L / H > 2.8) k.trait('a long slender body', '細長い体');
	if (tall) k.trait('a tall flat body', '平たく背の高い体');
	const parts = [];
	const shape = rng.pick(['ellipsoid', 'profile', 'profile', 'box']); // box = chunky big-block body
	const b = { name: 'body', shape, axis: '-z', center: true, size: [W, H, L], color: body };
	if (shape === 'profile') b.profile = [rng.float(0.3, 0.5), rng.float(0.6, 0.8), 1, rng.float(0.85, 1), rng.float(0.6, 0.8)].map((v) => Math.round(v * 100) / 100);
	parts.push(b);
	// Tail and tail fin
	const tailOutline = rng.pick(TAIL_OUTLINES);
	let finParent = 'body', finAt = 'back';
	if (rng.chance(0.45)) {
		parts.push({ name: 'tail', shape: 'cylinder', parent: 'body', at: 'back', axis: 'z', size: [u(W * 0.45), u(H * 0.4), u(L * rng.float(0.15, 0.3))], taper: 0.6, segments: 2, color: body });
		finParent = 'tail';
		finAt = 'tip';
	}
	parts.push({ name: 'tail_fin', shape: 'plane', parent: finParent, at: finAt, axis: 'z', size: [0, u(H * rng.float(0.7, 1.3), 2), u(L * rng.float(0.18, 0.35), 2)], outline: tailOutline, color: finCol });
	if (tailOutline === 'fork' || tailOutline === 'lunate') k.trait('a forked tail', '二股に分かれた尾びれ');
	// Dorsal fin
	if (rng.chance(0.8)) {
		const big = rng.chance(0.25);
		parts.push({ name: 'dorsal_fin', shape: 'plane', parent: 'body', at: [0.5, 1, rng.float(0.35, 0.6)], axis: 'y', center: false, size: [0, u(H * (big ? rng.float(0.7, 1.1) : rng.float(0.25, 0.5))), u(L * rng.float(0.25, 0.55), 2)], outline: rng.pick(FIN_OUTLINES), color: finCol });
		if (big) k.trait('a tall dorsal fin', '大きな背びれ');
	}
	// Pectoral fins
	if (rng.chance(0.85)) {
		const long = rng.chance(0.2);
		parts.push({ name: 'pectoral_fin', shape: 'plane', parent: 'body', at: [1, rng.float(0.3, 0.5), rng.float(0.25, 0.4)], axis: 'x', size: [u(H * (long ? rng.float(0.8, 1.3) : rng.float(0.3, 0.55))), 0, u(L * rng.float(0.1, 0.2))], outline: rng.pick(['leaf', 'round', 'fan']), mirror: true, color: finCol });
		if (long) k.trait('long pectoral fins', '長い胸びれ');
	}
	if (rng.chance(0.35)) parts.push({ name: 'anal_fin', shape: 'plane', parent: 'body', at: [0.5, 0, rng.float(0.6, 0.75)], axis: '-y', size: [0, u(H * rng.float(0.2, 0.4)), u(L * rng.float(0.15, 0.3))], outline: rng.pick(['triangle', 'round']), color: finCol });
	// Eyes
	const bigEye = rng.chance(0.3);
	const eye = u(bigEye ? H * rng.float(0.28, 0.4) : H * rng.float(0.12, 0.2), 1);
	parts.push({ name: 'eye', shape: 'eye', parent: 'body', at: [1, rng.float(0.55, 0.7), rng.float(0.1, 0.2)], size: eye, mirror: true });
	if (bigEye) k.trait('big eyes', '大きな目');
	// Mouth / jaw
	if (rng.chance(0.3)) {
		parts.push({ name: 'jaw', shape: 'box', parent: 'body', at: [0.5, 0.2, 0.05], axis: '-z', size: [u(W * 0.8), u(H * 0.25), u(L * rng.float(0.15, 0.3))], color: body, rotate: [rng.int(10, 30), 0, 0] });
		k.trait('a big gaping jaw', '大きく開いたあご');
	}
	// Deep-sea extras
	if (deep && rng.chance(0.35)) {
		const g = k.color('glow', k.pickCol(GLOW, 8));
		parts.push({ name: 'lure', shape: 'cylinder', parent: 'body', at: [0.5, 1, 0.12], axis: 'y', size: [1, u(H * rng.float(0.6, 1.1)), 1], segments: 3, curl: -rng.int(15, 35), color: body });
		parts.push({ name: 'lure_bulb', shape: 'ellipsoid', parent: 'lure', at: 'tip', center: true, size: [2, 2, 2], color: g, glow: 1.5, glow_color: g });
		k.kind('anglerfish', 'アンコウ');
		k.trait('a glowing lure on its head', '頭に光る提灯');
	} else if (deep && rng.chance(0.25)) {
		const g = k.color('glow', k.pickCol(GLOW, 8));
		parts.push({ name: 'barbel', shape: 'cylinder', parent: 'body', at: [0.5, 0, 0.1], axis: '-y', size: [1, u(H * rng.float(0.5, 1.2)), 1], segments: 2, color: body, glow: 1, glow_color: g });
		k.trait('a glowing chin barbel', '光るあごひげ');
	}
	if (deep) k.trait('deep-sea colours', '深海魚らしい暗い体色');
	const texture = rng.chance(0.5) ? { pattern: rng.pick(['scales', 'speckle', 'spots', 'stripes', 'mottled']) } : undefined;
	return { spec: { orientation: 'horizontal', parts, texture }, facts: k.facts };
}

export function jellyfish(rng) {
	const k = kit(rng);
	const { u } = k;
	const glowing = rng.chance(0.4);
	const bell = k.color('body', k.pickCol(rng.chance(0.7) ? PALE : ['red', 'purple', 'orange', 'pink', 'dark red']));
	const D = rng.int(8, 26);
	const H = u(D * rng.float(0.45, 1.0), 3);
	const shape = rng.pick(['dome', 'dome', 'profile', 'ellipsoid']);
	k.kind('jellyfish', 'クラゲ');
	const parts = [];
	const b = { name: 'bell', shape, size: [D, H, D], color: bell };
	if (shape === 'profile') b.profile = [1, rng.float(0.95, 1), rng.float(0.8, 0.95), rng.float(0.55, 0.75), rng.float(0.2, 0.4)].map((v) => Math.round(v * 100) / 100);
	parts.push(b);
	const tcol = glowing ? k.color('glow', k.pickCol(GLOW, 8)) : bell;
	const n = rng.pick([4, 6, 8, 8, 12, 16]);
	const TL = u(D * rng.float(0.8, 2.4), 4);
	parts.push({ name: 'tentacle', shape: 'cylinder', parent: 'bell', at: 'bottom', axis: '-y', size: [1, TL, 1], count: n, arrange: 'ring', radius: u(D * rng.float(0.3, 0.45)), tilt: rng.int(0, 20), segments: rng.int(3, 5), curl: rng.pick([-1, 1]) * rng.int(4, 18), color: tcol, glow: glowing ? 1 : undefined, glow_color: glowing ? tcol : undefined });
	if (TL > D * 1.6) k.trait('long trailing tentacles', '長く垂れた触手');
	if (n >= 12) k.trait('many tentacles', 'たくさんの触手');
	if (rng.chance(0.6)) {
		parts.push({ name: 'oral_arm', shape: rng.pick(['plane', 'cylinder']), parent: 'bell', at: 'bottom', axis: '-y', size: [2, u(TL * rng.float(0.4, 0.8)), 0], outline: 'frill', count: 4, arrange: 'ring', radius: 1, segments: 3, color: k.col(rng.pick(PALE)) });
		const oa = parts.at(-1);
		if (oa.shape === 'cylinder') (oa.size = [2, oa.size[1], 2]), delete oa.outline;
		k.trait('frilly oral arms', 'ひらひらした口腕');
	}
	if (rng.chance(0.3)) parts.push({ name: 'rim', shape: 'cylinder', parent: 'bell', at: [0.5, 0.05, 0.5], center: true, size: [D + 1, 1, D + 1], color: tcol, glow: glowing ? 1 : undefined, glow_color: glowing ? tcol : undefined });
	if (glowing) k.trait('glowing tentacles', '光る触手');
	if (bell && PALE.length && rng.chance(0.3)) k.trait('a translucent pale bell', '半透明の淡い傘');
	return { spec: { orientation: 'upright', parts }, facts: k.facts };
}

export function eel(rng) {
	const k = kit(rng);
	const { u } = k;
	const snake = rng.chance(0.3);
	const col = k.color('body', k.pickCol(snake ? ['green', 'brown', 'yellow', 'black', 'dark green', 'beige', 'red'] : [...DEEP, 'brown', 'gray', 'teal']));
	const H = rng.int(3, 7);
	const W = u(H * rng.float(0.7, 1), 2);
	const L = rng.int(24, 64);
	k.kind(snake ? 'snake' : 'eel', snake ? 'ヘビ' : 'ウナギ');
	if (L > 44) k.trait('a very long body', 'とても長い体');
	const parts = [{ name: 'body', shape: 'cylinder', axis: 'z', size: [W, H, L], segments: rng.int(4, 7), taper: Math.round(rng.float(0.2, 0.5) * 100) / 100, curl: rng.int(-6, 6), color: col }];
	const hl = u(H * rng.float(1.2, 2.2), 2);
	parts.push({ name: 'head', shape: rng.pick(['box', 'ellipsoid']), parent: 'body', at: 'front', axis: '-z', size: [u(W * rng.float(1, 1.4)), u(H * rng.float(1, 1.2)), hl], color: col });
	parts.push({ name: 'eye', shape: 'eye', parent: 'head', at: [1, 0.7, 0.3], size: rng.pick([1, 1, 1.5, 2]), mirror: true });
	if (!snake && rng.chance(0.3)) {
		parts.push({ name: 'jaw', shape: 'box', parent: 'head', at: [0.5, 0.1, 0.3], axis: '-z', size: [u(W * 1.6), u(H * 0.4), u(hl * rng.float(1.5, 2.5))], rotate: [rng.int(15, 30), 0, 0], color: col });
		k.trait('an enormous gaping mouth', '巨大な口');
	}
	if (snake) {
		parts.push({ name: 'tongue', shape: 'plane', parent: 'head', at: [0.5, 0.3, 0], axis: '-z', size: [2, 0, rng.int(2, 4)], outline: 'fork', color: k.col('red') });
		k.trait('a forked tongue', '先の割れた舌');
		if (rng.chance(0.25)) {
			parts.push({ name: 'hood', shape: 'plane', parent: 'body', at: [0.5, 0.5, 0.05], axis: 'y', size: [u(W * 2), u(H * 1.4), 0], outline: 'round', color: col });
			k.kind('cobra', 'コブラ');
		}
	} else {
		if (rng.chance(0.7)) parts.push({ name: 'dorsal_fin', shape: 'plane', parent: 'body', at: [0.5, 1, 0.5], axis: 'y', size: [0, rng.int(1, 3), u(L * rng.float(0.5, 0.85))], outline: rng.pick(['frill', 'rect', 'taper']), color: col });
		parts.push({ name: 'tail_fin', shape: 'plane', parent: 'body', at: 'tip', axis: 'z', size: [0, u(H * rng.float(1, 1.8)), rng.int(2, 5)], outline: rng.pick(['round', 'taper', 'fan']), color: col });
		if (rng.chance(0.3)) {
			const g = k.color('glow', k.pickCol(GLOW, 8));
			parts.push({ name: 'light', shape: 'ellipsoid', parent: 'body', at: 'tip', center: true, size: [2, 2, 2], color: g, glow: 1.5, glow_color: g });
			k.trait('a glowing tail tip', '光る尾の先');
		}
	}
	const texture = rng.chance(snake ? 0.8 : 0.4) ? { pattern: rng.pick(snake ? ['scales', 'bands', 'stripes', 'spots'] : ['speckle', 'mottled', 'bands']) } : undefined;
	return { spec: { orientation: 'horizontal', parts, texture }, facts: k.facts };
}

export function squid(rng) {
	const k = kit(rng);
	const { u } = k;
	const col = k.color('body', k.pickCol(['red', 'dark red', 'pink', 'white', 'cream', 'purple', 'orange', 'lavender']));
	const W = rng.int(5, 12);
	const L = u(W * rng.float(1.8, 3.2), 6);
	k.kind('squid', 'イカ');
	const parts = [];
	const mantle = { name: 'mantle', shape: rng.pick(['profile', 'cone']), size: [W, L, W], color: col };
	if (mantle.shape === 'profile') mantle.profile = [0.9, 1, 1, 0.9, 0.6, 0.25];
	else mantle.tip = 0.2;
	parts.push(mantle);
	parts.push({ name: 'fin', shape: 'plane', parent: 'mantle', at: [1, rng.float(0.7, 0.88), 0.5], axis: 'x', size: [u(W * rng.float(0.5, 0.9)), u(L * rng.float(0.2, 0.35)), 0], outline: rng.pick(['triangle', 'round', 'leaf']), mirror: true, color: col });
	parts.push({ name: 'head', shape: 'ellipsoid', parent: 'mantle', at: 'bottom', axis: '-y', size: [u(W * 0.85), u(W * 0.7), u(W * 0.85)], color: col });
	const bigEye = rng.chance(0.4);
	parts.push({ name: 'eye', shape: 'eye', parent: 'head', at: [1, 0.5, 0.5], size: u(W * (bigEye ? 0.4 : 0.22)), mirror: true });
	if (bigEye) k.trait('huge eyes', '巨大な目');
	const armL = u(L * rng.float(0.35, 0.7), 3);
	const glow = rng.chance(0.25) ? k.color('glow', k.pickCol(GLOW, 8)) : null;
	parts.push({ name: 'arm', shape: 'cylinder', parent: 'head', at: 'tip', axis: '-y', size: [1, armL, 1], count: 8, arrange: 'ring', radius: u(W * 0.25), tilt: rng.int(5, 25), segments: 3, curl: rng.int(-10, 10), color: col, glow: glow ? 1 : undefined, glow_color: glow || undefined });
	if (rng.chance(0.8)) {
		parts.push({ name: 'tentacle', shape: 'cylinder', parent: 'head', at: [0.7, 0, 0.5], axis: '-y', size: [1, u(armL * rng.float(1.8, 3)), 1], segments: 4, curl: rng.int(-8, 8), mirror: true, color: col });
		k.trait('two long feeding tentacles', '2本の長い触腕');
	}
	if (glow) k.trait('glowing arms', '光る腕');
	return { spec: { orientation: 'upright', parts, texture: rng.chance(0.5) ? { pattern: rng.pick(['speckle', 'spots']) } : undefined }, facts: k.facts };
}

export function octopus(rng) {
	const k = kit(rng);
	const { u } = k;
	const col = k.color('body', k.pickCol(['red', 'orange', 'pink', 'purple', 'brown', 'dark red', 'beige', 'lavender']));
	const D = rng.int(6, 16);
	const dumbo = rng.chance(0.3);
	k.kind(dumbo ? 'dumbo octopus' : 'octopus', dumbo ? 'メンダコ' : 'タコ');
	const parts = [{ name: 'mantle', shape: rng.pick(['ellipsoid', 'profile']), size: [D, u(D * rng.float(dumbo ? 0.7 : 1, dumbo ? 1 : 1.5)), D], color: col }];
	if (parts[0].shape === 'profile') parts[0].profile = [0.8, 1, 1, 0.9, 0.65, 0.3];
	parts.push({ name: 'eye', shape: 'eye', parent: 'mantle', at: [1, 0.3, 0.4], size: u(D * rng.float(0.12, 0.22)), mirror: true });
	const aL = u(D * rng.float(dumbo ? 0.4 : 0.8, dumbo ? 0.7 : 1.8), 3);
	parts.push({ name: 'arm', shape: 'cylinder', parent: 'mantle', at: 'bottom', axis: '-y', size: [u(D * 0.18), aL, u(D * 0.18)], count: 8, arrange: 'ring', radius: u(D * 0.3), tilt: rng.int(dumbo ? 20 : 30, dumbo ? 40 : 75), taper: 0.35, segments: 3, curl: rng.int(10, 35), color: col });
	if (dumbo) {
		parts.push({ name: 'ear_fin', shape: 'plane', parent: 'mantle', at: [1, 0.75, 0.5], axis: 'x', size: [u(D * 0.5), u(D * 0.4), 0], outline: 'round', mirror: true, color: col });
		k.trait('ear-like fins', '耳のようなひれ');
	} else if (aL > D * 1.4) k.trait('long curling arms', '長くくねった腕');
	if (rng.chance(0.3)) {
		parts.push({ name: 'web', shape: 'cone', parent: 'mantle', at: 'bottom', axis: '-y', size: [u(D * 1.3), u(aL * 0.4), u(D * 1.3)], tip: 1, color: col, detail: 2 });
		k.trait('webbed arms', '膜でつながった腕');
	}
	return { spec: { orientation: 'upright', parts, texture: rng.chance(0.6) ? { pattern: rng.pick(['spots', 'mottled', 'speckle']) } : undefined }, facts: k.facts };
}

export function shrimp(rng) {
	const k = kit(rng);
	const { u } = k;
	const col = k.color('body', k.pickCol(['red', 'orange', 'pink', 'white', 'cream', 'dark red', 'blue', 'light blue']));
	const L = rng.int(12, 30);
	const H = u(L * rng.float(0.22, 0.32), 3);
	const W = u(H * rng.float(0.7, 0.95), 2);
	const lobster = rng.chance(0.3);
	k.kind(lobster ? 'lobster' : 'shrimp', lobster ? 'ロブスター' : 'エビ');
	const parts = [{ name: 'body', shape: 'profile', axis: 'z', size: [W, H, L], segments: rng.int(3, 5), profile: [0.9, 1, 0.9, 0.7, 0.5, 0.35], curl: rng.int(0, 12), color: col }];
	parts.push({ name: 'tail_fin', shape: 'plane', parent: 'body', at: 'tip', axis: 'z', size: [u(W * 1.4), 0, u(L * 0.18, 2)], outline: 'fan', color: col });
	parts.push({ name: 'rostrum', shape: 'cone', parent: 'body', at: [0.5, 0.8, 0], axis: '-z', size: [1, 1, u(L * rng.float(0.12, 0.35))], tip: 0.2, color: col });
	const antL = u(L * rng.float(0.6, 1.8), 4);
	parts.push({ name: 'antenna', shape: 'cylinder', parent: 'body', at: [0.65, 0.7, 0], axis: '-z', size: [1, 1, antL], rotate: [rng.int(5, 25), rng.int(10, 25), 0], segments: 3, curl: rng.int(-12, 0), mirror: true, color: col });
	if (antL > L * 1.2) k.trait('very long antennae', 'とても長い触角');
	parts.push({ name: 'leg', shape: 'cylinder', parent: 'body', at: [0.85, 0.1, 0.25], axis: '-y', size: [1, u(H * rng.float(0.7, 1.1)), 1], count: rng.int(3, 5), arrange: 'row', spacing: [0, 0, u(L * 0.07)], rotate: [0, 0, -rng.int(5, 20)], segments: 2, mirror: true, color: col });
	parts.push({ name: 'eye', shape: 'eye', parent: 'body', at: [0.85, 0.8, 0.05], size: rng.pick([1, 1.5, 2]), mirror: true });
	if (lobster) {
		parts.push({ name: 'arm', shape: 'cylinder', parent: 'body', at: [0.85, 0.3, 0.05], axis: '-z', size: [u(W * 0.25), u(W * 0.25), u(L * 0.25)], rotate: [0, rng.int(15, 30), 0], mirror: true, color: col });
		parts.push({ name: 'claw', shape: 'box', parent: 'arm', at: 'tip', axis: '-z', size: [u(W * 0.5), u(W * 0.35), u(L * rng.float(0.2, 0.35))], mirror: true, color: col });
		k.trait('big front claws', '大きなはさみ');
	}
	if (rng.chance(0.2)) {
		const g = k.color('glow', k.pickCol(GLOW, 8));
		parts.push({ name: 'photophore', shape: 'box', parent: 'body', at: [1, 0.2, 0.4], size: [1, 1, 1], count: 3, arrange: 'row', spacing: [0, 0, u(L * 0.12)], mirror: true, color: g, glow: 1.5, glow_color: g });
		k.trait('glowing light organs', '光る発光器');
	}
	return { spec: { orientation: 'horizontal', parts, texture: rng.chance(0.4) ? { pattern: rng.pick(['bands', 'speckle', 'stripes']) } : undefined }, facts: k.facts };
}

export function isopod(rng) {
	const k = kit(rng);
	const { u } = k;
	const col = k.color('body', k.pickCol(['gray', 'light gray', 'beige', 'brown', 'lavender', 'cream', 'dark gray']));
	const W = rng.int(8, 18);
	const L = u(W * rng.float(1.4, 2), 8);
	const H = u(W * rng.float(0.3, 0.5), 3);
	k.kind('isopod', '等脚類');
	const parts = [{ name: 'body', shape: rng.pick(['ellipsoid', 'profile']), axis: 'z', size: [W, H, L], segments: rng.int(4, 7), color: col }];
	if (parts[0].shape === 'profile') parts[0].profile = [0.8, 0.95, 1, 1, 0.9, 0.6];
	parts.push({ name: 'head', shape: 'box', parent: 'body', at: 'front', axis: '-z', size: [u(W * 0.55), u(H * 0.7), u(L * 0.1, 2)], color: col });
	parts.push({ name: 'eye', shape: 'eye', parent: 'head', at: [1, 0.6, 0.4], size: rng.pick([1, 2]), mirror: true });
	parts.push({ name: 'antenna', shape: 'cylinder', parent: 'head', at: [0.8, 0.5, 0], axis: '-z', size: [1, 1, u(L * rng.float(0.2, 0.5))], rotate: [0, rng.int(20, 40), 0], segments: 2, curl: -8, mirror: true, color: col });
	const nLeg = rng.int(5, 7);
	parts.push({ name: 'leg', shape: 'cylinder', parent: 'body', at: [1, 0.1, 0.15], axis: 'x', size: [u(W * rng.float(0.2, 0.4)), 1, 1], count: nLeg, arrange: 'row', spacing: [0, 0, u(L * 0.7 / nLeg)], rotate: [0, 0, -rng.int(25, 45)], segments: 2, curl: -rng.int(15, 35), mirror: true, color: col });
	parts.push({ name: 'tail_fin', shape: 'plane', parent: 'body', at: 'tip', axis: 'z', size: [u(W * 0.6), 0, u(L * 0.12)], outline: 'fan', color: col });
	k.trait('an armoured segmented back', '節のある硬い背中');
	return { spec: { orientation: 'horizontal', parts, texture: { pattern: 'bands' } }, facts: k.facts };
}

export function tubeworm(rng) {
	const k = kit(rng);
	const { u } = k;
	const tube = k.color('body', k.pickCol(['white', 'cream', 'beige', 'light gray']));
	const plume = k.color('plume', k.pickCol(['red', 'dark red', 'orange', 'pink', 'magenta']));
	const W = rng.int(2, 5);
	const L = rng.int(10, 32);
	const cluster = rng.chance(0.4);
	k.kind('tubeworm', 'チューブワーム');
	const parts = [{ name: 'tube', shape: 'cylinder', size: [W, L, W], segments: rng.int(2, 3), taper: Math.round(rng.float(0.8, 1) * 100) / 100, curl: rng.int(-6, 6), color: tube }];
	if (cluster) Object.assign(parts[0], { count: rng.int(3, 5), arrange: 'ring', radius: W + 1, tilt: rng.int(5, 15) });
	if (cluster) k.trait('a cluster of tubes', '群生した管');
	const glow = rng.chance(0.2);
	if (rng.chance(0.6)) parts.push({ name: 'plume', shape: 'cylinder', parent: 'tube', at: 'tip', axis: 'y', size: [1, u(W * rng.float(1.2, 3)), 1], count: rng.int(5, 10), arrange: 'ring', radius: u(W * 0.3, 0.5), tilt: rng.int(15, 45), segments: 2, color: plume, glow: glow ? 1 : undefined, glow_color: glow ? plume : undefined });
	else parts.push({ name: 'plume', shape: 'plane', parent: 'tube', at: 'tip', axis: 'y', size: [u(W * 1.5), u(W * rng.float(1.2, 2.5)), 0], outline: rng.pick(['hair', 'frill', 'fan']), count: rng.int(3, 4), arrange: 'ring', radius: 0.5, tilt: rng.int(10, 30), color: plume });
	k.trait('a bright feathery plume', '鮮やかな羽毛状の鰓冠');
	if (glow) k.trait('a glowing plume', '光る鰓冠');
	return { spec: { orientation: 'upright', parts, texture: rng.chance(0.5) ? { pattern: 'bands' } : undefined }, facts: k.facts };
}

export function snail(rng) {
	const k = kit(rng);
	const { u } = k;
	const foot = k.color('body', k.pickCol(['gray', 'beige', 'cream', 'brown', 'black', 'dark gray']));
	const shell = k.color('shell', k.pickCol(['brown', 'beige', 'orange', 'white', 'black', 'dark red', 'yellow']));
	const W = rng.int(4, 9);
	const H = u(W * rng.float(0.4, 0.6), 2);
	const L = u(W * rng.float(1.6, 2.4), 6);
	if (rng.chance(0.5)) k.kind('snail', 'カタツムリ');
	else k.kind('sea snail', '巻貝');
	const parts = [{ name: 'foot', shape: rng.pick(['box', 'ellipsoid']), axis: '-z', center: true, size: [W, H, L], color: foot, role: 'body' }];
	const tall = rng.chance(0.4);
	parts.push({ name: 'shell', shape: 'profile', parent: 'foot', at: [0.5, 1, 0.6], axis: 'y', size: [u(L * 0.6), u(L * (tall ? rng.float(0.8, 1.1) : rng.float(0.45, 0.65))), u(L * 0.6)], profile: tall ? [0.8, 1, 0.8, 0.55, 0.35, 0.15] : [0.85, 1, 0.95, 0.75, 0.45], rotate: [rng.int(-15, 5), 0, 0], color: shell, material: 'shell' });
	if (tall) k.trait('a tall spiral shell', '高く尖った殻');
	else k.trait('a round shell', '丸い殻');
	if (rng.chance(0.8)) {
		parts.push({ name: 'eye_stalk', shape: 'cylinder', parent: 'foot', at: [0.7, 0.8, 0.05], axis: 'y', rotate: [-rng.int(10, 30), 0, 0], size: [1, u(H * rng.float(1, 2)), 1], mirror: true, color: foot });
		parts.push({ name: 'eye', shape: 'eye', parent: 'eye_stalk', at: 'tip', size: 1, mirror: true });
		k.trait('eyes on long stalks', '長い柄の先の目');
	}
	if (rng.chance(0.2)) {
		parts.push({ name: 'spike', shape: 'cone', parent: 'shell', at: [0.5, 0.5, 1], axis: 'z', size: [1, 1, rng.int(2, 3)], count: 3, arrange: 'row', spacing: [0, u(L * 0.12), 0], color: shell });
		k.trait('spikes on its shell', '殻のトゲ');
	}
	return { spec: { orientation: 'upright', parts, texture: rng.chance(0.5) ? { pattern: rng.pick(['bands', 'stripes', 'mottled']) } : undefined }, facts: k.facts };
}

export function sea_slug(rng) {
	const k = kit(rng);
	const { u } = k;
	const cucumber = rng.chance(0.5);
	const col = k.color('body', k.pickCol(cucumber ? ['brown', 'dark red', 'pink', 'beige', 'black', 'lavender', 'orange'] : ['blue', 'purple', 'orange', 'yellow', 'pink', 'white', 'magenta', 'lime']));
	const W = rng.int(5, 10);
	const H = u(W * rng.float(0.6, 1), 3);
	const L = u(W * rng.float(2, 3.4), 10);
	k.kind(cucumber ? 'sea cucumber' : 'sea slug', cucumber ? 'ナマコ' : 'ウミウシ');
	const parts = [{ name: 'body', shape: 'profile', axis: '-z', center: true, size: [W, H, L], profile: [0.5, 0.9, 1, 1, 0.9, 0.6], segments: 3, color: col }];
	if (cucumber) {
		if (rng.chance(0.7)) {
			parts.push({ name: 'papilla', shape: 'cone', parent: 'body', at: [0.8, 1, 0.5], axis: 'y', size: [1, u(H * rng.float(0.3, 0.8)), 1], tip: 0.2, count: rng.int(3, 6), arrange: 'row', spacing: [0, 0, u(L / 7)], mirror: true, color: col });
			k.trait('soft spikes along its back', '背中の柔らかい突起');
		}
		if (rng.chance(0.35)) {
			parts.push({ name: 'skirt', shape: 'plane', parent: 'body', at: [0.5, 0.9, 0.1], axis: '-z', size: [u(W * 1.4), 0, u(L * 0.3)], outline: 'round', rotate: [-rng.int(20, 45), 0, 0], color: col });
			k.trait('a veil-like front fin', 'ベールのようなひれ');
		}
	} else {
		const acc = k.color('accent', k.pickCol(['orange', 'yellow', 'white', 'red', 'blue', 'black']));
		parts.push({ name: 'rhinophore', shape: 'cylinder', parent: 'body', at: [0.7, 1, 0.1], axis: 'y', size: [1, rng.int(2, 3), 1], mirror: true, color: acc });
		parts.push({ name: 'gill', shape: 'cylinder', parent: 'body', at: [0.5, 1, 0.85], axis: 'y', size: [1, rng.int(2, 4), 1], count: rng.int(5, 8), arrange: 'ring', radius: 1, tilt: rng.int(25, 45), color: acc });
		k.trait('a flower-like gill tuft', '花のようなえら');
		if (rng.chance(0.5)) {
			parts.push({ name: 'skirt', shape: 'plane', parent: 'body', at: [1, 0.3, 0.5], axis: 'x', size: [u(W * 0.4), 0, u(L * 0.8)], outline: 'frill', mirror: true, color: acc });
			k.trait('frilly edges', 'ひらひらした縁');
		}
	}
	return { spec: { orientation: 'horizontal', parts, texture: rng.chance(0.6) ? { pattern: rng.pick(cucumber ? ['speckle', 'mottled'] : ['spots', 'stripes', 'speckle']) } : undefined }, facts: k.facts };
}

export function starfish(rng) {
	const k = kit(rng);
	const { u } = k;
	const col = k.color('body', k.pickCol(['orange', 'red', 'purple', 'pink', 'yellow', 'blue', 'beige', 'dark red']));
	const D = rng.int(4, 10);
	const H = u(D * rng.float(0.3, 0.5));
	const brittle = rng.chance(0.3);
	const n = brittle ? 5 : rng.weighted([[5, 6], [6, 1], [8, 1], [12, 1]]);
	k.kind(brittle ? 'brittle star' : 'starfish', brittle ? 'クモヒトデ' : 'ヒトデ');
	const parts = [{ name: 'disc', shape: 'cylinder', size: [D, H, D], color: col }];
	const armL = u(D * (brittle ? rng.float(1.8, 3) : rng.float(0.9, 1.8)), 3);
	parts.push({ name: 'arm', shape: 'cylinder', parent: 'disc', at: 'center', axis: '-z', size: [brittle ? 1 : u(D * 0.45), brittle ? 1 : u(H * 0.8), armL], count: n, arrange: 'ring', radius: u(D * 0.3), tilt: rng.int(0, 10), taper: brittle ? 0.8 : 0.3, segments: brittle ? 4 : 2, curl: brittle ? rng.int(-25, 25) : rng.int(-5, 5), color: col });
	if (n > 5) k.trait(`${n} arms`, `${n}本の腕`);
	if (brittle) k.trait('thin snake-like arms', '細くくねる腕');
	return { spec: { orientation: 'upright', parts, texture: { pattern: rng.pick(['spots', 'speckle', 'mottled']) } }, facts: k.facts };
}

export function sessile(rng) {
	const k = kit(rng);
	const { u } = k;
	const type = rng.pick(['anemone', 'coral', 'sponge', 'sea_lily', 'sea_squirt']);
	const col = k.color('body', k.pickCol(['pink', 'orange', 'purple', 'red', 'yellow', 'white', 'lavender', 'teal', 'cream']));
	const glow = rng.chance(0.25) ? k.color('glow', k.pickCol(GLOW, 8)) : null;
	const g = glow ? { glow: 1, glow_color: glow } : {};
	const parts = [];
	if (type === 'anemone') {
		k.kind('sea anemone', 'イソギンチャク');
		const W = rng.int(4, 10), H = rng.int(4, 12);
		parts.push({ name: 'base', shape: 'cylinder', size: [W, H, W], taper: Math.round(rng.float(0.8, 1.1) * 100) / 100, color: col, role: 'body' });
		parts.push({ name: 'disc', shape: 'cylinder', parent: 'base', at: 'tip', size: [u(W * 1.2), 1, u(W * 1.2)], color: col });
		parts.push({ name: 'tentacle', shape: 'cylinder', parent: 'disc', at: 'top', axis: 'y', size: [1, u(W * rng.float(0.5, 1.3)), 1], count: rng.pick([8, 10, 12, 16]), arrange: 'ring', radius: u(W * 0.45), tilt: rng.int(25, 60), segments: 2, curl: rng.int(-12, 12), color: glow || k.col(rng.pick(PALE)), ...g });
		k.trait('a crown of tentacles', '冠のような触手');
	} else if (type === 'coral') {
		k.kind('coral', 'サンゴ');
		const W = rng.int(2, 4);
		parts.push({ name: 'trunk', shape: 'cylinder', size: [W, rng.int(5, 10), W], color: col, role: 'body' });
		parts.push({ name: 'branch', shape: 'cylinder', parent: 'trunk', at: [0.5, rng.float(0.5, 0.9), 0.5], axis: 'y', size: [u(W * 0.8), rng.int(5, 10), u(W * 0.8)], count: rng.int(2, 4), arrange: 'ring', radius: 0, tilt: rng.int(25, 50), start_angle: rng.int(0, 90), segments: 2, curl: rng.int(-10, 10), color: col });
		parts.push({ name: 'polyp', shape: rng.pick(['ellipsoid', 'dome']), parent: 'branch', at: 'tip', center: true, size: [u(W * 1.3), u(W * 1.3), u(W * 1.3)], color: glow || col, ...g });
		k.trait('branching arms', '枝分かれした形');
	} else if (type === 'sponge') {
		k.kind('sponge', '海綿');
		const W = rng.int(5, 10), H = rng.int(8, 20);
		const n = rng.chance(0.4) ? rng.int(2, 4) : 1;
		parts.push({ name: 'body', shape: 'profile', size: [W, H, W], profile: [0.75, 0.9, 1, 1, 0.95], color: col, ...(n > 1 ? { count: n, arrange: 'ring', radius: W, tilt: rng.int(8, 20) } : {}) });
		parts.push({ name: 'rim', shape: 'cylinder', parent: 'body', at: 'tip', center: true, size: [W, 1, W], color: k.col('black') });
		k.trait(n > 1 ? 'several tube-shaped bodies' : 'a tube-shaped body', n > 1 ? '複数の筒状の体' : '筒状の体');
	} else if (type === 'sea_lily') {
		k.kind('sea lily', 'ウミユリ');
		parts.push({ name: 'stem', shape: 'cylinder', size: [1, rng.int(12, 26), 1], segments: 4, curl: rng.int(-6, 6), color: col });
		parts.push({ name: 'crown', shape: 'cone', parent: 'stem', at: 'tip', size: [3, 2, 3], tip: 1.6, color: col });
		parts.push({ name: 'arm', shape: 'plane', parent: 'crown', at: 'top', axis: 'y', size: [2, rng.int(6, 12), 0], outline: 'hair', count: rng.int(5, 10), arrange: 'ring', radius: 1, tilt: rng.int(30, 60), segments: 2, curl: rng.int(10, 25), color: glow || col, ...g });
		k.trait('feathery arms on a long stem', '長い柄の先の羽のような腕');
	} else {
		k.kind('sea squirt', 'ホヤ');
		const W = rng.int(6, 12);
		parts.push({ name: 'stalk', shape: 'cylinder', size: [2, rng.int(3, 12), 2], segments: 2, color: col });
		parts.push({ name: 'head', shape: 'profile', parent: 'stalk', at: 'tip', size: [W, u(W * rng.float(0.8, 1.3)), W], profile: [0.74, 1, 1, 0.97, 0.9, 0.76, 0.5], color: col });
		parts.push({ name: 'siphon', shape: 'cylinder', parent: 'head', at: 'top', size: [2, 2, 2], count: 2, arrange: 'row', spacing: [u(W * 0.3), 0, 0], color: col, detail: 2 });
		k.trait('a round bulb on a stalk', '柄の先の丸い体');
	}
	if (glow) k.trait('glowing tips', '光る先端');
	return { spec: { orientation: 'upright', parts, texture: rng.chance(0.5) ? { pattern: rng.pick(['speckle', 'mottled', 'spots']) } : undefined }, facts: k.facts };
}

export function crab(rng) {
	const k = kit(rng);
	const { u } = k;
	const shell = k.color('body', k.pickCol(['red', 'orange', 'white', 'cream', 'brown', 'dark red', 'blue', 'beige', 'purple']));
	const W = rng.int(10, 22);
	const D = u(W * rng.float(0.6, 0.95), 4);
	const H = u(W * rng.float(0.22, 0.4), 2);
	k.kind('crab', 'カニ');
	const legCol = rng.chance(0.7) ? shell : k.col(rng.pick(['white', 'cream', 'beige', 'orange']));
	const parts = [{ name: 'carapace', shape: rng.pick(['ellipsoid', 'box', 'dome']), size: [W, H, D], color: shell, role: 'body' }];
	const nLeg = rng.pick([3, 4, 4]);
	const legL = u(W * rng.float(0.45, 0.9), 3);
	parts.push({ name: 'leg', shape: 'cylinder', parent: 'carapace', at: [1, 0.3, 0.55], axis: 'x', size: [legL, 1, 1], count: nLeg, arrange: 'row', spacing: [0, 0, u(D / (nLeg + 1), 1)], rotate: [0, 0, -rng.int(20, 45)], segments: 2, curl: -rng.int(20, 45), mirror: true, color: legCol });
	if (legL > W * 0.75) k.trait('long spindly legs', '細長い脚');
	const clawBig = rng.chance(0.35);
	parts.push({ name: 'arm', shape: 'cylinder', parent: 'carapace', at: [0.85, 0.4, 0.05], axis: '-z', size: [u(W * 0.12), u(W * 0.12), u(W * rng.float(0.25, 0.4))], rotate: [0, rng.int(15, 35), 0], mirror: true, color: legCol });
	parts.push({ name: 'claw', shape: 'box', parent: 'arm', at: 'tip', axis: '-z', size: [u(W * (clawBig ? 0.35 : 0.2)), u(W * (clawBig ? 0.3 : 0.18)), u(W * (clawBig ? 0.45 : 0.28))], mirror: true, color: rng.chance(0.3) ? k.col('white') : legCol });
	if (clawBig) k.trait('huge claws', '大きなはさみ');
	if (rng.chance(0.6)) {
		parts.push({ name: 'eye_stalk', shape: 'cylinder', parent: 'carapace', at: [0.65, 0.9, 0.05], axis: 'y', size: [1, rng.int(2, 4), 1], mirror: true, color: shell });
		parts.push({ name: 'eye', shape: 'eye', parent: 'eye_stalk', at: 'tip', size: rng.pick([1, 1.5, 2]), mirror: true });
		k.trait('eyes on stalks', '飛び出した目');
	} else parts.push({ name: 'eye', shape: 'eye', parent: 'carapace', at: [0.62, 0.9, 0], size: 1, mirror: true });
	if (rng.chance(0.3)) {
		parts.push({ name: 'spike', shape: 'cone', parent: 'carapace', at: [0.5, 1, 0.5], axis: 'y', size: [2, rng.int(2, 3), 2], count: rng.int(3, 5), arrange: 'row', spacing: [u(W / 5), 0, 0], color: shell });
		k.trait('spiky shell', 'トゲのある甲羅');
	}
	const texture = rng.chance(0.4) ? { pattern: rng.pick(['spots', 'speckle', 'mottled']) } : undefined;
	return { spec: { orientation: 'upright', parts, texture }, facts: k.facts };
}
