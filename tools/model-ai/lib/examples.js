// Examples for the dashboard: dataset records and model predictions, rendered to images.
//   listSources(ver)            -> ['data', 'pred_v1_test', ...]
//   examples({ver, source, ...}) -> records with prompt / target / prediction (+ score)
//   renderExample({ver, source, id, which}) -> PNG (front / right / iso sheet) or null

import fs from 'node:fs';
import path from 'node:path';
import { specToDefinition, tryGenerate } from './gen.js';
import { scoreRecord } from './metrics.js';
import { applyPatch } from './patch.js';
import { encodePNG, renderSheet } from './render.js';
import { parseJsonLoose, serializeSpec } from './spec.js';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), '..');
const DATA = path.join(ROOT, 'data');
const cache = new Map();

function readJsonlCached(file) {
	const st = fs.statSync(file);
	const hit = cache.get(file);
	if (hit && hit.mtime === st.mtimeMs) return hit.value;
	const value = fs.readFileSync(file, 'utf8').split('\n').filter((l) => l.trim()).map((l) => JSON.parse(l));
	cache.set(file, { mtime: st.mtimeMs, value });
	return value;
}

const safe = (s) => String(s || '').replace(/[^a-zA-Z0-9_.-]/g, '');

export function listVersions() {
	return fs.existsSync(DATA) ? fs.readdirSync(DATA).filter((v) => fs.existsSync(path.join(DATA, v, 'test.jsonl'))) : [];
}

/** 'data' + prediction files (pred_<name>_<split>.jsonl), newest first. */
export function listSources(ver) {
	const d = path.join(DATA, safe(ver));
	if (!fs.existsSync(d)) return [];
	const preds = fs.readdirSync(d).filter((f) => /^pred_.*_(test|real|val)\.jsonl$/.test(f)).map((f) => ({ f, t: fs.statSync(path.join(d, f)).mtimeMs })).sort((a, b) => b.t - a.t).map((x) => x.f.replace(/\.jsonl$/, ''));
	return [...preds, 'data'];
}

const splitOf = (source) => (source === 'data' ? 'test' : source.match(/_(test|real|val)$/)[1]);

function records(ver, source) {
	return readJsonlCached(path.join(DATA, safe(ver), `${splitOf(source)}.jsonl`));
}

function predictions(ver, source) {
	if (source === 'data') return null;
	return new Map(readJsonlCached(path.join(DATA, safe(ver), `${safe(source)}.jsonl`)).map((p) => [p.id, p.output]));
}

/**
 * @param {{ver, source, task?: 'text2spec'|'edit'|'all', n?: number, page?: number, filter?: 'all'|'bad'|'good'}} o
 */
export function examples(o) {
	const ver = safe(o.ver || listVersions()[0]);
	const source = o.source && listSources(ver).includes(o.source) ? o.source : listSources(ver)[0];
	if (!source) return { ver, source: null, items: [], total: 0 };
	const preds = predictions(ver, source);
	let recs = records(ver, source);
	if (preds) recs = recs.filter((r) => preds.has(r.id));
	if (o.task && o.task !== 'all') recs = recs.filter((r) => r.task === o.task);
	const n = Math.min(24, Number(o.n) || 6);
	// Spread across the list so different body plans show up
	const page = Number(o.page) || 0;
	const stride = Math.max(1, Math.floor(recs.length / n / 4));
	let picked = [];
	for (let k = 0; k < recs.length && picked.length < n * 4; k++) picked.push(recs[(k * stride + page * 7) % recs.length]);
	picked = [...new Map(picked.map((r) => [r.id, r])).values()];
	const items = [];
	for (const r of picked) {
		const item = { id: r.id, task: r.task, lang: r.lang, archetype: r.archetype, op: r.op, prompt: r.prompt, target_text: r.task === 'edit' ? JSON.stringify(r.target_patch) : serializeSpec(r.target_spec) };
		if (r.task === 'edit') item.input_text = serializeSpec(r.input_spec);
		if (preds) {
			const s = scoreRecord(r, preds.get(r.id));
			item.pred = { output: String(preds.get(r.id)).slice(0, 4000), parsed: s.parsed, valid: !!s.valid, applied: s.applied, exact: s.exact, score: s.score, gain: s.gain, error: s.error || (s.problems || []).join('; ') || null };
			if (o.filter === 'bad' && s.score >= 0.7) continue;
			if (o.filter === 'good' && s.score < 0.7) continue;
		}
		items.push(item);
		if (items.length >= n) break;
	}
	return { ver, source, sources: listSources(ver), total: recs.length, items };
}

function specFor(ver, source, id, which) {
	const rec = records(ver, source).find((r) => r.id === id);
	if (!rec) return null;
	if (which === 'target') return rec.target_spec;
	if (which === 'input') return rec.input_spec || null;
	const preds = predictions(ver, source);
	if (!preds?.has(id)) return null;
	try {
		const v = parseJsonLoose(preds.get(id));
		return rec.task === 'edit' ? applyPatch(rec.input_spec, v) : v;
	} catch {
		return null;
	}
}

const pngCache = new Map();
export function renderExample({ ver, source, id, which = 'target', size = 128 }) {
	ver = safe(ver);
	const key = `${ver}|${source}|${id}|${which}|${size}`;
	if (pngCache.has(key)) return pngCache.get(key);
	const spec = specFor(ver, source, id, which);
	let png = null;
	if (spec?.parts) {
		const r = tryGenerate(specToDefinition(spec, { id: 'example' }));
		if (r.model) png = Buffer.from(encodePNG(renderSheet(r.model, ['front', 'right', 'iso'], Math.min(256, Number(size) || 128), [0, 0, 0, 0])));
	}
	if (pngCache.size > 500) pngCache.clear();
	pngCache.set(key, png);
	return png;
}
