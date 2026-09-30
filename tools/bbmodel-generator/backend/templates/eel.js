// EelTemplate: small head with (possibly huge) hinged jaws, a long tapering body chain that
// undulates, optional gill frills, low continuous fins and a tail fin or glowing tail tip.
// Covers the gulper eel (Eurypharynx) and the frilled shark (Chlamydoselachus).

import { ModelBuilder } from '../generator/builder.js';
import { clamp } from '../generator/util.js';
import { CreatureTemplate } from './base.js';
import { addEyes, addToothRow, taper } from './parts.js';
import { roundedBox, splitLength, spread } from './shared.js';

export class EelTemplate extends CreatureTemplate {
	static id = 'eel';
	static label = 'Eel';
	static description = 'Head with long hinged jaws (optional pouch), undulating tapered body chain, gill frills, ribbon fins, tail fin or luminous tail tip.';

	defaults() {
		return {
			template: 'eel',
			category: 'eel',
			body: { length: 36, width: 3, height: 4, segments: 7, taper: 0.35 },
			parts: [],
			params: {
				head: { length: 5, width: 4, height: 4, round: 0 },
				mouth: { jaw_height: 1, jaw_length: 5, upper_length: 5, width: 1, gape: 40, rest_gape: 0, pouch: 0 },
				teeth: { count: 0, length: 1, rows: 1 },
				eye: { size: 1, height: 0.7, forward: 0.2, protrude: 0.5 },
				gills: { frills: 0, height: 3, spacing: 1 },
				pectoral: { enabled: true, length: 2, height: 2, angle: 40 },
				dorsal: { enabled: true, start: 0.55, end: 0.95, height: 2 },
				anal: { enabled: true, start: 0.6, end: 0.95, height: 2 },
				pelvic: { enabled: false, length: 2, height: 2, position: 0.55 },
				caudal: { enabled: true, length: 4, height: 4, shape: 'round' },
				tail_tip: { enabled: false, size: 2 },
			},
			palette: {},
			texture: { pattern: 'mottled' },
			glow: [],
			animations: ['idle', 'swim', 'hurt', 'death', 'mouth_open', 'mouth_close'],
			animation: { speed: 1, amplitude: 1 },
			variation: { body: 0.08, head: 0.08, mouth: 0.1, fin: 0.12, eye: 0.1, glow: 0.2, hue: 6, lightness: 0.03 },
		};
	}

	editorSchema() {
		return [
			{
				section: 'Body Size',
				fields: [
					{ path: 'body.length', label: 'Length', type: 'int', min: 8, max: 160 },
					{ path: 'body.width', label: 'Width', type: 'int', min: 1, max: 16 },
					{ path: 'body.height', label: 'Height', type: 'int', min: 1, max: 16 },
					{ path: 'body.segments', label: 'Segments', type: 'int', min: 2, max: 16 },
					{ path: 'body.taper', label: 'Taper (tail)', type: 'range', min: 0.1, max: 1, step: 0.05 },
				],
			},
			{
				section: 'Head Size',
				fields: [
					{ path: 'params.head.length', label: 'Length', type: 'int', min: 2, max: 16 },
					{ path: 'params.head.width', label: 'Width', type: 'int', min: 1, max: 16 },
					{ path: 'params.head.height', label: 'Height', type: 'int', min: 2, max: 16 },
				],
			},
			{
				section: 'Mouth Size',
				fields: [
					{ path: 'params.mouth.jaw_length', label: 'Lower jaw length', type: 'int', min: 2, max: 32 },
					{ path: 'params.mouth.upper_length', label: 'Upper jaw length', type: 'int', min: 2, max: 32 },
					{ path: 'params.mouth.width', label: 'Jaw width ×', type: 'range', min: 0.5, max: 3, step: 0.05 },
					{ path: 'params.mouth.jaw_height', label: 'Jaw height', type: 'int', min: 1, max: 8 },
					{ path: 'params.mouth.gape', label: 'Gape (°)', type: 'range', min: 5, max: 110, step: 1 },
					{ path: 'params.mouth.pouch', label: 'Pouch depth', type: 'int', min: 0, max: 8 },
					{ path: 'params.teeth.count', label: 'Teeth', type: 'int', min: 0, max: 12 },
				],
			},
			{
				section: 'Eye Size',
				fields: [
					{ path: 'params.eye.size', label: 'Size', type: 'int', min: 1, max: 4 },
					{ path: 'params.eye.forward', label: 'Forward', type: 'range', min: 0, max: 1, step: 0.05 },
				],
			},
			{
				section: 'Tail Length / Fin Size',
				fields: [
					{ path: 'params.caudal.enabled', label: 'Tail fin', type: 'bool' },
					{ path: 'params.caudal.length', label: 'Tail fin length', type: 'int', min: 1, max: 16 },
					{ path: 'params.caudal.height', label: 'Tail fin height', type: 'int', min: 1, max: 16 },
					{ path: 'params.dorsal.height', label: 'Dorsal height', type: 'int', min: 1, max: 8 },
					{ path: 'params.anal.height', label: 'Anal height', type: 'int', min: 1, max: 8 },
					{ path: 'params.gills.frills', label: 'Gill frills', type: 'int', min: 0, max: 8 },
				],
			},
			{
				section: 'Glow Position / Size',
				fields: [
					{ path: 'params.tail_tip.enabled', label: 'Luminous tail tip', type: 'bool' },
					{ path: 'params.tail_tip.size', label: 'Tip size', type: 'int', min: 1, max: 4 },
				],
			},
		];
	}

	variationTargets() {
		return {
			body: ['body.length', 'body.width', 'body.height'],
			head: ['params.head.length', 'params.head.width', 'params.head.height'],
			mouth: ['params.mouth.jaw_length', 'params.mouth.upper_length'],
			fin: ['params.dorsal.height', 'params.anal.height', 'params.caudal.length', 'params.caudal.height', 'params.pectoral.length'],
			eye: ['params.eye.size'],
			glow: ['params.tail_tip.size'],
		};
	}

	exaggerationTargets() {
		return {
			head: ['params.head.length', 'params.head.width', 'params.head.height', 'params.mouth.jaw_length', 'params.mouth.upper_length'],
			eye: ['params.eye.size'],
			teeth: ['params.teeth.length'],
			fin: ['params.dorsal.height', 'params.anal.height', 'params.caudal.height'],
			glow: ['params.tail_tip.size'],
		};
	}

	semanticParts(def, ctx) {
		const P = def.params;
		const has = (...n) => this.partEnabled(def, ...n);
		const list = [];
		const add = (p) => (list.push(p), p);
		const segCount = Math.max(2, Math.round(ctx.lod([Math.min(3, def.body.segments), Math.ceil(def.body.segments * 0.7), def.body.segments])));
		const total = ctx.sz(def.body.length);
		const lengths = splitLength(total, segCount, ctx.grid);
		const w0 = def.body.width * ctx.scale[0], h0 = def.body.height * ctx.scale[1];
		const tp = clamp(def.body.taper ?? 0.35, 0.05, 1);
		const segments = lengths.map((length, i) => ({
			length,
			width: taper(w0, w0 * tp, i, segCount, ctx.grid),
			height: taper(h0, h0 * tp, i, segCount, ctx.grid),
		}));
		const S = { id: def.id, list };
		S.body = add({ id: 'body', kind: 'body_chain', segments, total });
		S.head = add({ id: 'head', kind: 'head', size: ctx.size([P.head.width, P.head.height, P.head.length]), round: ctx.atLeast('medium') ? Math.round(P.head.round || 0) * ctx.grid : 0 });
		if (has('mouth') && P.mouth) {
			S.jaw = add({
				id: 'mouth',
				kind: 'jaw',
				height: clamp(ctx.sy(P.mouth.jaw_height), ctx.grid, S.head.size[1] - ctx.grid),
				length: ctx.sz(P.mouth.jaw_length),
				upper: ctx.sz(P.mouth.upper_length),
				width: ctx.sx(P.head.width * (P.mouth.width ?? 1)),
				gape: P.mouth.gape ?? 40,
				restGape: P.mouth.rest_gape ?? 0,
				pouch: ctx.sy(P.mouth.pouch || 0, 0),
			});
		}
		if (S.jaw && has('teeth') && P.teeth?.count > 0) {
			S.teeth = add({ id: 'teeth', kind: 'teeth', count: Math.round(P.teeth.count * ctx.lod([0.4, 0.7, 1])), length: ctx.s(P.teeth.length), rows: ctx.atLeast('high') ? P.teeth.rows || 1 : 1 });
		}
		if (has('eye', 'eyes')) S.eye = add({ id: 'eye', kind: 'eye', size: ctx.s(P.eye.size), height: P.eye.height ?? 0.7, forward: P.eye.forward ?? 0.2, protrude: P.eye.protrude ?? 0.5 });
		if (has('gills') && P.gills?.frills > 0) S.gills = add({ id: 'gills', kind: 'gill_frills', frills: P.gills.frills, height: ctx.sy(P.gills.height), spacing: ctx.sz(P.gills.spacing) });
		if (has('fin', 'fins') && P.pectoral?.enabled) S.pectoral = add({ id: 'fin', kind: 'pectoral_fins', length: ctx.sz(P.pectoral.length), height: ctx.sy(P.pectoral.height), angle: P.pectoral.angle ?? 40 });
		if (has('fin', 'fins') && P.pelvic?.enabled && ctx.atLeast('medium')) S.pelvic = add({ id: 'pelvic_fin', kind: 'pelvic_fins', length: ctx.sz(P.pelvic.length), height: ctx.sy(P.pelvic.height), position: P.pelvic.position ?? 0.55 });
		if (has('fin', 'fins') && P.dorsal?.enabled) S.dorsal = add({ id: 'dorsal_fin', kind: 'ribbon_fin', start: P.dorsal.start, end: P.dorsal.end, height: ctx.sy(P.dorsal.height) });
		if (has('fin', 'fins') && P.anal?.enabled && ctx.atLeast('medium')) S.anal = add({ id: 'anal_fin', kind: 'ribbon_fin', start: P.anal.start, end: P.anal.end, height: ctx.sy(P.anal.height) });
		if (has('tail') && P.caudal?.enabled) S.caudal = add({ id: 'tail_fin', kind: 'caudal_fin', length: ctx.sz(P.caudal.length), height: ctx.sy(P.caudal.height), shape: P.caudal.shape || 'round' });
		if (has('tail', 'tail_tip') && P.tail_tip?.enabled) S.tip = add({ id: 'tail_tip', kind: 'light_organ', size: ctx.s(P.tail_tip.size) });
		return S;
	}

	build(S, ctx) {
		const b = new ModelBuilder();
		const g = ctx.grid;
		const root = S.id;
		const [hw, hh, hl] = S.head.size;
		const y0 = 32; // body centre line, grounded later
		const seg0 = S.body.segments[0];
		const zNeck = 0;
		b.bone(root, { pivot: [0, 0, 0], role: 'root' });

		// Body chain: body -> body_2 -> ... (each segment rotates relative to the previous one)
		let parent = root;
		let z = zNeck;
		const segBones = [];
		S.body.segments.forEach((seg, i) => {
			const name = i === 0 ? 'body' : `body_${i + 1}`;
			b.bone(name, { parent, pivot: [0, y0, z], role: i === 0 ? 'body' : 'segment', meta: { index: i, count: S.body.segments.length } });
			const bottom = y0 - Math.floor(seg.height / 2 / g) * g;
			b.box(name, name, {
				from: [-seg.width / 2, bottom, z - (i === 0 ? 0 : g)],
				to: [seg.width / 2, bottom + seg.height, z + seg.length],
				material: 'skin',
				faces: { down: 'belly' },
			});
			segBones.push({ name, z0: z, z1: z + seg.length, seg, bottom });
			parent = name;
			z += seg.length;
		});

		// Head in front of the first segment
		const headBottom = y0 - Math.floor(hh / 2 / g) * g;
		const headTop = headBottom + hh;
		const jawH = S.jaw ? S.jaw.height : 0;
		const yMouth = headBottom + jawH;
		const zHF = zNeck - hl;
		b.bone('head', { parent: 'body', pivot: [0, y0, zNeck], role: 'head' });
		roundedBox(b, 'head', 'head', {
			from: [-hw / 2, yMouth, zHF],
			to: [hw / 2, headTop, zNeck + g],
			round: { top: S.head.round, x: S.head.round, front: S.head.round, back: 0 },
			material: 'skin',
			faces: { down: S.jaw ? 'mouth' : 'belly' },
			meta: { depthAxis: 'z' },
		});

		if (S.jaw) {
			const J = S.jaw;
			const jw = J.width;
			// Upper jaw plate reaching past the cranium (gulper eel)
			if (J.upper > hl) {
				const uw = Math.max(g, Math.min(jw, hw + 2 * g));
				b.box('head', 'upper_jaw', { from: [-uw / 2, yMouth, zNeck - J.upper], to: [uw / 2, yMouth + g, zHF + g], material: 'skin', faces: { down: 'mouth' }, meta: { depthAxis: 'z' } });
			}
			const hingeZ = zNeck - g;
			b.bone('mouth', { parent: 'head', pivot: [0, yMouth, hingeZ], rotation: [-J.restGape, 0, 0], role: 'jaw', meta: { gape: J.gape, pouch: J.pouch } });
			b.box('mouth', 'jaw', { from: [-jw / 2, yMouth - jawH, zNeck - J.length], to: [jw / 2, yMouth, zNeck], material: 'skin', faces: { up: 'mouth', down: 'belly' }, meta: { lip: g, depthAxis: 'z' } });
			if (J.pouch > 0) {
				b.bone('pouch', { parent: 'mouth', pivot: [0, yMouth - jawH, hingeZ], role: 'pouch' });
				b.box('pouch', 'pouch', {
					from: [-(jw / 2 - g), yMouth - jawH - J.pouch, zNeck - J.length + 2 * g],
					to: [jw / 2 - g, yMouth - jawH + g, zNeck - g],
					material: 'membrane',
					meta: { alpha: 255 },
				});
			}
			if (S.teeth) {
				const front = zNeck - J.length;
				const xs = spread(S.teeth.count, jw - 2 * g, g);
				b.bone('lower_teeth', { parent: 'mouth', pivot: [0, yMouth, front], role: 'teeth' });
				addToothRow(b, { bone: 'lower_teeth', name: 'tooth_lower', xs, y: yMouth, z: front, length: S.teeth.length, dir: 1, g });
				const upperFront = zNeck - Math.max(hl, J.upper);
				b.bone('upper_teeth', { parent: 'head', pivot: [0, yMouth, upperFront], role: 'teeth' });
				addToothRow(b, { bone: 'upper_teeth', name: 'tooth_upper', xs: xs.length > 1 ? xs.slice(1).map((x, i) => (x + xs[i]) / 2) : xs, y: yMouth, z: upperFront, length: S.teeth.length, dir: -1, g });
				if (S.teeth.rows > 1) addToothRow(b, { bone: 'lower_teeth', name: 'tooth_lower_back', xs, y: yMouth, z: front + 2 * g, length: S.teeth.length, dir: 1, g, priority: 3 });
			}
		}

		if (S.eye) {
			addEyes(b, { parent: 'head', halfWidth: hw / 2, y: yMouth + (headTop - yMouth) * S.eye.height, z: zHF + hl * S.eye.forward, size: S.eye.size, protrude: S.eye.protrude, g });
		}

		// Frilled gill slits: ruffled plates on both sides of the neck plus the throat band
		if (S.gills) {
			// Each gill slit is a ruffled plate standing out from the neck (XY plane, wavy outer edge)
			const count = ctx.lod([0, Math.ceil(S.gills.frills / 2), S.gills.frills]);
			const depth = ctx.atLeast('high') ? 2 * g : g;
			const gh = Math.min(S.gills.height, seg0.height + 2 * g);
			const gy = y0 - Math.floor(gh / 2 / g) * g;
			for (const [side, sign] of [['right', 1], ['left', -1]]) {
				if (!count) break;
				const x0 = (sign * seg0.width) / 2;
				b.bone(`${side}_gills`, { parent: 'body', pivot: [x0, y0, zNeck], role: 'gills', side });
				for (let i = 0; i < count; i++) {
					const gz = zNeck + g / 2 + i * S.gills.spacing * (S.gills.frills / count);
					b.box(`${side}_gills`, `${side}_gill`, {
						from: [Math.min(x0, x0 + sign * depth), gy, gz],
						to: [Math.max(x0, x0 + sign * depth), gy + gh, gz],
						material: 'gill',
						shape: { type: 'frill', axis: 'x', dir: sign, across: 'y', waves: 2 },
						priority: i === 0 ? 2 : 3,
					});
				}
			}
			b.box('body', 'throat_frill', { from: [-seg0.width / 2, segBones[0].bottom - g, zNeck], to: [seg0.width / 2, segBones[0].bottom, zNeck + 2 * g], material: 'gill', priority: 3 });
		}

		if (S.pectoral) {
			const { length: fl, height: fh, angle } = S.pectoral;
			const py = y0 - Math.floor(fh / 2 / g) * g;
			const pz = zNeck + (S.gills ? S.gills.frills * S.gills.spacing + g : g);
			for (const [side, sign] of [['right', 1], ['left', -1]]) {
				const x = (sign * seg0.width) / 2;
				const name = `${side}_fin`;
				b.bone(name, { parent: 'body', pivot: [x, py + fh / 2, pz], rotation: [0, sign * angle, -sign * 15], role: 'fin', side });
				b.box(name, name, { from: [x, py, pz - g], to: [x, py + fh, pz + fl], material: 'fin', shape: { type: 'fan', axis: 'z', dir: 1, across: 'y' } });
			}
		}

		const segmentAt = (t) => {
			const zt = zNeck + S.body.total * clamp(t, 0, 1);
			return segBones.find((s) => zt >= s.z0 && zt < s.z1) || segBones[segBones.length - 1];
		};
		if (S.pelvic) {
			const s = segmentAt(S.pelvic.position);
			for (const [side, sign] of [['right', 1], ['left', -1]]) {
				const x = sign * Math.max(g / 2, s.seg.width / 2 - g / 2);
				const name = `${side}_pelvic_fin`;
				b.bone(name, { parent: s.name, pivot: [x, s.bottom, s.z0 + g], rotation: [-20, sign * 20, 0], role: 'pelvic', side });
				b.box(name, name, { from: [x, s.bottom - S.pelvic.height, s.z0], to: [x, s.bottom + g, s.z0 + S.pelvic.length], material: 'fin', shape: { type: 'triangle', axis: 'y', dir: -1, across: 'z', apex: 0.85 }, priority: 2 });
			}
		}
		// Ribbon fins follow the body chain: one flat piece per covered segment
		const ribbon = (F, top, role, base) => {
			const pieces = segBones.filter((s) => s.z1 > zNeck + S.body.total * F.start && s.z0 < zNeck + S.body.total * F.end);
			pieces.forEach((s, i) => {
				const name = i === 0 ? base : `${base}_${i + 1}`;
				const h = Math.max(g, Math.round((F.height * (0.5 + 0.5 * Math.sin(Math.PI * ((i + 0.5) / pieces.length)))) / g) * g);
				const yEdge = top ? s.bottom + s.seg.height : s.bottom;
				b.bone(name, { parent: s.name, pivot: [0, yEdge, s.z0], role, meta: { index: i } });
				const from = top ? [0, yEdge - g, s.z0] : [0, yEdge - h, s.z0];
				const to = top ? [0, yEdge + h, s.z1] : [0, yEdge + g, s.z1];
				const shape = i === 0 ? { type: 'triangle', axis: 'y', dir: top ? 1 : -1, across: 'z', apex: 0.95 } : null;
				b.box(name, name, { from, to, material: 'fin', shape, priority: i === 0 ? 1 : 2 });
			});
		};
		if (S.dorsal) ribbon(S.dorsal, true, 'dorsal', 'dorsal_fin');
		if (S.anal) ribbon(S.anal, false, 'anal', 'anal_fin');

		const last = segBones[segBones.length - 1];
		if (S.caudal) {
			const { length: cl, height: ch, shape } = S.caudal;
			b.bone('tail_fin', { parent: last.name, pivot: [0, y0, last.z1], role: 'tail_fin' });
			const bottom = y0 - Math.floor(ch / 2 / g) * g;
			b.box('tail_fin', 'tail_fin', { from: [0, bottom, last.z1 - g], to: [0, bottom + ch, last.z1 + cl], material: 'fin', shape: { type: shape, axis: 'z', dir: 1, across: 'y' } });
		}
		if (S.tip) {
			const e = S.tip.size;
			b.bone('tail_tip', { parent: last.name, pivot: [0, y0, last.z1], role: 'esca' });
			b.box('tail_tip', 'tail_tip', { center: [0, y0, last.z1 + e / 2 - g / 2], size: [e, e, e], material: 'glow', glow: { intensity: 1 } });
		}

		b.ground({ centerFilter: (c) => c.bone === 'head' || c.bone.startsWith('body'), snap: g });
		const geometry = b.build();
		geometry.root = root;
		return geometry;
	}

	animationCatalog() {
		return EEL_ANIMATIONS;
	}
}

const chain = (roles) => [roles.one('body'), ...roles.all('segment')].filter(Boolean);
const side = (bone) => (bone.side === 'left' ? -1 : 1);

function wave(c, bones, { amp, cycles = 1, wavelength = 0.9, grow = 0.7, axis = 1, phase = 0 }) {
	const n = bones.length;
	bones.forEach((bone, i) => {
		const a = amp * (1 - grow + grow * (n > 1 ? i / (n - 1) : 1));
		const v = [0, 0, 0];
		v[axis] = a;
		c.wave(bone.name, 'rotation', { amplitude: v, cycles, phase: phase - (i * wavelength) / n });
	});
}

export const EEL_ANIMATIONS = {
	idle({ amp, make, roles }) {
		const c = make(4, 'loop', 'Hovering: slow shallow undulation, breathing jaw, drifting fins');
		const bones = chain(roles);
		wave(c, bones, { amp: 5 * amp, wavelength: 0.8 });
		c.wave('head', 'rotation', { amplitude: [0, -2 * amp, 0], cycles: 1, phase: 0.1 });
		c.wave('body', 'position', { amplitude: [0, 0.3 * amp, 0], cycles: 1, phase: 0.25 });
		if (roles.one('jaw')) c.wave('mouth', 'rotation', { amplitude: [-5 * amp, 0, 0], cycles: 1, shape: 'cos1' });
		if (roles.one('pouch')) c.wave('pouch', 'scale', { amplitude: [0, 0.12, 0], cycles: 1, shape: 'cos1' });
		for (const fin of roles.all('fin')) c.wave(fin.name, 'rotation', { amplitude: [0, side(fin) * 10 * amp, 0], cycles: 2 });
		for (const g of roles.all('gills')) c.wave(g.name, 'rotation', { amplitude: [0, side(g) * 4 * amp, 0], cycles: 2, phase: 0.2 });
		if (roles.one('esca')) c.wave('tail_tip', 'scale', { amplitude: [0.15, 0.15, 0.15], cycles: 2 });
		return c.build();
	},
	swim({ amp, make, roles }) {
		const c = make(1.4, 'loop', 'Swimming: travelling wave from head to tail');
		const bones = chain(roles);
		wave(c, bones, { amp: 14 * amp, wavelength: 1.0, grow: 0.6 });
		c.wave('head', 'rotation', { amplitude: [0, -5 * amp, 0], cycles: 1, phase: 0.15 });
		if (roles.one('tail_fin')) c.wave('tail_fin', 'rotation', { amplitude: [0, 18 * amp, 0], cycles: 1, phase: -1.05 });
		for (const fin of roles.all('fin')) c.wave(fin.name, 'rotation', { amplitude: [0, side(fin) * 18 * amp, 0], cycles: 2 });
		for (const g of roles.all('gills')) c.wave(g.name, 'rotation', { amplitude: [0, side(g) * 8 * amp, 0], cycles: 2 });
		if (roles.one('jaw')) c.wave('mouth', 'rotation', { amplitude: [-3 * amp, 0, 0], cycles: 1, shape: 'cos1' });
		return c.build();
	},
	hurt({ amp, make, roles }) {
		const c = make(0.55, 'once', 'Recoil: the body kinks and snaps back');
		const bones = chain(roles);
		bones.forEach((bone, i) => {
			const a = (i % 2 ? -1 : 1) * 14 * amp;
			c.keys(bone.name, 'rotation', [[0, [0, 0, 0]], [0.12, [0, a, 0]], [0.3, [0, -a * 0.5, 0]], [0.55, [0, 0, 0]]]);
		});
		if (roles.one('jaw')) c.keys('mouth', 'rotation', [[0, [0, 0, 0]], [0.12, [-30 * amp, 0, 0]], [0.55, [0, 0, 0]]]);
		return c.build();
	},
	death({ amp, make, roles }) {
		const c = make(1.8, 'hold', 'Coils up, rolls onto its side and sinks');
		chain(roles).forEach((bone, i) => {
			if (i === 0) return;
			c.keys(bone.name, 'rotation', [[0, [0, 0, 0]], [1.2, [0, 18 * amp, 0]], [1.8, [0, 20 * amp, 0]]]);
		});
		c.keys('body', 'rotation', [[0, [0, 0, 0]], [1.2, [0, 0, 80]], [1.8, [0, 0, 90]]]);
		c.keys('body', 'position', [[0, [0, 0, 0]], [1.8, [0, -1.5, 0]]], 'linear');
		const gape = roles.one('jaw')?.meta.gape ?? 40;
		if (roles.one('jaw')) c.keys('mouth', 'rotation', [[0, [0, 0, 0]], [1, [-gape * 0.6, 0, 0]], [1.8, [-gape * 0.55, 0, 0]]]);
		if (roles.one('esca')) c.keys('tail_tip', 'scale', [[0, [1, 1, 1]], [1.8, [0.5, 0.5, 0.5]]], 'linear');
		return c.build();
	},
	mouth_open({ make, roles }) {
		const jaw = roles.one('jaw');
		if (!jaw) return null;
		const gape = jaw.meta.gape ?? 40;
		const c = make(0.6, 'hold', jaw.meta.pouch ? 'Unhinges the huge jaw and balloons the pouch' : 'Opens the jaw wide');
		c.keys('mouth', 'rotation', [[0, [0, 0, 0]], [0.4, [-gape * 1.04, 0, 0]], [0.6, [-gape, 0, 0]]]);
		c.keys('head', 'rotation', [[0, [0, 0, 0]], [0.6, [Math.min(18, gape * 0.2), 0, 0]]]);
		if (roles.one('pouch')) c.keys('pouch', 'scale', [[0, [1, 1, 1]], [0.4, [1.1, 2.2, 1.05]], [0.6, [1.1, 2, 1.05]]]);
		return c.build();
	},
	mouth_close({ make, roles }) {
		const jaw = roles.one('jaw');
		if (!jaw) return null;
		const gape = jaw.meta.gape ?? 40;
		const c = make(0.4, 'once', 'Closes the jaw');
		c.keys('mouth', 'rotation', [[0, [-gape, 0, 0]], [0.4, [0, 0, 0]]], 'linear');
		c.keys('head', 'rotation', [[0, [Math.min(18, gape * 0.2), 0, 0]], [0.4, [0, 0, 0]]], 'linear');
		if (roles.one('pouch')) c.keys('pouch', 'scale', [[0, [1.1, 2, 1.05]], [0.4, [1, 1, 1]]], 'linear');
		return c.build();
	},
	glow({ amp, make, roles }) {
		if (!roles.one('esca')) return null;
		const c = make(2, 'loop', 'Luminous tail tip flashes and flicks');
		c.wave('tail_tip', 'scale', { amplitude: [0.35, 0.35, 0.35], cycles: 2, shape: 'pulse' });
		const bones = chain(roles);
		bones.slice(-3).forEach((bone, i) => c.wave(bone.name, 'rotation', { amplitude: [0, 10 * amp, 6 * amp], cycles: 2, phase: -0.15 * i }));
		return c.build();
	},
};
