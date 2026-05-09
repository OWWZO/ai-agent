import { X } from "lucide-react";

const Title: ReactorType.FC<{
  onClose?: () => void;
}> = (props) => {
  const { children, onClose } = props;

  return (
    <div className="flex items-center justify-between px-1 pb-4">
      <h3 className="text-[17px] font-semibold tracking-[-0.01em] text-[#1d1d1f]">
        {children}
      </h3>
      <button
        onClick={onClose}
        className="flex h-8 w-8 items-center justify-center rounded-full text-[#86868b] transition-all duration-200 hover:bg-[#f5f5f7] hover:text-[#1d1d1f]"
        title="关闭智能体工作区"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
};

export default Title;
