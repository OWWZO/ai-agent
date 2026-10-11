import { defineConfig, loadEnv, type Plugin } from 'vite';
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
        'use-sync-external-store/shim': path.resolve(
          __dirname,
          'src/shims/use-sync-external-store/shim.ts'
        ),
        'use-sync-external-store/shim/with-selector': path.resolve(
          __dirname,
          'src/shims/use-sync-external-store/with-selector.ts'
        ),
      },
    },
    css: {preprocessorOptions: {less: {javascriptEnabled: true},},},
    optimizeDeps: {
      exclude: [
        'clsx',
        'nanoid',
        'radix-ui',
        'lucide-react',
        'tailwind-merge',
      ],
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
  }
});
