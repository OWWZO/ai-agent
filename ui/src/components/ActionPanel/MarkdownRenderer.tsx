import ReactMarkdown from 'react-markdown';
import gfm from 'remark-gfm';
// import Frame from 'react-frame-component';
import { CopyOutlined } from '@ant-design/icons';
import SyntaxHighlighter from 'react-syntax-highlighter';
import { useEffect, useRef } from 'react';
import { useMemoizedFn } from 'ahooks';
import { Empty } from 'antd';
import { copyText, showMessage } from '@/utils';
import classNames from 'classnames';
import { usePanelContext } from './PanelProvider';
import mermaid from 'mermaid';

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
}> = ({inline, className, children, ...props}) => {
  const match = /language-(\w+)/.exec(className || '');

  const copy = useMemoizedFn(() => {
    copyText(children as string);
    showMessage()?.success('复制成功');
  });

  if (match && match[1] === 'mermaid') {
    return <Mermaid>{children}</Mermaid>;
  }

  return !inline && match ? (
    <div className='rounded-[8px] overflow-hidden'>
      <div className='flex justify-start items-center px-12 py-8 bg-[#f0f0f0] text-[12px] text-[#666] border-b-1 border-b-[#ddd]'>
        <div className="lang">{match[1]}</div>
        <div className='flex items-center cursor-pointer ml-auto' onClick={copy}>
          <CopyOutlined className='flex items-center cursor-pointer' /> <span style={{marginLeft: 4}}>复制</span>
        </div>
      </div>
      <SyntaxHighlighter
        {...props}
        children={children as string}
        language={match[1]}
        PreTag="div"
        wrapLongLines={true}
        wrapLines={false}
        customStyle={{ padding: 16 }}
      />
    </div>
  ) : (
    <code {...props} className={className}>
      {children}
    </code>
  );
};
const MarkdownRenderer: GenieType.FC<{
  markDownContent?: string;
}> = (props) => {
  const { markDownContent, className } = props;

  const { scrollToBottom } = usePanelContext() || {};

  useEffect(() => {
    if (markDownContent) {
      scrollToBottom?.();
    }
  }, [markDownContent, scrollToBottom]);

  if (!markDownContent) {
    return <Empty description="暂无内容" className='mx-auto mt-32' />;
  }

  return <div className={classNames('w-full markdown-body', className)}>
    <ReactMarkdown remarkPlugins={[gfm]} components={{ code: CodeBlock }}>
      {markDownContent}
    </ReactMarkdown>
  </div>;
};

export default MarkdownRenderer;