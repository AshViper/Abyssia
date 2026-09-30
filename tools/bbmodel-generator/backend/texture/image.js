// Plain RGBA image buffer shared by the painter, PNG encoder and the browser preview.

export class RGBAImage {
	constructor(width, height) {
		this.width = width;
		this.height = height;
		this.data = new Uint8ClampedArray(width * height * 4);
	}
	inside(x, y) {
		return x >= 0 && y >= 0 && x < this.width && y < this.height;
	}
	set(x, y, rgba) {
		if (!this.inside(x, y)) return;
		const i = (y * this.width + x) * 4;
		this.data[i] = rgba[0];
		this.data[i + 1] = rgba[1];
		this.data[i + 2] = rgba[2];
		this.data[i + 3] = rgba[3] ?? 255;
	}
	get(x, y) {
		const i = (y * this.width + x) * 4;
		return [this.data[i], this.data[i + 1], this.data[i + 2], this.data[i + 3]];
	}
	/** Number of pixels with alpha > 0. */
	countOpaque() {
		let n = 0;
		for (let i = 3; i < this.data.length; i += 4) if (this.data[i] > 0) n++;
		return n;
	}
}
