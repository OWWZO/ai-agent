import { cn } from "@/lib/utils";

const Dot: CuteType.FC = (props) => {
  const { className } = props;
  return <div className={cn('size-14 bg-primary inline-block rounded-[50%]', className)}></div>;
};

export default Dot;