// Node-only file access for definitions, presets and exports (not imported by the browser).

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
export const DIRS = {
	definitions: path.join(ROOT, 'definitions'),
	presets: path.join(ROOT, 'presets'),
	output: path.join(ROOT, 'output'),
};

function readJsonDir(dir) {
	if (!fs.existsSync(dir)) return [];
	return fs
		.readdirSync(dir)
		.filter((f) => f.endsWith('.json'))
		.sort()
		.map((f) => {
			const file = path.join(dir, f);
			try {
				return { ...JSON.parse(fs.readFileSync(file, 'utf8')), _file: f };
			} catch (err) {
				return { id: path.basename(f, '.json'), _file: f, _error: err.message };
			}
		});
}

export const loadDefinitions = () => readJsonDir(DIRS.definitions);
export const loadPresets = () => readJsonDir(DIRS.presets);

export function loadDefinition(id) {
	const def = loadDefinitions().find((d) => d.id === id);
	if (!def) throw new Error(`Definition "${id}" not found in ${DIRS.definitions}`);
	if (def._error) throw new Error(`definitions/${def._file}: ${def._error}`);
	return strip(def);
}

export function loadPreset(id) {
	const preset = loadPresets().find((p) => p.id === id);
	if (!preset) throw new Error(`Preset "${id}" not found in ${DIRS.presets}`);
	return strip(preset);
}

export function saveDefinition(def) {
	if (!/^[a-z][a-z0-9_]*$/.test(def.id || '')) throw new Error('Invalid definition id');
	const file = path.join(DIRS.definitions, `${def.id}.json`);
	fs.writeFileSync(file, JSON.stringify(strip(def), null, '\t') + '\n');
	return file;
}

/** Writes export files into output/<folder>/ (folder name sanitised). */
export function writeExport(folder, files, outDir = DIRS.output) {
	const safe = String(folder).replace(/[^A-Za-z0-9_-]/g, '') || 'Creature';
	const dir = path.join(outDir, safe);
	fs.mkdirSync(dir, { recursive: true });
	for (const f of files) {
		const name = path.basename(f.name);
		fs.writeFileSync(path.join(dir, name), typeof f.data === 'string' ? f.data : Buffer.from(f.data));
	}
	return dir;
}

function strip(obj) {
	const out = { ...obj };
	delete out._file;
	delete out._error;
	return out;
}
