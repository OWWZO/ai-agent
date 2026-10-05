import React, { useEffect } from 'react';
import { ConfigProvider, Spin } from 'antd';
import { RouterProvider } from 'react-router-dom';
import zhCN from 'antd/locale/zh_CN';
import router from './router';
import { restoreAuthSession } from '@/services/auth';
import { useAuth } from '@/stores/auth';

// App 组件：应用的根组件，设置全局配置和路由
const App: ReactorType.FC = React.memo(() => {
  const auth = useAuth();

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
