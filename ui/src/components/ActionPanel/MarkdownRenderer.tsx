import ReactMarkdown from 'react-markdown';
import gfm from 'remark-gfm';
import { useEffect, useRef } from 'react';
import { Empty } from 'antd';
import classNames from 'classnames';
import { usePanelContext } from './PanelProvider';
import mermaid from 'mermaid';
import { Streamdown } from 'streamdown';
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
      <div className={classNames('w-full markdown-body', className)}>
        <Streamdown className="ai-chat-markdown size-full [&>*:first-child]:mt-0 [&>*:last-child]:mb-0">
          {markDownContent}
        </Streamdown>
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

export default MarkdownRenderer;
