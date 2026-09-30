// CPU inference with llama.cpp while the GPU trains: base GGUF + the newest LoRA checkpoint of a run.
//   - latestAdapter(run)   newest checkpoint-N (or the final adapter) in the WSL run dir
//   - ensureServer(run)     converts that adapter to GGUF (WSL python, llama.cpp's convert_lora_to_gguf.py)
//                           and (re)starts llama-server.exe on 127.0.0.1:5180 with it
//   - generate(prompt)      text -> parts spec (JSON mode), validated by the generator, retried on errors
// Files: runtime/ (llama.cpp build, base GGUF, converted LoRAs, server log, generated models). Not committed.

import { execFile, spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { exportFiles, specToDefinition, tryGenerate } from './gen.js';
import { runsDir } from './progress.js';
import { text2specMessages } from './prompts.js';
import { encodePNG, renderSheet } from './render.js';
import { canonicalSpec, parseJsonLoose } from './spec.js';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), '..');
export const RUNTIME = path.join(ROOT, 'runtime');
const LLAMA_DIR = path.join(RUNTIME, 'llama');
const BASE_GGUF = path.join(RUNTIME, 'models', 'qwen2.5-coder-3b-instruct-q4_k_m.gguf');
const LORA_DIR = path.join(RUNTIME, 'lora');
const OUT_DIR = path.join(RUNTIME, 'generated');
const PORT = Number(process.env.MODEL_AI_INFER_PORT || 5180);
const THREADS = Number(process.env.MODEL_AI_INFER_THREADS || 8); // leave CPU for the trainer
const DISTRO = process.env.MODEL_AI_DISTRO || 'Ubuntu-24.04';

const state = { status: 'stopped', adapter: null, pid: null, error: null, converting: null, busy: false, stage: 'idle' };
let proc = null;

export function inferState() {
	return { ...state, port: PORT, threads: THREADS, base: path.basename(BASE_GGUF), ready: state.status === 'ready' };
}

function findExe(name) {
	const direct = path.join(LLAMA_DIR, name);
	if (fs.existsSync(direct)) return direct;
	for (const d of fs.existsSync(LLAMA_DIR) ? fs.readdirSync(LLAMA_DIR) : []) {
		const p = path.join(LLAMA_DIR, d, name);
		if (fs.existsSync(p)) return p;
	}
	return null;
}

/** Newest usable adapter of a run: final adapter > highest checkpoint-N > none (base model). */
export function latestAdapter(run) {
	const dir = runsDir();
	if (!dir || !run) return { kind: 'base', step: 0, run };
	const runDir = path.join(dir, run);
	if (fs.existsSync(path.join(runDir, 'adapter', 'adapter_config.json'))) {
		let total = null;
		try {
			total = JSON.parse(fs.readFileSync(path.join(runDir, 'live.json'), 'utf8')).total;
		} catch {
			/* older runs have no live.json */
		}
		return { kind: 'final', step: total, run, winPath: path.join(runDir, 'adapter'), wslPath: `$HOME/model-ai/runs/${run}/adapter` };
	}
	const ck = path.join(runDir, 'checkpoints');
	const steps = fs.existsSync(ck) ? fs.readdirSync(ck).map((d) => /^checkpoint-(\d+)$/.exec(d)?.[1]).filter(Boolean).map(Number).sort((a, b) => b - a) : [];
	for (const step of steps) {
		if (fs.existsSync(path.join(ck, `checkpoint-${step}`, 'adapter_model.safetensors'))) return { kind: 'checkpoint', step, run, winPath: path.join(ck, `checkpoint-${step}`), wslPath: `$HOME/model-ai/runs/${run}/checkpoints/checkpoint-${step}` };
	}
	return { kind: 'base', step: 0, run };
}

const adapterKey = (a) => (a.kind === 'base' ? 'base' : `${a.run}-${a.kind}-${a.step}`);

function wsl(args, timeout = 20 * 60 * 1000) {
	return new Promise((resolve, reject) => {
		execFile('wsl.exe', ['-d', DISTRO, '--', ...args], { timeout, maxBuffer: 20 * 1024 * 1024 }, (err, stdout, stderr) => (err ? reject(new Error(`${err.message}\n${String(stderr).slice(-800)}`)) : resolve(stdout)));
	});
}

/** LoRA safetensors -> GGUF (f16) with llama.cpp's converter, run in the WSL training venv. */
async function convertAdapter(a) {
	const out = path.join(LORA_DIR, `${adapterKey(a)}.gguf`);
	if (fs.existsSync(out)) return out;
	fs.mkdirSync(LORA_DIR, { recursive: true });
	const src = fs.readdirSync(path.join(RUNTIME, 'src')).find((d) => d.startsWith('llama.cpp'));
	if (!src) throw new Error('llama.cpp source not found in runtime/src');
	const toWsl = (p) => `/mnt/${p[0].toLowerCase()}${p.slice(2).replace(/\\/g, '/')}`;
	state.converting = adapterKey(a);
	try {
		// The converter cannot read the bitsandbytes base config: use a weights-free copy (train/lora_base_config.py)
		const mk = `test -f $HOME/model-ai/lora-base/config.json || python ${toWsl(path.join(ROOT, 'train', 'lora_base_config.py'))}`;
		await wsl(['bash', '-lc', `source $HOME/model-ai/.venv/bin/activate && ${mk} && python ${toWsl(path.join(RUNTIME, 'src', src, 'convert_lora_to_gguf.py'))} --base $HOME/model-ai/lora-base --outtype f16 --outfile ${toWsl(out)} ${a.wslPath}`]);
	} finally {
		state.converting = null;
	}
	if (!fs.existsSync(out)) throw new Error('conversion produced no file');
	return out;
}

async function waitReady(ms = 120000) {
	const t0 = Date.now();
	while (Date.now() - t0 < ms) {
		try {
			const r = await fetch(`http://127.0.0.1:${PORT}/health`);
			if (r.ok) return true;
		} catch {
			/* not up yet */
		}
		await new Promise((r) => setTimeout(r, 500));
	}
	throw new Error('llama-server did not become ready');
}

function stopServer() {
	if (proc) proc.kill();
	proc = null;
	state.status = 'stopped';
	state.pid = null;
}

let ensuring = null;
/** Makes sure llama-server runs with the newest adapter of `run` (restarts when a newer one exists). */
export function ensureServer(run) {
	if (ensuring) return ensuring;
	ensuring = (async () => {
		try {
			const exe = findExe('llama-server.exe');
			if (!exe) throw new Error('runtime/llama/llama-server.exe not found (download llama.cpp win-cpu-x64)');
			if (!fs.existsSync(BASE_GGUF)) throw new Error(`base model missing: ${BASE_GGUF}`);
			const a = latestAdapter(run);
			if (state.status === 'ready' && state.adapter && adapterKey(state.adapter) === adapterKey(a)) return inferState();
			const lora = a.kind === 'base' ? null : await convertAdapter(a);
			stopServer();
			state.status = 'starting';
			state.error = null;
			fs.mkdirSync(RUNTIME, { recursive: true });
			const log = fs.openSync(path.join(RUNTIME, 'server.log'), 'w');
			const args = ['-m', BASE_GGUF, '--host', '127.0.0.1', '--port', String(PORT), '-t', String(THREADS), '-c', '4096', '-np', '1', '--no-webui'];
			if (lora) args.push('--lora', lora);
			proc = spawn(exe, args, { stdio: ['ignore', log, log], windowsHide: true });
			state.pid = proc.pid;
			proc.on('exit', (code) => {
				if (state.pid === proc?.pid || state.status !== 'stopped') (state.status = 'stopped'), (state.error = code ? `llama-server exited (${code}), see runtime/server.log` : null);
			});
			await waitReady();
			state.status = 'ready';
			state.adapter = a;
			return inferState();
		} catch (err) {
			state.status = 'error';
			state.error = err.message;
			throw err;
		} finally {
			ensuring = null;
		}
	})();
	return ensuring;
}

async function complete(messages, temperature) {
	const r = await fetch(`http://127.0.0.1:${PORT}/v1/chat/completions`, {
		method: 'POST',
		headers: { 'content-type': 'application/json' },
		body: JSON.stringify({ messages, temperature, max_tokens: 1400, response_format: { type: 'json_object' }, cache_prompt: true }),
	});
	if (!r.ok) throw new Error(`llama-server ${r.status}: ${(await r.text()).slice(0, 300)}`);
	const j = await r.json();
	return { text: j.choices?.[0]?.message?.content || '', tokens: j.usage?.completion_tokens, timings: j.timings };
}

const slug = (s) => s.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '').slice(0, 32) || 'model';

/**
 * Text -> model. Tries greedy first, then up to `retries` sampled attempts while the result is invalid.
 * Writes runtime/generated/<id>/ (spec.json, preview.png, export files) and returns a summary.
 */
export async function generate(prompt, { run, retries = 2 } = {}) {
	if (state.busy) throw new Error('busy: one generation at a time');
	state.busy = true;
	const t0 = Date.now();
	try {
		state.stage = 'server';
		await ensureServer(run);
		const attempts = [];
		let best = null;
		for (let i = 0; i <= retries; i++) {
			state.stage = 'infer';
			const { text, tokens } = await complete(text2specMessages(prompt), i === 0 ? 0 : 0.4);
			state.stage = 'validate';
			let spec = null, problems = [];
			try {
				spec = canonicalSpec(parseJsonLoose(text));
				if (!Array.isArray(spec.parts)) throw new Error('no "parts" list');
			} catch (err) {
				problems = [`JSON: ${err.message}`];
			}
			const gen = spec ? tryGenerate(specToDefinition(spec, { id: 'ai_model' })) : null;
			if (gen) problems = gen.problems;
			attempts.push({ ok: !!gen?.ok, tokens, problems: problems.slice(0, 3) });
			if (gen?.model && (!best || (gen.ok && !best.gen.ok))) best = { spec, gen, text };
			if (gen?.ok) break;
		}
		state.stage = 'export';
		const id = `${new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14)}_${slug(prompt)}`;
		const dir = path.join(OUT_DIR, id);
		fs.mkdirSync(dir, { recursive: true });
		const result = { id, prompt, ok: !!best?.gen.ok, adapter: state.adapter, attempts, seconds: Math.round((Date.now() - t0) / 100) / 10, files: [] };
		if (best) {
			fs.writeFileSync(path.join(dir, 'spec.json'), JSON.stringify(best.spec, null, 2) + '\n');
			fs.writeFileSync(path.join(dir, 'preview.png'), encodePNG(renderSheet(best.gen.model, ['front', 'right', 'iso'], 192, [0, 0, 0, 0])));
			if (best.gen.ok) {
				const def = specToDefinition(best.spec, { id: slug(prompt), name: prompt.slice(0, 60) });
				const model = tryGenerate(def).model;
				for (const f of exportFiles(model).files) {
					fs.writeFileSync(path.join(dir, f.name), f.data);
					result.files.push(f.name);
				}
			}
			result.cubes = best.gen.model.stats.cubes;
		} else result.raw = attempts.at(-1);
		fs.writeFileSync(path.join(dir, 'result.json'), JSON.stringify(result, null, 2) + '\n');
		return result;
	} finally {
		state.busy = false;
		state.stage = 'idle';
	}
}

export function listGenerated(limit = 30) {
	if (!fs.existsSync(OUT_DIR)) return [];
	return fs.readdirSync(OUT_DIR).sort().reverse().slice(0, limit).map((id) => {
		try {
			return JSON.parse(fs.readFileSync(path.join(OUT_DIR, id, 'result.json'), 'utf8'));
		} catch {
			return null;
		}
	}).filter(Boolean);
}

export function generatedFile(id, name) {
	if (!/^[\w.-]+$/.test(id) || !/^[\w.-]+$/.test(name)) return null;
	const f = path.join(OUT_DIR, id, name);
	return fs.existsSync(f) ? f : null;
}

export function shutdown() {
	stopServer();
}
