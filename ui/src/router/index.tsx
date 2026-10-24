import React, { Suspense } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import Layout from '@/layout/index';
// ⚠️ 必须直接 import，不要经 barrel 中转。
// 曾经的 `@/components` barrel 会 `export * from './ActionPanel'`，而 ActionPanel/index.ts
// 静态 import 了全部预览渲染器（含 MarkdownRenderer → react-markdown + remark-gfm），
// 仅为拿一个 Loading 就会把 markdown 解析器整条管线拖进首屏 chunk：
// 实测静态可达模块 273 → 24（详见 .workbuddy-ai/memory 的白屏诊断）。
// barrel 已删除，但这里的直接 import 习惯保留 —— 同样的坑会在任何新的
// barrel / 深层 re-export 上重现。
import Loading from '@/components/ActionPanel/Loading';
import { ROUTES } from './routes';
import RequireAuth from './RequireAuth';

// 使用 React.lazy 懒加载组件
const Home = React.lazy(() => import('@/pages/Home'));
const FeaturedConversations = React.lazy(
  () => import('@/pages/FeaturedConversations')
);
const FeaturedConversationDetail = React.lazy(
  () => import('@/pages/FeaturedConversationDetail')
);
const NotFound = React.lazy(() => import('@/components/NotFound'));
const AuthPage = React.lazy(() => import('@/pages/Auth'));
const OpenUiDemo = React.lazy(() => import('@/pages/OpenUiDemo'));

// OpenUI 混排协议的验证页：只在开发环境注册，不进生产产物。
const openUiDemoRoutes = import.meta.env.DEV
  ? [
      {
        path: ROUTES.OPENUI_DEMO,
        element: (
          <Suspense fallback={<Loading loading={true} className="h-full" />}>
            <OpenUiDemo />
          </Suspense>
        ),
      },
    ]
  : [];

// 创建路由配置
const router = createBrowserRouter([
  {
    path: ROUTES.HOME,
    element: <Layout />,
    children: [
      {
        path: ROUTES.LOGIN,
        element: (
          <Suspense fallback={<Loading loading={true} className="h-full" />}>
            <AuthPage mode="login" />
          </Suspense>
        ),
      },
      {
        path: ROUTES.REGISTER,
        element: (
          <Suspense fallback={<Loading loading={true} className="h-full" />}>
            <AuthPage mode="register" />
          </Suspense>
        ),
      },
      {
        path: ROUTES.FEATURED_CONVERSATIONS,
        element: (
          <Suspense fallback={<Loading loading={true} className="h-full" />}>
            <FeaturedConversations />
          </Suspense>
        ),
      },
      {
        element: <RequireAuth />,
        children: [
          {
            index: true,
            element: (
              <Suspense fallback={<Loading loading={true} className="h-full" />}>
                <Home />
              </Suspense>
            ),
          },
          {
            path: ROUTES.FEATURED_CONVERSATION_DETAIL,
            element: (
              <Suspense fallback={<Loading loading={true} className="h-full" />}>
                <FeaturedConversationDetail />
              </Suspense>
            ),
          },
          // 工作台只有一套导航：Home 页内切换。旧地址保留为入口，
          // 统一落到 `/?view=<key>`，避免同一批页面存在两套路由模型。
          {
            path: ROUTES.WORKSPACE,
            element: <Navigate to={ROUTES.WORKSPACE_MRAG} replace />,
          },
          {
            path: ROUTES.WORKSPACE_MRAG,
            element: <Navigate to={`${ROUTES.HOME}?view=mrag`} replace />,
          },
          {
            path: ROUTES.WORKSPACE_IMAGE_GENERATION,
            element: <Navigate to={`${ROUTES.HOME}?view=image-generation`} replace />,
          },
          {
            path: ROUTES.WORKSPACE_SOP,
            element: <Navigate to={`${ROUTES.HOME}?view=sop`} replace />,
          },
          {
            path: ROUTES.WORKSPACE_SUB_AGENTS,
            element: <Navigate to={`${ROUTES.HOME}?view=sub-agents`} replace />,
          },
          {
            path: ROUTES.WORKSPACE_MODELS,
            element: <Navigate to={`${ROUTES.HOME}?view=models`} replace />,
          },
          {
            path: ROUTES.WORKSPACE_CAPABILITIES,
            element: <Navigate to={`${ROUTES.HOME}?view=capabilities`} replace />,
          },
        ],
      },
      {
        path: ROUTES.NOT_FOUND,
        element: (
          <Suspense fallback={<Loading loading={true} className="h-full"/>}>
            <NotFound />
          </Suspense>
        ),
      },
    ],
  },
  // 开发用的 OpenUI 验证页（不挂 Layout / 不要求登录）
  ...openUiDemoRoutes,
  // 重定向所有未匹配的路由到 404 页面
  {
    path: '*',
    element: <Navigate to={ROUTES.NOT_FOUND} replace />,
  },
]);

export default router;
