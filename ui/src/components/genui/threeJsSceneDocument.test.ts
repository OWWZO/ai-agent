import { describe, expect, it } from "vitest";
import {
  buildThreeJsSceneDocument,
  type ThreeJsSceneDocumentOptions,
} from "./threeJsSceneDocument";

const options: ThreeJsSceneDocumentOptions = {
  title: "Custom scene",
  height: 480,
  background: "#0f172a",
  color: 0x38bdf8,
  accentColor: 0xfbbf24,
  cameraZ: 5,
  autoRotate: true,
  wireframe: false,
  particles: 0,
  orbiters: 0,
  detail: 1,
  rotateSpeed: 0.65,
  dpr: 1.5,
};

describe("threeJsSceneDocument", () => {
  it("preloads Three.js and exposes the scene runtime", () => {
    const document = buildThreeJsSceneDocument(
      "const scene = new THREE.Scene();",
      options
    );

    expect(document).toContain("three.module.js");
    expect(document).toContain("OrbitControls.js");
    expect(document).toContain("GLTFLoader.js");
    expect(document).toContain("window.THREE");
    expect(document).toContain("window.onResize");
  });

  it("keeps a script closing tag inside the sandbox source", () => {
    const document = buildThreeJsSceneDocument(
      'const label = "</script>";',
      options
    );

    expect(document).toContain('<\\/script>');
  });
});
