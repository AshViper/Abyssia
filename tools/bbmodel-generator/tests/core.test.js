// Core building blocks: PNG, ZIP, Box UV, packing, math, animation sampling.
import assert from 'node:assert/strict';
import test from 'node:test';
import zlib from 'node:zlib';
import { createZip } from '../backend/bbmodel/zip.js';
import { uuidFactory, UUID_RE } from '../backend/bbmodel/uuid.js';
import { sampleChannel } from '../backend/animation/clip.js';
import { eulerZYXFromMatrix, rotationZYX } from '../backend/generator/math3d.js';
import { RGBAImage } from '../backend/texture/image.js';
import { crc32, encodePNG } from '../backend/texture/png.js';
import { boxFaceUVs, boxFootprint } from '../backend/uv/box_uv.js';
import { MaxRectsPacker } from '../backend/uv/packer.js';

function readPNG(bytes) {
	const buf = Buffer.from(bytes);
	assert.deepEqual([...buf.subarray(0, 8)], [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
	let o = 8;
	const chunks = [];
	while (o < buf.length) {
		const len = buf.readUInt32BE(o);
		const type = buf.toString('ascii', o + 4, o + 8);
		const data = buf.subarray(o + 8, o + 8 + len);
		assert.equal(buf.readUInt32BE(o + 8 + len), crc32(buf.subarray(o + 4, o + 8 + len)), `CRC of ${type}`);
		chunks.push({ type, data });
		o += 12 + len;
	}
	const ihdr = chunks.find((c) => c.type === 'IHDR').data;
	const width = ihdr.readUInt32BE(0), height = ihdr.readUInt32BE(4);
	const raw = zlib.inflateSync(Buffer.concat(chunks.filter((c) => c.type === 'IDAT').map((c) => c.data)));
	// Undo PNG filters
	const stride = width * 4;
	const out = Buffer.alloc(width * height * 4);
	for (let y = 0; y < height; y++) {
		const type = raw[y * (stride + 1)];
		for (let x = 0; x < stride; x++) {
			const v = raw[y * (stride + 1) + 1 + x];
			const a = x >= 4 ? out[y * stride + x - 4] : 0;
			const b = y > 0 ? out[(y - 1) * stride + x] : 0;
			const c = x >= 4 && y > 0 ? out[(y - 1) * stride + x - 4] : 0;
			let pred = 0;
			if (type === 1) pred = a;
			else if (type === 2) pred = b;
			else if (type === 3) pred = (a + b) >> 1;
			else if (type === 4) {
				const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
				pred = pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
			}
			out[y * stride + x] = (v + pred) & 0xff;
		}
	}
	return { width, height, data: out };
}

test('PNG encoder round-trips pixels through zlib and all filters', () => {
	const img = new RGBAImage(37, 23);
	for (let y = 0; y < img.height; y++) for (let x = 0; x < img.width; x++) img.set(x, y, [(x * 7) & 255, (y * 11) & 255, (x * y) & 255, (x + y) % 3 ? 255 : 0]);
	const png = readPNG(encodePNG(img));
	assert.equal(png.width, 37);
	assert.equal(png.height, 23);
	assert.deepEqual([...png.data], [...img.data]);
});

test('ZIP writer produces readable entries', () => {
	const files = [
		{ name: 'Anglerfish/anglerfish.json', data: '{"a":1}'.repeat(50) },
		{ name: 'Anglerfish/bin.dat', data: new Uint8Array([1, 2, 3, 4, 5]) },
	];
	const zip = Buffer.from(createZip(files));
	const eocd = zip.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
	assert.ok(eocd > 0);
	assert.equal(zip.readUInt16LE(eocd + 10), 2);
	let o = 0;
	for (const f of files) {
		assert.equal(zip.readUInt32LE(o), 0x04034b50);
		const method = zip.readUInt16LE(o + 8);
		const size = zip.readUInt32LE(o + 18);
		const nameLen = zip.readUInt16LE(o + 26);
		assert.equal(zip.toString('utf8', o + 30, o + 30 + nameLen), f.name);
		const body = zip.subarray(o + 30 + nameLen, o + 30 + nameLen + size);
		const data = method === 8 ? zlib.inflateRawSync(body) : body;
		assert.deepEqual([...data], [...(typeof f.data === 'string' ? Buffer.from(f.data) : f.data)]);
		o += 30 + nameLen + size;
	}
});

test('Box UV faces match Blockbench / Minecraft layout', () => {
	// 8x8x8 cube at 0,0 (well known Blockbench output)
	const f = boxFaceUVs([8, 8, 8], [0, 0]);
	assert.deepEqual(f.north, [8, 8, 16, 16]);
	assert.deepEqual(f.east, [0, 8, 8, 16]);
	assert.deepEqual(f.south, [24, 8, 32, 16]);
	assert.deepEqual(f.west, [16, 8, 24, 16]);
	assert.deepEqual(f.up, [16, 8, 8, 0]);
	assert.deepEqual(f.down, [24, 0, 16, 8]);
	// Mirror swaps east / west and flips horizontally
	const m = boxFaceUVs([4, 6, 2], [10, 20], true);
	assert.deepEqual(m.east, [18, 22, 16, 28]);
	assert.deepEqual(m.west, [12, 22, 10, 28]);
});

test('Box UV footprint of flat fins only covers painted faces', () => {
	assert.deepEqual(boxFootprint([0, 7, 4]), { x: 0, y: 4, w: 8, h: 7 });
	assert.deepEqual(boxFootprint([6, 0, 3]), { x: 3, y: 0, w: 12, h: 3 });
	assert.deepEqual(boxFootprint([3, 2, 1]), { x: 0, y: 0, w: 8, h: 3 });
});

test('MaxRects packer never overlaps and respects minimum positions', () => {
	const p = new MaxRectsPacker(64, 64);
	const placed = [];
	const sizes = [[20, 10], [7, 7], [30, 5], [3, 12], [16, 16], [8, 2], [12, 9], [5, 5], [22, 6]];
	for (const [w, h] of sizes) {
		const pos = p.insert(w, h, { minY: 3 });
		assert.ok(pos, `fits ${w}x${h}`);
		assert.ok(pos.y >= 3);
		placed.push({ ...pos, w, h });
	}
	for (let i = 0; i < placed.length; i++) for (let j = i + 1; j < placed.length; j++) {
		const a = placed[i], b = placed[j];
		const overlap = a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
		assert.ok(!overlap, `rects ${i} and ${j} overlap`);
	}
});

test('ZYX Euler decomposition round-trips', () => {
	for (const r of [[10, 20, 30], [-45, 5, 80], [0, 0, 0], [170, -30, 12]]) {
		const back = eulerZYXFromMatrix(rotationZYX(r));
		const m1 = rotationZYX(r), m2 = rotationZYX(back);
		m1.forEach((v, i) => assert.ok(Math.abs(v - m2[i]) < 1e-9));
	}
});

test('Catmull-Rom sampling matches keyframes and loops smoothly', () => {
	const keys = [0, 0.25, 0.5, 0.75, 1].map((t) => ({ time: t, value: [Math.sin(t * Math.PI * 2) * 10, 0, 0], interpolation: 'catmullrom' }));
	assert.equal(sampleChannel(keys, 0.25, 'loop')[0], 10);
	const mid = sampleChannel(keys, 0.125, 'loop')[0];
	assert.ok(mid > 6 && mid < 8, `smooth value ${mid}`);
	const linear = [{ time: 0, value: [0, 0, 0], interpolation: 'linear' }, { time: 1, value: [10, 0, 0], interpolation: 'linear' }];
	assert.equal(sampleChannel(linear, 0.3, 'once')[0], 3);
});

test('Deterministic UUIDs are valid and stable', () => {
	const a = uuidFactory('x');
	const b = uuidFactory('x');
	assert.equal(a('cube:body'), b('cube:body'));
	assert.notEqual(a('cube:body'), a('cube:head'));
	assert.match(a('group:root'), UUID_RE);
});
