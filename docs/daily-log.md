# 项目学习日志

当前进度：Day 10 已验收，下一步为 Day 11：GET 查询与分页。
原始交接手册记录的是 Day 8 快照；接续时先核对本日志与实际代码。

## Day 9 — 统一异常处理与 API 错误契约

日期：2026-11-23（Asia/Shanghai）。

### 完成的任务

- 核对实际仓库、pom.xml 和 docs/api-design.md，沿用 Day 8 的知识库创建纵向切片。
- 使用 ApiResponse<T> 统一成功与失败 JSON：code、message、data、requestId；错误 data 为 null。
- 知识库重名通过 KnowledgeBaseNameConflictException 映射为 409 / KNOWLEDGE_BASE_NAME_CONFLICT。
- 名称和简介违反领域规则时抛出 KnowledgeBaseValidationException，映射为 422 / VALIDATION_ERROR。
- createdBy 由服务端提供；其缺失仍抛出普通 IllegalArgumentException，由兜底处理器返回 500 / INTERNAL_ERROR。
- 未预期异常返回固定通用消息，不向客户端返回原始异常内容。
- 修复 RequestIdFilter 遗漏 filterChain.doFilter(request, response) 的问题，使请求继续进入 Spring MVC。
- HTTP 状态统一使用 HttpStatus 枚举；422 使用当前 Spring Framework 7 支持的 UNPROCESSABLE_CONTENT。

### 主要新增或修改文件

以下路径均相对仓库根目录；部分文件在接手时已经存在，本轮进行了核对或补全。

- backend/spring-boot/src/main/java/com/enterprise/knowledge/common/api/ApiResponse.java
- backend/spring-boot/src/main/java/com/enterprise/knowledge/common/exception/GlobalExceptionHandler.java
- backend/spring-boot/src/main/java/com/enterprise/knowledge/common/filter/RequestIdFilter.java
- backend/spring-boot/src/main/java/com/enterprise/knowledge/knowledgebase/application/KnowledgeBaseNameConflictException.java
- backend/spring-boot/src/main/java/com/enterprise/knowledge/knowledgebase/application/KnowledgeBaseService.java
- backend/spring-boot/src/main/java/com/enterprise/knowledge/knowledgebase/domain/KnowledgeBaseValidationException.java
- backend/spring-boot/src/main/java/com/enterprise/knowledge/knowledgebase/domain/KnowledgeBase.java
- backend/spring-boot/src/main/java/com/enterprise/knowledge/knowledgebase/interfaces/rest/KnowledgeBaseController.java
- backend/spring-boot/src/test/java/com/enterprise/knowledge/common/exception/GlobalExceptionHandlerTest.java
- backend/spring-boot/src/test/java/com/enterprise/knowledge/knowledgebase/domain/KnowledgeBaseTest.java
- backend/spring-boot/requests.http
- README.md、docs/daily-log.md、Study_note/Day9.txt

### 功能结果与验收证据

用户使用 Postman 完成真实 POST /api/v1/knowledge-bases 验证，并确认以下结果均符合预期：

| 场景 | HTTP | code / 检查项 |
| --- | --- | --- |
| 正常创建 | 201 | SUCCESS；Location 与 data.id 对应 |
| trim 后、大小写不敏感重名 | 409 | KNOWLEDGE_BASE_NAME_CONFLICT；data 为 null |
| 空白名称 | 422 | VALIDATION_ERROR；data 为 null |
| 三次响应的追踪信息 | — | requestId 非空，且与各自 X-Request-Id 响应头一致 |

以上手动结果来自用户实际操作反馈；本轮未保存 Postman 响应导出文件。
500 的转换和通用错误文案由自动化测试验证。

导师在收尾时运行 Spring Boot 完整 Maven 测试，结果为 BUILD SUCCESS：

| 测试类 | 数量 | 失败 / 错误 |
| --- | --- | --- |
| KnowledgeBaseTest | 5 | 0 / 0 |
| KnowledgeBaseServiceTest | 4 | 0 / 0 |
| GlobalExceptionHandlerTest | 4 | 0 / 0 |
| ApplicationTests | 1 | 0 / 0 |
| 合计 | 14 | 0 / 0 |

GlobalExceptionHandlerTest 使用测试 Controller 验证异常转换；真实创建接口的整条路径本次由 Postman 验证，尚无对应的自动化整链路测试。

### 遇到的问题与解决方式

1. 异常处理测试预期 409、500，实际均为 200：过滤器未继续调用处理链，Controller 未执行。补上 doFilter 后通过。
2. 输入错误与内部错误都曾使用 IllegalArgumentException：新增其子类 KnowledgeBaseValidationException，仅用于名称、简介校验，避免将内部错误误归为 422。
3. 新增 422 分支最初使用数字状态：统一为 HttpStatus.UNPROCESSABLE_CONTENT，与现有 409、500 分支风格一致。

### 截至 Day 9 的技术债与范围

- Day 10：Request DTO 尚无 Bean Validation 约束，Controller 尚未使用 @Valid，也尚未处理 MethodArgumentNotValidException。
- 请求协议：损坏 JSON、字段类型不匹配、未知字段的专门错误处理及测试仍待补齐；当前兜底异常处理不代表这些分支已经符合 API 契约。
- 字符计数：API 契约要求 trim 后按 Unicode 码点计数；领域代码目前使用 String.length()。Day 10 添加请求校验前，应先核对 trim 和计数语义，避免与领域规则冲突。
- requestId：当前沿用非空客户端 X-Request-Id，否则生成 UUID；客户端值尚未限制长度与格式，与契约中由 Spring 生成的来源约定仍需统一。
- 公开响应的 Cache-Control: no-store 尚未统一设置。
- GET 列表与分页留在 Day 11；Flyway 和 PostgreSQL 持久化留在 Day 12–13。当前仍为内存仓库，重启丢失数据，查重与保存不具备原子唯一性保障。
- 认证与授权尚未接入，Controller 仍使用固定演示用户 ID。

### Git 与学习状态

Git commit：f62c7e2（异常处理更新），在 Day 10 开始时已核对，Day 8–9 的实现已提交。

用户能否解释关键代码：部分已验证。用户已准确说明，设置 requestId 后还需要将请求传给后续处理链；其他异常边界概念尚未逐项口述验收。

协作约定：用户终端为 Git Bash；后续提供 Git Bash 命令。Java 使用 IntelliJ IDEA，保持小步教学、明确文件位置、客观验收。

下一天前置条件：Day 9 测试已通过，保留领域规则与专用异常，从实际 CreateKnowledgeBaseRequest 和 Controller 开始 Day 10。

## Day 10 — Bean Validation 与请求边界

日期：2026-11-24（Asia/Shanghai）。状态：已验收。

### 完成的任务

- 在 CreateKnowledgeBaseRequest 的 name 上添加 @NotBlank，拒绝 null、空字符串和全空白输入。
- record 紧凑构造器对非 null 的 name 执行 trim，先规范化，再进行 Bean Validation；description 保留原文并允许 null。
- 按现有 API 契约使用 Hibernate Validator 的 @CodePointLength：名称最多 50 个 Unicode 码点，简介最多 500 个。没有使用按 UTF-16 单元计数的 @Size 作为字符串长度规则。
- Controller 的 @RequestBody 参数添加 @Valid，让请求校验在方法体与 Service 调用前执行。
- GlobalExceptionHandler 将 MethodArgumentNotValidException 转换为 422 / VALIDATION_ERROR，message 为“请求参数校验失败”，data 为 null，保留 requestId。
- Domain 保留独立规则保护，并将 String.length() 长度检查修正为 codePointCount；名称 trim 后计数，简介按原文计数。
- 增加 DTO 和真实 Controller 的边界测试；Controller 测试使用 mock Service，验证非法输入不调用业务用例、合法输入将 trim 后的名称交给用例。
- 新增 backend/spring-boot/day10-validation.http，在 IDEA HTTP Client 中生成输入并断言真实响应。

### 新增或修改文件

以下路径均相对仓库根目录。

- backend/spring-boot/src/main/java/com/enterprise/knowledge/knowledgebase/interfaces/rest/CreateKnowledgeBaseRequest.java
- backend/spring-boot/src/main/java/com/enterprise/knowledge/knowledgebase/interfaces/rest/KnowledgeBaseController.java
- backend/spring-boot/src/main/java/com/enterprise/knowledge/common/exception/GlobalExceptionHandler.java
- backend/spring-boot/src/main/java/com/enterprise/knowledge/knowledgebase/domain/KnowledgeBase.java
- backend/spring-boot/src/test/java/com/enterprise/knowledge/knowledgebase/interfaces/rest/CreateKnowledgeBaseRequestTest.java
- backend/spring-boot/src/test/java/com/enterprise/knowledge/knowledgebase/interfaces/rest/KnowledgeBaseControllerTest.java
- backend/spring-boot/src/test/java/com/enterprise/knowledge/knowledgebase/domain/KnowledgeBaseTest.java
- backend/spring-boot/day10-validation.http；原 requests.http 中也有用户追加的空白名称请求。
- README.md、docs/daily-log.md、Study_note/Day10.txt

### 测试结果与验收证据

导师已通过 Git Bash 运行 ./mvnw test：BUILD SUCCESS，34 tests，0 failures，0 errors，0 skipped。之后应用代码和测试代码未再改变，收尾核对沿用该次完整回归结果。

| 测试类 | 数量 | 验证内容 |
| --- | --- | --- |
| KnowledgeBaseTest | 9 | 原领域规则、50/51 码点名称、500/501 码点简介 |
| KnowledgeBaseServiceTest | 4 | 创建、名称 trim、重名、查询全部 |
| CreateKnowledgeBaseRequestTest | 9 | 必填、空白、可空简介、规范化与码点长度边界 |
| KnowledgeBaseControllerTest | 7 | 缺失/空白/超长输入在 Service 前拒绝，合法上限返回 201 |
| GlobalExceptionHandlerTest | 4 | 领域校验、重名及内部错误的转换 |
| ApplicationTests | 1 | Spring 应用上下文加载 |
| 合计 | 34 | 全部通过 |

用户在 IDEA 执行 day10-validation.http 的四条真实请求，确认请求和断言全部成功：

| 请求 | name / description 码点数 | HTTP | 结果 |
| --- | --- | --- | --- |
| 1 | 51 / 500 | 422 | VALIDATION_ERROR，data 为 null |
| 2 | 50 / 501 | 422 | VALIDATION_ERROR，data 为 null |
| 3 | 50 / 500 | 201 | SUCCESS，名称前后空格已去除，Location 对应 data.id |
| 4 | 重复第 3 条 | 409 | KNOWLEDGE_BASE_NAME_CONFLICT，data 为 null |

四条请求的 requestId 非空且与各自 X-Request-Id 一致。真实 HTTP 结果依据用户执行反馈记录，未保存 IDEA 响应导出；请求及断言脚本已保留在仓库。Controller 自动化测试中的 Service 为 mock，实际 Controller → Service → Domain → 内存仓库的衔接由本次真实 HTTP 验收覆盖。

### 问题与设计取舍

1. 普通 @Size 对字符串按 UTF-16 单元计数，与 API 要求的码点计数不同：请求层使用已安装的 Hibernate Validator 扩展 @CodePointLength；Domain 使用 Java 自身的 codePointCount，不依赖校验框架。
2. 原始名称含前后空格时，直接计算原文长度可能误拒绝合法名称：DTO 构造时先 trim，Domain 继续独立规范化和校验。
3. 请求校验失败与领域规则失败属于不同异常：分别处理 MethodArgumentNotValidException 和 KnowledgeBaseValidationException，对外都保持 422 / VALIDATION_ERROR。
4. 用户习惯 IDEA 的 .http 文件：使用请求前脚本生成长输入和响应脚本检查结果，后续手动接口验收优先沿用此方式。

### 未解决技术债与范围

- 损坏 JSON、字段类型不匹配、未知请求字段的专门处理及测试仍待补齐；当前验收不表示 API 全部协议分支已实现。
- requestId 的来源与格式限制、Cache-Control: no-store 仍沿用 Day 9 的待办。
- @CodePointLength 是 Hibernate Validator 扩展；若未来更换校验实现，需替换该约束。当前没有增加依赖或更改技术栈。
- GET 列表和分页按原计划在 Day 11 实现；认证与授权尚未接入，不得把后续演示列表描述为已实现员工权限过滤。
- 当前为内存仓库，重启丢失数据；持久化、Flyway、数据库唯一约束仍属于 Day 12–13。

### Git、学习状态与下一步

Git commit：Day 10 待提交。两个新增测试文件的暂存内容仍是早期版本，提交前重新 git add 选定文件并检查 git diff --cached。

用户能否解释关键代码：部分已验证；本日各小步测试与真实请求验收均成功，DTO/Domain 分层及码点计数尚未单独口述验收，不据此声称已能独立实现。

协作约定：终端指令使用 Git Bash；Java 使用 IntelliJ IDEA；手动接口验收优先使用 IDEA HTTP Client 的 .http 文件。

下一天前置条件：从当前 Repository、Service 和 Controller 核对 GET 列表起点，按既有 API 实现 page/pageSize 默认值、边界、稳定排序和响应 DTO。认证尚未实现时，仅明确标注为管理员演示范围；保留 Day 10 的校验和测试，不重复完成内容。
