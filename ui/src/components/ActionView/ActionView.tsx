import React, { forwardRef, useImperativeHandle, useRef } from "react";
import classNames from "classnames";
import Title from "./Title";
import { GetProps } from "antd";
import Tabs from "../Tabs";
import { useSafeState } from "ahooks";
import { useConstants } from "@/hooks";
import FilePreview from "./FilePreview";
import { ActionViewItemEnum } from "@/utils";

import BrowserList from "./BrowserList";
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
  plan?: CHAT.Plan;
  ref?: React.Ref<ActionViewRef>;
} & GetProps<typeof Title>;

const ActionViewComp: GenieType.FC<ActionViewProps> = forwardRef((props, ref) => {
  const { className, onClose, title, activeTask, taskList, plan } = props;

  const [curFileItem, setCurFileItem] = useSafeState<CHAT.TFile>();
  const planRef = useRef<PlanViewAction>(null);
  const { defaultActiveActionView, actionViewOptions } = useConstants();
  const [activeActionView, setActiveActionView] = useSafeState(defaultActiveActionView);

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
    <div className={classNames("flex h-full w-full flex-col bg-white/50", className)}>
      {/* Header Section */}
      <div className="flex flex-col gap-3 border-b border-[#e8e8ed] px-5 py-4">
        <Title onClose={onClose}>{title || "工作空间"}</Title>
        <Tabs
          value={activeActionView}
          onChange={setActiveActionView}
          options={actionViewOptions}
          className="min-h-[36px]"
        />
      </div>

      {/* Content Area */}
      <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
        <div className="flex-1 overflow-auto p-5">
          <FilePreview
            taskItem={activeTask}
            taskList={taskList}
            className={classNames({ hidden: activeActionView !== ActionViewItemEnum.follow })}
          />
          {activeActionView === ActionViewItemEnum.browser && <BrowserList taskList={taskList} />}
          {activeActionView === ActionViewItemEnum.file && (
            <FileList
              taskList={taskList}
              activeFile={curFileItem}
              clearActiveFile={() => {
                setCurFileItem(undefined);
              }}
            />
          )}
        </div>
        <PlanView plan={plan} ref={planRef} />
      </div>
    </div>
  );
});

// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-expect-error
const ActionView: typeof ActionViewComp & {
  useActionView: typeof useActionView;
} = ActionViewComp;
ActionView.useActionView = useActionView;

export default ActionView;
