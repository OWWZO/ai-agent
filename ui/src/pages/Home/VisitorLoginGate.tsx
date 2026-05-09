import { useState } from "react";

type VisitorLoginGateProps = {
  loading?: boolean;
  onSubmit: (username: string) => void;
};

/**
 * 当前浏览器首次进入时的独立登录界面。
 */
export default function VisitorLoginGate(props: VisitorLoginGateProps) {
  const [username, setUsername] = useState("");

  return (
    <div className="flex min-h-screen w-full items-center justify-center bg-[var(--page-gradient)] px-6 text-foreground">
      <div className="w-full max-w-[520px] rounded-[36px] border border-[var(--chat-border)] bg-[var(--chat-surface)]/94 p-8 shadow-[var(--shadow-md)] backdrop-blur-md md:p-10">
        <div className="mb-8 text-center">
          <div className="mb-3 inline-flex rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-4 py-1 text-[12px] text-[var(--chat-text-soft)]">
            登录
          </div>
          <h1
            className="mb-3 text-[34px] font-medium leading-[1.06] text-[var(--chat-text)] md:text-[40px]"
            style={{ fontFamily: "var(--font-sans)" }}
          >
            输入用户名后进入工作台
          </h1>
          <p className="text-[14px] leading-[1.7] text-[var(--chat-text-soft)]">
            不需要注册，只区分当前浏览器里的使用者。
          </p>
        </div>

        <div className="flex flex-col gap-4">
          <input
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            placeholder="请输入用户名"
            className="h-14 rounded-2xl border border-[var(--chat-border)] bg-white px-4 text-[15px] text-[var(--chat-text)] outline-none transition-colors focus:border-[var(--chat-border-strong)]"
          />
          <button
            type="button"
            disabled={props.loading === true || username.trim().length === 0}
            onClick={() => props.onSubmit(username.trim())}
            className="inline-flex h-12 items-center justify-center rounded-2xl bg-[var(--chat-text)] px-4 text-[14px] text-[var(--chat-surface)] transition-colors hover:bg-[var(--chat-text)]/90 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {props.loading === true ? "提交中..." : "进入对话"}
          </button>
        </div>
      </div>
    </div>
  );
}
