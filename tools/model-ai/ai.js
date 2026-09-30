#!/usr/bin/env node
// model-ai command line. Every command prints JSON on stdout (logs go to stderr).
//   node ai.js render <id|def.json|spec.json> [--views front,right,top,iso] [--size 192] [--out file.png]
//   node ai.js check  <def.json|spec.json>                -> {ok, problems, stats}
//   node ai.js sample <archetype|all> [--n 3] [--seed 1] [--out sheet.png] [--pretty]
//   node ai.js dataset [--n 20000] [--seed 1] [--out data/v1] [--images 0] [--dry-run]
//   node ai.js eval   --pred <preds.jsonl> --ref <split.jsonl> [--out report.json] [--subset]  (--subset: only ids that have predictions)
//   node ai.js score  <pred.json> <ref.json>              -> metrics for one pair
//   node ai.js gen "<text>" [--run v2]                   -> CPU generation with the newest checkpoint (runtime/generated/<id>/)
//   node ai.js watch [run]                                -> one JSON line per training step, live
//   node ai.js progress [run]                             -> training progress / GPU / eval reports (dashboard: node dashboard.js)
//   node ai.js baseline --ref <split.jsonl> --out <preds.jsonl> [--mode oracle|empty|keyword]

import fs from 'node:fs';
import path from 'node:path';
import { definitionToSpec, loadDefinition, specToDefinition, tryGenerate } from './lib/gen.js';
import { encodePNG, renderSheet } from './lib/render.js';

const HELP = fs.readFileSync(new URL(import.meta.url), 'utf8').split('\n').slice(1, 8).map((l) => l.replace(/^\/\/ ?/, '')).join('\n');

export function parseArgs(argv) {
	const args = { _: [] };
	for (let i = 0; i < argv.length; i++) {
		const a = argv[i];
		if (!a.startsWith('--')) args._.push(a);
		else if (['--dry-run', '--help', '--pretty', '--subset'].includes(a)) args[a.slice(2)] = true;
		else args[a.slice(2)] = argv[++i];
	}
	return args;
}

/** Accepts a definition id, a definition file or a spec file. */
export function loadAny(ref) {
	if (!ref.endsWith('.json') && !fs.existsSync(ref)) return loadDefinition(ref);
	const obj = JSON.parse(fs.readFileSync(ref, 'utf8'));
	const id = path.basename(ref, '.json').replace(/[^a-z0-9_]/g, '_');
	if (obj.template) return obj;
	return specToDefinition(obj.spec && obj.captions ? obj.spec : obj, { id }); // spec file or eval/real item
}

const out = (obj, pretty) => process.stdout.write(JSON.stringify(obj, null, pretty ? 2 : 0) + '\n');

async function main() {
	const args = parseArgs(process.argv.slice(2));
	const [cmd, ...rest] = args._;
	if (!cmd || args.help) return console.log(HELP), 0;
	switch (cmd) {
		case 'render': {
			const def = loadAny(rest[0]);
			const r = tryGenerate(def);
			if (!r.model) return out({ ok: false, problems: r.problems }, true), 2;
			const views = (args.views || 'front,right,top,iso').split(',');
			const file = args.out || `${def.id}_views.png`;
			fs.writeFileSync(file, encodePNG(renderSheet(r.model, views, Number(args.size || 192))));
			out({ ok: r.ok, file: path.resolve(file), views, problems: r.problems, cubes: r.model.stats.cubes });
			return 0;
		}
		case 'sample': {
			const { ARCHETYPES, sampleArchetype } = await import('./lib/archetypes/index.js');
			const { createRng } = await import('./lib/rng.js');
			const { RGBAImage } = await import('../bbmodel-generator/backend/texture/image.js');
			const ids = !rest.length || rest[0] === 'all' ? Object.keys(ARCHETYPES) : rest;
			const n = Number(args.n || 3), seed = Number(args.seed || 1), size = Number(args.size || 160);
			const rows = [], results = [];
			for (const id of ids)
				for (let i = 0; i < n; i++) {
					const s = sampleArchetype(id, createRng(`${id}:${seed + i}`));
					const r = tryGenerate(specToDefinition(s.spec, { id }));
					results.push({ archetype: id, seed: seed + i, ok: r.ok, problems: r.problems, cubes: r.model?.stats.cubes, kind: s.facts.kind?.en, traits: s.facts.traits.map((t) => t.en), spec: args.pretty ? s.spec : undefined });
					if (r.model) rows.push(renderSheet(r.model, ['front', 'right', 'top', 'iso'], size));
				}
			if (args.out && rows.length) {
				const sheet = new RGBAImage(rows[0].width, size * rows.length);
				rows.forEach((img, i) => sheet.data.set(img.data, i * img.data.length));
				fs.writeFileSync(args.out, encodePNG(sheet));
			}
			out(results, args.pretty);
			return results.every((r) => r.ok) ? 0 : 2;
		}
		case 'check': {
			const def = loadAny(rest[0]);
			const r = tryGenerate(def);
			out({ ok: r.ok, problems: r.problems, stats: r.model?.stats || null, spec: args.pretty ? definitionToSpec(def) : undefined }, true);
			return r.ok ? 0 : 2;
		}
		case 'dataset': {
			const { buildDataset } = await import('./lib/dataset.js');
			out(await buildDataset(args), true);
			return 0;
		}
		case 'eval': {
			const { evaluateFiles } = await import('./lib/metrics.js');
			const report = evaluateFiles(args.pred, args.ref, { subset: !!args.subset });
			if (args.out) fs.writeFileSync(args.out, JSON.stringify(report, null, 2) + '\n');
			out(report.summary, true);
			return 0;
		}
		case 'baseline': {
			const { predict } = await import('./lib/baseline.js');
			const { readJsonl } = await import('./lib/metrics.js');
			const mode = args.mode || 'keyword';
			const lines = readJsonl(args.ref).map((rec) => JSON.stringify({ id: rec.id, output: predict(rec, mode) }));
			fs.writeFileSync(args.out, lines.join('\n') + '\n');
			out({ mode, predictions: lines.length, file: path.resolve(args.out) });
			return 0;
		}
		case 'gen': {
			// Text -> model on the CPU with the newest checkpoint of a run (llama.cpp), while the GPU trains
			const { generate, shutdown } = await import('./lib/infer.js');
			const { listRuns } = await import('./lib/progress.js');
			try {
				const r = await generate(rest.join(' '), { run: args.run || listRuns()[0] });
				out({ ...r, dir: path.join('runtime', 'generated', r.id) }, true);
				return r.ok ? 0 : 2;
			} finally {
				shutdown();
			}
		}
		case 'watch': {
			// One JSON line per training step (Ctrl+C to stop); ends by itself when the run finishes
			const { liveStatus, listRuns } = await import('./lib/progress.js');
			const run = rest[0] || listRuns()[0];
			let lastStep = null;
			for (;;) {
				const s = liveStatus(run);
				const L = s.live;
				if (L && L.step !== lastStep) {
					lastStep = L.step;
					out({ run, phase: s.phase, step: L.step, total: L.total, percent: Math.round((1000 * L.step) / L.total) / 10, loss: L.loss, grad_norm: L.grad_norm, eval_loss: L.eval_loss, sec_per_step: L.sec_per_step, eta_min: L.eta_s != null ? Math.round(L.eta_s / 60) : null });
				}
				if (s.phase === 'done' || s.phase === 'failed') return out({ run, phase: s.phase, exit: s.exit }), 0;
				await new Promise((r) => setTimeout(r, 1000));
			}
		}
		case 'progress': {
			const { snapshot } = await import('./lib/progress.js');
			const s = await snapshot(rest[0]);
			if (s.run && !args.pretty) (s.run.loss = s.run.loss.slice(-3)), (s.run.eval_loss = s.run.eval_loss.slice(-3));
			if (!args.pretty) s.reports = s.reports.map((r) => ({ label: r.label, score: r.summary.all.score, valid: r.summary.all.valid_rate }));
			out(s, true);
			return 0;
		}
		case 'score': {
			const { scorePair } = await import('./lib/metrics.js');
			const read = (f) => JSON.parse(fs.readFileSync(f, 'utf8'));
			const a = read(rest[0]), b = read(rest[1]);
			out(scorePair(a.template ? definitionToSpec(a) : a, b.template ? definitionToSpec(b) : b), true);
			return 0;
		}
		default:
			console.error(`unknown command "${cmd}"\n${HELP}`);
			return 1;
	}
}

if (import.meta.url === `file:///${process.argv[1].replace(/\\/g, '/')}` || process.argv[1]?.endsWith('ai.js')) {
	main().then((code) => process.exit(code ?? 0), (err) => {
		console.error(err.stack || err.message);
		process.exit(1);
	});
}
