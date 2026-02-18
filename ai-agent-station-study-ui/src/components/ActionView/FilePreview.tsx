import classNames from "classnames";
import { useEffect, useMemo, useState } from "react";
import ActionPanel, { PanelItemType, useMsgTypes } from "../ActionPanel";
import { useBoolean, useMemoizedFn } from "ahooks";
import { Modal, Slider } from "antd";
import ActionViewFrame from "./ActionViewFrame";
import dayjs from "dayjs";

const STEP_CLASS = 'flex items-center justify-center rounded-[4px] size-16 font_family icon-fanhui cursor-pointer hover:bg-gray-300';

const Title: GenieType.FC<{
  taskItem?: PanelItemType;
}> = (props) => {
  const { taskItem } = props;

  const [ overlay, { setFalse: closeOverlay, setTrue: openOverlay } ] = useBoolean(false);

  const title = useMemo(() => {
    if (!taskItem) {
      return '';
    }
    const { messageType, resultMap } = taskItem;
    if (messageType === 'tool_result') {
      return taskItem.toolResult?.toolName;
    }
    if (messageType === 'file' || messageType === 'html') {
      const [fileInfo] = resultMap?.fileInfo || [];
      return fileInfo?.fileName || messageType;
    }
    if (messageType === 'deep_search' && resultMap.messageType === 'report') {
      return resultMap?.query;
    }
    return messageType;
  }, [taskItem]);

  const { useHtml, useExcel } = useMsgTypes(taskItem) || {};

  return <>
    <div
      className={classNames(
        'h-34 w-full border-b-[#e9e9f0] border-b-1 border-solid pl-[16px] pr-[16px] flex items-center justify-center text-[12px] font-semibold',
        (useHtml || useExcel) ? 'hover:text-primary cursor-pointer' : ''
      )}
      onClick={(useHtml || useExcel) ? openOverlay : undefined}
    >
      {title}
    </div>
    <Modal
      destroyOnHidden
      open={overlay}
      onCancel={closeOverlay}
      footer={null} // 如果不需要底部按钮，可以设置为 null
      styles={{
        body: {
          height: '100vh',
          boxSizing: 'border-box'
        },
        content: {
          borderRadius: 0,
          position: 'absolute',
          width: '100%',
          height: '100%',
          top: 0,
          left: 0,
          padding: 0,
        }
      }}
      className="top-0 m-0 h-[100vh] overflow-hidden max-w-[100vw] rounded-none"
      width="100vw" // 使 Modal 宽度为 100%
    >
      <ActionPanel className="flex-1 h-full" taskItem={taskItem} noPadding />;
    </Modal>
  </>;
};

const FilePreview: GenieType.FC<{
  /**
   * 文件信息
   */
  taskItem?: CHAT.Task;
  taskList?: PanelItemType[]
}> = (props) => {
  const { taskItem: defaultTaskItem, className, taskList: taskListProp } = props;

  const taskList = useMemo(() => {
    return taskListProp?.filter((item) => ![ 'task_summary', 'result' ].includes(item.messageType));
  }, [taskListProp]);

  const [curActiveTaskIndex, setCurActiveTaskIndex] = useState<number>();

  let taskItem = typeof curActiveTaskIndex === 'number' ? (taskList?.[curActiveTaskIndex] || defaultTaskItem) : (defaultTaskItem);

  if (!taskItem) {
    taskItem = taskList?.[taskList.length - 1];
  }

  useEffect(() => {
    // 变化之后重新归为，选择的状态
    if (defaultTaskItem) {
      setCurActiveTaskIndex(undefined);
    }
  }, [defaultTaskItem]);

  const realActiveTaskIndex = useMemo(() => {
    const index = taskList?.findIndex((item) => item.id === taskItem?.id);
    return index || 0;
  }, [taskItem?.id, taskList]);

  const taskLength = taskList?.length || 0;

  const next = useMemoizedFn(() => {
    setCurActiveTaskIndex(Math.min(taskLength - 1, realActiveTaskIndex + 1));
  });

  const pre = useMemoizedFn(() => {
    setCurActiveTaskIndex(Math.max(0, realActiveTaskIndex - 1));
  });

  const slideMaxVal = taskLength - 1;

  return (
    <ActionViewFrame
      className={classNames('h-full', className)}
    >
      <Title taskItem={taskItem} />
      <ActionPanel className="flex-1 h-0 my-8 overflow-y-auto" taskItem={taskItem} allowShowToolBar />
      {!!taskLength && <div className="w-full border-t-[#e9e9f0] border-t-1 border-solid flex items-center h-[38px] px-16">
        <i className={STEP_CLASS} onClick={pre}></i>
        <i className={classNames(STEP_CLASS, 'rotate-180')} onClick={next}></i>
        <Slider
          className="flex-1 text-primary"
          styles={{ track: { background: '#4040FFB2' } }}
          step={1}
          onChange={setCurActiveTaskIndex}
          // slider 的value和max都为0的时候会显示在最左边，所以这里给个默认值1
          value={slideMaxVal ? realActiveTaskIndex : 1}
          min={0}
          max={slideMaxVal || 1}
          tooltip={{
            formatter: () => {
              const { messageTime } = taskList?.[realActiveTaskIndex] || {};
              return dayjs((+messageTime!)).format('YYYY-MM-DD HH:mm');
            },
          }}
        />
      </div>}
    </ActionViewFrame>
  );
};

export default FilePreview;