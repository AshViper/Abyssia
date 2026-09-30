// Builds a three.js object tree from generated model data, placing bones and cubes exactly
// like Blockbench does (group origin relative to parent origin, ZYX Euler, box UV mapping).

import * as THREE from 'three';

const DEG = Math.PI / 180;
// Blockbench face order == three.js BoxGeometry group order
const FACE_ORDER = ['east', 'west', 'up', 'down', 'south', 'north'];

export const ROLE_COLORS = {
	root: 0x6f7c8c,
	body: 0x4f7ea8,
	head: 0x5a9bd5,
	snout: 0x5a9bd5,
	jaw: 0xd08a4a,
	teeth: 0xe8e2cf,
	eye: 0xb8c94a,
	lure: 0x3fd0c9,
	esca: 0x7cf2e4,
	fin: 0x6f68c8,
	pelvic: 0x6f68c8,
	dorsal: 0x6f68c8,
	anal: 0x6f68c8,
	tail: 0x4b9a7a,
	tail_fin: 0x6fb89a,
	leg: 0xc07a58,
	arm: 0xc0587a,
	tentacle: 0xd0708f,
	antenna: 0xb0a060,
	claw: 0xe09a6a,
	segment: 0x4f7ea8,
	plume: 0xd05050,
	tube: 0xd8d0c0,
};

/** Draws an RGBAImage into a canvas texture (nearest filtering, sRGB). */
export function imageToTexture(image) {
	const canvas = document.createElement('canvas');
	canvas.width = image.width;
	canvas.height = image.height;
	const ctx = canvas.getContext('2d');
	ctx.putImageData(new ImageData(new Uint8ClampedArray(image.data), image.width, image.height), 0, 0);
	const tex = new THREE.CanvasTexture(canvas);
	tex.magFilter = THREE.NearestFilter;
	tex.minFilter = THREE.NearestFilter;
	tex.generateMipmaps = false;
	// Blockbench and Minecraft shade the raw sRGB texel values; sample without conversion.
	tex.colorSpace = THREE.NoColorSpace;
	return tex;
}

function cubeGeometry(cube, cubeUV, uvW, uvH) {
	const size = [0, 1, 2].map((i) => cube.to[i] - cube.from[i]);
	const geom = new THREE.BoxGeometry(size[0] || 0.001, size[1] || 0.001, size[2] || 0.001);
	const center = [0, 1, 2].map((i) => (cube.from[i] + cube.to[i]) / 2 - cube.origin[i]);
	geom.translate(center[0], center[1], center[2]);
	const uvAttr = geom.attributes.uv;
	const box = cubeUV.offset !== undefined && cubeUV.faces;
	FACE_ORDER.forEach((face, index) => {
		let uv = cubeUV.faces[face].slice();
		// Blockbench: inset box UV by 1/64 px against texture bleeding
		if (box) {
			for (let si = 0; si < 2; si++) {
				let margin = 1 / 64;
				if (uv[si] > uv[si + 2]) margin = -margin;
				uv[si] += margin;
				uv[si + 2] -= margin;
			}
		}
		const arr = [
			[uv[0] / uvW, 1 - uv[1] / uvH],
			[uv[2] / uvW, 1 - uv[1] / uvH],
			[uv[0] / uvW, 1 - uv[3] / uvH],
			[uv[2] / uvW, 1 - uv[3] / uvH],
		];
		for (let i = 0; i < 4; i++) uvAttr.setXY(index * 4 + i, arr[i][0], arr[i][1]);
	});
	uvAttr.needsUpdate = true;
	// Hide faces without texture (per-face UV zero-area faces)
	geom.clearGroups();
	FACE_ORDER.forEach((face, index) => {
		if (cubeUV.enabled[face]) geom.addGroup(index * 6, 6, 0);
	});
	return geom;
}

const TRANSLUCENT = new Set(['dome', 'flesh', 'membrane']);

const VERTEX = `
varying vec2 vUv;
varying vec3 vNormal;
void main() {
	vUv = uv;
	vNormal = normalize(mat3(modelMatrix) * normal);
	gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}`;

// lightMode 0 = Blockbench viewport shading, 1 = Minecraft entity lighting (two fixed lights)
const FRAGMENT = `
uniform sampler2D map;
uniform sampler2D glowMap;
uniform bool useMap;
uniform bool useGlow;
uniform vec3 flatColor;
uniform float glowStrength;
uniform float lightScale;
uniform int lightMode;
uniform float alphaTest;
varying vec2 vUv;
varying vec3 vNormal;
void main() {
	vec4 color = useMap ? texture2D(map, vUv) : vec4(flatColor, 1.0);
	if (color.a < alphaTest) discard;
	vec3 N = normalize(vNormal);
	float light;
	if (lightMode == 1) {
		vec3 l0 = normalize(vec3(0.2, 1.0, -0.7));
		vec3 l1 = normalize(vec3(-0.2, 1.0, 0.7));
		light = min(1.0, (max(0.0, dot(l0, N)) + max(0.0, dot(l1, N))) * 0.6 + 0.4);
	} else {
		float yLight = (1.0 + N.y) * 0.5;
		light = yLight * 0.5 + N.x * N.x * -0.15 + N.z * N.z * 0.05 + 0.5;
	}
	vec3 rgb = color.rgb * light * lightScale;
	if (useGlow) {
		vec4 g = texture2D(glowMap, vUv);
		rgb += g.rgb * g.a * glowStrength;
	}
	gl_FragColor = vec4(min(rgb, vec3(1.0)), color.a);
}`;

/** Shader material that reproduces Blockbench / Minecraft entity shading (+ additive glow layer). */
export function createEntityMaterial({ map = null, glowMap = null, color = 0xffffff, translucent = false } = {}) {
	return new THREE.ShaderMaterial({
		uniforms: {
			map: { value: map },
			glowMap: { value: glowMap },
			useMap: { value: !!map },
			useGlow: { value: !!glowMap },
			flatColor: { value: new THREE.Color(color) },
			glowStrength: { value: 1 },
			lightScale: { value: 1 },
			lightMode: { value: 0 },
			alphaTest: { value: translucent ? 0.02 : 0.1 },
		},
		vertexShader: VERTEX,
		fragmentShader: FRAGMENT,
		side: THREE.FrontSide,
		transparent: translucent,
		depthWrite: !translucent,
	});
}

/**
 * @returns {{root: THREE.Group, bones: Map<string, THREE.Object3D>, meshes: THREE.Mesh[], dispose: Function}}
 */
export function buildModelObject(model, { baseTexture, glowTexture }) {
	const root = new THREE.Group();
	root.name = 'creature';
	const bones = new Map();
	const byName = new Map(model.geometry.bones.map((b) => [b.name, b]));
	for (const bone of model.geometry.bones) {
		const obj = new THREE.Group();
		obj.name = bone.name;
		obj.rotation.order = 'ZYX';
		const parent = bone.parent ? byName.get(bone.parent) : null;
		const p = parent ? bone.pivot.map((v, i) => v - parent.pivot[i]) : bone.pivot;
		obj.position.set(p[0], p[1], p[2]);
		obj.rotation.set(bone.rotation[0] * DEG, bone.rotation[1] * DEG, bone.rotation[2] * DEG);
		obj.userData = { bone, restPosition: obj.position.clone(), restRotation: obj.rotation.clone() };
		(parent ? bones.get(parent.name) : root).add(obj);
		bones.set(bone.name, obj);
	}

	const opaque = createEntityMaterial({ map: baseTexture, glowMap: glowTexture });
	const translucent = createEntityMaterial({ map: baseTexture, glowMap: glowTexture, translucent: true });
	const flatCache = new Map();
	const flat = (role) => {
		if (!flatCache.has(role)) flatCache.set(role, createEntityMaterial({ color: ROLE_COLORS[role] ?? 0x8090a0 }));
		return flatCache.get(role);
	};

	const meshes = [];
	for (const cube of model.geometry.cubes) {
		const bone = byName.get(cube.bone);
		const cuv = model.uv.cubes[cube.name];
		const geom = cubeGeometry(cube, cuv, model.uv.width, model.uv.height);
		const isTranslucent = TRANSLUCENT.has(cube.material) || (cube.meta?.alpha ?? 255) < 250;
		const mesh = new THREE.Mesh(geom, isTranslucent ? translucent : opaque);
		mesh.name = cube.name;
		mesh.rotation.order = 'ZYX';
		mesh.position.set(cube.origin[0] - bone.pivot[0], cube.origin[1] - bone.pivot[1], cube.origin[2] - bone.pivot[2]);
		mesh.rotation.set(cube.rotation[0] * DEG, cube.rotation[1] * DEG, cube.rotation[2] * DEG);
		mesh.renderOrder = isTranslucent ? 2 : 0;
		mesh.userData = { cube, bone, texturedMaterial: mesh.material, flatMaterial: flat(bone.role) };
		const edges = new THREE.LineSegments(new THREE.EdgesGeometry(geom), new THREE.LineBasicMaterial({ color: 0x9fe8ff, transparent: true, opacity: 0.55, depthTest: true }));
		edges.name = 'wire';
		edges.visible = false;
		mesh.add(edges);
		bones.get(cube.bone).add(mesh);
		meshes.push(mesh);
	}

	return {
		root,
		bones,
		meshes,
		materials: { opaque, translucent, flat: () => [...flatCache.values()] },
		dispose() {
			for (const m of meshes) {
				m.geometry.dispose();
				m.children.forEach((c) => c.geometry?.dispose());
			}
			opaque.dispose();
			translucent.dispose();
			for (const m of flatCache.values()) m.dispose();
		},
	};
}
