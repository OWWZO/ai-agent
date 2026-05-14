import React, { forwardRef, useImperativeHandle, useRef } from "react";
import classNames from "classnames";
import { motion, AnimatePresence } from "motion/react";
import { X, Maximize2, Minimize2 } from "lucide-react";
import Tabs from "../Tabs";
import { useSafeState } from "ahooks";
import { useConstants } from "@/hooks";
import FilePreview from "./FilePreview";
import { ActionViewItemEnum } from "@/utils";

import FileList from "./FileList";
import { PlanView, PlanViewAction } from "../PlanView";
import { PanelItemType } from "../ActionPanel";

type ActionViewRef = PlanViewAction & {
  setFilePreview: (file?: CHAT.TFile) => void;
  changeActionView: (item: ActionViewItemEnum) => void;
};

const useActionView = () => {
  const ref = useRef<ActionViewRef>(null);
  return ref;
};

type ActionViewProps = {
  title?: React.ReactNode;
  taskList?: PanelItemType[];
  activeTask?: CHAT.Task;
  streamTask?: CHAT.Task;
  plan?: CHAT.Plan;
  runState?: {
    status?: string;
    errorMsg?: string;
    finishedAt?: string;
  };
  isFocusMode?: boolean;
  onToggleFocusMode?: () => void;
  onClose?: () => void;
  ref?: React.Ref<ActionViewRef>;
};

const ActionViewComp: ReactorType.FC<ActionViewProps> = forwardRef((props, ref) => {
  const { className, onClose, activeTask, streamTask, taskList, plan, runState, isFocusMode, onToggleFocusMode } = props;

  const [curFileItem, setCurFileItem] = useSafeState<CHAT.TFile>();
  const planRef = useRef<PlanViewAction>(null);
  const { defaultActiveActionView, actionViewOptions } = useConstants();
  const [activeActionView, setActiveActionView] = useSafeState(defaultActiveActionView);
  const resolvedActiveActionView = actionViewOptions.some((item) => item.value === activeActionView)
    ? activeActionView
    : defaultActiveActionView;

  useImperativeHandle(ref, () => {
    return {
      ...planRef.current!,
      setFilePreview: (file) => {
        setActiveActionView(ActionViewItemEnum.file);
        setCurFileItem(file);
      },
      changeActionView: setActiveActionView,
    };
  });

  return (
    <motion.div
      className={classNames("flex h-full w-full flex-col bg-white/50", className)}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{
        duration: 0.4,
        ease: [0.25, 0.46, 0.45, 0.94],
      }}
    >
      {/* Header Section — Title 层删除，Tab 与工具按钮同行 */}
      <motion.div
        className="flex items-center justify-between gap-3 px-4 py-3"
        initial={{
          opacity: 0,
          y: -10,
        }}
        animate={{
          opacity: 1,
          y: 0,
        }}
        transition={{
          duration: 0.3,
          delay: 0.1,
        }}
      >
        <Tabs
          value={resolvedActiveActionView}
          onChange={setActiveActionView}
          options={actionViewOptions}
          className="min-h-[36px]"
        />
        <div className="flex shrink-0 items-center gap-1">
          {onToggleFocusMode && (
            <button
              onClick={onToggleFocusMode}
              className="flex h-8 w-8 items-center justify-center rounded-full text-[#86868b] transition-all duration-200 hover:bg-[#f5f5f7] hover:text-[#1d1d1f]"
              title={isFocusMode ? "退出专注模式" : "专注模式"}
            >
              {isFocusMode ? (
                <Minimize2 className="h-4 w-4" />
              ) : (
                <Maximize2 className="h-4 w-4" />
              )}
            </button>
          )}
          <button
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-full text-[#86868b] transition-all duration-200 hover:bg-[#f5f5f7] hover:text-[#1d1d1f]"
            title="关闭智能体工作区"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      </motion.div>

      {/* Content Area */}
      <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
        <motion.div
          className="flex-1 overflow-auto px-3 py-2"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{
            duration: 0.4,
            delay: 0.2,
          }}
        >
          <AnimatePresence mode="wait">
            {resolvedActiveActionView === ActionViewItemEnum.follow && (
              <motion.div
                key="follow"
                initial={{
                  opacity: 0,
                  y: 10,
                }}
                animate={{
                  opacity: 1,
                  y: 0,
                }}
                exit={{
                  opacity: 0,
                  y: -10,
                }}
                transition={{ duration: 0.3 }}
                className="h-full"
              >
                <FilePreview
                  taskItem={activeTask || streamTask}
                  taskList={taskList}
                  runState={runState}
                  className="h-full"
                />
              </motion.div>
            )}
            {resolvedActiveActionView === ActionViewItemEnum.file && (
              <motion.div
                key="file"
                initial={{
                  opacity: 0,
                  y: 10,
                }}
                animate={{
                  opacity: 1,
                  y: 0,
                }}
                exit={{
                  opacity: 0,
                  y: -10,
                }}
                transition={{ duration: 0.3 }}
                className="h-full"
              >
                <FileList
                  taskList={taskList}
                  activeFile={curFileItem}
                  clearActiveFile={() => {
                    setCurFileItem(undefined);
                  }}
                />
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>
        <PlanView plan={plan} ref={planRef} />
      </div>
    </motion.div>
  );
});

// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-expect-error
const ActionView: typeof ActionViewComp & {
  useActionView: typeof useActionView;
} = ActionViewComp;
ActionView.useActionView = useActionView;

export default ActionView;
