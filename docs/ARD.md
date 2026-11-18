# 企业知识库 Agent 平台 ARD

**版本：** v1.0（架构设计稿）  
**日期：** 2026-11-18  
**依据：** [PRD v1.0](PRD.md)，个人作品集、本地运行、外部模型 API。  
**性质：** 待实施的系统设计；本文中的接口、表名和进程安排是技术方案，不表示已有代码实现。  
**原项目：** `C:/Users/ASUS/Desktop/enterprise-knowledge-agent`。本交付目录保留同样的 `docs` 结构。

## 1. 架构结论与阅读顺序

第一版采用两个应用服务：Spring Boot 承担业务入口、身份与权限、业务状态和会话持久化；FastAPI 承担文档处理、检索与回答生成。前端只调用 Spring Boot，不直接调用 FastAPI、数据库、文件目录或模型供应商。

模块是各服务内部的代码边界，不为每个模块独立部署微服务。同一服务内使用应用服务接口调用；跨服务使用 HTTP。初始实现不引入 Redis、Kafka、RabbitMQ、向量数据库集群或 Agent 编排框架。

建议先阅读模块归属表，再看两条时序图，最后核对接口和数据所有权：

- [总体架构图](diagrams/architecture.html)：服务、数据与信任边界，含协议方向。
- [流程 A：文档上传与处理](diagrams/flow-a-upload.html)：上传、持久化任务、异步处理、条件发布与失败分支。
- [流程 B：AI 问答](diagrams/flow-b-chat.html)：鉴权、检索、模型流式调用、引用、持久化与停止分支。

两条核心流程使用时序图表达数据流，纵向表示先后顺序，不表示实测耗时。错误分支与成功路径互斥。

## 2. 部署单元与协议

| 部署单元 | 本地建议地址或位置 | 对外职责 | 通信方式 |
| --- | --- | --- | --- |
| 前端 | `127.0.0.1:5173` | 管理页、员工页、文件上传、聊天流式展示 | `fetch` 请求 Spring；普通 REST、multipart、SSE 格式流 |
| Spring Boot | `127.0.0.1:8080` | 唯一浏览器业务入口，业务状态的权威服务 | 对前端 HTTP；对 FastAPI 内部 HTTP；JDBC 访问 PostgreSQL |
| FastAPI | `127.0.0.1:8000` | 内部 AI 服务、受控文档处理执行器 | 内部 HTTP/JSON、SSE；psycopg 访问 AI 数据；HTTPS 调用模型 |
| PostgreSQL + pgvector | `127.0.0.1:5432` | 业务数据与向量数据持久化，同一实例分 schema 和数据库角色 | PostgreSQL wire protocol / SQL |
| 文件存储 | 配置项 `STORAGE_ROOT` 指向的本地目录 | 保存原文件和上传临时文件 | 仅 Spring 经文件系统 API 读写；不开放静态目录 |
| 外部模型服务 | 后端配置供应商地址与模型名 | 回答生成与 Embedding | HTTPS/JSON；生成接口支持流式响应 |

本地仅绑定 loopback 时使用 HTTP；后续部署到其他主机或公网时再补充 HTTPS、网络隔离和凭证管理。浏览器开发代理可以把 `/api` 转发到 Spring。使用浏览器 Session Cookie 时配置凭证和 CSRF，不能直接以开放 CORS 替代认证。

文件下载经过 Spring 重新鉴权后返回 `application/octet-stream` 或相应媒体类型。服务间文件读取走受限二进制 HTTP 流，FastAPI 不接受任意本地路径和任意 URL。

### 2.1 为什么采用这种拆分

| 方案 | 优点 | 代价及结论 |
| --- | --- | --- |
| **业务集中在 Spring，AI 计算集中在 FastAPI** | 权限和状态只有一套；利用 Java 业务工程与 Python 文档/模型生态 | 需要设计异步任务、HTTP 契约和跨服务失败恢复；本文采用 |
| 浏览器分别调用两个服务 | AI 流式链路表面较短 | 身份和授权逻辑容易复制，会话与状态跨入口不一致；不采用 |
| 拆成多个独立微服务并引入消息中间件 | 便于大规模独立扩容 | 超出本地作品集需要，增加部署和一致性成本；第一版不采用 |

## 3. 模块归属总表

| 模块 | 归属 | 权威数据或输出 | 第一版状态 |
| --- | --- | --- | --- |
| Authentication | **Spring Boot** | 身份、登录态、管理员密码校验结果 | 实现 |
| User Management | **Spring Boot** | 用户资料、固定角色、员工列表 | 实现 |
| Knowledge Base | **Spring Boot** | 知识库、授权关系、删除流程 | 实现 |
| Document Management | **Spring Boot** | 元数据、文件版本、文档状态、发布决定 | 实现 |
| Document Processing | **FastAPI** | 解析结果、切片、Embedding、待发布索引 | 实现 |
| Retrieval | **FastAPI** | 在已授权范围中的证据片段 | 实现 |
| AI Chat | **Spring Boot 主模块 + FastAPI Answer Generation 子模块** | Spring 持有会话和生成状态；FastAPI 产出回答事件 | 实现，分工见下文 |
| Agent | **FastAPI** | 将来的工具选择与调用计划 | **仅预留边界，不执行工具** |
| File Storage Adapter | **Spring Boot** | 存储键与原文件读写 | 支撑模块 |
| Processing Job Coordinator | **Spring Boot** | 持久化任务、截止时间、接收结果 | 支撑模块 |
| Model Gateway | **FastAPI** | 供应商 API 适配、流式解析、Embedding 调用 | 支撑模块 |

AI Chat 的拆分不是两份会话系统：只有 Spring 能写会话和消息。FastAPI 的 Answer Generation 是无会话所有权的推理执行能力。

## 4. 每个模块的四项边界

### 4.1 Authentication — Spring Boot

**它负责什么？** 处理登录、登录态建立与校验；在注册时调用 User Management 创建普通员工；验证删除非空知识库时输入的当前管理员密码。密码以支持既定输入长度范围的自适应单向哈希保存，例如 Argon2id，不能明文保存或返回。第一版可采用 Spring Security + 服务端 Session，数据库保存需要持久化的 Session。

**它不负责什么？** 不决定某员工能访问哪个知识库，不解析文档，不在 FastAPI 中复制用户密码或 Session 表。登录成功不等于对全部资料有权。

**它依赖哪些模块？** User Management 提供账号查询和创建；PostgreSQL 保存账号、凭证哈希与 Session。它不依赖 AI 服务可用性。

**其他模块如何调用它？** 浏览器调用 `POST /api/auth/register`、`POST /api/auth/login`、`GET /api/auth/me`；Spring Security 在请求入口生成 `CurrentUser`。同进程模块调用 `requireAuthenticated()`、`requireAdmin()`、`verifyCurrentPassword()`。FastAPI 只验证服务间凭证和请求作用域，不校验浏览器密码。

### 4.2 User Management — Spring Boot

**它负责什么？** 用户名规范化与唯一性、普通员工注册、固定 `ADMIN/EMPLOYEE` 角色、演示账号初始化、管理员员工列表。已确认的账号操作范围仅为员工列表与知识库授权入口。

**它不负责什么？** 不实现账号停用、删除、密码重置或角色变更；不持有文档解析逻辑；不自行保存第二份知识库授权表。

**它依赖哪些模块？** PostgreSQL。请求身份由 Authentication 在入口校验后传入。员工列表与已授权知识库在入口应用层组合：Knowledge Base 的管理应用服务读取 User Management 的只读查询及自身授权数据，User Management 核心不反向依赖 Knowledge Base，避免循环依赖。

**其他模块如何调用它？** `GET /api/users` 仅管理员；内部 `UserQueryService.findById()` / `listEmployees()` / `createEmployee()`。授权修改明确委托 Knowledge Base，不直接跨模块改授权实体。

### 4.3 Knowledge Base — Spring Boot

**它负责什么？** 名称、简介、创建与修改；员工与知识库授权；`assertCanRead(userId,kbId)`；知识库可用/删除中/删除未完成等内部管理状态；非空删除的密码确认与文档逐一清理；清理完成再移除授权并删除知识库。

**它不负责什么？** 不进行文本切片或向量相似度计算；不根据问题文本让 LLM 判定权限；不把“移除所有授权”当作账号停用。

**它依赖哪些模块？** Authentication、User Management 的只读用户查询、Document Management 的文档清理接口、PostgreSQL。批量清理的进度保存在数据库。

**其他模块如何调用它？** 前端走 `/api/knowledge-bases` CRUD 与 `/api/knowledge-bases/{kbId}/members/{userId}` 授权接口；Document Management、AI Chat 同进程调用 `KnowledgeBasePolicy`。FastAPI 通过 Spring 签发的受限执行上下文获取范围，不能直接修改授权。

### 4.4 Document Management — Spring Boot

**它负责什么？** 文件格式/大小/重名检查、所属知识库、原文件存储键、文档 ID、内部文件版本、三种展示状态；创建处理任务；处理成功后的条件发布；替换时停止旧内容服务；删除、下载与引用版本有效性检查。

**它不负责什么？** 不实际解析 PDF、DOCX 或 Markdown，不生成 Embedding，不通过 FastAPI 的一个成功响应直接无条件发布，不把文件接收完成等同于可检索。

**它依赖哪些模块？** Authentication、Knowledge Base、File Storage Adapter、Processing Job Coordinator、PostgreSQL。派发和清理通过 FastAPI 内部接口执行，但最终业务状态只能由本模块修改。

**其他模块如何调用它？** 前端调用 `/api/knowledge-bases/{kbId}/documents`、`/api/documents/{id}/file`、`/retry`、`DELETE /api/documents/{id}`；处理器通过限定 job 的内部接口读取文件并上报结果；AI Chat 通过 `DocumentAvailabilityService` 核对引用及当前版本。模块直接访问文件存储的动作必须经过 Storage Adapter。

### 4.5 Document Processing — FastAPI

**它负责什么？** 接受 Spring 已创建的处理任务；受限读取原文件；提取正文/表格文字和 PDF 页码；切片并记录可追溯位置；调用 Model Gateway 生成 Embedding；将切片和向量按任务尝试隔离写入 `ai` schema；完成后向 Spring 上报清单。

**它不负责什么？** 不接受浏览器上传入口，不创建用户或授权，不自行决定资料可被员工读取，不直接修改 `core.documents.status`，不自行发布文档，不启动全库 OCR 或抓取 Markdown 外链。

**它依赖哪些模块？** Spring 的受限文件读取与任务结果接口、Model Gateway、PostgreSQL 的 `ai` schema。CPU 解析在受控工作进程中执行，不阻塞 FastAPI 事件循环和问答流。

**其他模块如何调用它？** Spring 通过 `POST /internal/processing-jobs` 异步提交，返回 `202` 只表示接收；`jobId + attemptId` 相同的重复提交不得再次启动执行。取消使用 `POST /internal/processing-jobs/{jobId}/cancel`。结果使用 FastAPI → Spring 的认证回调。第一版本地一个处理工作进程，不引入 Celery 或外部 broker。

### 4.6 Retrieval — FastAPI

**它负责什么？** 在 Spring 签发的单一知识库范围中，将问题向量化，查询 pgvector，取回相关切片和文档/版本/页码位置，生成证据清单。初始使用固定上限的 Top-K 检索，具体参数通过 20 组验收案例调优。

**它不负责什么？** 不自行认定 `userId` 有访问权，不允许调用方不传范围就全库检索，不保存聊天历史，不把相似度分数等同于答案正确率，也不负责对外提供独立搜索产品功能。

**它依赖哪些模块？** Model Gateway 的查询 Embedding、`ai.retrievable_chunks` 只读视图、Spring 生成的签名作用域。视图只包含当前版本已发布文档和处于可用状态的知识库。

**其他模块如何调用它？** 第一版由 FastAPI 内的 Answer Generation 调用 `retrieve(query, verifiedScope)`，属于 Python 函数/接口调用。未来 Agent 也只能通过相同受限接口调用。Spring 通过生成接口间接使用，不需要把向量算法复制到 Java。

### 4.7 AI Chat — Spring Boot

**它负责什么？** 会话归属、知识库绑定、消息顺序、最近 10 轮上下文筛选、问题长度、最后一轮重试、最近问题编辑、同会话单生成约束；调用 FastAPI；接收流式事件；持续校验访问和任务有效性；保存部分/完整回答；引用持久化及后续查看鉴权。

**它不负责什么？** 不直接编写供应商 SDK 调用、不做文件解析、不把历史 AI 回答当证据、不让浏览器直接取得 FastAPI 或供应商凭证。

**它依赖哪些模块？** Authentication、Knowledge Base、Document Management、PostgreSQL，以及 FastAPI 的 Answer Generation。会话写入和状态更新由 Spring 一个模块负责。

**其他模块如何调用它？** 前端调用会话 REST 接口和 `POST /api/conversations/{id}/generations`，通过 `fetch` 读取 `text/event-stream`；停止走独立 POST；引用走 `/api/citations/{id}`。服务内部使用 `ChatApplicationService`。前端不能通过提交其他账号的 conversationId 绕过归属检查。

### 4.8 Answer Generation — FastAPI，AI Chat 的计算子模块

**它负责什么？** 使用受限上下文理解问题；必要时改写追问以利检索；调用 Retrieval；依据证据构造提示词；处理无依据、部分依据和冲突；调用 Model Gateway；输出 `delta/citations/done/error` 事件。证据和引用 ID 来自检索结果，不能由模型虚构。

**它不负责什么？** 不创建或写入业务会话，不授予访问权，不认为生成完成就意味着 Spring 已保存回答，不把模型输出的文件名/页码直接当可信引用。

**它依赖哪些模块？** Retrieval、Model Gateway、签名执行作用域。第一版不依赖 Agent 规划循环。

**其他模块如何调用它？** Spring 调用 `POST /internal/generations`，接收 SSE 格式流；取消走 `POST /internal/generations/{runId}/cancel`。同一 `runId` 重复创建必须返回冲突或现有执行状态，不重复调用模型。

### 4.9 Agent — FastAPI，后续能力

**它负责什么？** 后续按需增加工具登记、选择、调用及结果整理；复用 Retrieval 和 Model Gateway。第一版 ARD 只定义归属与扩展边界。

**它不负责什么？** 第一版不实现工具执行、自动修改业务数据、多 Agent 协作或复杂工作流；不得绕过 Spring 权限，不能给 LLM 直接暴露数据库凭证、任意文件系统或管理接口。

**它依赖哪些模块？** 后续依赖受限的 Retrieval、Model Gateway 和 Spring 明确暴露的工具 API。工具需要额外动作授权，知识库读权限不能自动转成写权限。

**其他模块如何调用它？** 后续由 Answer Generation 在显式启用工具的模式中调用 Python `AgentService`。第一版没有公共 Agent endpoint，总体图的“后续”连接不代表当前请求会经过 Agent。

### 4.10 支撑模块

| 模块 | 它负责什么 | 它不负责什么 | 依赖 | 其他模块如何调用 |
| --- | --- | --- | --- | --- |
| File Storage Adapter（Spring） | 生成不可猜测存储键、临时落盘、校验成功后固定存储、授权后的文件流和物理删除 | 不解析文档，不把用户文件名拼成任意路径，不开放原目录 | 本地文件系统 | `put/open/delete(storageKey)` 同进程接口；FastAPI 经任务限定 HTTP 读取 |
| Processing Job Coordinator（Spring） | 文档模块内部的应用层协作器：事务内记录任务、派发、300 秒 deadline、回调校验、重启恢复、取消 | 不解析文件，不把新建任务当发布，不自动重跑已失败任务 | 文档领域服务、PostgreSQL、FastAPI HTTP | 文档应用层调度；`/internal/processing-jobs/{jobId}/result` 接收结果，不与文档入口服务互相注入 |
| Model Gateway（FastAPI） | 回答/Embedding 的供应商适配、密钥注入、流式解析、错误归一、取消 | 不决定授权，不保存业务会话，不把 429/超时伪装为资料不足 | 外部 LLM/Embedding API | Python `generate()` / `embed()`；第一版仅外部 API，后续接入本地实现 |

## 5. 数据所有权及发布机制

### 5.1 同一 PostgreSQL 实例，分 schema 和数据库角色

| 数据集合 | 主要字段示例 | 写入方 | 读取及边界 |
| --- | --- | --- | --- |
| `core.users`、登录 Session | 用户、密码哈希、角色、Session | Spring | FastAPI 无读取密码/Session 的权限 |
| `core.knowledge_bases`、`core.kb_members` | 名称、简介、管理状态、员工授权 | Spring | FastAPI 无业务表写权限 |
| `core.documents` | documentId、kbId、currentVersion、status、publishedAttemptId、storageKey | Spring | 仅暴露给 AI 受控的可检索视图，不授予文件路径写权限 |
| `core.processing_jobs`、删除进度 | jobId、documentVersion、attemptId、dispatchState、deadlineAt、terminalState | Spring | FastAPI 通过内部 API 接收任务和上报，不直接改业务状态 |
| `core.conversations/messages/generation_runs/citations` | owner、kbId、runId、正文、生成状态、引用版本 | Spring | FastAPI 只接收本次必要上下文，无会话表写权限 |
| `ai.document_chunks` | documentId、documentVersion、attemptId、chunkId、text、page/locator、embedding、modelId | FastAPI | Spring 仅有核验 manifest 和引用所需的只读权限 |
| `ai.retrievable_chunks` | 当前发布的切片/向量与定位字段 | 数据库视图；部署迁移创建 | FastAPI 检索只读；不是全体切片表的无过滤查询 |

数据库迁移统一由一个迁移入口执行，创建 schema、角色、表及视图；运行时 Spring 和 FastAPI 各用自己的最小权限数据库角色。不要让两个 ORM 各自自动创建和修改同一批表。

### 5.2 防止“向量已写入但文档尚未发布”

1. 文档接收成功后，Spring 在一个业务事务中设置 `PROCESSING` 并建立任务。替换文件时增加内部 `documentVersion`，旧引用立即失效。
2. FastAPI 先将本次切片按 `(documentId, documentVersion, attemptId, chunkId)` 写入 AI 表并提交。未发布的切片是待用数据，不对 Retrieval 可见。
3. FastAPI 回调成功时，Spring 校验任务仍有效、版本仍为当前、知识库未删除、deadline 未过、切片数量/模型标识与已提交 manifest 一致。
4. Spring 在事务中设置 `PUBLISHED` 及 `publishedAttemptId`。只读视图只有在 `kb=ACTIVE`、`document=PUBLISHED`、`chunk.documentVersion=currentVersion` 且 `chunk.attemptId=publishedAttemptId` 时才返回内容。
5. 因而 FastAPI 先提交向量并不会提前发布；Spring 没有成功发布也不会出现“页面成功、检索数据未准备好”。不依赖跨服务分布式事务。

内部文件版本和尝试 ID 只用于一致性，不向用户提供历史版本查看或回滚能力。

文档的删除标记属于内部生命周期控制字段，与面向用户的 PROCESSING/FAILED/PUBLISHED 三种处理状态分开。删除中或已删除的记录始终被访问与检索过滤，不能因为保留状态或 tombstone 而重新提供内容。

### 5.3 授权不是向量相似度过滤的后处理

Spring 签发执行作用域前检查 Session、会话归属、知识库授权与可用状态。作用域绑定 `userId/kbId/runId/expiresAt`，并经服务间签名防篡改。FastAPI 验证来源和有效期后使用单一 `kbId` 查询，不接受从浏览器透传的任意 `allowedKbIds`。

检索 SQL 必须先限定 `kbId`、发布版本与模型标识，再选取相似切片。不能先全库召回，再在生成后删掉越权引用。第一版数据规模小，精确向量距离排序可先实现；ANN 索引属于后续实测优化，不改变授权过滤。

FastAPI 有读取已发布 AI 数据的技术能力，但没有决定业务授权的职责。最终向用户转发内容、返回引用和下载时，Spring 再校验当前权限与内容有效性。服务被攻陷的隔离能力不等于“签名范围实现了数据库行级安全”；当前是双服务之间的受信内部边界。

## 6. 服务间契约

### 6.1 文档处理接口

| 调用方向 | 接口草案 | 请求要点 | 响应及语义 |
| --- | --- | --- | --- |
| 前端 → Spring | `POST /api/knowledge-bases/{kbId}/documents` | multipart 单文件、Session、请求去重键 | `202` 返回 documentId、jobId、PROCESSING；不表示发布 |
| Spring → FastAPI | `POST /internal/processing-jobs` | jobId、documentId、kbId、documentVersion、attemptId、deadlineAt、fileTicket、embeddingModelId | `202 ACCEPTED`；同尝试重复投递不重复执行 |
| FastAPI → Spring | `GET /internal/processing-jobs/{jobId}/file` | 服务凭证和 fileTicket，绑定任务/版本/有效期 | `200` 文件流；过期、撤销或版本变更返回 `409/410` |
| FastAPI → Spring | `POST /internal/processing-jobs/{jobId}/result` | attemptId、version、SUCCEEDED/FAILED、chunkCount、manifestHash、modelId、错误码 | Spring 条件接纳；旧尝试 `409`，重复同结果返回已记录结果 |
| Spring → FastAPI | `POST /internal/processing-jobs/{jobId}/cancel` | attemptId、原因 | 幂等取消；迟到结果仍由 Spring 拒绝 |
| Spring → FastAPI | `DELETE /internal/documents/{documentId}/indexes` | 要删除的版本/尝试，或该文档全部索引 | 幂等清理 AI 数据；失败由删除流程保留进度 |
| 前端 → Spring | `GET /api/documents/{id}` / `POST /api/documents/{id}/retry` | 当前用户，重试仅管理员 | 查询状态 / 为同一文档创建新尝试 |

跨服务提交使用 `X-Service-Key`（后端环境变量）及每次任务或生成的签名作用域。日志只记录 ID 和错误码，不记录密钥。回调错误码至少区分 `UNSUPPORTED_CONTENT`、`NO_TEXT`、`PARSER_FAILED`、`MODEL_UNAVAILABLE`、`PROCESSING_TIMEOUT`、`CANCELLED`、`STALE_ATTEMPT`。

任务表与业务状态在同一事务写入。Spring 调度器在事务提交后从任务表派发；网络不确定时允许在原 `jobId/attemptId` 上重复投递，同一处理尝试只能被 FastAPI 接纳一次。此处是命令投递去重，不是自动重新处理失败文档。明确收到拒绝、任务失败或期限到达后，转为失败并等待管理员手动重试。

FastAPI 的进程内待执行队列和受控解析进程不是持久化队列。持久化事实在 Spring 的任务表；服务重启不会自动复活运行中的任务。Spring 重启将旧运行标记失败；FastAPI 重启导致未完成执行丢失时，由拒绝状态或 300 秒 deadline 使任务结束，管理员手动重试。不能只用 `BackgroundTasks` 而不记录任务。

### 6.2 问答、流式与取消接口

| 调用方向 | 接口草案 | 说明 |
| --- | --- | --- |
| 前端 → Spring | `POST /api/conversations/{id}/generations` | 提交 question、客户端去重键；Spring 创建 runId，绑定单一 kbId |
| Spring → FastAPI | `POST /internal/generations` | runId、问题、最近最多 10 轮有效上下文、签名作用域、deadline；返回 SSE 流 |
| 前端 → Spring | `POST /api/generations/{runId}/stop` | 主动停止并记录 STOPPED；不是只关闭浏览器连接 |
| Spring → FastAPI | `POST /internal/generations/{runId}/cancel` | 请求释放上游生成；关闭流，旧 run 结果不得落入新 run |
| 前端 → Spring | `POST /api/generations/{runId}/retry` | 仅最后一轮 INCOMPLETE；新建执行 run，替换对应回答，不新增用户问题 |
| 前端 → Spring | `PATCH /api/conversations/{id}/latest-question` | 停止后修改最近问题；新 run 替换回答与引用，保留旧 run ID 防止迟到写入 |
| 前端 → Spring | `GET /api/citations/{id}` | 核对归属、当前授权、文档/版本有效性后返回真实片段及 PDF 页码 |
| 前端 → Spring | `GET /api/documents/{id}/file` | 核对当前权限和版本后流式下载，不暴露文件系统路径 |

SSE 使用 POST 响应体的 `text/event-stream`，前端以 `fetch` 读取。原生 `EventSource` 只适合 GET，不能直接拿它发送上述 POST 问题。第一版不增加 WebSocket 或流断点续传。

事件结构示例（字段为接口契约，不是伪造的模型结果）：

```json
{"event":"delta","runId":"run-...","seq":7,"data":{"text":"报销需要"}}
{"event":"citations","runId":"run-...","seq":8,"data":{"items":[{"evidenceId":"e1","documentId":"doc-...","documentVersion":2,"chunkId":"chunk-...","page":3}]}}
{"event":"done","runId":"run-...","seq":9,"data":{}}
```

真正编码时按 SSE 的 `id/event/data` 行封装，UTF-8 编码；上面展示的是事件字段。FastAPI 的 `done` 是计算完成信号，Spring 验证来源、持久化回答与引用成功后，才向前端发送最终 `done`。服务间事件和前端事件统一携带 runId、递增 seq，禁止把旧 run 的片段追加到新回答。

引用的文件名、页码和片段由已检索的切片记录决定，模型只输出可识别的证据标记。FastAPI 拒绝未知标记；Spring 落库前再次核验引用属于本次证据集合和当前有效版本。最终是否充分支撑结论仍需 20 组人工质量验收，结构校验不能替代语义验证。

Spring 在转发每批正文及引用前检查该 run 仍为当前活动执行、会话仍存在、知识库授权仍有效。撤权、删除或取消后，停止转发并尝试取消 FastAPI；已经看见或下载的内容无法收回。生成期间发现本次证据的文档已被替换或删除时，终止本次生成并标记来源变更，避免继续输出已失效依据。

## 7. 流程 A：文档上传与处理

对应 [流程 A 时序图](diagrams/flow-a-upload.html)。

| 步骤 | 执行方与数据变化 | 跨边界通信及完成条件 |
| --- | --- | --- |
| A01 接收上传 | Spring 认证管理员、知识库可用性，校验格式、10 MB、空文件、同库重名 | 前端 → Spring，HTTP multipart；非法输入在处理前拒绝 |
| A02 保存原文件 | Storage Adapter 将临时文件固定为不可变 storageKey，用户文件名仅是元数据 | Spring → 本地文件系统；落盘失败不创建可处理文档 |
| A03 建立任务 | 一个数据库事务写 document/version/PROCESSING 与 job/attempt/deadline | Spring → PostgreSQL，SQL；DB 失败清理本次孤立文件，清理失败留下可追踪记录 |
| A04 返回与派发 | 返回 `202`；调度器读取持久化任务并派发 | Spring → FastAPI，HTTP JSON；此时尚不能检索 |
| A05 文件与解析 | 验证执行票据，向 Spring 请求该任务的原文件；提取文本、页码、段落、表格文字并切片 | FastAPI → Spring HTTP 二进制；Spring → 文件存储；不让 FastAPI 自定下载权限 |
| A06 Embedding | 对切片调用配置的 Embedding 模型，记录模型与维度 | FastAPI → Model API，HTTPS/JSON；不将 LLM 生成接口误当 Embedding 接口 |
| A07 写待发布索引 | 按 documentVersion/attemptId 事务写入切片、定位与向量，得到 manifest | FastAPI → PostgreSQL ai schema；成功提交前不能上报处理成功 |
| A08 结果回调 | 将 job/version/attempt/manifest 上报 | FastAPI → Spring，HTTP JSON；回调失败可重复同一结果，不能重新计算一遍 |
| A09 条件发布 | Spring 核对当前任务、期限、版本、KB 状态及已提交切片；事务置 PUBLISHED 和 publishedAttemptId | PostgreSQL 视图从此允许检索该尝试的数据 |
| A10 展示结果 | 前端轮询 Spring 查询文档状态，显示已发布或失败 | 前端 ↔ Spring REST；状态只以 Spring 为准 |

### 7.1 失败、更新和删除

- **解析或 Embedding 失败：** FastAPI 上报具体错误，Spring 转为处理失败；原文件保留，手动重试创建新 attemptId。
- **300 秒超时：** 从文件接收并校验成功、进入 PROCESSING 起计时，包括排队。Spring 截止控制先终止有效尝试，再发送取消；FastAPI 终止受控解析进程、取消网络调用。即使取消失败，旧尝试也不能发布。
- **回调丢失：** 保留同一任务结果重发；若截止前仍未被 Spring 接纳，则按超时失败。向量已写入但未发布时保持不可检索，后续清理孤立数据。
- **替换：** 接收新文件并通过校验后递增 currentVersion、转为 PROCESSING、清空发布指针，旧内容立即退出检索视图。新文件未接收成功则不改变旧记录。
- **处理中删除：** Spring 先设置内部删除标记/失效执行令牌，阻止检索和发布；取消任务；FastAPI 清理所有版本和尝试的 AI 记录；Spring 删除原文件并完成元数据清理。索引清理失败时不得报告物理清理成功。
- **知识库批量删除：** 先锁定为删除中并阻止新上传；逐文档使用相同清理接口；进度持久化；清理全部完成才移除知识库和授权。重启后保持删除未完成，不恢复已删除内容。

为拒绝迟到回调、隐藏历史引用和继续批量清理，可保留无正文、无文件路径的最小 tombstone/审计标识。它不提供文档恢复，也不能被员工读取为原文件内容。

## 8. 流程 B：AI 问答

对应 [流程 B 时序图](diagrams/flow-b-chat.html)。

| 步骤 | 执行方与数据变化 | 跨边界通信及完成条件 |
| --- | --- | --- |
| B01 提交 | 前端在固定知识库会话中发送问题；页面 1 秒内展示等待 | 前端 → Spring，HTTP POST |
| B02 鉴权与准备 | Spring 检查 Session、会话归属、KB 授权、2,000 字符、同会话单生成；保存问题与 GENERATING run | Spring → PostgreSQL；最多取最近 10 轮正常完成上下文，排除失效证据的事实依赖 |
| B03 委托生成 | 签发绑定 runId/kbId 的作用域及 deadline | Spring → FastAPI，HTTP JSON，接收 SSE |
| B04 受限检索 | 必要时用上下文补全追问；请求查询 Embedding；在可检索视图按 kbId、有效版本、模型标识过滤后排序 | FastAPI → Model API HTTPS；FastAPI → pgvector SQL |
| B05 构造证据 | 形成 chunkId/version/page/evidenceId 清单；资料不足、部分依据或冲突执行 PRD 规则 | FastAPI 内部 Retrieval → Answer Generation；不使用全库上下文 |
| B06 调用模型 | 证据作为不可信资料区输入；模型不得执行文档中夹带的指令，也不能补编企业规定 | FastAPI → LLM API，HTTPS 流式请求；模型 SDK 不在 Spring 和浏览器重复接入 |
| B07 流式中转 | FastAPI 输出带 runId/seq 的事件；Spring 校验当前权限/执行有效性，持久化后再转发已记录的片段 | LLM → FastAPI → Spring → 前端，HTTPS/HTTP SSE；两层流式连接 |
| B08 完成与引用 | FastAPI 输出候选引用与 done；Spring 检查真实来源、当前版本，事务保存完整正文/引用并标记 COMPLETED | Spring → PostgreSQL；保存失败不能给前端发送成功 done |
| B09 展示核对 | 前端显示完成；有有效依据时展示引用编号，打开引用时重新鉴权并取真实片段/页码。无依据回答可以正常完成，但不伪造引用 | 前端 → Spring REST；下载仍由 Spring 文件流返回 |

每个提交在 Spring 保留 userMessageId 与 generationRunId。重新生成产生新 runId，但替换同一用户问题对应的回答；原 run 结果因状态和标识不匹配被拒绝。

### 8.1 中断和权限变化

| 条件 | Spring 的权威动作 | FastAPI 的协作 | 前端结果 |
| --- | --- | --- | --- |
| 用户主动停止 | 将 run 置 STOPPED，之后不再接纳片段；开放重新发送 | cancel 接口及流断开，尽力释放供应商连接 | 已停止，保留已保存片段 |
| 30 秒无正文 | 将 run 置 INCOMPLETE/超时原因，停止转发 | 取消生成，不把心跳当正文 | 超时，可手动重试最后一轮 |
| 总长 120 秒 | 同上；总时长包含检索与模型调用 | 取消上游请求 | 回答未完成 |
| 失去 KB 权限/KB 开始删除 | 立即使活动 run 无效，拒绝后续转发和历史读取 | 收到取消后终止，迟到事件仍被 Spring 丢弃 | 访问已中止，不展示隐藏正文 |
| 文档来源更新或删除 | 本次 run 不再继续使用失效证据，历史引用标注变化 | 停止该次生成 | 未完成/来源变更；新提问重新检索 |
| 浏览器连接中断 | 标记未完成，停止该活动执行；不后台偷偷完成后标成功 | 取消或传播断开信号 | 重开历史可见已保存片段和未完成标记 |
| FastAPI/LLM 错误 | 记录具体系统错误，不标资料不足 | 输出 error 或关闭流 | 未完成/服务异常 |
| Spring 重启 | 旧活动 run 恢复为未完成，不自动执行 | 旧 run 没有有效写入目标 | 手动重新生成 |

停止外部模型调用是尽力而为的资源回收；业务上的“停止”必须由 Spring 的状态栅栏立即保证，不能等供应商确认取消才允许重新发送。

## 9. 一致性、配置与验收追踪

### 9.1 跨服务不能依赖“恰好执行一次”

- 业务命令、处理结果与 SSE 都可能重复或迟到。处理用 jobId/attemptId/documentVersion，问答用 runId/seq；数据库条件更新和唯一约束拒绝过期结果。
- 同一文档同一时间只接纳一个有效处理尝试，同一会话只接纳一个活动生成。取消或超时先令旧执行失效，再允许新尝试。
- 文件系统与 PostgreSQL 不是同一个事务。用临时存储、落盘确认、补偿清理和可追踪孤立文件处理失败，不能宣称数据库回滚能自动回滚磁盘文件。
- `ai.retrievable_chunks` 是一致性过滤视图。内部 staging 切片残留不能突破发布门槛；删除成功还需确认物理文件/索引清理完成。

### 9.2 模型与配置

生成模型与 Embedding 模型分别配置，未必由同一供应商提供。向量维度由选定的 Embedding 模型决定，建表迁移与向量索引必须与之匹配；查询与文档使用相同 modelId/维度。更换 Embedding 模型需要重建相关文档索引，不能混查不同向量空间。

建议后端配置包含：`STORAGE_ROOT`、数据库各角色连接、`AI_SERVICE_BASE_URL`、服务间密钥、生成/Embedding endpoint 与模型名、模型密钥、处理 300 秒、首正文 30 秒、总生成 120 秒、处理并发数。值从环境变量或本地未提交配置注入，不写入前端或示例截图。

后续本地模型由 Model Gateway 增加适配实现；不在第一版增加切换 UI、模型下载或工具执行功能。回答供应商切换不必重建文档向量；Embedding 模型切换通常需要。

### 9.3 PRD 到模块与验证的映射

验收仍使用 PRD 已确定的两个知识库、12 份文档和 20 组固定问答案例；四种文件格式均需有样例。架构联调的失败注入、权限和取消测试作为功能验证补充，不替代该问答质量集合。

| PRD 能力 | 主责模块 | 集成验证重点 |
| --- | --- | --- |
| 注册、登录与角色 | Authentication + User Management | 普通注册不能提升管理员，账号规则一致 |
| 默认无授权、授予与撤权 | Knowledge Base + AI Chat | 新请求、流式输出、历史、引用和下载同时受限 |
| 四种格式、10 MB、重名 | Document Management + Processing | 非法上传拒绝；损坏内容处理失败；页码可核对 |
| 自动发布与 300 秒恢复 | Coordinator + Processing | 写向量前不能发布；超时后旧回调无法覆盖重试 |
| 单库问答、无依据与冲突 | Retrieval + Answer Generation | 固定 20 组质量案例，不全库召回、不补编 |
| 引用和版本失效 | AI Chat + Document Management | 原文真实、页码真实，替换后旧引用不跳新版本 |
| 连续问答、最后一轮重试、停止编辑 | AI Chat + Answer Generation | 上下文隔离；旧 run 不覆盖新 run；只重试最后一轮 |
| 删除恢复、重启持久化 | Knowledge Base + Document Management + Coordinator | 删除进度不丢、任务不复活、会话和已完成资料保留 |
| 本地单请求性能 | 两服务及外部模型 | 测端到端反馈/首正文/完成耗时，失败单列 |

图中的接口与上述表格共用同一语义。实现阶段再把契约细化为 OpenAPI、数据库迁移与任务拆分；本 ARD 不以图代替这些接口和状态规则。

## 10. 实施顺序

1. Spring 账号/Session、知识库、授权及文档元数据，建立数据库权限和文件存储边界。
2. 打通流程 A：单文件上传 → 持久化任务 → FastAPI 解析/Embedding → 条件发布；同时验证失败、超时、替换和删除。
3. 打通流程 B：单库检索 → 回答 SSE → Spring 持久化与鉴权转发 → 引用核对。
4. 加入历史、多轮、停止、编辑、最后一轮重试及撤权的交叉场景，运行 PRD 功能和 20 组问答验收。
5. 完成后再探索本地模型适配与工具调用，不提前启用 Agent。
