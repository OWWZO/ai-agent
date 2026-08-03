import ReactMarkdown from 'react-markdown';
import gfm from 'remark-gfm';
import { memo, useEffect, useRef, type ComponentProps } from 'react';
import { Empty } from 'antd';
import classNames from 'classnames';
import { usePanelContext } from './PanelProvider';
import mermaid from 'mermaid';
import {
  CodeBlock as ShadcnCodeBlock,
  CodeBlockCopyButton,
} from '@/components/ai-elements/code-block';
import { MessageResponse } from '@/components/ai-elements/message';
import {
  normalizeMarkdownForDisplay,
  type MarkdownNormalizationScope,
} from '@/utils/markdown';
import type { BundledLanguage } from 'shiki';
import { bundledLanguages } from 'shiki';

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

// Markdown 中的链接统一在新标签页打开，避免打断当前对话或文档浏览。
const MarkdownLink = (props: unknown) => {
  const anchorProps = { ...(props as Record<string, unknown>) };
  delete anchorProps.node;

  return (
    <a
      {...(anchorProps as ComponentProps<'a'>)}
      target="_blank"
      rel="noreferrer"
    />
  );
};

const MarkdownRenderer: ReactorType.FC<{
  markDownContent?: string;
  isStreaming?: boolean;
  normalizationScope?: MarkdownNormalizationScope;
}> = (props) => {
  const {
    markDownContent,
    className,
    isStreaming = false,
    normalizationScope = 'default',
  } = props;
  const normalizedContent = normalizeMarkdownForDisplay(markDownContent, {
    scope: normalizationScope,
  });

  const { scrollToBottom } = usePanelContext() || {};
  const lastScrollAtRef = useRef<number>(0);

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
          components={{ a: MarkdownLink }}
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
        components={{
          code: CodeBlock,
          a: MarkdownLink,
        }}
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
    prevProps.normalizationScope === nextProps.normalizationScope &&
    prevProps.className === nextProps.className
);
