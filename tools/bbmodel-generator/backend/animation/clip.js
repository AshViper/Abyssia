// Animation clips in a library-neutral form.
//
// Values follow Blockbench 5 conventions (the same as the static bone rotation, three.js
// ZYX Euler): rotation in degrees added to the bone's rest rotation, position in model units
// added to the bone offset, scale multiplied. With the creature facing -Z a positive X
// rotation lifts a forward-pointing part (e.g. a jaw opens with a negative X rotation).

import { round } from '../generator/math3d.js';

export const CHANNELS = ['rotation', 'position', 'scale'];

/**
 * @typedef {{time:number, value:number[], interpolation:'linear'|'catmullrom'|'step'}} Keyframe
 * @typedef {{name:string, loop:'loop'|'once'|'hold', length:number, description?:string,
 *            tracks: Record<string, Partial<Record<'rotation'|'position'|'scale', Keyframe[]>>>}} AnimationClip
 */

const gcd = (a, b) => (b ? gcd(b, a % b) : a);
const lcm = (a, b) => (a * b) / gcd(a, b);

export class ClipBuilder {
	/**
	 * @param {string} name
	 * @param {{length:number, loop?:'loop'|'once'|'hold', geometry:{bones:Array}, description?:string}} opts
	 */
	constructor(name, { length, loop = 'loop', geometry, description = '' }) {
		this.name = name;
		this.length = length;
		this.loop = loop;
		this.description = description;
		this.boneNames = new Set(geometry.bones.map((b) => b.name));
		/** @type {Map<string, {fns: Array, keys: Keyframe[]}>} */
		this.tracks = new Map();
	}

	has(bone) {
		return this.boneNames.has(bone);
	}

	#track(bone, channel) {
		const id = `${bone}\u0000${channel}`;
		if (!this.tracks.has(id)) this.tracks.set(id, { bone, channel, fns: [], keys: [] });
		return this.tracks.get(id);
	}

	/**
	 * Periodic motion: value(t) = offset + amplitude * sin(2π (cycles·t/length + phase)).
	 * `amplitude` is a vector [x, y, z]; `cycles` must be a whole number so loops are seamless.
	 */
	wave(bone, channel, { amplitude, cycles = 1, phase = 0, offset = null, shape = 'sin' }) {
		if (!this.has(bone)) return this;
		const T = this.length;
		const amp = amplitude;
		const off = offset || (channel === 'scale' ? [0, 0, 0] : [0, 0, 0]);
		const c = Math.max(1, Math.round(cycles));
		const fn = (t) => {
			const a = 2 * Math.PI * (c * t / T + phase);
			let s;
			if (shape === 'pulse') s = Math.max(0, Math.sin(a)) ** 2; // 0..1 bumps
			else if (shape === 'cos1') s = (1 - Math.cos(a)) / 2; // 0..1..0
			else s = Math.sin(a);
			return [off[0] + amp[0] * s, off[1] + amp[1] * s, off[2] + amp[2] * s];
		};
		this.#track(bone, channel).fns.push({ fn, cycles: c });
		return this;
	}

	/** Explicit keyframes: [[time, [x,y,z], interpolation?], ...] */
	keys(bone, channel, list, interpolation = 'catmullrom') {
		if (!this.has(bone)) return this;
		const track = this.#track(bone, channel);
		for (const [time, value, interp] of list) {
			track.keys.push({ time, value: value.slice(), interpolation: interp || interpolation });
		}
		return this;
	}

	/** @returns {AnimationClip} */
	build() {
		const tracks = {};
		const T = this.length;
		for (const track of this.tracks.values()) {
			let keyframes = [];
			if (track.fns.length) {
				const cycles = track.fns.reduce((acc, f) => lcm(acc, f.cycles), 1);
				const samples = Math.min(96, cycles * 8);
				for (let i = 0; i <= samples; i++) {
					const t = (T * i) / samples;
					const v = [0, 0, 0];
					for (const f of track.fns) {
						const r = f.fn(t);
						v[0] += r[0];
						v[1] += r[1];
						v[2] += r[2];
					}
					if (track.channel === 'scale') {
						v[0] += 1;
						v[1] += 1;
						v[2] += 1;
					}
					keyframes.push({ time: round(t, 4), value: v.map((n) => round(n, 3)), interpolation: 'catmullrom' });
				}
				if (this.loop !== 'loop') keyframes[keyframes.length - 1].interpolation = 'linear';
			} else {
				keyframes = track.keys
					.slice()
					.sort((a, b) => a.time - b.time)
					.map((k) => ({ time: round(Math.min(T, Math.max(0, k.time)), 4), value: k.value.map((n) => round(n, 3)), interpolation: k.interpolation }));
				// Blockbench: the last smooth keyframe of a hold / once clip should be linear.
				if (keyframes.length && this.loop !== 'loop') keyframes[keyframes.length - 1].interpolation = 'linear';
				if (keyframes.length === 1 || keyframes.every((k) => k.interpolation !== 'catmullrom')) {
					for (const k of keyframes) if (k.interpolation === 'catmullrom') k.interpolation = 'linear';
				}
			}
			if (!keyframes.length) continue;
			tracks[track.bone] ??= {};
			tracks[track.bone][track.channel] = keyframes;
		}
		return { name: this.name, loop: this.loop, length: round(T, 4), description: this.description, tracks };
	}
}

/** Uniform Catmull-Rom exactly like three.js SplineCurve (used by Blockbench's catmullrom keyframes). */
function catmullRom(t, p0, p1, p2, p3) {
	const v0 = (p2 - p0) * 0.5;
	const v1 = (p3 - p1) * 0.5;
	const t2 = t * t, t3 = t * t2;
	return (2 * p1 - 2 * p2 + v0 + v1) * t3 + (-3 * p1 + 3 * p2 - 2 * v0 - v1) * t2 + v0 * t + p1;
}

/**
 * Samples a keyframe channel at `time` the way Blockbench's BoneAnimator.interpolate does.
 * @param {Keyframe[]} keys sorted keyframes
 */
export function sampleChannel(keys, time, loop = 'loop', length = 0) {
	if (!keys || !keys.length) return null;
	const eps = 1 / 1200;
	let before = null, after = null;
	for (const k of keys) {
		if (k.time < time) {
			if (!before || k.time > before.time) before = k;
		} else if (!after || k.time < after.time) after = k;
	}
	if (before && Math.abs(before.time - time) < eps) return before.value.slice();
	if (after && Math.abs(after.time - time) < eps) return after.value.slice();
	if (before && before.interpolation === 'step') return before.value.slice();
	if (before && !after) return before.value.slice();
	if (after && !before) return after.value.slice();
	const alpha = (time - before.time) / (after.time - before.time || 1);
	if (before.interpolation === 'linear' && (after.interpolation === 'linear' || after.interpolation === 'step')) {
		return before.value.map((v, i) => v + (after.value[i] - v) * alpha);
	}
	const i = keys.indexOf(before);
	let beforePlus = keys[i - 1];
	let afterPlus = keys[i + 2];
	if (loop === 'loop' && keys.length >= 3) {
		if (!beforePlus) beforePlus = keys[keys.length - 2];
		if (!afterPlus) afterPlus = keys[1];
	}
	void length;
	return before.value.map((v, axis) => {
		const pts = [];
		if (beforePlus) pts.push(beforePlus.value[axis]);
		pts.push(v, after.value[axis]);
		if (afterPlus) pts.push(afterPlus.value[axis]);
		// three.js SplineCurve.getPoint with duplicated endpoints
		const t = (alpha + (beforePlus ? 1 : 0)) / (pts.length - 1);
		const p = (pts.length - 1) * t;
		const ip = Math.floor(p);
		const w = p - ip;
		const P = (n) => pts[Math.min(pts.length - 1, Math.max(0, n))];
		const p0 = P(ip === 0 ? ip : ip - 1), p1 = P(ip), p2 = P(ip > pts.length - 2 ? pts.length - 1 : ip + 1), p3 = P(ip > pts.length - 3 ? pts.length - 1 : ip + 2);
		return catmullRom(w, p0, p1, p2, p3);
	});
}

/** Pose of every animated bone at `time` (for tests and the preview). */
export function samplePose(clip, time) {
	const pose = {};
	for (const [bone, channels] of Object.entries(clip.tracks)) {
		pose[bone] = {};
		for (const channel of CHANNELS) {
			if (channels[channel]) pose[bone][channel] = sampleChannel(channels[channel], time, clip.loop, clip.length);
		}
	}
	return pose;
}
