// Real-time 3D preview: orbit / zoom / pan, wireframe, bone and pivot helpers,
// texture and glow toggles, hover picking and animation playback.

import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { PreviewAnimator } from './animator.js';
import { buildModelObject, imageToTexture } from './model_mesh.js';

const BG = 0x0b1118;
const BG_DARK = 0x03060a;

export class Viewer {
	constructor(container) {
		this.container = container;
		this.options = { texture: true, glow: true, wireframe: false, bones: false, pivots: false, grid: true, dark: false, lighting: 'blockbench' };
		this.animator = new PreviewAnimator();
		this.listeners = { hover: [], select: [] };

		this.renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
		this.renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1));
		this.renderer.outputColorSpace = THREE.SRGBColorSpace;
		container.appendChild(this.renderer.domElement);

		this.scene = new THREE.Scene();
		this.scene.background = new THREE.Color(BG);
		this.camera = new THREE.PerspectiveCamera(40, 1, 0.5, 4000);
		this.camera.position.set(-38, 26, -38);
		this.controls = new OrbitControls(this.camera, this.renderer.domElement);
		this.controls.enableDamping = true;
		this.controls.dampingFactor = 0.12;
		this.controls.screenSpacePanning = true;

		this.hemi = new THREE.HemisphereLight(0xdfe9ff, 0x2a3140, 1.6);
		this.sun = new THREE.DirectionalLight(0xffffff, 1.5);
		this.sun.position.set(-30, 60, -40);
		this.fill = new THREE.DirectionalLight(0x9fb8ff, 0.5);
		this.fill.position.set(40, 20, 50);
		this.scene.add(this.hemi, this.sun, this.fill);

		this.grid = makeGrid();
		this.scene.add(this.grid);
		this.helpers = new THREE.Group();
		this.scene.add(this.helpers);

		this.model = null;
		this.built = null;
		this.textures = [];
		this.hovered = null;
		this.selectedBone = null;
		this.raycaster = new THREE.Raycaster();
		this.pointer = new THREE.Vector2();
		this.tooltip = document.createElement('div');
		this.tooltip.className = 'viewport-tooltip';
		container.appendChild(this.tooltip);

		this.#bindEvents();
		new ResizeObserver(() => this.#resize()).observe(container);
		this.#resize();
		this.timer = new THREE.Timer();
		this.timer.connect(document);
		this.renderer.setAnimationLoop((t) => this.#frame(t));
	}

	on(event, cb) {
		this.listeners[event]?.push(cb);
	}

	#emit(event, payload) {
		for (const cb of this.listeners[event] || []) cb(payload);
	}

	#resize() {
		const w = this.container.clientWidth || 1, h = this.container.clientHeight || 1;
		this.renderer.setSize(w, h, false);
		this.camera.aspect = w / h;
		this.camera.updateProjectionMatrix();
	}

	#bindEvents() {
		const el = this.renderer.domElement;
		let down = null;
		el.addEventListener('pointermove', (e) => {
			const r = el.getBoundingClientRect();
			this.pointer.set(((e.clientX - r.left) / r.width) * 2 - 1, -((e.clientY - r.top) / r.height) * 2 + 1);
			this.pointerPx = [e.clientX - r.left, e.clientY - r.top];
			this.#pick();
		});
		el.addEventListener('pointerleave', () => {
			this.#setHover(null);
		});
		el.addEventListener('pointerdown', (e) => (down = [e.clientX, e.clientY]));
		el.addEventListener('pointerup', (e) => {
			if (down && Math.hypot(e.clientX - down[0], e.clientY - down[1]) < 4) {
				const bone = this.hovered?.userData.bone?.name || null;
				this.selectBone(bone);
				this.#emit('select', bone);
			}
			down = null;
		});
	}

	#pick() {
		if (!this.built) return;
		this.raycaster.setFromCamera(this.pointer, this.camera);
		const hits = this.raycaster.intersectObjects(this.built.meshes, false);
		const hit = hits.find((h) => h.object.visible);
		this.#setHover(hit ? hit.object : null);
	}

	#setHover(mesh) {
		if (this.hovered === mesh) {
			if (mesh) this.#placeTooltip();
			return;
		}
		if (this.hovered) this.#styleWire(this.hovered);
		this.hovered = mesh;
		if (mesh) {
			const wire = mesh.getObjectByName('wire');
			wire.visible = true;
			wire.material.color.set(0xffd27a);
			wire.material.opacity = 1;
			const { cube, bone } = mesh.userData;
			this.tooltip.textContent = `${cube.name}  ·  ${bone.name} (${bone.role})  ·  ${cube.material}`;
			this.tooltip.style.display = 'block';
			this.#placeTooltip();
		} else {
			this.tooltip.style.display = 'none';
		}
		this.#emit('hover', mesh ? mesh.userData : null);
	}

	#placeTooltip() {
		if (!this.pointerPx) return;
		this.tooltip.style.left = `${this.pointerPx[0] + 14}px`;
		this.tooltip.style.top = `${this.pointerPx[1] + 12}px`;
	}

	#styleWire(mesh) {
		const wire = mesh.getObjectByName('wire');
		const selected = !!this.selectedBone && mesh.userData.bone.name === this.selectedBone;
		wire.visible = !!this.options.wireframe || selected;
		wire.material.color.set(selected ? 0x3fd0c9 : 0x9fe8ff);
		wire.material.opacity = selected ? 1 : 0.55;
	}

	selectBone(name) {
		this.selectedBone = name;
		if (this.built) for (const m of this.built.meshes) this.#styleWire(m);
	}

	setModel(model, { frame = false } = {}) {
		const firstModel = !this.model;
		this.model = model;
		if (this.built) {
			this.scene.remove(this.built.root);
			this.built.dispose();
		}
		for (const t of this.textures) t.dispose();
		const baseTexture = imageToTexture(model.textures.base);
		const glowTexture = model.textures.glow ? imageToTexture(model.textures.glow) : null;
		this.textures = [baseTexture, glowTexture].filter(Boolean);
		this.built = buildModelObject(model, { baseTexture, glowTexture });
		this.scene.add(this.built.root);
		this.#buildHelpers();
		this.applyOptions();
		const clipName = this.animator.clip?.name;
		this.animator.setClip(model.animations.find((a) => a.name === clipName) || null);
		this.hovered = null;
		if (frame || firstModel) this.frame();
	}

	#buildHelpers() {
		this.helpers.clear();
		const bones = this.model.geometry.bones.filter((b) => b.parent);
		const lineGeom = new THREE.BufferGeometry();
		lineGeom.setAttribute('position', new THREE.BufferAttribute(new Float32Array(bones.length * 6), 3));
		this.boneLines = new THREE.LineSegments(lineGeom, new THREE.LineBasicMaterial({ color: 0xffb454, depthTest: false, transparent: true }));
		this.boneLines.renderOrder = 10;
		this.boneLines.userData.pairs = bones.map((b) => [b.parent, b.name]);
		this.helpers.add(this.boneLines);
		this.pivotMarkers = new THREE.Group();
		const markerGeom = new THREE.OctahedronGeometry(0.35);
		for (const [name, obj] of this.built.bones) {
			const m = new THREE.Mesh(markerGeom, new THREE.MeshBasicMaterial({ color: name === this.model.geometry.root ? 0xff6b6b : 0xffd27a, depthTest: false, transparent: true }));
			m.renderOrder = 11;
			m.name = `pivot:${name}`;
			const axes = new THREE.AxesHelper(1.6);
			axes.material.depthTest = false;
			axes.renderOrder = 11;
			m.add(axes);
			m.userData.bone = obj;
			this.pivotMarkers.add(m);
		}
		this.helpers.add(this.pivotMarkers);
	}

	#updateHelpers() {
		if (!this.built) return;
		const v = new THREE.Vector3();
		if (this.boneLines.visible) {
			const pos = this.boneLines.geometry.attributes.position;
			this.boneLines.userData.pairs.forEach(([a, b], i) => {
				this.built.bones.get(a).getWorldPosition(v);
				pos.setXYZ(i * 2, v.x, v.y, v.z);
				this.built.bones.get(b).getWorldPosition(v);
				pos.setXYZ(i * 2 + 1, v.x, v.y, v.z);
			});
			pos.needsUpdate = true;
			this.boneLines.geometry.computeBoundingSphere();
		}
		if (this.pivotMarkers.visible) {
			for (const m of this.pivotMarkers.children) {
				m.userData.bone.getWorldPosition(m.position);
				m.userData.bone.getWorldQuaternion(m.quaternion);
			}
		}
	}

	setOption(key, value) {
		this.options[key] = value;
		this.applyOptions();
	}

	applyOptions() {
		const o = this.options;
		this.grid.visible = o.grid;
		this.scene.background.set(o.dark ? BG_DARK : BG);
		this.hemi.intensity = o.dark ? 0.12 : 1.6;
		this.sun.intensity = o.dark ? 0.1 : 1.5;
		this.fill.intensity = o.dark ? 0.08 : 0.5;
		if (!this.built) return;
		const { opaque, translucent, flat } = this.built.materials;
		for (const mat of [opaque, translucent, ...flat()]) {
			mat.uniforms.useGlow.value = !!(o.glow && o.texture && mat.uniforms.glowMap.value);
			mat.uniforms.glowStrength.value = o.dark ? 1.25 : 1;
			mat.uniforms.lightScale.value = o.dark ? 0.22 : 1;
			mat.uniforms.lightMode.value = o.lighting === 'minecraft' ? 1 : 0;
		}
		for (const m of this.built.meshes) {
			m.material = o.texture ? m.userData.texturedMaterial : m.userData.flatMaterial;
			this.#styleWire(m);
		}
		this.boneLines.visible = o.bones;
		this.pivotMarkers.visible = o.pivots || o.bones;
		for (const m of this.pivotMarkers.children) m.children[0].visible = o.pivots;
	}

	frame() {
		if (!this.built) return;
		this.animator.apply(this.built.bones);
		const box = new THREE.Box3().setFromObject(this.built.root);
		const size = box.getSize(new THREE.Vector3());
		const center = box.getCenter(new THREE.Vector3());
		const radius = Math.max(size.x, size.y, size.z, 8);
		const dir = this.camera.position.clone().sub(this.controls.target).normalize();
		if (!Number.isFinite(dir.x) || dir.lengthSq() < 0.5) dir.set(-0.6, 0.45, -0.66).normalize();
		this.controls.target.copy(center);
		this.camera.position.copy(center).add(dir.multiplyScalar(radius * 1.45));
		this.camera.near = radius / 50;
		this.camera.far = radius * 60;
		this.camera.updateProjectionMatrix();
		this.controls.update();
	}

	setView(name) {
		if (!this.built) return;
		const dirs = {
			front: [0, 0.05, -1],
			back: [0, 0.05, 1],
			right: [1, 0.05, 0],
			left: [-1, 0.05, 0],
			top: [0, 1, 0.0001],
			iso: [-0.6, 0.45, -0.66],
		};
		const d = new THREE.Vector3(...(dirs[name] || dirs.iso)).normalize();
		const dist = this.camera.position.distanceTo(this.controls.target);
		this.camera.position.copy(this.controls.target).add(d.multiplyScalar(dist));
		this.controls.update();
	}

	screenshot() {
		this.renderer.render(this.scene, this.camera);
		return this.renderer.domElement.toDataURL('image/png');
	}

	#frame(timestamp) {
		this.timer.update(timestamp);
		const dt = Math.min(0.1, this.timer.getDelta());
		this.animator.update(dt);
		if (this.built) this.animator.apply(this.built.bones);
		this.#updateHelpers();
		this.controls.update();
		this.renderer.render(this.scene, this.camera);
	}
}

function makeGrid() {
	const group = new THREE.Group();
	const size = 64;
	const minor = new THREE.GridHelper(size, size, 0x16202b, 0x16202b);
	minor.material.transparent = true;
	minor.material.opacity = 0.5;
	const major = new THREE.GridHelper(size, size / 16, 0x2c3a4a, 0x2c3a4a);
	major.position.y = 0.01;
	const axisX = new THREE.Line(new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(-size / 2, 0.02, 0), new THREE.Vector3(size / 2, 0.02, 0)]), new THREE.LineBasicMaterial({ color: 0x7a3a3a }));
	const axisZ = new THREE.Line(new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(0, 0.02, -size / 2), new THREE.Vector3(0, 0.02, size / 2)]), new THREE.LineBasicMaterial({ color: 0x3a4f7a }));
	// North marker: the creature faces -Z (north)
	const north = new THREE.Mesh(new THREE.ConeGeometry(0.8, 2, 3), new THREE.MeshBasicMaterial({ color: 0x3a4f7a }));
	north.rotation.x = -Math.PI / 2;
	north.position.set(0, 0.05, -size / 2 - 1);
	group.add(minor, major, axisX, axisZ, north);
	return group;
}
