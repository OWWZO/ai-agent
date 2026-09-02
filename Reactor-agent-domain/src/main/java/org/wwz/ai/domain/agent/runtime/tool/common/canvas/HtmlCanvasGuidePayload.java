package org.wwz.ai.domain.agent.runtime.tool.common.canvas;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按需提供给模型的 HTML canvas 创作指南。
 *
 * <p>指南描述的是 canvas_publish 的输入边界、预览运行时和质量门槛，
 * 不参与 HTML 解析或发布；GenUI HtmlFrame 使用另一条渲染路径。</p>
 */
public final class HtmlCanvasGuidePayload {

    private static final String REFERENCE_TEMPLATE = """
            <main class="min-h-screen bg-white text-zinc-950">
              <div class="mx-auto max-w-6xl px-5 py-12 sm:px-8 lg:py-20">
                <header class="max-w-3xl">
                  <p class="text-sm font-medium tracking-wide text-zinc-500">Context label</p>
                  <h1 class="mt-3 text-4xl font-semibold tracking-tight sm:text-6xl">
                    One clear promise, expressed in the product's own voice
                  </h1>
                  <p class="mt-5 max-w-2xl text-base leading-7 text-zinc-600 sm:text-lg">
                    A concise explanation that gives the reader enough context to act.
                  </p>
                </header>
                <section aria-labelledby="details" class="mt-12 border-t border-black/10 pt-8">
                  <h2 id="details" class="text-xl font-semibold tracking-tight">What matters</h2>
                  <div class="mt-6 grid gap-8 md:grid-cols-3">
                    <article>
                      <h3 class="font-medium">Clear hierarchy</h3>
                      <p class="mt-2 text-sm leading-6 text-zinc-600">
                        Make the reading order obvious without decorating every block.
                      </p>
                    </article>
                    <article>
                      <h3 class="font-medium">Useful content</h3>
                      <p class="mt-2 text-sm leading-6 text-zinc-600">
                        Replace this example with specific, credible user-facing copy.
                      </p>
                    </article>
                    <article>
                      <h3 class="font-medium">Responsive by default</h3>
                      <p class="mt-2 text-sm leading-6 text-zinc-600">
                        Let the layout collapse naturally before adding extra breakpoints.
                      </p>
                    </article>
                  </div>
                </section>
              </div>
            </main>
            """;

    private HtmlCanvasGuidePayload() {
    }

    public static Map<String, Object> payload() {
        Map<String, Object> guide = new LinkedHashMap<>();
        guide.put("purpose",
                "Create a complete, credible webpage. Visual language follows the content, audience, "
                        + "product, and brand—not a house style from this guide.");
        guide.put("when_to_call", List.of(
                "Use for substantial, branded, interactive, or appearance-sensitive HTML pages.",
                "Skip when the page has not first been written to the active workspace.",
                "This guide is for canvas_publish. GenUI HtmlFrame is a separate path.",
                "Charts / KPI / dashboards → use emit_ui_tree (Chart component), NOT canvas_publish."
        ));
        guide.put("design_method", List.of(
                "Infer the page job first: audience, primary action, information order, tone.",
                "Choose one coherent visual direction. Default to light surface unless dark is requested.",
                "Establish reading order before decoration: one dominant idea per viewport.",
                "Use the reference template only as structure; replace layout when the brief differs."
        ));
        guide.put("visual_quality", List.of(
                "Layout: small spacing scale, consistent alignment, deliberate container widths.",
                "Typography: contrast between display, heading, body, metadata; ~45–75 chars/line.",
                "Color: semantic roles from the brief; check contrast.",
                "Content: specific labels and realistic values; no filler or placeholders.",
                "Motion: only when it explains state; page must remain useful with JS off.",
                "Do NOT hand-draw fake pie charts with SVG stroke-dasharray — use emit_ui_tree Chart."
        ));
        guide.put("responsive_accessibility", List.of(
                "Semantic HTML and single-column mobile first; enhance at sm/md/lg only as needed.",
                "Verify 320px mobile, overflow, focus, keyboard, labels, hit areas.",
                "One h1, ordered headings, landmarks, native buttons/links, useful alt text."
        ));
        guide.put("preview_runtime", Map.of(
                "injected",
                "No preview shell, CSS, or script assets are injected. The workspace file must include its "
                        + "own complete HTML structure and any required styles, scripts, fonts, and media.",
                "document_shape",
                "Write a complete HTML document. A fragment may render as HTML, but it has no injected host "
                        + "styles or helper assets.",
                "javascript",
                "The preview URL serves the existing file. Include scripts in that file when needed and keep "
                        + "the page useful when scripts are unavailable.",
                "three_js",
                "Use preloaded window.THREE for free-form WebGL in canvas_publish. "
                        + "Prefer emit_ui_tree ThreeJsFrame for structured or sandboxed scripted scenes and Model3D for glb/gltf URLs. "
                        + "Do not add another Three.js script unless you need a different version.",
                "html_css_svg",
                "Inline <style>, class/style attributes, and SVG/chart primitives are supported when written "
                        + "into the workspace file."
        ));
        guide.put("available_shell_tokens", Map.of(
                "note",
                "Not injected by the preview host; define any compatibility primitives in the workspace file if needed.",
                "font",
                "Inter, system-ui, -apple-system, sans-serif.",
                "utilities",
                Map.of(
                        "wa-card", "Neutral bordered surface with 12px radius, padding, soft shadow.",
                        "wa-gradient", "Sky-to-indigo gradient with white text.",
                        "wa-gradient-warm", "Orange-to-pink gradient with white text.",
                        "wa-gradient-fresh", "Emerald-to-sky gradient with white text."
                )
        ));
        guide.put("reactor_delivery", Map.of(
                "delivery",
                "Write the complete HTML file via workspace_write first, then call canvas_publish with its required html_path. "
                        + "The path must already exist under the active session workspace.",
                "mode",
                "P0 is internally fixed to html. canvas_publish reuses the existing file-service preview/download "
                        + "routes and does not upload or register the file again."
        ));
        guide.put("quality_gate", List.of(
                "First viewport communicates what this is, why it matters, and what to do next.",
                "Hierarchy remains clear; no decorative device repeated by habit.",
                "Copy is complete and free of placeholders or implementation commentary.",
                "No clipping, horizontal overflow, broken assets, or unreadable contrast.",
                "The result fits this brief rather than a reusable AI template."
        ));
        guide.put("reference_template", REFERENCE_TEMPLATE);
        return guide;
    }
}
