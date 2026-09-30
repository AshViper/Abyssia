// .bbmodel writer. Mirrors what Blockbench 5.x itself saves (js/formats/bbmodel.js,
// FORMATV 5.0: "groups" array + UUID outliner) and can also write the Blockbench 4.x layout
// (format 4.10: nested group objects, keyframes with Bedrock-style X/Y rotation and X position).

import { FACE_KEYS } from '../uv/box_uv.js';
import { pngDataURL } from '../texture/png.js';
import { uuidFactory } from './uuid.js';

const ROLE_COLORS = {
	root: 0,
	body: 0,
	head: 1,
	snout: 1,
	jaw: 2,
	teeth: 3,
	eye: 4,
	lure: 5,
	esca: 5,
	fin: 6,
	pelvic: 6,
	dorsal: 6,
	anal: 6,
	tail: 7,
	tail_fin: 7,
};

const fmt = (v) => {
	const r = Math.round(v * 10000) / 10000;
	return String(Object.is(r, -0) ? 0 : r);
};

/** Animation name as stored in the project. */
export function animationName(model, clipName) {
	const naming = model.settings.animationNaming;
	const full = naming === 'bedrock' || (naming !== 'plain' && model.settings.modelFormat !== 'modded_entity');
	return full ? `animation.${model.id}.${clipName}` : clipName;
}

function textureEntry(model, { name, image, uuid, index, renderMode }) {
	return {
		path: '',
		name,
		folder: 'entity',
		namespace: model.settings.modId || '',
		id: String(index),
		group: '',
		width: image.width,
		height: image.height,
		uv_width: model.uv.width,
		uv_height: model.uv.height,
		particle: false,
		use_as_default: false,
		layers_enabled: false,
		sync_to_project: '',
		render_mode: renderMode,
		render_sides: 'auto',
		pbr_channel: 'color',
		frame_time: 1,
		frame_order_type: 'loop',
		frame_order: '',
		frame_interpolate: false,
		visible: true,
		internal: true,
		saved: false,
		uuid,
		relative_path: name,
		source: pngDataURL(image),
	};
}

/**
 * Builds the .bbmodel JSON object.
 * @param {object} model result of generateModel()
 */
export function buildBBModel(model) {
	const s = model.settings;
	const legacy = s.formatVersion === '4.10';
	const uuid = uuidFactory(`${model.id}:${model.seed}:${s.modelFormat}`);
	const { geometry, uv } = model;
	const boxUV = uv.mode === 'box';

	// Textures: base + glow layer (rendered as an overlay layer in Blockbench's single-texture formats)
	const textures = [];
	if (model.textures?.base) {
		textures.push(textureEntry(model, { name: `${model.id}.png`, image: model.textures.base, uuid: uuid('texture:base'), index: 0, renderMode: 'default' }));
	}
	if (model.textures?.glow) {
		textures.push(textureEntry(model, { name: `${model.id}_glow.png`, image: model.textures.glow, uuid: uuid('texture:glow'), index: textures.length, renderMode: 'layered' }));
	}
	const baseIndex = textures.length ? 0 : null;

	const boneColor = new Map(geometry.bones.map((b) => [b.name, ROLE_COLORS[b.role] ?? 0]));
	const elements = geometry.cubes.map((cube) => {
		const cuv = uv.cubes[cube.name];
		const faces = {};
		for (const face of FACE_KEYS) {
			faces[face] = {
				uv: cuv.faces[face].map((v) => Math.round(v * 10000) / 10000),
				texture: cuv.enabled[face] ? baseIndex : null,
			};
		}
		const el = {
			name: cube.name,
			box_uv: boxUV,
			render_order: 'default',
			locked: false,
			allow_mirror_modeling: true,
			from: cube.from,
			to: cube.to,
			autouv: 0,
			color: boneColor.get(cube.bone) ?? 0,
			origin: cube.origin,
		};
		if (cube.rotation.some((v) => v !== 0)) el.rotation = cube.rotation;
		if (boxUV) el.uv_offset = cuv.offset;
		if (cuv.mirror) el.mirror_uv = true;
		el.faces = faces;
		el.type = 'cube';
		el.uuid = uuid(`cube:${cube.name}`);
		return el;
	});

	// Hierarchy
	const children = new Map(geometry.bones.map((b) => [b.name, []]));
	const roots = [];
	for (const bone of geometry.bones) {
		if (bone.parent && children.has(bone.parent)) children.get(bone.parent).push({ type: 'bone', bone });
		else roots.push(bone);
	}
	const cubesOf = new Map(geometry.bones.map((b) => [b.name, []]));
	for (const cube of geometry.cubes) cubesOf.get(cube.bone)?.push(cube);
	const depthOf = (bone) => {
		let d = 0;
		let p = bone.parent;
		const byName = new Map(geometry.bones.map((b) => [b.name, b]));
		while (p) {
			d++;
			p = byName.get(p)?.parent;
		}
		return d;
	};
	const groupProps = (bone) => ({
		name: bone.name,
		origin: bone.pivot,
		rotation: bone.rotation,
		color: boneColor.get(bone.name) ?? 0,
		uuid: uuid(`group:${bone.name}`),
		export: true,
		mirror_uv: false,
		isOpen: depthOf(bone) < 2,
		locked: false,
		visibility: true,
		autouv: 0,
	});
	const outlinerNode = (bone) => {
		const kids = [...cubesOf.get(bone.name).map((c) => uuid(`cube:${c.name}`)), ...children.get(bone.name).map((k) => outlinerNode(k.bone))];
		if (legacy) {
			const g = groupProps(bone);
			if (bone.rotation.every((v) => v === 0)) delete g.rotation;
			return { ...g, children: kids };
		}
		return { uuid: uuid(`group:${bone.name}`), isOpen: depthOf(bone) < 2, children: kids };
	};
	const outliner = roots.map(outlinerNode);

	const animations = (model.animations || []).map((clip) => {
		const animators = {};
		for (const [boneName, channels] of Object.entries(clip.tracks)) {
			const keyframes = [];
			for (const [channel, keys] of Object.entries(channels)) {
				keys.forEach((k, i) => {
					let [x, y, z] = k.value;
					if (legacy && (channel === 'rotation' || channel === 'position')) x = -x;
					if (legacy && channel === 'rotation') y = -y;
					keyframes.push({
						channel,
						data_points: [{ x: fmt(x), y: fmt(y), z: fmt(z) }],
						uuid: uuid(`kf:${clip.name}:${boneName}:${channel}:${i}`),
						time: k.time,
						color: -1,
						interpolation: k.interpolation,
						...(channel === 'scale' ? { uniform: Math.abs(x - y) < 1e-6 && Math.abs(y - z) < 1e-6 } : {}),
					});
				});
			}
			animators[uuid(`group:${boneName}`)] = { name: boneName, type: 'bone', keyframes };
		}
		return {
			uuid: uuid(`animation:${clip.name}`),
			name: animationName(model, clip.name),
			loop: clip.loop,
			override: false,
			length: clip.length,
			snapping: 20,
			selected: false,
			anim_time_update: '',
			blend_weight: '',
			start_delay: '',
			loop_delay: '',
			animators,
		};
	});

	const b = model.bounds.size;
	const root = {
		meta: { format_version: legacy ? '4.10' : '5.0', model_format: s.modelFormat, box_uv: boxUV },
		name: model.id,
		model_identifier: model.id,
	};
	if (s.modelFormat === 'modded_entity') {
		root.modded_entity_entity_class = '';
		root.modded_entity_version = '1.17';
		root.modded_entity_flip_y = true;
	}
	if (s.modelFormat === 'geckolib_model') {
		root.geckolib_modid = s.modId || '';
		root.geckolib_model_type = 'Entity';
	}
	if (s.modelFormat === 'bedrock') root.bedrock_animation_mode = 'entity';
	Object.assign(root, {
		visible_box: [Math.max(1, Math.ceil((Math.max(b[0], b[2]) / 16) * 2) / 2), Math.max(1, Math.ceil((b[1] / 16) * 2) / 2), Math.round((b[1] / 32) * 100) / 100],
		variable_placeholders: '',
		variable_placeholder_buttons: [],
		timeline_setups: [],
		unhandled_root_fields: {},
		resolution: { width: uv.width, height: uv.height },
		elements,
	});
	if (!legacy) root.groups = geometry.bones.map((bone) => ({ ...groupProps(bone), children: [] }));
	root.outliner = outliner;
	root.textures = textures;
	if (animations.length) root.animations = animations;
	return root;
}

/** JSON text in Blockbench's style (tab indentation, short arrays on one line). */
export function stringifyBBModel(value, indent = '') {
	if (Array.isArray(value)) {
		if (!value.length) return '[]';
		if (value.every((v) => v === null || typeof v !== 'object')) return '[' + value.map((v) => JSON.stringify(v)).join(', ') + ']';
		const inner = indent + '\t';
		return '[\n' + value.map((v) => inner + stringifyBBModel(v, inner)).join(',\n') + '\n' + indent + ']';
	}
	if (value && typeof value === 'object') {
		const keys = Object.keys(value).filter((k) => value[k] !== undefined);
		if (!keys.length) return '{}';
		const inner = indent + '\t';
		return '{\n' + keys.map((k) => inner + JSON.stringify(k) + ': ' + stringifyBBModel(value[k], inner)).join(',\n') + '\n' + indent + '}';
	}
	return JSON.stringify(value);
}
