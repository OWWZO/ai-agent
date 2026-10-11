import { Component, type ErrorInfo, type ReactNode } from "react";

interface PreviewErrorBoundaryProps {
  children: ReactNode;
  /** 出错时展示的内容（通常是 DocumentFallback）。 */
  fallback: ReactNode;
  /**
   * 重置标识：文档切换（路径 / 大小 / 类型变化）时传入新值，
   * 边界会自动清掉错误态，避免上一次的失败把新文档也一起挡住。
   */
  resetKey: string;
  /** 出错时打日志用的上下文名，例如 "DOCX"。 */
  label?: string;
}

interface PreviewErrorBoundaryState {
  hasError: boolean;
}

/**
 * 文档预览（docx / xlsx）的错误边界。
 *
 * 第三方渲染器（docx-preview / WASM viewer）在解析损坏文件时可能直接在渲染期抛错，
 * 只靠 try/catch 包不住 React 渲染链路里的异常，必须用错误边界兜住，
 * 否则整个预览面板会白屏。
 */
export class PreviewErrorBoundary extends Component<
  PreviewErrorBoundaryProps,
  PreviewErrorBoundaryState
> {
  state: PreviewErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): PreviewErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error(
      `[PreviewErrorBoundary] ${this.props.label ?? "预览"}组件渲染失败`,
      error,
      info.componentStack ?? ""
    );
  }

  componentDidUpdate(previousProps: Readonly<PreviewErrorBoundaryProps>) {
    if (this.state.hasError && previousProps.resetKey !== this.props.resetKey) {
      this.setState({ hasError: false });
    }
  }

  render() {
    return this.state.hasError ? this.props.fallback : this.props.children;
  }
}

export default PreviewErrorBoundary;
