// Minimal PNG decoder (8-bit, non-interlaced; grey / grey+alpha / RGB / RGBA / palette).
// Isomorphic: the zlib inflate is injected (Node: zlib.inflateSync). The browser GUI decodes
// images with a canvas instead, so this is only needed by the CLI and tests.

const SIGNATURE = [137, 80, 78, 71, 13, 10, 26, 10];
const CHANNELS = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 };

/**
 * @param {Uint8Array} bytes PNG file
 * @param {(data: Uint8Array) => Uint8Array} inflate zlib stream inflater
 * @returns {{width:number, height:number, data:Uint8ClampedArray}} RGBA
 */
export function decodePNG(bytes, inflate) {
	const u8 = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
	if (!SIGNATURE.every((v, i) => u8[i] === v)) throw new Error('Not a PNG file (convert JPEG/WebP to PNG, or use the GUI which reads any image)');
	const view = new DataView(u8.buffer, u8.byteOffset, u8.byteLength);
	let pos = 8;
	let header = null;
	let palette = null;
	let trns = null;
	const idat = [];
	while (pos + 8 <= u8.length) {
		const len = view.getUint32(pos);
		const type = String.fromCharCode(u8[pos + 4], u8[pos + 5], u8[pos + 6], u8[pos + 7]);
		const body = u8.subarray(pos + 8, pos + 8 + len);
		if (type === 'IHDR') {
			header = { width: view.getUint32(pos + 8), height: view.getUint32(pos + 12), depth: body[8], color: body[9], interlace: body[12] };
		} else if (type === 'PLTE') palette = body;
		else if (type === 'tRNS') trns = body;
		else if (type === 'IDAT') idat.push(body);
		else if (type === 'IEND') break;
		pos += 12 + len;
	}
	if (!header) throw new Error('PNG has no IHDR chunk');
	const { width, height, depth, color, interlace } = header;
	if (interlace) throw new Error('Interlaced PNGs are not supported (re-save without interlacing)');
	if (depth !== 8) throw new Error(`${depth}-bit PNGs are not supported (re-save as 8-bit)`);
	const channels = CHANNELS[color];
	if (!channels) throw new Error(`Unsupported PNG colour type ${color}`);
	if (color === 3 && !palette) throw new Error('Palette PNG without PLTE chunk');

	const total = idat.reduce((a, c) => a + c.length, 0);
	const compressed = new Uint8Array(total);
	let o = 0;
	for (const c of idat) {
		compressed.set(c, o);
		o += c.length;
	}
	const raw = inflate(compressed);
	const stride = width * channels;
	const pixels = new Uint8Array(stride * height);
	let prev = new Uint8Array(stride);
	for (let y = 0; y < height; y++) {
		const filter = raw[y * (stride + 1)];
		const line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
		const out = pixels.subarray(y * stride, (y + 1) * stride);
		for (let x = 0; x < stride; x++) {
			const a = x >= channels ? out[x - channels] : 0;
			const b = prev[x];
			const c = x >= channels ? prev[x - channels] : 0;
			let v = line[x];
			if (filter === 1) v += a;
			else if (filter === 2) v += b;
			else if (filter === 3) v += (a + b) >> 1;
			else if (filter === 4) {
				const p = a + b - c;
				const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
				v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
			}
			out[x] = v & 255;
		}
		prev = out;
	}

	const data = new Uint8ClampedArray(width * height * 4);
	for (let i = 0, j = 0; i < width * height; i++, j += channels) {
		let r, g, b, a = 255;
		if (color === 0) r = g = b = pixels[j];
		else if (color === 4) (r = g = b = pixels[j]), (a = pixels[j + 1]);
		else if (color === 2) (r = pixels[j]), (g = pixels[j + 1]), (b = pixels[j + 2]);
		else if (color === 6) (r = pixels[j]), (g = pixels[j + 1]), (b = pixels[j + 2]), (a = pixels[j + 3]);
		else {
			const k = pixels[j];
			r = palette[k * 3];
			g = palette[k * 3 + 1];
			b = palette[k * 3 + 2];
			if (trns && k < trns.length) a = trns[k];
		}
		data[i * 4] = r;
		data[i * 4 + 1] = g;
		data[i * 4 + 2] = b;
		data[i * 4 + 3] = a;
	}
	return { width, height, data };
}
