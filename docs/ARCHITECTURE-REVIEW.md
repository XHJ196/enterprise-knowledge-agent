# 架构交付核对

日期：2026-11-18

## 覆盖范围

- [ARD](ARD.md) 按模块说明归属、职责、非职责、依赖及调用方式，覆盖用户要求的八类模块，并明确 AI Chat 两侧的分工。
- [总体架构](diagrams/architecture.html) 标注前端、Spring Boot、FastAPI、PostgreSQL + pgvector、文件存储、外部模型服务及通信协议。
- [上传与处理流程](diagrams/flow-a-upload.html) 包含上传、持久化任务、受限文件读取、解析、Embedding、待发布数据写入、结果回调、条件发布及失败分支。
- [AI 问答流程](diagrams/flow-b-chat.html) 包含鉴权、检索范围、模型调用、两层 SSE、引用校验、持久化及停止/超时分支。
- PRD 副本与原项目的 PRD 内容一致；本次没有修改原 PRD。

## 图文件验证

三张 HTML 均通过结构校验、交付检查、图检查和浏览器自动检查。HTML 无外部脚本依赖，可独立打开。

| 图 | 最终检查结果 |
| --- | --- |
| 总体架构 | [PASS](diagrams/review-architecture-3/architecture.finalize-summary.json) |
| 流程 A | [PASS](diagrams/review-flow-a-2/flow-a-upload.finalize-summary.json) |
| 流程 B | [PASS](diagrams/review-flow-b-2/flow-b-chat.finalize-summary.json) |

## 验证边界

这是待实施的架构设计，尚无应用代码，因此上述检查不代表服务联调、性能测试或业务验收已经完成。接口、数据库迁移和任务拆分在实施阶段继续细化。

Agent 仅预留边界；第一版不启用工具执行。本地模型适配保留为后续工作。

## 放回原项目

压缩包保留 `docs/` 相对结构。将其中的 `ARD.md`、本核对文件和 `diagrams/` 复制到项目的 `docs/` 目录，ARD 内的图链接即可继续使用。包中的 PRD 是本次设计依据的快照；若原项目已有更新，应保留更新后的版本。
