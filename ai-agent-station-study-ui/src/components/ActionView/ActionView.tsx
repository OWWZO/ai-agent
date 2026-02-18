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
  /**
   * 显示文件
   * @param file 文件
   * @returns
   */
  setFilePreview: (file?: CHAT.TFile) => void;
  /**
   * 改变现实的面板
   */
  changeActionView: (item: ActionViewItemEnum) => void;
};

const useActionView = () => {
  const ref = useRef<ActionViewRef>(null);

  return ref;
};

type ActionViewProps = {
  title?: React.ReactNode;
  taskList?: (PanelItemType)[];
  activeTask?: CHAT.Task;
  plan?: CHAT.Plan;
  ref?: React.Ref<ActionViewRef>;
} & GetProps<typeof Title>;

const ActionViewComp: GenieType.FC<ActionViewProps> = forwardRef((props, ref) => {
  const { className, onClose, title, activeTask, taskList, plan } = props;

  const [ curFileItem, setCurFileItem ] = useSafeState<CHAT.TFile>();

  const planRef = useRef<PlanViewAction>(null);

  const { defaultActiveActionView, actionViewOptions } = useConstants();

  const [ activeActionView, setActiveActionView ] = useSafeState(defaultActiveActionView);

  useImperativeHandle(ref, () => {
    return {
      ...planRef.current!,
      setFilePreview: (file) => {
        setActiveActionView(ActionViewItemEnum.file);
        setCurFileItem(file);
      },
      changeActionView: setActiveActionView
    };
  });
  return (
    <div className={classNames("p-24 pt-8 pb-24 w-full h-full flex flex-col", className)}>
      <Title onClose={onClose}>{title || '工作空间'}</Title>
      <Tabs value={activeActionView} onChange={setActiveActionView} options={actionViewOptions} />
      {/* 展示区域 */}
      <div className='mt-12 flex-1 h-0 flex flex-col'>
        <FilePreview taskItem={activeTask} taskList={taskList} className={classNames({ 'hidden': activeActionView !== ActionViewItemEnum.follow })} />
        {activeActionView === ActionViewItemEnum.browser && <BrowserList taskList={taskList}/>}
        {activeActionView === ActionViewItemEnum.file && <FileList
          taskList={taskList}
          activeFile={curFileItem}
          clearActiveFile={() => {
            setCurFileItem(undefined);
          }}
        />}
      </div>
      <PlanView plan={plan} ref={planRef} />
    </div>
  );
});

// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-expect-error
const ActionView: typeof ActionViewComp & {
  useActionView: typeof useActionView
} = ActionViewComp;
ActionView.useActionView = useActionView;

export default ActionView;