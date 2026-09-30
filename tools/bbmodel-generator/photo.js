#!/usr/bin/env node
// Photo -> creature definition (template "photo") from the command line.
//   node photo.js samples/stalked_ascidian.png --id stalked_sea_squirt --crop 235,90,195,370 --ground
//   node photo.js photo.png --id sponge --height 20 --generate      (also runs cli.js <id>)
// Writes definitions/<id>.json and output/_photo/<id>_mask.png (segmentation check image).
// PNG only here; the GUI (Import Image) reads any image format the browser can decode.

import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import zlib from 'node:zlib';
import { DIRS, saveDefinition } from './backend/creatures/node_store.js';
import { checkDefinition } from './backend/creatures/schema.js';
import { DEFAULT_THRESHOLD, decodePNG, definitionFromImage, maskPreview } from './backend/image/index.js';
import { listTemplates } from './backend/templates/index.js';
import { encodePNG } from './backend/texture/png.js';

const HELP = `Usage: node photo.js <image.png> --id <creature_id> [options]

Options
  --name <text>        display name (default from id)
  --crop x,y,w,h       subject rectangle in image pixels (default: whole image)
  --seed x,y           point on the subject, 0..1 inside the crop (default 0.5,0.5)
  --height <units>     model height in model units = grid rows (default 24, max 64)
  --colors <n>         palette size 1..16 (default 8)
  --threshold <n>      subject threshold in background-noise units (default ${DEFAULT_THRESHOLD}; lower = more)
  --ground             subject stands on the crop's bottom edge (follows a faint stalk down)
  --depth <ratio>      depth / width of each slice (default 1)
  --smooth <n>         mask smoothing multiplier (default 1, 0 = none)
  --dry-run            print the grid, write nothing
  --force              overwrite an existing definition that is not a photo model
  --generate           also generate the model (node cli.js <id>)
  --json               print {definition file, grid, palette, threshold} as JSON
`;

function parseArgs(argv) {
	const args = { _: [] };
	for (let i = 0; i < argv.length; i++) {
		const a = argv[i];
		if (!a.startsWith('--')) args._.push(a);
		else if (['ground', 'dry-run', 'generate', 'json', 'help', 'force'].includes(a.slice(2))) args[a.slice(2)] = true;
		else args[a.slice(2)] = argv[++i];
	}
	return args;
}

const nums = (s, n) => {
	const v = String(s).split(',').map(Number);
	if (v.length !== n || v.some((x) => !Number.isFinite(x))) throw new Error(`Expected ${n} comma-separated numbers, got "${s}"`);
	return v;
};

const args = parseArgs(process.argv.slice(2));
if (args.help || !args._.length || !args.id) {
	console.log(HELP);
	process.exit(args.help ? 0 : 1);
}
try {
	const file = path.resolve(args._[0]);
	const img = decodePNG(fs.readFileSync(file), (d) => zlib.inflateSync(d));
	const crop = args.crop ? (([x, y, w, h]) => ({ x, y, w, h }))(nums(args.crop, 4)) : null;
	const seed = args.seed ? (([x, y]) => ({ x, y }))(nums(args.seed, 2)) : undefined;
	const { definition, segmentation, profile } = definitionFromImage(img, {
		id: args.id,
		name: args.name,
		crop,
		seed,
		rows: args.height ? Number(args.height) : 24,
		colors: args.colors ? Number(args.colors) : 8,
		threshold: args.threshold !== undefined ? Number(args.threshold) : null,
		ground: !!args.ground,
		depthRatio: args.depth ? Number(args.depth) : 1,
		smooth: args.smooth !== undefined ? Number(args.smooth) : 1,
		source: path.relative(process.cwd(), file).replace(/\\/g, '/'),
	});
	const problems = checkDefinition(definition, listTemplates().map((t) => t.id)).filter((p) => p.level === 'error');
	if (problems.length) throw new Error(problems.map((p) => `${p.path}: ${p.message}`).join('; '));

	let defFile = null, maskFile = null;
	if (!args['dry-run']) {
		const existing = path.join(DIRS.definitions, `${definition.id}.json`);
		if (fs.existsSync(existing) && !args.force && JSON.parse(fs.readFileSync(existing, 'utf8')).template !== 'photo') {
			throw new Error(`definitions/${definition.id}.json exists and is not a photo model (use another --id or --force)`);
		}
		defFile = saveDefinition(definition);
		const dir = path.join(DIRS.output, '_photo');
		fs.mkdirSync(dir, { recursive: true });
		maskFile = path.join(dir, `${definition.id}_mask.png`);
		fs.writeFileSync(maskFile, encodePNG(maskPreview(segmentation)));
	}
	const grid = definition.params.photo.grid;
	if (args.json) {
		console.log(JSON.stringify({ definition: defFile, mask: maskFile, threshold: segmentation.threshold, palette: profile.palette, grid }, null, 2));
	} else {
		console.log(`${definition.id}: ${grid.length} rows × ${grid[0].length} cols, ${profile.palette.length} colours, threshold ${segmentation.threshold}`);
		console.log(grid.map((r) => '  ' + r).join('\n'));
		if (defFile) console.log(`-> ${path.relative(process.cwd(), defFile)}\n-> ${path.relative(process.cwd(), maskFile)} (check the red outline)`);
	}
	if (args.generate && defFile) {
		const r = spawnSync(process.execPath, [fileURLToPath(new URL('./cli.js', import.meta.url)), definition.id], { stdio: 'inherit' });
		process.exit(r.status ?? 1);
	}
} catch (err) {
	console.error(`photo: ${err.message}`);
	process.exit(1);
}
