import React from 'react';
import styles from './index.module.css';
import classNames from 'classnames';

const LoadingDot: React.FC = () => {
  const dots = new Array(3).fill(undefined);

  return (
    <div className={styles.loadingDot}>
      {dots.map((_, index) => (
        <div key={index} className={classNames(styles[`dot_${index}`], styles.dot)} />
      ))}
    </div>
  );
};

export default LoadingDot;