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
  --node-modules DIR         directory containing playwright and @oai/artifact-tool
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
  if (nodeModules) {
    return createRequire(path.join(path.resolve(nodeModules), "noop.js"));
  }
  return createRequire(import.meta.url);
}

async function loadDependencies(nodeModules) {
  const req = makeRequire(nodeModules);
  let playwright;
  let artifactTool;
  try {
    playwright = req("playwright");
  } catch (error) {
    throw new Error(`Cannot resolve playwright. Pass --node-modules or set NODE_PATH to bundled node_modules. ${error.message}`);
  }
  try {
    artifactTool = await import("@oai/artifact-tool");
  } catch {
    try {
      artifactTool = req("@oai/artifact-tool");
    } catch (error) {
      throw new Error(`Cannot resolve @oai/artifact-tool. Initialize the presentation workspace or pass --node-modules. ${error.message}`);
    }
  }
  return { chromium: playwright.chromium, ...artifactTool };
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

const ARTIFACT_TOOL_SUPPORTS_TRANSPARENT_FILL = false;

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
  if (parsed.alpha < 0.995 && ARTIFACT_TOOL_SUPPORTS_TRANSPARENT_FILL) {
    return { color: parsed.hex, transparency: Math.round((1 - parsed.alpha) * 100) };
  }
  const mixed = parsed.alpha < 0.995 ? mixCssColor(parsed, backdrop) : parsed;
  return mixed ? hexFromRgb(mixed) : fallback;
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

async function writeBlob(filePath, blob) {
  await fs.writeFile(filePath, new Uint8Array(await blob.arrayBuffer()));
}

function addText(slide, item, slideWidth, slideHeight) {
  const box = clampBox(item.box, slideWidth, slideHeight);
  if (box.width < 2 || box.height < 2) return;
  const shape = slide.shapes.add({
    geometry: "textbox",
    position: box,
    fill: "none",
    line: { style: "solid", fill: "none", width: 0 },
  });
  shape.text = item.text;
  shape.text.style = {
    fontSize: Math.max(8, Math.round(item.fontSize)),
    bold: Number.parseInt(item.fontWeight, 10) >= 650 || item.fontWeight === "bold",
    italic: item.fontStyle === "italic",
    color: colorForPpt(item.color, "#1a1a1a", item.backdrop || "#ffffff"),
    alignment: ["center", "right", "justify"].includes(item.textAlign) ? item.textAlign : "left",
    verticalAlignment: "top",
    lineSpacing: 1.1,
    typeface: item.fontFamily?.split(",")[0]?.replaceAll('"', "").trim(),
    insets: { top: 0, right: 0, bottom: 0, left: 0 },
  };
}

function addShape(slide, item, slideWidth, slideHeight) {
  const box = clampBox(item.box, slideWidth, slideHeight);
  if (box.width < 1 || box.height < 0.25) return;
  if (item.kind === "rule" && box.height < 1.5) {
    box.height = 1.5;
  }
  slide.shapes.add({
    geometry: "rect",
    position: box,
    fill: colorForPpt(item.fill, "none", item.backdrop || "#ffffff"),
    line: {
      style: "solid",
      fill: colorForPpt(item.line?.fill, "none", item.backdrop || "#ffffff"),
      width: item.line?.width ?? 0,
    },
  });
}

async function addImage(slide, item, baseDir, slideWidth, slideHeight) {
  const box = clampBox(item.box, slideWidth, slideHeight);
  if (box.width < 2 || box.height < 2) return;
  const { bytes, contentType } = await bytesForImage(item.src, baseDir);
  slide.images.add({
    blob: bytes,
    contentType,
    alt: item.alt,
    fit: item.fit,
    position: box,
  });
}

async function exportEditable({ Presentation, PresentationFile }, slides, htmlPath, output, options) {
  const presentation = Presentation.create({
    slideSize: { width: options.slideWidth, height: options.slideHeight },
  });
  const baseDir = path.dirname(path.resolve(htmlPath));
  for (const item of slides) {
    const slide = presentation.slides.add();
    slide.background.fill = colorForPpt(item.background, "#ffffff", "#ffffff");
    for (const shape of item.shapes.filter((shape) => shape.kind !== "rule")) addShape(slide, shape, options.slideWidth, options.slideHeight);
    for (const image of item.images) await addImage(slide, image, baseDir, options.slideWidth, options.slideHeight);
    for (const shape of item.shapes.filter((shape) => shape.kind === "rule")) addShape(slide, shape, options.slideWidth, options.slideHeight);
    for (const text of item.texts) addText(slide, text, options.slideWidth, options.slideHeight);
    if (item.notes) {
      slide.speakerNotes.textFrame.setText(item.notes);
      slide.speakerNotes.setVisible(true);
    }
  }
  await fs.mkdir(path.dirname(path.resolve(output)), { recursive: true });
  if (options.previewDir) await writeQa(presentation, options.previewDir);
  const pptx = await PresentationFile.exportPptx(presentation);
  await pptx.save(output);
  return presentation;
}

async function exportRaster({ Presentation, PresentationFile }, chromium, htmlPath, output, options) {
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
  const presentation = Presentation.create({ slideSize: { width: options.slideWidth, height: options.slideHeight } });
  for (let index = 0; index < frames.length; index += 1) {
    const bytes = await frames[index].screenshot({ type: "png" });
    const slide = presentation.slides.add();
    slide.images.add({
      blob: bytes,
      contentType: "image/png",
      alt: `Slide ${index + 1}`,
      fit: "cover",
      position: { left: 0, top: 0, width: options.slideWidth, height: options.slideHeight },
    });
  }
  await browser.close();
  await fs.mkdir(path.dirname(path.resolve(output)), { recursive: true });
  if (options.previewDir) await writeQa(presentation, options.previewDir);
  const pptx = await PresentationFile.exportPptx(presentation);
  await pptx.save(output);
  return presentation;
}

async function writeQa(presentation, previewDir) {
  await fs.mkdir(previewDir, { recursive: true });
  for (const [index, slide] of presentation.slides.items.entries()) {
    const stem = `slide-${String(index + 1).padStart(2, "0")}`;
    await writeBlob(path.join(previewDir, `${stem}.png`), await presentation.export({ slide, format: "png", scale: 1 }));
    await fs.writeFile(path.join(previewDir, `${stem}.layout.json`), await (await slide.export({ format: "layout" })).text());
  }
  await writeBlob(path.join(previewDir, "deck-montage.webp"), await presentation.export({ format: "webp", montage: true, scale: 0.5 }));
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help || !args.input || !args.output) {
    usage();
    process.exit(args.help ? 0 : 1);
  }
  if (!["editable", "raster"].includes(args.mode)) throw new Error("--mode must be editable or raster");
  const deps = await loadDependencies(args.nodeModules);
  let presentation;
  if (args.mode === "raster") {
    presentation = await exportRaster(deps, deps.chromium, args.input, args.output, args);
  } else {
    const slides = await extractDeck(deps.chromium, args.input, args.slideWidth, args.slideHeight, args.browserExecutable);
    if (!slides.length) throw new Error("No .deck-card / .slide-frame slides found.");
    presentation = await exportEditable(deps, slides, args.input, args.output, args);
  }
  const inspectPath = `${args.output}.inspect.ndjson`;
  const inspected = await presentation.inspect({ kind: "slide,textbox,shape,image,notes", maxChars: 20000 });
  await fs.writeFile(inspectPath, inspected.ndjson);
  console.log(JSON.stringify({
    output: path.resolve(args.output),
    inspect: path.resolve(inspectPath),
    slides: presentation.slides.items.length,
    mode: args.mode,
  }, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
