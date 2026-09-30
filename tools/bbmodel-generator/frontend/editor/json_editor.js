// Raw JSON editing of the working definition (modal), with live schema checks.

import { checkDefinition } from '/backend/creatures/schema.js';
import { listTemplates } from '/backend/templates/index.js';
import { button, h } from '../components/dom.js';

export function openJsonEditor(definition, { onApply }) {
	const templates = listTemplates().map((t) => t.id);
	const clean = { ...definition };
	delete clean._variation;
	const area = h('textarea', { class: 'json-edit', spellcheck: 'false' });
	area.value = JSON.stringify(clean, null, '\t');
	const status = h('div', { class: 'json-status' });
	const check = () => {
		try {
			const parsed = JSON.parse(area.value);
			const problems = checkDefinition(parsed, templates);
			status.replaceChildren(
				problems.length
					? h('ul', {}, problems.map((p) => h('li', { class: p.level }, `${p.path ? p.path + ': ' : ''}${p.message}`)))
					: h('span', { class: 'ok' }, '✔ Valid definition'),
			);
			return problems.some((p) => p.level === 'error') ? null : parsed;
		} catch (err) {
			status.replaceChildren(h('span', { class: 'error' }, `JSON error: ${err.message}`));
			return null;
		}
	};
	area.addEventListener('input', check);
	area.addEventListener('keydown', (e) => {
		if (e.key === 'Tab') {
			e.preventDefault();
			const { selectionStart: a, selectionEnd: b } = area;
			area.setRangeText('\t', a, b, 'end');
		}
	});
	const close = () => overlay.remove();
	const overlay = h(
		'div',
		{ class: 'modal-overlay', onClick: (e) => e.target === overlay && close() },
		h(
			'div',
			{ class: 'modal' },
			h('div', { class: 'modal-title' }, `Creature Definition — ${definition.id}.json`),
			area,
			status,
			h(
				'div',
				{ class: 'row end' },
				button('Cancel', close, { kind: 'ghost' }),
				button('Apply', () => {
					const parsed = check();
					if (!parsed) return;
					onApply(parsed);
					close();
				}, { kind: 'primary' }),
			),
		),
	);
	document.body.append(overlay);
	check();
	area.focus();
}
