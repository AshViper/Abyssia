// MaxRects rectangle packer (Jukka Jylänki's algorithm) with alignment and minimum
// position constraints. Used to lay out Box UV strips / per-face rectangles without overlaps.

const HEURISTICS = ['bssf', 'bl', 'baf'];

export class MaxRectsPacker {
	constructor(width, height, { align = 1, heuristic = 'bssf' } = {}) {
		this.width = width;
		this.height = height;
		this.align = align;
		this.heuristic = heuristic;
		this.free = [{ x: 0, y: 0, w: width, h: height }];
		this.used = [];
	}

	#alignUp(v) {
		return Math.ceil(v / this.align - 1e-9) * this.align;
	}

	/** Finds a spot for a w×h rectangle whose position is >= (minX, minY). Returns {x, y} or null. */
	insert(w, h, { minX = 0, minY = 0 } = {}) {
		if (w <= 0 || h <= 0) return { x: this.#alignUp(minX), y: this.#alignUp(minY) };
		let best = null;
		for (const f of this.free) {
			const x = this.#alignUp(Math.max(f.x, minX));
			const y = this.#alignUp(Math.max(f.y, minY));
			if (x + w > f.x + f.w + 1e-9 || y + h > f.y + f.h + 1e-9) continue;
			const leftoverW = f.x + f.w - (x + w);
			const leftoverH = f.y + f.h - (y + h);
			let score1, score2;
			switch (this.heuristic) {
				case 'bl':
					score1 = y + h;
					score2 = x;
					break;
				case 'baf':
					score1 = f.w * f.h - w * h;
					score2 = Math.min(leftoverW, leftoverH);
					break;
				default:
					score1 = Math.min(leftoverW, leftoverH);
					score2 = Math.max(leftoverW, leftoverH);
			}
			if (!best || score1 < best.s1 || (score1 === best.s1 && (score2 < best.s2 || (score2 === best.s2 && (y < best.y || (y === best.y && x < best.x)))))) {
				best = { x, y, s1: score1, s2: score2 };
			}
		}
		if (!best) return null;
		this.#place({ x: best.x, y: best.y, w, h });
		return { x: best.x, y: best.y };
	}

	#place(rect) {
		const next = [];
		for (const f of this.free) {
			if (!intersects(f, rect)) {
				next.push(f);
				continue;
			}
			if (rect.x > f.x) next.push({ x: f.x, y: f.y, w: rect.x - f.x, h: f.h });
			if (rect.x + rect.w < f.x + f.w) next.push({ x: rect.x + rect.w, y: f.y, w: f.x + f.w - (rect.x + rect.w), h: f.h });
			if (rect.y > f.y) next.push({ x: f.x, y: f.y, w: f.w, h: rect.y - f.y });
			if (rect.y + rect.h < f.y + f.h) next.push({ x: f.x, y: rect.y + rect.h, w: f.w, h: f.y + f.h - (rect.y + rect.h) });
		}
		// Remove free rectangles contained in others
		this.free = next.filter((a, i) => !next.some((b, j) => j !== i && contains(b, a) && (!contains(a, b) || j < i)));
		this.used.push(rect);
	}
}

function intersects(a, b) {
	return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
}
function contains(outer, inner) {
	return inner.x >= outer.x && inner.y >= outer.y && inner.x + inner.w <= outer.x + outer.w && inner.y + inner.h <= outer.y + outer.h;
}

/**
 * Packs items [{key, w, h, minX, minY}] into the first atlas size that fits.
 * @returns {{width:number, height:number, positions: Map<string,{x:number,y:number}>} | null}
 */
export function packIntoAtlas(items, sizes, { align = 1 } = {}) {
	const sorted = items
		.filter((it) => it.w > 0 && it.h > 0)
		.slice()
		.sort((a, b) => Math.max(b.w, b.h) - Math.max(a.w, a.h) || b.w * b.h - a.w * a.h || (a.key < b.key ? -1 : 1));
	for (const [width, height] of sizes) {
		for (const heuristic of HEURISTICS) {
			const packer = new MaxRectsPacker(width, height, { align, heuristic });
			const positions = new Map();
			let ok = true;
			for (const it of sorted) {
				const pos = packer.insert(it.w, it.h, { minX: it.minX || 0, minY: it.minY || 0 });
				if (!pos) {
					ok = false;
					break;
				}
				positions.set(it.key, pos);
			}
			if (ok) {
				for (const it of items) if (!positions.has(it.key)) positions.set(it.key, { x: 0, y: 0 });
				return { width, height, positions };
			}
		}
	}
	return null;
}

/** Candidate atlas sizes (UV units) in increasing area; Minecraft style power-of-two, width >= height. */
export function atlasCandidates(minimum = 16, max = 1024) {
	const out = [];
	for (let s = minimum; s <= max; s *= 2) {
		out.push([s, s]);
		if (s * 2 <= max) out.push([s * 2, s]);
	}
	return out.sort((a, b) => a[0] * a[1] - b[0] * b[1] || b[0] - a[0]);
}
