import { FC } from "react";
import LoadingDot from "@/components/LoadingDot";
import DataChat from "@/components/DataChat";

type Props = {
  chat: Record<string, any>;
};

const DataDialogue: FC<Props> = (props) => {
  const { chat } = props;

  function renderBreakText(text: string) {
    return (
      <>
        <div className="font-bold text-[16px] mb-[8px]">思考过程</div>
        {text.split("\n").map((seg: string, i: number) => (
          <span key={i}>
            {seg}
            {i !== text.split("\n").length - 1 && <br />}
          </span>
        ))}
      </>
    );
  }

  return (
    <div className="h-full text-[14px] font-normal flex flex-col text-[#27272a]">
      {chat.query ? (
        <div className="w-full mt-[24px] flex justify-end">
          <div className="max-w-[80%] bg-[#4040FFB2] text-[#fff] px-12 py-8 rounded-[12px] rounded-tr-[12px] rounded-br-[4px] rounded-bl-[12px] ">{chat.query}</div>
        </div>
      ) : null}
      <div className="border border-gray-200 mt-[24px] bg-[#F2F3F7] rounded-[12px] p-12">
        {chat.think ? <div className="w-full">{renderBreakText(chat.think)}</div> : null}
        {chat.chartData && <div className="font-bold text-[16px] mt-[18px] mb-[-10px]">输出结果</div>}
        {chat.chartData?.map((n: Record<string, any> | undefined, index: number) => {
          return <DataChat key={index} data={n} />;
        })}
        {chat.error?.length > 0 && (
          <div className="leading-[22px] text-[#1b1b1b] mt-[20px]">
            <span className="font-medium">回答失败，没能理解您的意图。</span>
          </div>
        )}
        {chat.loading ? <LoadingDot /> : null}
      </div>
    </div>
  );
};

export default DataDialogue;
