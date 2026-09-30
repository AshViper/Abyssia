// Photo pipeline: PNG decode, segmentation, grid, photo template.
import assert from 'node:assert/strict';
import test from 'node:test';
import zlib from 'node:zlib';
import { exportFiles, generateModel } from '../backend/index.js';
import { checkDefinition } from '../backend/creatures/schema.js';
import { decodePNG, definitionFromImage, segmentSubject, trimGrid } from '../backend/image/index.js';
import { gridRows, segmentRows } from '../backend/templates/photo.js';
import { listTemplates } from '../backend/templates/index.js';
import { RGBAImage } from '../backend/texture/image.js';
import { encodePNG } from '../backend/texture/png.js';

/** Dark water with a pale bulb (r = 30) on a 10 px stalk standing on grainy sand. */
function syntheticPhoto() {
	const img = new RGBAImage(200, 240);
	let seed = 7;
	const rnd = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
	for (let y = 0; y < img.height; y++) {
		for (let x = 0; x < img.width; x++) {
			const sand = y > 190;
			const n = (rnd() - 0.5) * (sand ? 30 : 6);
			let c = sand ? [150 + n, 145 + n, 135 + n] : [18 + n, 24 + n, 30 + n];
			const inBulb = Math.hypot(x - 100, y - 70) < 30;
			const inStalk = Math.abs(x - 100) <= 5 && y >= 70 && y < 230;
			if (inBulb) c = [205, 220, 230];
			else if (inStalk) c = [120, 130, 140];
			img.set(x, y, [c[0], c[1], c[2], 255]);
		}
	}
	return img;
}

const errorsOf = (report) => report.items.filter((i) => i.level === 'error').map((i) => `${i.check}: ${i.message}`);

test('decodePNG round-trips encodePNG output', () => {
	const img = syntheticPhoto();
	const back = decodePNG(encodePNG(img), (d) => zlib.inflateSync(d));
	assert.equal(back.width, img.width);
	assert.equal(back.height, img.height);
	assert.deepEqual([...back.data.slice(0, 400)], [...img.data.slice(0, 400)]);
	assert.throws(() => decodePNG(new Uint8Array([1, 2, 3, 4, 5, 6, 7, 8]), zlib.inflateSync), /Not a PNG/);
});

test('segmentation finds the bulb and, with ground, the stalk down to the crop bottom', () => {
	const img = syntheticPhoto();
	const seg = segmentSubject(img, { ground: true });
	const at = (x, y) => seg.mask[Math.round(y / seg.scale) * seg.width + Math.round(x / seg.scale)];
	assert.equal(at(100, 70), 1, 'bulb centre');
	assert.equal(at(100, 150), 1, 'stalk over water');
	assert.equal(at(100, 220), 1, 'stalk over sand');
	assert.equal(at(20, 20), 0, 'water');
	assert.equal(at(20, 220), 0, 'sand');
});

test('grid: wide head rows on top, narrow stalk rows below; definition is valid', () => {
	const { definition } = definitionFromImage(syntheticPhoto(), { id: 'test_photo', rows: 24, ground: true });
	assert.deepEqual(checkDefinition(definition, listTemplates().map((t) => t.id)).filter((p) => p.level === 'error'), []);
	const rows = gridRows(definition.params.photo.grid);
	const widest = Math.max(...rows.map((r) => r.w));
	assert.ok(rows[Math.floor(rows.length * 0.2)].w >= widest * 0.8, 'head is wide');
	assert.ok(rows[rows.length - 2].w <= widest * 0.35, 'stalk is narrow');
	assert.ok(Object.keys(definition.palette).includes('p0'));
});

test('trimGrid and segmentRows', () => {
	assert.deepEqual(trimGrid(['....', '.01.', '..1.', '....']), ['01', '.1']);
	const items = [4, 4, 4, 10, 10, 10].map((w, i) => ({ r: i, w, c: 5 }));
	assert.deepEqual(segmentRows(items, 2), [[0, 3], [3, 6]]);
	assert.equal(segmentRows(items, 10).length, 6);
});

test('photo template builds valid models for every detail level / pixel scale / budget', () => {
	const { definition } = definitionFromImage(syntheticPhoto(), { id: 'test_photo', rows: 24, ground: true });
	for (const settings of [{ detail: 'low' }, { detail: 'medium' }, { detail: 'high' }, { pixelScale: 8 }, { pixelScale: 32 }, { blockCount: 4 }, { size: { height: 40 } }, { variation: 1, seed: 3 }]) {
		const model = generateModel(definition, settings);
		const { report } = exportFiles(model);
		assert.deepEqual(errorsOf(report), [], JSON.stringify(settings));
		assert.ok(model.geometry.bones.some((b) => b.role === 'head'), 'head bone detected');
		assert.ok(model.animations.some((a) => a.name === 'sway'));
	}
});
