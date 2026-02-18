import classNames from "classnames";

const Dot: GenieType.FC = (props) => {
  const { className } = props;
  return <div className={classNames('size-14 bg-primary inline-block rounded-[50%]', className)}></div>;
};

export default Dot;