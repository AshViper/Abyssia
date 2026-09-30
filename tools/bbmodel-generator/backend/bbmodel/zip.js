// Minimal ZIP writer (deflate or store per entry, UTF-8 names). Works in the browser and Node.

import { crc32, deflateRaw } from '../texture/png.js';

const enc = new TextEncoder();

function dosDateTime(date) {
	const time = (date.getHours() << 11) | (date.getMinutes() << 5) | Math.floor(date.getSeconds() / 2);
	const day = ((Math.max(1980, date.getFullYear()) - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate();
	return { time, day };
}

/**
 * @param {{name:string, data:Uint8Array|string}[]} files
 * @returns {Uint8Array}
 */
export function createZip(files, { date = new Date() } = {}) {
	const { time, day } = dosDateTime(date);
	const locals = [];
	const centrals = [];
	let offset = 0;
	for (const file of files) {
		const name = enc.encode(file.name.replace(/\\/g, '/'));
		const data = typeof file.data === 'string' ? enc.encode(file.data) : file.data;
		const crc = crc32(data);
		const deflated = deflateRaw(data);
		const useDeflate = deflated.length < data.length;
		const body = useDeflate ? deflated : data;
		const method = useDeflate ? 8 : 0;

		const local = new DataView(new ArrayBuffer(30));
		local.setUint32(0, 0x04034b50, true);
		local.setUint16(4, 20, true);
		local.setUint16(6, 0x0800, true);
		local.setUint16(8, method, true);
		local.setUint16(10, time, true);
		local.setUint16(12, day, true);
		local.setUint32(14, crc, true);
		local.setUint32(18, body.length, true);
		local.setUint32(22, data.length, true);
		local.setUint16(26, name.length, true);
		local.setUint16(28, 0, true);
		locals.push(new Uint8Array(local.buffer), name, body);

		const central = new DataView(new ArrayBuffer(46));
		central.setUint32(0, 0x02014b50, true);
		central.setUint16(4, 20, true);
		central.setUint16(6, 20, true);
		central.setUint16(8, 0x0800, true);
		central.setUint16(10, method, true);
		central.setUint16(12, time, true);
		central.setUint16(14, day, true);
		central.setUint32(16, crc, true);
		central.setUint32(20, body.length, true);
		central.setUint32(24, data.length, true);
		central.setUint16(28, name.length, true);
		central.setUint32(42, offset, true);
		centrals.push(new Uint8Array(central.buffer), name);
		offset += 30 + name.length + body.length;
	}
	const centralSize = centrals.reduce((a, p) => a + p.length, 0);
	const end = new DataView(new ArrayBuffer(22));
	end.setUint32(0, 0x06054b50, true);
	end.setUint16(8, files.length, true);
	end.setUint16(10, files.length, true);
	end.setUint32(12, centralSize, true);
	end.setUint32(16, offset, true);
	const parts = [...locals, ...centrals, new Uint8Array(end.buffer)];
	const out = new Uint8Array(parts.reduce((a, p) => a + p.length, 0));
	let o = 0;
	for (const p of parts) {
		out.set(p, o);
		o += p.length;
	}
	return out;
}
