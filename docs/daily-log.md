# 项目学习日志

当前进度：Day 9 已验收，下一步为 Day 10：Bean Validation 与请求边界。
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

### 未解决技术债与范围

- Day 10：Request DTO 尚无 Bean Validation 约束，Controller 尚未使用 @Valid，也尚未处理 MethodArgumentNotValidException。
- 请求协议：损坏 JSON、字段类型不匹配、未知字段的专门错误处理及测试仍待补齐；当前兜底异常处理不代表这些分支已经符合 API 契约。
- 字符计数：API 契约要求 trim 后按 Unicode 码点计数；领域代码目前使用 String.length()。Day 10 添加请求校验前，应先核对 trim 和计数语义，避免与领域规则冲突。
- requestId：当前沿用非空客户端 X-Request-Id，否则生成 UUID；客户端值尚未限制长度与格式，与契约中由 Spring 生成的来源约定仍需统一。
- 公开响应的 Cache-Control: no-store 尚未统一设置。
- GET 列表与分页留在 Day 11；Flyway 和 PostgreSQL 持久化留在 Day 12–13。当前仍为内存仓库，重启丢失数据，查重与保存不具备原子唯一性保障。
- 认证与授权尚未接入，Controller 仍使用固定演示用户 ID。

### Git 与学习状态

Git commit：待提交。当前工作区同时包含 Day 8 与 Day 9 的变更，部分新增文件的暂存版本仍是早期内容；提交前需重新暂存选定文件并检查 git diff --cached，确保提交的是最新实现。

用户能否解释关键代码：部分已验证。用户已准确说明，设置 requestId 后还需要将请求传给后续处理链；其他异常边界概念尚未逐项口述验收。

协作约定：用户终端为 Git Bash；后续提供 Git Bash 命令。Java 使用 IntelliJ IDEA，保持小步教学、明确文件位置、客观验收。

下一天前置条件：Day 9 测试已通过，保留领域规则与专用异常，从实际 CreateKnowledgeBaseRequest 和 Controller 开始 Day 10。
