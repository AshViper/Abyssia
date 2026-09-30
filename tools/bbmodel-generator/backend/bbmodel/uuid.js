// Deterministic UUIDs: the same creature + settings always produce the same .bbmodel,
// which keeps diffs readable and lets Blockbench match animators to groups by UUID.

import { hashInts, hashString } from '../generator/random.js';

const hex8 = (n) => (n >>> 0).toString(16).padStart(8, '0');

/** Returns a function key -> RFC 4122 v4-formatted UUID (lowercase, Blockbench isUUID compatible). */
export function uuidFactory(namespace) {
	const ns = hashString(String(namespace));
	const used = new Map();
	return (key) => {
		const k = String(key);
		if (used.has(k)) return used.get(k);
		const h = hashString(k);
		const a = hashInts(ns, h, 1), b = hashInts(ns, h, 2), c = hashInts(ns, h, 3), d = hashInts(ns, h, 4);
		const s = hex8(a) + hex8(b) + hex8(c) + hex8(d);
		const variant = ((parseInt(s[16], 16) & 0x3) | 0x8).toString(16);
		const uuid = `${s.slice(0, 8)}-${s.slice(8, 12)}-4${s.slice(13, 16)}-${variant}${s.slice(17, 20)}-${s.slice(20, 32)}`;
		used.set(k, uuid);
		return uuid;
	};
}

export const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
