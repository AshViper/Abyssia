// BBModel Generator GUI: wires state, generation pipeline, preview and panels.

import {
	DEFAULT_SETTINGS,
	buildBBModel,
	bytesToBase64,
	createZip,
	exportFiles,
	generateModel,
	getTemplate,
	mergeReports,
	stringifyBBModel,
	validateBBModel,
} from '/backend/index.js';
import { clone, deepMerge } from '/backend/generator/util.js';
import { hashString } from '/backend/generator/random.js';
import { CreaturePanel } from './components/creature_panel.js';
import { download, h, toast } from './components/dom.js';
import { Drawer } from './components/drawer.js';
import { SettingsPanel } from './components/settings_panel.js';
import { Timeline, renderToolbar } from './components/toolbar.js';
import { DefinitionEditor } from './editor/definition_editor.js';
import { openImageImport } from './editor/image_import.js';
import { openJsonEditor } from './editor/json_editor.js';
import { Store, debounce, frameThrottle } from './editor/store.js';
import { Viewer } from './preview/viewer.js';

const api = {
	async get(url) {
		const r = await fetch(url);
		if (!r.ok) throw new Error(`${url}: ${r.status}`);
		return r.json();
	},
	async send(method, url, body) {
		const r = await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
		const data = await r.json().catch(() => ({}));
		if (!r.ok) throw new Error(data.error ? `${data.error}${data.problems ? ': ' + data.problems.map((p) => p.message).join('; ') : ''}` : `${r.status}`);
		return data;
	},
};

const LS_KEY = 'bbmodel-generator.session';

async function boot() {
	const [definitionList, presetList] = await Promise.all([api.get('/api/definitions'), api.get('/api/presets')]);
	const definitions = Object.fromEntries(definitionList.filter((d) => !d._error).map((d) => [d.id, d]));
	const presets = Object.fromEntries(presetList.map((p) => [p.id, p]));
	const defaultPreset = presetList.find((p) => p.default) || presetList[0];
	let session = {};
	try {
		session = JSON.parse(localStorage.getItem(LS_KEY) || '{}');
	} catch {
		session = {};
	}
	const creatureId = definitions[session.creatureId] ? session.creatureId : definitions.anglerfish ? 'anglerfish' : Object.keys(definitions)[0];
	const presetId = presets[session.presetId] ? session.presetId : defaultPreset.id;

	const store = new Store({
		definitions,
		presets,
		creatureId,
		baseDefinition: clone(definitions[creatureId]),
		definition: clone(definitions[creatureId]),
		presetId,
		settings: deepMerge(DEFAULT_SETTINGS, { ...(presets[presetId].settings || {}), ...(session.settings || {}), preset: presetId }),
		autoUpdate: true,
		dirty: false,
		model: null,
		report: null,
		animation: session.animation ?? 'idle',
		selectedBone: null,
	});

	// Layout
	const viewer = new Viewer(document.getElementById('viewport'));
	const creaturePanel = new CreaturePanel(document.getElementById('left'), store, null);
	const settingsPanel = new SettingsPanel(document.getElementById('right'), store, null);
	const drawer = new Drawer(document.getElementById('drawer'), store, null);
	const timeline = new Timeline(document.getElementById('timeline'), viewer, store, null);
	const editor = new DefinitionEditor(creaturePanel.editorHost, store, { onChange: () => markDirty() });
	const statusEl = document.getElementById('status');

	const preset = () => store.get().presets[store.get().presetId];
	const saveSession = debounce(() => {
		const s = store.get();
		localStorage.setItem(LS_KEY, JSON.stringify({ creatureId: s.creatureId, presetId: s.presetId, settings: s.settings, animation: s.animation }));
	}, 400);

	function markDirty() {
		const el = document.querySelector('.dirty');
		if (el) {
			el.textContent = '● modified';
			el.classList.add('on');
		}
	}

	const validateLater = debounce(() => {
		const s = store.get();
		if (!s.model) return;
		try {
			const json = buildBBModel(s.model);
			const text = stringifyBBModel(json);
			store.update((st) => (st.report = mergeReports(st.model.validation, validateBBModel(json, text))), 'report');
		} catch (err) {
			store.update((st) => (st.report = mergeReports(st.model.validation, { items: [{ level: 'error', check: 'bbmodel', message: err.message }] })), 'report');
		}
		settingsPanel.renderStats();
		drawer.render();
		updateStatus();
	}, 250);

	function updateStatus() {
		const { model, report } = store.get();
		if (!model) return;
		const rep = report || model.validation;
		const st = model.stats;
		statusEl.className = `status ${rep.errors ? 'bad' : rep.warnings ? 'warn' : 'ok'}`;
		statusEl.textContent = `${model.name} · ${model.template} · ${st.cubes} cubes · ${st.bones} bones · ${st.texture} · ${st.animations} anims · ${rep.errors ? rep.errors + ' errors' : rep.warnings ? rep.warnings + ' warnings' : 'valid'}`;
	}

	function runGeneration({ frame = false } = {}) {
		const s = store.get();
		let model;
		try {
			model = generateModel(s.definition, s.settings, preset());
		} catch (err) {
			console.error(err);
			statusEl.className = 'status bad';
			statusEl.textContent = `Generation failed: ${err.message}`;
			toast(`Generation failed: ${err.message}`, 'bad');
			return;
		}
		store.update((st) => {
			st.model = model;
			st.report = null;
			if (st.animation && !model.animations.some((a) => a.name === st.animation)) st.animation = null;
		}, 'model');
		const clip = model.animations.find((a) => a.name === store.get().animation) || null;
		const wasPlaying = viewer.animator.playing;
		viewer.setModel(model, { frame });
		if (clip && viewer.animator.clip?.name !== clip.name) viewer.animator.setClip(clip);
		if (clip && (wasPlaying || !viewer.animator.clip)) viewer.animator.playing = true;
		viewer.selectBone(store.get().selectedBone);
		settingsPanel.renderStats();
		drawer.render();
		timeline.render();
		updateStatus();
		validateLater();
		saveSession();
	}

	const regenerate = frameThrottle(() => {
		if (store.get().autoUpdate) runGeneration();
	});

	function loadCreature(def, { base = def, keepSettings = true } = {}) {
		store.update((s) => {
			s.creatureId = def.id;
			s.definition = clone(def);
			s.baseDefinition = clone(base);
			s.dirty = false;
			s.selectedBone = null;
			if (!keepSettings) s.settings.size = { width: 0, height: 0, length: 0 };
		}, 'creature');
		creaturePanel.render();
		editor.render();
		settingsPanel.render();
		runGeneration({ frame: true });
		playAnimation(store.get().animation || 'idle');
	}

	function playAnimation(name) {
		const model = store.get().model;
		const clip = model?.animations.find((a) => a.name === name) || null;
		store.update((s) => (s.animation = clip ? clip.name : null), 'animation');
		viewer.animator.setClip(clip);
		viewer.animator.playing = !!clip;
		timeline.render();
		if (drawer.tab === 'Animations') drawer.render();
		saveSession();
	}

	const actions = {
		regenerate,
		generateModel: () => runGeneration(),
		generateTexture() {
			store.update((s) => (s.settings.textureSeed = (s.settings.textureSeed || 0) + 1), 'settings');
			runGeneration();
			toast(`Texture repainted (texture seed ${store.get().settings.textureSeed})`);
		},
		downloadBBModel() {
			const { model } = store.get();
			if (!model) return;
			const out = exportFiles(model);
			store.update((s) => (s.report = out.report), 'report');
			drawer.render();
			updateStatus();
			if (!out.report.ok) {
				drawer.tab = 'Validation';
				drawer.render();
				toast(`Validation failed (${out.report.errors} errors) — .bbmodel not written`, 'bad');
				return;
			}
			download(`${model.id}.bbmodel`, out.text, 'application/json');
			toast(`${model.id}.bbmodel generated${out.report.warnings ? ` (${out.report.warnings} warnings)` : ''}`, 'ok');
		},
		exportZip() {
			const { model } = store.get();
			if (!model) return;
			const out = exportFiles(model);
			if (!out.report.ok) {
				store.update((s) => (s.report = out.report), 'report');
				drawer.tab = 'Validation';
				drawer.render();
				return toast('Fix validation errors before exporting', 'bad');
			}
			const zip = createZip(out.files.map((f) => ({ name: `${out.folder}/${f.name}`, data: f.data })));
			download(`${out.folder}.zip`, zip, 'application/zip');
			toast(`${out.folder}.zip (${out.files.length} files)`, 'ok');
		},
		async saveOutput() {
			const { model } = store.get();
			if (!model) return;
			const out = exportFiles(model);
			if (!out.report.ok) return toast('Fix validation errors before exporting', 'bad');
			try {
				const res = await api.send('POST', '/api/export', {
					folder: out.folder,
					files: out.files.map((f) => (typeof f.data === 'string' ? { name: f.name, text: f.data } : { name: f.name, base64: bytesToBase64(f.data) })),
				});
				toast(`Saved to ${res.dir}/ (${res.files.join(', ')})`, 'ok', 5000);
			} catch (err) {
				toast(`Save failed: ${err.message}`, 'bad');
			}
		},
		selectCreature(id) {
			const def = store.get().definitions[id];
			if (def) loadCreature(def);
		},
		setTemplate(id) {
			store.update((s) => {
				s.definition.template = id;
				s.dirty = true;
			}, 'definition');
			editor.render();
			runGeneration({ frame: true });
			markDirty();
		},
		selectPreset(id) {
			const p = store.get().presets[id];
			store.update((s) => {
				s.presetId = id;
				s.settings = deepMerge(s.settings, { ...(p.settings || {}), preset: id });
			}, 'settings');
			settingsPanel.render();
			runGeneration();
		},
		setSeed(seed) {
			store.update((s) => (s.settings.seed = seed), 'settings');
			creaturePanel.setSeed(seed);
			regenerate();
		},
		randomSeed() {
			actions.setSeed(Math.floor(Math.random() * 1e9));
			if (!store.get().settings.variation) actions.setVariation(1, true);
		},
		variant(letter) {
			const seed = hashString(`${store.get().definition.id}:${letter}`) % 1000000;
			store.update((s) => {
				s.settings.seed = seed;
				s.settings.variation = 1;
			}, 'settings');
			creaturePanel.render();
			editor.render();
			runGeneration();
			toast(`${store.get().definition.name} ${letter} (seed ${seed})`);
		},
		setVariation(v, rerender = false) {
			store.update((s) => (s.settings.variation = v), 'settings');
			if (rerender) creaturePanel.render(), editor.render();
			regenerate();
		},
		async saveDefinition() {
			const def = clone(store.get().definition);
			delete def._variation;
			try {
				await api.send('PUT', `/api/definitions/${def.id}`, def);
				store.update((s) => {
					s.definitions[def.id] = clone(def);
					s.baseDefinition = clone(def);
					s.dirty = false;
				}, 'saved');
				creaturePanel.render();
				editor.render();
				toast(`Saved definitions/${def.id}.json`, 'ok');
			} catch (err) {
				toast(`Save failed: ${err.message}`, 'bad', 6000);
			}
		},
		revertDefinition() {
			const base = store.get().definitions[store.get().creatureId] || store.get().baseDefinition;
			loadCreature(base);
		},
		editJson() {
			openJsonEditor(store.get().definition, {
				onApply(def) {
					const known = store.get().definitions[def.id];
					store.update((s) => {
						s.definition = def;
						s.creatureId = def.id;
						s.dirty = true;
						if (!known) s.baseDefinition = clone(def);
					}, 'definition');
					creaturePanel.render();
					editor.render();
					runGeneration({ frame: true });
				},
			});
		},
		newCreature() {
			const id = prompt('New creature id (lower_snake_case):', 'new_creature');
			if (!id || !/^[a-z][a-z0-9_]*$/.test(id)) return id && toast('Invalid id', 'bad');
			const template = store.get().definition.template || 'fish';
			const def = {
				...getTemplate(template).defaults(),
				id,
				name: id.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()),
				template,
			};
			loadCreature(def, { base: def, keepSettings: false });
			store.update((s) => (s.dirty = true), 'definition');
			creaturePanel.render();
			toast(`New ${template} creature "${id}" — Save Definition to keep it`);
		},
		importImage() {
			return openImageImport({
				definitions: store.get().definitions,
				onCreate(def) {
					loadCreature(def, { base: def, keepSettings: false });
					store.update((s) => (s.dirty = true), 'definition');
					creaturePanel.render();
					toast(`Photo model "${def.id}" created — Save Definition to keep it`, 'ok', 5000);
				},
			});
		},
		selectBone(name) {
			store.update((s) => (s.selectedBone = name), 'selection');
			viewer.selectBone(name);
			if (drawer.tab === 'Outliner') drawer.render();
		},
		playAnimation,
	};
	creaturePanel.actions = actions;
	settingsPanel.actions = actions;
	drawer.actions = actions;
	timeline.actions = actions;
	viewer.on('select', (bone) => actions.selectBone(bone));

	const toolbarEl = document.getElementById('toolbar');
	renderToolbar(toolbarEl, viewer);

	document.addEventListener('keydown', (e) => {
		if (e.target.closest('input, textarea, select')) return;
		if (e.key === 'F5') {
			e.preventDefault();
			runGeneration();
		} else if (e.key === ' ') {
			e.preventDefault();
			timeline.toggle();
		} else if (e.key.toLowerCase() === 'w') {
			viewer.setOption('wireframe', !viewer.options.wireframe);
			renderToolbar(toolbarEl, viewer);
		} else if (e.key.toLowerCase() === 'b') {
			viewer.setOption('bones', !viewer.options.bones);
			renderToolbar(toolbarEl, viewer);
		}
	});

	window.bbgen = { store, viewer, actions, runGeneration };
	loadCreature(store.get().definition);
}

boot().catch((err) => {
	console.error(err);
	document.getElementById('status').textContent = `Startup failed: ${err.message}`;
	document.body.append(h('pre', { class: 'fatal' }, String(err.stack || err)));
});
