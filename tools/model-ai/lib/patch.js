// Part-addressed JSON Patch: RFC 6902 add / remove / replace where "/parts/<name>" selects
// the part with that name (not an index), so edits stay short and do not depend on list order.
//   {"op":"replace","path":"/parts/tail_fin/size","value":[0,8,6]}
//   {"op":"add","path":"/parts/-","value":{...new part...}}      (append a part)
//   {"op":"remove","path":"/parts/dorsal_fin"}                  (also removes its descendants)
//   {"op":"replace","path":"/texture/pattern","value":"spots"}
//   {"op":"add","path":"/parts/tentacle/glow","value":1}

import { canonicalSpec, clone, descendants, sameValue } from './spec.js';

const decode = (s) => s.replace(/~1/g, '/').replace(/~0/g, '~');

export class PatchError extends Error {}

/** Applies ops to a copy of spec; throws PatchError with the failing op index. */
export function applyPatch(spec, ops) {
	if (!Array.isArray(ops)) throw new PatchError('patch must be a JSON array of operations');
	let s = clone(spec);
	ops.forEach((op, i) => {
		try {
			s = applyOp(s, op);
		} catch (err) {
			throw new PatchError(`op[${i}] ${op?.op} ${op?.path}: ${err.message}`);
		}
	});
	return s;
}

function applyOp(s, op) {
	if (!op || typeof op !== 'object') throw new Error('operation must be an object');
	if (!['add', 'remove', 'replace'].includes(op.op)) throw new Error('op must be add, remove or replace');
	if (typeof op.path !== 'string' || !op.path.startsWith('/')) throw new Error('path must start with "/"');
	if (op.op !== 'remove' && op.value === undefined) throw new Error('missing value');
	const segs = op.path.slice(1).split('/').map(decode);

	if (segs[0] === 'parts') {
		if (segs.length === 1) {
			if (op.op === 'remove') throw new Error('cannot remove all parts');
			if (!Array.isArray(op.value)) throw new Error('/parts value must be a list');
			s.parts = clone(op.value);
			return s;
		}
		if (segs[1] === '-') {
			if (op.op !== 'add' || segs.length !== 2) throw new Error('"/parts/-" only supports add of a whole part');
			if (s.parts.some((p) => p.name === op.value?.name)) throw new Error(`part "${op.value?.name}" already exists`);
			s.parts.push(clone(op.value));
			return s;
		}
		const idx = s.parts.findIndex((p) => p.name === segs[1]);
		if (segs.length === 2) {
			if (op.op === 'add') {
				if (idx >= 0) throw new Error(`part "${segs[1]}" already exists`);
				s.parts.push({ ...clone(op.value), name: segs[1] });
				return s;
			}
			if (idx < 0) throw new Error(`unknown part "${segs[1]}"`);
			if (op.op === 'remove') {
				const gone = new Set([segs[1], ...descendants(s, segs[1])]);
				s.parts = s.parts.filter((p) => !gone.has(p.name));
			} else s.parts[idx] = { ...clone(op.value), name: op.value?.name || segs[1] };
			return s;
		}
		if (idx < 0) throw new Error(`unknown part "${segs[1]}"`);
		setIn(s.parts[idx], segs.slice(2), op);
		return s;
	}
	setIn(s, segs, op);
	return s;
}

function setIn(obj, segs, op) {
	let o = obj;
	for (let k = 0; k < segs.length - 1; k++) {
		const key = Array.isArray(o) ? Number(segs[k]) : segs[k];
		if (o[key] === undefined || o[key] === null) {
			if (op.op === 'remove') throw new Error(`"${segs[k]}" does not exist`);
			o[key] = {};
		}
		o = o[key];
	}
	const last = segs.at(-1);
	if (Array.isArray(o)) {
		const i = last === '-' ? o.length : Number(last);
		if (!Number.isInteger(i) || i < 0 || i > o.length) throw new Error(`bad array index "${last}"`);
		if (op.op === 'remove') o.splice(i, 1);
		else if (op.op === 'add') o.splice(i, 0, clone(op.value));
		else o[i] = clone(op.value);
		return;
	}
	if (op.op === 'remove') {
		if (!(last in o)) throw new Error(`"${last}" does not exist`);
		delete o[last];
	} else if (op.op === 'replace' && !(last in o)) throw new Error(`"${last}" does not exist (use add)`);
	else o[last] = clone(op.value);
}

/**
 * Minimal part-addressed patch turning spec a into spec b (whole values per key).
 * New parts are appended in b's order, so parents come before children.
 */
export function diffSpecs(a, b) {
	const A = canonicalSpec(a), B = canonicalSpec(b);
	const ops = [];
	for (const key of ['orientation', 'scale']) {
		if (sameValue(A[key], B[key])) continue;
		if (B[key] === undefined) ops.push({ op: 'remove', path: `/${key}` });
		else ops.push({ op: A[key] === undefined ? 'add' : 'replace', path: `/${key}`, value: B[key] });
	}
	if (!sameValue(A.texture, B.texture)) {
		if (!B.texture) ops.push({ op: 'remove', path: '/texture' });
		else if (!A.texture) ops.push({ op: 'add', path: '/texture', value: B.texture });
		else for (const k of new Set([...Object.keys(A.texture), ...Object.keys(B.texture)])) {
			if (sameValue(A.texture[k], B.texture[k])) continue;
			if (B.texture[k] === undefined) ops.push({ op: 'remove', path: `/texture/${k}` });
			else ops.push({ op: A.texture[k] === undefined ? 'add' : 'replace', path: `/texture/${k}`, value: B.texture[k] });
		}
	}
	const an = new Map(A.parts.map((p) => [p.name, p]));
	const bn = new Map(B.parts.map((p) => [p.name, p]));
	// Removals: only the top of each removed subtree (remove takes descendants along)
	const removed = A.parts.filter((p) => !bn.has(p.name));
	const removedSet = new Set(removed.map((p) => p.name));
	for (const p of removed) if (!p.parent || !removedSet.has(p.parent)) ops.push({ op: 'remove', path: `/parts/${p.name}` });
	for (const p of B.parts) {
		const q = an.get(p.name);
		if (!q) continue;
		for (const k of new Set([...Object.keys(q), ...Object.keys(p)])) {
			if (k === 'name' || sameValue(q[k], p[k])) continue;
			if (p[k] === undefined) ops.push({ op: 'remove', path: `/parts/${p.name}/${k}` });
			else ops.push({ op: q[k] === undefined ? 'add' : 'replace', path: `/parts/${p.name}/${k}`, value: p[k] });
		}
	}
	for (const p of B.parts) if (!an.has(p.name)) ops.push({ op: 'add', path: '/parts/-', value: p });
	return ops;
}

/** Names of parts an op list touches (for "did it change the intended part"). */
export function touchedParts(ops) {
	const out = new Set();
	for (const op of ops || []) {
		const m = /^\/parts\/([^/]+)/.exec(op?.path || '');
		if (m && m[1] !== '-') out.add(m[1]);
		else if (m && op.value?.name) out.add(op.value.name);
		else if (op?.path) out.add(op.path.split('/')[1] ? `#${op.path.split('/')[1]}` : '#');
	}
	return out;
}
