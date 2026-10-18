import React, { useEffect, useLayoutEffect } from 'react';
import { ConfigProvider, Spin } from 'antd';
import { RouterProvider } from 'react-router-dom';
import zhCN from 'antd/locale/zh_CN';
import router from './router';
// OpenUI 渲染器的样式。Tailwind v4 项目必须用 layered 入口（规则全包在 @layer openui 里），
// 否则它会以未分层样式出现、盖掉 Tailwind utilities。变量前缀 --openui-* / --iui-*，与现有主题无冲突。
import '@openuidev/react-ui/layered/styles/index.css';
import { restoreAuthSession } from '@/services/auth';
import { useAuth } from '@/stores/auth';

/** index.html 里首屏骨架屏的 id（见 ui/index.html 顶部注释）。 */
const BOOT_SKELETON_ID = 'boot-skeleton';

// App 组件：应用的根组件，设置全局配置和路由
const App: ReactorType.FC = React.memo(() => {
  const auth = useAuth();

  // 骨架屏在 index.html 里、#root 之外，这里在 React 首次提交后同步摘掉：
  // useLayoutEffect 跑在 DOM 变更之后、浏览器绘制之前，所以不会闪一下空白。
  // 此处首帧要么是下面的 Spin、要么是路由内容，摘掉后一定立刻有东西可看。
  useLayoutEffect(() => {
    document.getElementById(BOOT_SKELETON_ID)?.remove();
  }, []);

  useEffect(() => {
    void restoreAuthSession();
  }, []);

  return (
    <ConfigProvider locale={zhCN}>
      {auth.status === 'initializing' ? (
        <div className="flex min-h-screen items-center justify-center">
          <Spin size="large" aria-label="正在恢复登录状态" />
        </div>
      ) : (
        <RouterProvider router={router} />
      )}
    </ConfigProvider>
  );
});

export default App;
