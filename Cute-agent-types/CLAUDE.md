[根目录](../CLAUDE.md) > **ai-agent-station-study-types**

# ai-agent-station-study-types 模块

## 模块职责

提供项目基础类型定义，包括常量、枚举、异常类、任务调度接口等。本模块无任何外部业务依赖，被所有其他模块引用。

---

## 入口与启动

本模块为纯库模块，无启动类。

---

## 对外接口

### 任务调度接口
- `ITaskJobService`: 任务作业服务接口
- `ITaskDataProvider`: 任务数据提供者接口

### 异常类
- `AppException`: 应用异常基类
- `BizException`: 业务异常

### 常量与枚举
- `Constants`: 通用常量
- `ResponseCode`: 响应码枚举

---

## 关键依赖与配置

### 依赖
- `spring-boot-starter-web`: Spring Web 支持
- `lombok`: 代码简化
- `xstream`: XML 序列化
- `dom4j`: XML 解析
- `commons-lang3`: 工具类

---

## 数据模型

### 任务调度相关
- `TaskJob`: 任务作业注解
- `TaskScheduleVO`: 任务调度值对象
- `TaskJobAutoProperties`: 任务调度自动配置属性
- `TaskJobAutoConfig`: 任务调度自动配置

---

## 测试与质量

本模块主要为接口和常量定义，测试覆盖通过集成测试验证。

---

## 常见问题 (FAQ)

**Q: 为什么把任务调度放在 types 模块？**
A: 任务调度是基础设施能力，被 domain 和 trigger 模块共同依赖，放在 types 避免循环依赖。

---

## 相关文件清单

| 文件路径 | 说明 |
|---------|------|
| `src/main/java/org/wwz/ai/types/job/service/TaskJobService.java` | 任务作业服务实现 |
| `src/main/java/org/wwz/ai/types/job/service/ITaskJobService.java` | 任务作业服务接口 |
| `src/main/java/org/wwz/ai/types/job/model/TaskScheduleVO.java` | 任务调度 VO |
| `src/main/java/org/wwz/ai/types/job/config/TaskJobAutoProperties.java` | 自动配置属性 |
| `src/main/java/org/wwz/ai/types/job/config/TaskJobAutoConfig.java` | 自动配置类 |
| `src/main/java/org/wwz/ai/types/job/provider/ITaskDataProvider.java` | 数据提供者接口 |
| `src/main/java/org/wwz/ai/types/job/TaskJob.java` | 任务作业注解 |
| `src/main/java/org/wwz/ai/types/common/Constants.java` | 通用常量 |
| `src/main/java/org/wwz/ai/types/enums/ResponseCode.java` | 响应码枚举 |
| `src/main/java/org/wwz/ai/types/exception/AppException.java` | 应用异常 |
| `src/main/java/org/wwz/ai/types/exception/BizException.java` | 业务异常 |

---

## 变更记录 (Changelog)

### 2026-04-07
- 初始化模块文档
