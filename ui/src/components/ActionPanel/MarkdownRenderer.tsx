import ReactMarkdown from 'react-markdown';
import gfm from 'remark-gfm';
import { useEffect, useRef } from 'react';
import { Empty } from 'antd';
import classNames from 'classnames';
import { usePanelContext } from './PanelProvider';
import mermaid from 'mermaid';
import { TerminalStreamText } from "@/components/ai-elements/terminal-stream";
import {
  CodeBlock as ShadcnCodeBlock,
  CodeBlockCopyButton,
} from '@/components/ai-elements/code-block';
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

  useEffect(() => {
    if (markDownContent) {
      scrollToBottom?.();
    }
  }, [markDownContent, scrollToBottom]);

  if (!markDownContent) {
    return <Empty description="暂无内容" className='mx-auto mt-32' />;
  }

  if (isStreaming) {
    return (
      // During streaming, avoid markdown parsing/re-layout costs.
      // We only need terminal-like incremental reveal for perceived speed.
      <TerminalStreamText
        className={classNames("w-full", className)}
        text={markDownContent}
        isStreaming={true}
      />
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

export default MarkdownRenderer;
