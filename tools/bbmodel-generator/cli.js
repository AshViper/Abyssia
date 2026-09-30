#!/usr/bin/env node
// Batch generation from the command line.
//   node cli.js anglerfish                 -> output/Anglerfish/anglerfish.bbmodel, .png, _glow.png, .json
//   node cli.js all --preset low_detail
//   node cli.js anglerfish --seed 7 --variation 1 --zip

import fs from 'node:fs';
import path from 'node:path';
import { createZip, exportFiles, generateModel } from './backend/index.js';
import { DIRS, loadDefinition, loadDefinitions, loadPreset, writeExport } from './backend/creatures/node_store.js';

const HELP = `Usage: node cli.js <creature-id|all> [options]

Options
  --preset <id>        preset id (default rich_deep_sea)
  --seed <n|text>      seed (default 1)
  --variation <0..1>   individual variation amount (default 0)
  --detail <low|medium|high>
  --pixel <8|16|32>    pixel scale (px per block)
  --texture <auto|16|32|64|128|256>
  --uv <box|per_face>
  --format <modded_entity|geckolib_model|bedrock|free>
  --version <5.0|4.10> Blockbench project format
  --blocks <n>         max cube count (0 = unlimited)
  --no-glow --no-animation --no-texture --static
  --out <dir>          output directory (default ./output)
  --zip                also write <Name>.zip
  --json               print the validation report as JSON
  --force              write files even when validation reports errors
`;

function parseArgs(argv) {
	const args = { _: [] };
	for (let i = 0; i < argv.length; i++) {
		const a = argv[i];
		if (!a.startsWith('--')) {
			args._.push(a);
			continue;
		}
		const key = a.slice(2);
		if (key.startsWith('no-') || ['zip', 'json', 'static', 'help', 'force'].includes(key)) args[key] = true;
		else args[key] = argv[++i];
	}
	return args;
}

const args = parseArgs(process.argv.slice(2));
if (args.help || !args._.length) {
	console.log(HELP);
	process.exit(args.help ? 0 : 1);
}

const presetId = args.preset || 'rich_deep_sea';
const preset = loadPreset(presetId);
const settings = {
	...(preset.settings || {}),
	preset: presetId,
	seed: args.seed !== undefined ? (Number.isFinite(Number(args.seed)) ? Number(args.seed) : args.seed) : 1,
	variation: args.variation !== undefined ? Number(args.variation) : 0,
};
if (args.detail) settings.detail = args.detail;
if (args.pixel) settings.pixelScale = Number(args.pixel);
if (args.texture) settings.textureSize = args.texture === 'auto' ? 'auto' : Number(args.texture);
if (args.uv) settings.uvMode = args.uv;
if (args.format) settings.modelFormat = args.format;
if (args.version) settings.formatVersion = args.version;
if (args.blocks) settings.blockCount = Number(args.blocks);
if (args['no-glow']) settings.glowLayer = false;
if (args['no-animation']) settings.generateAnimation = false;
if (args['no-texture']) settings.generateTexture = false;
if (args.static) settings.animationReady = false;

const ids = args._[0] === 'all' ? loadDefinitions().filter((d) => !d._error).map((d) => d.id) : args._;
const outDir = args.out ? path.resolve(args.out) : DIRS.output;
let failed = 0;
for (const id of ids) {
	const def = loadDefinition(id);
	const model = generateModel(def, settings, preset);
	const { folder, files, report } = exportFiles(model);
	if (!report.ok && !args.force) {
		console.log(`ERR ${id.padEnd(16)} validation failed (${report.errors} errors) — nothing written (use --force to write anyway)`);
		for (const item of report.items.filter((i) => i.level === 'error')) console.log(`    [error] ${item.check}: ${item.message}`);
		failed++;
		continue;
	}
	const dir = writeExport(folder, files, outDir);
	if (args.zip) {
		fs.writeFileSync(path.join(outDir, `${folder}.zip`), createZip(files.map((f) => ({ name: `${folder}/${f.name}`, data: f.data }))));
	}
	const st = model.stats;
	const status = report.ok ? 'OK ' : 'ERR';
	console.log(`${status} ${id.padEnd(16)} ${String(st.cubes).padStart(3)} cubes ${String(st.bones).padStart(3)} bones  tex ${st.texture.padEnd(8)} anim ${st.animations}  ${report.errors}E/${report.warnings}W  -> ${path.relative(process.cwd(), dir)}`);
	if (args.json) console.log(JSON.stringify(report, null, 2));
	else for (const item of report.items.filter((i) => i.level !== 'info')) console.log(`    [${item.level}] ${item.check}: ${item.message}`);
}
process.exit(failed ? 2 : 0);
