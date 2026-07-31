会话工作区与文件复用模块提供会话级产物文件存储、元数据管理及跨层复用机制，支持Java Agent与Python工具服务在相同会话下安全访问共享文件路径与历史产物。

## 会话工作区核心设计

会话工作区通过request_id作为唯一会话标识符，为每个会话分配独立的子目录（如`file_db_dir/{safe_request_id}/`），实现文件隔离与复用。Python工具服务通过`FileDB`类将上传文件落盘到会话专用目录，本地路径包含request_id以便跨组件复用。

```mermaid
graph LR
    Agent(Java) --> FileManageAPI(HTTP)
    FileManageAPI --> FileInfoOp(SQLModel)
    FileInfoOp --> FileDB(Python local storage)
    FileDB --> RequestId(scope)
    RequestId --> SubDir(safe_scope)
    SubDir --> FilePath(absolute path)
    FilePath --> Preview/Download(URL)
```

Sources: [reactor-tool/reactor_tool/api/file_manage.py](reactor-tool/reactor_tool/api/file_manage.py#L34-L176)
Sources: [reactor-tool/reactor_tool/db/file_table_op.py](reactor-tool/reactor_tool/db/file_table_op.py#L19-L73)

## 文件元数据与CRUD操作

FileInfo表记录文件名、file_path、description、request_id及file_id（MD5派生）。add_by_content、add_by_file、add_by_existing_path等方法支持文本、二进制及已存在文件登记，统一通过normalize_stored_file_name去掉路径污染。

Sources: [reactor-tool/reactor_tool/db/file_table.py](reactor-tool/reactor_tool/db/file_table.py#L15-L33)
Sources: [reactor-tool/reactor_tool/db/file_table_op.py](reactor-tool/reactor_tool/db/file_table_op.py#L89-L150)

## 预览与下载URL生成

get_file_preview_url与get_file_download_url构造`/preview/{request_id}/{filename}`及`/download/{request_id}/{filename}`路径，兼容Java前端与Python工具服务。legacy_file_id规则确保历史兼容。

Sources: [reactor-tool/reactor_tool/api/file_manage.py](reactor-tool/reactor_tool/api/file_manage.py#L46-L175)
Sources: [reactor-tool/reactor_tool/model/protocal.py](reactor-tool/reactor_tool/model/protocal.py#L85-L93)

## Java侧文件适配与复用

ReactorToolFileArtifactAdapter实现FileArtifactPort，WorkspaceFileReadStateStore与WorkspaceReadStateStore维护会话级文件状态，支持WorkspaceWriteTool、WorkspaceReadTool等工具在Java端直接复用文件路径。

Sources: [Reactor-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/adapter/port/ReactorToolFileArtifactAdapter.java](Reactor-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/adapter/port/ReactorToolFileArtifactAdapter.java#L1-L50)
Sources: [Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/memory/WorkspaceReadStateStore.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/memory/WorkspaceReadStateStore.java#L1-L100)

## 文件服务与本地存储切换

util/file_util.py提供upload_file、upload_file_by_path、get_file_content等工具，优先尝试HTTP端点（ossUrl），否则回退本地FILE_SAVE_PATH目录，支持Markdown/二进制产物复用。

Sources: [reactor-tool/reactor_tool/util/file_util.py](reactor-tool/reactor_tool/util/file_util.py#L103-L197)
Sources: [reactor-tool/reactor_tool/model/protocal.py](reactor-tool/reactor_tool/model/protocal.py#L104-L114)

## 文件复用实践路径

在Plan-Execute或ReAct链路中，Agent通过FileTool或Workspace工具可直接引用request_id下的文件路径，Python skill与Java runtime均可复用同一会话文件，避免重复上传。

Sources: [reactor-tool/reactor_tool/tool/workspace/WorkspaceService.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/workspace/WorkspaceService.java#L1-L200)
Sources: [reactor-tool/reactor_tool/tool/sop_workspace.py](reactor-tool/reactor_tool/tool/sop_workspace.py#L129-L200)

## 配置文件与环境变量

FILE_SAVE_PATH默认`file_db_dir`，支持环境变量配置安全_scope字符清洗，避免Windows路径冲突。request_id应保持唯一性以确保文件隔离。

Sources: [reactor-tool/reactor_tool/db/file_table_op.py](reactor-tool/reactor_tool/db/file_table_op.py#L23-L36)
Sources: [reactor-tool/reactor_tool/model/protocal.py](reactor-tool/reactor_tool/model/protocal.py#L62-L64)

## 文件复用与上下文管理关系

会话工作区文件路径与WorkingMemoryService、SOP召回配合使用，压缩后仅保留必要文件元数据，结合MRAG检索实现历史产物复用。

Sources: [reactor-tool/reactor_tool/db/file_table_op.py](reactor-tool/reactor_tool/db/file_table_op.py#L175-L196)
Sources: [reactor-tool/reactor_tool/db/file_table_op.py](reactor-tool/reactor_tool/db/file_table_op.py#L189-L196)

## 后续阅读

- [工作记忆压缩与上下文管理](23-gong-zuo-ji-yi-ya-suo-yu-shang-xia-wen-guan-li)
- [端到端请求流转](10-duan-dao-duan-qing-qiu-liu-zhuan)
- [Java 后端启动与配置](4-java-hou-duan-qi-dong-yu-pei-zhi)