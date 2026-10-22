import { memo, useEffect } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { ConstantProvider } from "@/hooks";
import * as constants from "@/utils/constants";
import { Toaster } from "@/components/ui/sonner";
import { ConfirmDialogHost } from "@/components/ui/confirm-dialog";
import {
  AUTH_SESSION_EXPIRED_EVENT,
  type AuthSessionExpiredDetail,
} from "@/services/authSessionExpired";
import { buildLoginPath, ROUTES } from "@/router/routes";

// Layout 组件：应用的主要布局结构
const Layout: ReactorType.FC = memo(() => {
  const location = useLocation();
  const navigate = useNavigate();

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
    <>
      {/* antd message 自带 success 绿 / error 红；richColors 保住同等的信息辨识度 */}
      <Toaster position="top-center" richColors />
      <ConfirmDialogHost />
      {/* 暂时只有静态的 */}
      <ConstantProvider value={constants}>
        <Outlet />
      </ConstantProvider>
    </>
  );
});

Layout.displayName = "Layout";

export default Layout;
