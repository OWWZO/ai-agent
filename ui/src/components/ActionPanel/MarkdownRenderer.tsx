import ReactMarkdown from 'react-markdown';
import gfm from 'remark-gfm';
import { useEffect, useRef, useState } from 'react';
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

  const renderedText = useStreamingText(markDownContent || '', Boolean(isStreaming));

  useEffect(() => {
    if (renderedText) {
      scrollToBottom?.();
    }
  }, [renderedText, scrollToBottom]);

  if (!markDownContent) {
    return <Empty description="暂无内容" className='mx-auto mt-32' />;
  }

  if (isStreaming) {
    return (
      <div className={classNames('w-full markdown-body', className)}>
        <Streamdown className="ai-chat-markdown size-full [&>*:first-child]:mt-0 [&>*:last-child]:mb-0">
          {renderedText}
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

/**
 * Streaming text hook (same idea as `MessageResponse`):
 * throttle updates using requestAnimationFrame + time gate,
 * so markdown parsing doesn't run on every chunk.
 */
function useStreamingText(text: string, isStreaming: boolean) {
  const [displayedText, setDisplayedText] = useState(text);
  const displayedRef = useRef(text);
  const targetRef = useRef(text);
  const frameRef = useRef<number | null>(null);
  const lastUpdateRef = useRef<number>(0);

  useEffect(() => {
    targetRef.current = text;

    if (!isStreaming) {
      displayedRef.current = text;
      setDisplayedText(text);
      if (frameRef.current) {
        cancelAnimationFrame(frameRef.current);
        frameRef.current = null;
      }
      return;
    }

    if (displayedRef.current.length > text.length) {
      displayedRef.current = text;
      setDisplayedText(text);
      return;
    }

    const tick = (timestamp: number) => {
      if (timestamp - lastUpdateRef.current < 12) {
        frameRef.current = requestAnimationFrame(tick);
        return;
      }
      lastUpdateRef.current = timestamp;

      const currentValue = displayedRef.current;
      const targetValue = targetRef.current;

      if (currentValue === targetValue) {
        frameRef.current = null;
        return;
      }

      const remaining = targetValue.length - currentValue.length;
      const chunkSize = Math.max(
        1,
        Math.min(
          remaining < 10 ? 1 : remaining < 50 ? 2 : remaining < 200 ? 4 : 8,
          Math.ceil(remaining / 20)
        )
      );
      const nextValue = targetValue.slice(0, currentValue.length + chunkSize);
      displayedRef.current = nextValue;
      setDisplayedText(nextValue);

      frameRef.current = requestAnimationFrame(tick);
    };

    if (!frameRef.current) {
      frameRef.current = requestAnimationFrame(tick);
    }

    return () => {
      if (frameRef.current) {
        cancelAnimationFrame(frameRef.current);
        frameRef.current = null;
      }
    };
  }, [isStreaming, text]);

  return displayedText;
}
