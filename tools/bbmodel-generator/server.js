#!/usr/bin/env node
// Local web server for the BBModel Generator GUI.
//   node server.js            -> http://127.0.0.1:5178
//   PORT=8080 node server.js
// Serves the frontend, the (isomorphic) backend modules, definitions, presets, output and
// three.js, plus a small JSON API for saving definitions and writing exports to output/.

import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { DIRS, ROOT, loadDefinitions, loadPresets, saveDefinition, writeExport } from './backend/creatures/node_store.js';
import { checkDefinition } from './backend/creatures/schema.js';
import { listTemplates } from './backend/templates/index.js';

const PORT = Number(process.env.PORT || process.argv.find((a) => a.startsWith('--port='))?.slice(7) || 5178);
const HOST = process.env.HOST || '127.0.0.1';

const MIME = {
	'.html': 'text/html; charset=utf-8',
	'.js': 'text/javascript; charset=utf-8',
	'.mjs': 'text/javascript; charset=utf-8',
	'.css': 'text/css; charset=utf-8',
	'.json': 'application/json; charset=utf-8',
	'.bbmodel': 'application/json; charset=utf-8',
	'.png': 'image/png',
	'.svg': 'image/svg+xml',
	'.zip': 'application/zip',
	'.map': 'application/json',
	'.md': 'text/markdown; charset=utf-8',
	'.ico': 'image/x-icon',
};

const STATIC = {
	'/frontend/': path.join(ROOT, 'frontend'),
	'/backend/': path.join(ROOT, 'backend'),
	'/definitions/': DIRS.definitions,
	'/presets/': DIRS.presets,
	'/output/': DIRS.output,
	'/vendor/three/': path.join(ROOT, 'node_modules', 'three'),
};

function send(res, status, body, type = 'application/json; charset=utf-8') {
	res.writeHead(status, {
		'Content-Type': type,
		'Cache-Control': 'no-cache',
		'Access-Control-Allow-Origin': '*',
	});
	res.end(typeof body === 'string' || Buffer.isBuffer(body) ? body : JSON.stringify(body));
}

function readBody(req, limit = 64 * 1024 * 1024) {
	return new Promise((resolve, reject) => {
		const chunks = [];
		let size = 0;
		req.on('data', (c) => {
			size += c.length;
			if (size > limit) {
				reject(new Error('Request too large'));
				req.destroy();
			} else chunks.push(c);
		});
		req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
		req.on('error', reject);
	});
}

function serveFile(res, file) {
	fs.stat(file, (err, stat) => {
		if (err || !stat.isFile()) return send(res, 404, { error: 'Not found' });
		res.writeHead(200, {
			'Content-Type': MIME[path.extname(file).toLowerCase()] || 'application/octet-stream',
			'Content-Length': stat.size,
			'Cache-Control': 'no-cache',
			'Access-Control-Allow-Origin': '*',
		});
		fs.createReadStream(file).pipe(res);
	});
}

function resolveStatic(urlPath) {
	for (const [prefix, dir] of Object.entries(STATIC)) {
		if (!urlPath.startsWith(prefix)) continue;
		const rel = decodeURIComponent(urlPath.slice(prefix.length));
		const file = path.resolve(dir, rel);
		if (file !== dir && !file.startsWith(dir + path.sep)) return null;
		return file;
	}
	return null;
}

// eslint-disable-next-line no-unused-vars
const strip = (list) => list.map(({ _file, ...rest }) => rest);

async function handleApi(req, res, url) {
	const templates = listTemplates();
	if (req.method === 'GET' && url.pathname === '/api/definitions') return send(res, 200, strip(loadDefinitions()));
	if (req.method === 'GET' && url.pathname === '/api/presets') return send(res, 200, strip(loadPresets()));
	if (req.method === 'GET' && url.pathname === '/api/templates') return send(res, 200, templates);
	const defMatch = url.pathname.match(/^\/api\/definitions\/([a-z][a-z0-9_]*)$/);
	if (req.method === 'PUT' && defMatch) {
		const def = JSON.parse(await readBody(req));
		if (def.id !== defMatch[1]) return send(res, 400, { error: 'id in body does not match URL' });
		const problems = checkDefinition(def, templates.map((t) => t.id)).filter((p) => p.level === 'error');
		if (problems.length) return send(res, 400, { error: 'Invalid definition', problems });
		const file = saveDefinition(def);
		return send(res, 200, { ok: true, file: path.relative(ROOT, file) });
	}
	if (req.method === 'POST' && url.pathname === '/api/export') {
		const body = JSON.parse(await readBody(req));
		if (!body.folder || !Array.isArray(body.files)) return send(res, 400, { error: 'Expected {folder, files: [{name, base64 | text}]}' });
		const files = body.files.map((f) => ({ name: f.name, data: f.text ?? Buffer.from(f.base64 || '', 'base64') }));
		const dir = writeExport(body.folder, files);
		return send(res, 200, { ok: true, dir: path.relative(ROOT, dir), files: files.map((f) => f.name) });
	}
	return send(res, 404, { error: 'Unknown API endpoint' });
}

const server = http.createServer(async (req, res) => {
	try {
		const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
		if (req.method === 'OPTIONS') {
			res.writeHead(204, {
				'Access-Control-Allow-Origin': '*',
				'Access-Control-Allow-Methods': 'GET, PUT, POST, OPTIONS',
				'Access-Control-Allow-Headers': 'Content-Type',
				'Access-Control-Allow-Private-Network': 'true',
			});
			return res.end();
		}
		if (url.pathname.startsWith('/api/')) return await handleApi(req, res, url);
		if (url.pathname === '/' || url.pathname === '/index.html') return serveFile(res, path.join(ROOT, 'frontend', 'index.html'));
		if (url.pathname === '/favicon.ico') return send(res, 204, '');
		const file = resolveStatic(url.pathname);
		if (!file) return send(res, 404, { error: 'Not found' });
		return serveFile(res, file);
	} catch (err) {
		send(res, 500, { error: err.message });
	}
});

server.listen(PORT, HOST, () => {
	console.log(`BBModel Generator: http://${HOST}:${PORT}`);
});
