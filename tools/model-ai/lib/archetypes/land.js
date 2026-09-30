// Land creatures and generic things (so the model is not deep-sea only).
// Same part conventions as sea.js.

import { kit } from './kit.js';

const FUR = ['brown', 'beige', 'gray', 'black', 'white', 'orange', 'cream', 'dark gray'];
const GLOW = ['cyan', 'lime', 'magenta', 'yellow', 'light blue', 'orange'];

export function quadruped(rng) {
	const k = kit(rng);
	const { u } = k;
	const col = k.color('body', k.pickCol(FUR));
	const W = rng.int(5, 12);
	const H = u(W * rng.float(0.8, 1.1), 3);
	const L = u(W * rng.float(1.3, 2.2), 6);
	const legL = u(H * rng.float(0.6, 1.6), 2);
	const lw = u(W * rng.float(0.25, 0.35));
	k.kind('four-legged animal', '四足の動物');
	const parts = [{ name: 'body', shape: rng.pick(['box', 'box', 'ellipsoid']), axis: '-z', center: true, size: [W, H, L], color: col }];
	parts.push({ name: 'front_leg', shape: rng.pick(['box', 'cylinder']), parent: 'body', at: [0.8, 0.05, 0.15], axis: '-y', size: [lw, legL, lw], segments: 2, mirror: true, color: col });
	parts.push({ name: 'back_leg', shape: parts[1].shape, parent: 'body', at: [0.8, 0.05, 0.85], axis: '-y', size: [lw, legL, lw], segments: 2, mirror: true, color: col });
	if (legL > H * 1.3) k.trait('long legs', '長い脚');
	let headParent = 'body', headAt = [0.5, 0.75, 0];
	if (rng.chance(0.25)) {
		parts.push({ name: 'neck', shape: 'box', parent: 'body', at: [0.5, 0.8, 0.05], axis: 'y', rotate: [-rng.int(10, 35), 0, 0], size: [u(W * 0.4), u(H * rng.float(0.8, 1.8)), u(W * 0.4)], segments: 2, color: col });
		headParent = 'neck';
		headAt = 'tip';
		k.trait('a long neck', '長い首');
	}
	const hw = u(W * rng.float(0.6, 0.85), 3);
	parts.push({ name: 'head', shape: 'box', parent: headParent, at: headAt, axis: '-z', size: [hw, u(hw * rng.float(0.8, 1)), u(hw * rng.float(0.8, 1.1))], color: col });
	if (rng.chance(0.7)) parts.push({ name: 'snout', shape: 'box', parent: 'head', at: [0.5, 0.3, 0], axis: '-z', size: [u(hw * 0.55), u(hw * 0.45), rng.int(1, 4)], color: rng.chance(0.3) ? k.col(rng.pick(['pink', 'black', 'beige'])) : col });
	parts.push({ name: 'eye', shape: 'eye', parent: 'head', at: [1, 0.7, 0.25], size: 1, mirror: true });
	const ears = rng.pick(['none', 'pointy', 'long', 'round']);
	if (ears === 'pointy') parts.push({ name: 'ear', shape: 'box', parent: 'head', at: [0.8, 1, 0.6], axis: 'y', size: [2, rng.int(2, 3), 1], mirror: true, color: col });
	if (ears === 'long') {
		parts.push({ name: 'ear', shape: 'box', parent: 'head', at: [0.75, 1, 0.6], axis: 'y', size: [2, rng.int(4, 8), 1], rotate: [rng.int(0, 20), 0, 0], mirror: true, color: col });
		k.trait('long ears', '長い耳');
	}
	if (ears === 'round') parts.push({ name: 'ear', shape: 'ellipsoid', parent: 'head', at: [0.85, 1, 0.6], axis: 'y', size: [2, 2, 1], mirror: true, color: col });
	if (rng.chance(0.35)) {
		parts.push({ name: 'horn', shape: 'cone', parent: 'head', at: [0.75, 1, 0.4], axis: 'y', size: [u(hw * 0.2), rng.int(2, 7), u(hw * 0.2)], tip: 0.2, rotate: [-rng.int(0, 30), 0, -rng.int(0, 30)], segments: 2, curl: -rng.int(0, 25), mirror: true, color: k.col(rng.pick(['beige', 'cream', 'gray', 'black'])) });
		k.kind('horned beast', '角のある獣');
		k.trait('curved horns', '曲がった角');
	}
	if (rng.chance(0.8)) {
		const long = rng.chance(0.4);
		parts.push({ name: 'tail', shape: 'cylinder', parent: 'body', at: [0.5, 0.8, 1], axis: 'z', rotate: [rng.int(-45, 10), 0, 0], size: [u(W * 0.18), u(W * 0.18), u(L * (long ? rng.float(0.6, 1) : rng.float(0.15, 0.4)))], taper: 0.6, segments: long ? 3 : 1, curl: long ? rng.int(-20, 5) : undefined, color: col });
		if (long) k.trait('a long tail', '長い尾');
	}
	return { spec: { orientation: 'horizontal', parts, texture: rng.chance(0.4) ? { pattern: rng.pick(['spots', 'stripes', 'mottled', 'speckle']) } : undefined }, facts: k.facts };
}

export function bird(rng) {
	const k = kit(rng);
	const { u } = k;
	const col = k.color('body', k.pickCol(['white', 'black', 'brown', 'red', 'blue', 'yellow', 'gray', 'green', 'orange']));
	const beak = k.col(rng.pick(['orange', 'yellow', 'black', 'beige']));
	const W = rng.int(4, 10);
	const H = u(W * rng.float(0.9, 1.1), 3);
	const L = u(W * rng.float(1.3, 1.8), 5);
	k.kind('bird', '鳥');
	const parts = [{ name: 'body', shape: 'ellipsoid', axis: '-z', center: true, size: [W, H, L], color: col }];
	const hs = u(W * rng.float(0.6, 0.8), 3);
	parts.push({ name: 'head', shape: rng.pick(['ellipsoid', 'box']), parent: 'body', at: [0.5, 0.85, 0.08], axis: 'y', size: [hs, hs, hs], color: col });
	const bl = u(hs * rng.float(0.4, 1.3));
	parts.push({ name: 'beak', shape: 'cone', parent: 'head', at: [0.5, 0.4, 0], axis: '-z', size: [u(hs * 0.35), u(hs * 0.3), bl], tip: 0.3, color: beak });
	if (bl > hs) k.trait('a long beak', '長いくちばし');
	parts.push({ name: 'eye', shape: 'eye', parent: 'head', at: [1, 0.65, 0.35], size: 1, mirror: true });
	const spread = rng.chance(0.4);
	if (spread) {
		parts.push({ name: 'wing', shape: 'plane', parent: 'body', at: [1, 0.7, 0.4], axis: 'x', size: [u(W * rng.float(1.5, 3)), 0, u(L * rng.float(0.4, 0.6))], outline: rng.pick(['leaf', 'sail', 'round']), segments: 2, mirror: true, color: col });
		k.trait('spread wings', '広げた翼');
	} else parts.push({ name: 'wing', shape: 'plane', parent: 'body', at: [1, 0.6, 0.45], axis: 'z', size: [0, u(H * 0.6), u(L * 0.8)], outline: 'leaf', mirror: true, color: col });
	parts.push({ name: 'tail', shape: 'plane', parent: 'body', at: [0.5, 0.6, 1], axis: 'z', size: [u(W * 0.7), 0, u(L * rng.float(0.3, 0.8))], outline: 'fan', rotate: [rng.int(-20, 10), 0, 0], color: col });
	parts.push({ name: 'leg', shape: 'cylinder', parent: 'body', at: [0.7, 0.05, 0.55], axis: '-y', size: [1, u(H * rng.float(0.3, 0.9)), 1], mirror: true, color: beak });
	if (rng.chance(0.3)) {
		parts.push({ name: 'crest', shape: 'plane', parent: 'head', at: [0.5, 1, 0.4], axis: 'y', size: [0, rng.int(2, 4), rng.int(3, 5)], outline: 'frill', color: k.col(rng.pick(['red', 'yellow', 'orange'])) });
		k.trait('a crest on its head', '頭のとさか');
	}
	return { spec: { orientation: 'horizontal', parts }, facts: k.facts };
}

export function insect(rng) {
	const k = kit(rng);
	const { u } = k;
	const type = rng.pick(['beetle', 'bee', 'butterfly', 'ant', 'firefly']);
	const col = k.color('body', k.pickCol(type === 'bee' ? ['yellow', 'orange'] : type === 'butterfly' ? ['black', 'brown', 'dark gray'] : ['black', 'brown', 'dark red', 'green', 'blue', 'red']));
	const W = rng.int(3, 6);
	const names = { beetle: ['beetle', '甲虫'], bee: ['bee', 'ハチ'], butterfly: ['butterfly', 'チョウ'], ant: ['ant', 'アリ'], firefly: ['firefly', 'ホタル'] };
	k.kind(...names[type]);
	const AL = u(W * (type === 'ant' ? 1.3 : rng.float(1.5, 2.5)), 3);
	const parts = [{ name: 'thorax', shape: 'ellipsoid', axis: '-z', center: true, size: [W, u(W * 0.9), u(W * 1.2)], color: col, role: 'body' }];
	parts.push({ name: 'abdomen', shape: rng.pick(['ellipsoid', 'profile']), parent: 'thorax', at: 'back', axis: 'z', size: [u(W * (type === 'beetle' ? 1.4 : 1.1)), u(W * 1.0), AL], color: col });
	if (parts[1].shape === 'profile') parts[1].profile = [0.7, 1, 1, 0.8, 0.4];
	parts.push({ name: 'head', shape: 'ellipsoid', parent: 'thorax', at: 'front', axis: '-z', size: [u(W * 0.8), u(W * 0.8), u(W * 0.7)], color: col });
	parts.push({ name: 'eye', shape: 'eye', parent: 'head', at: [1, 0.6, 0.4], size: 1, mirror: true });
	parts.push({ name: 'antenna', shape: 'cylinder', parent: 'head', at: [0.7, 0.9, 0.1], axis: '-z', rotate: [rng.int(20, 50), rng.int(10, 30), 0], size: [1, 1, u(W * rng.float(0.8, 2))], segments: 2, curl: rng.int(0, 20), mirror: true, color: col });
	parts.push({ name: 'leg', shape: 'cylinder', parent: 'thorax', at: [1, 0.2, 0.5], axis: 'x', size: [u(W * rng.float(1, 1.8)), 1, 1], count: 3, arrange: 'row', spacing: [0, 0, u(W * 0.35)], rotate: [0, 0, -rng.int(25, 45)], segments: 2, curl: -rng.int(30, 55), mirror: true, color: col });
	if (type === 'butterfly' || type === 'bee' || type === 'firefly') {
		const wc = type === 'butterfly' ? k.color('wing', k.pickCol(['blue', 'orange', 'yellow', 'white', 'purple', 'cyan', 'red'])) : k.col('light blue');
		parts.push({ name: 'wing', shape: 'plane', parent: 'thorax', at: [0.8, 1, 0.4], axis: 'x', rotate: [0, 0, rng.int(10, 40)], size: [u(W * (type === 'butterfly' ? rng.float(2.5, 4) : 1.8)), 0, u(W * (type === 'butterfly' ? 2.5 : 1.5))], outline: rng.pick(['leaf', 'round', 'sail']), mirror: true, color: wc });
		k.trait(type === 'butterfly' ? 'large colourful wings' : 'clear wings', type === 'butterfly' ? '大きな色鮮やかな羽' : '透明な羽');
	}
	if (type === 'beetle' && rng.chance(0.6)) {
		parts.push({ name: 'horn', shape: 'cone', parent: 'head', at: [0.5, 0.8, 0], axis: '-z', rotate: [rng.int(20, 45), 0, 0], size: [2, 2, u(W * rng.float(1, 2))], tip: 0.3, segments: 2, curl: rng.int(10, 30), color: col });
		k.trait('a big horn', '大きな角');
	}
	if (type === 'firefly') {
		const g = k.color('glow', k.pickCol(['yellow', 'lime'], 8));
		parts.push({ name: 'light', shape: 'ellipsoid', parent: 'abdomen', at: 'tip', center: true, size: [u(W * 0.9), u(W * 0.8), u(W * 0.8)], color: g, glow: 1.5, glow_color: g });
		k.trait('a glowing tail', '光るお尻');
	}
	if (type === 'bee') {
		parts.push({ name: 'stinger', shape: 'cone', parent: 'abdomen', at: 'tip', axis: 'z', size: [1, 1, 2], tip: 0.2, color: k.col('black') });
		k.trait('a stinger', '毒針');
	}
	const texture = type === 'bee' ? { pattern: 'bands' } : type === 'beetle' && rng.chance(0.4) ? { pattern: 'spots' } : undefined;
	return { spec: { orientation: 'horizontal', parts, texture }, facts: k.facts };
}

export function spider(rng) {
	const k = kit(rng);
	const { u } = k;
	const col = k.color('body', k.pickCol(['black', 'brown', 'dark red', 'dark gray', 'beige', 'purple']));
	const W = rng.int(4, 9);
	k.kind('spider', 'クモ');
	const parts = [{ name: 'body', shape: 'ellipsoid', axis: '-z', center: true, size: [W, u(W * 0.7), W], color: col }];
	const big = rng.float(1.2, 1.9);
	parts.push({ name: 'abdomen', shape: 'ellipsoid', parent: 'body', at: 'back', axis: 'z', size: [u(W * big), u(W * big * 0.9), u(W * big * 1.1)], color: col });
	parts.push({ name: 'eye', shape: 'eye', parent: 'body', at: [0.6, 0.8, 0], size: 1, count: 2, arrange: 'row', spacing: [0, 1, 0], mirror: true });
	const legL = u(W * rng.float(1.2, 2.4), 4);
	parts.push({ name: 'leg', shape: 'cylinder', parent: 'body', at: [1, 0.4, 0.5], axis: 'x', size: [legL, 1, 1], count: 4, arrange: 'row', spacing: [0, 0, u(W * 0.22)], rotate: [0, 0, rng.int(15, 40)], segments: 2, curl: -rng.int(50, 80), mirror: true, color: col });
	if (legL > W * 1.9) k.trait('very long legs', 'とても長い脚');
	if (rng.chance(0.5)) {
		parts.push({ name: 'fang', shape: 'cone', parent: 'body', at: [0.6, 0.2, 0], axis: '-y', size: [1, 2, 1], tip: 0.3, mirror: true, color: k.col('black') });
		k.trait('fangs', '牙');
	}
	return { spec: { orientation: 'horizontal', parts, texture: rng.chance(0.5) ? { pattern: rng.pick(['spots', 'stripes', 'bands', 'speckle']) } : undefined }, facts: k.facts };
}

export function biped(rng) {
	const k = kit(rng);
	const { u } = k;
	const type = rng.pick(['golem', 'robot', 'humanoid', 'goblin']);
	const palettes = { golem: ['gray', 'dark gray', 'brown', 'green', 'beige'], robot: ['light gray', 'gray', 'blue', 'white', 'orange'], humanoid: ['beige', 'brown', 'cream', 'pink'], goblin: ['green', 'lime', 'dark green', 'gray'] };
	const names = { golem: ['golem', 'ゴーレム'], robot: ['robot', 'ロボット'], humanoid: ['humanoid', '人型の生き物'], goblin: ['goblin', 'ゴブリン'] };
	k.kind(...names[type]);
	const col = k.color('body', k.pickCol(palettes[type]));
	const W = rng.int(6, 12);
	const H = u(W * rng.float(0.9, 1.4), 6);
	const D = u(W * rng.float(0.5, 0.7), 3);
	const hs = u(W * (type === 'goblin' ? rng.float(0.8, 1) : rng.float(0.55, 0.8)), 3);
	const parts = [{ name: 'torso', shape: 'box', size: [W, H, D], color: col, role: 'body' }];
	parts.push({ name: 'head', shape: 'box', parent: 'torso', at: 'top', axis: 'y', size: [hs, hs, hs], color: col });
	const glow = type === 'robot' || rng.chance(0.2) ? k.color('glow', k.pickCol(GLOW, 8)) : null;
	parts.push({ name: 'eye', shape: 'eye', parent: 'head', at: [0.72, 0.6, 0], size: 1, mirror: true, ...(glow ? { glow: 1.5, glow_color: glow } : {}) });
	if (glow) k.trait('glowing eyes', '光る目');
	const armL = u(H * (type === 'golem' ? rng.float(1.1, 1.5) : rng.float(0.8, 1.1)), 4);
	const aw = u(W * (type === 'golem' ? rng.float(0.3, 0.45) : rng.float(0.2, 0.3)));
	parts.push({ name: 'arm', shape: 'box', parent: 'torso', at: [1, 0.95, 0.5], axis: '-y', size: [aw, armL, aw], segments: 2, offset: [aw / 2, 0, 0], mirror: true, color: col });
	if (type === 'golem') k.trait('huge heavy arms', '巨大で重い腕');
	parts.push({ name: 'leg', shape: 'box', parent: 'torso', at: [0.75, 0, 0.5], axis: '-y', size: [u(W * 0.35), u(H * rng.float(0.6, 1.1)), u(D * 0.9)], segments: 2, mirror: true, color: col });
	if (type === 'robot' && rng.chance(0.7)) {
		parts.push({ name: 'antenna', shape: 'cylinder', parent: 'head', at: 'top', axis: 'y', size: [1, rng.int(2, 5), 1], color: col });
		parts.push({ name: 'antenna_orb', shape: 'ellipsoid', parent: 'antenna', at: 'tip', center: true, size: [2, 2, 2], color: glow, glow: 1.5, glow_color: glow });
		k.trait('an antenna', 'アンテナ');
	}
	if (type === 'golem' && rng.chance(0.5)) {
		const c = k.color('core', k.pickCol(GLOW, 8));
		parts.push({ name: 'core', shape: 'box', parent: 'torso', at: [0.5, 0.65, 0], size: [u(W * 0.3), u(W * 0.3), 1], color: c, glow: 1.5, glow_color: c });
		k.trait('a glowing core in its chest', '胸の光るコア');
	}
	if (type === 'goblin' || (type === 'humanoid' && rng.chance(0.3))) {
		parts.push({ name: 'ear', shape: 'box', parent: 'head', at: [1, 0.7, 0.5], axis: 'x', size: [rng.int(2, 4), 2, 1], rotate: [0, 0, rng.int(10, 30)], mirror: true, color: col });
		k.trait('pointed ears', '尖った耳');
	}
	if (rng.chance(0.2)) {
		parts.push({ name: 'horn', shape: 'cone', parent: 'head', at: [0.8, 1, 0.5], axis: 'y', size: [2, rng.int(2, 5), 2], tip: 0.2, mirror: true, color: k.col(rng.pick(['beige', 'black', 'red'])) });
		k.trait('horns', '角');
	}
	return { spec: { orientation: 'upright', parts, texture: type === 'golem' ? { pattern: rng.pick(['mottled', 'speckle']) } : undefined }, facts: k.facts };
}

export function slime(rng) {
	const k = kit(rng);
	const { u } = k;
	const col = k.color('body', k.pickCol(['lime', 'green', 'cyan', 'pink', 'purple', 'blue', 'orange', 'red']));
	const S = rng.int(6, 16);
	k.kind('slime', 'スライム');
	const parts = [{ name: 'body', shape: rng.pick(['box', 'dome', 'ellipsoid']), size: [S, u(S * rng.float(0.7, 1)), S], color: col }];
	parts.push({ name: 'eye', shape: 'eye', parent: 'body', at: [0.7, 0.65, 0], size: u(S * rng.float(0.12, 0.25)), mirror: true });
	if (rng.chance(0.5)) parts.push({ name: 'mouth', shape: 'box', parent: 'body', at: [0.5, 0.35, 0], size: [u(S * 0.3), 1, 1], color: k.col('dark gray') });
	if (rng.chance(0.4)) {
		const c = k.col(rng.pick(['yellow', 'red', 'white', 'dark green']));
		parts.push({ name: 'core', shape: 'box', parent: 'body', at: 'center', center: true, size: [u(S * 0.35), u(S * 0.35), u(S * 0.35)], color: c, detail: 2 });
		k.trait('a core inside', '中に見える核');
	}
	if (rng.chance(0.3)) {
		parts.push({ name: 'crown', shape: 'cone', parent: 'body', at: 'top', axis: 'y', size: [2, 3, 2], count: rng.int(3, 5), arrange: 'row', spacing: [2, 0, 0], tip: 0.2, color: k.col('yellow') });
		k.trait('a crown', '王冠');
	}
	if (rng.chance(0.3)) {
		parts[0].glow = 0.6;
		parts[0].glow_color = col;
		k.trait('a soft glow', 'ほのかな光');
	}
	return { spec: { orientation: 'upright', parts }, facts: k.facts };
}

export function mushroom(rng) {
	const k = kit(rng);
	const { u } = k;
	const cap = k.color('body', k.pickCol(['red', 'brown', 'orange', 'beige', 'purple', 'white', 'yellow', 'blue']));
	const W = rng.int(2, 6);
	const H = rng.int(4, 16);
	const C = u(W * rng.float(2, 4), 5);
	const n = rng.chance(0.3) ? rng.int(2, 4) : 1;
	k.kind('mushroom', 'キノコ');
	const glow = rng.chance(0.25) ? k.color('glow', k.pickCol(GLOW, 8)) : null;
	const parts = [{ name: 'stem', shape: 'cylinder', size: [W, H, W], taper: Math.round(rng.float(0.75, 1) * 100) / 100, curl: rng.int(-8, 8), segments: n > 1 ? 1 : 2, color: k.col(rng.pick(['white', 'cream', 'beige'])), ...(n > 1 ? { count: n, arrange: 'ring', radius: u(C * 0.45), tilt: rng.int(8, 20) } : {}) }];
	parts.push({ name: 'cap', shape: rng.pick(['dome', 'profile', 'ellipsoid']), parent: 'stem', at: 'tip', size: [C, u(C * rng.float(0.3, 0.6)), C], color: cap, ...(glow ? { glow: 1, glow_color: glow } : {}) });
	if (parts[1].shape === 'profile') parts[1].profile = [1, 0.95, 0.8, 0.5, 0.2];
	if (parts[1].shape === 'ellipsoid') parts[1].center = true;
	if (n > 1) k.trait('a cluster of caps', '寄り添う複数のかさ');
	if (C > W * 3.2) k.trait('a wide cap', '大きく広がったかさ');
	if (glow) k.trait('a glowing cap', '光るかさ');
	return { spec: { orientation: 'upright', parts, texture: rng.chance(0.5) ? { pattern: 'spots' } : undefined }, facts: k.facts };
}

export function plant(rng) {
	const k = kit(rng);
	const { u } = k;
	const type = rng.pick(['kelp', 'flower', 'tree', 'cactus']);
	const green = k.color('body', k.pickCol(type === 'kelp' ? ['brown', 'green', 'dark green', 'beige'] : ['green', 'dark green', 'lime']));
	const parts = [];
	if (type === 'kelp') {
		k.kind('kelp', '海藻');
		const L = rng.int(16, 40);
		const n = rng.int(3, 6);
		parts.push({ name: 'stem', shape: 'cylinder', size: [1, L, 1], segments: rng.int(4, 6), curl: rng.int(-6, 6), color: green });
		parts.push({ name: 'blade', shape: 'plane', parent: 'stem', at: [0.5, 0.25, 0.5], axis: 'x', size: [rng.int(3, 6), rng.int(2, 4), 0], outline: 'leaf', count: n, arrange: 'row', spacing: [0, u(L * 0.7 / n), 0], rotate: [0, 0, rng.int(10, 35)], mirror: true, color: green });
		k.trait('long wavy blades', '長く揺れる葉');
	} else if (type === 'flower') {
		k.kind('flower', '花');
		const L = rng.int(6, 16);
		const petal = k.color('petal', k.pickCol(['red', 'pink', 'yellow', 'white', 'purple', 'blue', 'orange']));
		parts.push({ name: 'stem', shape: 'cylinder', size: [1, L, 1], segments: 2, curl: rng.int(-6, 6), color: green });
		parts.push({ name: 'leaf', shape: 'plane', parent: 'stem', at: [1, 0.3, 0.5], axis: 'x', size: [rng.int(2, 4), 0, 2], outline: 'leaf', rotate: [0, 0, rng.int(10, 30)], mirror: true, color: green });
		parts.push({ name: 'center', shape: 'ellipsoid', parent: 'stem', at: 'tip', center: true, size: [2, 2, 2], color: k.col(rng.pick(['yellow', 'brown', 'orange'])) });
		parts.push({ name: 'petal', shape: 'plane', parent: 'center', at: 'center', axis: 'y', size: [rng.int(2, 3), rng.int(3, 5), 0], outline: rng.pick(['leaf', 'round']), count: rng.int(5, 8), arrange: 'ring', radius: 1, tilt: rng.int(50, 80), color: petal });
		k.trait('bright petals', '鮮やかな花びら');
	} else if (type === 'tree') {
		k.kind('tree', '木');
		const W = rng.int(2, 5);
		const H = rng.int(8, 24);
		parts.push({ name: 'trunk', shape: 'cylinder', size: [W, H, W], taper: 0.8, segments: 2, color: k.col(rng.pick(['brown', 'dark gray', 'beige'])) });
		const C = u(W * rng.float(3, 5), 8);
		parts.push({ name: 'crown', shape: rng.pick(['ellipsoid', 'dome', 'cone', 'profile']), parent: 'trunk', at: 'tip', center: rng.chance(0.5), size: [C, u(C * rng.float(0.7, 1.4)), C], color: green });
		if (parts[1].shape === 'profile') parts[1].profile = [0.7, 1, 0.9, 0.6, 0.3];
		if (parts[1].shape === 'cone') (parts[1].tip = 0.1), k.trait('a pointed crown', '尖った樹冠');
		else k.trait('a round leafy crown', '丸く茂った樹冠');
	} else {
		k.kind('cactus', 'サボテン');
		const W = rng.int(3, 6);
		const H = rng.int(8, 20);
		parts.push({ name: 'trunk', shape: 'cylinder', size: [W, H, W], segments: 2, color: green });
		if (rng.chance(0.7)) {
			parts.push({ name: 'arm', shape: 'box', parent: 'trunk', at: [1, rng.float(0.35, 0.6), 0.5], axis: 'x', size: [rng.int(2, 4), u(W * 0.7), u(W * 0.7)], mirror: rng.chance(0.6) || undefined, color: green });
			parts.push({ name: 'arm_tip', shape: 'cylinder', parent: 'arm', at: 'tip', axis: 'y', size: [u(W * 0.7), rng.int(3, 7), u(W * 0.7)], mirror: parts.at(-1).mirror, color: green });
			k.trait('upturned arms', '上に伸びる腕');
		}
		if (rng.chance(0.3)) {
			parts.push({ name: 'flower', shape: 'dome', parent: 'trunk', at: 'tip', size: [2, 2, 2], color: k.col(rng.pick(['pink', 'red', 'yellow'])) });
			k.trait('a flower on top', 'てっぺんの花');
		}
	}
	return { spec: { orientation: 'upright', parts, texture: type === 'cactus' ? { pattern: 'speckle' } : undefined }, facts: k.facts };
}
