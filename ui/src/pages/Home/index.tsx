import { useState, useCallback, memo, useRef, useEffect } from "react";
import GeneralInput from "@/components/GeneralInput";
import ChatView from "@/components/ChatView";
import DataListDrawer from "@/components/DataListDrawer";
import ColsAndDataDrawer from "@/components/DataListDrawer/ColsAndDataDrawer";

import { productList, defaultProduct, chatQustions } from "@/utils/constants";
import { Image } from "antd";
import { demoList } from "@/utils/constants";
import classNames from "classnames";

type HomeProps = Record<string, never>;

// 标签颜色映射
const tagColorMap: Record<string, string> = {
  专业研究: "bg-[rgba(64,64,255,0.08)] text-[#4040ff]",
  数据分析: "bg-[rgba(16,185,129,0.08)] text-[#059669]",
  竞品调研: "bg-[rgba(245,158,11,0.08)] text-[#d97706]",
};

// 输出模式：网页 / 文档 / PPT / 表格 合并为一个下拉组
const OUTPUT_TYPES = ["html", "docs", "ppt", "table"];

// 输出模式的描述文案
const outputDescMap: Record<string, string> = {
  html: "生成可交互的 HTML 网页报告",
  docs: "以 Markdown 格式输出结构化文档",
  ppt: "自动生成可演示的 PPT 文稿",
  table: "以结构化表格形式呈现结论",
};

const Home: GenieType.FC<HomeProps> = memo(() => {
  const [inputInfo, setInputInfo] = useState<CHAT.TInputInfo>({
    message: "",
    deepThink: false,
  });
  const [product, setProduct] = useState(() => productList.find((p) => p.type === "html") ?? defaultProduct);
  const [videoModalOpen, setVideoModalOpen] = useState();
  const [dbsShow, setDbsShow] = useState(false);
  const [dataShow, setDataShow] = useState(false);
  const [outputMenuOpen, setOutputMenuOpen] = useState(false);
  // 记录"上次使用的输出模式"，按钮始终显示它
  const [displayOutput, setDisplayOutput] = useState(() => productList.find((p) => p.type === "html")!);
  const [curModel, setCurModel] = useState<CHAT.ModelInfo>({
    modelName: "",
    modelCode: "",
    schemaList: [],
  });

  const outputMenuRef = useRef<HTMLDivElement>(null);

  // 点击外部关闭下拉
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (outputMenuRef.current && !outputMenuRef.current.contains(e.target as Node)) {
        setOutputMenuOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const changeInputInfo = useCallback((info: CHAT.TInputInfo) => {
    setInputInfo(info);
  }, []);

  const toSendMessage = useCallback((query: Record<string, any>) => {
    setInputInfo({
      message: query.label,
      outputStyle: "dataAgent",
      deepThink: query.type === 2,
    });
  }, []);

  const showDetail = useCallback((modelInfo: any) => {
    setCurModel(modelInfo);
    setDataShow(true);
  }, []);

  // 主要按钮（聊天 + 智能问数）
  const primaryProducts = productList.filter((p) => !OUTPUT_TYPES.includes(p.type));
  // 输出模式列表
  const outputProducts = productList.filter((p) => OUTPUT_TYPES.includes(p.type));
  // 当前 product 是否与显示的输出模式一致（激活态）
  const isOutputActive = product.type === displayOutput.type;

  const CaseCard = ({ title, description, tag, image, url, videoUrl }: any) => {
    const tagColor = tagColorMap[tag] ?? "bg-gray-100 text-gray-500";
    return (
      <div className="group flex flex-col rounded-2xl bg-white shadow-[0_2px_16px_rgba(64,64,255,0.06)] hover:shadow-[0_12px_36px_rgba(64,64,255,0.14)] hover:-translate-y-[6px] transition-all duration-300 ease-in-out cursor-pointer w-[210px] shrink-0 border border-[rgba(233,233,240,0.9)] overflow-hidden">
        {/* 预览图 - 顶部 */}
        <div className="relative overflow-hidden h-[148px]">
          <img src={image} className="w-full h-full object-cover group-hover:scale-[1.06] transition-transform duration-500 ease" />
          <div
            className="absolute inset-0 flex items-center justify-center bg-transparent group-hover:bg-[rgba(0,0,0,0.45)] transition-all duration-300"
            onClick={() => setVideoModalOpen(videoUrl)}
          >
            <div className="opacity-0 group-hover:opacity-100 w-[44px] h-[44px] rounded-full bg-white/20 backdrop-blur-sm flex items-center justify-center border border-white/50 transition-all duration-300 scale-75 group-hover:scale-100">
              <i className="font_family icon-bofang text-[#fff] text-[18px] ml-[2px]"></i>
            </div>
          </div>
          <Image
            style={{ display: "none" }}
            preview={{
              visible: videoModalOpen === videoUrl,
              destroyOnHidden: true,
              imageRender: () => <video muted width="80%" controls autoPlay src={videoUrl} />,
              toolbarRender: () => null,
              onVisibleChange: () => {
                setVideoModalOpen(undefined);
              },
            }}
            src={image}
          />
        </div>
        {/* 卡片内容 */}
        <div className="p-[16px] flex flex-col gap-[8px]">
          <div className="flex items-center justify-between gap-[8px]">
            <div className="text-[14px] font-bold text-[#18181b] truncate">{title}</div>
            <span className={`shrink-0 inline-block px-[8px] leading-[22px] text-[11px] rounded-[6px] font-medium ${tagColor}`}>{tag}</span>
          </div>
          <div className="text-[12px] text-[#71717a] line-clamp-2 leading-[20px]">{description}</div>
          <div
            className="text-[#4040ff] group-hover:text-[#656cff] text-[12px] flex items-center gap-[3px] cursor-pointer transition-colors duration-200 pt-[2px]"
            onClick={() => window.open(url)}
          >
            <span>查看报告</span>
            <i className="font_family icon-xinjianjiantou text-[10px]"></i>
          </div>
        </div>
      </div>
    );
  };

  const renderContent = () => {
    if (inputInfo.message.length === 0) {
      return (
        <div
          className="pt-[80px] flex flex-col items-center w-full"
          style={{ background: "radial-gradient(ellipse 80% 40% at 50% 0%, rgba(196,196,255,0.18) 0%, transparent 70%)" }}
        >
          {/* Hero 区域 */}
          <div className="flex flex-col items-center">
            {/* Reactor 品牌名 */}
            <h1
              className="mb-[14px] text-[58px] font-black tracking-[-1.5px] leading-none select-none"
              style={{
                background: "linear-gradient(130deg, #4040ff 0%, #a855f7 50%, #06b6d4 100%)",
                WebkitBackgroundClip: "text",
                WebkitTextFillColor: "transparent",
                backgroundClip: "text",
                WebkitFontSmoothing: "antialiased",
                MozOsxFontSmoothing: "grayscale",
              }}
            >
              Reactor
            </h1>
            <p className="text-[15px] text-[#71717a] text-center max-w-[420px] leading-[1.75] mb-[32px]">
              AI 智能体平台 · 一句话完成数据分析、研究调查和内容创作
            </p>
          </div>

          {/* 输入框 */}
          <div className="w-[760px] rounded-xl shadow-[0_18px_39px_0_rgba(198,202,240,0.1)]">
            <GeneralInput placeholder={product.placeholder} showBtn={true} size="big" disabled={false} product={product} send={changeInputInfo} dbsShow={setDbsShow} />
          </div>

          {/* 产品模式选择器 */}
          <div className="w-[760px] flex items-center gap-[8px] mt-[12px]">
            {/* 输出格式下拉按钮 - 最左边 */}
            <div className="relative" ref={outputMenuRef}>
              <div
                className={classNames(
                  "h-[36px] px-[16px] cursor-pointer flex items-center gap-[6px] border rounded-[8px] text-[13px] font-medium transition-all duration-200 select-none whitespace-nowrap",
                  isOutputActive
                    ? "border-[#4040ff] bg-[rgba(64,64,255,0.07)] text-[#4040ff] shadow-[0_0_0_1px_rgba(64,64,255,0.08)]"
                    : "border-[#E9E9F0] text-[#52525b] hover:border-[#c5c5ff] hover:text-[#4040ff] hover:bg-[rgba(64,64,255,0.02)]",
                  outputMenuOpen && !isOutputActive && "border-[#c5c5ff] text-[#4040ff] bg-[rgba(64,64,255,0.02)]"
                )}
                onClick={() => {
                  if (!isOutputActive) setProduct(displayOutput);
                  setOutputMenuOpen((v) => !v);
                }}
              >
                <i className={`font_family ${displayOutput.img} ${displayOutput.color} text-[14px]`}></i>
                <span>{displayOutput.name}</span>
                {/* 下拉箭头 */}
                <svg
                  className={classNames("w-[12px] h-[12px] ml-[1px] transition-transform duration-200", { "rotate-180": outputMenuOpen })}
                  viewBox="0 0 12 12"
                  fill="none"
                >
                  <path d="M2.5 4.5L6 8L9.5 4.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </div>

              {/* 下拉菜单 */}
              {outputMenuOpen && (
                <div className="absolute left-0 top-[42px] z-50 w-[220px] bg-white rounded-[12px] border border-[#E9E9F0] shadow-[0_8px_32px_rgba(0,0,0,0.10)] p-[6px] animate-in fade-in slide-in-from-top-1 duration-150">
                  {outputProducts.map((item) => {
                    const isSelected = product.type === item.type;
                    return (
                      <div
                        key={item.type}
                        className={classNames(
                          "flex items-start gap-[10px] px-[10px] py-[9px] rounded-[8px] cursor-pointer transition-all duration-150 group/item",
                          isSelected ? "bg-[rgba(64,64,255,0.06)]" : "hover:bg-[#f7f7fb]"
                        )}
                        onClick={() => {
                          setProduct(item);
                          setDisplayOutput(item);
                          setOutputMenuOpen(false);
                        }}
                      >
                        {/* 图标 */}
                        <div
                          className={classNames(
                            "w-[28px] h-[28px] rounded-[7px] flex items-center justify-center shrink-0 mt-[1px]",
                            isSelected ? "bg-[rgba(64,64,255,0.1)]" : "bg-[#f4f4f9] group-hover/item:bg-[rgba(64,64,255,0.06)]"
                          )}
                        >
                          <i className={`font_family ${item.img} ${item.color} text-[14px]`}></i>
                        </div>
                        {/* 文字 */}
                        <div className="flex-1 min-w-0">
                          <div className={classNames("text-[13px] font-medium leading-[20px]", isSelected ? "text-[#4040ff]" : "text-[#18181b]")}>{item.name}</div>
                          <div className="text-[11px] text-[#a1a1aa] leading-[17px] mt-[1px]">{outputDescMap[item.type]}</div>
                        </div>
                        {/* 选中勾 */}
                        {isSelected && (
                          <svg className="w-[14px] h-[14px] shrink-0 mt-[6px] text-[#4040ff]" viewBox="0 0 14 14" fill="none">
                            <path d="M2.5 7L5.5 10L11.5 4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
                          </svg>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* 主要模式按钮：聊天 + 智能问数 */}
            {primaryProducts.map((item) => (
              <div
                key={item.type}
                className={classNames(
                  "h-[36px] px-[16px] cursor-pointer flex items-center justify-center gap-[5px] border rounded-[8px] text-[13px] font-medium transition-all duration-200 select-none whitespace-nowrap",
                  item.type === product.type
                    ? "border-[#4040ff] bg-[rgba(64,64,255,0.07)] text-[#4040ff] shadow-[0_0_0_1px_rgba(64,64,255,0.08)]"
                    : "border-[#E9E9F0] text-[#52525b] hover:border-[#c5c5ff] hover:text-[#4040ff] hover:bg-[rgba(64,64,255,0.02)]"
                )}
                onClick={() => setProduct(item)}
              >
                <i className={`font_family ${item.img} ${item.color} text-[14px]`}></i>
                <span>{item.name}</span>
              </div>
            ))}
          </div>

          <div className="mt-[52px] mb-[80px] relative w-full flex flex-col items-center">
            {/* 建议问题 */}
            <div
              className={classNames("p-0 w-full overflow-hidden transition-all duration-400 opacity-0 max-h-0", {
                "opacity-100 max-h-[60px] mb-[16px]": product.type === "dataAgent",
              })}
            >
              <div className="flex gap-x-[10px] justify-center flex-wrap">
                {chatQustions.map((item, i) => (
                  <div
                    key={i}
                    className="text-[#52525B] cursor-pointer border border-[#E9E9F0] rounded-[20px] px-[14px] py-[5px] text-[13px] whitespace-nowrap flex items-center gap-[4px] hover:border-[#4040ff] hover:text-[#4040ff] hover:bg-[rgba(64,64,255,0.04)] transition-all duration-200"
                    onClick={() => toSendMessage(item)}
                  >
                    {item.type === 2 && <i className="font_family icon-shendusikao text-[12px]"></i>}
                    {item.label}
                  </div>
                ))}
              </div>
            </div>

            {/* 精选案例标题 */}
            <div className="text-center mb-[28px]">
              <div className="flex items-center justify-center gap-[12px] mb-[6px]">
                <div className="w-[40px] h-[1px] bg-gradient-to-r from-transparent to-[rgba(64,64,255,0.25)]"></div>
                <h2 className="text-[20px] font-bold text-[#18181b] tracking-wide">精选案例</h2>
                <div className="w-[40px] h-[1px] bg-gradient-to-l from-transparent to-[rgba(64,64,255,0.25)]"></div>
              </div>
              <p className="text-[13px] text-[#a1a1aa]">和 Genie 一起，让效率飞起来</p>
            </div>

            {/* 案例卡片 */}
            <div className="flex gap-[20px]">
              {demoList.map((demo, i) => (
                <CaseCard key={i} {...demo} />
              ))}
            </div>
          </div>

          {/* 模型列表 */}
          <DataListDrawer show={dbsShow} dbsShow={setDbsShow} showDetail={showDetail}></DataListDrawer>
          {/* 列字段和数据 */}
          {dataShow && <ColsAndDataDrawer show={dataShow} dataShow={setDataShow} modelInfo={curModel}></ColsAndDataDrawer>}
        </div>
      );
    }
    return <ChatView inputInfo={inputInfo} product={product} />;
  };

  return <div className="h-full flex flex-col items-center">{renderContent()}</div>;
});

Home.displayName = "Home";

export default Home;
