// Minimal observable application state.

export class Store {
	constructor(state) {
		this.state = state;
		this.listeners = new Set();
	}
	get() {
		return this.state;
	}
	/** Mutates the state through `fn` and notifies listeners with a reason tag. */
	update(fn, reason = 'change') {
		fn(this.state);
		this.emit(reason);
	}
	on(fn) {
		this.listeners.add(fn);
		return () => this.listeners.delete(fn);
	}
	emit(reason) {
		for (const fn of this.listeners) fn(this.state, reason);
	}
}

/** Coalesces rapid calls into one per animation frame (latest wins). */
export function frameThrottle(fn) {
	let pending = false;
	return () => {
		if (pending) return;
		pending = true;
		requestAnimationFrame(() => {
			pending = false;
			fn();
		});
	};
}

export function debounce(fn, ms) {
	let t = null;
	return (...args) => {
		clearTimeout(t);
		t = setTimeout(() => fn(...args), ms);
	};
}
