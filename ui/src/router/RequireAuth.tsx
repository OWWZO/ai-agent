import { Navigate, Outlet, useLocation } from "react-router-dom";

import Loading from "@/components/ActionPanel/Loading";
import { useAuth } from "@/stores/auth";
import { buildLoginPath, ROUTES } from "./routes";

export default function RequireAuth() {
  const auth = useAuth();
  const location = useLocation();
  const returnUrl = `${location.pathname}${location.search}${location.hash}`;

  if (auth.status === "initializing") {
    return <Loading loading className="h-full" />;
  }

  if (auth.status !== "authenticated") {
    return <Navigate to={buildLoginPath(returnUrl || ROUTES.HOME)} replace />;
  }

  return <Outlet />;
}
