import React from 'react';
import classNames from 'classnames';
import LoadingSpinner from '../LoadingSpinner';

interface LoadingProps {
  loading?: boolean;
  className?: string;
  children?: React.ReactNode;
}

const LOADING_CONTAINER_CLASS = 'flex flex-col items-center justify-center w-full';
const TEXT_CLASS = 'mt-6 text-lg text-gray-700';

/**
 * Loading 组件
 *
 * @param loading - 是否显示加载动画
 * @param className - 自定义类名
 * @param children - 自定义加载文本
 */
const Loading: GenieType.FC<LoadingProps> = React.memo(({ loading, className, children }) => {
  if (!loading) return null;

  return (
    <div className={classNames(LOADING_CONTAINER_CLASS, className)}>
      <LoadingSpinner className='text-[32px]'>
        <p className={TEXT_CLASS}>{children || '加载中'}</p>
      </LoadingSpinner>
    </div>
  );
});

Loading.displayName = 'Loading';

export default Loading;
