export type ThreeJsSceneDocumentOptions = {
  title: string;
  height: number;
  background: string;
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

const THREE_VERSION = "0.160.0";
const THREE_CDN_BASE = `https://cdn.jsdelivr.net/npm/three@${THREE_VERSION}`;

function escapeInlineScript(value: string): string {
  return value.replace(/<\/script/gi, "<\\/script");
}

export function buildThreeJsSceneDocument(
  sceneScript: string,
  options: ThreeJsSceneDocumentOptions
): string {
  const frameOptions = escapeInlineScript(JSON.stringify(options));
  const source = escapeInlineScript(sceneScript);

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Three.js scene</title>
  <style>
    :root { color-scheme: dark; }
    html, body {
      width: 100%;
      height: 100%;
      margin: 0;
      overflow: hidden;
      background: ${options.background};
    }
    body { min-height: 100%; }
    #genui-three-root {
      position: relative;
      width: 100%;
      height: 100%;
      min-height: 160px;
      overflow: hidden;
    }
    #genui-three-canvas {
      display: block;
      width: 100%;
      height: 100%;
    }
    #genui-three-error {
      position: absolute;
      inset: 12px;
      z-index: 10;
      overflow: auto;
      margin: 0;
      padding: 12px;
      border: 1px solid rgba(248, 113, 113, 0.45);
      border-radius: 8px;
      background: rgba(69, 10, 10, 0.92);
      color: #fecaca;
      font: 12px/1.5 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      white-space: pre-wrap;
    }
  </style>
</head>
<body>
  <div id="genui-three-root">
    <canvas id="genui-three-canvas"></canvas>
    <pre id="genui-three-error" hidden></pre>
  </div>
  <script type="importmap">
    {
      "imports": {
        "three": "${THREE_CDN_BASE}/build/three.module.js",
        "three/addons/": "${THREE_CDN_BASE}/examples/jsm/"
      }
    }
  </script>
  <script>
    (() => {
      const errorElement = document.getElementById("genui-three-error");
      let lastMessage = "";
      const reportError = (value) => {
        const message = value instanceof Error
          ? value.stack || value.message
          : String(value ?? "Unknown Three.js scene error");
        if (!message || message === lastMessage) return;
        lastMessage = message;
        if (errorElement) {
          errorElement.hidden = false;
          errorElement.textContent = "Three.js scene error\\n" + message;
        }
        window.parent.postMessage({
          type: "genui-three-frame-error",
          message,
        }, "*");
      };
      window.__GENUI_THREE_REPORT_ERROR__ = reportError;
      window.addEventListener("error", (event) => {
        reportError(event.error || event.message);
      });
      window.addEventListener("unhandledrejection", (event) => {
        reportError(event.reason);
      });
    })();
  </script>
  <script type="module">
    import * as __GENUI_THREE from "three";
    import { OrbitControls as __GENUI_ORBIT_CONTROLS } from "three/addons/controls/OrbitControls.js";
    import { GLTFLoader as __GENUI_GLTF_LOADER } from "three/addons/loaders/GLTFLoader.js";

    const __GENUI_REPORT_ERROR__ = window.__GENUI_THREE_REPORT_ERROR__;
    const __GENUI_CONTAINER__ = document.getElementById("genui-three-root");
    const __GENUI_CANVAS__ = document.getElementById("genui-three-canvas");
    const __GENUI_FRAME_OPTIONS__ = ${frameOptions};
    const __GENUI_RESIZE_LISTENERS__ = new Set();

    window.THREE = __GENUI_THREE;
    window.OrbitControls = __GENUI_ORBIT_CONTROLS;
    window.GLTFLoader = __GENUI_GLTF_LOADER;
    window.container = __GENUI_CONTAINER__;
    window.canvas = __GENUI_CANVAS__;
    window.frameOptions = __GENUI_FRAME_OPTIONS__;
    window.reportError = __GENUI_REPORT_ERROR__;
    window.importThreeAddon = (specifier) => import(
      "three/addons/" + String(specifier).replace(/^\\/+/, "")
    );

    const __GENUI_ON_RESIZE__ = (listener) => {
      if (typeof listener !== "function") return () => {};
      __GENUI_RESIZE_LISTENERS__.add(listener);
      try {
        listener(__GENUI_CONTAINER__);
      } catch (error) {
        __GENUI_REPORT_ERROR__(error);
      }
      return () => __GENUI_RESIZE_LISTENERS__.delete(listener);
    };
    window.onResize = __GENUI_ON_RESIZE__;

    const __GENUI_RESIZE_OBSERVER__ = new ResizeObserver(() => {
      __GENUI_RESIZE_LISTENERS__.forEach((listener) => {
        try {
          listener(__GENUI_CONTAINER__);
        } catch (error) {
          __GENUI_REPORT_ERROR__(error);
        }
      });
    });
    __GENUI_RESIZE_OBSERVER__.observe(__GENUI_CONTAINER__);
    window.dispatchEvent(new Event("genui-three-ready"));
  </script>
  <script>
    window.addEventListener("genui-three-ready", () => {
      try {
        const result = (() => {
${source}
        })();
        if (result && typeof result.then === "function") {
          result.catch(window.reportError);
        }
      } catch (error) {
        window.reportError(error);
      }
    }, { once: true });
  </script>
</body>
</html>`;
}
