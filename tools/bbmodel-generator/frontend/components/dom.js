// Tiny DOM helpers and form controls (no framework).

export function h(tag, props = {}, ...children) {
	const el = document.createElement(tag);
	for (const [k, v] of Object.entries(props || {})) {
		if (v == null || v === false) continue;
		if (k === 'class') el.className = v;
		else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
		else if (k === 'dataset') Object.assign(el.dataset, v);
		else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
		else if (k === 'value') el.value = v;
		else if (k === 'checked') el.checked = !!v;
		else if (v === true) el.setAttribute(k, '');
		else el.setAttribute(k, v);
	}
	append(el, children);
	return el;
}

function append(el, children) {
	for (const c of children.flat(Infinity)) {
		if (c == null || c === false) continue;
		el.append(c instanceof Node ? c : document.createTextNode(String(c)));
	}
}

export function clear(el, ...children) {
	el.replaceChildren();
	append(el, children);
	return el;
}

/** Labelled row. */
export function field(label, control, { hint = null, wide = false } = {}) {
	return h('label', { class: `field${wide ? ' wide' : ''}`, title: hint || undefined }, h('span', { class: 'field-label' }, label), control);
}

export function numberInput({ value, min, max, step = 1, placeholder, onChange }) {
	const el = h('input', { type: 'number', class: 'num', value: value ?? '', min, max, step, placeholder });
	el.addEventListener('input', () => {
		if (el.value === '') return onChange?.(null);
		const v = Number(el.value);
		if (Number.isFinite(v)) onChange?.(v);
	});
	return el;
}

export function rangeInput({ value, min = 0, max = 1, step = 0.01, onChange, format = (v) => v }) {
	const out = h('span', { class: 'range-value' }, format(value));
	const input = h('input', { type: 'range', min, max, step, value });
	input.addEventListener('input', () => {
		const v = Number(input.value);
		out.textContent = format(v);
		onChange?.(v);
	});
	const wrap = h('span', { class: 'range' }, input, out);
	wrap.setValue = (v) => {
		input.value = v;
		out.textContent = format(v);
	};
	return wrap;
}

export function selectInput({ value, options, onChange }) {
	const el = h(
		'select',
		{},
		options.map((o) => {
			const opt = typeof o === 'object' ? o : { value: o, label: String(o) };
			return h('option', { value: opt.value, selected: String(opt.value) === String(value) || undefined }, opt.label);
		}),
	);
	el.value = value;
	el.addEventListener('change', () => onChange?.(el.value));
	return el;
}

export function checkbox({ checked, label, hint, onChange }) {
	const input = h('input', { type: 'checkbox', checked });
	input.addEventListener('change', () => onChange?.(input.checked));
	return h('label', { class: 'check', title: hint || undefined }, input, h('span', {}, label));
}

export function colorInput({ value, onChange }) {
	const color = h('input', { type: 'color', value });
	const text = h('input', { type: 'text', class: 'hex', value, spellcheck: 'false' });
	color.addEventListener('input', () => {
		text.value = color.value;
		onChange?.(color.value);
	});
	text.addEventListener('change', () => {
		if (/^#[0-9a-fA-F]{6}$/.test(text.value)) {
			color.value = text.value;
			onChange?.(text.value.toLowerCase());
		} else text.value = color.value;
	});
	return h('span', { class: 'color' }, color, text);
}

export function button(label, onClick, { kind = '', title = null, icon = null } = {}) {
	return h('button', { class: `btn ${kind}`.trim(), title: title || undefined, onClick }, icon ? h('span', { class: 'icon' }, icon) : null, label);
}

export function section(title, body, { open = true, actions = null } = {}) {
	const details = h('details', { class: 'section', open: open || undefined }, h('summary', {}, h('span', {}, title), actions), h('div', { class: 'section-body' }, body));
	return details;
}

export function toast(message, kind = 'info', ms = 3200) {
	let host = document.getElementById('toasts');
	if (!host) {
		host = h('div', { id: 'toasts' });
		document.body.append(host);
	}
	const el = h('div', { class: `toast ${kind}` }, message);
	host.append(el);
	setTimeout(() => el.classList.add('out'), ms);
	setTimeout(() => el.remove(), ms + 400);
}

export function download(filename, data, mime = 'application/octet-stream') {
	const blob = data instanceof Blob ? data : new Blob([data], { type: mime });
	const url = URL.createObjectURL(blob);
	const a = h('a', { href: url, download: filename });
	document.body.append(a);
	a.click();
	a.remove();
	setTimeout(() => URL.revokeObjectURL(url), 2000);
}
