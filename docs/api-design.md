# API 接口设计

**版本：** v1.0（Day 5 契约稿）  
**日期：** 2026-11-19  
**依据：** [PRD](PRD.md)、[ARD](ARD.md)、[数据库设计](database-design.md)。本稿不改变产品范围，不实施应用代码。  
**学习材料：** [Day5：OpenAPI 与 Swagger](../Study_note/Day5.txt)。

## 1. 通信边界与阅读规则

React 只调用 Spring Boot 的 `/api/v1`。Spring 负责 Session、角色、知识库授权、对象归属、任务/会话状态和持久化；FastAPI 负责文档处理、受限检索及模型调用。普通接口使用 HTTP JSON；上传使用 multipart；下载是二进制；回答用 POST 响应里的 SSE。两个服务之间使用 `/internal/v1`，有些内部接口由 Spring 实现，不能把所有内部地址都路由到 FastAPI。

本地 React 通过 Vite 开发代理把 `/api` 转给 `127.0.0.1:8080`；FastAPI 在 `127.0.0.1:8000`，Spring 的内部文件/回调接口仍在8080。浏览器代理不转发 `/internal`；服务端对该路径只接受服务凭证。FastAPI不配置允许浏览器跨域的入口。本地loopback使用HTTP，跨主机部署才补HTTPS；调用外部模型始终走后端HTTPS。

选择 REST + POST SSE，以支持现有请求和单向流式结果。浏览器分别访问两个后端会复制身份/权限逻辑；WebSocket和消息总线会增加MVP不需要的状态。因此不引入它们，也不增加Agent工具、组织租户、模型管理或全局幂等平台。

### 1.1 全局请求规则

- JSON使用UTF-8、camelCase，`Content-Type: application/json`；字段未声明则拒绝422，避免批量绑定role/status等内部属性。FastAPI默认422、Spring默认异常需归一为本稿格式。无Body接口遇非空Body返回400；未知Query字段返回422。
- 所有Path ID、clientRequestId均为标准UUID；Path格式错误返回400 INVALID_PATH_PARAMETER，Query/Body校验错误为422 VALIDATION_ERROR。时间为ISO 8601 UTC；整数必须是JSON number，不能用数字字符串代替（明确声明ordinal为字符串的除外）。
- 名称与问题按PRD trim；密码不trim。字符数按Unicode码点，与PostgreSQL char_length对齐。multipart filename规则见文档上传；SHA-256为64位小写十六进制。
- 分页统一1起始，page默认1，pageSize默认20、最大100；返回items/page/pageSize/total/hasNext；total只统计有权可见项。分页是实时快照，新增/删除时可能变动，不承诺跨请求快照一致性。
- 公开JSON/流式/文件响应统一 `Cache-Control: no-store`；每个HTTP响应包含 `X-Request-Id`。Spring生成不含敏感信息的requestId并透传内部链路，不能当作用户、run或幂等身份。
- JSON响应中的data对象只承诺本稿声明的字段；客户端忽略新增响应字段，服务端拒绝未知请求字段。未注明可空的字段必填且不可null；可选字段省略与null的语义逐接口说明。

### 1.2 Session 与 CSRF

采用Day3的Spring Security + 服务端Session，不新增浏览器JWT/refresh-token。Cookie名 `SESSION`，HttpOnly、SameSite=Lax、Path=/；本地HTTP不设Secure，HTTPS部署设Secure；不设Domain，不把Cookie内容放localStorage。初始Session空闲超时30分钟，后端可配置；每次请求和活动流须验证Session仍有效。

React先GET `/api/v1/auth/csrf`，保存响应中的CSRF token于内存；所有POST/PUT/PATCH/DELETE，包括注册、登录、退出和SSE POST，带 `X-CSRF-TOKEN`。登录成功轮换Session及token；退出后重新获取。开发代理保持浏览器同源，fetch使用credentials:'include'；不以开放CORS替代CSRF。

公开curl/HTTP示例中的 `Cookie: SESSION=demo-session`、`X-CSRF-TOKEN: demo-csrf-token` 仅演示头部，不能当有效凭证。普通读接口不要求CSRF；公开变更接口缺失/错误token返回403 CSRF_INVALID。内部接口不用浏览器CSRF，也不接受Session替代服务认证。服务凭证不传给React。

### 1.3 权限与错误优先级

先验证协议/登录和CSRF，再按对象处理角色、归属和当前KB权限；对象不存在才是404。**已存在KB但员工无授权必须403 KNOWLEDGE_BASE_FORBIDDEN，不能以200空对象假装成功。** 会话、run和citation即使管理员也不能读取别人的私人对话。普通员工不能通过metadata/detail接口看到未发布文档或管理员错误详情。

权限拒绝不包含文档名/原文/文件地址/隐藏标题；只返回通用错误data:null。处理/删除状态对管理员可见；历史列表对owner提供脱敏占位行，详情依旧403。KB暂停/来源变更的状态错误只在当前身份有权获知对象时返回。列表总数、摘要和分页也执行同一过滤。

## 2. 统一响应、状态码与对象约定

### 2.1 JSON envelope

```json
{"code":"SUCCESS","message":"Request successful","data":{"id":"20000000-0000-4000-8000-000000000001","name":"HR Policy"},"requestId":"req-demo-001"}
```

```json
{"code":"DOCUMENT_NOT_FOUND","message":"文档不存在","data":null,"requestId":"req-demo-002"}
```

HTTP状态码决定协议结果，code表达业务原因；禁止所有错误都返回200。message可本地化，前端逻辑按code判断。成功code固定SUCCESS；分页放data里。所有JSON错误均data:null，具体字段错误可选放顶层 `errors:[{"field":"name","code":"TOO_LONG","message":"最多50个字符"}]`，只能描述输入字段，不回显密码。幂等重复的原run位置通过Location响应头返回，不能在error.data临时塞另一种对象。

| HTTP | 用途 |
| --- | --- |
| 200 | 查询、修改、停止、已完成删除/内部清理；SSE握手成功也200，但不代表生成完成 |
| 201 | 注册、新建知识库或会话；可用Location标明资源地址（注册例外，不承诺已登录） |
| 202 | 文档已接收待处理、清理已受理；前端须轮询，不能显示“发布/删除成功” |
| 400 | JSON损坏、路径ID错误、无Body接口收到了Body |
| 401 | 缺失/失效Session、登录凭据不正确，或内部服务身份验证失败 |
| 403 | 已认证但角色/对象/知识库权限不足，CSRF/签名用途不符 |
| 404 | 目标不存在或已物理删除；不用于掩盖明确要求返回403的KB授权失败 |
| 409 | 当前业务状态冲突、重名、幂等载荷冲突、旧版本/旧attempt、同会话已有执行 |
| 410 | 有权限的请求访问已删除来源或已过期票据/作用域 |
| 413 / 415 / 422 | 体积超限 / 格式不支持 / 字段或内容校验不通过 |
| 500 / 502 / 503 / 504 / 507 | 内部错误 / 上游错误 / 暂不可用 / 同步等待超时 / 文件存储失败 |

所有已登录接口还可能返回401 AUTHENTICATION_REQUIRED、403 ADMIN_REQUIRED（仅管理接口）、403 CSRF_INVALID（仅变更）、400 INVALID_JSON/INVALID_PATH_PARAMETER、422 VALIDATION_ERROR、500 INTERNAL_ERROR、503 SERVICE_UNAVAILABLE；接口错误表列业务特有项，不排除这些通用错误。匿名接口也执行JSON/字段/CSRF/存储异常规范。禁止返回堆栈、SQL、token、storageKey或供应商原始错误体。

下载成功与SSE成功不包JSON envelope：下载返回文件字节；SSE按第9节事件封装。握手前错误仍是JSON+相应HTTP状态；HTTP头提交后不能改状态码或在流尾拼一个JSON错误对象。

### 2.2 返回字段与状态含义

| 对象 | 字段/规则 |
| --- | --- |
| User | id UUID、username string、role ADMIN/EMPLOYEE、createdAt 时间；不返回密码哈希 |
| KnowledgeBase | id、name(删除标记可null)、description(null允许)、lifecycle、createdBy、createdAt/updatedAt；管理员详情另带documentCount和deletion |
| deletion | remainingDocuments/failedDocuments 非负整数、error:null或{code,message}；DELETED必须remaining=0且所有文件/索引清理完成 |
| Document | id/kbId/fileName/mediaType/sizeBytes/documentVersion/status/lifecycle/createdAt/updatedAt；只向ADMIN发failure与processing；DELETED元数据清空 |
| processing | jobId/attemptId/attemptNo/state/deadlineAt；当前未完成尝试或最近失败尝试；没有时null；所有参数来自Spring数据库 |
| failure | null或{code,message}；只对ADMIN展示解析/超时等原因，消息净化 |
| ConversationSummary | id/kbId/knowledgeBaseName/title/accessState/createdAt/lastMessageAt；空会话title/lastMessageAt=null；隐藏时name/title=null且有displayText |
| Run | runId/conversationId/userMessageId/assistantMessageId/clientRequestId/status/isCurrent/lastSeq/requestedAt/firstTextAt/finishedAt/error；时间未发生可null；不含旧回答正文 |
| CitationSummary | id/number/documentId/documentVersion/fileName/pageStart/pageEnd/availability；失效时fileName/pageStart/pageEnd=null，不附原文；真实文本另走citation详情 |
| Message | id、ordinal十进制字符串、role USER/ASSISTANT、replyToMessageId、content、createdAt/updatedAt；ASSISTANT另带currentRunId/currentClientRequestId/status/lastSeq/citations/sourceWarning；currentClientRequestId用于匹配响应丢失时的原提交 |

文档处理status只使用PROCESSING/FAILED/PUBLISHED；内部lifecycle为ACTIVE/DELETING/DELETE_FAILED/DELETED；processing.state使用Day4八种attempt状态。会话accessState为AVAILABLE/FORBIDDEN/KB_UNAVAILABLE/KB_DELETED；run.status只使用GENERATING/COMPLETED/INCOMPLETE/STOPPED。引用availability为AVAILABLE/SOURCE_CHANGED/SOURCE_DELETED/SOURCE_UNAVAILABLE，逐引用动态判断，不从模型猜测。上述metadata不是授权快照。

以下示例UUID均为格式合法的演示值。模型名为配置标识，不代表已选择供应商；出现的embeddingDimensions=1024**仅演示正整数字段**，运行值必须来自最终模型配置和实际向量核验，不能抄为DDL固定维度。时间和SHA为示例，不可作为验收实测或真实manifest。

## 3. 用户认证

### 3.1 POST `/api/v1/auth/register` — 用户注册

**调用方向：** React → Spring Boot。  
**权限：** 匿名可用；仍须 CSRF。注册成功不登录。

| 参数位置 | 契约 |
| --- | --- |
| Path | 无 |
| Query | 无 |
| Body | JSON：username:string 必填，trim 后3～32字符，仅[A-Za-z0-9_]；password:string 必填8～64字符、区分大小写、不 trim；confirmPassword:string 必填且相等。禁止 role、userId 等额外字段。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
POST /api/v1/auth/register HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
X-CSRF-TOKEN: demo-csrf-token
Content-Type: application/json
Accept: application/json

{
  "username": "alice",
  "password": "DemoPass_2026",
  "confirmPassword": "DemoPass_2026"
}
```

成功示例：HTTP **201**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "id": "10000000-0000-4000-8000-000000000001",
    "username": "alice",
    "role": "EMPLOYEE",
    "createdAt": "2026-11-19T02:00:00Z"
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 409 | `USERNAME_ALREADY_EXISTS` | 用户名已存在 |
| 422 | `VALIDATION_ERROR` | 用户名、密码或确认密码不符合要求 |

错误响应示例：HTTP **409**。

```json
{
  "code": "USERNAME_ALREADY_EXISTS",
  "message": "用户名已存在",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：服务端只创建 EMPLOYEE，初始无 KB 授权。数据库 username_key 保证大小写不敏感唯一；不返回 passwordHash。注册成功不发指向 /me 的Location，前端跳转登录页。

### 3.2 POST `/api/v1/auth/login` — 用户登录

**调用方向：** React → Spring Boot。  
**权限：** 匿名可用；须 CSRF。

| 参数位置 | 契约 |
| --- | --- |
| Path | 无 |
| Query | 无 |
| Body | JSON：username:string 必填，按注册相同 trim/大小写规则识别；password:string 必填，不修改大小写/空白。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
POST /api/v1/auth/login HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
X-CSRF-TOKEN: demo-csrf-token
Content-Type: application/json
Accept: application/json

{
  "username": "alice",
  "password": "DemoPass_2026"
}
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "user": {
      "id": "10000000-0000-4000-8000-000000000001",
      "username": "alice",
      "role": "EMPLOYEE",
      "createdAt": "2026-11-19T02:00:00Z"
    },
    "csrfToken": "demo-csrf-after-login"
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 401 | `INVALID_CREDENTIALS` | 账号或密码错误 |
| 422 | `VALIDATION_ERROR` | 登录参数不合法 |

错误响应示例：HTTP **401**。

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "账号或密码错误",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：成功轮换 Session ID 和 CSRF token，Set-Cookie 建立服务端 Session；请求和响应均禁止日志记录密码。用户名不存在与密码错误使用同一失败码。

### 3.3 POST `/api/v1/auth/logout` — 用户退出

**调用方向：** React → Spring Boot。  
**权限：** 当前 Session 或匿名；须 CSRF，过期时先重新获取匿名 CSRF。

| 参数位置 | 契约 |
| --- | --- |
| Path | 无 |
| Query | 无 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
POST /api/v1/auth/logout HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
X-CSRF-TOKEN: demo-csrf-token
Accept: application/json
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "loggedOut": true
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `CSRF_INVALID` | CSRF 校验失败 |

错误响应示例：HTTP **403**。

```json
{
  "code": "CSRF_INVALID",
  "message": "CSRF 校验失败",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：使当前 Session 失效并清除 Cookie；已无登录态仍返回200。使该 Session 的活动 run 失效并尽力取消，保留历史；不影响另一 Session 的登录。

### 3.4 GET `/api/v1/auth/me` — 当前用户

**调用方向：** React → Spring Boot。  
**权限：** 已登录。

| 参数位置 | 契约 |
| --- | --- |
| Path | 无 |
| Query | 无 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
GET /api/v1/auth/me HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
Accept: application/json
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "id": "10000000-0000-4000-8000-000000000001",
    "username": "alice",
    "role": "EMPLOYEE",
    "createdAt": "2026-11-19T02:00:00Z"
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 401 | `AUTHENTICATION_REQUIRED` | 请先登录 |

错误响应示例：HTTP **401**。

```json
{
  "code": "AUTHENTICATION_REQUIRED",
  "message": "请先登录",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：返回服务端身份与固定角色；不把角色从请求参数读取。不附送全部文档或授权快照作为后续通行证。

## 4. 知识库

### 4.1 POST `/api/v1/knowledge-bases` — 创建知识库

**调用方向：** React → Spring Boot。  
**权限：** ADMIN。

| 参数位置 | 契约 |
| --- | --- |
| Path | 无 |
| Query | 无 |
| Body | JSON：name:string 必填，trim 后1～50字符、英文字母大小写不敏感唯一；description:string / null 可选，最多500字符，省略等同 null。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
POST /api/v1/knowledge-bases HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
X-CSRF-TOKEN: demo-csrf-token
Content-Type: application/json
Accept: application/json

{
  "name": "HR Policy",
  "description": "企业制度演示库"
}
```

成功示例：HTTP **201**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "id": "20000000-0000-4000-8000-000000000001",
    "name": "HR Policy",
    "description": "企业制度演示库",
    "lifecycle": "ACTIVE",
    "createdBy": "10000000-0000-4000-8000-000000000002",
    "createdAt": "2026-11-19T02:00:00Z",
    "updatedAt": "2026-11-19T02:00:00Z"
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 409 | `KNOWLEDGE_BASE_NAME_CONFLICT` | 知识库名称已存在 |
| 422 | `VALIDATION_ERROR` | 名称或简介不合法 |

错误响应示例：HTTP **409**。

```json
{
  "code": "KNOWLEDGE_BASE_NAME_CONFLICT",
  "message": "知识库名称已存在",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：创建空库，不默认授权员工。Location: /api/v1/knowledge-bases/{id}。不支持幂等重放；响应不确定时先查列表，勿盲目创建。

### 4.2 GET `/api/v1/knowledge-bases` — 知识库列表

**调用方向：** React → Spring Boot。  
**权限：** ADMIN 查看非 DELETED 库及管理状态；EMPLOYEE 只查看当前已授权 ACTIVE 库。

| 参数位置 | 契约 |
| --- | --- |
| Path | 无 |
| Query | page:integer 默认1且>=1；pageSize:integer 默认20，1～100。排序 createdAt DESC,id ASC。 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
GET /api/v1/knowledge-bases?page=1&pageSize=20 HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
Accept: application/json
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "items": [
      {
        "id": "20000000-0000-4000-8000-000000000001",
        "name": "HR Policy",
        "description": "企业制度演示库",
        "lifecycle": "ACTIVE",
        "createdBy": "10000000-0000-4000-8000-000000000002",
        "createdAt": "2026-11-19T02:00:00Z",
        "updatedAt": "2026-11-19T02:00:00Z"
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "hasNext": false
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 422 | `VALIDATION_ERROR` | 分页参数不合法 |

错误响应示例：HTTP **422**。

```json
{
  "code": "VALIDATION_ERROR",
  "message": "分页参数不合法",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：无授权返回200、items=[]、total=0；total 只统计当前可见范围。没有全局搜索或跨库文档检索参数。

### 4.3 GET `/api/v1/knowledge-bases/{kbId}` — 知识库详情

**调用方向：** React → Spring Boot。  
**权限：** ADMIN；或对该 ACTIVE 库当前有成员授权的 EMPLOYEE。

| 参数位置 | 契约 |
| --- | --- |
| Path | kbId:UUID 必填。 |
| Query | 无 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
GET /api/v1/knowledge-bases/20000000-0000-4000-8000-000000000001 HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
Accept: application/json
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "id": "20000000-0000-4000-8000-000000000001",
    "name": "HR Policy",
    "description": "企业制度演示库",
    "lifecycle": "ACTIVE",
    "createdBy": "10000000-0000-4000-8000-000000000002",
    "createdAt": "2026-11-19T02:00:00Z",
    "updatedAt": "2026-11-19T02:00:00Z",
    "documentCount": 12
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `KNOWLEDGE_BASE_FORBIDDEN` | 没有该知识库访问权限 |
| 404 | `KNOWLEDGE_BASE_NOT_FOUND` | 知识库不存在 |

错误响应示例：HTTP **403**。

```json
{
  "code": "KNOWLEDGE_BASE_FORBIDDEN",
  "message": "没有该知识库访问权限",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：documentCount 对管理员统计未删除文档，对员工仅统计已发布可用文档。管理员轮询删除时可读取最小 tombstone：lifecycle=DELETED、name=null、description=null、documentCount=0；不开放给员工。删除进度另含 deletion 对象，见第2节。

### 4.4 PATCH `/api/v1/knowledge-bases/{kbId}` — 修改知识库

**调用方向：** React → Spring Boot。  
**权限：** ADMIN，且库为 ACTIVE。

| 参数位置 | 契约 |
| --- | --- |
| Path | kbId:UUID 必填。 |
| Query | 无 |
| Body | JSON：name:string 可选但不可 null/空；description:string / null 可选。至少提供一项；省略表示不修改，description=null 表示清空；长度/重名规则同创建。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
PATCH /api/v1/knowledge-bases/20000000-0000-4000-8000-000000000001 HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
X-CSRF-TOKEN: demo-csrf-token
Content-Type: application/json
Accept: application/json

{
  "description": "报销与出差制度演示库"
}
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "id": "20000000-0000-4000-8000-000000000001",
    "name": "HR Policy",
    "description": "报销与出差制度演示库",
    "lifecycle": "ACTIVE",
    "createdBy": "10000000-0000-4000-8000-000000000002",
    "createdAt": "2026-11-19T02:00:00Z",
    "updatedAt": "2026-11-19T02:00:00Z"
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 404 | `KNOWLEDGE_BASE_NOT_FOUND` | 知识库不存在 |
| 409 | `KNOWLEDGE_BASE_NAME_CONFLICT` | 知识库名称已存在 |
| 409 | `KNOWLEDGE_BASE_NOT_ACTIVE` | 知识库正在删除或已删除 |
| 422 | `VALIDATION_ERROR` | 没有有效修改字段或字段不合法 |

错误响应示例：HTTP **404**。

```json
{
  "code": "KNOWLEDGE_BASE_NOT_FOUND",
  "message": "知识库不存在",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：不允许修改 createdBy、lifecycle 或 members；改名不改变文档、授权和会话关联。重复相同 PATCH 的业务效果相同。

### 4.5 DELETE `/api/v1/knowledge-bases/{kbId}` — 删除或继续清理知识库

**调用方向：** React → Spring Boot。  
**权限：** ADMIN；非空库必须校验当前登录管理员密码。

| 参数位置 | 契约 |
| --- | --- |
| Path | kbId:UUID 必填。 |
| Query | 无 |
| Body | JSON：confirm:boolean 必须为 true；currentPassword:string 对非空库首次删除及失败重试必填，空库可省略。不把密码放 Query/URL。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
DELETE /api/v1/knowledge-bases/20000000-0000-4000-8000-000000000001 HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
X-CSRF-TOKEN: demo-csrf-token
Content-Type: application/json
Accept: application/json

{
  "confirm": true,
  "currentPassword": "DemoAdminPass_2026"
}
```

成功示例：HTTP **202**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "kbId": "20000000-0000-4000-8000-000000000001",
    "lifecycle": "DELETING",
    "deletion": {
      "remainingDocuments": 12,
      "failedDocuments": 0,
      "error": null
    },
    "statusUrl": "/api/v1/knowledge-bases/20000000-0000-4000-8000-000000000001"
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 404 | `KNOWLEDGE_BASE_NOT_FOUND` | 知识库不存在 |
| 403 | `ADMIN_PASSWORD_INVALID` | 管理员密码校验失败 |
| 422 | `CONFIRMATION_REQUIRED` | 请确认不可恢复删除 |

错误响应示例：HTTP **404**。

```json
{
  "code": "KNOWLEDGE_BASE_NOT_FOUND",
  "message": "知识库不存在",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：DELETE 的业务结果幂等；本地 fetch 明确携带 JSON 请求体，代理和 OpenAPI 生成器须保留它。202 仅表示已接受，前端不能显示删除成功；GET 轮询至 DELETED 才成功。先阻断访问和新上传，再逐文档清理，最后删授权并留最小标记。正在 DELETING 的重复请求返回202且不并行清理；DELETE_FAILED 时本次明确操作继续剩余清理，仍须确认/必要密码；完成后的重复 DELETE 返回200、lifecycle=DELETED。失败由详情 deletion.error 返回，不自动重试。

## 5. 文档管理

### 5.1 POST `/api/v1/knowledge-bases/{kbId}/documents` — 上传单份文档

**调用方向：** React → Spring Boot。  
**权限：** ADMIN，且 KB ACTIVE。

| 参数位置 | 契约 |
| --- | --- |
| Path | kbId:UUID 必填。 |
| Query | 无 |
| Body | multipart/form-data：file:binary 必填且仅一个；非空，<=10,000,000字节；TXT(UTF-8)、文字PDF、DOCX、MD/MARKDOWN(UTF-8)。文件名取 multipart filename 的安全 basename，拒绝路径、NUL、空名，不擅自 trim 合法文件名。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
POST /api/v1/knowledge-bases/20000000-0000-4000-8000-000000000001/documents HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
X-CSRF-TOKEN: demo-csrf-token
Accept: application/json
Content-Type: multipart/form-data; boundary=demo-boundary

--demo-boundary
Content-Disposition: form-data; name="file"; filename="报销制度.pdf"
Content-Type: application/pdf

[本部分为真实PDF字节，示例省略]
--demo-boundary--
```

成功示例：HTTP **202**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "id": "30000000-0000-4000-8000-000000000001",
    "kbId": "20000000-0000-4000-8000-000000000001",
    "fileName": "报销制度.pdf",
    "mediaType": "application/pdf",
    "sizeBytes": 18432,
    "documentVersion": 1,
    "status": "PROCESSING",
    "lifecycle": "ACTIVE",
    "createdAt": "2026-11-19T02:00:00Z",
    "updatedAt": "2026-11-19T02:00:00Z",
    "failure": null,
    "processing": {
      "jobId": "40000000-0000-4000-8000-000000000001",
      "attemptId": "50000000-0000-4000-8000-000000000001",
      "attemptNo": 1,
      "state": "QUEUED",
      "deadlineAt": "2026-11-19T02:05:00Z"
    }
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 404 | `KNOWLEDGE_BASE_NOT_FOUND` | 知识库不存在 |
| 409 | `KNOWLEDGE_BASE_NOT_ACTIVE` | 知识库不可用 |
| 409 | `DOCUMENT_NAME_CONFLICT` | 同库已有同名文档，请替换文件或改名 |
| 413 | `FILE_TOO_LARGE` | 文件超过10000000字节 |
| 415 | `UNSUPPORTED_FILE_TYPE` | 文件格式不支持 |
| 422 | `EMPTY_FILE` | 不允许上传空文件 |
| 507 | `FILE_STORAGE_FAILED` | 文件保存失败 |

错误响应示例：HTTP **404**。

```json
{
  "code": "KNOWLEDGE_BASE_NOT_FOUND",
  "message": "知识库不存在",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：校验后落盘并同事务创建 document/version/job/attempt。Location 指向文档详情；上传完成不等于发布。若解析损坏/无文字是在异步阶段发现，随后状态 FAILED，并非上传请求立即返回422。重复上传以同库不区分英文字母大小写的文件名唯一键拒绝409；不承诺重放原202。公开上传不存在数据库尚未支持的全局幂等缓存，响应不确定先查列表确认，勿换文件名自动重传。

### 5.2 GET `/api/v1/knowledge-bases/{kbId}/documents` — 文档列表

**调用方向：** React → Spring Boot。  
**权限：** ADMIN 管理；EMPLOYEE 必须当前有 KB 授权且仅见 ACTIVE/PUBLISHED。

| 参数位置 | 契约 |
| --- | --- |
| Path | kbId:UUID 必填。 |
| Query | page/pageSize 同全局；status:PROCESSING / FAILED / PUBLISHED 可选，仅 ADMIN 可用；不提供关键词搜索。排序 createdAt DESC,id ASC。 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
GET /api/v1/knowledge-bases/20000000-0000-4000-8000-000000000001/documents?page=1&pageSize=20 HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
Accept: application/json
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "items": [
      {
        "id": "30000000-0000-4000-8000-000000000001",
        "kbId": "20000000-0000-4000-8000-000000000001",
        "fileName": "报销制度.pdf",
        "mediaType": "application/pdf",
        "sizeBytes": 18432,
        "documentVersion": 1,
        "status": "PUBLISHED",
        "lifecycle": "ACTIVE",
        "createdAt": "2026-11-19T02:00:00Z",
        "updatedAt": "2026-11-19T02:00:00Z",
        "failure": null,
        "processing": null
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "hasNext": false
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `KNOWLEDGE_BASE_FORBIDDEN` | 没有知识库权限 |
| 403 | `FILTER_NOT_ALLOWED` | 员工不能查询处理状态筛选 |
| 404 | `KNOWLEDGE_BASE_NOT_FOUND` | 知识库不存在 |
| 409 | `KNOWLEDGE_BASE_NOT_ACTIVE` | 知识库暂停使用 |
| 422 | `VALIDATION_ERROR` | 查询参数不合法 |

错误响应示例：HTTP **403**。

```json
{
  "code": "KNOWLEDGE_BASE_FORBIDDEN",
  "message": "没有知识库权限",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：员工 total 只统计可用文档。ADMIN 可见 DELETING/DELETE_FAILED 文档以继续清理；DELETED 不在列表。失败处理字段只发给管理员。

### 5.3 GET `/api/v1/documents/{documentId}` — 文档详情与处理/删除进度

**调用方向：** React → Spring Boot。  
**权限：** ADMIN；或 KB 当前有权且文档可用的 EMPLOYEE。

| 参数位置 | 契约 |
| --- | --- |
| Path | documentId:UUID 必填。 |
| Query | 无 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
GET /api/v1/documents/30000000-0000-4000-8000-000000000001 HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
Accept: application/json
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "id": "30000000-0000-4000-8000-000000000001",
    "kbId": "20000000-0000-4000-8000-000000000001",
    "fileName": "报销制度.pdf",
    "mediaType": "application/pdf",
    "sizeBytes": 18432,
    "documentVersion": 1,
    "status": "PUBLISHED",
    "lifecycle": "ACTIVE",
    "createdAt": "2026-11-19T02:00:00Z",
    "updatedAt": "2026-11-19T02:00:00Z",
    "failure": null,
    "processing": null
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `KNOWLEDGE_BASE_FORBIDDEN` | 没有所属知识库权限 |
| 404 | `DOCUMENT_NOT_FOUND` | 文档不存在 |
| 409 | `DOCUMENT_NOT_PUBLISHED` | 文档尚不可访问 |
| 409 | `KNOWLEDGE_BASE_NOT_ACTIVE` | 知识库暂停使用 |

错误响应示例：HTTP **403**。

```json
{
  "code": "KNOWLEDGE_BASE_FORBIDDEN",
  "message": "没有所属知识库权限",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：管理员轮询可见 processing/job/attempt/error，不暴露 storageKey/绝对路径。管理员读取 DELETED tombstone 返回200、fileName/mediaType/sizeBytes=null 和 lifecycle=DELETED，用于确认物理清理结束；员工不能读取。

### 5.4 GET `/api/v1/documents/{documentId}/download` — 下载当前原文件

**调用方向：** React → Spring Boot。  
**权限：** ADMIN 或当前拥有该 KB 读权限的用户；所有角色下载内容均要求 KB ACTIVE、文档 ACTIVE/PUBLISHED。

| 参数位置 | 契约 |
| --- | --- |
| Path | documentId:UUID 必填。 |
| Query | documentVersion:integer>=1 可选；提供时必须等于当前版本，用于引用下载防止旧链接误取新版本；不支持历史下载。 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
GET /api/v1/documents/30000000-0000-4000-8000-000000000001/download HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
Accept: application/octet-stream
```

成功示例：`200 OK`。

```http
HTTP/1.1 200 OK
Content-Type: application/pdf
Content-Disposition: attachment; filename*=UTF-8''%E6%8A%A5%E9%94%80%E5%88%B6%E5%BA%A6.pdf
Content-Length: 18432
Cache-Control: no-store
X-Request-Id: req-demo-001

[真实文件字节，不是JSON]
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `KNOWLEDGE_BASE_FORBIDDEN` | 没有知识库权限 |
| 404 | `DOCUMENT_NOT_FOUND` | 文档不存在 |
| 410 | `DOCUMENT_DELETED` | 文档已删除 |
| 409 | `SOURCE_CHANGED` | 请求的文件版本已更新 |
| 409 | `DOCUMENT_NOT_PUBLISHED` | 当前文件不可访问 |
| 500 | `FILE_UNAVAILABLE` | 数据库记录存在但原文件不可读取 |

错误响应示例：HTTP **403**。

```json
{
  "code": "KNOWLEDGE_BASE_FORBIDDEN",
  "message": "没有知识库权限",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：成功返回二进制流，不包 JSON；无重定向到本地路径/外部公开 URL。鉴权在发送任何文件字节之前完成。失败未提交流时返回 JSON 错误；流中读失败只能中断连接，客户端不能将不完整文件标成功。第一版不支持 Range 或断点续传。下载响应 no-store、X-Content-Type-Options:nosniff。

### 5.5 DELETE `/api/v1/documents/{documentId}` — 删除或重试清理文档

**调用方向：** React → Spring Boot。  
**权限：** ADMIN；须显式确认。

| 参数位置 | 契约 |
| --- | --- |
| Path | documentId:UUID 必填。 |
| Query | 无 |
| Body | JSON：confirm:boolean 必须 true。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
DELETE /api/v1/documents/30000000-0000-4000-8000-000000000001 HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
X-CSRF-TOKEN: demo-csrf-token
Content-Type: application/json
Accept: application/json

{
  "confirm": true
}
```

成功示例：HTTP **202**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "documentId": "30000000-0000-4000-8000-000000000001",
    "lifecycle": "DELETING",
    "statusUrl": "/api/v1/documents/30000000-0000-4000-8000-000000000001"
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 404 | `DOCUMENT_NOT_FOUND` | 文档不存在 |
| 422 | `CONFIRMATION_REQUIRED` | 请确认删除 |
| 409 | `KNOWLEDGE_BASE_NOT_ACTIVE` | 知识库已进入批量删除流程 |

错误响应示例：HTTP **404**。

```json
{
  "code": "DOCUMENT_NOT_FOUND",
  "message": "文档不存在",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：处理中也可删除：先使尝试失效、停服务，再清索引及所有版本文件。GET 轮询到 DELETED 才成功。DELETING 重复 DELETE 返回202；DELETE_FAILED 再次明确确认后继续剩余清理；已 DELETED 返回200。整库正在删除时由批量清理器负责，避免两个并行清理器。

## 6. AI 对话

### 6.1 POST `/api/v1/conversations` — 创建空对话

**调用方向：** React → Spring Boot。  
**权限：** 已登录并对指定 KB 有当前读权限；管理员按固定角色可读，仍只创建自己的会话。

| 参数位置 | 契约 |
| --- | --- |
| Path | 无 |
| Query | 无 |
| Body | JSON：kbId:UUID 必填。禁止 ownerId、messages 和自定义 title。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
POST /api/v1/conversations HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
X-CSRF-TOKEN: demo-csrf-token
Content-Type: application/json
Accept: application/json

{
  "kbId": "20000000-0000-4000-8000-000000000001"
}
```

成功示例：HTTP **201**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "id": "60000000-0000-4000-8000-000000000001",
    "kbId": "20000000-0000-4000-8000-000000000001",
    "knowledgeBaseName": "HR Policy",
    "title": null,
    "accessState": "AVAILABLE",
    "createdAt": "2026-11-19T02:00:00Z",
    "lastMessageAt": null
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `KNOWLEDGE_BASE_FORBIDDEN` | 没有知识库权限 |
| 404 | `KNOWLEDGE_BASE_NOT_FOUND` | 知识库不存在 |
| 409 | `KNOWLEDGE_BASE_NOT_ACTIVE` | 知识库暂停使用 |

错误响应示例：HTTP **403**。

```json
{
  "code": "KNOWLEDGE_BASE_FORBIDDEN",
  "message": "没有知识库权限",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：同库可创建多个独立空会话；不自动启动模型。Location 指向详情。无需幂等缓存；响应丢失先查历史，新建两个空会话不重复模型调用。

### 6.2 GET `/api/v1/conversations` — 个人对话列表

**调用方向：** React → Spring Boot。  
**权限：** 已登录，只看自己的；ADMIN 也不能读取他人的会话。

| 参数位置 | 契约 |
| --- | --- |
| Path | 无 |
| Query | page/pageSize 同全局；kbId:UUID 可选，只筛选本人关联的会话，不扩大权限。排序 lastMessageAt DESC NULLS LAST,createdAt DESC,id ASC。 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
GET /api/v1/conversations?page=1&pageSize=20 HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
Accept: application/json
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "items": [
      {
        "id": "60000000-0000-4000-8000-000000000001",
        "kbId": "20000000-0000-4000-8000-000000000001",
        "knowledgeBaseName": "HR Policy",
        "title": null,
        "accessState": "AVAILABLE",
        "createdAt": "2026-11-19T02:00:00Z",
        "lastMessageAt": null
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "hasNext": false
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 422 | `VALIDATION_ERROR` | 分页或筛选参数不合法 |

错误响应示例：HTTP **422**。

```json
{
  "code": "VALIDATION_ERROR",
  "message": "分页或筛选参数不合法",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：无权限仍保留列表项，但 title=null、knowledgeBaseName=null，accessState=FORBIDDEN，displayText=关联知识库不可访问；库已 DELETED 则 accessState=KB_DELETED、displayText=关联知识库已删除。不返回摘要、正文或引用。列表不包含 ownerId 选择器。

### 6.3 GET `/api/v1/conversations/{conversationId}` — 获取已保存的对话历史

**调用方向：** React → Spring Boot。  
**权限：** 会话 owner，且当前有 KB 访问权。

| 参数位置 | 契约 |
| --- | --- |
| Path | conversationId:UUID 必填。 |
| Query | afterOrdinal:十进制整数字符串 可选，默认"0"且>=0；limit:integer 默认50、1～100。按 ordinal ASC 返回其后消息；返回的 ordinal 用字符串避免 JS bigint 精度丢失。 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
GET /api/v1/conversations/60000000-0000-4000-8000-000000000001?afterOrdinal=0&limit=50 HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
Accept: application/json
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "conversation": {
      "id": "60000000-0000-4000-8000-000000000001",
      "kbId": "20000000-0000-4000-8000-000000000001",
      "knowledgeBaseName": "HR Policy",
      "title": "报销最晚什么时候提交？",
      "accessState": "AVAILABLE",
      "createdAt": "2026-11-19T02:00:00Z",
      "lastMessageAt": "2026-11-19T02:00:00Z"
    },
    "items": [
      {
        "id": "70000000-0000-4000-8000-000000000001",
        "ordinal": "1",
        "role": "USER",
        "replyToMessageId": null,
        "content": "报销最晚什么时候提交？",
        "createdAt": "2026-11-19T02:00:00Z",
        "updatedAt": "2026-11-19T02:00:00Z"
      },
      {
        "id": "70000000-0000-4000-8000-000000000002",
        "ordinal": "2",
        "role": "ASSISTANT",
        "replyToMessageId": "70000000-0000-4000-8000-000000000001",
        "content": "应在出差结束后十个工作日内提交〔1〕。",
        "currentRunId": "80000000-0000-4000-8000-000000000001",
        "currentClientRequestId": "90000000-0000-4000-8000-000000000001",
        "status": "COMPLETED",
        "lastSeq": 5,
        "citations": [
          {
            "id": "a0000000-0000-4000-8000-000000000001",
            "number": 1,
            "documentId": "30000000-0000-4000-8000-000000000001",
            "documentVersion": 1,
            "fileName": "报销制度.pdf",
            "pageStart": 3,
            "pageEnd": 3,
            "availability": "AVAILABLE"
          }
        ],
        "sourceWarning": null,
        "createdAt": "2026-11-19T02:00:00Z",
        "updatedAt": "2026-11-19T02:00:00Z"
      }
    ],
    "hasMore": false,
    "nextAfterOrdinal": "2"
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `CONVERSATION_FORBIDDEN` | 不能访问他人会话 |
| 403 | `KNOWLEDGE_BASE_FORBIDDEN` | 关联知识库不可访问 |
| 403 | `KNOWLEDGE_BASE_DELETED` | 关联知识库已删除 |
| 404 | `CONVERSATION_NOT_FOUND` | 会话不存在 |
| 409 | `KNOWLEDGE_BASE_NOT_ACTIVE` | 知识库暂停使用 |

错误响应示例：HTTP **403**。

```json
{
  "code": "CONVERSATION_FORBIDDEN",
  "message": "不能访问他人会话",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：返回数据库已保存片段/终态，不主动连接 FastAPI。正在生成的历史是快照；末条 ASSISTANT 可能被继续追加，恢复时重新获取该消息所在页，而非只查其后 ordinal。历史引用只带编号、来源可用性等摘要，不带原文；失效显示 SOURCE_CHANGED/SOURCE_DELETED，回答 sourceWarning 提示可能过期；无授权时整个响应拒绝。

### 6.4 POST `/api/v1/conversations/{conversationId}/messages` — 提交问题并接收 SSE

**调用方向：** React → Spring Boot。  
**权限：** owner + 当前 KB 读权限 + 无同会话活动 run。

| 参数位置 | 契约 |
| --- | --- |
| Path | conversationId:UUID 必填。 |
| Query | 无 |
| Body | JSON：content:string 必填，trim 后1～2000个Unicode字符；clientRequestId:UUID 必填，前端每次明确的新命令生成一次，网络重投保持不变。禁止 kbId、userId、history、model、allowedKbIds。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
POST /api/v1/conversations/60000000-0000-4000-8000-000000000001/messages HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
X-CSRF-TOKEN: demo-csrf-token
Content-Type: application/json
Accept: text/event-stream

{
  "content": "报销最晚什么时候提交？",
  "clientRequestId": "90000000-0000-4000-8000-000000000001"
}
```

成功握手示例：`200 OK`、`Content-Type: text/event-stream; charset=utf-8`；下面仅展示第一事件，完整成功/失败流见第9节。

```text
id: 80000000-0000-4000-8000-000000000001:1
event: message_start
data: {"runId":"80000000-0000-4000-8000-000000000001","seq":1,"conversationId":"60000000-0000-4000-8000-000000000001","userMessageId":"70000000-0000-4000-8000-000000000001","assistantMessageId":"70000000-0000-4000-8000-000000000002","triggerKind":"INITIAL","status":"GENERATING","requestId":"req-demo-001"}

```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `CONVERSATION_FORBIDDEN` | 会话不属于当前用户 |
| 403 | `KNOWLEDGE_BASE_FORBIDDEN` | 没有知识库权限 |
| 404 | `CONVERSATION_NOT_FOUND` | 会话不存在 |
| 409 | `GENERATION_IN_PROGRESS` | 同会话已有生成 |
| 409 | `GENERATION_ALREADY_EXISTS` | 这条命令已创建生成，请读取历史或执行记录 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 相同clientRequestId被用于不同命令 |
| 409 | `KNOWLEDGE_BASE_NOT_ACTIVE` | 知识库暂停使用 |
| 422 | `VALIDATION_ERROR` | 问题或去重键不合法 |

错误响应示例：HTTP **403**。

```json
{
  "code": "CONVERSATION_FORBIDDEN",
  "message": "会话不属于当前用户",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：以一个事务保存消息对、run、currentRun 指针并占用会话，再发送 message_start；SSE 详见第9节。对同一 clientRequestId 的相同命令不建新run、不附着第二条流，返回409并用 Location 指向原run查询地址；权限检查在去重结果返回前执行。模型调用失败若已开始SSE，则发 message_error，不把HTTP状态改为500。最多最近10轮正常完成上下文由 Spring 从数据库筛选，前端不得自行提交。

### 6.5 POST `/api/v1/generation-runs/{runId}/stop` — 停止生成

**调用方向：** React → Spring Boot。  
**权限：** run 所属会话 owner；即使当前失去 KB 权限，也允许中止自己的执行，但不回传隐藏正文。

| 参数位置 | 契约 |
| --- | --- |
| Path | runId:UUID 必填。 |
| Query | 无 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
POST /api/v1/generation-runs/80000000-0000-4000-8000-000000000001/stop HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
X-CSRF-TOKEN: demo-csrf-token
Accept: application/json
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "runId": "80000000-0000-4000-8000-000000000001",
    "status": "STOPPED",
    "changed": true,
    "lastSeq": 4
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `CONVERSATION_FORBIDDEN` | 不能停止他人的生成 |
| 404 | `GENERATION_RUN_NOT_FOUND` | 执行记录不存在 |

错误响应示例：HTTP **403**。

```json
{
  "code": "CONVERSATION_FORBIDDEN",
  "message": "不能停止他人的生成",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：先在数据库置 STOPPED、阻断迟到事件并保留部分内容，再调用 FastAPI cancel，200不等待供应商确认。重复停止返回当前终态、changed=false；若完成先提交，返回COMPLETED且不能把已完成改成STOPPED。响应不包含回答正文。停止旧run只影响其自身，不改变同消息的新currentRun。

## 7. MVP 必需的配套接口

以下补齐PRD已有授权、替换、重试、引用与会话删除，以及Session安全初始化。它们属于现有MVP，不新增产品模块。所有接口同样给出完整契约。

### 7.1 GET `/api/v1/auth/csrf` — 获取 CSRF token

**调用方向：** React → Spring Boot。  
**权限：** 匿名或已登录。

| 参数位置 | 契约 |
| --- | --- |
| Path | 无 |
| Query | 无 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
GET /api/v1/auth/csrf HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
Accept: application/json
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "csrfToken": "demo-csrf-token",
    "headerName": "X-CSRF-TOKEN"
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 503 | `SERVICE_UNAVAILABLE` | Session存储暂不可用 |

错误响应示例：HTTP **503**。

```json
{
  "code": "SERVICE_UNAVAILABLE",
  "message": "Session存储暂不可用",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：为当前 Session 签发/读取 token；必要时 Set-Cookie 创建匿名 Session。登录成功后采用新token，退出后重新获取。此接口只服务 Spring Session CSRF，不是访问令牌。

### 7.2 GET `/api/v1/users` — 员工列表和授权摘要

**调用方向：** React → Spring Boot。  
**权限：** ADMIN。

| 参数位置 | 契约 |
| --- | --- |
| Path | 无 |
| Query | page/pageSize 同全局；固定只查询 EMPLOYEE，不支持任意角色过滤。 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
GET /api/v1/users HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
Accept: application/json
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "items": [
      {
        "id": "10000000-0000-4000-8000-000000000001",
        "username": "alice",
        "role": "EMPLOYEE",
        "createdAt": "2026-11-19T02:00:00Z",
        "knowledgeBases": [
          {
            "id": "20000000-0000-4000-8000-000000000001",
            "name": "HR Policy"
          }
        ]
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "hasNext": false
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `ADMIN_REQUIRED` | 仅管理员可查看员工列表 |
| 422 | `VALIDATION_ERROR` | 分页参数不合法 |

错误响应示例：HTTP **403**。

```json
{
  "code": "ADMIN_REQUIRED",
  "message": "仅管理员可查看员工列表",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：不返回密码或哈希；用于现有员工授权页，不增加停用、删除、角色修改接口。

### 7.3 PUT `/api/v1/knowledge-bases/{kbId}/members/{userId}` — 授予知识库访问权限

**调用方向：** React → Spring Boot。  
**权限：** ADMIN；目标为现存 EMPLOYEE，KB ACTIVE。

| 参数位置 | 契约 |
| --- | --- |
| Path | kbId:UUID、userId:UUID 均必填。 |
| Query | 无 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
PUT /api/v1/knowledge-bases/20000000-0000-4000-8000-000000000001/members/10000000-0000-4000-8000-000000000001 HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
X-CSRF-TOKEN: demo-csrf-token
Accept: application/json
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "kbId": "20000000-0000-4000-8000-000000000001",
    "userId": "10000000-0000-4000-8000-000000000001",
    "granted": true
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 404 | `USER_NOT_FOUND` | 员工不存在 |
| 404 | `KNOWLEDGE_BASE_NOT_FOUND` | 知识库不存在 |
| 409 | `KNOWLEDGE_BASE_NOT_ACTIVE` | 知识库不可授权 |
| 422 | `MEMBER_ROLE_INVALID` | 只能给普通员工授权 |

错误响应示例：HTTP **404**。

```json
{
  "code": "USER_NOT_FOUND",
  "message": "员工不存在",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：联合PK去重；已授权仍200，幂等。创建人/管理员角色不是由此接口赋予。

### 7.4 DELETE `/api/v1/knowledge-bases/{kbId}/members/{userId}` — 撤销知识库访问权限

**调用方向：** React → Spring Boot。  
**权限：** ADMIN；目标为现存 EMPLOYEE，KB ACTIVE。

| 参数位置 | 契约 |
| --- | --- |
| Path | kbId:UUID、userId:UUID 均必填。 |
| Query | 无 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
DELETE /api/v1/knowledge-bases/20000000-0000-4000-8000-000000000001/members/10000000-0000-4000-8000-000000000001 HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
X-CSRF-TOKEN: demo-csrf-token
Accept: application/json
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "kbId": "20000000-0000-4000-8000-000000000001",
    "userId": "10000000-0000-4000-8000-000000000001",
    "granted": false
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 404 | `USER_NOT_FOUND` | 员工不存在 |
| 404 | `KNOWLEDGE_BASE_NOT_FOUND` | 知识库不存在 |
| 409 | `KNOWLEDGE_BASE_NOT_ACTIVE` | 知识库已进入删除流程 |

错误响应示例：HTTP **404**。

```json
{
  "code": "USER_NOT_FOUND",
  "message": "员工不存在",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：删除成员行，事务中使该员工相关活动run失效并取消；已无成员行仍200。保留历史会话，但隐藏正文/原标题/引用。

### 7.5 POST `/api/v1/documents/{documentId}/replacements` — 替换原文件

**调用方向：** React → Spring Boot。  
**权限：** ADMIN；KB/document ACTIVE。

| 参数位置 | 契约 |
| --- | --- |
| Path | documentId:UUID 必填。 |
| Query | 无 |
| Body | multipart/form-data：file 必填；规则同首次上传。替换文件可与本记录同名，不可与同库其他未删除文档重名。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
POST /api/v1/documents/30000000-0000-4000-8000-000000000001/replacements HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
X-CSRF-TOKEN: demo-csrf-token
Accept: application/json
Content-Type: multipart/form-data; boundary=demo-boundary

--demo-boundary
Content-Disposition: form-data; name="file"; filename="报销制度.pdf"
Content-Type: application/pdf

[新版PDF字节]
--demo-boundary--
```

成功示例：HTTP **202**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "id": "30000000-0000-4000-8000-000000000001",
    "kbId": "20000000-0000-4000-8000-000000000001",
    "fileName": "报销制度.pdf",
    "mediaType": "application/pdf",
    "sizeBytes": 18432,
    "documentVersion": 2,
    "status": "PROCESSING",
    "lifecycle": "ACTIVE",
    "createdAt": "2026-11-19T02:00:00Z",
    "updatedAt": "2026-11-19T02:00:00Z",
    "failure": null,
    "processing": {
      "jobId": "40000000-0000-4000-8000-000000000002",
      "attemptId": "50000000-0000-4000-8000-000000000002",
      "attemptNo": 1,
      "state": "QUEUED",
      "deadlineAt": "2026-11-19T02:05:00Z"
    }
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 404 | `DOCUMENT_NOT_FOUND` | 文档不存在 |
| 409 | `DOCUMENT_NAME_CONFLICT` | 同库其他文档已用该文件名 |
| 409 | `KNOWLEDGE_BASE_NOT_ACTIVE` | 知识库不可用 |
| 413 | `FILE_TOO_LARGE` | 文件大小超限 |
| 415 | `UNSUPPORTED_FILE_TYPE` | 不支持该格式 |
| 422 | `EMPTY_FILE` | 文件为空 |

错误响应示例：HTTP **404**。

```json
{
  "code": "DOCUMENT_NOT_FOUND",
  "message": "文档不存在",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：校验和接收成功后才增加版本、建新job/attempt、清空发布指针、使旧引用失效；校验失败原状态不变。可替换正在处理的文档：先终止旧attempt，再建新版本。采用POST创建一次替换操作，避免把每次递增版本的动作误声明为幂等PUT；不提供替换历史/回滚查询。网络未知先查详情版本/状态，不自动重放。

### 7.6 POST `/api/v1/documents/{documentId}/retry` — 人工重试文档处理

**调用方向：** React → Spring Boot。  
**权限：** ADMIN；文档 ACTIVE/FAILED，KB ACTIVE。

| 参数位置 | 契约 |
| --- | --- |
| Path | documentId:UUID 必填。 |
| Query | 无 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
POST /api/v1/documents/30000000-0000-4000-8000-000000000001/retry HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
X-CSRF-TOKEN: demo-csrf-token
Accept: application/json
```

成功示例：HTTP **202**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "id": "30000000-0000-4000-8000-000000000001",
    "kbId": "20000000-0000-4000-8000-000000000001",
    "fileName": "报销制度.pdf",
    "mediaType": "application/pdf",
    "sizeBytes": 18432,
    "documentVersion": 1,
    "status": "PROCESSING",
    "lifecycle": "ACTIVE",
    "createdAt": "2026-11-19T02:00:00Z",
    "updatedAt": "2026-11-19T02:00:00Z",
    "failure": null,
    "processing": {
      "jobId": "40000000-0000-4000-8000-000000000001",
      "attemptId": "50000000-0000-4000-8000-000000000001",
      "attemptNo": 1,
      "state": "QUEUED",
      "deadlineAt": "2026-11-19T02:05:00Z"
    }
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 404 | `DOCUMENT_NOT_FOUND` | 文档不存在 |
| 409 | `DOCUMENT_NOT_FAILED` | 文档不是可重试失败状态 |
| 409 | `PROCESSING_IN_PROGRESS` | 已有有效处理尝试 |
| 409 | `KNOWLEDGE_BASE_NOT_ACTIVE` | 知识库不可用 |

错误响应示例：HTTP **404**。

```json
{
  "code": "DOCUMENT_NOT_FOUND",
  "message": "文档不存在",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：保留当前文件版本和jobId，生成新的attemptId/attemptNo；deadline按当前时刻重新计算300秒。同次重复点击在PROCESSING时返回409，不新建尝试。若响应未知先GET详情；不能无限自动POST，否则已再次失败时会变成新业务重试。

### 7.7 DELETE `/api/v1/conversations/{conversationId}` — 删除自己的会话

**调用方向：** React → Spring Boot。  
**权限：** owner；失去 KB 权限/库已删除仍允许。

| 参数位置 | 契约 |
| --- | --- |
| Path | conversationId:UUID 必填。 |
| Query | 无 |
| Body | JSON：confirm:boolean 必须 true。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
DELETE /api/v1/conversations/60000000-0000-4000-8000-000000000001 HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
X-CSRF-TOKEN: demo-csrf-token
Content-Type: application/json
Accept: application/json

{
  "confirm": true
}
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "conversationId": "60000000-0000-4000-8000-000000000001",
    "deleted": true
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `CONVERSATION_FORBIDDEN` | 不能删除他人会话 |
| 404 | `CONVERSATION_NOT_FOUND` | 会话不存在或已经删除 |
| 422 | `CONFIRMATION_REQUIRED` | 请确认删除 |

错误响应示例：HTTP **403**。

```json
{
  "code": "CONVERSATION_FORBIDDEN",
  "message": "不能删除他人会话",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：先使活动run失效，同事务清除会话/消息/run/citations，再尽力取消FastAPI。删除文档不受影响；不保留会话tombstone，因此重复删除可404，删除效果仍幂等。迟到回调只UPDATE既有行，禁止UPSERT复活。

### 7.8 GET `/api/v1/generation-runs/{runId}` — 读取执行状态

**调用方向：** React → Spring Boot。  
**权限：** owner + 当前 KB 读权限；不绕过历史隐藏规则。

| 参数位置 | 契约 |
| --- | --- |
| Path | runId:UUID 必填。 |
| Query | 无 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
GET /api/v1/generation-runs/80000000-0000-4000-8000-000000000001 HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
Accept: application/json
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "runId": "80000000-0000-4000-8000-000000000001",
    "conversationId": "60000000-0000-4000-8000-000000000001",
    "userMessageId": "70000000-0000-4000-8000-000000000001",
    "assistantMessageId": "70000000-0000-4000-8000-000000000002",
    "clientRequestId": "90000000-0000-4000-8000-000000000001",
    "status": "GENERATING",
    "isCurrent": true,
    "lastSeq": 1,
    "requestedAt": "2026-11-19T02:00:00Z",
    "firstTextAt": null,
    "finishedAt": null,
    "error": null
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `CONVERSATION_FORBIDDEN` | 不能访问他人的执行记录 |
| 403 | `KNOWLEDGE_BASE_FORBIDDEN` | 关联知识库不可访问 |
| 404 | `GENERATION_RUN_NOT_FOUND` | 执行记录不存在 |

错误响应示例：HTTP **403**。

```json
{
  "code": "CONVERSATION_FORBIDDEN",
  "message": "不能访问他人的执行记录",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：用于相同clientRequestId返回的Location、断线和停止竞态核对；只返回执行元数据，当前正文通过会话历史读取。isCurrent=false 的旧run不提供旧回答版本。

### 7.9 POST `/api/v1/generation-runs/{runId}/retry` — 重试最后一轮未完成回答

**调用方向：** React → Spring Boot。  
**权限：** owner + 当前KB读权限；目标为当前最后一轮 INCOMPLETE，无其他GENERATING。

| 参数位置 | 契约 |
| --- | --- |
| Path | runId:UUID 必填。 |
| Query | 无 |
| Body | JSON：clientRequestId:UUID 必填；不接受 content/kbId/history。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
POST /api/v1/generation-runs/80000000-0000-4000-8000-000000000001/retry HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
X-CSRF-TOKEN: demo-csrf-token
Content-Type: application/json
Accept: text/event-stream

{
  "clientRequestId": "90000000-0000-4000-8000-000000000001"
}
```

成功握手示例：`200 OK`、`Content-Type: text/event-stream; charset=utf-8`；下面仅展示第一事件，完整成功/失败流见第9节。

```text
id: 80000000-0000-4000-8000-000000000002:1
event: message_start
data: {"runId":"80000000-0000-4000-8000-000000000002","seq":1,"conversationId":"60000000-0000-4000-8000-000000000001","userMessageId":"70000000-0000-4000-8000-000000000001","assistantMessageId":"70000000-0000-4000-8000-000000000002","triggerKind":"RETRY","status":"GENERATING","requestId":"req-demo-001"}

```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 404 | `GENERATION_RUN_NOT_FOUND` | 执行不存在 |
| 403 | `KNOWLEDGE_BASE_FORBIDDEN` | 没有知识库权限 |
| 409 | `RETRY_NOT_ALLOWED` | 不是最后一轮当前未完成回答 |
| 409 | `GENERATION_IN_PROGRESS` | 仍有正在生成的回答 |
| 409 | `GENERATION_ALREADY_EXISTS` | 该重试命令已经创建执行 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 去重键被用于不同命令 |

错误响应示例：HTTP **404**。

```json
{
  "code": "GENERATION_RUN_NOT_FOUND",
  "message": "执行不存在",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：新建run、复用USER/ASSISTANT消息ID、清空当前正文与旧引用；采用当前资料重新检索，不拼接旧片段。SSE同提交问题，message_start中triggerKind=RETRY。不能对STOPPED使用此接口；停止后修改最近问题走编辑接口。

### 7.10 PATCH `/api/v1/conversations/{conversationId}/latest-question` — 编辑最近被停止的问题并重发

**调用方向：** React → Spring Boot。  
**权限：** owner + 当前KB读权限；仅最后一轮 currentRun STOPPED，且无活动生成。

| 参数位置 | 契约 |
| --- | --- |
| Path | conversationId:UUID 必填。 |
| Query | 无 |
| Body | JSON：content:string 必填，trim后1～2000字符；clientRequestId:UUID 必填。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
PATCH /api/v1/conversations/60000000-0000-4000-8000-000000000001/latest-question HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
X-CSRF-TOKEN: demo-csrf-token
Content-Type: application/json
Accept: text/event-stream

{
  "content": "出差报销需要什么材料？",
  "clientRequestId": "90000000-0000-4000-8000-000000000001"
}
```

成功握手示例：`200 OK`、`Content-Type: text/event-stream; charset=utf-8`；下面仅展示第一事件，完整成功/失败流见第9节。

```text
id: 80000000-0000-4000-8000-000000000002:1
event: message_start
data: {"runId":"80000000-0000-4000-8000-000000000002","seq":1,"conversationId":"60000000-0000-4000-8000-000000000001","userMessageId":"70000000-0000-4000-8000-000000000001","assistantMessageId":"70000000-0000-4000-8000-000000000002","triggerKind":"EDIT","status":"GENERATING","requestId":"req-demo-001"}

```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 404 | `CONVERSATION_NOT_FOUND` | 会话不存在 |
| 403 | `KNOWLEDGE_BASE_FORBIDDEN` | 没有知识库权限 |
| 409 | `EDIT_NOT_ALLOWED` | 不是可编辑的最近已停止问题 |
| 409 | `GENERATION_IN_PROGRESS` | 仍有活动生成 |
| 409 | `GENERATION_ALREADY_EXISTS` | 该编辑命令已经创建执行 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 去重键被用于不同命令 |
| 422 | `VALIDATION_ERROR` | 问题不合法 |

错误响应示例：HTTP **404**。

```json
{
  "code": "CONVERSATION_NOT_FOUND",
  "message": "会话不存在",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：同一事务替换最近USER内容、清空对应ASSISTANT、删除旧引用并建新run；首问被改时同步标题。ordinal/消息ID不变，无对话分支。SSE triggerKind=EDIT；不要把PATCH的网络重投变成第二次编辑执行。

### 7.11 GET `/api/v1/citations/{citationId}` — 查看引用原文

**调用方向：** React → Spring Boot。  
**权限：** 关联会话owner + 当前KB读权限 + 来源仍有效。

| 参数位置 | 契约 |
| --- | --- |
| Path | citationId:UUID 必填。 |
| Query | 无 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
GET /api/v1/citations/a0000000-0000-4000-8000-000000000001 HTTP/1.1
Host: 127.0.0.1:8080
Cookie: SESSION=demo-session
Accept: application/json
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "id": "a0000000-0000-4000-8000-000000000001",
    "number": 1,
    "documentId": "30000000-0000-4000-8000-000000000001",
    "documentVersion": 1,
    "fileName": "报销制度.pdf",
    "pageStart": 3,
    "pageEnd": 3,
    "availability": "AVAILABLE",
    "quote": "出差报销应在出差结束后十个工作日内提交。",
    "downloadUrl": "/api/v1/documents/30000000-0000-4000-8000-000000000001/download?documentVersion=1"
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `CONVERSATION_FORBIDDEN` | 不能访问他人的引用 |
| 403 | `KNOWLEDGE_BASE_FORBIDDEN` | 没有知识库权限 |
| 404 | `CITATION_NOT_FOUND` | 引用不存在或已随重试清除 |
| 409 | `SOURCE_CHANGED` | 文档已更新，原引用失效 |
| 410 | `SOURCE_DELETED` | 来源文档已删除 |
| 409 | `KNOWLEDGE_BASE_NOT_ACTIVE` | 知识库暂停使用 |
| 500 | `SOURCE_UNAVAILABLE` | 当前来源切片缺失，无法读取 |

错误响应示例：HTTP **403**。

```json
{
  "code": "CONVERSATION_FORBIDDEN",
  "message": "不能访问他人的引用",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：quote 来自真实切片，不从模型或引用快照读。PDF用实际页码；TXT/DOCX/Markdown页码为null。URL是受鉴权的Spring相对地址，带版本防止下载新版冒充原依据。权限失败先返回403，不泄露旧原文/文件名。

## 8. 服务间内部接口

**实现方向必须按每项标注。** files/result由Spring提供，其余由FastAPI提供。所有服务端baseUrl和回调地址固定配置；不接受浏览器输入的内部目的地址。统一JSON envelope同第2节，内部SSE同第9节。

### 8.1 服务身份与请求范围：两层证明

`X-Service-Key`证明“哪个受信服务在调用”，`X-Execution-Scope`证明“这个调用被Spring限定在哪个业务对象和动作”。前者不等于员工授权，后者也不能取代Spring当前权限检查。

| 项目 | MVP约定 |
| --- | --- |
| 服务密钥 | 两个不同的随机后端环境变量：Spring→Fast使用AI_SERVICE_KEY；Fast→Spring使用CORE_SERVICE_KEY；接收方常量时间比较；不放前端、库表、URL或示例仓库 |
| 范围签名 | Spring使用独立EXECUTION_SCOPE_SECRET生成短期JWS（JWT，HS256固定白名单，不接受none/自选alg）；Fast持有验签所需密钥，属于受信内部边界；不宣称防已攻陷Fast服务 |
| 通用claims | iss=core-service、aud=ai-service或core-internal、action、iat/exp整数秒、jti随机UUID；无浏览器输入的角色、密钥或文件路径 |
| processing.execute | 绑定jobId/attemptId/documentId/documentVersion/kbId/embeddingModelId/deadlineAt；exp不超过本attempt截止点 |
| processing.report | Spring同时签发aud=core-internal的报告scope，绑定同一任务；Fast只持有此次报告票据，不读取或修改core业务表 |
| answer/retrieval | 绑定userId/kbId/conversationId/runId、deadlineAt和配置标识；exp不超过run总deadline。这里只允许一个kbId，没有任意allowedKbIds列表 |
| fileTicket | 单独Spring签发的aud=core-internal、action=file.read票据，含同一job/attempt/document/version/kb及exp；只含标识，不含storageKey；URL安全compact JWS，使用同样固定算法 |
| cancel / indexes.delete | 由Spring在业务失效事务提交后签发相应精确作用域；回收请求的exp为签发后60秒，与已经到期的执行scope分开，避免超时后无法取消。清理scope另带cleanupFence=SOURCE_INVALIDATED与版本/attempt范围 |

通用身份失败返回401 SERVICE_AUTHENTICATION_REQUIRED或SERVICE_AUTHENTICATION_FAILED；签名错误、aud/action/对象不匹配返回403 SCOPE_INVALID；已过exp返回410 SCOPE_EXPIRED。仅本地时间校验允许最多5秒时钟偏差，**业务deadline不因此延长**。接收端同时匹配Path/Body与claims，禁止“验签成功后仍用未验证Body里的kbId”。不得把外部转发头当作内部身份。

任务Body中的 `resultScope` 是必填的Spring签发processing.report票据，仅允许回调当前job/attempt；不是callbackUrl，也不允许Fast指定回调地址。完整提交请求见8.3。

Fast在提交时校验fileTicket中的标识与processing.execute一致；读取文件时将该票据作为Path值，带CORE_SERVICE_KEY；回调时用resultScope作X-Execution-Scope。Spring的文件/结果路径只在内部服务安全过滤链上启用，不允许Session或普通Bearer Token替代服务密钥。网络loopback、无公开CORS和服务认证共同限制浏览器；用户仍能自行构造HTTP，所以服务器永远不能仅凭“请求来自前端”判断授权。

检索时Fast取得verifiedScope.kbId，在 `ai.retrievable_chunks` 内先限定这个库、模型与实际查询向量维度，再排序取Top-K。View只筛选已发布当前版本/attempt，不识别用户。签名作用域签发后撤权仍可能发生，因此Spring在转发、终态提交、历史/引用/下载时用当前数据重新鉴权；Fast的计算结果不能直接对外输出。

服务密钥校验错误不返回期望的密钥。日志只留requestId/jobId/attemptId/runId和净化错误码；fileTicket、scope、Cookie、密码、正文均脱敏。内部文件读取只到配置中的Spring地址；LLM/Embedding供应商地址和key由Fast Model Gateway配置。

### 8.2 处理任务的许可、失败报告与幂等

Day4已有持久化 `processing_attempts.status`，无需新增幂等表。`STARTED`是本稿在同一个result接口内补充的执行许可报告，用于把Fast的“接纳”落到Spring数据库，解决Fast重启丢失内存去重记录后重复执行的问题；它不把状态写权限交给Fast。

顺序必须如下：

1. Spring在事务中建立PROCESSING文档/版本/job/attempt，保存实际deadline；调度前将QUEUED标DISPATCHED并更新dispatch_count，重投不改deadline。
2. Fast验证静态身份、签名和固定载荷，对同job/attempt在本进程互斥；向Spring发送STARTED。登记与许可须在入队/模型调用之前完成，不允许先处理再登记。
3. Spring按Day4锁序核验当前KB、文档版本、有效attempt、deadline，原子地把QUEUED或DISPATCHED改RUNNING。首次明确返回claimAccepted=true，Fast才可入本地队列并处理。RUNNING的重复STARTED返回claimAccepted=false，不能作为第二份执行许可。
4. 解析/Embedding/切片事务后报告SUCCEEDED；失败报告FAILED。Spring接纳成功时同事务置attempt成功和文档发布；Fast的202/计算成功不决定发布。
5. STARTED许可响应丢失时，Fast**不猜测自己已获许可并继续执行**；停止该尝试，在可确认情况下报告失败，否则由原deadline结束。宁可要求人工新attempt，也不能为同attempt自动重新计算。

STARTED请求/响应：

```json
{"reportType":"STARTED","attemptId":"50000000-0000-4000-8000-000000000001","documentId":"30000000-0000-4000-8000-000000000001","documentVersion":1}
```

```json
{"code":"SUCCESS","message":"Request successful","data":{"jobId":"40000000-0000-4000-8000-000000000001","attemptId":"50000000-0000-4000-8000-000000000001","claimAccepted":true,"attemptState":"RUNNING"},"requestId":"req-demo-001"}
```

失败报告示例（仅发送净化原因，不发送异常栈或模型供应商原始JSON）：

```json
{"reportType":"FAILED","attemptId":"50000000-0000-4000-8000-000000000001","documentId":"30000000-0000-4000-8000-000000000001","documentVersion":1,"error":{"code":"NO_TEXT","message":"未能提取有效文字"}}
```

```json
{"code":"SUCCESS","message":"Request successful","data":{"jobId":"40000000-0000-4000-8000-000000000001","attemptId":"50000000-0000-4000-8000-000000000001","accepted":true,"deduplicated":false,"attemptState":"FAILED","documentStatus":"FAILED"},"requestId":"req-demo-001"}
```

FAILED.code允许UNSUPPORTED_CONTENT、NO_TEXT、PARSER_FAILED、EMBEDDING_SERVICE_ERROR、MODEL_CONFIG_MISMATCH、MANIFEST_MISMATCH、PROCESSOR_UNAVAILABLE。超时/取消/重启终态由Spring决定，Fast可报告对应失败现象但不能把已经TIMED_OUT/CANCELLED/INTERRUPTED改成FAILED或SUCCEEDED。错误展示文案由Spring按code映射，message只是净化补充。成功候选回调被422 MANIFEST_MISMATCH拒绝时不发布，Fast停止该次执行并以FAILED/MANIFEST_MISMATCH报告；不能修改已经提交的切片再伪装成同一次成功，回调持续失败则由原deadline兜底。

相同job/attempt固定载荷只允许接纳一次。固定载荷指documentId/kbId/documentVersion/embeddingModelId/deadlineAt；fileTicket/resultScope的重新签名不会创造新attempt，不能用于改变这些业务值。进程中正在执行或保留已有结果时，重复提交只返回去重状态；已提交切片的同一次成功结果可重复回调，不重新生成Embedding。

若Spring调用超时而Fast已完成：Spring不能立即创建新attempt；在当前attempt/deadline内重投**同job/attempt**，或等待同一结果回调。Fast返回现存状态/结果，Spring以数据库条件接纳一次。相同终态回调在仍有效的服务scope内返回200 deduplicated=true；不同manifest/失败原因冲突409 RESULT_CONFLICT；过期scope返回410且不触发重算。已接纳成功的重复失败不能撤销发布。

若Fast重启后registry不存在：RUNNING不能重新取得STARTED许可，返回409 ATTEMPT_ALREADY_CLAIMED；Spring不把它当作新计算许可，保留旧状态直到确认为中断或原deadline失效，管理员再创建新attempt。QUEUED/DISPATCHED若尚未取得许可，可以在同有效尝试上首次接纳；这不重新运行已开始的工作。Spring自身重启按PRD终止旧未完成尝试，不重新派发。这个MVP保证业务结果只接纳一次，**不承诺所有外部计算恰好执行一次或每次响应丢失都能无损继续**。

document_version只在替换成功增加；attemptId在人工重试增加；重复HTTP投递三者均不变。SUCCEEDED的清单哈希严格采用Day4的JCS/float32规范，不能用回调自行声明的chunkCount直接发布。删除/替换/超时先使attempt失效；写切片触发器拒绝失效来源，cancel与清索引可以幂等重试但不能复活任务。


### 8.3 POST `/internal/v1/processing-jobs` — 提交文档处理任务

**调用方向：** Spring Boot → FastAPI。  
**权限：** Spring服务密钥 + processing.execute签名作用域。

| 参数位置 | 契约 |
| --- | --- |
| Path | 无 |
| Query | 无 |
| Body | JSON：jobId/documentId/kbId/attemptId:UUID 必填；documentVersion:integer>=1；deadlineAt:ISO时间；fileTicket/resultScope/embeddingModelId:string 均必填；各字段必须与作用域/持久化attempt一致。无自定义callbackUrl、filePath或任意URL。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
POST /internal/v1/processing-jobs HTTP/1.1
Host: 127.0.0.1:8000
X-Service-Key: demo-spring-service-key
X-Execution-Scope: demo-signed-scope
X-Request-Id: req-demo-001
Content-Type: application/json
Accept: application/json

{
  "jobId": "40000000-0000-4000-8000-000000000001",
  "attemptId": "50000000-0000-4000-8000-000000000001",
  "documentId": "30000000-0000-4000-8000-000000000001",
  "kbId": "20000000-0000-4000-8000-000000000001",
  "documentVersion": 1,
  "deadlineAt": "2026-11-19T02:05:00Z",
  "fileTicket": "demo-signed-file-ticket",
  "resultScope": "demo-signed-result-scope",
  "embeddingModelId": "embedding-config-v1"
}
```

成功示例：HTTP **202**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "jobId": "40000000-0000-4000-8000-000000000001",
    "attemptId": "50000000-0000-4000-8000-000000000001",
    "state": "RUNNING",
    "deduplicated": false
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 409 | `STALE_ATTEMPT` | 尝试已失效或版本不匹配 |
| 409 | `ATTEMPT_ALREADY_CLAIMED` | 尝试已被接纳，不能重新执行 |
| 409 | `IDEMPOTENCY_CONFLICT` | 相同job/attempt载荷不一致 |
| 410 | `SCOPE_EXPIRED` | 任务作用域已到期 |
| 422 | `MODEL_CONFIG_MISMATCH` | 模型配置与任务不一致 |
| 503 | `PROCESSOR_UNAVAILABLE` | 执行器不可用 |

错误响应示例：HTTP **409**。

```json
{
  "code": "STALE_ATTEMPT",
  "message": "尝试已失效或版本不匹配",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：Fast先验证作用域，在本进程对job/attempt互斥登记，再以result的STARTED报告原子取得Spring执行许可，最后入队并返回202。只有首次claimAccepted=true才能处理；重复命令有现存registry则返回202 deduplicated=true，已完成则200 state=SUCCEEDED并不重算。registry丢失且core已RUNNING时409、不重新解析；不把内存表称为持久队列。身份/幂等与STARTED细节见8.2。

### 8.4 GET `/internal/v1/files/{fileTicket}` — 读取任务授权文件流

**调用方向：** FastAPI → Spring Boot。  
**权限：** FastAPI服务密钥 + Spring签发fileTicket；不接受浏览器Session。

| 参数位置 | 契约 |
| --- | --- |
| Path | fileTicket:URL安全的签名票据；绑定job/attempt/document/version/kb/deadline。 |
| Query | 无 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
GET /internal/v1/files/demo-signed-file-ticket HTTP/1.1
Host: 127.0.0.1:8080
X-Service-Key: demo-fastapi-service-key
X-Request-Id: req-demo-001
Accept: application/octet-stream
```

成功示例：`200 OK`。

```http
HTTP/1.1 200 OK
Content-Type: application/pdf
Content-Length: 18432
Cache-Control: no-store
X-Request-Id: req-demo-001

[真实文件字节，不是JSON]
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `FILE_TICKET_INVALID` | 票据不合法或用途不符 |
| 409 | `STALE_ATTEMPT` | 任务/版本已失效 |
| 410 | `FILE_TICKET_EXPIRED` | 文件票据到期 |
| 410 | `DOCUMENT_DELETED` | 来源已删除 |
| 500 | `FILE_UNAVAILABLE` | 原文件不可读取 |

错误响应示例：HTTP **403**。

```json
{
  "code": "FILE_TICKET_INVALID",
  "message": "票据不合法或用途不符",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：这是 FastAPI → Spring，不能按URL前缀误认为FastAPI实现。Spring逐次核验当前attempt/版本，允许同有效尝试重复读取；GET无执行claim副作用。返回原文件二进制，no-store；配置的Spring内部baseUrl固定，不允许任务提供任意host。反向代理访问日志对票据脱敏。

### 8.5 POST `/internal/v1/processing-jobs/{jobId}/result` — 接纳执行许可/成功/失败报告

**调用方向：** FastAPI → Spring Boot。  
**权限：** FastAPI服务密钥 + 任务签名作用域；Spring仍以数据库状态决定是否接纳。

| 参数位置 | 契约 |
| --- | --- |
| Path | jobId:UUID 必填，并与Body/作用域任务匹配。 |
| Query | 无 |
| Body | JSON tagged union：共有attemptId/documentId:UUID，documentVersion:integer>=1，reportType:STARTED / SUCCEEDED / FAILED。STARTED无额外结果字段；SUCCEEDED必有chunkCount>0、manifestSha256(64位小写hex)、embeddingModelId、embeddingDimensions>0；FAILED必有error:{code,message}且无manifest字段。未知混合字段拒绝。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
POST /internal/v1/processing-jobs/40000000-0000-4000-8000-000000000001/result HTTP/1.1
Host: 127.0.0.1:8080
X-Service-Key: demo-fastapi-service-key
X-Execution-Scope: demo-signed-scope
X-Request-Id: req-demo-001
Content-Type: application/json
Accept: application/json

{
  "reportType": "SUCCEEDED",
  "attemptId": "50000000-0000-4000-8000-000000000001",
  "documentId": "30000000-0000-4000-8000-000000000001",
  "documentVersion": 1,
  "chunkCount": 24,
  "manifestSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "embeddingModelId": "embedding-config-v1",
  "embeddingDimensions": 1024
}
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "jobId": "40000000-0000-4000-8000-000000000001",
    "attemptId": "50000000-0000-4000-8000-000000000001",
    "accepted": true,
    "deduplicated": false,
    "attemptState": "SUCCEEDED",
    "documentStatus": "PUBLISHED"
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 404 | `PROCESSING_JOB_NOT_FOUND` | 逻辑任务不存在 |
| 409 | `STALE_ATTEMPT` | 尝试已超时、取消、替换或删除 |
| 409 | `RESULT_CONFLICT` | 同尝试回调内容不一致 |
| 422 | `MANIFEST_MISMATCH` | 切片数量/摘要/模型维度校验失败 |
| 410 | `SCOPE_EXPIRED` | 任务作用域已过期 |

错误响应示例：HTTP **404**。

```json
{
  "code": "PROCESSING_JOB_NOT_FOUND",
  "message": "逻辑任务不存在",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：STARTED使用条件更新把QUEUED/DISPATCHED→RUNNING并写started_at，首次返回claimAccepted=true；RUNNING重复STARTED返回claimAccepted=false；只有首次明确许可者可执行。SUCCEEDED只能在向量事务提交后报告；Spring锁定KB/document/attempt，按Day4规范重算manifest，未过deadline才同事务记录成功和发布。FAILED示例及回调重投规则见8.2。该接口不允许Fast直接改业务表。

### 8.6 POST `/internal/v1/processing-jobs/{jobId}/cancel` — 尽力取消处理尝试

**调用方向：** Spring Boot → FastAPI。  
**权限：** Spring服务密钥 + processing.cancel作用域。

| 参数位置 | 契约 |
| --- | --- |
| Path | jobId:UUID 必填。 |
| Query | 无 |
| Body | JSON：attemptId:UUID 必填；reason:USER_DELETE / REPLACED / TIMEOUT / INTERRUPTED 必填。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
POST /internal/v1/processing-jobs/40000000-0000-4000-8000-000000000001/cancel HTTP/1.1
Host: 127.0.0.1:8000
X-Service-Key: demo-spring-service-key
X-Execution-Scope: demo-signed-scope
X-Request-Id: req-demo-001
Content-Type: application/json
Accept: application/json

{
  "attemptId": "50000000-0000-4000-8000-000000000001",
  "reason": "TIMEOUT"
}
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "jobId": "40000000-0000-4000-8000-000000000001",
    "attemptId": "50000000-0000-4000-8000-000000000001",
    "cancelRequested": true,
    "workerState": "CANCELLING"
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `SCOPE_INVALID` | 取消作用域不匹配 |
| 422 | `VALIDATION_ERROR` | 取消参数不合法 |

错误响应示例：HTTP **403**。

```json
{
  "code": "SCOPE_INVALID",
  "message": "取消作用域不匹配",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：Spring先使core尝试终态，再发送取消，不因取消HTTP失败释放/恢复业务状态。Fast取消队列/解析进程/Embedding请求；已完成、未知或已取消都200，可返回workerState=FINISHED/NOT_FOUND/CANCELLED。200表示资源回收请求已处理，不证明供应商已停止。取消范围只匹配指定attempt，不波及新尝试。

### 8.7 DELETE `/internal/v1/documents/{documentId}/indexes` — 删除文档切片及向量

**调用方向：** Spring Boot → FastAPI。  
**权限：** Spring服务密钥 + indexes.delete作用域，明确document及可删除范围。

| 参数位置 | 契约 |
| --- | --- |
| Path | documentId:UUID 必填。 |
| Query | documentVersion:integer>=1 可选；attemptId:UUID 可选且必须同时有documentVersion。都省略表示此document的全部版本/尝试。必须与签名scope允许的范围相同。 |
| Body | 无；不得发送请求体 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
DELETE /internal/v1/documents/30000000-0000-4000-8000-000000000001/indexes HTTP/1.1
Host: 127.0.0.1:8000
X-Service-Key: demo-spring-service-key
X-Execution-Scope: demo-signed-scope
X-Request-Id: req-demo-001
Accept: application/json
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "documentId": "30000000-0000-4000-8000-000000000001",
    "deletedChunks": 24
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `SCOPE_INVALID` | 不允许扩大清理范围或未签发来源失效栅栏 |
| 503 | `AI_DATABASE_UNAVAILABLE` | 索引清理未完成 |

错误响应示例：HTTP **403**。

```json
{
  "code": "SCOPE_INVALID",
  "message": "不允许扩大清理范围或未签发来源失效栅栏",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：一次ai事务删除范围内切片；已不存在也200 deletedChunks=0。Spring事先阻断/失效来源，Day4写入触发器阻止旧任务在清理后写回。无条件删除全部向量是不合法调用。返回成功只代表ai数据清理完成，Spring仍需清文件及元数据。

### 8.8 POST `/internal/v1/retrieval` — 执行单库受限检索

**调用方向：** Spring Boot → FastAPI。  
**权限：** Spring服务密钥 + retrieval或answer作用域；使用verified kbId。

| 参数位置 | 契约 |
| --- | --- |
| Path | 无 |
| Query | 无 |
| Body | JSON：runId/kbId:UUID，query:string trim后1～2000字符，embeddingModelId:string，topK:integer默认5、1～10。字段需与作用域一致；只有已存在GENERATING run可委托。FastAPI按配置产生查询向量，不接收外部向量或任意SQL。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
POST /internal/v1/retrieval HTTP/1.1
Host: 127.0.0.1:8000
X-Service-Key: demo-spring-service-key
X-Execution-Scope: demo-signed-scope
X-Request-Id: req-demo-001
Content-Type: application/json
Accept: application/json

{
  "runId": "80000000-0000-4000-8000-000000000001",
  "kbId": "20000000-0000-4000-8000-000000000001",
  "query": "报销提交期限",
  "embeddingModelId": "embedding-config-v1",
  "topK": 5
}
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "runId": "80000000-0000-4000-8000-000000000001",
    "kbId": "20000000-0000-4000-8000-000000000001",
    "embeddingModelId": "embedding-config-v1",
    "evidence": [
      {
        "evidenceId": "e1",
        "documentId": "30000000-0000-4000-8000-000000000001",
        "documentVersion": 1,
        "attemptId": "50000000-0000-4000-8000-000000000001",
        "chunkId": "b0000000-0000-4000-8000-000000000001",
        "fileName": "报销制度.pdf",
        "pageStart": 3,
        "pageEnd": 3,
        "locator": {},
        "content": "出差报销应在出差结束后十个工作日内提交。",
        "distance": 0.18
      }
    ]
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `SCOPE_INVALID` | 请求知识库与作用域不匹配 |
| 410 | `SCOPE_EXPIRED` | 检索作用域过期 |
| 422 | `MODEL_CONFIG_MISMATCH` | 模型/维度不匹配 |
| 502 | `EMBEDDING_SERVICE_ERROR` | Embedding调用失败 |
| 504 | `UPSTREAM_TIMEOUT` | 检索超过本run剩余时限 |

错误响应示例：HTTP **403**。

```json
{
  "code": "SCOPE_INVALID",
  "message": "请求知识库与作用域不匹配",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：先在View按verified kbId、模型、维度限定，再计算距离；distance采用Day4余弦距离约定，不是置信度。无命中200 evidence=[]。query可由已鉴权上下文补全，证据清单不是最终引用。正常answer-stream在Fast内直接调用相同Retrieval函数，不再HTTP调自己；保留本接口用于Spring联调/单独调用，不会在普通问答中重复检索两次。topK是初始可调技术值，不更改固定20组质量验收。

### 8.9 POST `/internal/v1/answer-stream` — 单次回答计算与内部 SSE

**调用方向：** Spring Boot → FastAPI。  
**权限：** Spring服务密钥 + answer作用域。

| 参数位置 | 契约 |
| --- | --- |
| Path | 无 |
| Query | 无 |
| Body | JSON：runId/conversationId/userMessageId/assistantMessageId/kbId:UUID；question:string1～2000字符；history:数组0～10个{question,answer}正常完成轮次，按时间升序；requestedAt/firstTextDeadlineAt/deadlineAt:时间；embeddingModelId/llmModelId:string。均由Spring组装，禁止从浏览器直接透传。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
POST /internal/v1/answer-stream HTTP/1.1
Host: 127.0.0.1:8000
X-Service-Key: demo-spring-service-key
X-Execution-Scope: demo-signed-scope
X-Request-Id: req-demo-001
Content-Type: application/json
Accept: text/event-stream

{
  "runId": "80000000-0000-4000-8000-000000000001",
  "conversationId": "60000000-0000-4000-8000-000000000001",
  "userMessageId": "70000000-0000-4000-8000-000000000001",
  "assistantMessageId": "70000000-0000-4000-8000-000000000002",
  "kbId": "20000000-0000-4000-8000-000000000001",
  "question": "报销最晚什么时候提交？",
  "history": [],
  "requestedAt": "2026-11-19T02:00:00Z",
  "firstTextDeadlineAt": "2026-11-19T02:00:30Z",
  "deadlineAt": "2026-11-19T02:02:00Z",
  "embeddingModelId": "embedding-config-v1",
  "llmModelId": "answer-config-v1"
}
```

成功握手示例：`200 OK`、`Content-Type: text/event-stream; charset=utf-8`；下面仅展示第一事件，完整成功/失败流见第9节。

```text
id: 80000000-0000-4000-8000-000000000001:1
event: message_start
data: {"runId":"80000000-0000-4000-8000-000000000001","seq":1,"conversationId":"60000000-0000-4000-8000-000000000001","userMessageId":"70000000-0000-4000-8000-000000000001","assistantMessageId":"70000000-0000-4000-8000-000000000002","triggerKind":"INITIAL","status":"GENERATING","requestId":"req-demo-001"}

```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `SCOPE_INVALID` | 回答作用域与请求不一致 |
| 409 | `RUN_ALREADY_ACCEPTED` | 本进程已接纳该run，禁止第二次计算或附着第二条流 |
| 410 | `SCOPE_EXPIRED` | 本轮已超过期限 |
| 422 | `CONTEXT_TOO_LARGE` | 模型容量不足，请缩短内容或新建会话 |
| 503 | `AI_SERVICE_UNAVAILABLE` | AI执行器不可用 |

错误响应示例：HTTP **403**。

```json
{
  "code": "SCOPE_INVALID",
  "message": "回答作用域与请求不一致",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：在调用模型前按runId互斥登记；内部evidence事件必须在首段token之前。所有计算在已有120秒总deadline内，不从收到Fast请求再算120秒。POST超时/流中断不自动重发此接口；Spring置INCOMPLETE，显式新命令新run才能再计算。Fast进程内重复run返回409；跨重启由Spring绝不重投旧run保证，不把内存registry宣传为持久幂等。

### 8.10 POST `/internal/v1/generation-runs/{runId}/cancel` — 尽力取消回答计算（必要配套）

**调用方向：** Spring Boot → FastAPI。  
**权限：** Spring服务密钥 + answer.cancel作用域。

| 参数位置 | 契约 |
| --- | --- |
| Path | runId:UUID 必填。 |
| Query | 无 |
| Body | JSON：reason:USER_STOP / CLIENT_DISCONNECTED / TIMEOUT / ACCESS_REVOKED / SOURCE_CHANGED / CONVERSATION_DELETED / SESSION_ENDED / INTERRUPTED 必填。 |

请求示例（HTTP字段示意；multipart分隔和内容由FormData生成，不手写Content-Length）：

```http
POST /internal/v1/generation-runs/80000000-0000-4000-8000-000000000001/cancel HTTP/1.1
Host: 127.0.0.1:8000
X-Service-Key: demo-spring-service-key
X-Execution-Scope: demo-signed-scope
X-Request-Id: req-demo-001
Content-Type: application/json
Accept: application/json

{
  "reason": "USER_STOP"
}
```

成功示例：HTTP **200**。

```json
{
  "code": "SUCCESS",
  "message": "Request successful",
  "data": {
    "runId": "80000000-0000-4000-8000-000000000001",
    "cancelRequested": true,
    "workerState": "CANCELLING"
  },
  "requestId": "req-demo-001"
}
```

可能错误（另包含第2节通用错误）：

| HTTP | code | 含义 |
| --- | --- | --- |
| 403 | `SCOPE_INVALID` | 取消范围不匹配 |
| 422 | `VALIDATION_ERROR` | 取消原因不合法 |

错误响应示例：HTTP **403**。

```json
{
  "code": "SCOPE_INVALID",
  "message": "取消范围不匹配",
  "data": null,
  "requestId": "req-demo-002"
}
```

行为与边界：只释放指定run上游连接；已结束/不在registry也200，不得重建run。Spring先在core记录终态，因此cancel HTTP超时也不能导致旧事件覆盖新回答。


## 9. SSE 协议：两段连接、一个业务执行

### 9.1 连接与解析

```text
React --POST /api/v1/.../messages--> Spring Boot
Spring --POST /internal/v1/answer-stream--> FastAPI
FastAPI --内部SSE--> Spring --校验/持久化/重新封装SSE--> React
```

React使用 `fetch`、`credentials:'include'`、`Accept:text/event-stream`、CSRF头和JSON Body。原生EventSource不能发送本稿的POST JSON，不能直接使用它。成功流响应：

```http
HTTP/1.1 200 OK
Content-Type: text/event-stream; charset=utf-8
Cache-Control: no-store
X-Accel-Buffering: no
X-Request-Id: req-demo-001
```

每个事件是UTF-8文本，字段行用LF，空行结束：`id: <runId>:<seq>`、`event: <name>`、`data: <单行JSON>`。JSON里的换行需转义，不能破坏帧边界。服务端及时flush，关闭会缓存整段结果的代理/压缩配置；不手工设置HTTP/2不支持的Connection头。可每10秒发SSE注释 `: ping`，注释无runId/seq、不写业务状态，不算正文或延长任何deadline。

React必须用流式TextDecoder处理跨网络块的UTF-8字符，按完整SSE帧解析；一个网络chunk不是一个token或一条事件。第1次读到非200时按Content-Type解析JSON错误，不用SSE解析器读错误页。没有收到终态就EOF，视为连接中断，不能因HTTP200或EOF显示完成。

### 9.2 事件名称与字段

所有data均必含runId(UUID)、seq(正整数)、requestId(string)。message_start的身份字段必须等于Spring已保存的消息/run；`content`是任意长度的正文增量，不承诺模型token边界。事件里不再包一层SUCCESS envelope。

| 事件 | FastAPI → Spring | Spring → React |
| --- | --- | --- |
| message_start | runId、conversationId、userMessageId、assistantMessageId、status=GENERATING；证明接受本次计算，不另建run | 同身份字段，加triggerKind=INITIAL/RETRY/EDIT；Spring在事务建立run后立即发送一次，用于绑定当前UI |
| evidence | **在首个token前恰好一次**。items为本次全部候选证据的evidenceId/documentId/documentVersion/attemptId/chunkId/fileName/pageStart/pageEnd/locator；可为空。不得夹带模型生成原文 | **完成阶段至多一次**。citations为已经校验并持久化的最终CitationSummary，不是直接透传候选清单；在message_complete之前发送 |
| token | content:string，已按真实证据标记转换为可识别引用编号的正文增量；不能含任意模型指令帧 | content:string，同一run累计到当前ASSISTANT正文；仅转发已保存部分 |
| message_complete | status=COMPLETED、citations:[{number,evidenceId}]候选最终引用；只表示Fast计算正常结束 | status=COMPLETED、assistantMessageId、lastSeq、finishedAt；仅Spring正文+最终引用+终态事务提交后发送 |
| message_error | code、message；检索/模型/协议异常，不能当资料不足 | status=INCOMPLETE、code、message、retryable:boolean；正文保留已保存部分，权限变化则清除当前UI内容。retryable仍须满足最后一轮和当前权限 |
| message_stopped | 收到取消或连接断开后可关闭流，不强求Fast发此事件 | status=STOPPED、reason=USER_STOP；业务停止事务成功后尽力发出，通常也可由stop接口响应确认 |

候选evidence不是最终引用。内部evidence数据由Spring核对来源身份、当前版本和权限，存run.evidence_manifest（仅ID和定位，不复制正文），**不向React直接暴露候选文件列表**。最终引用编号由Fast根据候选evidenceId建立稳定映射，模型只输出候选ID；Fast缓冲不完整标记、拒绝未知ID，将合法标记转换为〔number〕。最终citations只含正文实际使用的标记，编号可有间隔，不把未使用候选伪造成引用。完整正文的引用标记与最终列表必须一一可解释；不满足则协议错误，不发完成。

Fast的message_error本身终止计算；Spring生成自己的INCOMPLETE事件。文档足以回答、部分支持、无依据澄清和冲突提示都可以正常COMPLETED；是否有引用由真实证据决定。服务故障必须message_error，禁止转成“当前知识库没有足够资料”。

### 9.3 runId 与 seq 的范围

runId由Spring为每次明确的新生成命令创建；重试/编辑新run，消息对可复用。前端只接收当前活动run的事件；不能仅因seq更大就把另一个run的内容追加进来。

**seq在每一段SSE连接内从1递增，两个后端的计数不共享。** Fast的seq用于Spring校验内部帧顺序；Spring的seq是对外业务事件顺序，并持久化到Day4的run.last_seq。Spring会隐藏内部候选evidence、提前发公开message_start、在完成时新增最终evidence，因此不能假设两段连接的seq相等；转发必须重新封装，禁止原字节透传。

处理规则：

1. Spring每个run只有一个事件写入器。公开message_start先持久化seq=1；内部message_start核对后不再次向React发送。
2. 内部同run重复seq<=已处理值丢弃；新seq必须连续，缺号/乱序或run不匹配终止为STREAM_PROTOCOL_ERROR。不尝试跨连接续接或更换同run上游。
3. 每批正文在事务中核验当前Session、权限、来源、run=GENERATING、messages.current_run_id=该run，再保存content和新的公开last_seq，提交后才发送token。只以匹配runId+seq作幂等条件；不得用seq单独定位消息。
4. Fast的最终evidenceId清单收到后，Spring再次核验真实切片及当前发布指针，在同一事务保存完整正文、citations、COMPLETED并预留公开evidence和message_complete的连续seq；提交后按序发送。完成事务失败则只发可记录的message_error，不能提前告知完成。
5. React仅对当前run接受seq=lastReceivedSeq+1；重复值丢弃、缺号停止拼接并GET历史。复用同一assistantMessageId时先按新run的message_start清空旧片段；旧流迟到永远不能覆盖当前run。stop响应或流终态确认后锁定该run为终态，丢弃之后所有token，即使它们已在网络缓冲中、seq更大也不能追加。

run.last_seq不提供断点重放语义，没有事件日志表，也不接受Last-Event-ID作续传。允许合并模型原始小token为一条业务token事件；合并后再分配连续seq，不在已分配事件之间跳号。

### 9.4 完整公开成功流示例

以下对应一个run的一条公开流，seq连续；sample答案仅用于契约展示，实际依据演示资料决定。

```text
id: 80000000-0000-4000-8000-000000000001:1
event: message_start
data: {"runId":"80000000-0000-4000-8000-000000000001","seq":1,"requestId":"req-demo-001","conversationId":"60000000-0000-4000-8000-000000000001","userMessageId":"70000000-0000-4000-8000-000000000001","assistantMessageId":"70000000-0000-4000-8000-000000000002","triggerKind":"INITIAL","status":"GENERATING"}

id: 80000000-0000-4000-8000-000000000001:2
event: token
data: {"runId":"80000000-0000-4000-8000-000000000001","seq":2,"requestId":"req-demo-001","content":"应在出差结束后"}

id: 80000000-0000-4000-8000-000000000001:3
event: token
data: {"runId":"80000000-0000-4000-8000-000000000001","seq":3,"requestId":"req-demo-001","content":"十个工作日内提交〔1〕。"}

id: 80000000-0000-4000-8000-000000000001:4
event: evidence
data: {"runId":"80000000-0000-4000-8000-000000000001","seq":4,"requestId":"req-demo-001","citations":[{"id":"a0000000-0000-4000-8000-000000000001","number":1,"documentId":"30000000-0000-4000-8000-000000000001","documentVersion":1,"fileName":"报销制度.pdf","pageStart":3,"pageEnd":3,"availability":"AVAILABLE"}]}

id: 80000000-0000-4000-8000-000000000001:5
event: message_complete
data: {"runId":"80000000-0000-4000-8000-000000000001","seq":5,"requestId":"req-demo-001","status":"COMPLETED","assistantMessageId":"70000000-0000-4000-8000-000000000002","lastSeq":5,"finishedAt":"2026-11-19T02:00:08Z"}

```

无依据的正常回答仍可先token给出说明，再evidence citations=[]和message_complete；不会制造引用。前端收到完成后才启用可点击引用，逐个详情请求再次鉴权；生成中的〔number〕只作文字，不提前暴露文件地址。

### 9.5 内部事件顺序示例

内部message_start seq=1；首段正文前的evidence seq=2：

```text
id: 80000000-0000-4000-8000-000000000001:2
event: evidence
data: {"runId":"80000000-0000-4000-8000-000000000001","seq":2,"requestId":"req-demo-001","items":[{"evidenceId":"e1","documentId":"30000000-0000-4000-8000-000000000001","documentVersion":1,"attemptId":"50000000-0000-4000-8000-000000000001","chunkId":"b0000000-0000-4000-8000-000000000001","fileName":"报销制度.pdf","pageStart":3,"pageEnd":3,"locator":{}}]}

```

随后内部token seq=3、4；最后内部message_complete seq=5，citations=[{number:1,evidenceId:'e1'}]。Spring隐藏内部seq=2的候选清单并保存manifest，对公开正文重新编号为2、3，再在事务完成后发公开evidence=4和message_complete=5；本例数字偶合不表示两个计数器等价。Fast不得在token开始后换入未先上报的新证据；确需重新检索则必须在首个token前完成并最终只发一份evidence。

### 9.6 错误、停止、超时与断线

| 条件 | Spring权威动作 | Fast/React协作 |
| --- | --- | --- |
| 尚未提交SSE头的校验失败 | 返回对应HTTP+JSON错误；不创建模型执行 | React解析错误envelope，保留输入 |
| 接收问题后30秒无正文 | INCOMPLETE，code=FIRST_TEXT_TIMEOUT；停止本run | Fast取消；提示超时，可按最后一轮规则人工重试；start/evidence/心跳均不算正文 |
| 单次120秒上限 | INCOMPLETE，code=GENERATION_TIMEOUT，保留已保存正文 | 不从Fast收到请求时重算；原run后续token/done丢弃 |
| Fast/LLM故障、模型容量不足 | INCOMPLETE，AI_SERVICE_ERROR / LLM_SERVICE_ERROR / CONTEXT_TOO_LARGE | 发message_error，不能描述为资料不足；容量错误引导缩短/新会话 |
| 用户停止 | 数据库原子STOPPED，先释放会话生成约束，再cancel | stop响应或message_stopped确认；部分正文保留，之后可编辑最近问题重发 |
| Session失效/退出 | INCOMPLETE，AUTHENTICATION_REQUIRED | 停止、提示重新登录，隐藏受保护内容；不会因重新登录自动重启run |
| 撤权/KB开始删除 | INCOMPLETE，ACCESS_REVOKED | 尝试发终态后关闭流；React立刻清除该会话当前正文/来源并显示占位；历史GET重新鉴权 |
| 证据文档替换/删除 | INCOMPLETE，SOURCE_CHANGED | 停止继续使用失效证据；旧片段保留但标未完成/可能过期，后续提问重新检索 |
| 浏览器网络断开/主动Abort | INCOMPLETE，CLIENT_DISCONNECTED（如果完成事务尚未胜出） | Spring传播取消；重连读取历史，不自动重发问题/续接流 |
| 完成已提交但最后帧丢失 | 保留COMPLETED，不把已成功状态回退 | GET历史/执行记录得到真实结果；不能因客户端没看到complete就新建相同问题 |
| Spring重启 | 旧GENERATING改INCOMPLETE，INTERRUPTED | 保存片段不丢；人工新run，不自动恢复旧连接 |

计时起点是本次问题提交：React从用户点击提交起执行1秒等待反馈及30/120秒端到端计时，Spring从HTTP请求入口即记录requestedAt并覆盖校验、检索、排队和网络等待。浏览器到Spring的传输时间无法由服务端单独量到，因此前端截止更早时主动stop/Abort，服务端期限仍是保守的后端上限；心跳不延长任何期限。不是“每30秒没新token就超时”的空闲计时。

Spring活动流在内存中关联当前Session与run以处理退出；持久化事实仍在run表。每批接纳按Day4锁序同步权限/来源变更；Session过期/退出同步使本Session活动run失效。已经发出的网络字节无法收回，协议只保证确认失效后不再接纳新片段，并要求前端清除当前展示。

流中错误示例（假设当前公开last_seq=3）：

```text
id: 80000000-0000-4000-8000-000000000001:4
event: message_error
data: {"runId":"80000000-0000-4000-8000-000000000001","seq":4,"requestId":"req-demo-001","status":"INCOMPLETE","code":"LLM_SERVICE_ERROR","message":"模型服务中断，回答未完成","retryable":true}

```

一个run只能提交一个终态；stop、超时、权限变更与complete竞争同一状态条件。若数据库暂不可写，Spring关闭流并返回可用的连接失败信号，不发送声称已持久化的终态；重启/恢复后按实际数据库状态核对。STOPPED/COMPLETED/INCOMPLETE不能因迟到回调互相覆盖。

React点击停止时，先调用stop接口；收到成功终态再终止本地reader/AbortController。若网络失败可Abort以释放连接，但界面显示“停止状态待确认”，通过GET执行记录确认，不能把本地Abort冒充后端停止已生效。若连message_start都未收到，先Abort原连接，并GET历史：从提交前最后已知ordinal之后分页读取，找ASSISTANT.currentClientRequestId等于原命令的消息，取得currentRunId再查询/停止；没有找到则继续只读核对，不能用重发POST来“查询”并可能启动一个本未抵达服务端的命令。retry/edit复用消息对时重新获取原消息所在页；若该消息已经指向另一clientRequestId，原执行不再是当前执行，不能停止后来的run。无权或会话已删除按403/404处理。停止还未确认前不发送会触发第二个run的新命令。

### 9.7 幂等：只承诺已有存储能保证的行为

生成命令统一以 `(conversation_id,client_request_id)` 唯一键去重。Spring在权限检查后，**先查询幂等记录，再检查目标run是否仍是最后一轮**，避免同一次retry的网络重投因第一次已成功而误判。request_sha256计算规范化JSON `{kind,conversationId,targetRunId,content}` 的JCS UTF-8 SHA-256：kind是INITIAL/RETRY/EDIT，INITIAL和EDIT的content为trim后的提交问题、RETRY的content=null，INITIAL的targetRunId=null，RETRY是Path目标runId，EDIT的targetRunId=null（用其命令类型和提交内容绑定原命令）。这些值在命令首次接纳时保存摘要，不能用后来已编辑的当前消息正文重新计算旧命令。

相同去重键和相同指纹：不新增消息、不再次调用Fast、不返回第二条流；示例：

```http
HTTP/1.1 409 Conflict
Content-Type: application/json
Location: /api/v1/generation-runs/80000000-0000-4000-8000-000000000001
X-Request-Id: req-demo-002

{"code":"GENERATION_ALREADY_EXISTS","message":"该命令已创建生成，请读取执行状态和历史","data":null,"requestId":"req-demo-002"}
```

同键不同指纹409 IDEMPOTENCY_KEY_REUSED；新的明确问题/人工retry/edit使用新的clientRequestId，新run才是新执行。重复请求不能先冲掉当前正文再检查唯一键。会话删除后去重记录也删除；不创建不存在会话，不跨用户/会话查询原结果。

| 操作 | 幂等/重试约定 |
| --- | --- |
| GET列表/详情/历史 | 可重复读取；每次重做权限检查，结果可能随状态变化 |
| PUT/DELETE成员、PATCH知识库、logout | 业务效果幂等；响应字段/时间可能不同 |
| DELETE文档/知识库/indexes、stop/cancel | 指定对象清理/失效效果幂等；状态反馈可从202变200，不自动重复失败后的业务操作 |
| 创建账号/知识库/会话、上传/替换文件 | 不增加通用幂等表；依唯一约束及状态查询处理响应未知，不自动重放POST/上传/替换 |
| 文档人工retry | 不重复启动活动attempt；再次失败后再次点击才创建新attempt，不由网络库自动重试 |
| processing-jobs同job/attempt | 有限网络重投不启动第二次处理；STARTED持久化许可防进程registry丢失后重算 |
| 处理结果回调 | 可重发相同已提交结果，直到原scope/deadline；不同结果冲突，不重新算向量 |
| retrieval | 无业务写入，可在本run剩余期限内有限重试；可能产生重复Embedding成本，不能超出总deadline |
| answer-stream | 不对同run自动重试创建；中断转INCOMPLETE，人工重试新run |

网络库关闭生成POST、上传、文件替换和人工retry的透明自动重试。处理命令/回调只在原deadline内进行有限重投，建议最多3次、短退避；达到上限不创建新attempt、不延长期限，也不把“未收到响应”当作“对方没执行”。本稿不添加Redis、事件重放表、跨服务分布式事务或通用Idempotency-Key缓存。

## 10. 两个核心流程的接口串联

### 10.1 文档上传到可检索

1. React带Session/CSRF multipart上传到Spring；服务端校验ADMIN/KB/文件并保存原文件，事务创建PROCESSING、version/job/attempt；返回202和详情地址。
2. Spring调Fast processing-jobs，传三组能力：执行scope、fileTicket、resultScope；Fast以STARTED向Spring取得一次执行许可。React只轮询Spring详情。
3. Fast GET Spring files票据获取二进制，受控解析/切分，向外部Embedding API计算，再提交ai切片。切片写入触发器校验有效来源，不根据浏览器传来的权限放行。
4. Fast用resultScope回调SUCCEEDED或FAILED。Spring核验当前版本/attempt/deadline/manifest，在事务里发布或失败；向量写入成功却未发布的内容不进入View。
5. React轮询到PUBLISHED才展示发布完成。300秒未完成转FAILED，人工retry创建同job的新attempt；迟到原attempt无法覆盖。删除则先失效再取消/清索引/清文件，不因Fast晚到恢复内容。

### 10.2 问题到回答和引用

1. React创建固定KB会话，POST messages只发content/clientRequestId；Spring从会话取KB并校验owner/当前授权/输入/单生成约束，保存消息和run后发公开message_start。
2. Spring选取最近最多10轮正常完成上下文、配置模型与实际deadline，签发单库scope，POST Fast answer-stream；Fast同run只接受一次。
3. Fast在受限View中检索，先发内部evidence，再调用LLM并流式发token。Spring核验证据、保存manifest，每批正文重新检查当前执行/权限/来源，先保存再发给React。
4. Fast计算完成给出最终evidenceId引用；Spring核验真实切片和当前版本，将全文、最终引用和COMPLETED原子保存，然后发公开evidence和message_complete。
5. React点击引用GET Spring citation，读取真实片段并用带版本的download地址下载。任何时刻的撤权/替换/删除/停止都通过Spring状态终止后续接纳；网络断开只读已保存历史，不另开模型执行。

## 11. 与Day3/Day4的对应及本次收敛

| 设计点 | 本稿落地 |
| --- | --- |
| 前端唯一入口与core写入所有权 | React只到Spring；files/result是Spring内部接口；Fast不写core |
| Day3无版本草案路径 | 本稿统一/api/v1、/internal/v1；messages/answer-stream/download取代草案generations/file路径，第一版不同时维护旧别名 |
| 处理任务、版本与attempt | job对应版本、人工retry新增attempt；结果携带三元组，发布必须匹配当前指针 |
| Day4幂等字段与消息模型 | clientRequestId/request_sha256落generation_runs；current_run_id栅栏，重试复用消息对 |
| Day4首token前证据清单 | 内部evidence先保存，不直接向前端暴露候选；完成时发最终引用 |
| Day4 run.last_seq | 只存Spring对外事件序列；内部seq另校验，不能直通两个后端 |
| Day4删除/重启 | 最小来源标记支持历史失效，物理内容先清；重启不自动重跑未完成执行 |
| 本次必要细化 | result新增STARTED条件许可；接口无新增业务表，复用attempt.status/started_at；新增Session CSRF初始化及既有PRD配套接口 |
| 保持范围 | 无JWT浏览器登录、refresh token、WebSocket、事件续传、Agent工具接口、多租户、批量上传或通用工作流 |

公开文档管理列表遵循这次明确要求，作为基本维护/有权列表，不增加PRD暂缓的独立全文搜索产品。模型配置由后端固定，React不能传API key或选择任意模型；Embedding维度仍在选模型后由配置/实际结果确认，API示例不决定物理维度。

## 12. 评审与实施验收清单

| 评审维度 | 本稿应核对的证据 |
| --- | --- |
| REST合理性20% | 资源路径/方法、201/202/200、DELETE确认体的本地兼容约定、列表/详情/配套命令边界；不把202等同最终成功 |
| 契约完整性20% | 19个指定公开接口、7个指定内部接口均有参数位置、类型/校验、请求/成功/错误示例、权限；另补现有MVP必需接口 |
| 双后端边界20% | 每个内部接口明确宿主与调用方向；Spring只写core、Fast写ai并计算；回调和文件读取不会误路由到Fast |
| 权限安全15% | Session/CSRF、角色/归属、KB当前授权、服务密钥+签名范围、单库SQL、历史隐藏/引用再校验 |
| SSE15% | POST解析、两段seq、run/currentRun匹配、内部证据先行、先保存后转发、唯一终态、停止/断线/超时 |
| 错误与幂等10% | HTTP与业务码区分、握手前后错误不同、生成去重键、任务STARTED许可/相同回调、重启不自动重算 |

实施后的必要集成场景：注册role注入失败；无KB权限403；匿名/失效Session401；CSRF失败403；员工不能看管理字段；同库大小写重名409；文档202后不检索staging；回调超时重投不重算；v2失败不恢复v1；Fast重启拒绝重复STARTED；同clientRequestId不重复问题；相同key不同载荷409；R1停止后迟到token不能写R2；撤权/来源更新中止输出；断线后读部分历史；完成帧丢失但数据库COMPLETED仍可读；引用删除不返旧文；服务异常不伪装资料不足；30秒无正文和120秒总上限包含检索/网络；原20组固定问答案例继续验收。

本交付是接口契约，尚未启动Spring/FastAPI、执行OpenAPI生成或进行真实HTTP联调；不能把示例当已实现接口。后续用springdoc-openapi和FastAPI生成两份实际OpenAPI，再按本稿核对；不提前引入网关/SDK发布平台。文档静态检查可确认覆盖、JSON/UUID示例和SSE顺序，但不能证明权限实现或数据库锁已正确生效。
