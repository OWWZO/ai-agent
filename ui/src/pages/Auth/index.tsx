import { useState } from "react";
import { Alert, Button, Form, Input } from "antd";
import { ArrowRight, Bot } from "lucide-react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";

import {
  authApi,
  loadCurrentUser,
  type LoginPayload,
  type RegisterPayload,
} from "@/services/auth";
import { setAuthSession } from "@/stores/auth";
import { normalizeReturnUrl, ROUTES } from "@/router/routes";

type AuthPageProps = {
  mode: "login" | "register";
};

type AuthFormValues = LoginPayload & {
  nickname?: string;
  confirmPassword?: string;
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

export default function AuthPage({ mode }: AuthPageProps) {
  const [form] = Form.useForm<AuthFormValues>();
  const [searchParams] = useSearchParams();
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const navigate = useNavigate();
  const isRegister = mode === "register";
  const returnUrl = normalizeReturnUrl(searchParams.get("returnUrl"));

  const handleSubmit = async (values: AuthFormValues) => {
    setSubmitting(true);
    setErrorMessage("");

    try {
      if (isRegister) {
        const payload: RegisterPayload = {
          loginName: values.loginName.trim(),
          password: values.password,
          nickname: values.nickname?.trim() || "",
        };
        const session = await authApi.register(payload);
        if (session?.accessToken) {
          setAuthSession(session.accessToken, session.user ?? null);
          await loadCurrentUser().catch(() => undefined);
          navigate(returnUrl, { replace: true });
          return;
        }
        navigate(buildAuthPath(ROUTES.LOGIN, returnUrl, true), { replace: true });
        return;
      }

      await authApi.login({
        loginName: values.loginName.trim(),
        password: values.password,
      });
      await loadCurrentUser().catch(() => undefined);
      navigate(returnUrl, { replace: true });
    } catch (error) {
      setErrorMessage(getErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  const nextModePath = buildAuthPath(
    isRegister ? ROUTES.LOGIN : ROUTES.REGISTER,
    returnUrl
  );

  return (
    <main className="flex min-h-screen items-center justify-center bg-[var(--page-gradient)] px-5 py-10 text-[var(--chat-text)]">
      <section className="w-full max-w-[440px] rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface)] p-6 shadow-[var(--shadow-md)] sm:p-9">
        <div className="mb-8">
          <div className="mb-5 flex items-center gap-2 text-[15px] font-semibold">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-[var(--chat-text)] text-white">
              <Bot className="h-5 w-5" />
            </span>
            <span>Reactor</span>
          </div>
          <h1 className="text-[26px] font-semibold leading-tight">
            {isRegister ? "创建账号" : "登录账号"}
          </h1>
          <p className="mt-2 text-[14px] text-[var(--chat-text-muted)]">
            {isRegister ? "注册后即可开始使用 Reactor" : "登录以继续使用 Reactor"}
          </p>
        </div>

        {searchParams.get("registered") === "1" && !isRegister ? (
          <Alert
            className="mb-5"
            type="success"
            showIcon
            message="账号创建成功，请登录继续。"
          />
        ) : null}
        {errorMessage ? (
          <Alert
            className="mb-5"
            type="error"
            showIcon
            message={errorMessage}
          />
        ) : null}

        <Form
          form={form}
          layout="vertical"
          requiredMark={false}
          onFinish={handleSubmit}
          size="large"
        >
          <Form.Item
            label="账号"
            name="loginName"
            rules={[
              {
                required: true,
                message: "请输入账号",
              },
            ]}
          >
            <Input autoComplete="username" placeholder="请输入账号" />
          </Form.Item>

          {isRegister ? (
            <Form.Item
              label="昵称"
              name="nickname"
              rules={[
                {
                  required: true,
                  message: "请输入昵称",
                },
              ]}
            >
              <Input maxLength={40} placeholder="请输入昵称" />
            </Form.Item>
          ) : null}

          <Form.Item
            label="密码"
            name="password"
            rules={[
              {
                required: true,
                message: "请输入密码",
              },
            ]}
          >
            <Input.Password
              autoComplete={isRegister ? "new-password" : "current-password"}
              placeholder="请输入密码"
            />
          </Form.Item>

          {isRegister ? (
            <Form.Item
              label="确认密码"
              name="confirmPassword"
              dependencies={["password"]}
              rules={[
                {
                  required: true,
                  message: "请再次输入密码",
                },
                ({ getFieldValue }) => ({
                  validator(_, value: string | undefined) {
                    if (!value || getFieldValue("password") === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error("两次输入的密码不一致"));
                  },
                }),
              ]}
            >
              <Input.Password autoComplete="new-password" placeholder="请再次输入密码" />
            </Form.Item>
          ) : null}

          <Button
            block
            type="primary"
            htmlType="submit"
            loading={submitting}
            icon={!submitting ? <ArrowRight className="h-4 w-4" /> : undefined}
            className="mt-2"
          >
            {isRegister ? "创建账号" : "登录"}
          </Button>
        </Form>

        <p className="mt-6 text-center text-[14px] text-[var(--chat-text-muted)]">
          {isRegister ? "已有账号？" : "还没有账号？"}
          <Link
            className="ml-1 font-medium text-[var(--chat-text)] underline-offset-4 hover:underline"
            to={nextModePath}
          >
            {isRegister ? "登录" : "注册"}
          </Link>
        </p>
      </section>
    </main>
  );
}
