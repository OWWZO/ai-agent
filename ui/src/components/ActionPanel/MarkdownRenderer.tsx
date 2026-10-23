import ReactMarkdown from 'react-markdown';
import gfm from 'remark-gfm';
import { memo, useEffect, useMemo, useRef, useState, type ComponentProps, type ReactNode } from 'react';
import { Empty, EmptyHeader, EmptyTitle } from '@/components/ui/empty';
import { cn } from '@/lib/utils';
import { usePanelContext } from './PanelProvider';
import { containsMath, loadMathPlugins, type MathPlugins } from '@/lib/lazy/math';
import { createCodeBlock } from './MarkdownCodeBlock';
import { MarkdownImagePreview, MarkdownMediaPlayer } from './MarkdownMedia';
import {
  createMarkdownArtifactHrefResolver,
  resolveMarkdownMediaKind,
  rewriteMarkdownArtifactRefs,
} from '@/utils/markdownArtifacts';
import {
  collectLinkDefinitions,
  splitMarkdownBlocks,
  withLinkDefinitions,
} from '@/lib/markdownBlocks';

/** `ReactMarkdown` 的 props 形状：块级组件只取插件相关的三个字段。 */
type ReactMarkdownProps = ComponentProps<typeof ReactMarkdown>;

/** react-markdown 默认 pre>code；我们的 fence 自带外壳，必须拆掉外层 pre，否则套住 Shiki 的 pre 会重复渲染 HTML 源码 */
const MarkdownPre: ReactorType.FC<{ children?: ReactNode }> = ({ children }) => (
  <>{children}</>
);

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
  // 产物 lookup 在这里建一次，供该 components 下的所有链接 / 图片复用。
  // 每个 `<a>` / `<img>` 各自重建是 O(链接 × 文件) —— 正是 markdownArtifacts
  // 里刚修掉的热点，不能在渲染层又引回来。
  const resolveHref = createMarkdownArtifactHrefResolver(artifactFiles);

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
