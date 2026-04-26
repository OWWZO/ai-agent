## ADDED Requirements

### Requirement: Workspace image generation SHALL be submitted through Java backend APIs
生图工作台的文生图与图生图请求 SHALL 通过 Java 后端业务接口提交，浏览器 MUST NOT 再直接依赖 Python 工具服务地址完成图片生成。

#### Scenario: Text-to-image request is submitted from workspace
- **WHEN** 用户在生图工作台提交文生图请求
- **THEN** 前端必须调用 Java 生图接口，由 Java 后端再调用配置好的 Python 图片生成服务

#### Scenario: Image edit request is submitted from workspace
- **WHEN** 用户在生图工作台提交带参考图或遮罩图的图生图请求
- **THEN** Java 后端必须接收并转发这些输入给 Python 图片生成服务，而不是让前端直接请求 Python 接口

### Requirement: Java backend SHALL return normalized generated image results for workspace usage
Java 后端 SHALL 把 Python 图片生成结果转换为前端工作台可直接消费的统一响应，且响应 MUST 至少包含请求标识、生成摘要和可预览/下载的图片文件信息列表。

#### Scenario: Python generation succeeds
- **WHEN** Python 图片生成服务返回成功的 `fileInfo` 列表
- **THEN** Java 后端必须返回包含 `requestId`、结果摘要以及每张图片文件元数据的成功响应给前端

#### Scenario: Python generation fails
- **WHEN** Python 图片生成服务返回错误或超时
- **THEN** Java 后端必须向前端返回失败结果，并透出可用于排障的错误信息

### Requirement: Successful generated images SHALL be persisted as history records
每张成功生成的结果图片 SHALL 由 Java 后端落库形成可查询的历史记录；同一次生成请求下的多张图片 MUST 共享同一个 `requestId` 并使用顺序字段区分。

#### Scenario: Single request generates multiple images
- **WHEN** 一次生成请求成功返回多张结果图片
- **THEN** Java 后端必须为每张图片分别创建一条历史记录，并保存该批次的请求摘要与图片文件元数据

#### Scenario: Generation request does not produce successful images
- **WHEN** 生成请求失败，或上游没有返回可识别的成功图片结果
- **THEN** Java 后端不得写入成功历史记录

### Requirement: Workspace history SHALL be queryable by request batch for the current device scope
系统 SHALL 提供生图历史查询接口，并按单次生成请求聚合返回历史记录；历史结果 MUST 按当前设备范围隔离，并按时间倒序展示最新批次。

#### Scenario: Device queries generation history
- **WHEN** 前端携带当前 `X-Device-Id` 请求生图历史列表
- **THEN** Java 后端必须只返回该设备可见的历史批次，并在每个批次中包含对应的结果图片列表

#### Scenario: No history exists for device
- **WHEN** 当前设备下还没有任何已持久化的生图记录
- **THEN** 历史查询接口必须返回空列表，而不是报错或返回其他设备的数据
