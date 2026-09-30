// Validation run before a .bbmodel is written (and again on the written JSON).
// Results: {level: 'error'|'warning'|'info', check, message, target}

import { FACE_KEYS, cubeSize, faceHasArea, uvRect } from '../uv/box_uv.js';
import { UUID_RE } from './uuid.js';

const NAME_RE = /^[a-z0-9_]+$/;
const LIMIT = 512; // |coordinate| beyond this is almost certainly a bug
const LARGE_BLOCKS = 6;

class Report {
	constructor() {
		this.items = [];
	}
	add(level, check, message, target = null) {
		this.items.push({ level, check, message, target });
	}
	error(check, message, target) {
		this.add('error', check, message, target);
	}
	warn(check, message, target) {
		this.add('warning', check, message, target);
	}
	info(check, message, target) {
		this.add('info', check, message, target);
	}
	result() {
		const count = (lvl) => this.items.filter((i) => i.level === lvl).length;
		return { ok: count('error') === 0, errors: count('error'), warnings: count('warning'), infos: count('info'), items: this.items };
	}
}

const finite = (v) => typeof v === 'number' && Number.isFinite(v);
const vecOk = (v, n = 3) => Array.isArray(v) && v.length === n && v.every(finite);

/** Checks generated model data (geometry, UV, textures, animations). */
export function validateModel(model) {
	const r = new Report();
	const { geometry, uv, animations } = model;

	for (const w of model.warnings || []) r.warn('generator', w);

	// Blockbench: only formats with optional_box_uv keep per-face UVs (Modded Entity forces Box UV,
	// and Java ModelPart / CubeListBuilder only supports Box UV texture offsets).
	if (uv.mode === 'per_face' && model.settings.modelFormat === 'modded_entity') {
		r.error('uv_mode_format', 'Per Face UV は Modded Entity 形式では使えません（Blockbench が Box UV に戻し、Java の ModelPart も Box UV のみ）。Box UV にするか、GeckoLib / Bedrock / Generic 形式を選んでください');
	}

	// Bones
	const boneNames = new Map();
	for (const bone of geometry.bones) {
		boneNames.set(bone.name, (boneNames.get(bone.name) || 0) + 1);
		if (!NAME_RE.test(bone.name)) r.error('bone_name', `Bone名に使えない文字があります: "${bone.name}"（a-z, 0-9, _ のみ）`, bone.name);
		if (!vecOk(bone.pivot)) r.error('pivot_invalid', `不正なPivotです: ${bone.name} [${bone.pivot}]`, bone.name);
		if (!vecOk(bone.rotation)) r.error('rotation_invalid', `不正な回転です: ${bone.name} [${bone.rotation}]`, bone.name);
		if (bone.parent && !geometry.bones.some((b) => b.name === bone.parent)) r.error('bone_parent', `Bone "${bone.name}" の親 "${bone.parent}" が存在しません`, bone.name);
	}
	for (const [name, n] of boneNames) if (n > 1) r.error('bone_duplicate', `Bone名が重複しています: "${name}" ×${n}`, name);

	// Empty bones
	const hasCubes = new Set(geometry.cubes.map((c) => c.bone));
	const hasChildren = new Set(geometry.bones.map((b) => b.parent).filter(Boolean));
	for (const bone of geometry.bones) {
		if (!hasCubes.has(bone.name) && !hasChildren.has(bone.name)) r.warn('empty_bone', `空のBoneです（Cubeも子Boneもありません）: ${bone.name}`, bone.name);
	}

	// Cubes
	const cubeNames = new Map();
	const b = model.bounds;
	for (const cube of geometry.cubes) {
		cubeNames.set(cube.name, (cubeNames.get(cube.name) || 0) + 1);
		if (!boneNames.has(cube.bone)) r.error('cube_bone', `Cube "${cube.name}" のBone "${cube.bone}" が存在しません`, cube.name);
		for (const key of ['from', 'to', 'origin', 'rotation']) {
			if (!vecOk(cube[key])) r.error('coord_invalid', `Cube "${cube.name}" の ${key} が不正です`, cube.name);
		}
		const extreme = [...cube.from, ...cube.to, ...cube.origin].find((v) => Math.abs(v) > LIMIT);
		if (extreme !== undefined) r.error('coord_extreme', `極端な座標です: ${cube.name} (${extreme})`, cube.name);
		const size = cubeSize(cube);
		if (size.some((v) => v < 0)) r.error('cube_size', `Cube "${cube.name}" のサイズが負です`, cube.name);
		if (size.filter((v) => v === 0).length >= 2) r.warn('cube_degenerate', `Cube "${cube.name}" は2軸以上がサイズ0です（表示されません）`, cube.name);
		if (uv.mode === 'box') {
			if (size.some((v) => v > 0.005 && v < 0.999)) r.warn('box_uv_small', `Box UVで1未満のサイズがあります（Blockbenchで表示が崩れます）: ${cube.name}`, cube.name);
			else if (size.some((v) => Math.abs(v - Math.round(v)) > 1e-6)) r.warn('box_uv_fraction', `Box UVで端数サイズがあります（テクスチャがずれます）: ${cube.name}`, cube.name);
		}
		if (Math.abs(cube.rotation[0]) > 180 || Math.abs(cube.rotation[1]) > 180 || Math.abs(cube.rotation[2]) > 180) {
			r.warn('rotation_range', `Cube "${cube.name}" の回転が ±180° を超えています`, cube.name);
		}
	}
	for (const [name, n] of cubeNames) if (n > 1) r.error('cube_duplicate', `Cube名が重複しています: "${name}" ×${n}`, name);

	// Pivots far away from everything
	if (b) {
		const margin = 24;
		for (const bone of geometry.bones) {
			if (!vecOk(bone.pivot)) continue;
			const far = bone.pivot.some((v, i) => v < b.min[i] - margin || v > b.max[i] + margin);
			if (far) r.warn('pivot_far', `Pivotがモデルから大きく離れています: ${bone.name} [${bone.pivot.join(', ')}]`, bone.name);
		}
		const blocks = Math.max(...b.size) / 16;
		if (blocks > LARGE_BLOCKS) r.warn('model_large', `モデルが大きいです（最大 ${blocks.toFixed(1)} ブロック）。Rendererでのスケールを検討してください`);
	}

	checkUV(model, r);
	checkTextures(model, r);
	checkAnimations(geometry, animations || [], r);
	return r.result();
}

function checkUV(model, r) {
	const { uv, geometry } = model;
	const W = uv.width, H = uv.height;
	const owner = new Int32Array(W * H).fill(-1);
	const reported = new Set();
	geometry.cubes.forEach((cube, ci) => {
		const cuv = uv.cubes[cube.name];
		if (!cuv) {
			r.error('uv_missing', `Cube "${cube.name}" にUVがありません`, cube.name);
			return;
		}
		FACE_KEYS.forEach((face, fi) => {
			const f = cuv.faces[face];
			if (!f || f.some((v) => !finite(v))) {
				r.error('uv_invalid', `不正なUVです: ${cube.name}.${face}`, cube.name);
				return;
			}
			if (!cuv.enabled[face] || !faceHasArea(f)) return;
			const rect = uvRect(f);
			if (rect.x < -1e-6 || rect.y < -1e-6 || rect.x + rect.w > W + 1e-6 || rect.y + rect.h > H + 1e-6) {
				r.error('uv_out_of_range', `UVがテクスチャ範囲外です: ${cube.name}.${face} [${f.join(', ')}] / ${W}×${H}`, cube.name);
				return;
			}
			if (cuv.sharedFrom) return;
			const id = ci * 6 + fi;
			for (let y = Math.floor(rect.y); y < Math.ceil(rect.y + rect.h - 1e-9); y++) {
				for (let x = Math.floor(rect.x); x < Math.ceil(rect.x + rect.w - 1e-9); x++) {
					const o = owner[y * W + x];
					if (o >= 0 && o !== id) {
						const other = geometry.cubes[Math.floor(o / 6)].name;
						const key = [other, cube.name].sort().join('|');
						if (!reported.has(key)) {
							reported.add(key);
							const lvl = model.settings.generateUV ? 'error' : 'warning';
							r.add(lvl, 'uv_overlap', `UVが重なっています: ${other} と ${cube.name}${other === cube.name ? ` (${face})` : ''}`, cube.name);
						}
					} else owner[y * W + x] = id;
				}
			}
		});
	});
	const shared = geometry.cubes.filter((c) => uv.cubes[c.name]?.sharedFrom).length;
	if (shared) r.info('uv_shared', `${shared} 個のCubeが左右対称パーツのUVをミラー共有しています（Mirror UV）`);
}

function checkTextures(model, r) {
	const { uv, textures } = model;
	if (!textures?.base) {
		r.error('texture_missing', 'ベーステクスチャがありません');
		return;
	}
	const expW = Math.round(uv.width * uv.texelsPerUnit), expH = Math.round(uv.height * uv.texelsPerUnit);
	if (textures.base.width !== expW || textures.base.height !== expH) {
		r.error('texture_size', `テクスチャ ${textures.base.width}×${textures.base.height} が UV解像度 ${uv.width}×${uv.height} × Pixel Density と一致しません`);
	}
	if (textures.base.countOpaque() === 0) r.error('texture_empty', 'ベーステクスチャが空です');
	if (model.settings.glowLayer && !textures.glow) r.info('glow_none', '発光部位がないため Glow テクスチャは生成されません');
}

function checkAnimations(geometry, animations, r) {
	const bones = new Set(geometry.bones.map((b) => b.name));
	const names = new Set();
	for (const clip of animations) {
		if (names.has(clip.name)) r.error('anim_duplicate', `Animation名が重複しています: ${clip.name}`);
		names.add(clip.name);
		if (!(clip.length > 0)) r.error('anim_length', `Animation "${clip.name}" の長さが不正です`);
		for (const [bone, channels] of Object.entries(clip.tracks)) {
			if (!bones.has(bone)) r.error('anim_missing_bone', `Animation "${clip.name}" が存在しないBone "${bone}" を参照しています`, bone);
			for (const [channel, keys] of Object.entries(channels)) {
				if (keys.some((k) => !vecOk(k.value) || !finite(k.time))) r.error('anim_nan', `Animation "${clip.name}" の ${bone}.${channel} に不正な値があります`, bone);
				if (keys.some((k) => k.time < -1e-6 || k.time > clip.length + 1e-6)) r.warn('anim_time', `Animation "${clip.name}" の ${bone}.${channel} に長さ外のキーフレームがあります`, bone);
				if (clip.loop === 'loop' && keys.length > 1) {
					const a = keys[0].value, z = keys[keys.length - 1].value;
					if (a.some((v, i) => Math.abs(v - z[i]) > 0.01)) r.warn('anim_loop_seam', `ループAnimation "${clip.name}" の ${bone}.${channel} が始点と終点で一致しません`, bone);
				}
			}
		}
	}
}

/** Structural checks on the written .bbmodel JSON (references, UUIDs, JSON validity). */
export function validateBBModel(json, text = null) {
	const r = new Report();
	try {
		const src = text ?? JSON.stringify(json);
		const parsed = JSON.parse(src);
		if (!parsed.meta?.format_version || !parsed.meta?.model_format) r.error('json_meta', 'meta.format_version / model_format がありません');
		findBadNumbers(parsed, 'root', r);
	} catch (err) {
		r.error('json_invalid', `JSONとして不正です: ${err.message}`);
		return r.result();
	}
	const uuids = new Map();
	const addUuid = (u, what) => {
		if (!UUID_RE.test(u)) r.error('uuid_invalid', `UUID形式が不正です: ${what} (${u})`);
		if (uuids.has(u)) r.error('uuid_duplicate', `UUIDが重複しています: ${what} / ${uuids.get(u)}`);
		uuids.set(u, what);
	};
	const elements = new Map();
	for (const el of json.elements || []) {
		addUuid(el.uuid, `element ${el.name}`);
		elements.set(el.uuid, el);
		for (const face of FACE_KEYS) {
			const f = el.faces?.[face];
			if (!f) {
				r.error('face_missing', `面データがありません: ${el.name}.${face}`);
				continue;
			}
			if (f.texture !== null && f.texture !== undefined && !(json.textures || [])[f.texture]) {
				r.error('texture_missing_ref', `存在しないTextureを参照しています: ${el.name}.${face} → #${f.texture}`);
			}
		}
	}
	const groups = new Map();
	for (const g of json.groups || []) {
		addUuid(g.uuid, `group ${g.name}`);
		groups.set(g.uuid, g);
	}
	const seen = new Set();
	const walk = (list) => {
		for (const item of list) {
			if (typeof item === 'string') {
				if (!elements.has(item)) r.error('outliner_ref', `Outlinerが存在しないCubeを参照しています: ${item}`);
				seen.add(item);
			} else {
				if (!item.name && !groups.has(item.uuid)) r.error('outliner_ref', `Outlinerが存在しないGroupを参照しています: ${item.uuid}`);
				if (item.name) addUuid(item.uuid, `group ${item.name}`);
				seen.add(item.uuid);
				walk(item.children || []);
			}
		}
	};
	walk(json.outliner || []);
	for (const u of elements.keys()) if (!seen.has(u)) r.error('outliner_missing', `Outlinerに含まれていないCubeがあります: ${elements.get(u).name}`);

	const used = new Set();
	for (const el of json.elements || []) for (const face of FACE_KEYS) if (el.faces?.[face]?.texture != null) used.add(el.faces[face].texture);
	(json.textures || []).forEach((t, i) => {
		addUuid(t.uuid, `texture ${t.name}`);
		if (!String(t.source || '').startsWith('data:image/png;base64,')) r.error('texture_source', `Texture "${t.name}" の画像データがありません`);
		if (!used.has(i)) {
			if (t.render_mode === 'layered') r.info('texture_layer', `${t.name} は発光レイヤーです（Blockbenchでは Layered 表示で重ねて表示、面には割り当てません）`);
			else r.warn('texture_unused', `Textureがどの面からも参照されていません: ${t.name}`);
		}
	});

	const groupNameByUuid = new Map([...groups.values()].map((g) => [g.uuid, g.name]));
	const collectLegacy = (list) => {
		for (const item of list) if (typeof item === 'object' && item.name) {
			groupNameByUuid.set(item.uuid, item.name);
			collectLegacy(item.children || []);
		}
	};
	collectLegacy(json.outliner || []);
	for (const anim of json.animations || []) {
		addUuid(anim.uuid, `animation ${anim.name}`);
		for (const [key, animator] of Object.entries(anim.animators || {})) {
			if (!groupNameByUuid.has(key)) r.error('anim_missing_bone', `Animation "${anim.name}" が存在しないBone "${animator.name}" を参照しています`);
			else if (groupNameByUuid.get(key) !== animator.name) r.warn('anim_bone_name', `Animation "${anim.name}" のAnimator名 "${animator.name}" がBone名と一致しません`);
			for (const kf of animator.keyframes || []) {
				addUuid(kf.uuid, `keyframe ${anim.name}`);
				for (const dp of kf.data_points || []) {
					for (const axis of ['x', 'y', 'z']) {
						if (!Number.isFinite(Number(dp[axis]))) r.error('anim_nan', `Animation "${anim.name}" のキーフレーム値が不正です: ${animator.name}.${kf.channel}.${axis}`);
					}
				}
			}
		}
	}
	return r.result();
}

function findBadNumbers(value, path, r, depth = 0) {
	if (depth > 12) return;
	if (typeof value === 'number' && !Number.isFinite(value)) r.error('json_nan', `数値に NaN / Infinity があります: ${path}`);
	else if (Array.isArray(value)) {
		const numeric = value.some((v) => typeof v === 'number');
		value.forEach((v, i) => {
			if (numeric && v === null) r.error('json_nan', `数値配列に null（NaN）があります: ${path}[${i}]`);
			else findBadNumbers(v, `${path}[${i}]`, r, depth + 1);
		});
	} else if (value && typeof value === 'object') {
		for (const [k, v] of Object.entries(value)) {
			if (k === 'source') continue;
			findBadNumbers(v, `${path}.${k}`, r, depth + 1);
		}
	}
}

/** Merges two reports (model + bbmodel). */
export function mergeReports(a, b) {
	const items = [...(a?.items || []), ...(b?.items || [])];
	const count = (lvl) => items.filter((i) => i.level === lvl).length;
	return { ok: count('error') === 0, errors: count('error'), warnings: count('warning'), infos: count('info'), items };
}
