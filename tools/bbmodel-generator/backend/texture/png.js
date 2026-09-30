// Dependency-free PNG encoder (RGBA, 8 bit) with a small LZ77 + fixed-Huffman deflate.
// Pure JS so the browser and Node write byte-identical files.

let CRC_TABLE = null;
function crcTable() {
	if (CRC_TABLE) return CRC_TABLE;
	CRC_TABLE = new Uint32Array(256);
	for (let n = 0; n < 256; n++) {
		let c = n;
		for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
		CRC_TABLE[n] = c >>> 0;
	}
	return CRC_TABLE;
}

export function crc32(bytes, crc = 0) {
	const table = crcTable();
	let c = (crc ^ 0xffffffff) >>> 0;
	for (let i = 0; i < bytes.length; i++) c = table[(c ^ bytes[i]) & 0xff] ^ (c >>> 8);
	return (c ^ 0xffffffff) >>> 0;
}

export function adler32(bytes) {
	let a = 1, b = 0;
	for (let i = 0; i < bytes.length; i++) {
		a = (a + bytes[i]) % 65521;
		b = (b + a) % 65521;
	}
	return ((b << 16) | a) >>> 0;
}

class BitWriter {
	constructor(capacity = 1024) {
		this.buf = new Uint8Array(capacity);
		this.pos = 0;
		this.bit = 0;
		this.acc = 0;
	}
	#ensure(n) {
		if (this.pos + n <= this.buf.length) return;
		let size = this.buf.length * 2;
		while (size < this.pos + n) size *= 2;
		const next = new Uint8Array(size);
		next.set(this.buf);
		this.buf = next;
	}
	/** Writes `count` bits of `value`, least significant bit first. */
	write(value, count) {
		for (let i = 0; i < count; i++) {
			this.acc |= ((value >>> i) & 1) << this.bit;
			if (++this.bit === 8) {
				this.#ensure(1);
				this.buf[this.pos++] = this.acc;
				this.acc = 0;
				this.bit = 0;
			}
		}
	}
	/** Writes a Huffman code (most significant bit first). */
	writeCode(code, length) {
		let rev = 0;
		for (let i = 0; i < length; i++) rev |= ((code >>> i) & 1) << (length - 1 - i);
		this.write(rev, length);
	}
	finish() {
		if (this.bit > 0) {
			this.#ensure(1);
			this.buf[this.pos++] = this.acc;
			this.acc = 0;
			this.bit = 0;
		}
		return this.buf.slice(0, this.pos);
	}
}

const LENGTH_BASE = [3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258];
const LENGTH_EXTRA = [0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0];
const DIST_BASE = [1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577];
const DIST_EXTRA = [0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13];

function writeLiteral(w, sym) {
	if (sym < 144) w.writeCode(0x30 + sym, 8);
	else if (sym < 256) w.writeCode(0x190 + sym - 144, 9);
	else if (sym < 280) w.writeCode(sym - 256, 7);
	else w.writeCode(0xc0 + sym - 280, 8);
}

function writeMatch(w, length, distance) {
	let li = LENGTH_BASE.length - 1;
	while (LENGTH_BASE[li] > length) li--;
	writeLiteral(w, 257 + li);
	if (LENGTH_EXTRA[li]) w.write(length - LENGTH_BASE[li], LENGTH_EXTRA[li]);
	let di = DIST_BASE.length - 1;
	while (DIST_BASE[di] > distance) di--;
	w.writeCode(di, 5);
	if (DIST_EXTRA[di]) w.write(distance - DIST_BASE[di], DIST_EXTRA[di]);
}

/** Raw deflate (RFC 1951) with one fixed-Huffman block. */
export function deflateRaw(data) {
	const w = new BitWriter(Math.max(64, data.length >> 1));
	w.write(1, 1); // BFINAL
	w.write(1, 2); // BTYPE = fixed Huffman
	const WINDOW = 32768, HASH_SIZE = 1 << 15, MAX_CHAIN = 48, MAX_LEN = 258;
	const head = new Int32Array(HASH_SIZE).fill(-1);
	const prev = new Int32Array(WINDOW).fill(-1);
	const hashAt = (i) => (((data[i] << 10) ^ (data[i + 1] << 5) ^ data[i + 2]) & (HASH_SIZE - 1));
	const insert = (i) => {
		if (i + 2 >= data.length) return;
		const h = hashAt(i);
		prev[i & (WINDOW - 1)] = head[h];
		head[h] = i;
	};
	let i = 0;
	while (i < data.length) {
		let bestLen = 0, bestDist = 0;
		if (i + 2 < data.length) {
			let candidate = head[hashAt(i)];
			let chain = 0;
			const maxLen = Math.min(MAX_LEN, data.length - i);
			while (candidate >= 0 && i - candidate <= WINDOW && chain++ < MAX_CHAIN) {
				if (data[candidate + bestLen] === data[i + bestLen]) {
					let len = 0;
					while (len < maxLen && data[candidate + len] === data[i + len]) len++;
					if (len > bestLen) {
						bestLen = len;
						bestDist = i - candidate;
						if (len === maxLen) break;
					}
				}
				const next = prev[candidate & (WINDOW - 1)];
				if (next >= candidate) break;
				candidate = next;
			}
		}
		if (bestLen >= 3) {
			writeMatch(w, bestLen, bestDist);
			for (let k = 0; k < bestLen; k++) insert(i + k);
			i += bestLen;
		} else {
			writeLiteral(w, data[i]);
			insert(i);
			i++;
		}
	}
	writeLiteral(w, 256);
	return w.finish();
}

/** zlib stream (RFC 1950) around raw deflate. */
export function zlibDeflate(data) {
	const raw = deflateRaw(data);
	const out = new Uint8Array(raw.length + 6);
	out[0] = 0x78;
	out[1] = 0x01;
	out.set(raw, 2);
	const a = adler32(data);
	out[out.length - 4] = (a >>> 24) & 0xff;
	out[out.length - 3] = (a >>> 16) & 0xff;
	out[out.length - 2] = (a >>> 8) & 0xff;
	out[out.length - 1] = a & 0xff;
	return out;
}

function paeth(a, b, c) {
	const p = a + b - c;
	const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
	if (pa <= pb && pa <= pc) return a;
	if (pb <= pc) return b;
	return c;
}

/** Filters scanlines choosing the filter with the smallest sum of absolute differences per row. */
function filterScanlines(width, height, rgba) {
	const stride = width * 4;
	const out = new Uint8Array((stride + 1) * height);
	const row = new Uint8Array(stride);
	for (let y = 0; y < height; y++) {
		const cur = y * stride;
		const up = (y - 1) * stride;
		let bestType = 0, bestSum = Infinity, best = null;
		for (let type = 0; type < 5; type++) {
			let sum = 0;
			for (let x = 0; x < stride; x++) {
				const raw = rgba[cur + x];
				const a = x >= 4 ? rgba[cur + x - 4] : 0;
				const b = y > 0 ? rgba[up + x] : 0;
				const c = x >= 4 && y > 0 ? rgba[up + x - 4] : 0;
				let v;
				switch (type) {
					case 0: v = raw; break;
					case 1: v = raw - a; break;
					case 2: v = raw - b; break;
					case 3: v = raw - ((a + b) >> 1); break;
					default: v = raw - paeth(a, b, c);
				}
				v &= 0xff;
				row[x] = v;
				sum += v < 128 ? v : 256 - v;
			}
			if (sum < bestSum) {
				bestSum = sum;
				bestType = type;
				best = row.slice();
			}
		}
		out[y * (stride + 1)] = bestType;
		out.set(best, y * (stride + 1) + 1);
	}
	return out;
}

function chunk(type, data) {
	const out = new Uint8Array(12 + data.length);
	const len = data.length;
	out[0] = (len >>> 24) & 0xff;
	out[1] = (len >>> 16) & 0xff;
	out[2] = (len >>> 8) & 0xff;
	out[3] = len & 0xff;
	for (let i = 0; i < 4; i++) out[4 + i] = type.charCodeAt(i);
	out.set(data, 8);
	const crc = crc32(out.subarray(4, 8 + len));
	out[8 + len] = (crc >>> 24) & 0xff;
	out[9 + len] = (crc >>> 16) & 0xff;
	out[10 + len] = (crc >>> 8) & 0xff;
	out[11 + len] = crc & 0xff;
	return out;
}

/** Encodes an RGBA image {width, height, data} to PNG bytes. */
export function encodePNG(image) {
	const { width, height, data } = image;
	const ihdr = new Uint8Array(13);
	ihdr[0] = (width >>> 24) & 0xff;
	ihdr[1] = (width >>> 16) & 0xff;
	ihdr[2] = (width >>> 8) & 0xff;
	ihdr[3] = width & 0xff;
	ihdr[4] = (height >>> 24) & 0xff;
	ihdr[5] = (height >>> 16) & 0xff;
	ihdr[6] = (height >>> 8) & 0xff;
	ihdr[7] = height & 0xff;
	ihdr[8] = 8; // bit depth
	ihdr[9] = 6; // RGBA
	const idat = zlibDeflate(filterScanlines(width, height, data));
	const parts = [
		new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
		chunk('IHDR', ihdr),
		chunk('IDAT', idat),
		chunk('IEND', new Uint8Array(0)),
	];
	const total = parts.reduce((a, p) => a + p.length, 0);
	const out = new Uint8Array(total);
	let o = 0;
	for (const p of parts) {
		out.set(p, o);
		o += p.length;
	}
	return out;
}

const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

export function bytesToBase64(bytes) {
	let out = '';
	let i = 0;
	for (; i + 2 < bytes.length; i += 3) {
		const n = (bytes[i] << 16) | (bytes[i + 1] << 8) | bytes[i + 2];
		out += B64[(n >> 18) & 63] + B64[(n >> 12) & 63] + B64[(n >> 6) & 63] + B64[n & 63];
	}
	if (i < bytes.length) {
		const n = (bytes[i] << 16) | ((bytes[i + 1] || 0) << 8);
		out += B64[(n >> 18) & 63] + B64[(n >> 12) & 63] + (i + 1 < bytes.length ? B64[(n >> 6) & 63] : '=') + '=';
	}
	return out;
}

export function pngDataURL(image) {
	return 'data:image/png;base64,' + bytesToBase64(encodePNG(image));
}
