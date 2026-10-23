import ReactMarkdown from 'react-markdown';
import gfm from 'remark-gfm';
import { memo, useEffect, useMemo, useRef, useState, type ComponentProps, type ReactNode } from 'react';
import { ZoomableImage } from "@/components/ui/zoomable-image";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Empty, EmptyHeader, EmptyTitle } from '@/components/ui/empty';
import { cn } from '@/lib/utils';
import { Expand } from 'lucide-react';
import { usePanelContext } from './PanelProvider';
import MermaidBlock from './MermaidBlock';
import { containsMath, loadMathPlugins, type MathPlugins } from '@/lib/lazy/math';
import {
  DiffCodeFence,
  KimiCodeFence,
} from '@/components/Dialogue/markdown/KimiCodeFence';
import {
  resolveMarkdownArtifactHref,
  resolveMarkdownMediaKind,
  rewriteMarkdownArtifactRefs,
} from '@/utils/markdownArtifacts';
import {
  collectLinkDefinitions,
  splitMarkdownBlocks,
  withLinkDefinitions,
} from '@/lib/markdownBlocks';
import type { BundledLanguage } from 'shiki';

/** `ReactMarkdown` 的 props 形状：块级组件只取插件相关的三个字段。 */
type ReactMarkdownProps = ComponentProps<typeof ReactMarkdown>;

/** 终答 Markdown 内嵌图：点击放大预览 */
const MarkdownImagePreview: ReactorType.FC<{
  src: string;
  alt?: string;
}> = ({ src, alt }) => {
  if (!src) {
    return null;
  }
  return (
    <span className="markdown-media-image-root">
      <ZoomableImage
        src={src}
        alt={alt || '图片'}
        className="markdown-media-image"
        style={{
          maxWidth: 'min(360px, 100%)',
          maxHeight: 280,
          width: 'auto',
          height: 'auto',
          objectFit: 'contain',
          borderRadius: '0.65rem',
          cursor: 'zoom-in'
        }}
      />
    </span>
  );
};

/** 终答 Markdown 内嵌音视频：内联播放 + 放大预览 Modal */
const MarkdownMediaPlayer: ReactorType.FC<{
  kind: 'video' | 'audio';
  src: string;
  title?: string;
}> = ({ kind, src, title }) => {
  const [open, setOpen] = useState(false);
  if (!src) {
    return null;
  }

  return (
    <>
      <div className="markdown-media-player-wrap">
        {kind === 'video' ? (
          <video
            className="markdown-media-video"
            src={src}
            controls
            preload="metadata"
            title={title}
          />
        ) : (
          <audio
            className="markdown-media-audio"
            src={src}
            controls
            preload="metadata"
            title={title}
          />
        )}
        <button
          type="button"
          className="markdown-media-expand-btn"
          title="放大预览"
          aria-label="放大预览"
          onClick={() => setOpen(true)}
        >
          <Expand className="h-3.5 w-3.5" />
        </button>
      </div>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent
          className={cn(
            "markdown-media-preview-modal",
            kind === "video"
              ? "max-w-[min(920px,96vw)]"
              : "max-w-[min(520px,94vw)]"
          )}
        >
          <DialogHeader>
            <DialogTitle>
              {title || (kind === "video" ? "视频预览" : "音频预览")}
            </DialogTitle>
          </DialogHeader>
          {open ? (
            kind === "video" ? (
              <video
                className="markdown-media-video-preview"
                src={src}
                controls
                autoPlay
                preload="metadata"
              />
            ) : (
              <audio
                className="markdown-media-audio-preview"
                src={src}
                controls
                autoPlay
                preload="metadata"
              />
            )
          ) : null}
        </DialogContent>
      </Dialog>
    </>
  );
};

function childrenToText(children: unknown): string {
  if (children == null || typeof children === "boolean") {
    return "";
  }
  if (typeof children === "string" || typeof children === "number") {
    return String(children);
  }
  if (Array.isArray(children)) {
    return children.map(childrenToText).join("");
  }
  if (typeof children === "object" && "props" in children) {
    return childrenToText(
      (children as { props?: { children?: unknown } }).props?.children
    );
  }
  return "";
}

/**
 * 代码围栏渲染器工厂。
 *
 * 做成工厂而不是模块级常量，是因为 `streaming` 必须来自当前渲染上下文 ——
 * 流式期间要跳过 shiki 高亮与 Mermaid（对齐 ZCode
 * `enableSyntaxHighlighting={!renderStreaming}` / `renderMermaid={!renderStreaming}`）。
 */
const createCodeBlock = (streaming: boolean): ReactorType.FC<{
  inline?: boolean;
  className?: string;
  children?: unknown;
}> =>
  function CodeBlock({ inline, className, children, ...rest }) {
    // Streamdown 用 data-block 标记围栏；react-markdown 用 inline=false
    const isBlock =
      !inline || Object.prototype.hasOwnProperty.call(rest, "data-block");
    const match = /language-(\w+)/.exec(className || "");
    const trimmed = childrenToText(children).replace(/\n$/, "");

    // Mermaid 解析 + 渲染比 shiki 更重，流式时先按普通代码块展示。
    if (match?.[1] === "mermaid" && !streaming) {
      return <MermaidBlock>{children as ReactNode}</MermaidBlock>;
    }

    if (isBlock && match?.[1] === "diff") {
      return <DiffCodeFence code={trimmed} />;
    }

    if (isBlock && match) {
      const rawLang = match[1];
      const safeLanguage = rawLang as BundledLanguage;
      return (
        <KimiCodeFence
          code={trimmed}
          language={safeLanguage}
          streaming={streaming}
        />
      );
    }

    // 无 language 的多行 fence：仍按代码块渲染，避免掉进行内 chip
    if (isBlock && trimmed.includes("\n")) {
      return (
        <KimiCodeFence code={trimmed} language="text" streaming={streaming} />
      );
    }

    return (
      <code className={cnInlineCode(className)}>{children as ReactNode}</code>
    );
  };

/** react-markdown 默认 pre>code；我们的 fence 自带外壳，必须拆掉外层 pre，否则套住 Shiki 的 pre 会重复渲染 HTML 源码 */
const MarkdownPre: ReactorType.FC<{ children?: ReactNode }> = ({ children }) => (
  <>{children}</>
);

function cnInlineCode(className?: string) {
  return cn('kimi-inline-code', className);
}

/**
 * components 工厂。
 *
 * 提到模块级是为了让「已落定」与「流式」两套 components 各自**引用稳定** ——
 * 它们是块级 `memo` 的 prop，每次渲染新建对象会让所有块的 memo 全部失效。
 */
const buildMarkdownComponents = (
  artifactFiles: CHAT.TFile[] | undefined,
  isStreaming: boolean
) => {
  const resolveHref = (href?: string | null) =>
    resolveMarkdownArtifactHref(href, artifactFiles);

  // 链接统一新标签打开；相对文件名先走产物表解析。
  // 指向 mp4/mp3 等时内嵌 video/audio，避免只显示冷链接。
  const MarkdownLink = (linkProps: unknown) => {
    const anchorProps = { ...(linkProps as Record<string, unknown>) };
    delete anchorProps.node;
    const href = resolveHref(
      typeof anchorProps.href === 'string' ? anchorProps.href : undefined
    );
    const mediaKind = resolveMarkdownMediaKind(href);
    if ((mediaKind === 'video' || mediaKind === 'audio') && href) {
      const label =
        typeof anchorProps.children === 'string'
          ? anchorProps.children
          : undefined;
      return <MarkdownMediaPlayer kind={mediaKind} src={href} title={label} />;
    }
    return (
      <a
        {...(anchorProps as ComponentProps<'a'>)}
        href={href}
        target="_blank"
        rel="noreferrer"
      />
    );
  };

  const MarkdownImage = (imageProps: unknown) => {
    const imgProps = { ...(imageProps as Record<string, unknown>) };
    delete imgProps.node;
    const src = resolveHref(
      typeof imgProps.src === 'string' ? imgProps.src : undefined
    );
    const alt = typeof imgProps.alt === 'string' ? imgProps.alt : undefined;
    const mediaKind = resolveMarkdownMediaKind(src);
    if ((mediaKind === 'video' || mediaKind === 'audio') && src) {
      return <MarkdownMediaPlayer kind={mediaKind} src={src} title={alt} />;
    }
    return <MarkdownImagePreview src={src || ''} alt={alt} />;
  };

  return {
    pre: MarkdownPre,
    code: createCodeBlock(isStreaming),
    a: MarkdownLink,
    img: MarkdownImage,
  };
};

/**
 * 单个 Markdown 块。
 *
 * `memo` 是块级增量的关键：流式时只有最后一块内容在变，前面的块靠 memo 跳过
 * 整棵子树的重新解析与 reconcile。
 *
 * 所有 prop 都必须引用稳定：`components` 由 `buildMarkdownComponents` 工厂按
 * `artifactFiles` 缓存，remark / rehype 插件按 `mathPlugins` 缓存。
 * 任一 prop 每次渲染都新建对象，都会让所有块的 memo 全部失效。
 */
const MarkdownBlock = memo(function MarkdownBlock(props: {
  content: string;
  components: ReturnType<typeof buildMarkdownComponents>;
  remarkPlugins: ReactMarkdownProps['remarkPlugins'];
  rehypePlugins: ReactMarkdownProps['rehypePlugins'];
}) {
  const { content, components, remarkPlugins, rehypePlugins } = props;
  return (
    <ReactMarkdown
      remarkPlugins={remarkPlugins}
      rehypePlugins={rehypePlugins}
      components={components}
    >
      {content}
    </ReactMarkdown>
  );
});

const MarkdownRenderer: ReactorType.FC<{
  markDownContent?: string;
  isStreaming?: boolean;
  /** 本轮产物文件；相对路径 Markdown 引用据此解析为 preview/download URL */
  artifactFiles?: CHAT.TFile[];
}> = (props) => {
  const {
    markDownContent,
    className,
    isStreaming = false,
    artifactFiles,
  } = props;
  // Agent 输出按合法 Markdown 原样渲染；仅把相对文件名替换成产物 URL。
  const normalizedContent = rewriteMarkdownArtifactRefs(
    markDownContent || '',
    artifactFiles
  );

  const { scrollToBottom } = usePanelContext() || {};
  const lastScrollAtRef = useRef<number>(0);

  // 数学公式按需加载：只有内容里出现 `$...$` / `$$...$$` 时才拉取 katex（含样式）。
  const hasMath = useMemo(() => containsMath(normalizedContent), [normalizedContent]);
  const [mathPlugins, setMathPlugins] = useState<MathPlugins | null>(null);

  useEffect(() => {
    if (!hasMath || mathPlugins) return;
    let cancelled = false;
    void loadMathPlugins()
      .then((plugins) => {
        if (!cancelled) setMathPlugins(plugins);
      })
      .catch((error) => {
        // 公式插件加载失败不影响其余 markdown 渲染，退化成纯文本。
        console.error('加载数学公式插件失败', error);
      });
    return () => {
      cancelled = true;
    };
  }, [hasMath, mathPlugins]);

  const remarkPlugins = useMemo(
    () => (mathPlugins ? [gfm, ...mathPlugins.remarkPlugins] : [gfm]),
    [mathPlugins]
  );
  const rehypePlugins = useMemo(
    () => (mathPlugins ? [...mathPlugins.rehypePlugins] : []),
    [mathPlugins]
  );

  /**
   * 两套 components，**引用必须稳定**，否则每个块的 `memo` 都会失效。
   *
   * 区别只在 `code` 渲染器要不要跳过 shiki / Mermaid：已落定的块应该高亮，
   * 正在增长的最后一块才跳过 —— 流式时每帧重跑 TextMate 语法是纯浪费。
   */
  const settledComponents = useMemo(
    () => buildMarkdownComponents(artifactFiles, false),
    [artifactFiles]
  );
  const streamingComponents = useMemo(
    () => buildMarkdownComponents(artifactFiles, true),
    [artifactFiles]
  );

  useEffect(() => {
    if (!isStreaming || !normalizedContent) return;
    const now = Date.now();
    if (now - lastScrollAtRef.current < 80) return;
    lastScrollAtRef.current = now;
    scrollToBottom?.();
  }, [normalizedContent, scrollToBottom, isStreaming]);

  /**
   * 块级切分 + 链接引用定义收集。
   *
   * 必须在任何早退之前调用（hooks 不能条件执行）。
   *
   * 流式时整段重解析是 O(n²)：内容每帧增长，每帧都把**全部**文本再解析一遍
   * （实测每帧 +10 行、累计 400 行：整段 51.6ms/帧，块级 13.2ms/帧，3.9x）。
   *
   * 切块后每块是独立的 `ReactMarkdown`，配 `memo`：内容没变的块不会重解析，
   * 只有最后一块（正在增长的那块）会。
   *
   * 切分用 `streamdown` 的 `parseMarkdownIntoBlocks`，但它有个坑：块与块之间是
   * 独立文档，链接引用定义（`[ref]: url`）落在别的块里时，正文块的 `[文本][ref]`
   * 会退化成字面文本。所以要把收集到的定义追加回每个块 —— 定义段渲染为空，
   * 追加它不会有可见内容。脚注定义**不能**追加（会渲染出重复的脚注区）。
   */
  const blocks = useMemo(
    () => splitMarkdownBlocks(normalizedContent),
    [normalizedContent]
  );
  const linkDefs = useMemo(() => collectLinkDefinitions(blocks), [blocks]);

  if (!normalizedContent) {
    return (
      <div className="mx-auto mt-32">
        <Empty>
          <EmptyHeader>
            <EmptyTitle>暂无内容</EmptyTitle>
          </EmptyHeader>
        </Empty>
      </div>
    );
  }

  return (
    <div className={cn('w-full markdown-body kimi-md', className)}>
      {blocks.map((block, index) => (
        <MarkdownBlock
          key={index}
          content={withLinkDefinitions(block, linkDefs)}
          remarkPlugins={remarkPlugins}
          rehypePlugins={rehypePlugins}
          // 已落定的块要补上 shiki / Mermaid；只有正在增长的最后一块跳过。
          components={
            isStreaming && index === blocks.length - 1
              ? streamingComponents
              : settledComponents
          }
        />
      ))}
    </div>
  );
};

export default memo(
  MarkdownRenderer,
  (prevProps, nextProps) =>
    prevProps.markDownContent === nextProps.markDownContent &&
    prevProps.isStreaming === nextProps.isStreaming &&
    prevProps.className === nextProps.className &&
    prevProps.artifactFiles === nextProps.artifactFiles
);
