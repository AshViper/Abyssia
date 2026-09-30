#!/usr/bin/env node
// Progress dashboard for model-ai training / evaluation.
//   node dashboard.js [--port 5179]  ->  http://localhost:5179
// Reads the WSL run directories through \\wsl.localhost (lib/progress.js); read-only, no side effects.
// JSON: GET /api/snapshot[?run=<name>]  (same data as `node ai.js progress --pretty`)
//       GET /api/examples?ver=v1&source=data|pred_<name>_<split>&task=text2spec|edit|all&n=6&page=0&filter=all|bad|good
//       GET /api/stream[?run=<name>]  (server-sent events: live training state, pushed on change)
//       GET /api/model[?run=<name>]   (architecture from model_config.json + live training state, for /model)
//       GET /api/gen/status, POST /api/gen {prompt}, GET /api/gen/file?id=&name=  (CPU generation while training, lib/infer.js)
// PNG:  GET /api/render?ver=v1&source=...&id=<record id>&which=target|input|pred

import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { examples, renderExample } from './lib/examples.js';
import { generate, generatedFile, inferState, latestAdapter, listGenerated, shutdown } from './lib/infer.js';
import { listRuns, liveStatus, modelInfo, runStatus, snapshot } from './lib/progress.js';

const dir = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const portArg = process.argv.indexOf('--port');
const PORT = Number(process.env.PORT || (portArg > 0 ? process.argv[portArg + 1] : 5179));

const json = (res, obj, code = 200) => (res.writeHead(code, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' }), res.end(JSON.stringify(obj)));
const readBody = (req) => new Promise((resolve) => {
	let b = '';
	req.on('data', (c) => (b += c));
	req.on('end', () => resolve(b));
});

const server = http.createServer(async (req, res) => {
	const url = new URL(req.url, 'http://localhost');
	try {
		if (url.pathname === '/api/snapshot') {
			const body = JSON.stringify(await snapshot(url.searchParams.get('run') || undefined));
			res.writeHead(200, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
			return res.end(body);
		}
		if (url.pathname === '/api/stream') {
			// Server-sent events: pushes the live training state whenever it changes (checked every second)
			const run = url.searchParams.get('run') || listRuns()[0];
			res.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8', 'cache-control': 'no-store', connection: 'keep-alive' });
			let last = '';
			const tick = () => {
				const body = JSON.stringify(liveStatus(run));
				if (body !== last) res.write(`data: ${(last = body)}\n\n`);
			};
			tick();
			const iv = setInterval(tick, 1000);
			const hb = setInterval(() => res.write(': ping\n\n'), 15000);
			req.on('close', () => (clearInterval(iv), clearInterval(hb)));
			return;
		}
		// ---- CPU generation with the newest checkpoint (lib/infer.js) ----
		if (url.pathname === '/api/gen/status') {
			const run = url.searchParams.get('run') || listRuns()[0];
			return json(res, { ...inferState(), latest: latestAdapter(run), run, history: listGenerated(24) });
		}
		if (url.pathname === '/api/gen' && req.method === 'POST') {
			const body = JSON.parse(await readBody(req) || '{}');
			if (!body.prompt || typeof body.prompt !== 'string') return json(res, { error: 'prompt is required' }, 400);
			const run = body.run || listRuns()[0];
			try {
				return json(res, await generate(body.prompt.slice(0, 400), { run }));
			} catch (err) {
				return json(res, { error: err.message }, err.message.startsWith('busy') ? 409 : 500);
			}
		}
		if (url.pathname === '/api/gen/file') {
			const f = generatedFile(url.searchParams.get('id') || '', url.searchParams.get('name') || '');
			if (!f) return res.writeHead(404).end('not found');
			const type = f.endsWith('.png') ? 'image/png' : f.endsWith('.json') ? 'application/json' : 'application/octet-stream';
			res.writeHead(200, { 'content-type': type, ...(url.searchParams.get('download') ? { 'content-disposition': `attachment; filename="${path.basename(f)}"` } : {}) });
			return res.end(fs.readFileSync(f));
		}
		if (url.pathname === '/api/model') {
			const run = url.searchParams.get('run') || listRuns()[0];
			const ex = examples({ ver: 'v1', source: 'data', task: 'text2spec', n: 12, page: Number(url.searchParams.get('page') || 0) });
			const status = run ? runStatus(run) : null;
			const body = JSON.stringify({
				run,
				info: modelInfo(run),
				examples: ex.items.map((i) => ({ id: i.id, lang: i.lang, prompt: i.prompt, target_text: i.target_text })),
				live: status && { phase: status.phase, progress: status.progress, percent: status.percent, loss: status.loss.slice(-40), eval_loss: status.eval_loss },
			});
			res.writeHead(200, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
			return res.end(body);
		}
		if (url.pathname === '/api/examples') {
			const q = Object.fromEntries(url.searchParams);
			const body = JSON.stringify(examples(q));
			res.writeHead(200, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
			return res.end(body);
		}
		if (url.pathname === '/api/render') {
			const q = Object.fromEntries(url.searchParams);
			const png = renderExample(q);
			if (!png) return res.writeHead(404).end('no model');
			res.writeHead(200, { 'content-type': 'image/png', 'cache-control': 'max-age=60' });
			return res.end(png);
		}
		const page = { '/': 'index.html', '/index.html': 'index.html', '/model': 'model.html', '/model.html': 'model.html', '/generate': 'generate.html' }[url.pathname];
		if (page) {
			res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' });
			return res.end(fs.readFileSync(path.join(dir, 'dashboard', page)));
		}
		res.writeHead(404).end('not found');
	} catch (err) {
		res.writeHead(500, { 'content-type': 'application/json' }).end(JSON.stringify({ error: err.message }));
	}
});
server.listen(PORT, () => console.log(`model-ai dashboard: http://localhost:${PORT}`));
for (const sig of ['SIGINT', 'SIGTERM', 'exit']) process.on(sig, () => (shutdown(), sig !== 'exit' && process.exit(0)));
