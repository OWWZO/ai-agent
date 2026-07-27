package org.wwz.ai.domain.agent.runtime.tool.common.canvas;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML preview hardening + LeAgent-aligned host shell for canvas_publish.
 * <p>
 * Publish stores raw HTML (scripts kept). Preview can strip JS when allowJs=false.
 * Bare / utility-class pages get Tailwind + Inter + wa-* helpers injected.
 */
public final class HtmlPreviewSanitizer {

    private static final int AUTHORED_STYLE_MIN_CHARS = 80;

    private static final Pattern SCRIPT_TAG = Pattern.compile(
            "(?is)<script\\b[^>]*>.*?</script>");
    private static final Pattern EVENT_HANDLER = Pattern.compile(
            "(?i)\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern JS_URL = Pattern.compile(
            "(?i)\\b(href|src)\\s*=\\s*([\"'])\\s*javascript:[^\"']*\\2");
    private static final Pattern META_REFRESH = Pattern.compile(
            "(?is)<meta\\b[^>]*http-equiv\\s*=\\s*([\"']?)refresh\\1[^>]*>");
    private static final Pattern STYLE_BLOCK = Pattern.compile(
            "(?is)<style\\b[^>]*>(.*?)</style\\s*>");
    private static final Pattern LINK_TAG = Pattern.compile("(?is)<link\\b[^>]*>");
    private static final Pattern STYLESHEET_REL = Pattern.compile(
            "(?i)\\brel\\s*=\\s*(['\"]?)stylesheet\\1");
    private static final Pattern HREF_ATTR = Pattern.compile(
            "(?ix)\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    /** Real Three.js script loads only — not prose mentioning Three.js. */
    private static final Pattern THREE_SCRIPT_HINT = Pattern.compile(
            "(?is)(?:<script\\b[^>]*\\bsrc\\s*=\\s*['\"][^'\"]*three(?:\\.min)?\\.js"
                    + "|/npm/three@"
                    + "|from\\s+['\"]three['\"])");

    private static final String THREE_JS_BOOTSTRAP =
            "<script src=\"https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.min.js\"></script>\n";

    private static final String PREVIEW_HEAD_CORE = """
            <link rel="preconnect" href="https://fonts.googleapis.com"/>
            <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin/>
            <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet"/>
            <script src="https://cdn.tailwindcss.com"></script>
            <script>
            tailwind.config = {
              theme: {
                extend: {
                  fontFamily: { sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'] },
                  colors: {
                    primary: {50:'#f0f9ff',100:'#e0f2fe',200:'#bae6fd',300:'#7dd3fc',400:'#38bdf8',500:'#0ea5e9',600:'#0284c7',700:'#0369a1',800:'#075985',900:'#0c4a6e'},
                    surface: { DEFAULT:'#ffffff', elevated:'#ffffff', sunken:'#f1f5f9' },
                  },
                },
              },
              darkMode: 'class',
            }
            </script>
            <style>
              *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
              html { -webkit-font-smoothing: antialiased; -moz-osx-font-smoothing: grayscale; height: 100%; color-scheme: light; }
              body {
                font-family: 'Inter', system-ui, -apple-system, sans-serif;
                color: #1a1a2e;
                background: #ffffff;
                line-height: 1.6;
                min-height: 100%;
              }
              body::-webkit-scrollbar { width: 0; height: 0; }
              body { scrollbar-width: none; }
              img { max-width: 100%; height: auto; }
              a { color: #0284c7; text-decoration: none; }
              a:hover { text-decoration: underline; }
              .wa-card { background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
              .wa-gradient { background: linear-gradient(135deg, #0ea5e9 0%, #6366f1 100%); color: #fff; }
              .wa-gradient-warm { background: linear-gradient(135deg, #f97316 0%, #ec4899 100%); color: #fff; }
              .wa-gradient-fresh { background: linear-gradient(135deg, #10b981 0%, #0ea5e9 100%); color: #fff; }
              canvas { display: block; max-width: 100%; }
            </style>
            """;

    private static final String PREVIEW_IFRAME_BOOTSTRAP = """
            <script>/* __reactorPreviewIframeBootstrap */
            (function () {
              function syncViewport() {
                var h = window.innerHeight || document.documentElement.clientHeight || 0;
                if (h > 0) {
                  document.documentElement.style.height = h + 'px';
                  document.body.style.minHeight = h + 'px';
                }
                window.dispatchEvent(new Event('resize'));
              }
              syncViewport();
              window.addEventListener('load', function () {
                syncViewport();
                setTimeout(syncViewport, 0);
                setTimeout(syncViewport, 120);
                setTimeout(syncViewport, 400);
              });
              window.addEventListener('resize', syncViewport);
              if (window.ResizeObserver) {
                try {
                  new ResizeObserver(syncViewport).observe(document.documentElement);
                } catch (e) {}
              }
            })();
            </script>
            """;

    private HtmlPreviewSanitizer() {
    }

    /**
     * Build preview HTML aligned with LeAgent:
     * store-raw semantics when allowJs=true; strip scripts when false;
     * inject host shell for bare/utility pages.
     */
    public static String buildPreviewHtml(String html, boolean allowJs) {
        if (StringUtils.isBlank(html)) {
            return html;
        }
        String body = allowJs ? html : sanitize(html);
        String out;
        if (containsIgnoreCase(body, "<html")) {
            out = injectPreviewAssetsIntoFullDocument(body);
        } else {
            out = wrapHtmlFragment(body);
        }
        if (allowJs) {
            out = injectPreviewIframeBootstrap(out);
        } else {
            out = ensurePreviewCsp(out, false);
        }
        return out;
    }

    public static String sanitize(String html) {
        if (StringUtils.isBlank(html)) {
            return html;
        }
        String out = html;
        out = SCRIPT_TAG.matcher(out).replaceAll("");
        out = EVENT_HANDLER.matcher(out).replaceAll("");
        out = JS_URL.matcher(out).replaceAll("$1=$2#$2");
        out = META_REFRESH.matcher(out).replaceAll("");
        return out;
    }

    /**
     * Optional CSP meta for documents that lack one.
     * When allowJs=true, scripts may run (cdn + unsafe-inline); when false, script-src none.
     */
    public static String ensurePreviewCsp(String html) {
        return ensurePreviewCsp(html, false);
    }

    public static String ensurePreviewCsp(String html, boolean allowJs) {
        if (StringUtils.isBlank(html)) {
            return html;
        }
        if (containsIgnoreCase(html, "content-security-policy")) {
            return html;
        }
        String scriptSrc = allowJs
                ? "script-src 'self' 'unsafe-inline' 'unsafe-eval' https: blob: data:;"
                : "script-src 'none';";
        String cspMeta = "<meta http-equiv=\"Content-Security-Policy\" "
                + "content=\"default-src 'self' https: data: blob:; "
                + scriptSrc
                + " style-src 'self' 'unsafe-inline' https: data:; "
                + "img-src 'self' https: data: blob:; "
                + "font-src 'self' https: data:; "
                + "connect-src 'self' https: wss: blob: data:; "
                + "object-src 'none'; "
                + "base-uri 'none'; "
                + "frame-ancestors 'self';\">";
        int headIdx = indexOfIgnoreCase(html, "<head");
        if (headIdx >= 0) {
            int insertAt = html.indexOf('>', headIdx);
            if (insertAt > 0) {
                return html.substring(0, insertAt + 1) + "\n" + cspMeta + html.substring(insertAt + 1);
            }
        }
        if (!containsIgnoreCase(html, "<html")) {
            return "<!DOCTYPE html><html><head>" + cspMeta
                    + "</head><body>" + html + "</body></html>";
        }
        return cspMeta + html;
    }

    public static boolean documentProvidesTailwind(String html) {
        return containsIgnoreCase(html, "cdn.tailwindcss.com");
    }

    public static boolean documentHasAuthoredStyles(String html) {
        return authoredStyleCharCount(html) >= AUTHORED_STYLE_MIN_CHARS
                || hasNonFontStylesheetLink(html);
    }

    public static boolean shouldInjectPreviewShell(String html) {
        if (documentProvidesTailwind(html)) {
            return false;
        }
        return !documentHasAuthoredStyles(html);
    }

    public static boolean htmlAlreadyLoadsThree(String html) {
        return html != null && THREE_SCRIPT_HINT.matcher(html).find();
    }

    public static String threeBootstrapForHtml(String html) {
        return htmlAlreadyLoadsThree(html) ? "" : THREE_JS_BOOTSTRAP;
    }

    public static String previewHeadAssets(String html) {
        return PREVIEW_HEAD_CORE + threeBootstrapForHtml(html);
    }

    public static String injectPreviewAssetsIntoFullDocument(String html) {
        if (StringUtils.isBlank(html) || !containsIgnoreCase(html, "<html")) {
            return html;
        }
        if (!shouldInjectPreviewShell(html)) {
            // Still inject THREE when shell is skipped but page doesn't load it
            // and looks like a bare WebGL page that may need the global.
            if (!htmlAlreadyLoadsThree(html) && shouldInjectThreeOnly(html)) {
                return insertAssetsInDocumentHead(html, THREE_JS_BOOTSTRAP);
            }
            return html;
        }
        return insertAssetsInDocumentHead(html, previewHeadAssets(html));
    }

    public static String wrapHtmlFragment(String body) {
        return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n"
                + "<meta charset=\"utf-8\"/>\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>\n"
                + previewHeadAssets(body) + "\n"
                + "</head>\n<body>\n"
                + body + "\n"
                + "</body>\n</html>";
    }

    /** When shell is skipped (authored CSS), still offer THREE for bare WebGL pages. */
    private static boolean shouldInjectThreeOnly(String html) {
        if (html == null) {
            return false;
        }
        String low = html.toLowerCase();
        return low.contains("webgl") || low.contains("three.") || low.contains("window.three");
    }

    private static String injectPreviewIframeBootstrap(String html) {
        if (html != null && html.contains("__reactorPreviewIframeBootstrap")) {
            return html;
        }
        int bodyClose = indexOfIgnoreCase(html, "</body");
        if (bodyClose >= 0) {
            return html.substring(0, bodyClose) + PREVIEW_IFRAME_BOOTSTRAP + html.substring(bodyClose);
        }
        return html + PREVIEW_IFRAME_BOOTSTRAP;
    }

    private static String insertAssetsInDocumentHead(String html, String assets) {
        int headClose = indexOfIgnoreCase(html, "</head");
        if (headClose >= 0) {
            return html.substring(0, headClose) + "\n" + assets + "\n" + html.substring(headClose);
        }
        int headOpen = indexOfIgnoreCase(html, "<head");
        if (headOpen >= 0) {
            int insertAt = html.indexOf('>', headOpen);
            if (insertAt > 0) {
                return html.substring(0, insertAt + 1) + "\n" + assets + "\n" + html.substring(insertAt + 1);
            }
        }
        int htmlOpen = indexOfIgnoreCase(html, "<html");
        if (htmlOpen >= 0) {
            int insertAt = html.indexOf('>', htmlOpen);
            if (insertAt > 0) {
                return html.substring(0, insertAt + 1)
                        + "\n<head>\n" + assets + "\n</head>\n"
                        + html.substring(insertAt + 1);
            }
        }
        return html;
    }

    private static int authoredStyleCharCount(String html) {
        if (html == null) {
            return 0;
        }
        int total = 0;
        Matcher m = STYLE_BLOCK.matcher(html);
        while (m.find()) {
            String block = m.group(1) == null ? "" : m.group(1);
            total += block.replaceAll("\\s+", "").length();
        }
        return total;
    }

    private static boolean hasNonFontStylesheetLink(String html) {
        if (html == null) {
            return false;
        }
        Matcher m = LINK_TAG.matcher(html);
        while (m.find()) {
            String tag = m.group(0);
            if (!STYLESHEET_REL.matcher(tag).find()) {
                continue;
            }
            Matcher hrefM = HREF_ATTR.matcher(tag);
            if (!hrefM.find()) {
                continue;
            }
            String href = firstNonNull(hrefM.group(1), hrefM.group(2), hrefM.group(3));
            if (href == null) {
                continue;
            }
            href = href.trim().toLowerCase();
            if (href.isEmpty() || href.startsWith("data:")) {
                continue;
            }
            if (href.contains("fonts.googleapis.com") || href.contains("fonts.gstatic.com")) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static String firstNonNull(String a, String b, String c) {
        if (a != null) {
            return a;
        }
        if (b != null) {
            return b;
        }
        return c;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack == null ? -1 : haystack.toLowerCase().indexOf(needle.toLowerCase());
    }
}
