# 数据库设计：业务状态、AI 数据与一致性

**版本：** v1.0（Day 4 设计稿）  
**日期：** 2026-11-18  
**依据：** [PRD](PRD.md)、[ARD](ARD.md)。适用个人作品集、本地运行、Spring Boot + FastAPI + PostgreSQL/pgvector。  
**状态：** Day 4 设计稿；Day 12 已通过 Flyway V1/V2 在 PostgreSQL 16.16 中创建 core.users、core.knowledge_bases 及对应约束，重名与创建人外键已有真实数据库验收。Day 13 已接入知识库 JPA Entity 和 PostgreSQL adapter，验证 API 写库、Spring 重启后读取、指定唯一索引冲突转换和事务回滚；过滤、排序、分页仍在 Java 中执行。其余业务表、View、触发器、数据库角色权限和框架 Session 表仍待逐步迁移；下列 SQL 是完整设计的重点契约片段，不能作为全部已实施的证明。已执行结构以 backend/spring-boot/src/main/resources/db/migration 和 [学习日志](daily-log.md) 为准。

- [HTML ER 图](diagrams/er-diagram.html)：完整字段、键及主要关系，可缩放查看。
- [Mermaid ER 源文件](diagrams/er-diagram.mmd)：保留可编辑关系图。
- [Day 4 学习笔记](../Study_note/Day4.txt)：用本项目解释数据库基础。

## 1. 设计选择与范围

采用规范化的业务表、独立执行记录和内部文件版本记录。Spring 是 core 的唯一写入方；FastAPI 写 ai 切片。schema 是同一 PostgreSQL 中的命名空间，不是两套数据库，也不天然产生安全隔离。

对比三种安排：把文件、授权、消息和引用塞入一张宽表会制造重复和更新异常；只保留 documents 当前状态会丢失旧任务身份与物理文件清理范围；本稿分离“业务对象、文件版本、执行尝试、生成结果”，增加少量内部表以保证重试、替换和删除可以正确实现。暂不增加角色权限框架、Agent 工作流表、模型注册中心或向量索引参数表。

本次确认的新规则：**知识库名称去首尾空白后，也忽略英文字母大小写判断重名**。因此 HR 与 hr 不可并存。PRD/ARD 原文件未改动，实施时需同步该规则。

### 1.1 全局约定

| 项目 | 约定 |
| --- | --- |
| 命名 | 表和字段 snake_case；API 的 documentVersion/attemptId 对应 document_version/attempt_id |
| 标识 | UUID 由应用生成；版本、尝试次数、消息顺序用整数；这些编号不是用户权限 |
| 时间 | timestamptz，接口传 ISO 8601；数据库统一比较绝对时间；updated_at 由写入方维护 |
| 隔离 | 短事务采用 PostgreSQL 默认 READ COMMITTED；按统一顺序拿锁后，用后续语句重读权限/状态，不复用锁前快照或长事务快照 |
| 状态 | varchar + CHECK；本稿列出的枚举需在正式迁移中全部约束，不使用可任意填值的自由文本 |
| 必填 | 数据字典“可空=否”表示 NOT NULL；生成列和业务默认值另行注明 |
| Unicode | 问题/标题按 Unicode 字符计数，前端与 PostgreSQL char_length 对齐，避免直接用 UTF-16 length；不按字节或 token 计数 |
| 名称归一 | 账号名、知识库名和问题按 PRD 先 trim；上传文件名不擅自裁剪。username_key/name_key/file_name_key 是 STORED 生成列，lower(... COLLATE "C") 处理英文字母大小写；业务列保留显示大小写 |
| 密钥 | 不存模型密钥、服务间凭证或 Session Cookie 到这些业务字段；密码仅保存哈希 |
| 会话基础设施 | Spring Session JDBC 的框架表由框架对应版本的官方迁移管理，不混入消息表，也不在本业务 ER 中冒充已设计完成 |

## 2. 表关系先于字段

| 关系 | 基数与说明 |
| --- | --- |
| users → knowledge_bases | 1 : 0..N，created_by 表示创建；只有 ADMIN 能创建，非所有用户都能创建 |
| users ↔ knowledge_bases | M : N，经 kb_members 拆成两个 1 : N；创建人和获授权成员是不同概念 |
| knowledge_bases → documents | 1 : 0..N；文档第一版不跨库移动 |
| documents → document_versions | 1 : 1..N；每次替换才增加内部版本 |
| document_versions → processing_jobs | 1 : 1，接收文件版本时同事务创建任务 |
| processing_jobs → processing_attempts | 1 : 1..N，首次执行或人工重试各一行；重复投递不是新尝试 |
| processing_attempts → ai.document_chunks | 1 : 0..N；失败可能没有切片，也可能已有不可检索的待发布切片 |
| users → conversations | 1 : 0..N；会话仅归一人所有 |
| knowledge_bases → conversations | 1 : 0..N；会话固定绑定一个库，撤权不删除历史 |
| conversations → messages | 1 : 0..N；按 ordinal 排序，不能只依赖时间戳 |
| USER message → ASSISTANT message | 1 : 0..1，应用提交成功后成对存在；assistant.reply_to_message_id 指向问题 |
| 一对问题/回答 → generation_runs | 1 : 1..N；重试新增 run，保留同一对可见消息 ID |
| generation_runs → citations | 1 : 0..N；无依据澄清可正常结束且没有引用 |
| citations → chunks | 当前有效时引用一个切片；历史身份为逻辑引用，切片删除不连带删除历史引用记录 |

“一个用户可以创建多个知识库”只描述基数，不能替代管理员角色规则；“一个知识库拥有多个用户”也不是在 kb 表里保存一个逗号分隔的 user_ids 字符串。

## 3. 数据字典

以下共 11 张 core 业务表、1 张 ai 物理表。联合唯一键和部分唯一索引中的各列，不代表每一列单独唯一；图中的 UK 也遵循这一规则。

### 3.1 core.users — 用户

账号和固定角色的权威记录；知识库授权不放在用户行内。

| 字段 | PostgreSQL 类型 | 可空 | 键角色 | 含义 / 默认值 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 否 | PK | 应用生成 UUID；账号稳定标识 |
| `username` | `varchar(32)` | 否 | — | 去除首尾空白后的显示名；仅英文字母、数字、下划线 |
| `username_key` | `text` | 否 | UK | 生成列：lower(username COLLATE "C")；按英文字母大小写归一 |
| `password_hash` | `text` | 否 | — | 自适应密码哈希；不存明文或确认密码 |
| `role` | `varchar(16)` | 否 | — | ADMIN / EMPLOYEE；注册接口只能创建 EMPLOYEE |
| `created_at` | `timestamptz` | 否 | — | 创建时间；默认 now() |
| `updated_at` | `timestamptz` | 否 | — | 由 Spring 在业务更新时维护 |

约束与规则：

- UNIQUE(username_key)；CHECK username ~ '^[A-Za-z0-9_]{3,32}$'；CHECK role IN ('ADMIN','EMPLOYEE')。
- 密码原文的 8～64 字符规则由认证服务在哈希前校验，不能对哈希长度套用。
- 第一版没有账号删除、停用或角色变更；不额外设计通用 RBAC 表。

### 3.2 core.knowledge_bases — 知识库

知识库配置及内部删除进度；creator 是创建人，不是全部读权限的代表。

| 字段 | PostgreSQL 类型 | 可空 | 键角色 | 含义 / 默认值 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 否 | PK | 稳定知识库 ID |
| `name` | `varchar(50)` | 是 | — | 已 trim 的名称；删除完成后置 NULL |
| `name_key` | `text` | 是 | UK | 生成列：lower(name COLLATE "C")；仅非 DELETED 记录参与唯一性 |
| `description` | `varchar(500)` | 是 | — | 可选简介；删除完成后清空 |
| `created_by` | `uuid` | 否 | FK | 创建管理员；角色由 Spring 校验 |
| `lifecycle` | `varchar(20)` | 否 | — | ACTIVE / DELETING / DELETE_FAILED / DELETED |
| `delete_error_code` | `text` | 是 | — | 最近删除失败码；不记录密钥或文件正文 |
| `delete_error_detail` | `text` | 是 | — | 可展示的失败说明 |
| `created_at` | `timestamptz` | 否 | — | 创建时间 |
| `updated_at` | `timestamptz` | 否 | — | 更新时间 |
| `deleted_at` | `timestamptz` | 是 | — | 全部文件/索引及授权清理完成时间 |

约束与规则：

- 部分唯一索引 UNIQUE(name_key) WHERE lifecycle <> 'DELETED'；删除未完成期间仍占用名称。
- 本轮用户确认：名称也忽略英文字母大小写；CHECK 非 DELETED 时 name 非空、已 trim、长度 1～50。
- DELETED 行仅保留 ID、创建关联及生命周期时间等最小标记；name/description/删除错误明细清空。
- 物理保留最小标记使历史会话可区分“知识库已删除”；不表示产品支持恢复。

### 3.3 core.kb_members — 知识库成员授权

用户与知识库的多对多授权；一行就是一条当前有效员工授权。

| 字段 | PostgreSQL 类型 | 可空 | 键角色 | 含义 / 默认值 |
| --- | --- | --- | --- | --- |
| `kb_id` | `uuid` | 否 | PK/FK | 联合主键的一部分 |
| `user_id` | `uuid` | 否 | PK/FK | 联合主键的一部分 |
| `granted_by` | `uuid` | 否 | FK | 执行授权的管理员 |
| `granted_at` | `timestamptz` | 否 | — | 授权时间 |

约束与规则：

- PRIMARY KEY(kb_id,user_id)；不必再加一列毫无业务用途的 id。
- 撤权删除该行，重新授权重新插入；不需要 granted=false 的历史行。
- 仅可给 EMPLOYEE 授权且操作者须为 ADMIN；FK 不能校验这些角色语义，由 Spring 事务校验。
- 管理员的管理访问由固定角色处理，不需要给管理员复制全部成员记录。

### 3.4 core.documents — 文档当前业务状态

同一份逻辑文档的稳定身份、当前文件版本和唯一发布指针。

| 字段 | PostgreSQL 类型 | 可空 | 键角色 | 含义 / 默认值 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 否 | PK/FK | 替换和重试均不改变 document ID；也是当前版本联合 FK 的一部分 |
| `kb_id` | `uuid` | 否 | FK | 文档所属知识库；第一版不可移动 |
| `file_name` | `text` | 是 | — | 当前显示文件名（含扩展名）；删除完成后清空 |
| `file_name_key` | `text` | 是 | UK | 生成列：lower(file_name COLLATE "C")；非 DELETED 时参与同库唯一性 |
| `current_version` | `integer` | 否 | FK | 从 1 开始；只在替换接收成功时递增；联合 FK 见下文 |
| `status` | `varchar(16)` | 否 | — | PROCESSING / FAILED / PUBLISHED；三种用户处理状态 |
| `lifecycle` | `varchar(20)` | 否 | — | ACTIVE / DELETING / DELETE_FAILED / DELETED；内部状态 |
| `published_attempt_id` | `uuid` | 是 | FK | 仅 PUBLISHED 时非空；联合 FK 绑定本 document/current_version |
| `created_by` | `uuid` | 否 | FK | 首次上传管理员 |
| `created_at` | `timestamptz` | 否 | — | 首次上传时间 |
| `updated_at` | `timestamptz` | 否 | — | 状态或版本变更时间 |
| `delete_error_code` | `text` | 是 | — | 删除进度失败码 |
| `delete_error_detail` | `text` | 是 | — | 删除失败说明；成功后清空 |
| `deleted_at` | `timestamptz` | 是 | — | 全部清理完成时间 |

约束与规则：

- UNIQUE(kb_id,file_name_key) WHERE lifecycle <> 'DELETED'；删除中仍占名，完成后才释放。
- CHECK 非 DELETED 时 file_name 必填且非空；DELETED 时 file_name 和清理错误明细为空。
- CHECK current_version >= 1；CHECK ((status='PUBLISHED') = (published_attempt_id IS NOT NULL))。
- FK(id,current_version) → document_versions(document_id,document_version)，DEFERRABLE INITIALLY DEFERRED；只写 current_version FK 并不够。
- FK(published_attempt_id,id,current_version) → processing_attempts(id,document_id,document_version)，DEFERRABLE INITIALLY DEFERRED；不能指向别的文档或旧版本。
- 开始删除时清空发布指针、将隐藏的处理状态置 FAILED；lifecycle 先阻断访问。DELETED 时清空文件名和错误明细。

### 3.5 core.document_versions — 文件版本与物理清理

不可变原文件的内部版本；保存存储键及清理进度，支持替换与删除补偿，不提供历史版本功能。

| 字段 | PostgreSQL 类型 | 可空 | 键角色 | 含义 / 默认值 |
| --- | --- | --- | --- | --- |
| `document_id` | `uuid` | 否 | PK/FK | 联合主键 |
| `document_version` | `integer` | 否 | PK | 联合主键；相同文档递增 |
| `original_file_name` | `text` | 是 | — | 接收时文件名；物理清理完成后清空 |
| `storage_key` | `text` | 是 | UK | 服务端生成的相对存储键；不是用户给定路径；清理完成后 NULL |
| `media_type` | `text` | 是 | — | 校验后的实际格式；清理完成后清空 |
| `size_bytes` | `bigint` | 是 | — | 有效原文件为 1～10,000,000 字节；清理完成后清空 |
| `file_sha256` | `char(64)` | 是 | — | 文件完整性校验；不用于内容去重；清理后清空 |
| `file_state` | `varchar(20)` | 否 | — | PRESENT / DELETE_PENDING / DELETE_FAILED / DELETED |
| `cleanup_error` | `text` | 是 | — | 最近物理清理失败原因；成功后清空 |
| `uploaded_by` | `uuid` | 否 | FK | 上传此版本的管理员 |
| `created_at` | `timestamptz` | 否 | — | 文件接收并通过校验的时间 |
| `file_deleted_at` | `timestamptz` | 是 | — | 确认文件已不存在的时间 |

约束与规则：

- PRIMARY KEY(document_id,document_version)；CHECK document_version>=1；UNIQUE(storage_key)，多个 NULL 合法。
- CHECK size_bytes IS NULL OR size_bytes BETWEEN 1 AND 10000000。非 DELETED 时 storage_key/原文件名/media_type/size/sha256 必须存在；DELETED 时它们全部为空。
- 同一版本的文件字节与存储键在清理前不变。重试复用此版本；替换创建新版本。
- 旧版本退出服务后可以清理文件；版本行保留最小关联标记，外键仍有效，不能下载已清理内容。

### 3.6 core.processing_jobs — 文档处理逻辑任务

一份文件版本对应一个逻辑处理任务；手动重试属于该任务的新执行尝试。

| 字段 | PostgreSQL 类型 | 可空 | 键角色 | 含义 / 默认值 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 否 | PK | 跨服务 jobId |
| `document_id` | `uuid` | 否 | FK/UK | 联合 FK 指向版本 |
| `document_version` | `integer` | 否 | FK/UK | 与 document_id 组成版本 FK 及唯一键 |
| `created_by` | `uuid` | 否 | FK | 创建该版本任务的管理员 |
| `created_at` | `timestamptz` | 否 | — | 逻辑任务创建时间 |

约束与规则：

- UNIQUE(document_id,document_version)；FK(document_id,document_version) → document_versions 的联合主键。
- 为下游联合 FK 声明 UNIQUE(id,document_id,document_version)。
- 不再复制一份 job_status；当前进度由当前版本的最近一次 attempt 得出，避免两套状态分叉。
- 替换版本生成新 jobId；重试同版本保留 jobId、生成新 attemptId；HTTP 重投保留二者。

### 3.7 core.processing_attempts — 文档处理执行记录

每次真正处理的权威执行记录；包含派发、截止时间、结果与错误，Spring 唯一写入。

| 字段 | PostgreSQL 类型 | 可空 | 键角色 | 含义 / 默认值 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 否 | PK | 跨服务 attemptId；不是 HTTP 请求次数 |
| `job_id` | `uuid` | 否 | FK/UK | 联合 FK 的任务 ID |
| `document_id` | `uuid` | 否 | FK | 联合 FK 约束任务所属文档 |
| `document_version` | `integer` | 否 | FK | 联合 FK 约束任务所属版本 |
| `attempt_no` | `integer` | 否 | UK | 同一 job 从 1 递增；由锁定 job 后分配 |
| `status` | `varchar(20)` | 否 | — | QUEUED / DISPATCHED / RUNNING / SUCCEEDED / FAILED / TIMED_OUT / CANCELLED / INTERRUPTED |
| `embedding_model_id` | `text` | 否 | — | 包含供应商、模型与配置版本的稳定标识，不只用可变昵称 |
| `embedding_dimensions` | `integer` | 否 | — | 本次预期维度；选定模型后从配置确认；无预设数值 |
| `dispatch_count` | `integer` | 否 | — | 同一尝试的命令投递次数；默认 0 |
| `requested_by` | `uuid` | 否 | FK | 触发上传或本次手动重试的管理员 |
| `created_at` | `timestamptz` | 否 | — | 进入 PROCESSING 的时间；排队从此计时 |
| `deadline_at` | `timestamptz` | 否 | — | 默认由配置计算 created_at+300秒；存实际截止点 |
| `last_dispatched_at` | `timestamptz` | 是 | — | 最近命令投递时间 |
| `started_at` | `timestamptz` | 是 | — | 执行器确认开始的时间；不可把排队时间排除 |
| `finished_at` | `timestamptz` | 是 | — | Spring 接纳终态的时间 |
| `chunk_count` | `integer` | 是 | — | 接纳成功 manifest 的切片数，成功必须 >0 |
| `manifest_sha256` | `char(64)` | 是 | — | 已提交切片清单的规范化摘要；成功必填 |
| `error_code` | `text` | 是 | — | PARSER_FAILED / PROCESSING_TIMEOUT / INTERRUPTED 等 |
| `error_detail` | `text` | 是 | — | 不含密钥、正文和任意绝对路径的说明 |

约束与规则：

- FK(job_id,document_id,document_version) → processing_jobs(id,document_id,document_version)；UNIQUE(job_id,attempt_no)。
- UNIQUE(id,document_id,document_version) 供文档发布指针、切片和引用使用。
- 部分唯一索引 UNIQUE(document_id) WHERE status IN ('QUEUED','DISPATCHED','RUNNING')；同一文档最多一个有效执行。
- CHECK attempt_no>=1、dispatch_count>=0、embedding_dimensions>0、deadline_at>created_at；终态 iff finished_at 非空；SUCCEEDED 必须有 chunk_count>0 和 manifest。
- FastAPI 的成功是候选结果；Spring 在核验和发布的同一事务里将本次 attempt 置 SUCCEEDED。过期尝试不能改回成功。
- 同一 job/attempt 的 HTTP 重投或相同结果回调幂等；不新增 attempt，也不重置 deadline。

### 3.8 core.conversations — 对话会话

个人会话归属、固定单知识库范围和列表排序。

| 字段 | PostgreSQL 类型 | 可空 | 键角色 | 含义 / 默认值 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 否 | PK | 会话 ID |
| `owner_id` | `uuid` | 否 | FK | 会话所属用户 |
| `kb_id` | `uuid` | 否 | FK | 固定绑定知识库；切库创建新会话 |
| `title` | `text` | 是 | — | 首问前20个字符及省略号的派生缓存；新空会话可 NULL |
| `next_message_ordinal` | `bigint` | 否 | — | 下次可分配的消息序号；默认1；行锁内递增 |
| `created_at` | `timestamptz` | 否 | — | 创建时间 |
| `last_message_at` | `timestamptz` | 是 | — | 最后一条消息创建或替换的时间；空会话 NULL |
| `updated_at` | `timestamptz` | 否 | — | 会话变更时间；不是每个 token 的排序依据 |

约束与规则：

- CHECK next_message_ordinal>=1；title 不作授权凭据。撤权/知识库删除时由响应层屏蔽原标题和正文。
- 知识库只留最小 DELETED 行，因此 kb_id 保持有效 FK；不因撤权删除会话。
- 用户删除会话时物理删除会话、消息、执行和引用；先使活动 run 失效，再级联删除，不保留对话分支。

### 3.9 core.messages — 当前可见消息

保存用户问题与当前 AI 正文；同一轮重试或编辑替换内容，不产生额外可见回答版本。

| 字段 | PostgreSQL 类型 | 可空 | 键角色 | 含义 / 默认值 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 否 | PK | 消息 ID；重试复用同一 assistant message |
| `conversation_id` | `uuid` | 否 | FK/UK | 所属会话 |
| `ordinal` | `bigint` | 否 | UK | 在会话内稳定排序；并发下由会话行锁分配 |
| `role` | `varchar(16)` | 否 | — | USER / ASSISTANT |
| `reply_to_message_id` | `uuid` | 是 | FK/UK | ASSISTANT 指向同会话的 USER；USER 为 NULL |
| `content` | `text` | 否 | — | USER 是已 trim 问题；ASSISTANT 初始空串、流式累计保存 |
| `current_run_id` | `uuid` | 是 | FK | ASSISTANT 当前执行指针；USER 为 NULL；循环 FK 延迟校验 |
| `created_at` | `timestamptz` | 否 | — | 首次创建时间 |
| `updated_at` | `timestamptz` | 否 | — | 内容替换或片段保存时间 |

约束与规则：

- UNIQUE(conversation_id,ordinal)；UNIQUE(id,conversation_id)；UNIQUE(id,conversation_id,reply_to_message_id) 供联合 FK 使用。
- 部分唯一索引 UNIQUE(reply_to_message_id) WHERE role='ASSISTANT'；一问最多一条当前可见 AI 消息。
- FK(reply_to_message_id,conversation_id) → messages(id,conversation_id)，保证不回复其他会话的消息。
- FK(current_run_id,id,conversation_id) → generation_runs(id,assistant_message_id,conversation_id)，DEFERRABLE INITIALLY DEFERRED。
- CHECK role 合法、ordinal>0、不能回复自身；USER 的 reply_to/current_run 为空且 trim 后字符数为1～2000；ASSISTANT 的 reply_to/current_run 非空。角色配对仍由 Spring 校验。
- 消息不再冗余保存生成 status；回答展示状态取 current_run_id 对应的 run。

### 3.10 core.generation_runs — AI 生成执行记录

每一次模型生成的执行栅栏、幂等键、超时与结果状态；不保存历史回答版本。

| 字段 | PostgreSQL 类型 | 可空 | 键角色 | 含义 / 默认值 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 否 | PK | runId；每次重试或编辑重发都新建 |
| `conversation_id` | `uuid` | 否 | FK | 所属会话；同会话只允许一个 GENERATING |
| `user_message_id` | `uuid` | 否 | FK | 本轮问题；联合 FK 限定会话 |
| `assistant_message_id` | `uuid` | 否 | FK | 本轮当前可见回答；联合 FK 还限定 reply_to |
| `trigger_kind` | `varchar(16)` | 否 | — | INITIAL / RETRY / EDIT |
| `status` | `varchar(16)` | 否 | — | GENERATING / COMPLETED / INCOMPLETE / STOPPED |
| `client_request_id` | `uuid` | 否 | UK | 前端命令去重键；同会话唯一 |
| `request_sha256` | `char(64)` | 否 | — | 绑定命令类型、目标和当次问题；相同去重键不同载荷返回冲突 |
| `llm_model_id` | `text` | 否 | — | 实际选用生成模型的供应商/模型配置标识 |
| `evidence_manifest` | `jsonb` | 否 | — | 本次检索证据 ID/版本/attempt/chunk 的受校验清单；默认 []，不含原文或密钥 |
| `last_seq` | `bigint` | 否 | — | 已成功持久化的最大流事件序号；默认0 |
| `requested_at` | `timestamptz` | 否 | — | 服务端接收本次命令的起点；包含检索和网络 |
| `first_text_deadline_at` | `timestamptz` | 否 | — | requested_at + 配置30秒；不能由心跳刷新 |
| `deadline_at` | `timestamptz` | 否 | — | requested_at + 配置120秒 |
| `first_text_at` | `timestamptz` | 是 | — | 首批正文落库时间；不把等待提示算正文 |
| `finished_at` | `timestamptz` | 是 | — | 接纳最终状态的时间 |
| `error_code` | `text` | 是 | — | 超时、撤权、来源变化、中断等原因；COMPLETED 时 NULL |
| `error_detail` | `text` | 是 | — | 系统异常说明；不伪装成资料不足 |

约束与规则：

- UNIQUE(conversation_id,client_request_id)；UNIQUE(id,assistant_message_id)；UNIQUE(id,assistant_message_id,conversation_id)。
- 部分唯一索引 UNIQUE(conversation_id) WHERE status='GENERATING'。
- FK(user_message_id,conversation_id) → messages(id,conversation_id)；FK(assistant_message_id,conversation_id,user_message_id) → messages(id,conversation_id,reply_to_message_id)。
- CHECK last_seq>=0、first_text_deadline_at>requested_at、deadline_at>=first_text_deadline_at、jsonb_typeof(evidence_manifest)='array'；终态 iff finished_at 非空。
- 新 run 前锁会话并校验业务入口；流写入必须同时匹配 current_run_id、GENERATING、当前权限及有效证据。
- evidence_manifest 的 JSONB 只承载本次变长执行清单；Spring 校验字段和所有 ID、禁止任意 payload，不能代替最终 citations 的关系约束。

### 3.11 core.citations — AI 回答引用

将当前 AI 回答中的引用编号绑定到本次生成的真实切片身份；不复制原文。

| 字段 | PostgreSQL 类型 | 可空 | 键角色 | 含义 / 默认值 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 否 | PK | 引用 API 的稳定 ID |
| `generation_run_id` | `uuid` | 否 | FK/UK | 与 assistant_message_id 组成联合 FK |
| `assistant_message_id` | `uuid` | 否 | FK | 通过联合 FK 确保引用属于该 run 的回答 |
| `citation_no` | `integer` | 否 | UK | 回答中的〔1〕、〔2〕，同一 run 唯一 |
| `evidence_id` | `text` | 否 | UK | 本次证据标记，如 e1；由检索清单提供 |
| `document_id` | `uuid` | 否 | FK | 与 version/attempt 组成联合来源 FK |
| `document_version` | `integer` | 否 | FK | 引用时的版本；不会自动跟随 current_version |
| `attempt_id` | `uuid` | 否 | FK | 引用时的处理尝试；不能用别的尝试冒充 |
| `source_chunk_id` | `uuid` | 否 | — | 历史来源定位；刻意不设到 ai 切片的物理 FK，可在来源删除后保留引用身份 |
| `created_at` | `timestamptz` | 否 | — | 引用与完整回答原子提交的时间 |

约束与规则：

- FK(generation_run_id,assistant_message_id) → generation_runs(id,assistant_message_id) ON DELETE CASCADE。
- FK(attempt_id,document_id,document_version) → processing_attempts(id,document_id,document_version)；CHECK citation_no>0。
- UNIQUE(generation_run_id,citation_no)；UNIQUE(generation_run_id,evidence_id)。
- source_chunk_id 是明确标注的逻辑引用；完成提交前 Spring 查询核验其确属该 doc/version/attempt 和本次 evidence_manifest。
- 不保存 quote_snapshot/下载 URL；失效时不能从引用表直接返回旧原文。重试/编辑时删除旧 run 的可见引用。

### 3.12 ai.document_chunks — 解析切片与向量

FastAPI 管理的文本证据和 Embedding；写入成功不等于业务已发布。

| 字段 | PostgreSQL 类型 | 可空 | 键角色 | 含义 / 默认值 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 否 | PK | 切片 ID；由执行器生成，作为 evidence 的真实身份 |
| `document_id` | `uuid` | 否 | FK | 与 version/attempt 组成联合 FK |
| `document_version` | `integer` | 否 | FK | 原文件版本 |
| `attempt_id` | `uuid` | 否 | FK/UK | 此次处理执行 ID |
| `chunk_index` | `integer` | 否 | UK | 尝试内从0开始的有序切片编号 |
| `content` | `text` | 否 | — | 解析器产生的真实文字；不得由模型重写 |
| `token_count` | `integer` | 是 | — | 可选 tokenizer 计数，非字符数 |
| `page_start` | `integer` | 是 | — | PDF 实际页序，从1开始；其他格式 NULL |
| `page_end` | `integer` | 是 | — | PDF 结束页；第一版 PDF 切片不跨页，等于 page_start |
| `locator` | `jsonb` | 否 | — | 段落/标题/行范围等格式相关位置；默认 {}；不放正文副本 |
| `embedding_model_id` | `text` | 否 | — | 必须匹配处理尝试的预期模型配置标识 |
| `embedding_dimensions` | `integer` | 否 | — | 实际向量维度；大于0，无默认常量 |
| `embedding` | `vector` | 否 | — | 不固定长度的 pgvector 类型；用行级 CHECK 验证实际维度 |
| `created_at` | `timestamptz` | 否 | — | 本批切片提交时间 |

约束与规则：

- FK(attempt_id,document_id,document_version) → core.processing_attempts(id,document_id,document_version) ON DELETE RESTRICT。
- UNIQUE(attempt_id,chunk_index)；CHECK chunk_index>=0、trim(content)非空、token_count IS NULL OR token_count>0。
- CHECK embedding_dimensions>0 AND vector_dims(embedding)=embedding_dimensions；CHECK jsonb_typeof(locator)='object'。
- 页码须同时为空，或 1<=page_start=page_end；PDF 按页切分后再按长度切片，确保引用页码准确。格式对应关系由解析器和 Spring manifest 核验。
- 同 attempt 重复落库以幂等事务处理；同序号不同内容/模型/向量不是合法重复，必须报冲突，不能静默覆盖已发布切片。
- 切片 INSERT/UPDATE 必须经数据库 BEFORE ROW 触发器校验当前 KB/document/attempt 有效性并取共享锁；已发布或已失效执行不能改写切片。DELETE 保留给显式清理。
- 向量与查询必须同模型、同维度、同距离约定；数值维度未最终选定，第一版先精确扫描，不创建 HNSW/IVFFlat。

## 4. 三种“当前”不要混淆

### 4.1 document_version 与 attempt_id

document_version 回答“处理的是哪份文件”，attempt_id 回答“对这份文件的哪次执行”。jobId 则是该版本的逻辑任务身份。同一次网络重投的三个值都不变。

| 时点 | document/version | job/attempt | 文档状态 | 可检索内容 |
| --- | --- | --- | --- | --- |
| 首次接收文件 | D/v1 | J1/A1 | PROCESSING，发布指针为空 | 无 |
| A1 成功并被接纳 | D/v1 | J1/A1 | PUBLISHED，指向 A1 | 仅 v1+A1 |
| 新文件校验未通过 | 保持 D/v1 | 不创建 J2/A2 | 保持原状态 | v1+A1 不受影响 |
| 新文件接收成功 | D/v2 | J2/A2 | PROCESSING，清空发布指针 | v1 立即不可用 |
| A2 失败或超时 | D/v2 | J2/A2 | FAILED，无发布指针 | 无；不回退 v1 |
| 管理员手动重试 | D/v2 | J2/A3 | PROCESSING；重算300秒 | 无 |
| A2 迟到报告成功 | D/v2 | J2/A2 | 仍由有效 A3 控制 | A2 不得发布 |
| A3 被接纳成功 | D/v2 | J2/A3 | PUBLISHED，指向 A3 | 仅 v2+A3 |

只存 document_id 会混入 v1；只存 version 又会混入 v2 的失败 A2。三元组 `(document_id,document_version,attempt_id)` 同时出现在切片与引用中，才可以准确选择来源。

这是对 ARD 概念任务的细化：原先描述在 processing_jobs 上的 dispatch/deadline/terminal 信息，本稿归入 processing_attempts；processing_jobs 只保留版本级逻辑身份。API 继续传 jobId + attemptId，job 路径不能用来忽略 attempt 或覆盖其状态；不需要第二套任务 API。

### 4.2 消息与 run

M1 是用户问题，M2 是当前展示的回答。初次执行 R1 失败后，人工重试创建 R2，M2.current_run_id 改成 R2，正文清空并重新生成；M1 不重复创建。旧 R1 只留状态、时间和错误，不提供历史回答版本。编辑最近问题时更新 M1，创建 R3，再替换 M2。

更新同一条消息并不意味着没有执行历史；反过来，保留 run 也不意味着给用户增加对话分支。流写入同时匹配 `M2.current_run_id=R2` 和 `R2.status=GENERATING`，旧 R1 的片段和 done 才无法追加进来。

### 4.3 引用的“当前有效”

引用永久绑定生成时的 doc/version/attempt/chunk 身份，不能把旧引用自动改到新版本。打开引用先经会话归属与当前 KB 权限校验，再确认来源仍等于当前已发布版本/尝试，最后读取真实切片。文档删除、替换或知识库暂停时，返回对应不可用说明，不返回旧片段或文件。

历史回答正文按 PRD 保留并显示过期风险；知识库权限已失去时，整个历史正文也必须隐藏。引用表不存原文快照可以减少误返回旧片段的机会，但不能让权限检查变成可选项。

## 5. View：ai.retrievable_chunks

这是普通 View，不是物化视图，也不复制向量。每次查询按当前已提交状态计算可见记录。下面是关键定义片段，列名与字典一致：

```sql
CREATE VIEW ai.retrievable_chunks
WITH (security_barrier = true) AS
SELECT c.id AS chunk_id,
       d.kb_id, c.document_id, c.document_version, c.attempt_id,
       d.file_name, v.media_type,
       c.chunk_index, c.content, c.page_start, c.page_end, c.locator,
       c.embedding_model_id, c.embedding_dimensions, c.embedding
FROM ai.document_chunks AS c
JOIN core.documents AS d
  ON d.id = c.document_id
JOIN core.knowledge_bases AS kb
  ON kb.id = d.kb_id
JOIN core.document_versions AS v
  ON v.document_id = c.document_id
 AND v.document_version = c.document_version
JOIN core.processing_attempts AS a
  ON a.id = c.attempt_id
 AND a.document_id = c.document_id
 AND a.document_version = c.document_version
WHERE kb.lifecycle = 'ACTIVE'
  AND d.lifecycle = 'ACTIVE'
  AND d.status = 'PUBLISHED'
  AND d.current_version = c.document_version
  AND d.published_attempt_id = c.attempt_id
  AND v.file_state = 'PRESENT'
  AND a.status = 'SUCCEEDED'
  AND c.embedding_model_id = a.embedding_model_id
  AND c.embedding_dimensions = a.embedding_dimensions;
```

View 输出 15 列：chunk_id、kb_id、document_id、document_version、attempt_id、file_name、media_type、chunk_index、content、page_start、page_end、locator、embedding_model_id、embedding_dimensions、embedding。ER 中的 View 单独标明为派生对象，不能 INSERT 或当作新实体增加外键。

**View 不知道当前浏览器用户是谁。** 它过滤发布状态和版本，不自动授予权限，security_barrier 也不是用户级 RLS。必须保留三层约束：

1. Spring 根据 Session、会话归属和当前 kb_members 校验单库访问；管理员访问遵循固定角色规则。
2. Spring 签发绑定 userId/kbId/runId/有效期的作用域；FastAPI 验证签名后，将 verified_kb_id 强制放入 SQL，不能相信原始前端参数。
3. Spring 在转发正文、持久化引用、读取历史和下载时重新核验当前权限与来源。执行期间撤权使活动 run 失效。

查询片段（`:...` 为驱动参数绑定的说明形式，不是拼接 SQL）：

```sql
WITH scoped AS MATERIALIZED (
    SELECT *
    FROM ai.retrievable_chunks
    WHERE kb_id = :verified_kb_id
      AND embedding_model_id = :configured_model_id
      AND embedding_dimensions = :verified_query_dimensions
)
SELECT chunk_id, document_id, document_version, attempt_id,
       file_name, content, page_start, page_end, locator,
       embedding <=> CAST(:query_embedding AS vector) AS distance
FROM scoped
ORDER BY distance, chunk_id
LIMIT :top_k;
```

这个例子采用**余弦距离 `<=>`**作为首版检索约定，须与最终 Embedding 模型的使用说明匹配；不适配的模型需统一改距离契约。FastAPI 先确认查询向量的实际维度与配置一致；不能只把错误维度填进 WHERE。MATERIALIZED 明确先限定知识库/模型/维度，再做精确距离排序；不承诺 ANN 性能。

数据库权限由迁移账户建立：Spring 角色可写 core、只读核验需要的 ai 列；FastAPI 角色可写 ai.document_chunks、读该 View，无 core 业务表写权限或密码/Session 读取权限。View 由专用 NOLOGIN owner 持有，它具有底层必要列的 SELECT，FastAPI 只获 View SELECT；不要改成需要调用者直接访问所有 core 表的 security_invoker 方案。schema 的 REFERENCES 权限和 FK 在迁移时统一建立。

FastAPI 的处理角色具备访问切片基表的技术能力，不能宣称 View 已隔离服务被攻陷时的全部风险；第一版依靠受信内部服务、强制请求范围和 Spring 的最终校验，不引入连接级动态 current_user 伪装成业务用户。

## 6. 必须落实的约束和索引

### 6.1 关键 DDL 契约片段

这些片段展示联合约束，须在所有表建立后按迁移依赖顺序执行；不是可单独执行的完整建库脚本。

```sql
-- 父侧联合候选键，供“属于同一文档/版本”的 FK 引用。
ALTER TABLE core.processing_jobs
  ADD CONSTRAINT uq_job_source UNIQUE (id, document_id, document_version);
ALTER TABLE core.processing_attempts
  ADD CONSTRAINT uq_attempt_source UNIQUE (id, document_id, document_version);

ALTER TABLE core.processing_attempts
  ADD CONSTRAINT fk_attempt_job_source
  FOREIGN KEY (job_id, document_id, document_version)
  REFERENCES core.processing_jobs (id, document_id, document_version);

ALTER TABLE core.documents
  ADD CONSTRAINT fk_document_current_version
  FOREIGN KEY (id, current_version)
  REFERENCES core.document_versions (document_id, document_version)
  DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE core.documents
  ADD CONSTRAINT fk_document_published_attempt
  FOREIGN KEY (published_attempt_id, id, current_version)
  REFERENCES core.processing_attempts (id, document_id, document_version)
  DEFERRABLE INITIALLY DEFERRED;

CREATE UNIQUE INDEX uq_live_kb_name ON core.knowledge_bases (name_key)
  WHERE lifecycle <> 'DELETED';
CREATE UNIQUE INDEX uq_live_document_name ON core.documents (kb_id, file_name_key)
  WHERE lifecycle <> 'DELETED';
CREATE UNIQUE INDEX uq_active_processing_attempt ON core.processing_attempts (document_id)
  WHERE status IN ('QUEUED', 'DISPATCHED', 'RUNNING');
CREATE UNIQUE INDEX uq_active_generation ON core.generation_runs (conversation_id)
  WHERE status = 'GENERATING';
CREATE UNIQUE INDEX uq_one_visible_answer ON core.messages (reply_to_message_id)
  WHERE role = 'ASSISTANT';

ALTER TABLE ai.document_chunks
  ADD CONSTRAINT fk_chunk_attempt_source
  FOREIGN KEY (attempt_id, document_id, document_version)
  REFERENCES core.processing_attempts (id, document_id, document_version)
  ON DELETE RESTRICT;
ALTER TABLE ai.document_chunks
  ADD CONSTRAINT ck_embedding_dimensions CHECK (
    embedding_dimensions > 0
    AND vector_dims(embedding) = embedding_dimensions
  );
```

CHECK 只约束单行，不用于跨表读角色、当前授权或任务状态。FK 保证来源关联存在，不保证这个任务真的成功，更不保证其结果有业务权限。跨行状态由事务、锁和条件更新保证。

### 6.2 非唯一查询索引

| 索引 | 解决的查询 |
| --- | --- |
| kb_members(user_id,kb_id) | 按员工列出授权知识库；PK 的 kb_id 前缀同时支持按库查成员 |
| knowledge_bases(created_by) | 创建人关联及 FK 检查；用户表不自动为引用方建索引 |
| documents(kb_id,lifecycle,created_at DESC,id) | 知识库文档列表和删除剩余范围；非唯一列表索引 |
| document_versions(file_state) WHERE file_state IN ('DELETE_PENDING','DELETE_FAILED') | 定位尚未清理的文件；重启只呈现进度，不自动重试业务删除 |
| processing_attempts(status,deadline_at) WHERE status IN ('QUEUED','DISPATCHED','RUNNING') | Spring 的待派发与超时扫描 |
| conversations(owner_id,last_message_at DESC NULLS LAST,created_at DESC,id) | 自己的历史列表；业务排序以最后消息为准，ID 消除并列不稳定 |
| conversations(kb_id) | 知识库停用/删除时定位会话 |
| generation_runs(assistant_message_id,requested_at DESC) | 查询消息的执行记录；不能因查到旧 run 就返回旧正文 |
| citations(document_id,document_version,attempt_id) | 找出来源变化影响的历史回答；正常读取仍动态核验 |
| ai.document_chunks(document_id,document_version,attempt_id) | 文档全部索引清理与来源核对 |

PK/UNIQUE 已自动产生 B-tree 索引，不要重复为相同字段顺序建普通索引。例如 messages(conversation_id,ordinal)、chunks(attempt_id,chunk_index)、jobs(document_id,document_version) 已由唯一键覆盖。额外的联合候选键是为了 FK 精确性，不能随意删掉；其空间代价在小规模 MVP 可接受。其余引用方 FK 索引根据实际删除/查询计划补充，不宣称每个 FK 自动带索引。

### 6.3 向量索引暂缓

先选定 Embedding 模型、实际维度和距离契约，再考虑 vector(d)、HNSW/IVFFlat。当前 `vector` 不写 `(1536)` 或其他默认猜测；正整数维度作为配置存入每次 attempt 和每个 chunk，查询严格匹配。仅在字段增加维度 CHECK 不代表可以混用模型。

HNSW 通常需要更多内存和构建成本；IVFFlat 的聚类与数据量/训练分布有关。两者的距离算子类必须与 ORDER BY 一致；支持的维度上限及类型依 pgvector 版本核实。不在本阶段固定 m、ef_construction、ef_search、lists 或 probes。后续要在模型/维度受限的物理列、分区或表达式索引上另做迁移和召回评估，不能直接给混维度 vector 建一个通用索引。

## 7. 事务与并发：表正确还不够

### 7.1 上传、替换与重试

先完成文件上传校验和不可变存储，再开启短业务事务：按顺序锁 KB → document → job/attempt。首次上传插入 document、version、job、attempt；替换先令旧活动尝试失效，再增加版本；重试锁同一 job，增加 attempt_no 并创建新 attempt。清空 published_attempt_id，设置 PROCESSING，deadline 从本次进入状态起按实际配置计算。事务成功后才派发命令，不能在锁中等 HTTP、解析器或模型。

document 与 version、message 与 run 之间存在循环引用，因此先建立表，再 ALTER 添加延迟 FK；应用为同一业务事务预先生成 UUID，事务提交时所有目标必须存在。DEFERRABLE 只延后检查，不允许提交永久悬空引用。

文件系统写入不被数据库回滚。若文件保存成功但事务失败，清理本次文件；清理失败需由存储补偿清单记录 storage_key（存储层可用持久化本地 journal），不能留下一个无人知道的路径。本清单只用于未入库孤立文件，不是另一套文档数据库。

### 7.2 FastAPI 写入与 Spring 条件发布

FastAPI 在一个 ai 事务里提交本 attempt 的完整切片及向量；失败回滚该批。提交成功后的回调携带 jobId/attemptId/version、chunk_count、模型标识/维度和 manifest_sha256。

**写入本身也必须受状态约束。** 迁移建立 `ai.guard_chunk_write()` 及 INSERT/UPDATE 的 BEFORE ROW 触发器：从 NEW.attempt_id 找到来源，按 KB → document → attempt 的全局顺序取 `FOR SHARE` 锁，重新检查 KB/document ACTIVE、文档 PROCESSING、版本匹配、attempt 为 QUEUED/DISPATCHED/RUNNING 且 `clock_timestamp()<deadline_at`，并验证模型/维度等于 attempt。失败即抛错回滚整批；成功只允许写 ai，不修改 core 状态。触发器函数使用专用 NOLOGIN owner、SECURITY DEFINER、固定 `search_path=pg_catalog` 和全部限定名。该 owner 获必要列的 SELECT 及 PostgreSQL 行锁所需的最小 UPDATE 列权限（如各来源表的 id），函数体不执行 core UPDATE；FastAPI 运行角色没有这些权限、不是表 owner，也不能关闭触发器或取得该 owner。普通 FK 和 CHECK 不能替代这项跨表校验。

UPDATE 还需拒绝修改已存在行的 id/document_id/document_version/attempt_id/chunk_index，防止把同一切片身份挪给另一个来源。不同内容的重复切片提交视为冲突；新尝试使用新行和新 ID。

这项校验只检查 Spring 已决定的文档/任务状态，不决定用户读权限。删除或替换先在 Spring 事务中使来源失效并提交，再调用 ai 清理；若旧写入已持共享锁，它先完成、随后被清理；若旧解析器晚到，触发器拒绝 INSERT。因而不能在已宣布删除成功后重新写回孤立切片。DELETE 不受该写入触发器阻挡，可清理终态数据；已成功尝试的重复命令直接返回已有结果，不能重写已发布切片。

manifest 按 chunk_index 升序组成数组，每项固定包含 chunk_id、document_id、document_version、attempt_id、chunk_index、content_sha256、page_start、page_end、locator、embedding_model_id、embedding_dimensions、vector_sha256。UUID 为小写标准字符串，content_sha256 对原始 content 的 UTF-8 字节计算，不改变换行。vector_sha256 对数据库实际保存的 float32 数组计算：按维度顺序、小端 IEEE 754、将负零统一为正零，不接受非有限值。整数组按 RFC 8785 JCS 规范化为 UTF-8 后计算 SHA-256，摘要输出小写十六进制。Spring 从 ai 只读核验完整行集和摘要，不信任回调自行声明的数量；实施时用跨语言固定样例验证两端实现一致。

Spring 开启事务，按 KB → document → attempt 锁定并核对：KB ACTIVE、document ACTIVE 且 PROCESSING、当前版本匹配、attempt 属于同 job 且仍为有效非终态、数据库时钟尚未到 deadline、模型/维度与清单一致。满足后在**同一事务**将 attempt 置 SUCCEEDED、记录 manifest/finished_at，并将文档置 PUBLISHED、指向该 attempt。任一更新失败则整体回滚，不返回发布成功。

重复相同成功回调返回已经接纳的结果，不再修改发布指针；相同 attempt 的不同 manifest 或已超时/取消后的成功回调返回冲突。回调与截止扫描竞争同一行锁，最终条件更新使用实际数据库时间 `clock_timestamp()` 检查，不能用事务开始时的 now() 放行已过期限的结果。

这不是横跨 HTTP 和文件系统的分布式事务：FastAPI 先提交 AI 数据，Spring 后发布。View 的指针过滤保证中间态不可检索。

### 7.3 创建 run、停止、编辑与流式写入

Spring 先锁会话并验证 owner、KB 当前权限、问题长度、最后一轮规则及无活动生成。新问题原子创建 USER/ASSISTANT 消息、分配 ordinal、创建 run、设置 assistant.current_run_id；重试复用消息对，编辑只修改最近被停止的问题，均删除旧引用、清空当前回答并建立新 run。去重键冲突时相同命令返回原 run，不重复问答；载荷不一致返回冲突。

FastAPI 必须在首个正文 delta 之前向 Spring 提供本次检索证据清单（内部 `evidence` 控制事件，不直接给前端展示）；Spring 核验后保存到 evidence_manifest，后续才能逐批检查来源是否仍有效。这是对 ARD 事件契约的补充，不能等最终 done 才知道回答引用了哪些文档。

每批片段在一个短事务中同时更新 messages.content 与 run.last_seq，条件为：run 仍 GENERATING、该消息仍指向该 run、seq 大于已记录值、当前权限和证据仍有效。允许合并连续 delta 为有序批次，但只向前端转发已保存片段；重复 seq 丢弃，缺号/乱序按协议错误终止，不伪装完成。

收到 done 后再次核验引用：所有 evidence_id 在本次清单中，source_chunk_id 的 doc/version/attempt 一致且当前有效。把完整正文、最终 citations 和 COMPLETED 在同一事务提交，之后才向前端发最终 done。没有证据的合理澄清或资料不足提示可以 COMPLETED 且零引用。

停止/超时/断线先把 run 改为 STOPPED 或 INCOMPLETE，再请求 FastAPI 取消。部分唯一索引释放后才能创建新 run；旧事件即使迟到，也无法满足状态和 current_run 条件。30 秒是提交后仍无正文的截止线，并非每段正文的空闲超时；120 秒为本轮总上限，均包含检索和网络。

撤权/KB 暂停操作与流片段接纳需用一致的同步方式。统一全局锁序为 KB → 涉及的 documents（按 ID 排序）→ conversation → message/run 或 job/attempt；不涉及的层跳过。片段处理可先无锁读取 run 的证据 ID，再对 KB 取共享锁、对来源文档按序取共享锁，最后锁会话/run 并重新核验指针和清单；不得先锁 run 再倒回锁文档。授权变更对 KB 取排他锁后检查/变更成员并终止相关 run；文档变更按同一顺序阻断来源。快照隔离下的普通 SELECT 不会自动同步撤权，这一要求不能仅靠 FK 达成。权限事务提交后才向新请求放行；已经发送的网络字节无法收回。

### 7.4 删除策略与重启

| 操作 | 持久化动作 | 为什么不直接一路 CASCADE |
| --- | --- | --- |
| 撤销成员 | 删除 kb_members 行并使相关活动 run 失效 | 保留账号与历史会话；重新授权后可恢复访问 |
| 删除文档 | lifecycle 阻断、取消尝试 → 清 ai 全部版本/attempt 切片 → 清各版本原文件 → 清理元数据为最小标记 | 数据库级联不能替你删除磁盘文件，也不能先丢掉失败重试范围 |
| 删除知识库 | 置 DELETING → 逐文档上述清理 → 删除成员 → 最后置 DELETED 最小标记 | 中途失败置 DELETE_FAILED；所有员工访问持续暂停；历史会话仍能识别库已删除 |
| 删除会话 | 先使 run 失效 → 物理删除会话/消息/run/citations → 事务提交后取消计算 | 这些都属同一用户会话聚合，可 CASCADE；没有自动 INSERT/UPSERT 复活逻辑 |
| 替换文件 | 新版本提交后旧内容立即失效；旧版本物理清理另行记录 | 新版本失败也不能恢复旧内容；旧引用身份仍须保留 |

普通来源 FK 使用 NO ACTION/RESTRICT，防止意外删掉仍有依赖的用户、KB、文档版本或处理记录。会话到 messages、generation_runs 的归属 FK 用 ON DELETE CASCADE；run 到 citations 也 CASCADE；messages 的 current_run 循环 FK 用 DEFERRABLE NO ACTION，整会话删除在提交时不再有悬空行。引用到处理尝试不级联，ai 切片到 attempt 用 RESTRICT，物理清理顺序必须显式执行。

core 的版本/处理来源最小记录保留是为了历史关联和拒绝迟到结果，不含正文或可用文件地址；ai 切片和文件在删除成功前必须真的清理。可见列表过滤 tombstone，不能把软删除当作物理删除已完成。

Spring 重启时将旧 QUEUED/DISPATCHED/RUNNING 尝试标为 INTERRUPTED、文档置 FAILED；旧 GENERATING run 置 INCOMPLETE 并保留已保存片段。删除中状态保留剩余范围并标示未完成。**不自动重新解析、重新生成或重试删除**，由用户手动触发；网络重投同一仍有效尝试与失败后重处理是不同事情。

## 8. 验证清单与交付边界

| 验证场景 | 必须观察到的结果 |
| --- | --- |
| alice / ALICE 同时注册 | username_key 的唯一键只允许一个提交；不依赖“先查再插” |
| 重复授予同一 KB/员工 | 联合 PK 拒绝重复，接口可幂等返回已有授权 |
| 同库 Policy.pdf / policy.PDF | 活跃文档唯一索引拒绝重复；另一库可用同名 |
| 并发点击处理重试或发送两次问题 | 部分唯一索引和行锁保证仅一个有效尝试/生成；事务冲突给明确反馈 |
| 外文档/版本/attempt 混搭切片 | 联合 FK 拒绝写入，而不是只校验各 ID 分别存在 |
| v1成功、v2失败、v2重试A3中收到A2迟到结果 | 不恢复v1、不发布A2；只有A3成功后可检索 |
| FastAPI已写切片，Spring发布事务回滚 | View 不返回这些切片 |
| 删除清理后，旧解析器尝试再次写向量 | 数据库写入触发器拒绝失效来源，已删除切片不复活 |
| 同模型名但维度不符 / 不同模型同维度 | CHECK拒绝行内维度错误；配置/manifest/查询拒绝模型空间混用 |
| 员工仅有A库却篡改请求查B库 | Spring拒绝；Fast只接受签名A范围，SQL必须带单库约束 |
| R1停止后R2执行，R1继续发delta/done | 不修改 M2 或 R2，不创建旧引用 |
| 删除文档或替换后打开旧引用 | 仅提示失效，不返回旧文、不自动跳到新版本 |
| 会话删除时模型仍在输出 | 会话/消息/引用保持删除，回调找不到目标直接拒绝 |
| Spring在解析、生成或批量删除期间关闭 | 重启保留资料和部分结果，未完成显式失败；不自动恢复执行 |

Day 4 阶段完成的是表、关系、约束、View 和事务契约；当时未执行 SQL。ER 与数据字典使用同一份字段定义生成并做一致性核对；HTML 另做结构及浏览器检查。Day 12 已在 PostgreSQL 16.16 / pgvector 0.8.7 环境执行 users 和 knowledge_bases 的 Flyway 迁移，核对实际结构，并验证名称唯一性与创建人外键。该验收不覆盖其余表、并发事务、权限 View、处理状态机或向量检索；这些仍需后续迁移和对应集成场景验证。
