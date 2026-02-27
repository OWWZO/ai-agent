import classNames from "classnames";

const LoadingSpinner: GenieType.FC<{
  color?: string;
}> = (props) => {
  const { className, children, color = 'white' } = props;

  return (
    <>
      <div className={classNames('relative size-[1em] shrink-0', className)}>
        <div className="absolute inset-0 rounded-full bg-gradient-to-r from-[#4040ff] to-transparent animate-spin bg-clip-padding p-2">
          <div className="absolute inset-2 rounded-full" style={{ backgroundColor: color }}></div>
        </div>
      </div>
      {children}
    </>
  );
};

export default LoadingSpinner;