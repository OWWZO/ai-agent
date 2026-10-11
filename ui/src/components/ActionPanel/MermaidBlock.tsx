import React, { useEffect, useRef, useState } from "react";
import { loadMermaid } from "@/lib/lazy/mermaid";
import { cn } from "@/lib/utils";

interface MermaidBlockProps {
  /** mermaid 源码文本。 */
  children?: React.ReactNode;
  className?: string;
}

/**
 * Mermaid 图形渲染块。
 *
 * 从 `MarkdownRenderer` 里抽出，供 Markdown 预览与「代码预览遇到 mermaid 语言」共用：
 * 以 `.mermaid` 容器 + `mermaid.contentLoaded()` 的方式交给 mermaid 自行扫描渲染。
 */
const MermaidBlock: ReactorType.FC<MermaidBlockProps> = (props) => {
  const { children, className } = props;
  const ref = useRef<HTMLDivElement | null>(null);
  const [loadError, setLoadError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoadError(false);

    void loadMermaid()
      .then(({ default: mermaid }) => {
        if (cancelled || !ref.current) return;
        mermaid.contentLoaded();
      })
      .catch((error) => {
        if (!cancelled) {
          console.error("加载 Mermaid 失败", error);
          setLoadError(true);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [children]);

  if (loadError) {
    return <pre className={cn("overflow-auto", className)}>{children}</pre>;
  }

  return (
    <div className={cn("mermaid", className)} ref={ref}>
      {children}
    </div>
  );
};

MermaidBlock.displayName = "MermaidBlock";

export default React.memo(MermaidBlock);
