import { Box } from "lucide-react";
import { FC, memo, useEffect, useMemo, useRef, useState } from "react";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js";

type Props = {
  src?: string;
  height?: number | string;
  background?: string;
  autoRotate?: boolean;
  rotateSpeed?: number | string;
  wireframe?: boolean;
  caption?: string;
};

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

function parseBackground(value: unknown): string {
  if (typeof value === "string" && value.trim()) {
    const v = value.trim();
    if (/^#[0-9a-fA-F]{3,8}$/.test(v) || /^rgb(a)?\(/i.test(v) || /^hsl(a)?\(/i.test(v)) {
      return v;
    }
  }
  return "#0f172a";
}

function parseNumber(value: unknown, fallback: number, min: number, max: number): number {
  const n = typeof value === "number" ? value : typeof value === "string" ? Number(value) : NaN;
  if (!Number.isFinite(n)) return fallback;
  return Math.min(max, Math.max(min, n));
}

const GenUiModel3D: FC<Props> = memo(
  ({ src, height, background, autoRotate, rotateSpeed, wireframe, caption }) => {
    const hostRef = useRef<HTMLDivElement | null>(null);
    const [status, setStatus] = useState<"idle" | "loading" | "ready" | "error">("idle");
    const modelUrl = typeof src === "string" ? src.trim() : "";

    const options = useMemo(
      () => ({
        height: parseHeight(height),
        background: parseBackground(background),
        autoRotate: autoRotate !== false,
        rotateSpeed: parseNumber(rotateSpeed, 1, 0, 3),
        wireframe: Boolean(wireframe),
        caption: typeof caption === "string" ? caption : "",
      }),
      [height, background, autoRotate, rotateSpeed, wireframe, caption]
    );

    useEffect(() => {
      const host = hostRef.current;
      if (!host || !modelUrl) return;

      // 每个 src/options 组合拥有独立场景；清理阶段必须释放观察器、材质、几何体和 renderer。
      setStatus("loading");
      let disposed = false;

      const scene = new THREE.Scene();
      scene.background = new THREE.Color(options.background);

      const camera = new THREE.PerspectiveCamera(50, 1, 0.01, 5000);
      camera.position.set(0, 0.6, 3);

      const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false });
      renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
      renderer.outputColorSpace = THREE.SRGBColorSpace;
      host.appendChild(renderer.domElement);

      const controls = new OrbitControls(camera, renderer.domElement);
      controls.enableDamping = true;
      controls.dampingFactor = 0.08;
      controls.autoRotate = options.autoRotate;
      controls.autoRotateSpeed = options.rotateSpeed * 2;

      scene.add(new THREE.AmbientLight(0xffffff, 0.7));
      const key = new THREE.DirectionalLight(0xffffff, 1.6);
      key.position.set(4, 6, 5);
      scene.add(key);
      const rim = new THREE.DirectionalLight(0x88aaff, 0.6);
      rim.position.set(-5, -2, -4);
      scene.add(rim);

      let modelRoot: THREE.Object3D | null = null;

      const loader = new GLTFLoader();
      loader.load(
        modelUrl,
        (gltf) => {
          if (disposed) return;
          // 模型加载后按包围盒居中并重算相机距离，保证不同尺寸的 glb 都能完整入镜。
          modelRoot = gltf.scene;
          if (options.wireframe) {
            modelRoot.traverse((child) => {
              const mesh = child as THREE.Mesh;
              const mat = mesh.material as THREE.Material | THREE.Material[] | undefined;
              const apply = (m: THREE.Material) => {
                (m as THREE.MeshStandardMaterial).wireframe = true;
              };
              if (Array.isArray(mat)) mat.forEach(apply);
              else if (mat) apply(mat);
            });
          }
          const box = new THREE.Box3().setFromObject(modelRoot);
          const size = box.getSize(new THREE.Vector3());
          const center = box.getCenter(new THREE.Vector3());
          modelRoot.position.sub(center);
          const maxDim = Math.max(size.x, size.y, size.z) || 1;
          const fitDist = maxDim / 2 / Math.tan((camera.fov * Math.PI) / 360);
          camera.position.set(0, maxDim * 0.15, fitDist * 1.6);
          camera.near = maxDim / 100;
          camera.far = maxDim * 100;
          camera.updateProjectionMatrix();
          controls.target.set(0, 0, 0);
          controls.update();
          scene.add(modelRoot);
          setStatus("ready");
        },
        undefined,
        () => {
          if (!disposed) setStatus("error");
        }
      );

      const resize = () => {
        // 使用宿主容器尺寸而非 window 尺寸，兼容侧边栏展开和响应式布局变化。
        const rect = host.getBoundingClientRect();
        const width = Math.max(Math.floor(rect.width), 1);
        const h = Math.max(Math.floor(rect.height), 1);
        camera.aspect = width / h;
        camera.updateProjectionMatrix();
        renderer.setSize(width, h, false);
      };
      resize();
      const resizeObserver = new ResizeObserver(resize);
      resizeObserver.observe(host);

      const tick = () => {
        if (disposed) return;
        requestAnimationFrame(tick);
        controls.update();
        renderer.render(scene, camera);
      };
      tick();

      return () => {
        // React effect 重跑或卸载时阻止异步回调继续写状态，并释放 WebGL 资源。
        disposed = true;
        resizeObserver.disconnect();
        controls.dispose();
        if (modelRoot) {
          modelRoot.traverse((child) => {
            const mesh = child as THREE.Mesh;
            mesh.geometry?.dispose?.();
            const mat = mesh.material as THREE.Material | THREE.Material[] | undefined;
            if (Array.isArray(mat)) mat.forEach((m) => m.dispose());
            else mat?.dispose?.();
          });
        }
        if (renderer.domElement.parentNode === host) host.removeChild(renderer.domElement);
        renderer.dispose();
      };
    }, [modelUrl, options]);

    return (
      <figure className="space-y-1">
        <div className="relative overflow-hidden rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)]">
          <div
            ref={hostRef}
            role="img"
            aria-label={options.caption || "3D model"}
            className="w-full"
            style={{ height: options.height, background: options.background }}
          />
          {status === "loading" && (
            <div className="absolute inset-0 flex items-center justify-center text-xs text-white/70">
              Loading 3D model…
            </div>
          )}
          {(status === "error" || !modelUrl) && (
            <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 text-white/70">
              <Box className="h-6 w-6" aria-hidden />
              <span className="text-xs">{!modelUrl ? "Missing model src" : "Failed to load model"}</span>
            </div>
          )}
        </div>
        {options.caption ? (
          <figcaption className="text-center text-xs text-[var(--chat-text-soft)]">
            {options.caption}
          </figcaption>
        ) : null}
      </figure>
    );
  }
);

GenUiModel3D.displayName = "GenUiModel3D";

export default GenUiModel3D;
