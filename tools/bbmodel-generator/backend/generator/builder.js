// ModelBuilder: the small API every template uses to emit bones and cubes.
//
// Coordinates are Blockbench model units (1 unit = 1/16 block), Y up, the creature
// faces north (-Z) and its right side is +X (east). Like Blockbench, positions are
// stored in absolute model space ignoring parent rotations; a child built "straight"
// inside a rotated parent simply follows that rotation when displayed.

import { applyAffine, boneWorldTransforms, cubeWorldTransform, round } from './math3d.js';

const r4 = (v) => round(v, 4);

/** Modelling rule: these small parts are always a single flat plane (zero thickness), never a box. */
export const PLANE_MATERIALS = new Set(['teeth', 'setae']);
const AXIS = { x: 0, y: 1, z: 2 };
const vec = (v) => [r4(v[0]), r4(v[1]), r4(v[2])];

/**
 * @typedef {Object} Bone
 * @property {string} name
 * @property {string|null} parent
 * @property {number[]} pivot     absolute pivot (Blockbench group origin)
 * @property {number[]} rotation  static rotation, degrees, ZYX
 * @property {string} role        semantic role: root, body, head, jaw, eye, fin, tail, lure, leg, arm, ...
 * @property {string|null} side   'left' | 'right' | null
 * @property {object} meta        data for animation generators (chain index, phase...)
 */

/**
 * @typedef {Object} Cube
 * @property {string} id
 * @property {string} name
 * @property {string} bone
 * @property {number[]} from
 * @property {number[]} to
 * @property {number[]} origin
 * @property {number[]} rotation
 * @property {string} material               material key used by the texture painter
 * @property {Record<string,string>} faceMaterials per-face material overrides
 * @property {object|null} shape             silhouette cut out of the texture alpha (fins, plumes)
 * @property {object|null} glow              whole-cube bioluminescence
 * @property {number} priority               1 = essential, 2 = detail, 3 = fine detail (dropped first by the cube budget)
 * @property {boolean} mirror                Box UV mirror flag
 * @property {string|null} uvShare           name of the cube whose UV region this cube re-uses (mirrored)
 * @property {object} meta
 */

export class ModelBuilder {
	constructor() {
		/** @type {Map<string, Bone>} */
		this.bones = new Map();
		/** @type {Cube[]} */
		this.cubes = [];
		this.cubeNames = new Set();
		/** Surface light spots (photophores), stored in the owning bone's unrotated frame. */
		this.glowSpots = [];
	}

	/** Adds a bone. Names must be unique (they become Blockbench group / ModelPart names). */
	bone(name, { parent = null, pivot = [0, 0, 0], rotation = [0, 0, 0], role = 'part', side = null, meta = {} } = {}) {
		if (!/^[a-z0-9_]+$/.test(name)) throw new Error(`Invalid bone name "${name}" (use a-z, 0-9, _)`);
		if (this.bones.has(name)) throw new Error(`Duplicate bone name "${name}"`);
		if (parent && !this.bones.has(parent)) throw new Error(`Bone "${name}" references unknown parent "${parent}"`);
		this.bones.set(name, { name, parent, pivot: vec(pivot), rotation: vec(rotation), role, side, meta: { ...meta } });
		return name;
	}

	hasBone(name) {
		return this.bones.has(name);
	}

	getBone(name) {
		return this.bones.get(name);
	}

	/**
	 * Adds a cuboid to a bone.
	 * Accepts {from, to}, {from, size} or {center, size}. Sizes should be whole units
	 * (0 is allowed for flat fins) so Box UV maps cleanly.
	 */
	box(boneName, name, opts) {
		const bone = this.bones.get(boneName);
		if (!bone) throw new Error(`Cube "${name}" references unknown bone "${boneName}"`);
		let from, to;
		if (opts.from && opts.to) {
			from = opts.from.slice();
			to = opts.to.slice();
		} else if (opts.size && opts.from) {
			from = opts.from.slice();
			to = [from[0] + opts.size[0], from[1] + opts.size[1], from[2] + opts.size[2]];
		} else if (opts.size && opts.center) {
			from = [opts.center[0] - opts.size[0] / 2, opts.center[1] - opts.size[1] / 2, opts.center[2] - opts.size[2] / 2];
			to = [from[0] + opts.size[0], from[1] + opts.size[1], from[2] + opts.size[2]];
		} else {
			throw new Error(`Cube "${name}" needs {from,to}, {from,size} or {center,size}`);
		}
		for (let i = 0; i < 3; i++) {
			if (from[i] > to[i]) [from[i], to[i]] = [to[i], from[i]];
		}
		// Collapse to a plane through the centre (default for teeth / setae: facing forward, zero Z thickness)
		const plane = opts.plane ?? (PLANE_MATERIALS.has(opts.material) ? 'z' : null);
		if (plane) {
			const a = AXIS[plane];
			const c = (from[a] + to[a]) / 2;
			from[a] = c;
			to[a] = c;
		}
		const rotation = opts.rotation ? opts.rotation.slice() : [0, 0, 0];
		const rotated = rotation.some((v) => Math.abs(v) > 1e-9);
		const origin = opts.origin
			? opts.origin.slice()
			: rotated
				? [(from[0] + to[0]) / 2, (from[1] + to[1]) / 2, (from[2] + to[2]) / 2]
				: bone.pivot.slice();
		const uniqueName = this.#uniqueCubeName(name || boneName);
		const cube = {
			id: uniqueName,
			name: uniqueName,
			bone: boneName,
			from: vec(from),
			to: vec(to),
			origin: vec(origin),
			rotation: vec(rotation),
			material: opts.material || 'skin',
			faceMaterials: opts.faces ? { ...opts.faces } : {},
			shape: opts.shape || null,
			glow: opts.glow || null,
			priority: opts.priority ?? 1,
			mirror: !!opts.mirror,
			uvShare: opts.uvShare || null,
			meta: { ...(opts.meta || {}) },
		};
		this.cubes.push(cube);
		return cube;
	}

	/** Adds a bioluminescent spot (photophore) at `pos` on a bone's surface. */
	spot(boneName, pos, { radius = 0.7, intensity = 1, color = 'glow' } = {}) {
		if (!this.bones.has(boneName)) throw new Error(`Glow spot references unknown bone "${boneName}"`);
		this.glowSpots.push({ bone: boneName, pos: vec(pos), radius, intensity, color });
	}

	#uniqueCubeName(base) {
		let name = base;
		let i = 2;
		while (this.cubeNames.has(name)) name = `${base}_${i++}`;
		this.cubeNames.add(name);
		return name;
	}

	/** Moves the whole model. */
	translate(offset) {
		const [dx, dy, dz] = offset;
		for (const bone of this.bones.values()) bone.pivot = vec([bone.pivot[0] + dx, bone.pivot[1] + dy, bone.pivot[2] + dz]);
		const spans = new Set(); // meta.span (parts template) is shared by a part's cubes: move each once
		for (const cube of this.cubes) {
			cube.from = vec([cube.from[0] + dx, cube.from[1] + dy, cube.from[2] + dz]);
			cube.to = vec([cube.to[0] + dx, cube.to[1] + dy, cube.to[2] + dz]);
			cube.origin = vec([cube.origin[0] + dx, cube.origin[1] + dy, cube.origin[2] + dz]);
			const sp = cube.meta?.span;
			if (sp && !spans.has(sp)) {
				spans.add(sp);
				sp.start += offset[sp.axis];
			}
		}
		for (const spot of this.glowSpots) spot.pos = vec([spot.pos[0] + dx, spot.pos[1] + dy, spot.pos[2] + dz]);
	}

	/** World space bounds (rest pose, static rotations applied) of the selected cubes. */
	bounds(filter = null) {
		const geometry = this.build();
		return geometryBounds(geometry, filter);
	}

	/**
	 * Places the creature on the ground: lowest point at y = 0 and the horizontal centre of the
	 * selected cubes on x = z = 0 (Minecraft centres an entity's hitbox on its position).
	 */
	ground({ centerFilter = null, snap = 1 } = {}) {
		const all = this.bounds();
		const center = centerFilter ? this.bounds(centerFilter) : all;
		const snapTo = (v) => Math.round(v / snap) * snap;
		const dz = -snapTo((center.min[2] + center.max[2]) / 2);
		const dy = -all.min[1];
		this.translate([0, r4(dy), dz]);
		// The root bone sits at the entity origin (it is never rotated at rest, so this moves nothing).
		for (const bone of this.bones.values()) if (!bone.parent) bone.pivot = [0, 0, 0];
	}

	build() {
		return {
			bones: [...this.bones.values()].map((b) => ({ ...b, pivot: b.pivot.slice(), rotation: b.rotation.slice(), meta: { ...b.meta } })),
			cubes: this.cubes.map((c) => ({
				...c,
				from: c.from.slice(),
				to: c.to.slice(),
				origin: c.origin.slice(),
				rotation: c.rotation.slice(),
				faceMaterials: { ...c.faceMaterials },
				meta: { ...c.meta },
			})),
			glowSpots: this.glowSpots.map((g) => ({ ...g, pos: g.pos.slice() })),
		};
	}
}

/** Corner points of a cube in world space (rest pose). */
export function cubeCorners(cube, bone, boneWorld) {
	const W = cubeWorldTransform(cube, bone, boneWorld);
	const pts = [];
	for (let i = 0; i < 8; i++) {
		pts.push(applyAffine(W, [
			i & 1 ? cube.to[0] : cube.from[0],
			i & 2 ? cube.to[1] : cube.from[1],
			i & 4 ? cube.to[2] : cube.from[2],
		]));
	}
	return pts;
}

/** World space bounding box of a geometry (optionally a filtered subset of cubes). */
export function geometryBounds(geometry, filter = null) {
	const worlds = boneWorldTransforms(geometry);
	const bones = new Map(geometry.bones.map((b) => [b.name, b]));
	const min = [Infinity, Infinity, Infinity];
	const max = [-Infinity, -Infinity, -Infinity];
	for (const cube of geometry.cubes) {
		if (filter && !filter(cube)) continue;
		const bone = bones.get(cube.bone);
		for (const p of cubeCorners(cube, bone, worlds.get(cube.bone))) {
			for (let i = 0; i < 3; i++) {
				if (p[i] < min[i]) min[i] = p[i];
				if (p[i] > max[i]) max[i] = p[i];
			}
		}
	}
	if (min[0] === Infinity) return { min: [0, 0, 0], max: [0, 0, 0], size: [0, 0, 0] };
	return { min, max, size: [max[0] - min[0], max[1] - min[1], max[2] - min[2]] };
}
