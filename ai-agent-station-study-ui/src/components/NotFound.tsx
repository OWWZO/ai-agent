import { Result, Button } from 'antd';
import { useNavigate } from 'react-router-dom';

const NotFound: GenieType.FC = () => {
  const navigate = useNavigate();

  return (
    <Result
      status="404"
      title="404"
      subTitle="抱歉，您访问的页面不存在。"
      extra={
        <Button type="primary" onClick={() => navigate('/')}>
          返回首页
        </Button>
      }
    />
  );
};

export default NotFound;
