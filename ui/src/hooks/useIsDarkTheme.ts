import { useEffect, useState } from "react";

const DARK_CLASS = "dark";

function readIsDark(): boolean {
  if (typeof document === "undefined") return false;
  return document.documentElement.classList.contains(DARK_CLASS);
}

/**
 * 读取当前是否处于暗色主题。
 *
 * 本项目主题是**纯 CSS** 驱动的：`global.css` 定义了 `.dark` 变体，但仓库里没有任何
 * 运行时切换逻辑（没有 ThemeProvider / store）。所以这里只做两件事：
 * 1. 首次读取 `document.documentElement` 上的 `dark` class；
 * 2. 用 MutationObserver 监听该 class 变化，一旦将来接入切换开关就自动跟随。
 *
 * 与 `KimiCodeFence` 里 `document.documentElement.classList.contains("dark")` 的判定保持一致。
 */
export function useIsDarkTheme(): boolean {
  const [isDark, setIsDark] = useState<boolean>(readIsDark);

  useEffect(() => {
    if (typeof document === "undefined") return;
    const root = document.documentElement;
    // 同步一次：SSR / 首帧读取可能早于 class 落地。
    setIsDark(root.classList.contains(DARK_CLASS));

    if (typeof MutationObserver === "undefined") return;
    const observer = new MutationObserver(() => {
      setIsDark(root.classList.contains(DARK_CLASS));
    });
    observer.observe(root, { attributes: true, attributeFilter: ["class"] });
    return () => observer.disconnect();
  }, []);

  return isDark;
}

export default useIsDarkTheme;
