// Scoring of predicted specs against references (0..1, higher is better).
//   valid      generator + validator accept the prediction
//   iou        silhouette IoU per view in a shared frame (absolute size and position matter)
//   shape_iou  silhouette IoU with each model fitted to its own frame (shape only)
//   size       mean over axes of min/max of the bounding box sizes
//   color      1 - mean colour distance over pixels both models cover
//   parts      F1 of part names
//   score      weighted sum (0 when invalid)
//   gain       edits only: (score - score(input)) / (1 - score(input)) clamped to -1..1; no-op = 0, exact = 1

import fs from 'node:fs';
import { specToDefinition, tryGenerate } from './gen.js';
import { applyPatch, touchedParts } from './patch.js';
import { fitFrame, modelQuads, renderView, unionBounds, viewBounds } from './render.js';
import { canonicalSpec, parseJsonLoose, sameValue } from './spec.js';
import { colorDistance } from './vocab.js';

export const METRIC_VIEWS = ['front', 'right', 'top'];
const SIZE = 96;
const WEIGHTS = { iou: 0.35, shape_iou: 0.25, size: 0.15, color: 0.15, parts: 0.1 };

const r3 = (v) => Math.round(v * 1000) / 1000;

function iou(a, b) {
	let inter = 0, uni = 0;
	for (let i = 3; i < a.data.length; i += 4) {
		const x = a.data[i] > 0, y = b.data[i] > 0;
		if (x && y) inter++;
		if (x || y) uni++;
	}
	return uni ? inter / uni : 1;
}

function colorSim(a, b) {
	let sum = 0, n = 0;
	for (let i = 0; i < a.data.length; i += 4) {
		if (!a.data[i + 3] || !b.data[i + 3]) continue;
		sum += colorDistance([a.data[i], a.data[i + 1], a.data[i + 2]], [b.data[i], b.data[i + 1], b.data[i + 2]]);
		n++;
	}
	return n ? 1 - Math.min(1, sum / n / 300) : 0;
}

const baseName = (n) => n.replace(/_\d+$/, '');
function partF1(a, b) {
	const A = new Set(a.parts.map((p) => baseName(p.name))), B = new Set(b.parts.map((p) => baseName(p.name)));
	const tp = [...A].filter((x) => B.has(x)).length;
	if (!tp) return 0;
	const prec = tp / A.size, rec = tp / B.size;
	return (2 * prec * rec) / (prec + rec);
}

/** Generates a spec once (cache by canonical text). */
const cache = new Map();
function generated(spec) {
	const key = JSON.stringify(canonicalSpec(spec));
	if (!cache.has(key)) {
		if (cache.size > 2000) cache.clear();
		const r = tryGenerate(specToDefinition(spec));
		cache.set(key, r.model ? { ...r, quads: modelQuads(r.model) } : r);
	}
	return cache.get(key);
}

export function scorePair(pred, ref) {
	const P = generated(pred), R = generated(ref);
	if (!R.model) throw new Error(`reference does not generate: ${R.problems.join('; ')}`);
	if (!P.model || !P.ok) return { valid: false, problems: P.problems.slice(0, 5), score: 0 };
	const out = { valid: true, iou: {}, shape_iou: {} };
	let colorSum = 0;
	for (const view of METRIC_VIEWS) {
		const bp = viewBounds(P.quads, view), br = viewBounds(R.quads, view);
		const frame = fitFrame(unionBounds(bp, br));
		const pm = renderView(P.model, { view, size: SIZE, frame, quads: P.quads });
		const rm = renderView(R.model, { view, size: SIZE, frame, quads: R.quads });
		out.iou[view] = r3(iou(pm, rm));
		colorSum += colorSim(pm, rm);
		const pn = renderView(P.model, { view, size: SIZE, frame: fitFrame(bp), quads: P.quads, mode: 'mask' });
		const rn = renderView(R.model, { view, size: SIZE, frame: fitFrame(br), quads: R.quads, mode: 'mask' });
		out.shape_iou[view] = r3(iou(pn, rn));
	}
	const mean = (o) => Object.values(o).reduce((s, v) => s + v, 0) / Object.values(o).length;
	const sp = P.model.bounds.size, sr = R.model.bounds.size;
	const m = {
		iou: mean(out.iou),
		shape_iou: mean(out.shape_iou),
		size: [0, 1, 2].reduce((s, a) => s + Math.min(sp[a], sr[a]) / Math.max(sp[a], sr[a], 1e-6), 0) / 3,
		color: colorSum / METRIC_VIEWS.length,
		parts: partF1(pred, ref),
	};
	for (const [k, v] of Object.entries(m)) out[`${k}_mean`] = r3(v);
	out.score = r3(Object.entries(WEIGHTS).reduce((s, [k, w]) => s + w * m[k], 0));
	return out;
}

/**
 * Scores one prediction (raw model output text) against a dataset record.
 * @param {object} rec dataset record {task, input_spec?, target_spec, target_patch?}
 * @param {string} output raw model text
 */
export function scoreRecord(rec, output) {
	const res = { id: rec.id, task: rec.task, archetype: rec.archetype, parsed: false, score: 0 };
	let value;
	try {
		value = parseJsonLoose(output);
		res.parsed = true;
	} catch (err) {
		res.error = `parse: ${err.message}`;
		return res;
	}
	let spec = value;
	if (rec.task === 'edit') {
		try {
			spec = applyPatch(rec.input_spec, value);
			res.applied = true;
		} catch (err) {
			res.applied = false;
			res.error = err.message;
			return res;
		}
		const want = touchedParts(rec.target_patch), got = touchedParts(value);
		res.touched_ok = want.size === got.size && [...want].every((x) => got.has(x));
		res.exact = sameValue(canonicalSpec(spec), canonicalSpec(rec.target_spec));
	} else if (!spec || !Array.isArray(spec.parts)) {
		res.error = 'output is not a spec with "parts"';
		return res;
	}
	Object.assign(res, scorePair(spec, rec.target_spec));
	if (rec.task === 'edit') {
		// Share of the input->target gap the edit closed (no-op = 0, exact = 1, worse than no-op < 0)
		const before = scorePair(rec.input_spec, rec.target_spec).score;
		res.gain = r3(Math.max(-1, Math.min(1, before >= 0.999 ? (res.exact ? 1 : 0) : (res.score - before) / (1 - before))));
	}
	return res;
}

export const readJsonl = (file) =>
	fs.readFileSync(file, 'utf8').split('\n').filter((l) => l.trim()).map((l) => JSON.parse(l));

function summarize(rows) {
	const avg = (list, f) => (list.length ? r3(list.reduce((s, r) => s + (f(r) ? Number(f(r)) : 0), 0) / list.length) : null);
	const block = (list) => {
		const o = { n: list.length, parse_rate: avg(list, (r) => r.parsed), valid_rate: avg(list, (r) => r.valid), score: avg(list, (r) => r.score) };
		const valid = list.filter((r) => r.valid);
		if (valid.length) Object.assign(o, { iou: avg(valid, (r) => r.iou_mean), shape_iou: avg(valid, (r) => r.shape_iou_mean), size: avg(valid, (r) => r.size_mean), color: avg(valid, (r) => r.color_mean), parts: avg(valid, (r) => r.parts_mean) });
		const edits = list.filter((r) => r.task === 'edit');
		if (edits.length) Object.assign(o, { gain: avg(edits, (r) => r.gain), apply_rate: avg(edits, (r) => r.applied), exact_rate: avg(edits, (r) => r.exact), touched_rate: avg(edits, (r) => r.touched_ok) });
		return o;
	};
	const group = (key) => {
		const m = {};
		for (const r of rows) (m[r[key] ?? '-'] ||= []).push(r);
		return Object.fromEntries(Object.entries(m).sort().map(([k, v]) => [k, block(v)]));
	};
	return { all: block(rows), by_task: group('task'), by_archetype: group('archetype') };
}

/** preds.jsonl lines: {id, output}. ref.jsonl: dataset records. Missing predictions count as failures unless subset. */
export function evaluateFiles(predFile, refFile, { subset = false } = {}) {
	const preds = new Map(readJsonl(predFile).map((p) => [p.id, p.output]));
	const rows = readJsonl(refFile).filter((rec) => !subset || preds.has(rec.id)).map((rec) => (preds.has(rec.id) ? scoreRecord(rec, preds.get(rec.id)) : { id: rec.id, task: rec.task, archetype: rec.archetype, parsed: false, score: 0, error: 'missing prediction' }));
	return { summary: summarize(rows), rows };
}
