import { memo, useEffect } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { ConfigProvider, message } from "antd";
import { ConstantProvider } from "@/hooks";
import * as constants from "@/utils/constants";
import { setMessage } from "@/utils";
import {
  AUTH_SESSION_EXPIRED_EVENT,
  type AuthSessionExpiredDetail,
} from "@/services/authSessionExpired";
import { buildLoginPath, ROUTES } from "@/router/routes";

// Layout 组件：应用的主要布局结构
const Layout: ReactorType.FC = memo(() => {
  const [messageApi, messageContent] = message.useMessage();
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    // 初始化全局 message
    setMessage(messageApi);
  }, [messageApi]);

  useEffect(() => {
    const onAuthSessionExpired = (event: Event) => {
      if (
        location.pathname === ROUTES.LOGIN ||
        location.pathname === ROUTES.REGISTER
      ) {
        return;
      }

      const detail = (event as CustomEvent<AuthSessionExpiredDetail>).detail;
      const returnUrl =
        detail?.returnUrl ||
        `${location.pathname}${location.search}${location.hash}` ||
        ROUTES.HOME;
      navigate(buildLoginPath(returnUrl), { replace: true });
    };

    window.addEventListener(AUTH_SESSION_EXPIRED_EVENT, onAuthSessionExpired);
    return () => {
      window.removeEventListener(
        AUTH_SESSION_EXPIRED_EVENT,
        onAuthSessionExpired
      );
    };
  }, [location, navigate]);

  return (
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: "#1783ff",
          borderRadius: 8,
          controlHeight: 36,
        },
      }}
    >
      {messageContent}
      {/* 暂时只有静态的 */}
      <ConstantProvider value={constants}>
        <Outlet />
      </ConstantProvider>
    </ConfigProvider>
  );
});

Layout.displayName = "Layout";

export default Layout;
