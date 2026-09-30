// Post-processing of generated geometry: cube budget ("Block Count") and the static
// (non animation-ready) variant that bakes every bone transform into one bone.

import { applyAffine, boneWorldTransforms, cubeWorldTransform, eulerZYXFromMatrix, round, sub3 } from './math3d.js';

/**
 * Drops the least important cubes (priority 3, then 2; smallest first) until the model has at
 * most `maxCubes` cubes, then removes bones left completely empty.
 */
export function applyBlockBudget(geometry, maxCubes) {
	const warnings = [];
	if (!maxCubes || geometry.cubes.length <= maxCubes) return { geometry, removed: [], warnings };
	const volume = (c) => Math.max(0.01, (c.to[0] - c.from[0]) || 0.1) * Math.max(0.01, (c.to[1] - c.from[1]) || 0.1) * Math.max(0.01, (c.to[2] - c.from[2]) || 0.1);
	const removable = geometry.cubes
		.map((c, i) => ({ c, i }))
		.filter((x) => x.c.priority > 1)
		.sort((a, b) => b.c.priority - a.c.priority || volume(a.c) - volume(b.c) || b.i - a.i);
	const drop = new Set();
	let count = geometry.cubes.length;
	for (const { c } of removable) {
		if (count <= maxCubes) break;
		drop.add(c.name);
		count--;
	}
	if (count > maxCubes) warnings.push(`Block Count ${maxCubes} is below the ${count} essential cubes of this creature; kept ${count}.`);
	const cubes = geometry.cubes.filter((c) => !drop.has(c.name));
	const bones = pruneEmptyBones(geometry.bones, cubes);
	return { geometry: { ...geometry, bones, cubes, glowSpots: (geometry.glowSpots || []).filter((s) => bones.some((b) => b.name === s.bone)) }, removed: [...drop], warnings };
}

/** Removes bones that have no cubes and no (remaining) child bones. The root is always kept. */
export function pruneEmptyBones(bones, cubes) {
	let list = bones.slice();
	let changed = true;
	while (changed) {
		changed = false;
		const used = new Set(cubes.map((c) => c.bone));
		const parents = new Set(list.map((b) => b.parent).filter(Boolean));
		const next = list.filter((b) => !b.parent || used.has(b.name) || parents.has(b.name) || b.role === 'body');
		if (next.length !== list.length) {
			list = next;
			changed = true;
		}
	}
	return list;
}

/**
 * Static model: all cubes moved into a single "body" bone with their full rest transform baked
 * into per-cube rotation (Blockbench / Minecraft Java export turn rotated cubes into sub-parts).
 */
export function flattenForStatic(geometry) {
	const worlds = boneWorldTransforms(geometry);
	const bones = new Map(geometry.bones.map((b) => [b.name, b]));
	const root = geometry.bones.find((b) => !b.parent);
	const bodyPivot = (geometry.bones.find((b) => b.role === 'body') || root).pivot.slice();
	const cubes = geometry.cubes.map((cube) => {
		const W = cubeWorldTransform(cube, bones.get(cube.bone), worlds.get(cube.bone));
		const c = [(cube.from[0] + cube.to[0]) / 2, (cube.from[1] + cube.to[1]) / 2, (cube.from[2] + cube.to[2]) / 2];
		const wc = applyAffine(W, c);
		const delta = sub3(wc, c);
		const rotation = eulerZYXFromMatrix(W.m).map((v) => round(v, 3));
		const rotated = rotation.some((v) => Math.abs(v) > 1e-3);
		return {
			...cube,
			bone: 'body',
			from: cube.from.map((v, i) => round(v + delta[i], 4)),
			to: cube.to.map((v, i) => round(v + delta[i], 4)),
			origin: rotated ? wc.map((v) => round(v, 4)) : bodyPivot.slice(),
			rotation: rotated ? rotation : [0, 0, 0],
		};
	});
	const glowSpots = (geometry.glowSpots || []).map((s) => {
		const bone = bones.get(s.bone);
		return { ...s, bone: 'body', pos: applyAffine(worlds.get(s.bone), sub3(s.pos, bone.pivot)).map((v) => round(v, 4)) };
	});
	return {
		...geometry,
		bones: [
			{ ...root, rotation: [0, 0, 0] },
			{ name: 'body', parent: root.name, pivot: bodyPivot, rotation: [0, 0, 0], role: 'body', side: null, meta: {} },
		],
		cubes,
		glowSpots,
	};
}
