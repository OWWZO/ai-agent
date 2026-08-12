import ReactMarkdown from 'react-markdown';
import gfm from 'remark-gfm';
import { memo, useEffect, useMemo, useRef, useState, type ComponentProps } from 'react';
import { Empty, Image, Modal } from 'antd';
import classNames from 'classnames';
import { Expand, X } from 'lucide-react';
import { usePanelContext } from './PanelProvider';
import mermaid from 'mermaid';
import {
  CodeBlock as ShadcnCodeBlock,
  CodeBlockCopyButton,
} from '@/components/ai-elements/code-block';
import { MessageResponse } from '@/components/ai-elements/message';
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

const CodeBlock: ReactorType.FC<{
  inline?: boolean;
}> = ({ inline, className, children }) => {
  const match = /language-(\w+)/.exec(className || '');

  if (match?.[1] === 'mermaid') {
    return <Mermaid>{children}</Mermaid>;
  }

  if (!inline && match) {
    const rawLang = match[1];
    const safeLanguage = (rawLang in bundledLanguages ? rawLang : 'text') as BundledLanguage;
    const codeString = Array.isArray(children)
      ? children.join('')
      : typeof children === 'string'
        ? children
        : String(children);

    return (
      <ShadcnCodeBlock code={codeString.trim()} language={safeLanguage}>
        <CodeBlockCopyButton />
      </ShadcnCodeBlock>
    );
  }

  return <code className={className}>{children}</code>;
};

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

  if (isStreaming) {
    return (
      <div className={classNames('w-full markdown-body', className)}>
        <MessageResponse
          isStreaming
          showStreamingCursor={false}
          disableAutoScroll
          components={markdownComponents}
        >
          {normalizedContent}
        </MessageResponse>
      </div>
    );
  }

  return (
    <div className={classNames('w-full markdown-body', className)}>
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
