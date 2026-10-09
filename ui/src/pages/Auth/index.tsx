import LiquidGlassAuth from "./liquid-glass/LiquidGlassAuth";

type AuthPageProps = {
  mode: "login" | "register";
};

// 登录 / 注册统一走 Liquid Glass 视觉，路由契约（mode）保持不变
export default function AuthPage({ mode }: AuthPageProps) {
  return <LiquidGlassAuth mode={mode} />;
}
