// FishTemplate: head + hinged jaw + teeth + eyes (+ lure) + trunk + tail chain + fins.
// Covers anglerfish, viperfish, barreleye and goblin shark through parameters only.

import { ModelBuilder } from '../generator/builder.js';
import { clamp } from '../generator/util.js';
import { CreatureTemplate } from './base.js';
import { alignCenter, roundedBox, spread, splitLength } from './shared.js';

export class FishTemplate extends CreatureTemplate {
	static id = 'fish';
	static label = 'Fish';
	static description = 'Head, hinged lower jaw, teeth, eyes, optional lure (illicium + esca), trunk, tail chain and membrane fins.';

	defaults() {
		return {
			template: 'fish',
			category: 'deep_sea_fish',
			body: { length: 8, width: 5, height: 6, round: 1 },
			parts: [],
			params: {
				head: { length: 5, width: 5, height: 6, drop: 0, round: 1 },
				snout: { length: 0, width: 3, height: 1 },
				mouth: { enabled: true, jaw_height: 2, underbite: 0, gape: 35, rest_gape: 0, width: 1, protrude: 0, round: 1 },
				teeth: { count: 4, length: 1, vary: 1, side: 1, upper: true, lower: true, fangs: 0, fang_length: 3 },
				eye: { size: 1, height: 0.65, forward: 0.3, protrude: 0.5, tubular: 0 },
				lure: { enabled: false, base: 'head', position: 0.2, length: 6, segments: 3, lean: 15, arc: 120, esca: 2 },
				dome: { enabled: false, height: 3 },
				tail: { segments: 1, length: 3, width: 3, height: 3, taper: 0.7 },
				caudal: { length: 4, height: 6, shape: 'round' },
				pectoral: { enabled: true, length: 3, height: 3, angle: 30, droop: 10, position: 0.25 },
				pelvic: { enabled: false, length: 2, height: 2, position: 0.6 },
				dorsal: { count: 1, length: 3, height: 2, position: 0.55, apex: 0.8, second_position: 0.9 },
				anal: { enabled: true, length: 2, height: 2, position: 0.7 },
				photophores: { enabled: false, spacing: 2, radius: 0.6, rows: 1, head: true },
				gills: { slits: 0 },
			},
			palette: {},
			texture: { pattern: 'speckle' },
			glow: [],
			animations: ['idle', 'swim', 'hurt', 'death'],
			animation: { speed: 1, amplitude: 1 },
			variation: { body: 0.08, head: 0.08, fin: 0.12, tail: 0.1, eye: 0.1, glow: 0.2, lure: 0.1, hue: 6, lightness: 0.03 },
		};
	}

	editorSchema() {
		return [
			{
				section: 'Body Size',
				fields: [
					{ path: 'body.length', label: 'Length', type: 'int', min: 2, max: 48 },
					{ path: 'body.width', label: 'Width', type: 'int', min: 1, max: 32 },
					{ path: 'body.height', label: 'Height', type: 'int', min: 1, max: 32 },
				],
			},
			{
				section: 'Head Size',
				fields: [
					{ path: 'params.head.length', label: 'Length', type: 'int', min: 1, max: 32 },
					{ path: 'params.head.width', label: 'Width', type: 'int', min: 1, max: 32 },
					{ path: 'params.head.height', label: 'Height', type: 'int', min: 2, max: 32 },
					{ path: 'params.snout.length', label: 'Snout length', type: 'int', min: 0, max: 24 },
				],
			},
			{
				section: 'Mouth Size',
				fields: [
					{ path: 'params.mouth.jaw_height', label: 'Jaw height', type: 'int', min: 1, max: 16 },
					{ path: 'params.mouth.underbite', label: 'Underbite', type: 'int', min: -2, max: 4 },
					{ path: 'params.mouth.gape', label: 'Gape (°)', type: 'range', min: 5, max: 90, step: 1 },
					{ path: 'params.teeth.count', label: 'Teeth', type: 'int', min: 0, max: 12 },
					{ path: 'params.teeth.length', label: 'Tooth length', type: 'int', min: 1, max: 6 },
					{ path: 'params.teeth.fangs', label: 'Fangs / side', type: 'int', min: 0, max: 2 },
				],
			},
			{
				section: 'Eye Size',
				fields: [
					{ path: 'params.eye.size', label: 'Size', type: 'int', min: 1, max: 6 },
					{ path: 'params.eye.height', label: 'Height', type: 'range', min: 0, max: 1, step: 0.05 },
					{ path: 'params.eye.forward', label: 'Forward', type: 'range', min: 0, max: 1, step: 0.05 },
				],
			},
			{
				section: 'Tail Length / Fin Size',
				fields: [
					{ path: 'params.tail.length', label: 'Tail length', type: 'int', min: 1, max: 32 },
					{ path: 'params.tail.segments', label: 'Tail segments', type: 'int', min: 1, max: 6 },
					{ path: 'params.caudal.length', label: 'Tail fin length', type: 'int', min: 1, max: 24 },
					{ path: 'params.caudal.height', label: 'Tail fin height', type: 'int', min: 1, max: 32 },
					{ path: 'params.caudal.shape', label: 'Tail fin shape', type: 'select', options: ['round', 'fan', 'fork', 'lunate', 'heterocercal', 'rect'] },
					{ path: 'params.pectoral.length', label: 'Pectoral length', type: 'int', min: 1, max: 16 },
					{ path: 'params.pectoral.height', label: 'Pectoral height', type: 'int', min: 1, max: 16 },
					{ path: 'params.dorsal.height', label: 'Dorsal height', type: 'int', min: 1, max: 16 },
				],
			},
			{
				section: 'Glow Position / Size',
				fields: [
					{ path: 'params.lure.enabled', label: 'Lure (illicium)', type: 'bool' },
					{ path: 'params.lure.length', label: 'Lure length', type: 'int', min: 2, max: 24 },
					{ path: 'params.lure.arc', label: 'Lure arc (°)', type: 'range', min: 0, max: 200, step: 5 },
					{ path: 'params.lure.position', label: 'Lure position', type: 'range', min: 0, max: 1, step: 0.05 },
					{ path: 'params.lure.esca', label: 'Esca size', type: 'int', min: 1, max: 6 },
					{ path: 'params.photophores.enabled', label: 'Photophores', type: 'bool' },
					{ path: 'params.photophores.radius', label: 'Photophore size', type: 'range', min: 0.3, max: 2, step: 0.1 },
				],
			},
		];
	}

	variationTargets() {
		return {
			body: ['body.length', 'body.width', 'body.height'],
			head: ['params.head.length', 'params.head.width', 'params.head.height'],
			fin: ['params.pectoral.length', 'params.pectoral.height', 'params.caudal.height', 'params.dorsal.height', 'params.anal.height'],
			tail: ['params.tail.length', 'params.caudal.length'],
			eye: ['params.eye.size'],
			glow: ['params.lure.esca', 'params.photophores.radius'],
			lure: ['params.lure.length'],
		};
	}

	exaggerationTargets() {
		return {
			head: ['params.head.length', 'params.head.width', 'params.head.height'],
			eye: ['params.eye.size'],
			teeth: ['params.teeth.length', 'params.teeth.fang_length'],
			fin: ['params.pectoral.length', 'params.pectoral.height', 'params.caudal.length', 'params.caudal.height', 'params.dorsal.height'],
			glow: ['params.lure.esca'],
		};
	}

	semanticParts(def, ctx) {
		const P = def.params;
		const has = (...n) => this.partEnabled(def, ...n);
		const list = [];
		const add = (part) => {
			list.push(part);
			return part;
		};
		const round = (v) => (ctx.atLeast('medium') ? Math.max(0, Math.round(v || 0)) * ctx.grid : 0);
		const body = add({ id: 'body', kind: 'trunk', size: ctx.size([def.body.width, def.body.height, def.body.length]), round: round(def.body.round) });
		const head = add({ id: 'head', kind: 'head', size: ctx.size([P.head.width, P.head.height, P.head.length]), drop: ctx.qp(P.head.drop || 0), round: round(P.head.round) });
		const S = { id: def.id, list, body, head };

		if (P.snout?.length > 0 && has('snout', 'head')) {
			S.snout = add({ id: 'snout', kind: 'rostrum', size: ctx.size([P.snout.width, P.snout.height, P.snout.length]) });
		}
		if (P.mouth?.enabled !== false && has('mouth')) {
			const jawH = clamp(ctx.sy(P.mouth.jaw_height), ctx.grid, head.size[1] - ctx.grid);
			const width = Math.min(head.size[0], ctx.sx(head.size[0] * (P.mouth.width ?? 1) / ctx.scale[0]));
			S.jaw = add({ id: 'mouth', kind: 'jaw', height: jawH, width, underbite: ctx.qp(P.mouth.underbite || 0), gape: P.mouth.gape ?? 35, restGape: P.mouth.rest_gape ?? 0, protrude: P.mouth.protrude || 0, round: jawH > ctx.grid ? round(P.mouth.round) : 0 });
		}
		if (S.jaw && has('teeth') && (P.teeth?.count > 0 || P.teeth?.fangs > 0)) {
			const avail = Math.max(1, S.jaw.width - 2 * ctx.grid);
			const maxTeeth = Math.max(1, Math.floor((avail + ctx.grid) / (2 * ctx.grid)));
			const count = Math.min(Math.round(ctx.lod([0.4, 0.7, 1]) * P.teeth.count), maxTeeth);
			S.teeth = add({
				id: 'teeth',
				kind: 'teeth',
				count,
				side: ctx.atLeast('high') ? P.teeth.side ?? 1 : ctx.atLeast('medium') ? Math.min(1, P.teeth.side ?? 1) : 0,
				length: Math.max(ctx.grid, ctx.s(P.teeth.length)),
				vary: ctx.atLeast('medium') ? P.teeth.vary ?? 0 : 0,
				upper: P.teeth.upper !== false,
				lower: P.teeth.lower !== false,
				fangs: P.teeth.fangs || 0,
				fangLength: ctx.s(P.teeth.fang_length || 3),
			});
		}
		if (has('eye', 'eyes')) {
			S.eye = add({ id: 'eye', kind: 'eye', size: ctx.s(P.eye.size), height: P.eye.height, forward: P.eye.forward, protrude: P.eye.protrude ?? 0.5, tubular: ctx.s(P.eye.tubular || 0, 0) });
		}
		if (P.lure?.enabled && has('illicium', 'lure')) {
			const segments = ctx.lod([1, 2, P.lure.segments || 3]);
			S.lure = add({
				id: 'illicium',
				kind: 'lure',
				base: P.lure.base || 'head',
				position: P.lure.position ?? 0.2,
				segments: splitLength(ctx.s(P.lure.length), Math.max(1, segments), ctx.grid),
				lean: P.lure.lean ?? 15,
				arc: P.lure.arc ?? 120,
			});
			if (has('esca', 'illicium', 'lure')) S.esca = add({ id: 'esca', kind: 'light_organ', size: ctx.s(P.lure.esca || 2) });
		}
		if (P.dome?.enabled && has('dome', 'head')) S.dome = add({ id: 'dome', kind: 'dome', height: ctx.sy(P.dome.height) });
		if (has('tail')) {
			const n = Math.max(1, ctx.lod([1, Math.min(2, P.tail.segments), P.tail.segments]));
			const lengths = splitLength(ctx.sz(P.tail.length), n, ctx.grid);
			const taper = P.tail.taper ?? 0.7;
			const segs = lengths.map((l, i) => {
				const f = n === 1 ? 1 : 1 - (1 - taper) * (i / (n - 1));
				return { length: l, width: ctx.sx(P.tail.width * f), height: ctx.sy(P.tail.height * f) };
			});
			S.tail = add({ id: 'tail', kind: 'tail', segments: segs });
			S.caudal = add({ id: 'tail_fin', kind: 'caudal_fin', length: ctx.sz(P.caudal.length), height: ctx.sy(P.caudal.height), shape: P.caudal.shape || 'round' });
		}
		if (has('fin', 'fins', 'pectoral_fin') && P.pectoral?.enabled !== false) {
			S.pectoral = add({ id: 'fin', kind: 'pectoral_fins', length: ctx.sz(P.pectoral.length), height: ctx.sy(P.pectoral.height), angle: P.pectoral.angle ?? 30, droop: P.pectoral.droop ?? 10, position: P.pectoral.position ?? 0.25 });
		}
		if (has('fin', 'fins', 'pelvic_fin') && P.pelvic?.enabled && ctx.atLeast('medium')) {
			S.pelvic = add({ id: 'pelvic_fin', kind: 'pelvic_fins', length: ctx.sz(P.pelvic.length), height: ctx.sy(P.pelvic.height), position: P.pelvic.position ?? 0.6 });
		}
		if (has('fin', 'fins', 'dorsal_fin') && (P.dorsal?.count ?? 0) > 0) {
			S.dorsal = add({ id: 'dorsal_fin', kind: 'dorsal_fins', count: ctx.atLeast('medium') ? P.dorsal.count : 1, length: ctx.sz(P.dorsal.length), height: ctx.sy(P.dorsal.height), position: P.dorsal.position, second: P.dorsal.second_position ?? 0.9, apex: P.dorsal.apex ?? 0.8 });
		}
		if (has('fin', 'fins', 'anal_fin') && P.anal?.enabled && ctx.atLeast('medium')) {
			S.anal = add({ id: 'anal_fin', kind: 'anal_fin', length: ctx.sz(P.anal.length), height: ctx.sy(P.anal.height), position: P.anal.position ?? 0.7 });
		}
		if (P.photophores?.enabled && has('photophores', 'body')) {
			S.photophores = add({ id: 'photophores', kind: 'photophores', spacing: Math.max(1, P.photophores.spacing * ctx.scale[2]), radius: P.photophores.radius ?? 0.6, rows: P.photophores.rows ?? 1, head: P.photophores.head !== false });
		}
		if (P.gills?.slits > 0) S.gills = add({ id: 'gills', kind: 'gill_slits', slits: P.gills.slits });
		return S;
	}

	build(S, ctx) {
		const b = new ModelBuilder();
		const root = S.id;
		const [tw, th, tl] = S.body.size;
		const [hw, hh, hl] = S.head.size;
		const g = ctx.grid;
		const y0 = 32;
		const zHF = 0; // head front (jaw may protrude further)
		const zHB = zHF + hl; // head back / neck
		const zTF = zHB - g; // trunk overlaps the head by one pixel
		const zTB = zTF + tl;
		const tb = y0 - Math.floor(th / 2 / g) * g; // trunk bottom
		const tt = tb + th;
		const hb = y0 - Math.floor(hh / 2 / g) * g + S.head.drop; // head bottom
		const ht = hb + hh;
		const yMouth = S.jaw ? hb + S.jaw.height : hb;
		const mainBones = new Set(['body', 'head']);

		b.bone(root, { pivot: [0, 0, 0], role: 'root' });
		b.bone('body', { parent: root, pivot: [0, y0, zHB], role: 'body' });
		const br = th > 3 * S.body.round && tw > 2 * S.body.round ? S.body.round : 0;
		roundedBox(b, 'body', 'body', {
			from: [-tw / 2, tb, zTF],
			to: [tw / 2, tb + th, zTF + tl],
			round: { top: br, bottom: br, x: br, front: 0, back: br },
			material: 'skin',
			faces: { down: 'belly' },
			meta: S.gills && hl < 3 ? { slits: gillSlits(S.gills.slits, zTF, g) } : {},
		});

		// Head: cranium above the mouth line
		b.bone('head', { parent: 'body', pivot: [0, y0, zHB], role: 'head' });
		const hr = ht - yMouth > 3 * S.head.round && hw > 2 * S.head.round ? S.head.round : 0;
		roundedBox(b, 'head', 'head', {
			from: [-hw / 2, yMouth, zHF],
			to: [hw / 2, ht, zHB],
			round: { top: hr, x: hr, front: hr, back: hr },
			material: 'skin',
			faces: { down: S.jaw ? 'mouth' : 'belly' },
			meta: { lip: 0, depthAxis: 'z', ...(S.gills ? { slits: gillSlits(S.gills.slits, zHB - g * (S.gills.slits * 1.5 + 0.5), g) } : {}) },
		});

		if (S.snout) {
			const [sw, sh, sl] = S.snout.size;
			b.bone('snout', { parent: 'head', pivot: [0, ht - sh, zHF], role: 'snout' });
			b.box('snout', 'snout', { from: [-sw / 2, ht - sh - g, zHF - sl], to: [sw / 2, ht - g, zHF + g], material: 'skin', faces: { down: 'belly' } });
		}

		if (S.jaw) {
			const jw = S.jaw.width;
			const ub = S.jaw.underbite;
			const hingeZ = zHB - g;
			b.bone('mouth', { parent: 'head', pivot: [0, yMouth, hingeZ], rotation: [-S.jaw.restGape, 0, 0], role: 'jaw', meta: { gape: S.jaw.gape, restGape: S.jaw.restGape, protrude: S.jaw.protrude } });
			const jr = S.jaw.round;
			roundedBox(b, 'mouth', 'jaw', {
				from: [-jw / 2, hb, zHF - ub],
				to: [jw / 2, yMouth, zHB],
				round: { bottom: jr, x: jr, front: jr, back: 0 },
				material: 'skin',
				faces: { up: 'mouth', down: 'belly' },
				meta: { lip: g, depthAxis: 'z' },
			});
			if (ctx.atLeast('medium') && jw > 2 * g && hl > 3 * g && S.jaw.height > g) {
				const tz = zHF + Math.max(g, Math.round((hl * 0.4) / g) * g);
				b.box('head', 'throat', {
					from: [-(jw / 2 - g), hb + g, tz],
					to: [jw / 2 - g, yMouth, zHB - g],
					material: 'mouth',
					meta: { depthAxis: 'z' },
					priority: 2,
				});
			}
		}

		if (S.teeth) this.#teeth(b, S, { hw, yMouth, zHF, zHB, g });

		if (S.eye) this.#eyes(b, S, { hw, ht, yMouth, zHF, hl, g });

		if (S.dome) {
			const dh = S.dome.height;
			b.bone('dome', { parent: 'head', pivot: [0, ht, zHF], role: 'dome' });
			// One pixel wider than the head and shifted half a pixel so no face is coplanar with the head or eyes
			b.box('dome', 'dome', { from: [-(hw + g) / 2, ht - g, zHF - g / 2], to: [(hw + g) / 2, ht - g + dh, zHB - g / 2], material: 'dome', meta: { alpha: 84 } });
		}

		if (S.lure) this.#lure(b, S, { ht, tt, zHF, zTF, hl, tl, g });

		// Pectoral fins (left/right): flat membranes flaring out behind the head
		if (S.pectoral) {
			const { length: fl, height: fh, angle, droop, position } = S.pectoral;
			const pz = zTF + Math.max(g, Math.round((tl * position) / g) * g);
			const py = alignCenter(tb + th * 0.42, fh, g);
			for (const [side, sign] of [['right', 1], ['left', -1]]) {
				const x = (sign * tw) / 2;
				const name = `${side}_fin`;
				b.bone(name, { parent: 'body', pivot: [x, py, pz], rotation: [0, sign * angle, -sign * droop], role: 'fin', side });
				b.box(name, name, {
					from: [x, py - fh / 2, pz - g],
					to: [x + sign * ctx.finThickness, py + fh / 2, pz - g + fl + g],
					material: 'fin',
					shape: { type: 'fan', axis: 'z', dir: 1, across: 'y' },
					uvShare: side === 'left' ? 'right_fin' : null,
					mirror: side === 'left' && ctx.settings.mirrorUV,
				});
			}
		}

		if (S.pelvic) {
			const { length: fl, height: fh, position } = S.pelvic;
			const pz = zTF + Math.round((tl * position) / g) * g;
			for (const [side, sign] of [['right', 1], ['left', -1]]) {
				const x = sign * Math.max(g, tw / 2 - g);
				const name = `${side}_pelvic_fin`;
				b.bone(name, { parent: 'body', pivot: [x, tb, pz], rotation: [-20, sign * 20, 0], role: 'pelvic', side });
				b.box(name, name, { from: [x, tb - fh, pz - g], to: [x, tb + g, pz + fl], material: 'fin', shape: { type: 'triangle', axis: 'y', dir: -1, across: 'z', apex: 0.85 }, priority: 2 });
			}
		}

		if (S.dorsal) {
			const positions = S.dorsal.count > 1 ? [S.dorsal.position, S.dorsal.second] : [S.dorsal.position];
			positions.forEach((pos, i) => {
				const name = i === 0 ? 'dorsal_fin' : `dorsal_fin_${i + 1}`;
				const scale = i === 0 ? 1 : 0.7;
				const dl = Math.max(g, Math.round((S.dorsal.length * scale) / g) * g);
				const dh = Math.max(g, Math.round((S.dorsal.height * scale) / g) * g);
				const z0 = zTF + Math.round((clamp(pos, 0, 1) * (tl - dl)) / g) * g;
				b.bone(name, { parent: 'body', pivot: [0, tt, z0], role: 'dorsal', meta: { index: i } });
				b.box(name, name, { from: [0, tt - g, z0], to: [0, tt + dh, z0 + dl], material: 'fin', shape: { type: 'triangle', axis: 'y', dir: 1, across: 'z', apex: S.dorsal.apex }, priority: i === 0 ? 1 : 2 });
			});
		}

		if (S.anal) {
			const { length: al, height: ah, position } = S.anal;
			const z0 = zTF + Math.round((clamp(position, 0, 1) * (tl - al)) / g) * g;
			b.bone('anal_fin', { parent: 'body', pivot: [0, tb, z0], role: 'anal' });
			b.box('anal_fin', 'anal_fin', { from: [0, tb - ah, z0], to: [0, tb + g, z0 + al], material: 'fin', shape: { type: 'triangle', axis: 'y', dir: -1, across: 'z', apex: 0.8 }, priority: 2 });
		}

		// Tail chain + caudal fin
		if (S.tail) {
			let parent = 'body';
			let z = zTB - g;
			const yc = tb + Math.floor(th / 2 / g) * g;
			S.tail.segments.forEach((seg, i) => {
				const name = i === 0 ? 'tail' : `tail_${i + 1}`;
				b.bone(name, { parent, pivot: [0, yc, z], role: 'tail', meta: { index: i } });
				const last = i === S.tail.segments.length - 1;
				b.box(name, name, {
					from: [-seg.width / 2, yc - Math.floor(seg.height / 2 / g) * g, z],
					size: [seg.width, seg.height, seg.length + (last ? 0 : g)],
					material: 'skin',
					faces: { down: 'belly' },
				});
				mainBones.add(name);
				parent = name;
				z += seg.length;
			});
			const { length: cl, height: ch, shape } = S.caudal;
			b.bone('tail_fin', { parent, pivot: [0, yc, z], role: 'tail_fin' });
			const bottom = alignCenter(yc, ch, g) - ch / 2;
			b.box('tail_fin', 'tail_fin', {
				from: [0, bottom, z - g],
				to: [ctx.finThickness, bottom + ch, z + cl],
				material: 'fin',
				shape: { type: shape, axis: 'z', dir: 1, across: 'y' },
			});
		}

		// Photophores: rows of light spots along the lower flanks
		if (S.photophores) {
			const { spacing, radius, rows } = S.photophores;
			const rowY = [tb + g * 0.5, tb + g * 2.5].slice(0, rows);
			for (const y of rowY) {
				for (let z = zTF + spacing / 2; z < zTB - 0.5; z += spacing) {
					for (const sx of [1, -1]) b.spot('body', [(sx * tw) / 2, y, z], { radius });
					b.spot('body', [0, tb, z], { radius: radius * 0.8 });
				}
			}
			if (S.photophores.head) {
				for (let z = zHF + spacing; z < zHB - 0.5; z += spacing) {
					for (const sx of [1, -1]) b.spot('head', [(sx * hw) / 2, yMouth + g * 0.5, z], { radius });
				}
			}
		}

		b.ground({ centerFilter: (c) => mainBones.has(c.bone), snap: g });
		const geometry = b.build();
		geometry.root = root;
		return geometry;
	}

	#teeth(b, S, { hw, yMouth, zHF, zHB, g }) {
		const T = S.teeth;
		const jw = S.jaw.width;
		const ub = S.jaw.underbite;
		const len = T.length;
		const xs = spread(T.count, jw - 2 * g, g);
		const upperXs = xs.length > 1 ? xs.slice(1).map((x, i) => alignTooth((x + xs[i]) / 2, jw, g)) : xs;
		if (T.upper) {
			b.bone('upper_teeth', { parent: 'head', pivot: [0, yMouth, zHF], role: 'teeth', meta: { jaw: 'upper' } });
			const front = ub < 0 ? zHF - ub : zHF;
			upperXs.forEach((x, i) => {
				const l = len + (T.vary && i % 2 === 1 ? g : 0);
				b.box('upper_teeth', 'tooth_upper', { from: [x - g / 2, yMouth - l, front], size: [g, l, g], material: 'teeth', meta: { toothDir: -1 }, priority: 2 });
			});
			for (let i = 0; i < T.side; i++) {
				const z = zHF + g * (2 + i * 2);
				if (z >= zHB - 2 * g) break;
				for (const sx of [1, -1]) b.box('upper_teeth', 'tooth_upper_side', { from: [sx * (hw / 2 - 1.5 * g) - g / 2, yMouth - len, z], size: [g, len, g], material: 'teeth', plane: 'x', meta: { toothDir: -1 }, priority: 3 });
			}
			for (let i = 0; i < T.fangs; i++) {
				for (const sx of [1, -1]) {
					const x = sx * (jw / 2 + g / 2);
					b.box('upper_teeth', 'fang_upper', { from: [x - g / 2, yMouth - T.fangLength + g, zHF + g * (1 + i * 2)], size: [g, T.fangLength, g], material: 'teeth', plane: 'x', meta: { toothDir: -1 }, priority: 1 });
				}
			}
		}
		if (T.lower) {
			const front = zHF - ub;
			b.bone('lower_teeth', { parent: 'mouth', pivot: [0, yMouth, front], role: 'teeth', meta: { jaw: 'lower' } });
			xs.forEach((x, i) => {
				const l = len + (T.vary && i % 2 === 1 ? g : 0);
				b.box('lower_teeth', 'tooth_lower', { from: [x - g / 2, yMouth, front], size: [g, l, g], material: 'teeth', meta: { toothDir: 1 }, priority: 2 });
			});
			for (let i = 0; i < T.side; i++) {
				const z = front + g * (2 + i * 2);
				if (z >= zHB - 2 * g) break;
				for (const sx of [1, -1]) b.box('lower_teeth', 'tooth_lower_side', { from: [sx * (jw / 2 - 1.5 * g) - g / 2, yMouth, z], size: [g, len, g], material: 'teeth', plane: 'x', meta: { toothDir: 1 }, priority: 3 });
			}
			for (let i = 0; i < T.fangs; i++) {
				// Long lower fangs rise in front of the upper jaw (viperfish)
				for (const sx of [1, -1]) {
					const x = sx * Math.max(g, jw / 2 - g * (1.5 + i * 2));
					b.box('lower_teeth', 'fang_lower', { from: [x - g / 2, yMouth - g, front - g], size: [g, T.fangLength + g, g], material: 'teeth', meta: { toothDir: 1 }, priority: 1 });
				}
			}
		}
	}

	#eyes(b, S, { hw, ht, yMouth, zHF, hl, g }) {
		const E = S.eye;
		const es = E.size;
		const ez = alignCenter(zHF + hl * clamp(E.forward, 0, 1), es, g);
		for (const [side, sign] of [['right', 1], ['left', -1]]) {
			const name = `${side}_eye`;
			if (E.tubular > 0) {
				// Barreleye style: upward-looking tubular eyes on top of the head (inside the dome)
				const cx = sign * (es / 2 + g / 2);
				const base = ht - g;
				b.bone(name, { parent: 'head', pivot: [cx, base, ez], role: 'eye', side });
				b.box(name, name, { from: [cx - es / 2, base, ez - es / 2], size: [es, E.tubular, es], material: 'eye', meta: { eyeFace: 'up', glintLeft: sign < 0 } });
				continue;
			}
			const ey = alignCenter(yMouth + (ht - yMouth) * clamp(E.height, 0, 1), es, g);
			const cx = sign * (hw / 2 + es * (E.protrude - 0.5));
			b.bone(name, { parent: 'head', pivot: [cx, ey, ez], role: 'eye', side });
			b.box(name, name, {
				center: [cx, ey, ez],
				size: [es, es, es],
				material: 'eye',
				meta: { eyeFace: sign > 0 ? 'east' : 'west', glintLeft: sign < 0 },
			});
		}
	}

	#lure(b, S, { ht, tt, zHF, zTF, hl, tl, g }) {
		const L = S.lure;
		let parent, y, z;
		if (L.base === 'dorsal') {
			parent = 'body';
			y = tt;
			z = zTF + Math.round((tl * L.position) / g) * g + g / 2;
		} else {
			parent = 'head';
			y = ht;
			z = zHF + Math.round((hl * L.position) / g) * g + g / 2;
		}
		const n = L.segments.length;
		const rest = n > 1 ? (L.arc - L.lean) / (n - 1) : 0;
		L.segments.forEach((len, i) => {
			const name = i === 0 ? 'illicium' : `illicium_${i + 1}`;
			const rot = i === 0 ? -(n > 1 ? L.lean : L.arc * 0.5) : -rest;
			b.bone(name, { parent, pivot: [0, y, z], rotation: [rot, 0, 0], role: 'lure', meta: { index: i } });
			b.box(name, name, {
				from: [-g / 2, y - (i === 0 ? g : 0), z - g / 2],
				to: [g / 2, y + len, z + g / 2],
				material: 'lure',
				meta: { tipFactor: (i + 1) / n },
				priority: i === 0 ? 1 : 2,
			});
			parent = name;
			y += len;
		});
		if (S.esca) {
			const e = S.esca.size;
			b.bone('esca', { parent, pivot: [0, y, z], role: 'esca' });
			b.box('esca', 'esca', { from: [-e / 2, y - g / 2, z - e / 2], size: [e, e, e], material: 'glow', glow: { intensity: 1 } });
			if (b.cubes.length && e >= 2 * g) {
				b.box('esca', 'esca_tip', { from: [-g / 2, y - g / 2 + e, z - g / 2], size: [g, g, g], material: 'glow', glow: { intensity: 0.75 }, priority: 3 });
			}
		}
	}

	animationCatalog() {
		return FISH_ANIMATIONS;
	}
}

/** Snaps a tooth centre so its faces stay on the jaw's pixel grid. */
function alignTooth(x, width, g) {
	const base = -width / 2;
	return base + Math.round((x - g / 2 - base) / g) * g + g / 2;
}

function gillSlits(count, zStart, g) {
	return { count, start: zStart, spacing: 1.5 * g };
}

const side = (bone) => (bone.side === 'left' ? -1 : 1);

export const FISH_ANIMATIONS = {
	idle({ amp, make, roles }) {
		const c = make(3, 'loop', 'Hovering: slow bob, breathing jaw, fluttering fins, swaying lure');
		c.wave('body', 'position', { amplitude: [0, 0.35 * amp, 0], cycles: 1 });
		c.wave('body', 'rotation', { amplitude: [0, 0, 1.5 * amp], cycles: 1, phase: 0.25 });
		roles.all('tail').forEach((t, i) => c.wave(t.name, 'rotation', { amplitude: [0, 6 * amp, 0], cycles: 1, phase: -0.12 * i }));
		c.wave('tail_fin', 'rotation', { amplitude: [0, 9 * amp, 0], cycles: 1, phase: -0.2 });
		for (const fin of roles.all('fin')) c.wave(fin.name, 'rotation', { amplitude: [0, side(fin) * 10 * amp, side(fin) * 4 * amp], cycles: 2 });
		for (const fin of roles.all('pelvic')) c.wave(fin.name, 'rotation', { amplitude: [4 * amp, 0, 0], cycles: 2, phase: 0.3 });
		roles.all('dorsal').forEach((f, i) => c.wave(f.name, 'rotation', { amplitude: [0, 0, 3 * amp], cycles: 2, phase: 0.2 * i }));
		if (roles.one('jaw')) c.wave('mouth', 'rotation', { amplitude: [-6 * amp, 0, 0], cycles: 1, shape: 'cos1' });
		roles.all('lure').forEach((l, i) => {
			c.wave(l.name, 'rotation', { amplitude: [5 * amp, 0, 3 * amp], cycles: 1, phase: 0.15 * i });
		});
		if (roles.one('esca')) c.wave('esca', 'scale', { amplitude: [0.08, 0.08, 0.08], cycles: 2 });
		return c.build();
	},
	swim({ amp, make, roles }) {
		const c = make(1.2, 'loop', 'Swimming: tail beat with body counter-sway, paddling fins, lure dragging back');
		c.wave('body', 'rotation', { amplitude: [0, 5 * amp, 0], cycles: 1, phase: 0.5 });
		c.wave('body', 'position', { amplitude: [0, 0.3 * amp, 0], cycles: 2 });
		c.wave('head', 'rotation', { amplitude: [0, 3 * amp, 0], cycles: 1, phase: 0.5 });
		roles.all('tail').forEach((t, i) => c.wave(t.name, 'rotation', { amplitude: [0, (20 + i * 6) * amp, 0], cycles: 1, phase: -0.12 * i }));
		c.wave('tail_fin', 'rotation', { amplitude: [0, 24 * amp, 0], cycles: 1, phase: -0.18 - 0.1 * (roles.all('tail').length - 1) });
		for (const fin of roles.all('fin')) c.wave(fin.name, 'rotation', { amplitude: [0, side(fin) * 22 * amp, 0], cycles: 2 });
		for (const fin of roles.all('pelvic')) c.wave(fin.name, 'rotation', { amplitude: [8 * amp, 0, 0], cycles: 2 });
		roles.all('dorsal').forEach((f) => c.wave(f.name, 'rotation', { amplitude: [0, 6 * amp, 0], cycles: 1, phase: -0.1 }));
		if (roles.one('anal')) c.wave('anal_fin', 'rotation', { amplitude: [0, 6 * amp, 0], cycles: 1, phase: -0.1 });
		if (roles.one('jaw')) c.wave('mouth', 'rotation', { amplitude: [-3 * amp, 0, 0], cycles: 1, shape: 'cos1' });
		roles.all('lure').forEach((l, i) => c.wave(l.name, 'rotation', { amplitude: [4 * amp, 0, 0], offset: [i === 0 ? 12 * amp : 6 * amp, 0, 0], cycles: 2, phase: 0.1 * i }));
		return c.build();
	},
	hurt({ amp, make, roles }) {
		const c = make(0.5, 'once', 'Flinch: twist, jolt and snap');
		c.keys('body', 'rotation', [[0, [0, 0, 0]], [0.1, [0, 8 * amp, -12 * amp]], [0.25, [0, -5 * amp, 8 * amp]], [0.5, [0, 0, 0]]]);
		c.keys('body', 'position', [[0, [0, 0, 0]], [0.1, [0.6 * amp, 0, 0]], [0.25, [-0.4 * amp, 0, 0]], [0.5, [0, 0, 0]]]);
		if (roles.one('jaw')) c.keys('mouth', 'rotation', [[0, [0, 0, 0]], [0.1, [-25 * amp, 0, 0]], [0.35, [-10 * amp, 0, 0]], [0.5, [0, 0, 0]]]);
		roles.all('tail').forEach((t) => c.keys(t.name, 'rotation', [[0, [0, 0, 0]], [0.12, [0, 25 * amp, 0]], [0.3, [0, -12 * amp, 0]], [0.5, [0, 0, 0]]]));
		for (const fin of roles.all('fin')) c.keys(fin.name, 'rotation', [[0, [0, 0, 0]], [0.1, [0, side(fin) * 25 * amp, 0]], [0.5, [0, 0, 0]]]);
		return c.build();
	},
	death({ amp, def, make, roles }) {
		const c = make(1.6, 'hold', 'Rolls onto its side and sinks, jaw slack, lure drooping, light fading');
		c.keys('body', 'rotation', [[0, [0, 0, 0]], [0.5, [-5, 0, 35]], [1.2, [-10, 0, 90]], [1.6, [-10, 0, 90]]]);
		c.keys('body', 'position', [[0, [0, 0, 0]], [1.6, [0, -1.5, 0]]], 'linear');
		const gape = roles.one('jaw')?.meta.gape ?? def.params?.mouth?.gape ?? 35;
		if (roles.one('jaw')) c.keys('mouth', 'rotation', [[0, [0, 0, 0]], [0.8, [-gape * 0.8, 0, 0]], [1.6, [-gape * 0.75, 0, 0]]]);
		roles.all('tail').forEach((t) => c.keys(t.name, 'rotation', [[0, [0, 0, 0]], [1, [0, 18 * amp, 0]], [1.6, [0, 15 * amp, 0]]]));
		for (const fin of roles.all('fin')) c.keys(fin.name, 'rotation', [[0, [0, 0, 0]], [1.2, [0, -side(fin) * 20, 0]], [1.6, [0, -side(fin) * 20, 0]]]);
		roles.all('lure').forEach((l) => c.keys(l.name, 'rotation', [[0, [0, 0, 0]], [1.2, [30, 0, 0]], [1.6, [32, 0, 0]]]));
		if (roles.one('esca')) c.keys('esca', 'scale', [[0, [1, 1, 1]], [1.6, [0.6, 0.6, 0.6]]], 'linear');
		return c.build();
	},
	mouth_open({ def, make, roles }) {
		if (!roles.one('jaw')) return null;
		const gape = roles.one('jaw').meta.gape ?? def.params?.mouth?.gape ?? 35;
		const p = roles.one('jaw').meta.protrude || 0;
		const c = make(0.5, 'hold', p ? 'Shoots the jaw forward and opens it (protrusible jaw)' : 'Opens the jaw to full gape and holds');
		c.keys('mouth', 'rotation', [[0, [0, 0, 0]], [0.3, [-gape * 1.05, 0, 0]], [0.5, [-gape, 0, 0]]]);
		if (p) c.keys('mouth', 'position', [[0, [0, 0, 0]], [0.25, [0, -p * 0.3, -p]], [0.5, [0, -p * 0.3, -p]]]);
		c.keys('head', 'rotation', [[0, [0, 0, 0]], [0.5, [4, 0, 0]]]);
		return c.build();
	},
	mouth_close({ def, make, roles }) {
		if (!roles.one('jaw')) return null;
		const gape = roles.one('jaw').meta.gape ?? def.params?.mouth?.gape ?? 35;
		const p = roles.one('jaw').meta.protrude || 0;
		const c = make(0.35, 'once', 'Snaps the jaw shut from full gape');
		c.keys('mouth', 'rotation', [[0, [-gape, 0, 0]], [0.35, [0, 0, 0]]], 'linear');
		if (p) c.keys('mouth', 'position', [[0, [0, -p * 0.3, -p]], [0.35, [0, 0, 0]]], 'linear');
		c.keys('head', 'rotation', [[0, [4, 0, 0]], [0.35, [0, 0, 0]]], 'linear');
		return c.build();
	},
	bite({ def, make, roles }) {
		if (!roles.one('jaw')) return null;
		const gape = roles.one('jaw').meta.gape ?? def.params?.mouth?.gape ?? 35;
		const p = roles.one('jaw').meta.protrude || 0;
		const c = make(0.6, 'once', 'Lunge and bite');
		c.keys('mouth', 'rotation', [[0, [0, 0, 0]], [0.2, [-gape, 0, 0]], [0.32, [-gape, 0, 0]], [0.42, [0, 0, 0]], [0.6, [0, 0, 0]]]);
		if (p) c.keys('mouth', 'position', [[0, [0, 0, 0]], [0.2, [0, -p * 0.3, -p]], [0.36, [0, -p * 0.3, -p]], [0.46, [0, 0, 0]], [0.6, [0, 0, 0]]]);
		c.keys('body', 'position', [[0, [0, 0, 0]], [0.3, [0, 0, -2]], [0.6, [0, 0, 0]]]);
		return c.build();
	},
	glow({ amp, make, roles }) {
		if (!roles.one('esca') && !roles.one('lure')) return null;
		const c = make(2, 'loop', 'Lure display: pulsing esca and twitching illicium');
		if (roles.one('esca')) {
			c.wave('esca', 'scale', { amplitude: [0.3, 0.3, 0.3], cycles: 2, shape: 'pulse' });
			c.wave('esca', 'rotation', { amplitude: [0, 15 * amp, 0], cycles: 1 });
		}
		roles.all('lure').forEach((l, i) => c.wave(l.name, 'rotation', { amplitude: [6 * amp, 0, 4 * amp], cycles: 2, phase: 0.18 * i }));
		return c.build();
	},
};
