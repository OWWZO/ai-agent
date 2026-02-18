const Title: GenieType.FC<{
  onClose?: () => void;
}> = (props) => {
  const { children, onClose } = props;

  return <div className="text-[16px] font-semibold flex items-center justify-between mb-[8px]">
    {children}
    <i className="font_family icon-guanbi cursor-pointer" onClick={onClose}></i>
  </div>;
};

export default Title;