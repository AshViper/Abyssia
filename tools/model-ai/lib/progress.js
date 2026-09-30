// Training / evaluation progress read from the WSL run directories (Windows side, via \\wsl.localhost).
// Used by `node ai.js progress` and the dashboard (dashboard.js).

import { execFile } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), '..');
const DISTRO = process.env.MODEL_AI_DISTRO || 'Ubuntu-24.04';

/** \\wsl.localhost\<distro>\home\<user>\model-ai\runs (first home that has one), or MODEL_AI_RUNS. */
export function runsDir() {
	if (process.env.MODEL_AI_RUNS) return process.env.MODEL_AI_RUNS;
	const home = `\\\\wsl.localhost\\${DISTRO}\\home`;
	try {
		for (const user of fs.readdirSync(home)) {
			const dir = path.join(home, user, 'model-ai', 'runs');
			if (fs.existsSync(dir)) return dir;
		}
	} catch {
		/* distro not reachable */
	}
	return null;
}

const readText = (f) => {
	try {
		return fs.readFileSync(f, 'utf8');
	} catch {
		return null;
	}
};
const readJson = (f) => {
	const t = readText(f);
	try {
		return t ? JSON.parse(t) : null;
	} catch {
		return null;
	}
};

function parseDuration(s) {
	const p = s.split(':').map(Number);
	return p.reduce((acc, v) => acc * 60 + v, 0);
}

/**
 * Trainer progress from the tqdm bars printed after the dataset header ({"rows": ...}).
 * Unlabelled bars only ("Loading weights", "Map" etc. are skipped); the bar with the largest total is
 * training, a later bar with a smaller total means an evaluation pass is running.
 * @returns {{step, total, elapsed_s, eta_s, sec_per_step, evaluating}|null}
 */
function parseProgress(log) {
	const start = log.indexOf('{"rows":');
	if (start < 0) return null;
	const text = log.slice(start).replace(/\r/g, '\n');
	const re = /^\s*\d+%\|[^|\n]*\|\s*(\d+)\/(\d+) \[([\d:]+)<([\d:?]+),\s*([\d.]+)(s\/it|it\/s)\]/gm;
	const bars = [];
	let m;
	while ((m = re.exec(text))) bars.push(m);
	if (!bars.length) return null;
	const total = Math.max(...bars.map((b) => Number(b[2])));
	const last = bars.filter((b) => Number(b[2]) === total).at(-1);
	const rate = Number(last[5]);
	return {
		step: Number(last[1]),
		total,
		elapsed_s: parseDuration(last[3]),
		eta_s: last[4].includes('?') ? null : parseDuration(last[4]),
		sec_per_step: last[6] === 's/it' ? rate : rate > 0 ? 1 / rate : null,
		evaluating: Number(bars.at(-1)[2]) !== total,
	};
}

function phaseOf(log, logLines, progress, exit) {
	if (exit !== null) return exit === 0 ? 'done' : 'failed';
	if (/Traceback|CUDA out of memory|Error:/.test(log.slice(-5000))) return 'failed';
	if (progress) return progress.step >= progress.total ? 'finishing' : progress.evaluating ? 'evaluating' : 'training';
	if (/"rows":/.test(log)) return 'starting';
	return log ? 'loading' : 'queued';
}

export function runStatus(name, dir = runsDir()) {
	if (!dir) return { name, phase: 'unreachable' };
	const run = path.join(dir, name);
	const log = readText(path.join(run, 'train.log')) || '';
	const exitText = readText(path.join(run, 'train.log.exit'));
	const exit = exitText === null ? null : Number(exitText.trim());
	const logLines = (readText(path.join(run, 'train_log.jsonl')) || '').split('\n').filter(Boolean).map((l) => {
		try {
			return JSON.parse(l);
		} catch {
			return null;
		}
	}).filter(Boolean);
	const progress = parseProgress(log);
	const header = log.match(/\{"rows": \d+.*\}/)?.[0];
	let stat = null;
	try {
		stat = fs.statSync(path.join(run, 'train.log'));
	} catch {
		/* not started */
	}
	const errors = log.split(/\r?\n/).filter((l) => /Traceback|Error|Killed/.test(l) && !/FutureWarning|UserWarning/.test(l)).slice(-3);
	return {
		name,
		phase: phaseOf(log, logLines, progress, exit),
		exit,
		progress,
		percent: progress ? Math.round((progress.step / progress.total) * 1000) / 10 : 0,
		log_updated: stat ? stat.mtime.toISOString() : null,
		stale_min: stat ? Math.round((Date.now() - stat.mtimeMs) / 6000) / 10 : null,
		data: header ? JSON.parse(header) : null,
		loss: logLines.filter((l) => l.loss !== undefined).map((l) => ({ step: l.step, loss: Number(l.loss), lr: Number(l.learning_rate) })),
		eval_loss: logLines.filter((l) => l.eval_loss !== undefined).map((l) => ({ step: l.step, loss: Number(l.eval_loss) })),
		result: readJson(path.join(run, 'run.json')),
		errors,
		log_tail: log.replace(/\r/g, '\n').split('\n').filter((l) => l.trim() && !/^\s*W\d{4}|Warning|original_setattr/.test(l)).slice(-6),
	};
}

export function listRuns(dir = runsDir()) {
	if (!dir) return [];
	try {
		return fs.readdirSync(dir).filter((n) => fs.existsSync(path.join(dir, n, 'train.log'))).map((n) => ({ name: n, mtime: fs.statSync(path.join(dir, n, 'train.log')).mtimeMs })).sort((a, b) => b.mtime - a.mtime).map((r) => r.name);
	} catch {
		return [];
	}
}

/** Evaluation reports written by `node ai.js eval --out data/<ver>/report_*.json`. */
export function listReports() {
	const out = [];
	const dataDir = path.join(ROOT, 'data');
	const baseDir = path.join(ROOT, 'eval', 'baselines');
	const add = (file, label) => {
		const r = readJson(file);
		if (r?.summary) out.push({ label, file: path.relative(ROOT, file), mtime: fs.statSync(file).mtimeMs, summary: { all: r.summary.all, by_task: r.summary.by_task } });
	};
	for (const v of fs.existsSync(dataDir) ? fs.readdirSync(dataDir) : []) {
		const d = path.join(dataDir, v);
		if (!fs.statSync(d).isDirectory()) continue;
		for (const f of fs.readdirSync(d).filter((x) => /^report_.*\.json$/.test(x))) add(path.join(d, f), `${v}/${f.replace(/^report_|\.json$/g, '')}`);
	}
	for (const f of fs.existsSync(baseDir) ? fs.readdirSync(baseDir).filter((x) => x.endsWith('.json')) : []) add(path.join(baseDir, f), `baseline/${f.replace('.json', '')}`);
	return out.sort((a, b) => b.mtime - a.mtime);
}

export function gpuStatus() {
	return new Promise((resolve) => {
		execFile('nvidia-smi', ['--query-gpu=name,utilization.gpu,memory.used,memory.total,temperature.gpu,power.draw', '--format=csv,noheader,nounits'], { timeout: 4000 }, (err, stdout) => {
			if (err) return resolve(null);
			const [name, util, used, total, temp, power] = stdout.trim().split(/,\s*/);
			resolve({ name, util: Number(util), mem_used_mb: Number(used), mem_total_mb: Number(total), temp_c: Number(temp), power_w: Number(power) });
		});
	});
}

/** Last `bytes` of a file as text (cheap enough to poll every second over \\wsl.localhost). */
function readTail(file, bytes = 8192) {
	let fd;
	try {
		fd = fs.openSync(file, 'r');
		const size = fs.fstatSync(fd).size;
		const len = Math.min(size, bytes);
		const buf = Buffer.alloc(len);
		fs.readSync(fd, buf, 0, len, size - len);
		return buf.toString('utf8');
	} catch {
		return '';
	} finally {
		if (fd !== undefined) fs.closeSync(fd);
	}
}

/**
 * Small live state for the dashboard stream (polled every second): live.json written by
 * train_sft.py every step + the last logged steps + phase. Unchanged state -> identical JSON.
 */
export function liveStatus(name, dir = runsDir()) {
	if (!dir || !name) return { run: name || null, phase: 'unreachable' };
	const run = path.join(dir, name);
	const live = readJson(path.join(run, 'live.json'));
	const exitText = readText(path.join(run, 'train.log.exit'));
	const exit = exitText === null ? null : Number(exitText.trim());
	const recent = readTail(path.join(run, 'train_log.jsonl'), 6000).split('\n').slice(1).filter(Boolean).map((l) => {
		try {
			const j = JSON.parse(l);
			return { step: j.step, loss: j.loss, grad_norm: j.grad_norm, lr: j.learning_rate, eval_loss: j.eval_loss };
		} catch {
			return null;
		}
	}).filter(Boolean).slice(-25);
	const logTail = readTail(path.join(run, 'train.log'), 3000);
	let phase;
	if (exit !== null) phase = exit === 0 ? 'done' : 'failed';
	else if (/Traceback|CUDA out of memory/.test(logTail)) phase = 'failed';
	else if (live?.step > 0) phase = live.step >= live.total ? 'finishing' : /\|\s*\d+\/\d+ \[/.test(logTail.split('\n').slice(-2).join('')) && /eval/i.test(logTail.slice(-400)) ? 'evaluating' : 'training';
	else phase = logTail ? (/"rows":/.test(readTail(path.join(run, 'train.log'), 200000)) ? 'starting' : 'loading') : 'queued';
	return { run: name, phase, exit, live, recent };
}

/** Reads a file in the WSL HF cache; snapshot files are Linux symlinks that Windows cannot follow. */
function readWslLinked(file, depth = 0) {
	try {
		return fs.readFileSync(file, 'utf8');
	} catch (err) {
		if (depth > 3) throw err;
		const target = fs.readlinkSync(file).split('/').join(path.sep);
		return readWslLinked(path.isAbsolute(target) ? target : path.join(path.dirname(file), target), depth + 1);
	}
}

/**
 * Architecture of the base model (config.json in the WSL Hugging Face cache) + LoRA settings of a run,
 * with parameter counts for the structure view.
 */
export function modelInfo(runName) {
	const dir = runsDir();
	const home = dir ? path.dirname(dir) : null;
	const run = dir && runName ? readJson(path.join(dir, runName, 'run.json')) : null;
	const modelId = run?.model || 'unsloth/Qwen2.5-Coder-3B-Instruct-bnb-4bit';
	let config = dir && runName ? readJson(path.join(dir, runName, 'model_config.json')) : null;
	const rank = run?.args?.rank || config?._lora_rank || 32;
	let source = config ? `runs/${runName}/model_config.json` : 'fallback';
	if (!config) try {
		const snaps = path.join(home, 'hf', 'hub', `models--${modelId.replace('/', '--')}`, 'snapshots');
		const snap = path.join(snaps, fs.readdirSync(snaps)[0]);
		config = JSON.parse(readWslLinked(path.join(snap, 'config.json')));
		source = 'config.json';
	} catch {
		// Qwen2.5-3B defaults, used only when the cache is unreachable
		config = { architectures: ['Qwen2ForCausalLM'], hidden_size: 2048, num_hidden_layers: 36, num_attention_heads: 16, num_key_value_heads: 2, intermediate_size: 11008, vocab_size: 151936, tie_word_embeddings: true };
	}
	const h = config.hidden_size, ff = config.intermediate_size, heads = config.num_attention_heads, kv = config.num_key_value_heads;
	const hd = h / heads;
	const proj = {
		q_proj: [h, heads * hd], k_proj: [h, kv * hd], v_proj: [h, kv * hd], o_proj: [heads * hd, h],
		gate_proj: [h, ff], up_proj: [h, ff], down_proj: [ff, h],
	};
	const perLayer = Object.values(proj).reduce((s, [i, o]) => s + i * o, 0) + 2 * h;
	const loraPerLayer = Object.values(proj).reduce((s, [i, o]) => s + rank * (i + o), 0);
	const embed = config.vocab_size * h;
	const total = perLayer * config.num_hidden_layers + embed * (config.tie_word_embeddings ? 1 : 2) + h;
	return {
		model: modelId,
		source,
		arch: config.architectures?.[0],
		hidden: h,
		layers: config.num_hidden_layers,
		heads,
		kv_heads: kv,
		head_dim: hd,
		ffn: ff,
		vocab: config.vocab_size,
		tie_embeddings: !!config.tie_word_embeddings,
		quant: config.quantization_config?.bnb_4bit_quant_type || (/bnb-4bit/.test(modelId) ? 'nf4' : null),
		lora: { rank, alpha: rank, targets: Object.keys(proj) },
		proj,
		params: { per_layer: perLayer, embed, total, lora_per_layer: loraPerLayer, lora_total: loraPerLayer * config.num_hidden_layers },
	};
}

/** Phase-2 pass rule: valid >= 0.9 and score clearly (+0.05) above the keyword baseline of that split. */
export const PHASE2 = { valid: 0.9, margin: 0.05, keyword: { test: 0.511, real: 0.447 } };

/**
 * State of each pipeline stage for one run (data version = run name when it exists):
 * data -> train -> predict -> eval -> judge. status: done | active | pending | failed
 */
export function pipeline(run) {
	const ver = run && fs.existsSync(path.join(ROOT, 'data', run.name)) ? run.name : 'v1';
	const d = path.join(ROOT, 'data', ver);
	const stats = readJson(path.join(d, 'stats.json'));
	const trainStatus = !run ? 'pending' : run.phase === 'done' ? 'done' : run.phase === 'failed' ? 'failed' : 'active';
	const splits = ['test', 'real'];
	const preds = splits.filter((s) => fs.existsSync(path.join(d, `pred_${run?.name}_${s}.jsonl`)));
	const reports = Object.fromEntries(splits.map((s) => [s, readJson(path.join(d, `report_${run?.name}_${s}.json`))?.summary || null]));
	const done = splits.filter((s) => reports[s]);
	const verdicts = done.map((s) => {
		const t = reports[s].by_task.text2spec;
		return { split: s, valid: t.valid_rate, score: t.score, target: PHASE2.keyword[s] + PHASE2.margin, pass: t.valid_rate >= PHASE2.valid && t.score >= PHASE2.keyword[s] + PHASE2.margin };
	});
	return {
		ver,
		stages: [
			{ id: 'data', status: stats ? 'done' : 'pending', detail: stats ? `${(stats.records.train + stats.records.val + stats.records.test).toLocaleString()}件` : '未作成' },
			{ id: 'train', status: trainStatus, detail: run ? (run.phase === 'done' ? '完了' : `${run.percent.toFixed(1)}%`) : '未開始' },
			{ id: 'predict', status: preds.length === 2 ? 'done' : trainStatus === 'done' ? 'active' : 'pending', detail: preds.length ? `${preds.join(' / ')}` : '学習後' },
			{ id: 'eval', status: done.length === 2 ? 'done' : preds.length ? 'active' : 'pending', detail: done.length ? done.join(' / ') : '予測後' },
			{ id: 'judge', status: verdicts.length === 2 ? (verdicts.every((v) => v.pass) ? 'done' : 'failed') : 'pending', detail: verdicts.length === 2 ? (verdicts.every((v) => v.pass) ? '合格' : '未達') : '採点後' },
		],
		verdicts,
	};
}

export async function snapshot(runName) {
	const dir = runsDir();
	const runs = listRuns(dir);
	const name = runName || runs[0];
	const run = name ? runStatus(name, dir) : null;
	return { time: new Date().toISOString(), runs_dir: dir, runs, run, pipeline: pipeline(run), gpu: await gpuStatus(), reports: listReports() };
}
