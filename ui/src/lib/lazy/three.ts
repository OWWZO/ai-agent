export type ThreeModule = typeof import("three");
export type OrbitControlsModule = typeof import(
  "three/examples/jsm/controls/OrbitControls.js"
);
export type GltfLoaderModule = typeof import(
  "three/examples/jsm/loaders/GLTFLoader.js"
);
export type RoomEnvironmentModule = typeof import(
  "three/examples/jsm/environments/RoomEnvironment.js"
);

let threePromise: Promise<ThreeModule> | undefined;
let orbitControlsPromise: Promise<OrbitControlsModule> | undefined;
let gltfLoaderPromise: Promise<GltfLoaderModule> | undefined;
let roomEnvironmentPromise: Promise<RoomEnvironmentModule> | undefined;

export function loadThree(): Promise<ThreeModule> {
  return (threePromise ??= import("three"));
}

export function loadOrbitControls(): Promise<OrbitControlsModule> {
  return (orbitControlsPromise ??= import(
    "three/examples/jsm/controls/OrbitControls.js"
  ));
}

export function loadGltfLoader(): Promise<GltfLoaderModule> {
  return (gltfLoaderPromise ??= import(
    "three/examples/jsm/loaders/GLTFLoader.js"
  ));
}

export function loadRoomEnvironment(): Promise<RoomEnvironmentModule> {
  return (roomEnvironmentPromise ??= import(
    "three/examples/jsm/environments/RoomEnvironment.js"
  ));
}
