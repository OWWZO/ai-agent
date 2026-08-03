import { Result, Button } from 'antd';

import { jumpUrl } from '@/utils/utils';

const NotFound: ReactorType.FC = () => {
  return (
    <Result
      status="404"
      title="404"
      subTitle="抱歉，您访问的页面不存在。"
      extra={
        <Button type="primary" onClick={() => jumpUrl('/')}>
          返回首页
        </Button>
      }
    />
  );
};

export default NotFound;
