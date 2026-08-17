#!/usr/bin/env node
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { createRequire } from "node:module";

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_SLIDE_WIDTH = 1600;
const DEFAULT_SLIDE_HEIGHT = 900;

function parseArgs(argv) {
  const args = {
    slideWidth: DEFAULT_SLIDE_WIDTH,
    slideHeight: DEFAULT_SLIDE_HEIGHT,
    scale: 2,
    previewDir: null,
    nodeModules: null,
    browserExecutable: null,
  };
  const positional = [];
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--preview-dir") args.previewDir = argv[++i];
    else if (arg === "--node-modules") args.nodeModules = argv[++i];
    else if (arg === "--browser-executable") args.browserExecutable = argv[++i];
    else if (arg === "--slide-width") args.slideWidth = Number(argv[++i]);
    else if (arg === "--slide-height") args.slideHeight = Number(argv[++i]);
    else if (arg === "--scale") args.scale = Number(argv[++i]);
    else if (arg === "--help" || arg === "-h") args.help = true;
    else positional.push(arg);
  }
  args.input = positional[0];
  args.output = positional[1];
  return args;
}

function usage() {
  console.log(`Usage:
  node ${path.join(SCRIPT_DIR, "export-waterfall-to-pdf.mjs")} input.html output.pdf [options]

Options:
  --preview-dir DIR          write per-slide PNG screenshots used in the PDF
  --node-modules DIR         directory containing playwright and pdf-lib
  --browser-executable PATH  optional Chrome/Chromium executable if Playwright browsers are absent
  --slide-width N            PDF page width in points, default 1600
  --slide-height N           PDF page height in points, default 900
  --scale N                  screenshot scale/deviceScaleFactor, default 2

Notes:
  This exporter creates one 16:9 PDF page per .slide-frame. It hides captions
  and page badges, then embeds each rendered slide as a full-page PNG. Use this
  for high-fidelity sharing PDFs; HTML text is not editable inside the PDF.`);
}

function makeRequire(nodeModules) {
  if (nodeModules) {
    return createRequire(path.join(path.resolve(nodeModules), "noop.js"));
  }
  return createRequire(import.meta.url);
}

function loadDependencies(nodeModules) {
  const req = makeRequire(nodeModules);
  let playwright;
  let pdfLib;
  try {
    playwright = req("playwright");
  } catch (error) {
    throw new Error(`Cannot resolve playwright. Pass --node-modules or set NODE_PATH. ${error.message}`);
  }
  try {
    pdfLib = req("pdf-lib");
  } catch (error) {
    throw new Error(`Cannot resolve pdf-lib. Pass --node-modules or set NODE_PATH. ${error.message}`);
  }
  return { chromium: playwright.chromium, ...pdfLib };
}

async function findBrowserExecutable(explicitPath) {
  const candidates = [
    explicitPath,
    process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE,
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
  ].filter(Boolean);
  for (const candidate of candidates) {
    try {
      await fs.access(candidate);
      return candidate;
    } catch {
      // Try the next conventional browser location.
    }
  }
  return null;
}

async function captureSlides(chromium, htmlPath, options) {
  const browserExecutable = await findBrowserExecutable(options.browserExecutable);
  const browser = await chromium.launch({
    headless: true,
    ...(browserExecutable ? { executablePath: browserExecutable } : {}),
  });
  const page = await browser.newPage({
    viewport: { width: options.slideWidth + 120, height: options.slideHeight + 120 },
    deviceScaleFactor: options.scale,
  });
  await page.goto(pathToFileURL(path.resolve(htmlPath)).href, { waitUntil: "networkidle" });
  await page.addStyleTag({
    content: `
      html, body { background: #f1f1f0 !important; }
      .deck-shell { width: ${options.slideWidth}px !important; padding: 0 !important; }
      .caption { display: none !important; }
      .page-badge { opacity: 0 !important; }
    `,
  });
  await page.waitForTimeout(500);
  await page.evaluate(() => window.dispatchEvent(new Event("resize")));
  await page.waitForTimeout(500);

  const frames = await page.locator(".slide-frame").all();
  const images = [];
  for (let index = 0; index < frames.length; index += 1) {
    const bytes = await frames[index].screenshot({ type: "png" });
    images.push(bytes);
    if (options.previewDir) {
      await fs.mkdir(options.previewDir, { recursive: true });
      const filename = `slide-${String(index + 1).padStart(2, "0")}.png`;
      await fs.writeFile(path.join(options.previewDir, filename), bytes);
    }
  }
  await browser.close();
  return images;
}

async function buildPdf(deps, images, output, options) {
  const pdf = await deps.PDFDocument.create();
  pdf.setTitle(path.basename(output, path.extname(output)));
  pdf.setCreator("ppt-page export-waterfall-to-pdf.mjs");
  for (const bytes of images) {
    const page = pdf.addPage([options.slideWidth, options.slideHeight]);
    const png = await pdf.embedPng(bytes);
    page.drawImage(png, {
      x: 0,
      y: 0,
      width: options.slideWidth,
      height: options.slideHeight,
    });
  }
  await fs.mkdir(path.dirname(path.resolve(output)), { recursive: true });
  await fs.writeFile(output, await pdf.save({ useObjectStreams: true }));
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help || !args.input || !args.output) {
    usage();
    process.exit(args.help ? 0 : 1);
  }
  if (!Number.isFinite(args.scale) || args.scale <= 0) throw new Error("--scale must be a positive number");
  const deps = loadDependencies(args.nodeModules);
  const images = await captureSlides(deps.chromium, args.input, args);
  if (!images.length) throw new Error("No .slide-frame elements were captured.");
  await buildPdf(deps, images, args.output, args);
  console.log(JSON.stringify({
    output: path.resolve(args.output),
    slides: images.length,
    previewDir: args.previewDir ? path.resolve(args.previewDir) : null,
  }, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
