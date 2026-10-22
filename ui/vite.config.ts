import { loadEnv, type Plugin } from 'vite';
// vitest/config 的 defineConfig 是 vite 的超集，多了 test 字段；vite 本身照旧读取同一份配置。
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { createReadStream, existsSync, mkdirSync, copyFileSync, readdirSync } from 'node:fs';
import path from 'path';
import tailwindcss from '@tailwindcss/vite';
import { createToolProxyConfig } from './toolProxy';

/**
 * pdf.js 的 cMaps / standard_fonts 资源随 pdfjs-dist 发布，但不在 public/ 下。
 * 这里把它们挂到 `${BASE_URL}pdfjs/*`：
 * - dev：中间件直接读 node_modules，无需拷贝
 * - build：复制进产物目录
 * 这样既能渲染「未嵌入字体的中文 PDF」（ReportLab STSong-Light 之类），
 * 又不用把 170+ 个资源文件提交进仓库。
 */
function pdfjsStaticAssets(): Plugin {
  const pdfjsRoot = path.resolve(__dirname, 'node_modules/pdfjs-dist');
  const assetDirs = ['cmaps', 'standard_fonts'];
  const mount = '/pdfjs';
  let resolvedOutDir = path.resolve(__dirname, 'dist');
  let isBuild = false;

  return {
    name: 'pdfjs-static-assets',
    configResolved(config) {
      // closeBundle 在 dev server 关闭 / vitest 结束时同样会触发，此时并没有产物目录，
      // 必须只认真正的 build，否则会按测试态解析出的 outDir 写出野目录。
      isBuild = config.command === 'build';
      resolvedOutDir = path.resolve(config.root || __dirname, config.build?.outDir || 'dist');
    },
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        try {
          const url = String(req.url || '').split('?')[0];
          if (!url.startsWith(mount + '/')) return next();
          const rel = decodeURIComponent(url.slice(mount.length + 1));
          const file = path.join(pdfjsRoot, rel);
          if (!file.startsWith(pdfjsRoot) || !existsSync(file)) return next();
          res.setHeader('Content-Type', 'application/octet-stream');
          res.setHeader('Cache-Control', 'public, max-age=86400');
          createReadStream(file).pipe(res);
        } catch {
          next();
        }
      });
    },
    closeBundle() {
      if (!isBuild) return;
      try {
        for (const dir of assetDirs) {
          const from = path.join(pdfjsRoot, dir);
          if (!existsSync(from)) continue;
          const to = path.join(resolvedOutDir, 'pdfjs', dir);
          mkdirSync(to, { recursive: true });
          for (const entry of readdirSync(from)) {
            copyFileSync(path.join(from, entry), path.join(to, entry));
          }
        }
      } catch {
        // 资源复制失败不应让构建失败：PDF 仍可渲染，只是缺少 cMap / 标准字体的兜底
      }
    },
  };
}

export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [
      react(),
      tailwindcss(),
      pdfjsStaticAssets()
    ],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
        crypto: 'crypto-browserify',
        // `use-sync-external-store` 未安装，用本地 shim 顶上（zustand v4 依赖它）。
        // 必须把带 `.js` 的写法也列出来：Vite 的字符串 alias 是前缀匹配，
        // 只写无后缀那条时，`.../shim/with-selector.js` 会被 `.../shim` 吃掉前缀，
        // 拼成 `shim.ts/with-selector.js` 这种不存在的路径。
        'use-sync-external-store/shim/with-selector.js': path.resolve(
          __dirname,
          'src/shims/use-sync-external-store/with-selector.ts'
        ),
        'use-sync-external-store/shim/with-selector': path.resolve(
          __dirname,
          'src/shims/use-sync-external-store/with-selector.ts'
        ),
        'use-sync-external-store/shim/index.js': path.resolve(
          __dirname,
          'src/shims/use-sync-external-store/shim.ts'
        ),
        'use-sync-external-store/shim': path.resolve(
          __dirname,
          'src/shims/use-sync-external-store/shim.ts'
        ),
      },
    },
    css: {preprocessorOptions: {less: {javascriptEnabled: true},},},
    optimizeDeps: {
      // ⚠️ 必须限制扫描入口。Vite 默认会爬取项目里**所有 .html**，实测它把
      // `dist-doc/index.html`、`docs/superpowers/decks/**/slides/*.html`（演示文稿）、
      // `src/assets/relayFonts/RelayIcon.html` 也当成依赖扫描入口 —— 这些都不是应用的一部分。
      // 只留真正的应用入口 index.html。
      entries: ['index.html'],
      // ⚠️ 不要把 lucide-react / radix-ui 加回 exclude（barrel 包，1706 条 re-export，
      // 排除后 dev 下浏览器要逐个请求上千个模块）。
      //
      // ⚠️ 也不要试图用 exclude 压冷启动 —— 实测过，不划算。
      // 冷启动的耗时是 esbuild **按模块数**打包 43 个依赖造成的，实测 `Dependencies
      // bundled in ~100s`，其中模块数大头是：antd 1871、lucide-react 1967、echarts 700、
      // mermaid 204。前两个是运行时真正需要的，压不掉。
      // 能排除的（three / shiki 实测 OK 400ms / 599ms）都是单文件或小库，省不了多少；
      // 想排除 mermaid 会直接坏掉：它的依赖 @braintree/sanitize-url 是 CJS，排除后浏览器报
      // `does not provide an export named 'sanitizeUrl'`，图渲染不出来。
      // 冷启动是一次性的（改 vite.config.ts / 装依赖才会重来），之后热启动页面加载仅 479ms。
      exclude: ['clsx', 'nanoid', 'tailwind-merge'],
    },
    server: {
      // 修改为监听所有接口，而不是特定主机名
      host: '0.0.0.0',
      port: 3000,
      allowedHosts: true,
      proxy: {
        '/web': {
          target: env.SERVICE_BASE_URL,
          changeOrigin: true,
        },
        '/tool': createToolProxyConfig(env.REACTOR_TOOL_BASE_URL),
      },
    },
    define: {
      // 一定要序列化，否则打包时会报错
      SERVICE_BASE_URL: JSON.stringify(env.SERVICE_BASE_URL),
      REACTOR_TOOL_BASE_URL: JSON.stringify(env.REACTOR_TOOL_BASE_URL || ''),
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
      minify: 'terser' as const,
    },
    worker: {
      // `@extend-ai/react-xlsx` 内部带 web worker 且 worker 入口自身有动态 import。
      // vite 默认的 iife worker 格式不支持代码分割，会直接构建失败；
      // 现代浏览器都支持 module worker，切到 es 即可（本项目没有其他 worker，无回归面）。
      format: 'es' as const,
      rollupOptions: {
        // 该 worker 入口依赖「import 后注册 message 监听」的副作用，
        // 摇树会把生产 worker 子构建摇成 0B，只关闭 worker 构建的摇树。
        treeshake: false,
      },
    },
    // openui 的 @FormatDate/@AddDays 等函数按本地时区格式化日期，单测里断言的是 UTC 结果。
    // 固定 TZ=UTC 让测试与机器时区无关（等价于上游 openui 的 vitest.config.mts）。
    test: {
      env: { TZ: 'UTC' },
    },
  }
});
