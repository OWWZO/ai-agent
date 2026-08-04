import { FC, memo, useEffect, useMemo, useRef } from "react";
import * as THREE from "three";

type GeometryKind =
  | "box"
  | "sphere"
  | "icosahedron"
  | "octahedron"
  | "dodecahedron"
  | "tetrahedron"
  | "torusKnot";

type SceneOptions = {
  title: string;
  height: number;
  background: string;
  geometry: GeometryKind;
  color: number;
  accentColor: number;
  cameraZ: number;
  autoRotate: boolean;
  wireframe: boolean;
  particles: number;
  orbiters: number;
  detail: number;
  rotateSpeed: number;
  dpr: number;
};

type Props = {
  title?: string;
  height?: number | string;
  background?: string;
  geometry?: string;
  shape?: string;
  color?: string | number;
  accentColor?: string | number;
  cameraZ?: number | string;
  autoRotate?: boolean;
  wireframe?: boolean;
  particles?: number | string;
  orbiters?: number | string;
  detail?: number | string;
  rotateSpeed?: number | string;
  quality?: string;
  dpr?: number | string;
  sceneScript?: string;
};

const GEOMETRY_HINTS: Array<[GeometryKind, RegExp]> = [
  ["icosahedron", /IcosahedronGeometry|icosahedron|多面/i],
  ["dodecahedron", /DodecahedronGeometry|dodecahedron/i],
  ["octahedron", /OctahedronGeometry|octahedron/i],
  ["tetrahedron", /TetrahedronGeometry|tetrahedron/i],
  ["torusKnot", /TorusKnotGeometry|torus\s*knot/i],
  ["sphere", /SphereGeometry|sphere/i],
  ["box", /BoxGeometry|cube|box/i],
];

function parseHeight(value: unknown): number {
  if (typeof value === "number" && Number.isFinite(value) && value > 0) {
    return Math.min(2000, Math.max(160, Math.round(value)));
  }
  if (typeof value === "string") {
    const n = parseInt(value.replace(/px$/i, "").trim(), 10);
    if (Number.isFinite(n) && n > 0) return Math.min(2000, Math.max(160, n));
  }
  return 360;
}

function parseCameraZ(value: unknown): number {
  if (typeof value === "number" && Number.isFinite(value) && value > 0) {
    return Math.min(100, Math.max(1, value));
  }
  return 5;
}

function parseNumber(value: unknown, fallback: number, min: number, max: number): number {
  const n = typeof value === "number" ? value : typeof value === "string" ? Number(value) : NaN;
  if (!Number.isFinite(n)) return fallback;
  return Math.min(max, Math.max(min, n));
}

function parseBackground(value: unknown): string {
  if (typeof value === "string" && value.trim()) {
    const v = value.trim();
    if (/^#[0-9a-fA-F]{3,8}$/.test(v) || /^rgb(a)?\(/i.test(v) || /^hsl(a)?\(/i.test(v)) {
      return v;
    }
  }
  return "#0f172a";
}

function parseColor(value: unknown, fallback: number): number {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value !== "string") return fallback;
  const v = value.trim();
  if (/^0x[0-9a-f]+$/i.test(v)) return Number.parseInt(v.slice(2), 16);
  if (/^#[0-9a-f]{6}$/i.test(v)) return Number.parseInt(v.slice(1), 16);
  return fallback;
}

function parseGeometry(value: unknown, sceneScript: string): GeometryKind {
  if (typeof value === "string") {
    const normalized = value.trim().toLowerCase();
    if (normalized === "torus-knot" || normalized === "torusknot") return "torusKnot";
    if (
      normalized === "box" ||
      normalized === "sphere" ||
      normalized === "icosahedron" ||
      normalized === "octahedron" ||
      normalized === "dodecahedron" ||
      normalized === "tetrahedron"
    ) {
      return normalized;
    }
  }
  return GEOMETRY_HINTS.find(([, re]) => re.test(sceneScript))?.[0] ?? "icosahedron";
}

function inferSceneOptions(props: Props): SceneOptions {
  // GenUI 属性来自模型输出，先统一做范围限制和默认推断，再交给 Three.js；这样异常的
  // 高度、粒子数或颜色不会把渲染循环变成无限资源消耗。
  const sceneScript = typeof props.sceneScript === "string" ? props.sceneScript : "";
  const quality = String(props.quality || "auto").toLowerCase();
  const particleFallback = /particle|PointsMaterial/i.test(sceneScript) ? 360 : 140;
  const orbiterFallback = /orbit|OctahedronGeometry/i.test(sceneScript) ? 8 : 0;
  const dprFallback = quality === "high" ? 2 : quality === "low" ? 1 : 1.5;

  return {
    title:
      typeof props.title === "string" && props.title.trim() ? props.title.trim() : "Three.js scene",
    height: parseHeight(props.height),
    background: parseBackground(props.background),
    geometry: parseGeometry(props.geometry ?? props.shape, sceneScript),
    color: parseColor(props.color, 0x38bdf8),
    accentColor: parseColor(props.accentColor, 0xfbbf24),
    cameraZ: parseCameraZ(props.cameraZ),
    autoRotate: props.autoRotate !== false,
    wireframe: props.wireframe !== false,
    particles: Math.round(parseNumber(props.particles, particleFallback, 0, 1200)),
    orbiters: Math.round(parseNumber(props.orbiters, orbiterFallback, 0, 24)),
    detail: Math.round(parseNumber(props.detail, 0, 0, 4)),
    rotateSpeed: parseNumber(props.rotateSpeed, 0.65, 0, 3),
    dpr: parseNumber(props.dpr, dprFallback, 1, 2),
  };
}

function createGeometry(kind: GeometryKind, detail: number): THREE.BufferGeometry {
  switch (kind) {
    case "box":
      return new THREE.BoxGeometry(1.8, 1.8, 1.8);
    case "sphere":
      return new THREE.SphereGeometry(1.45, 48, 32);
    case "octahedron":
      return new THREE.OctahedronGeometry(1.65, detail);
    case "dodecahedron":
      return new THREE.DodecahedronGeometry(1.55, detail);
    case "tetrahedron":
      return new THREE.TetrahedronGeometry(1.85, detail);
    case "torusKnot":
      return new THREE.TorusKnotGeometry(1.05, 0.32, 160, 18);
    case "icosahedron":
    default:
      return new THREE.IcosahedronGeometry(1.65, detail);
  }
}

function disposeObject(obj: THREE.Object3D): void {
  // Three.js 不会因为从 scene 移除对象就自动释放 GPU 资源，effect 重建或组件卸载时必须
  // 显式 dispose geometry/material，避免聊天页面多次渲染后显存持续增长。
  obj.traverse((child) => {
    const mesh = child as THREE.Mesh | THREE.LineSegments | THREE.Points;
    const maybeGeometry = mesh.geometry as THREE.BufferGeometry | undefined;
    if (maybeGeometry?.dispose) maybeGeometry.dispose();
    const material = mesh.material as THREE.Material | THREE.Material[] | undefined;
    if (Array.isArray(material)) {
      material.forEach((m) => m.dispose());
    } else if (material?.dispose) {
      material.dispose();
    }
  });
}

const GenUiThreeJsFrame: FC<Props> = memo((props) => {
  const hostRef = useRef<HTMLDivElement | null>(null);
  const options = useMemo(() => inferSceneOptions(props), [props]);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;

    const scene = new THREE.Scene();
    // 每次 options 变化都建立完整的独立 scene；清理函数会销毁旧 renderer，避免旧动画
    // 循环继续引用已卸载的 DOM 节点。
    scene.background = new THREE.Color(options.background);
    scene.fog = new THREE.Fog(options.background, options.cameraZ + 2, options.cameraZ + 11);

    const camera = new THREE.PerspectiveCamera(55, 1, 0.1, 2000);
    camera.position.set(0, 0.15, options.cameraZ);

    const renderer = new THREE.WebGLRenderer({
      antialias: options.dpr > 1,
      alpha: false,
      powerPreference: "high-performance",
    });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, options.dpr));
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    host.appendChild(renderer.domElement);

    const group = new THREE.Group();
    scene.add(group);

    const geometry = createGeometry(options.geometry, options.detail);
    const material = new THREE.MeshPhysicalMaterial({
      color: options.color,
      metalness: 0.35,
      roughness: 0.24,
      clearcoat: 0.5,
      transparent: true,
      opacity: 0.9,
    });
    const mesh = new THREE.Mesh(geometry, material);
    group.add(mesh);

    if (options.wireframe) {
      const edges = new THREE.EdgesGeometry(geometry);
      const edgeMaterial = new THREE.LineBasicMaterial({
        color: 0xffffff,
        transparent: true,
        opacity: 0.38,
      });
      group.add(new THREE.LineSegments(edges, edgeMaterial));
    }

    const core = new THREE.Mesh(
      new THREE.IcosahedronGeometry(0.5, 0),
      new THREE.MeshBasicMaterial({ color: options.accentColor })
    );
    group.add(core);

    const orbiters: THREE.Mesh[] = [];
    for (let i = 0; i < options.orbiters; i += 1) {
      const orbiter = new THREE.Mesh(
        new THREE.OctahedronGeometry(0.11 + (i % 3) * 0.03, 0),
        new THREE.MeshStandardMaterial({
          color: i % 2 === 0 ? options.accentColor : options.color,
          emissive: i % 2 === 0 ? options.accentColor : options.color,
          emissiveIntensity: 0.25,
          roughness: 0.45,
        })
      );
      orbiter.userData.angle = (i / Math.max(options.orbiters, 1)) * Math.PI * 2;
      orbiter.userData.radius = 2.25 + (i % 4) * 0.13;
      orbiter.userData.speed = 0.3 + (i % 5) * 0.04;
      scene.add(orbiter);
      orbiters.push(orbiter);
    }

    let particles: THREE.Points | null = null;
    if (options.particles > 0) {
      const particleGeometry = new THREE.BufferGeometry();
      const positions = new Float32Array(options.particles * 3);
      for (let i = 0; i < positions.length; i += 1) {
        positions[i] = (Math.random() - 0.5) * 12;
      }
      particleGeometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
      particles = new THREE.Points(
        particleGeometry,
        new THREE.PointsMaterial({
          color: 0x94a3b8,
          size: 0.025,
          transparent: true,
          opacity: 0.55,
        })
      );
      scene.add(particles);
    }

    scene.add(new THREE.AmbientLight(0xffffff, 0.36));
    const key = new THREE.DirectionalLight(0xffffff, 1.25);
    key.position.set(4, 5, 6);
    scene.add(key);
    const rim = new THREE.DirectionalLight(options.color, 0.75);
    rim.position.set(-5, -2, -4);
    scene.add(rim);
    const point = new THREE.PointLight(options.accentColor, 0.8, 10);
    point.position.set(0, 2, 2);
    scene.add(point);

    let visible = true;
    let disposed = false;
    const clock = new THREE.Clock();

    const resize = () => {
      const rect = host.getBoundingClientRect();
      const width = Math.max(Math.floor(rect.width), 1);
      const height = Math.max(Math.floor(rect.height), 1);
      camera.aspect = width / height;
      camera.updateProjectionMatrix();
      renderer.setSize(width, height, false);
    };

    resize();
    const resizeObserver = new ResizeObserver(resize);
    resizeObserver.observe(host);
    const intersectionObserver =
      typeof IntersectionObserver === "undefined"
        ? null
        : new IntersectionObserver(([entry]) => {
            visible = Boolean(entry?.isIntersecting);
          });
    intersectionObserver?.observe(host);

    const tick = () => {
      if (disposed) return;
      // requestAnimationFrame 只在组件仍存活且画布可见时渲染；不可见时保留循环调度，
      // 但跳过 GPU 绘制，重新进入视口后可从同一个 Clock 继续动画。
      requestAnimationFrame(tick);
      if (!visible) return;
      const t = clock.getElapsedTime();
      if (options.autoRotate) {
        group.rotation.x = t * 0.18 * options.rotateSpeed;
        group.rotation.y = t * 0.36 * options.rotateSpeed;
        core.rotation.y = -t * 0.55 * options.rotateSpeed;
      }
      particles?.rotation.set(t * 0.015, t * 0.04, 0);
      orbiters.forEach((orbiter, i) => {
        const angle = (orbiter.userData.angle as number) + t * (orbiter.userData.speed as number);
        const radius = orbiter.userData.radius as number;
        orbiter.position.set(
          Math.cos(angle) * radius,
          Math.sin(angle * 1.7 + i) * 0.72,
          Math.sin(angle) * radius
        );
        orbiter.rotation.x += 0.018;
        orbiter.rotation.y += 0.024;
      });
      renderer.render(scene, camera);
    };
    tick();

    return () => {
      disposed = true;
      // 清理顺序先阻止下一帧，再断开观察器、移除 canvas、释放场景资源和 renderer，
      // 覆盖 React effect 重跑与组件卸载两种生命周期。
      resizeObserver.disconnect();
      intersectionObserver?.disconnect();
      if (renderer.domElement.parentNode === host) {
        host.removeChild(renderer.domElement);
      }
      disposeObject(scene);
      renderer.dispose();
    };
  }, [options]);

  return (
    <figure className="overflow-hidden rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)]">
      <div
        ref={hostRef}
        role="img"
        aria-label={options.title}
        className="w-full"
        style={{ height: options.height, background: options.background }}
      />
    </figure>
  );
});

GenUiThreeJsFrame.displayName = "GenUiThreeJsFrame";

export default GenUiThreeJsFrame;
