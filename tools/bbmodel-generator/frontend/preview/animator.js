// Plays animation clips on the preview bones with Blockbench 5 semantics:
// rotation (deg) is added to the rest Euler angles, position is added, scale multiplies.
// Interpolation is the shared implementation used for the .bbmodel keyframes.

import { sampleChannel } from '/backend/animation/clip.js';

const DEG = Math.PI / 180;

export class PreviewAnimator {
	constructor() {
		this.clip = null;
		this.time = 0;
		this.playing = false;
		this.speed = 1;
		this.pauseTimer = 0;
		this.onTime = null;
	}

	setClip(clip) {
		this.clip = clip || null;
		this.time = 0;
		this.pauseTimer = 0;
	}

	get length() {
		return this.clip?.length || 0;
	}

	seek(t) {
		this.time = Math.max(0, Math.min(this.length, t));
		this.pauseTimer = 0;
		this.onTime?.(this.time);
	}

	update(dt) {
		if (!this.clip || !this.playing) return;
		if (this.pauseTimer > 0) {
			this.pauseTimer -= dt;
			if (this.pauseTimer <= 0) this.time = 0;
			return;
		}
		this.time += dt * this.speed;
		if (this.time >= this.length) {
			if (this.clip.loop === 'loop') this.time %= this.length;
			else {
				// once / hold: show the final frame briefly, then replay for preview purposes
				this.time = this.length;
				this.pauseTimer = 0.8;
			}
		}
		this.onTime?.(this.time);
	}

	/** Resets bones to rest pose and applies the current frame. */
	apply(bones) {
		for (const obj of bones.values()) {
			const { restPosition, restRotation } = obj.userData;
			obj.position.copy(restPosition);
			obj.rotation.copy(restRotation);
			obj.scale.set(1, 1, 1);
		}
		if (!this.clip) return;
		const t = this.time;
		for (const [name, channels] of Object.entries(this.clip.tracks)) {
			const obj = bones.get(name);
			if (!obj) continue;
			const { restPosition, restRotation } = obj.userData;
			if (channels.rotation) {
				const r = sampleChannel(channels.rotation, t, this.clip.loop);
				if (r) obj.rotation.set(restRotation.x + r[0] * DEG, restRotation.y + r[1] * DEG, restRotation.z + r[2] * DEG);
			}
			if (channels.position) {
				const p = sampleChannel(channels.position, t, this.clip.loop);
				if (p) obj.position.set(restPosition.x + p[0], restPosition.y + p[1], restPosition.z + p[2]);
			}
			if (channels.scale) {
				const s = sampleChannel(channels.scale, t, this.clip.loop);
				if (s) obj.scale.set(s[0] || 1e-5, s[1] || 1e-5, s[2] || 1e-5);
			}
		}
	}
}
