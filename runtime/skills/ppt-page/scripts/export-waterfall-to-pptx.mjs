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
    mode: "editable",
    slideWidth: DEFAULT_SLIDE_WIDTH,
    slideHeight: DEFAULT_SLIDE_HEIGHT,
    previewDir: null,
    nodeModules: null,
    browserExecutable: null,
  };
  const positional = [];
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--mode") args.mode = argv[++i];
    else if (arg === "--preview-dir") args.previewDir = argv[++i];
    else if (arg === "--node-modules") args.nodeModules = argv[++i];
    else if (arg === "--browser-executable") args.browserExecutable = argv[++i];
    else if (arg === "--slide-width") args.slideWidth = Number(argv[++i]);
    else if (arg === "--slide-height") args.slideHeight = Number(argv[++i]);
    else if (arg === "--help" || arg === "-h") args.help = true;
    else positional.push(arg);
  }
  args.input = positional[0];
  args.output = positional[1];
  return args;
}

function usage() {
  console.log(`Usage:
  node ${path.join(SCRIPT_DIR, "export-waterfall-to-pptx.mjs")} input.html output.pptx [options]

Options:
  --mode editable|raster     editable = text/shapes/images where practical; raster = one PNG per slide
  --preview-dir DIR          write rendered PNG previews and layout QA artifacts
  --node-modules DIR         directory containing playwright and pptxgenjs
  --browser-executable PATH  optional Chrome/Chromium executable if Playwright browsers are absent
  --slide-width N            PPT canvas width in px, default 1600
  --slide-height N           PPT canvas height in px, default 900

Notes:
  Editable mode is a best-effort converter for waterfall decks built with this skill.
  It preserves text as PowerPoint text boxes, local images as image objects, common
  boxes/rules as native shapes, and captions as speaker notes. CSS-only effects,
  complex masks, and arbitrary HTML layouts may need light manual cleanup.`);
}

function makeRequire(nodeModules) {
  const candidates = [];
  if (nodeModules) candidates.push(path.join(path.resolve(nodeModules), "noop.js"));
  candidates.push("/home/user/noop.js");
  candidates.push(path.join(SCRIPT_DIR, "noop.js"));
  for (const filename of candidates) {
    try {
      const req = createRequire(filename);
      req.resolve("playwright");
      return req;
    } catch {
      // Try the next Node resolution root.
    }
  }
  return createRequire(import.meta.url);
}

function loadDependencies(nodeModules) {
  const req = makeRequire(nodeModules);
  let playwright;
  let PptxGenJS;
  try {
    playwright = req("playwright");
  } catch (error) {
    throw new Error(`Cannot resolve playwright. Pass --node-modules or set NODE_PATH. ${error.message}`);
  }
  try {
    PptxGenJS = req("pptxgenjs");
  } catch (error) {
    throw new Error(`Cannot resolve pptxgenjs. Pass --node-modules or set NODE_PATH. ${error.message}`);
  }
  return { chromium: playwright.chromium, PptxGenJS };
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

const SLIDE_INCH_WIDTH = 13.333;

function clampChannel(value) {
  return Math.max(0, Math.min(255, Math.round(value)));
}

function hexFromRgb({ r, g, b }) {
  return `#${[r, g, b].map((n) => clampChannel(n).toString(16).padStart(2, "0")).join("")}`;
}

function parseCssColor(value) {
  if (!value || value === "transparent" || value === "none") return null;
  if (typeof value === "object" && value.hex) return value;
  const hex = String(value).trim();
  const shortHex = hex.match(/^#([0-9a-f]{3})$/i);
  if (shortHex) {
    const [r, g, b] = shortHex[1].split("").map((part) => Number.parseInt(part + part, 16));
    return { r, g, b, alpha: 1, hex: hexFromRgb({ r, g, b }) };
  }
  const longHex = hex.match(/^#([0-9a-f]{6})$/i);
  if (longHex) {
    const raw = longHex[1];
    const r = Number.parseInt(raw.slice(0, 2), 16);
    const g = Number.parseInt(raw.slice(2, 4), 16);
    const b = Number.parseInt(raw.slice(4, 6), 16);
    return { r, g, b, alpha: 1, hex: hexFromRgb({ r, g, b }) };
  }
  const match = hex.match(/rgba?\(([^)]+)\)/i);
  if (!match) return { r: 0, g: 0, b: 0, alpha: 1, hex };
  const parts = match[1].split(",").map((part) => part.trim());
  const [r, g, b] = parts.slice(0, 3).map((part) => clampChannel(Number.parseFloat(part) || 0));
  const alpha = parts.length >= 4 ? Math.max(0, Math.min(1, Number.parseFloat(parts[3]))) : 1;
  return { r, g, b, alpha: Number.isFinite(alpha) ? alpha : 1, hex: hexFromRgb({ r, g, b }) };
}

function mixCssColor(foreground, backdrop) {
  const fg = parseCssColor(foreground);
  if (!fg) return null;
  if (fg.alpha >= 0.995) return fg;
  const bg = parseCssColor(backdrop) || { r: 255, g: 255, b: 255, alpha: 1, hex: "#ffffff" };
  const alpha = fg.alpha;
  return {
    r: clampChannel(fg.r * alpha + bg.r * (1 - alpha)),
    g: clampChannel(fg.g * alpha + bg.g * (1 - alpha)),
    b: clampChannel(fg.b * alpha + bg.b * (1 - alpha)),
    alpha: 1,
    hex: "",
  };
}

function colorForPpt(value, fallback = "none", backdrop = "#ffffff") {
  const parsed = parseCssColor(value);
  if (!parsed || parsed.alpha < 0.02) return fallback;
  const mixed = parsed.alpha < 0.995 ? mixCssColor(parsed, backdrop) : parsed;
  return mixed ? hexFromRgb(mixed) : fallback;
}

function pptHex(value, fallback = "FFFFFF", backdrop = "#ffffff") {
  const raw = colorForPpt(value, `#${fallback}`, backdrop);
  if (!raw || raw === "none") return null;
  return String(raw).replace("#", "").toUpperCase();
}

function boxInches(box, slideWidth, slideHeight) {
  const heightIn = (slideHeight / slideWidth) * SLIDE_INCH_WIDTH;
  return {
    x: (box.left * SLIDE_INCH_WIDTH) / slideWidth,
    y: (box.top * heightIn) / slideHeight,
    w: (box.width * SLIDE_INCH_WIDTH) / slideWidth,
    h: (box.height * heightIn) / slideHeight,
  };
}

function createPresentation(PptxGenJS, slideWidth, slideHeight) {
  const pres = new PptxGenJS();
  const heightIn = (slideHeight / slideWidth) * SLIDE_INCH_WIDTH;
  pres.defineLayout({ name: "WATERFALL", width: SLIDE_INCH_WIDTH, height: heightIn });
  pres.layout = "WATERFALL";
  return pres;
}

function normalizePathFromUrl(src) {
  if (src.startsWith("file://")) return fileURLToPath(src);
  return null;
}

async function bytesForImage(src, baseDir) {
  const filePath = normalizePathFromUrl(src) ?? (src.startsWith("http") ? null : path.resolve(baseDir, src));
  if (filePath) {
    const bytes = await fs.readFile(filePath);
    return { bytes, contentType: contentTypeForPath(filePath) };
  }
  const response = await fetch(src);
  if (!response.ok) throw new Error(`Failed to fetch image ${src}: ${response.status}`);
  return {
    bytes: new Uint8Array(await response.arrayBuffer()),
    contentType: response.headers.get("content-type") || contentTypeForPath(src),
  };
}

function contentTypeForPath(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  if (ext === ".png") return "image/png";
  if (ext === ".webp") return "image/webp";
  if (ext === ".gif") return "image/gif";
  if (ext === ".svg") return "image/svg+xml";
  return "image/jpeg";
}

function clampBox(box, width, height) {
  const left = Math.max(0, Math.min(width, box.left));
  const top = Math.max(0, Math.min(height, box.top));
  const right = Math.max(0, Math.min(width, box.left + box.width));
  const bottom = Math.max(0, Math.min(height, box.top + box.height));
  return {
    left,
    top,
    width: Math.max(0, right - left),
    height: Math.max(0, bottom - top),
  };
}

async function extractDeck(chromium, htmlPath, slideWidth, slideHeight, browserExecutablePath) {
  const browserExecutable = await findBrowserExecutable(browserExecutablePath);
  const browser = await chromium.launch({
    headless: true,
    ...(browserExecutable ? { executablePath: browserExecutable } : {}),
  });
  const page = await browser.newPage({
    viewport: { width: slideWidth + 120, height: slideHeight + 120 },
    deviceScaleFactor: 1,
  });
  const htmlUrl = pathToFileURL(path.resolve(htmlPath)).href;
  await page.goto(htmlUrl, { waitUntil: "networkidle" });
  await page.addStyleTag({
    content: `
      html, body { background: #f1f1f0 !important; }
      .deck-shell { width: ${slideWidth}px !important; padding: 0 !important; }
      .caption { display: none !important; }
      .page-badge { opacity: 0 !important; }
    `,
  });
  await page.waitForTimeout(500);
  await page.evaluate(() => window.dispatchEvent(new Event("resize")));
  await page.waitForTimeout(500);

  const slides = await page.evaluate(({ slideWidth, slideHeight }) => {
    const transparent = (value) => !value || value === "transparent" || value === "rgba(0, 0, 0, 0)";
    const rectData = (rect, frameRect) => ({
      left: (rect.left - frameRect.left) * (slideWidth / frameRect.width),
      top: (rect.top - frameRect.top) * (slideHeight / frameRect.height),
      width: rect.width * (slideWidth / frameRect.width),
      height: rect.height * (slideHeight / frameRect.height),
    });
    const isVisible = (el, options = {}) => {
      const cs = getComputedStyle(el);
      const rect = el.getBoundingClientRect();
      const minSize = options.allowHairline ? 0.25 : 1;
      return cs.display !== "none" && cs.visibility !== "hidden" && Number(cs.opacity) !== 0 && rect.width > minSize && rect.height > minSize;
    };
    const textCandidateSelector = [
      ".slide-kicker", ".slide-title", ".slide-lead", ".quote", ".stat",
      ".specimen-label", ".latin", ".tag",
      "h1", "h2", "h3", "p", "li", "strong", "b", "span",
    ].join(",");
    const hasClassPrefix = (el, prefixes) => Array.from(el.classList || []).some((className) => prefixes.some((prefix) => className.startsWith(prefix)));
    const shapeSelector = [
      ".rule", ".media-frame",
    ].join(",");

    return Array.from(document.querySelectorAll(".deck-card")).map((card, slideIndex) => {
      const frame = card.querySelector(".slide-frame");
      const stage = card.querySelector(".slide-stage") || frame;
      const frameRect = frame.getBoundingClientRect();
      const frameStyle = getComputedStyle(frame);
      const slideBackdrop = frameStyle.backgroundColor || "rgb(255, 255, 255)";
      const base = {
        index: slideIndex + 1,
        background: frameStyle.backgroundColor,
        notes: Array.from(card.querySelectorAll(".caption p")).map((p) => p.innerText.trim()).filter(Boolean).join("\n\n"),
        shapes: [],
        images: [],
        texts: [],
      };

      const pushedTextRects = [];
      const overlapsExistingText = (box, text) => pushedTextRects.some((item) => {
        const same = item.text === text;
        const x = Math.max(0, Math.min(item.left + item.width, box.left + box.width) - Math.max(item.left, box.left));
        const y = Math.max(0, Math.min(item.top + item.height, box.top + box.height) - Math.max(item.top, box.top));
        const overlap = x * y;
        const area = Math.max(1, Math.min(item.width * item.height, box.width * box.height));
        return same && overlap / area > 0.72;
      });

      const shapeElements = Array.from(new Set([
        ...Array.from(frame.querySelectorAll(shapeSelector)),
        ...Array.from(frame.querySelectorAll("[class]")).filter((el) => hasClassPrefix(el, ["export-shape-", "export-chart-"])),
      ]));
      for (const el of shapeElements) {
        if (!isVisible(el, { allowHairline: true })) continue;
        const cs = getComputedStyle(el);
        const isRule = el.classList.contains("rule");
        const rect = rectData(el.getBoundingClientRect(), frameRect);
        const fill = transparent(cs.backgroundColor) ? "none" : cs.backgroundColor;
        const borders = [
          { side: "top", width: parseFloat(cs.borderTopWidth) || 0, color: cs.borderTopColor },
          { side: "right", width: parseFloat(cs.borderRightWidth) || 0, color: cs.borderRightColor },
          { side: "bottom", width: parseFloat(cs.borderBottomWidth) || 0, color: cs.borderBottomColor },
          { side: "left", width: parseFloat(cs.borderLeftWidth) || 0, color: cs.borderLeftColor },
        ].map((border) => ({ ...border, color: transparent(border.color) ? "none" : border.color }));
        const visibleBorders = borders.filter((border) => border.width > 0 && border.color !== "none");
        const uniformBorder = visibleBorders.length === 4
          && visibleBorders.every((border) => Math.abs(border.width - visibleBorders[0].width) < 0.5 && border.color === visibleBorders[0].color);
        if (fill !== "none" || uniformBorder || isRule) {
          base.shapes.push({
            kind: isRule ? "rule" : "rect",
            box: rect,
            fill,
            backdrop: slideBackdrop,
            line: uniformBorder
              ? { fill: visibleBorders[0].color, width: visibleBorders[0].width }
              : { fill: fill === "none" ? cs.color : "none", width: 0 },
          });
        }
        if (!uniformBorder) {
          for (const border of visibleBorders) {
            const borderBox = { ...rect };
            if (border.side === "top") borderBox.height = border.width;
            else if (border.side === "bottom") { borderBox.top = rect.top + rect.height - border.width; borderBox.height = border.width; }
            else if (border.side === "left") borderBox.width = border.width;
            else if (border.side === "right") { borderBox.left = rect.left + rect.width - border.width; borderBox.width = border.width; }
            base.shapes.push({
              box: borderBox,
              fill: border.color,
              backdrop: fill !== "none" ? fill : slideBackdrop,
              line: { fill: "none", width: 0 },
            });
          }
        }
      }

      for (const img of Array.from(frame.querySelectorAll("img"))) {
        if (!isVisible(img)) continue;
        const cs = getComputedStyle(img);
        base.images.push({
          src: img.currentSrc || img.src,
          alt: img.alt || `Slide ${slideIndex + 1} image`,
          fit: cs.objectFit === "cover" ? "cover" : "contain",
          box: rectData(img.getBoundingClientRect(), frameRect),
        });
      }

      const textElements = Array.from(new Set([
        ...Array.from(stage.querySelectorAll(textCandidateSelector)),
        ...Array.from(stage.querySelectorAll("[class]")).filter((el) => hasClassPrefix(el, ["export-text-"])),
      ]));
      for (const el of textElements) {
        if (!isVisible(el)) continue;
        if (el.closest(".page-badge")) continue;
        const text = (el.innerText || el.textContent || "").trim().replace(/\s+\n/g, "\n");
        if (!text) continue;
        const childText = Array.from(el.children).map((child) => (child.innerText || "").trim()).filter(Boolean).join("\n").trim();
        if (childText && childText.length >= text.length * 0.72) continue;
        const cs = getComputedStyle(el);
        const box = rectData(el.getBoundingClientRect(), frameRect);
        if (box.width < 2 || box.height < 2 || overlapsExistingText(box, text)) continue;
        pushedTextRects.push({ ...box, text });
        base.texts.push({
          text,
          box,
          color: cs.color,
          fontSize: parseFloat(cs.fontSize) || 24,
          fontFamily: cs.fontFamily,
          fontWeight: cs.fontWeight,
          fontStyle: cs.fontStyle,
          textAlign: cs.textAlign,
          lineHeight: cs.lineHeight,
          backdrop: slideBackdrop,
        });
      }
      return base;
    });
  }, { slideWidth, slideHeight });

  await browser.close();
  return slides;
}

function addText(slide, item, slideWidth, slideHeight) {
  const box = clampBox(item.box, slideWidth, slideHeight);
  if (box.width < 2 || box.height < 2) return;
  const pos = boxInches(box, slideWidth, slideHeight);
  const color = pptHex(item.color, "1A1A1A", item.backdrop || "#ffffff");
  slide.addText(item.text, {
    x: pos.x,
    y: pos.y,
    w: pos.w,
    h: pos.h,
    fontSize: Math.max(8, Math.round(item.fontSize * (12 / 16))),
    bold: Number.parseInt(item.fontWeight, 10) >= 650 || item.fontWeight === "bold",
    italic: item.fontStyle === "italic",
    color: color || "1A1A1A",
    align: ["center", "right", "justify"].includes(item.textAlign) ? item.textAlign : "left",
    valign: "top",
    margin: 0,
    fontFace: item.fontFamily?.split(",")[0]?.replaceAll('"', "").trim() || "Arial",
  });
}

function addShape(slide, item, slideWidth, slideHeight) {
  const box = clampBox(item.box, slideWidth, slideHeight);
  if (box.width < 1 || box.height < 0.25) return;
  if (item.kind === "rule" && box.height < 1.5) {
    box.height = 1.5;
  }
  const pos = boxInches(box, slideWidth, slideHeight);
  const fill = pptHex(item.fill, null, item.backdrop || "#ffffff");
  const line = pptHex(item.line?.fill, null, item.backdrop || "#ffffff");
  const options = {
    x: pos.x,
    y: pos.y,
    w: pos.w,
    h: pos.h,
  };
  if (fill) options.fill = { color: fill };
  if (line && (item.line?.width ?? 0) > 0) {
    options.line = { color: line, width: item.line.width * 0.75 };
  } else {
    options.line = { color: fill || "FFFFFF", width: 0 };
  }
  slide.addShape("rect", options);
}

async function addImage(slide, item, baseDir, slideWidth, slideHeight) {
  const box = clampBox(item.box, slideWidth, slideHeight);
  if (box.width < 2 || box.height < 2) return;
  const { bytes, contentType } = await bytesForImage(item.src, baseDir);
  const pos = boxInches(box, slideWidth, slideHeight);
  const mime = contentType || "image/png";
  slide.addImage({
    data: `${mime};base64,${Buffer.from(bytes).toString("base64")}`,
    x: pos.x,
    y: pos.y,
    w: pos.w,
    h: pos.h,
    sizing: { type: item.fit === "cover" ? "cover" : "contain", w: pos.w, h: pos.h },
  });
}

async function exportEditable({ PptxGenJS }, slides, htmlPath, output, options) {
  const presentation = createPresentation(PptxGenJS, options.slideWidth, options.slideHeight);
  const baseDir = path.dirname(path.resolve(htmlPath));
  for (const item of slides) {
    const slide = presentation.addSlide();
    const bg = pptHex(item.background, "FFFFFF", "#ffffff");
    if (bg) slide.background = { color: bg };
    for (const shape of item.shapes.filter((shape) => shape.kind !== "rule")) addShape(slide, shape, options.slideWidth, options.slideHeight);
    for (const image of item.images) await addImage(slide, image, baseDir, options.slideWidth, options.slideHeight);
    for (const shape of item.shapes.filter((shape) => shape.kind === "rule")) addShape(slide, shape, options.slideWidth, options.slideHeight);
    for (const text of item.texts) addText(slide, text, options.slideWidth, options.slideHeight);
    if (item.notes) slide.addNotes(item.notes);
  }
  await fs.mkdir(path.dirname(path.resolve(output)), { recursive: true });
  if (options.previewDir) await writeQa(slides, options.previewDir);
  await presentation.writeFile({ fileName: path.resolve(output) });
  return { presentation, slideCount: slides.length, slides };
}

async function exportRaster({ PptxGenJS, chromium }, htmlPath, output, options) {
  const browserExecutable = await findBrowserExecutable(options.browserExecutable);
  const browser = await chromium.launch({
    headless: true,
    ...(browserExecutable ? { executablePath: browserExecutable } : {}),
  });
  const page = await browser.newPage({
    viewport: { width: options.slideWidth + 120, height: options.slideHeight + 120 },
    deviceScaleFactor: 2,
  });
  await page.goto(pathToFileURL(path.resolve(htmlPath)).href, { waitUntil: "networkidle" });
  await page.addStyleTag({
    content: `.deck-shell{width:${options.slideWidth}px!important;padding:0!important}.caption{display:none!important}.page-badge{opacity:0!important}`,
  });
  await page.waitForTimeout(500);
  await page.evaluate(() => window.dispatchEvent(new Event("resize")));
  await page.waitForTimeout(500);
  const frames = await page.locator(".slide-frame").all();
  const presentation = createPresentation(PptxGenJS, options.slideWidth, options.slideHeight);
  const previews = [];
  for (let index = 0; index < frames.length; index += 1) {
    const bytes = await frames[index].screenshot({ type: "png" });
    previews.push(bytes);
    const slide = presentation.addSlide();
    slide.addImage({
      data: `image/png;base64,${Buffer.from(bytes).toString("base64")}`,
      x: 0,
      y: 0,
      w: SLIDE_INCH_WIDTH,
      h: (options.slideHeight / options.slideWidth) * SLIDE_INCH_WIDTH,
    });
  }
  await browser.close();
  await fs.mkdir(path.dirname(path.resolve(output)), { recursive: true });
  if (options.previewDir) {
    await fs.mkdir(options.previewDir, { recursive: true });
    for (let index = 0; index < previews.length; index += 1) {
      const stem = `slide-${String(index + 1).padStart(2, "0")}`;
      await fs.writeFile(path.join(options.previewDir, `${stem}.png`), previews[index]);
    }
  }
  await presentation.writeFile({ fileName: path.resolve(output) });
  return { presentation, slideCount: frames.length, slides: [] };
}

async function writeQa(slides, previewDir) {
  await fs.mkdir(previewDir, { recursive: true });
  for (const [index, slide] of slides.entries()) {
    const stem = `slide-${String(index + 1).padStart(2, "0")}`;
    await fs.writeFile(
      path.join(previewDir, `${stem}.layout.json`),
      JSON.stringify({
        index: slide.index,
        texts: slide.texts?.length || 0,
        shapes: slide.shapes?.length || 0,
        images: slide.images?.length || 0,
      }),
    );
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help || !args.input || !args.output) {
    usage();
    process.exit(args.help ? 0 : 1);
  }
  if (!["editable", "raster"].includes(args.mode)) throw new Error("--mode must be editable or raster");
  const deps = loadDependencies(args.nodeModules);
  let result;
  if (args.mode === "raster") {
    result = await exportRaster(deps, args.input, args.output, args);
  } else {
    const slides = await extractDeck(deps.chromium, args.input, args.slideWidth, args.slideHeight, args.browserExecutable);
    if (!slides.length) throw new Error("No .deck-card / .slide-frame slides found.");
    result = await exportEditable(deps, slides, args.input, args.output, args);
  }
  const inspectPath = `${args.output}.inspect.ndjson`;
  const inspectLines = (result.slides || []).map((slide, index) => JSON.stringify({
    kind: "slide",
    index: index + 1,
    notes: slide.notes || "",
    texts: slide.texts?.length || 0,
    shapes: slide.shapes?.length || 0,
    images: slide.images?.length || 0,
  }));
  if (!inspectLines.length) {
    inspectLines.push(JSON.stringify({ kind: "deck", slides: result.slideCount, mode: args.mode }));
  }
  await fs.writeFile(inspectPath, `${inspectLines.join("\n")}\n`);
  console.log(JSON.stringify({
    output: path.resolve(args.output),
    inspect: path.resolve(inspectPath),
    slides: result.slideCount,
    mode: args.mode,
  }, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
