import ReactMarkdown from 'react-markdown';
import gfm from 'remark-gfm';
import { memo, useEffect, useRef } from 'react';
import { Empty } from 'antd';
import classNames from 'classnames';
import { usePanelContext } from './PanelProvider';
import mermaid from 'mermaid';
import {
  CodeBlock as ShadcnCodeBlock,
  CodeBlockCopyButton,
} from '@/components/ai-elements/code-block';
import { MessageResponse } from '@/components/ai-elements/message';
import type { BundledLanguage } from 'shiki';
import { bundledLanguages } from 'shiki';

const Mermaid: GenieType.FC = (props) => {
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

const CodeBlock: GenieType.FC<{
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

const MarkdownRenderer: GenieType.FC<{
  markDownContent?: string;
  isStreaming?: boolean;
}> = (props) => {
  const { markDownContent, className, isStreaming = false } = props;

  const { scrollToBottom } = usePanelContext() || {};
  const lastScrollAtRef = useRef<number>(0);

  useEffect(() => {
    if (!isStreaming || !markDownContent) return;
    const now = Date.now();
    if (now - lastScrollAtRef.current < 80) return;
    lastScrollAtRef.current = now;
    scrollToBottom?.();
  }, [markDownContent, scrollToBottom, isStreaming]);

  if (!markDownContent) {
    return <Empty description="暂无内容" className='mx-auto mt-32' />;
  }

  if (isStreaming) {
    return (
      <div className={classNames('w-full markdown-body', className)}>
        <MessageResponse
          isStreaming
          showStreamingCursor={false}
          disableAutoScroll
        >
          {markDownContent}
        </MessageResponse>
      </div>
    );
  }

  return (
    <div className={classNames('w-full markdown-body', className)}>
      <ReactMarkdown remarkPlugins={[gfm]} components={{ code: CodeBlock }}>
        {markDownContent}
      </ReactMarkdown>
    </div>
  );
};

export default memo(
  MarkdownRenderer,
  (prevProps, nextProps) =>
    prevProps.markDownContent === nextProps.markDownContent &&
    prevProps.isStreaming === nextProps.isStreaming &&
    prevProps.className === nextProps.className
);
