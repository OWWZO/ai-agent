import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";

import {
  authApi,
  loadCurrentUser,
  type RegisterPayload,
} from "@/services/auth";
import { setAuthSession } from "@/stores/auth";
import { normalizeReturnUrl, ROUTES } from "@/router/routes";

import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import "./liquid-glass.css";

type LiquidGlassAuthProps = {
  mode: "login" | "register";
};

function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : "请求失败，请稍后重试";
}

function buildAuthPath(path: string, returnUrl: string, registered = false) {
  const params = new URLSearchParams({ returnUrl });
  if (registered) {
    params.set("registered", "1");
  }
  return `${path}?${params.toString()}`;
}

export default function LiquidGlassAuth({ mode }: LiquidGlassAuthProps) {
  const [loginName, setLoginName] = useState("");
  const [nickname, setNickname] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const isRegister = mode === "register";
  const returnUrl = normalizeReturnUrl(searchParams.get("returnUrl"));

  const inputClassName =
    "border-white/40 bg-white/10 placeholder:text-card-foreground/50 text-card-foreground py-3 focus:ring-2 focus:ring-blue-400 focus:border-blue-400 focus:bg-white/15 transition-all duration-200";

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setErrorMessage("");

    if (isRegister && password !== confirmPassword) {
      setErrorMessage("两次输入的密码不一致");
      return;
    }

    setIsLoading(true);

    try {
      if (isRegister) {
        const payload: RegisterPayload = {
          loginName: loginName.trim(),
          password,
          nickname: nickname.trim() || "",
        };
        const session = await authApi.register(payload);
        if (session?.accessToken) {
          setAuthSession(session.accessToken, session.user ?? null);
          await loadCurrentUser().catch(() => undefined);
          navigate(returnUrl, { replace: true });
          return;
        }
        navigate(buildAuthPath(ROUTES.LOGIN, returnUrl, true), {
          replace: true,
        });
        return;
      }

      await authApi.login({ loginName: loginName.trim(), password });
      await loadCurrentUser().catch(() => undefined);
      navigate(returnUrl, { replace: true });
    } catch (error) {
      setErrorMessage(getErrorMessage(error));
    } finally {
      setIsLoading(false);
    }
  };

  const nextModePath = buildAuthPath(
    isRegister ? ROUTES.LOGIN : ROUTES.REGISTER,
    returnUrl
  );
  const registered = searchParams.get("registered") === "1";

  return (
    <div
      className="liquid-glass-scope relative flex min-h-screen items-center justify-center p-4"
      style={{
        backgroundImage: "url('/images/gradient-background.jpg')",
        backgroundSize: "cover",
        backgroundPosition: "center",
        backgroundRepeat: "no-repeat",
      }}
    >
      <div
        className="absolute inset-0 opacity-0"
        style={{
          background: "rgba(0, 0, 0, 0.15)",
        }}
      ></div>

      {/* Floating glass orbs for visual interest */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="liquid-glass-orb absolute top-1/4 left-1/4 w-32 h-32 rounded-full opacity-50 animate-pulse"></div>
        <div className="liquid-glass-orb absolute top-3/4 right-1/4 w-24 h-24 rounded-full opacity-40 animate-pulse delay-1000"></div>
        <div className="liquid-glass-orb absolute top-1/2 right-1/3 w-16 h-16 rounded-full opacity-45 animate-pulse delay-500"></div>
      </div>

      <div
        className="relative z-10 w-full max-w-md flex flex-col gap-6 rounded-xl py-6 hover-lift"
        style={{
          background: "rgba(255, 255, 255, 0.25)",
          backdropFilter: "blur(40px) saturate(250%)",
          WebkitBackdropFilter: "blur(40px) saturate(250%)",
          border: "1px solid rgba(255, 255, 255, 0.4)",
          boxShadow:
            "0 32px 80px rgba(0, 0, 0, 0.3), 0 16px 64px rgba(255, 255, 255, 0.2), inset 0 3px 0 rgba(255, 255, 255, 0.6), inset 0 -1px 0 rgba(255, 255, 255, 0.3)",
        }}
      >
        <div className="text-center space-y-2 px-6">
          <div className="text-3xl font-bold font-sans text-card-foreground leading-none">
            {isRegister ? "创建账号" : "欢迎回来"}
          </div>
          <div className="text-sm text-card-foreground/70 font-sans">
            {isRegister ? "注册后即可开始使用 Reactor" : "登录以继续使用 Reactor"}
          </div>
        </div>

        <div className="space-y-6 px-6">
          {registered && !isRegister ? (
            <div
              className="rounded-xl px-4 py-3 text-sm font-sans text-card-foreground"
              style={{
                background: "rgba(255, 255, 255, 0.35)",
                border: "1px solid rgba(255, 255, 255, 0.5)",
              }}
            >
              账号创建成功，请登录继续。
            </div>
          ) : null}

          {errorMessage ? (
            <div
              className="rounded-xl px-4 py-3 text-sm font-sans"
              style={{
                background: "rgba(185, 28, 28, 0.22)",
                border: "1px solid rgba(255, 255, 255, 0.5)",
                color: "oklch(0.3 0.12 25)",
              }}
            >
              {errorMessage}
            </div>
          ) : null}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label
                htmlFor="loginName"
                className="text-sm font-medium text-card-foreground font-sans"
              >
                账号
              </Label>
              <Input
                id="loginName"
                type="text"
                autoComplete="username"
                placeholder="请输入账号"
                value={loginName}
                onChange={(e) => setLoginName(e.target.value)}
                className={inputClassName}
                required
              />
            </div>

            {isRegister ? (
              <div className="space-y-2">
                <Label
                  htmlFor="nickname"
                  className="text-sm font-medium text-card-foreground font-sans"
                >
                  昵称
                </Label>
                <Input
                  id="nickname"
                  type="text"
                  maxLength={40}
                  autoComplete="nickname"
                  placeholder="请输入昵称"
                  value={nickname}
                  onChange={(e) => setNickname(e.target.value)}
                  className={inputClassName}
                  required
                />
              </div>
            ) : null}

            <div className="space-y-2">
              <Label
                htmlFor="password"
                className="text-sm font-medium text-card-foreground font-sans"
              >
                密码
              </Label>
              <Input
                id="password"
                type="password"
                autoComplete={isRegister ? "new-password" : "current-password"}
                placeholder="请输入密码"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={inputClassName}
                required
              />
            </div>

            {isRegister ? (
              <div className="space-y-2">
                <Label
                  htmlFor="confirmPassword"
                  className="text-sm font-medium text-card-foreground font-sans"
                >
                  确认密码
                </Label>
                <Input
                  id="confirmPassword"
                  type="password"
                  autoComplete="new-password"
                  placeholder="请再次输入密码"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  className={inputClassName}
                  required
                />
              </div>
            ) : null}

            <Button
              type="submit"
              className="w-full ripple-effect hover-lift font-sans font-bold py-5 transition-all duration-300 bg-[#0C115B] hover:bg-[#0A0E4A] text-white"
              style={{ backgroundColor: "#0C115B", color: "white" }}
              disabled={isLoading}
            >
              {isLoading
                ? isRegister
                  ? "提交中..."
                  : "登录中..."
                : isRegister
                  ? "创建账号"
                  : "登 录"}
            </Button>
          </form>

          <div className="text-center">
            <span className="text-sm text-card-foreground/70 font-sans">
              {isRegister ? "已有账号？" : "还没有账号？"}
            </span>
            <Link
              className="ml-1 text-sm font-medium text-card-foreground font-sans underline-offset-4 hover:underline transition-colors"
              to={nextModePath}
            >
              {isRegister ? "登录" : "注册"}
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
