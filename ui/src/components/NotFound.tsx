import { Button } from '@/components/ui/button';
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyTitle } from '@/components/ui/empty';

import { jumpUrl } from '@/utils/utils';

const NotFound: ReactorType.FC = () => {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-6">
      <Empty>
        <EmptyHeader>
          <EmptyTitle className="text-4xl font-semibold">404</EmptyTitle>
          <EmptyDescription>抱歉，您访问的页面不存在。</EmptyDescription>
        </EmptyHeader>
        <EmptyContent>
          <Button onClick={() => jumpUrl('/')}>返回首页</Button>
        </EmptyContent>
      </Empty>
    </div>
  );
};

export default NotFound;
