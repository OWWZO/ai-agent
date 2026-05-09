import LoadingSpinner from "@/components/LoadingSpinner";

/**
 * visitor bootstrap 进行中的独立加载界面。
 */
export default function VisitorBootstrapScreen() {
  return (
    <div className="flex min-h-screen w-full items-center justify-center bg-[var(--page-gradient)] px-6 text-foreground">
      <div className="w-full max-w-[420px] rounded-[32px] border border-[var(--chat-border)] bg-[var(--chat-surface)]/92 p-10 text-center shadow-[var(--shadow-sm)] backdrop-blur-md">
        <div className="mb-6 flex justify-center">
          <LoadingSpinner
            size="lg"
            color="rgba(255,255,255,0.92)"
            className="text-[var(--primary)]"
          />
        </div>
        <h1
          className="mb-3 text-[30px] font-medium leading-[1.08] text-[var(--chat-text)]"
          style={{ fontFamily: "var(--font-sans)" }}
        >
          正在进入工作台
        </h1>
        <p className="text-[14px] text-[var(--chat-text-soft)]">请稍候...</p>
      </div>
    </div>
  );
}
