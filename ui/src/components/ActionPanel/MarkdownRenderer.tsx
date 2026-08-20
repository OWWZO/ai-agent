import ReactMarkdown from 'react-markdown';
import gfm from 'remark-gfm';
import { memo, useEffect, useMemo, useRef, useState, type ComponentProps, type ReactNode } from 'react';
import { Empty, Image, Modal } from 'antd';
import classNames from 'classnames';
import { Expand, X } from 'lucide-react';
import { usePanelContext } from './PanelProvider';
import mermaid from 'mermaid';
import {
  DiffCodeFence,
  KimiCodeFence,
} from '@/components/Dialogue/markdown/KimiCodeFence';
import {
  resolveMarkdownArtifactHref,
  resolveMarkdownMediaKind,
  rewriteMarkdownArtifactRefs,
} from '@/utils/markdownArtifacts';
import type { BundledLanguage } from 'shiki';
import { bundledLanguages } from 'shiki';

/** 终答 Markdown 内嵌图：点击 antd Image 预览放大 */
const MarkdownImagePreview: ReactorType.FC<{
  src: string;
  alt?: string;
}> = ({ src, alt }) => {
  if (!src) {
    return null;
  }
  return (
    <Image
      src={src}
      alt={alt || '图片'}
      className="markdown-media-image"
      rootClassName="markdown-media-image-root"
      style={{
        maxWidth: 'min(360px, 100%)',
        maxHeight: 280,
        width: 'auto',
        height: 'auto',
        objectFit: 'contain',
        borderRadius: '0.65rem',
        cursor: 'zoom-in'
      }}
      preview={{mask: '点击放大',}}
    />
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
      <Modal
        open={open}
        onCancel={() => setOpen(false)}
        footer={null}
        centered
        width={kind === 'video' ? 'min(920px, 96vw)' : 'min(520px, 94vw)'}
        destroyOnClose
        className="markdown-media-preview-modal"
        closeIcon={<X className="h-4 w-4" />}
        title={title || (kind === 'video' ? '视频预览' : '音频预览')}
      >
        {kind === 'video' ? (
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
        )}
      </Modal>
    </>
  );
};

const Mermaid: ReactorType.FC = (props) => {
  const { children } = props;
  const ref = useRef(null);
  useEffect(() => {
    if (ref.current) {
      mermaid.contentLoaded();
    }
  }, [children]);
  return (
    <div className="mermaid" ref={ref}>
      {children}
    </div>
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

const CodeBlock: ReactorType.FC<{
  inline?: boolean;
  className?: string;
  children?: unknown;
}> = ({ inline, className, children, ...rest }) => {
  // Streamdown 用 data-block 标记围栏；react-markdown 用 inline=false
  const isBlock =
    !inline || Object.prototype.hasOwnProperty.call(rest, "data-block");
  const match = /language-(\w+)/.exec(className || "");
  const trimmed = childrenToText(children).replace(/\n$/, "");

  if (match?.[1] === "mermaid") {
    return <Mermaid>{children as ReactNode}</Mermaid>;
  }

  if (isBlock && match?.[1] === "diff") {
    return <DiffCodeFence code={trimmed} />;
  }

  if (isBlock && match) {
    const rawLang = match[1];
    const safeLanguage = (
      rawLang in bundledLanguages ? rawLang : "text"
    ) as BundledLanguage;
    return <KimiCodeFence code={trimmed} language={safeLanguage} />;
  }

  // 无 language 的多行 fence：仍按代码块渲染，避免掉进行内 chip
  if (isBlock && trimmed.includes("\n")) {
    return <KimiCodeFence code={trimmed} language="text" />;
  }

  return <code className={cnInlineCode(className)}>{children as ReactNode}</code>;
};

/** react-markdown 默认 pre>code；我们的 fence 自带外壳，必须拆掉外层 pre，否则套住 Shiki 的 pre 会重复渲染 HTML 源码 */
const MarkdownPre: ReactorType.FC<{ children?: ReactNode }> = ({
  children,
}) => <>{children}</>;

function cnInlineCode(className?: string) {
  return classNames('kimi-inline-code', className);
}

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

  const markdownComponents = useMemo(() => {
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
      code: CodeBlock,
      a: MarkdownLink,
      img: MarkdownImage,
    };
  }, [artifactFiles]);

  useEffect(() => {
    if (!isStreaming || !normalizedContent) return;
    const now = Date.now();
    if (now - lastScrollAtRef.current < 80) return;
    lastScrollAtRef.current = now;
    scrollToBottom?.();
  }, [normalizedContent, scrollToBottom, isStreaming]);

  if (!normalizedContent) {
    return <Empty description="暂无内容" className='mx-auto mt-32' />;
  }

  // 流式/落定统一走 ReactMarkdown + KimiCodeFence(tokens)。
  // 不再用 Streamdown 自带 Shiki：会与自定义 code 叠出「高亮 + HTML 源码」两段。
  return (
    <div className={classNames('w-full markdown-body kimi-md', className)}>
      <ReactMarkdown
        remarkPlugins={[gfm]}
        components={markdownComponents}
      >
        {normalizedContent}
      </ReactMarkdown>
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
