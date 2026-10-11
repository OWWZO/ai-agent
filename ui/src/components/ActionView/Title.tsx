import { X } from "lucide-react";

const Title: ReactorType.FC<{
  onClose?: () => void;
}> = (props) => {
  const { children, onClose } = props;

  return (
    <div className="flex items-center justify-between px-1 pb-4">
      <h3 className="text-ui-lg font-semibold tracking-[-0.01em] text-foreground">
        {children}
      </h3>
      <button
        onClick={onClose}
        className="flex h-8 w-8 items-center justify-center rounded-full text-foreground-subtle transition-[background-color,color,transform] duration-150 hover:bg-hover hover:text-foreground active:scale-[0.97]"
        title="关闭智能体工作区"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
};

export default Title;
