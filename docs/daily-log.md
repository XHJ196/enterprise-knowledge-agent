# 项目学习日志

当前进度：Day 14 已验收（完整后端测试 100 项通过）；Day 15 项目结构和 React 基础进行中。已提交基线仍为 931cbec，本轮实现与收尾更新尚未提交。
原始交接手册记录的是 Day 8 快照；接续时先核对本日志与实际代码。

协作约定更新（2026-11-26）：用户要求整个项目完成后再集中做理解复盘。当前继续按小步骤实现、核对代码并运行验收；不以口述问答作为进入下一步的前置条件。Java 使用 IDEA，终端使用 Git Bash，手动请求使用 .http。

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

Git commit：6e23448（Complete Day 10 request validation and Unicode length checks）。Day 11 收尾时已核对，Day 10 的实现和验收记录已提交。

用户能否解释关键代码：部分已验证；本日各小步测试与真实请求验收均成功，DTO/Domain 分层及码点计数尚未单独口述验收，不据此声称已能独立实现。

协作约定：终端指令使用 Git Bash；Java 使用 IntelliJ IDEA；手动接口验收优先使用 IDEA HTTP Client 的 .http 文件。

下一天前置条件：从当前 Repository、Service 和 Controller 核对 GET 列表起点，按既有 API 实现 page/pageSize 默认值、边界、稳定排序和响应 DTO。认证尚未实现时，仅明确标注为管理员演示范围；保留 Day 10 的校验和测试，不重复完成内容。

## Day 11 — GET 查询与分页

日期：2026-11-24（Asia/Shanghai）。状态：已验收。

### 完成的任务

- 实现 GET /api/v1/knowledge-bases，沿用 ApiResponse<T> 成功信封，HTTP 200。
- KnowledgeBaseListRequest 使用 Integer 接收可省略的查询参数；构造器提供 page=1、pageSize=20，@Min / @Max 限制 page>=1、1<=pageSize<=100。
- Controller 使用 @Valid @ModelAttribute 绑定、校验 URL 查询参数。越界数值、非整数文本及超过 Integer 范围的文本返回 422 / VALIDATION_ERROR，非法请求不调用 Service。
- Service 新增 findAllForAdminDemo()，明确为管理员演示范围，排除 DELETED，保留 ACTIVE、DELETING、DELETE_FAILED；尚未实现认证和员工授权过滤。
- 结果按 createdAt DESC、id ASC 稳定排序。UUID 使用规范字符串比较，避免 Java UUID.compareTo 的有符号比较与 PostgreSQL UUID 排序在高位跨界时出现差异。
- Service 新增 findPageForAdminDemo()，过滤和排序后再分页；total 统计演示可见范围，超出最后一页返回空 items，保留 total，hasNext=false。
- offset 使用 (page - 1L) * pageSize，避免 int 乘法溢出。Service 对非法分页参数保留 IllegalArgumentException 防御，不将所有此类异常统一映射为客户端 422。
- 应用层 KnowledgeBasePage 承载领域对象，并用 List.copyOf 复制为不可修改列表；HTTP 层 KnowledgeBasePageResponse 通过已有 KnowledgeBaseResponse.from 映射每一项，保持 Domain 与响应 DTO 分离。
- 列表接口仅允许 page、pageSize 两个查询参数；未知参数抛出 RequestValidationException，统一为 422 / VALIDATION_ERROR。
- 列表接口检查请求流的首字节，发现非空请求体时抛出 UnexpectedRequestBodyException，统一为 400 / INVALID_REQUEST_BODY。JSON、普通文本和空格均覆盖测试。
- docs/api-design.md 补齐该 400 错误码，并明确未知查询参数的 422 语义。
- 新增 backend/spring-boot/day11-pagination.http，含 12 条真实请求与响应断言，覆盖创建、列表、翻页、排序和错误边界。

### 新增或修改文件

以下路径均相对仓库根目录。

- backend/spring-boot/src/main/java/com/enterprise/knowledge/knowledgebase/interfaces/rest/KnowledgeBaseListRequest.java
- backend/spring-boot/src/main/java/com/enterprise/knowledge/knowledgebase/application/KnowledgeBasePage.java
- backend/spring-boot/src/main/java/com/enterprise/knowledge/knowledgebase/application/KnowledgeBaseService.java
- backend/spring-boot/src/main/java/com/enterprise/knowledge/knowledgebase/interfaces/rest/KnowledgeBasePageResponse.java
- backend/spring-boot/src/main/java/com/enterprise/knowledge/knowledgebase/interfaces/rest/KnowledgeBaseController.java
- backend/spring-boot/src/main/java/com/enterprise/knowledge/common/exception/RequestValidationException.java
- backend/spring-boot/src/main/java/com/enterprise/knowledge/common/exception/UnexpectedRequestBodyException.java
- backend/spring-boot/src/main/java/com/enterprise/knowledge/common/exception/GlobalExceptionHandler.java
- backend/spring-boot/src/test/java/com/enterprise/knowledge/knowledgebase/interfaces/rest/KnowledgeBaseListRequestTest.java
- backend/spring-boot/src/test/java/com/enterprise/knowledge/knowledgebase/application/KnowledgeBaseServiceTest.java
- backend/spring-boot/src/test/java/com/enterprise/knowledge/knowledgebase/interfaces/rest/KnowledgeBaseControllerTest.java
- backend/spring-boot/day11-pagination.http、docs/api-design.md
- README.md、docs/daily-log.md、Study_note/Day11.txt；Study_note/Day10.txt 补正提交记录。

### 测试结果与验收证据

导师在本次收尾通过 Git Bash 运行 ./mvnw test，结果为 BUILD SUCCESS：71 tests，0 failures，0 errors，0 skipped。

| 测试类 | 数量 | 验证内容 |
| --- | --- | --- |
| ApplicationTests | 1 | Spring 应用上下文加载 |
| GlobalExceptionHandlerTest | 4 | 已有领域校验、重名和内部错误转换 |
| KnowledgeBaseTest | 9 | 已有领域规则与 Unicode 码点边界 |
| CreateKnowledgeBaseRequestTest | 9 | 已有创建请求校验 |
| KnowledgeBaseListRequestTest | 9 | 默认分页值、合法边界、越界拒绝 |
| KnowledgeBaseServiceTest | 15 | 已有创建用例，状态过滤、排序、分页、空页和大页码 |
| KnowledgeBaseControllerTest | 24 | 已有 POST，GET 默认/显式分页、响应映射、非法参数、未知参数和请求体拒绝 |
| 合计 | 71 | 全部通过 |

Service 测试通过固定时间和 UUID 的夹具验证同时间的 ID 排序，覆盖 UUID 高位跨界；DELETED 不进入 items 或 total。Controller 测试使用 mock Service，验证 HTTP 绑定、响应和拒绝请求时不调用业务用例，不能据此单独证明实际存储链路正确。

用户在 IDEA HTTP Client 中按顺序执行 day11-pagination.http，并确认全部 12 条请求及响应断言通过：

| 请求编号 | 场景 | HTTP / 核心结果 |
| --- | --- | --- |
| 1 | 重启服务后的空列表，省略查询参数 | 200；page=1、pageSize=20、items=[]、total=0、hasNext=false |
| 2–4 | 创建三条验收数据 | 各 201；记录 ID、Location 和 requestId |
| 5 | 默认分页查询三条记录 | 200；检查三个 ID、createdAt DESC / id ASC 顺序及字段 |
| 6 | page=1、pageSize=2 | 200；前两条、total=3、hasNext=true |
| 7 | page=2、pageSize=2 | 200；剩余一条、total=3、hasNext=false，与第一页不重复 |
| 8 | page=3、pageSize=2 | 200；items=[]，total 仍为 3，hasNext=false |
| 9 | pageSize=101 | 422 / VALIDATION_ERROR |
| 10 | page=abc | 422 / VALIDATION_ERROR |
| 11 | 多出未知参数 size | 422 / VALIDATION_ERROR |
| 12 | GET 携带非空 JSON 请求体 | 400 / INVALID_REQUEST_BODY |

每条请求均检查 JSON requestId 非空且与 X-Request-Id 响应头一致。真实 HTTP 结果依据用户执行反馈记录，未保存 IDEA 响应导出；请求与断言脚本保存在仓库。真实 Controller → Service → Domain / 内存仓库的衔接由本次 HTTP 验收补充验证；手动验收没有构造相同时间戳或 DELETED 状态，这些场景由 Service 自动化测试覆盖。

### 问题与设计取舍

1. @ModelAttribute 绑定 URL Query，@RequestBody 处理请求体，两者职责不同。本项目当前 Spring Framework 7 的绑定或 Bean Validation 失败由已有 MethodArgumentNotValidException 处理器归一为 422，实际测试已验证。
2. @Valid 不会自动拒绝未知查询参数：在当前列表接口中显式检查允许的参数名，避免拼错 pageSize 后静默使用默认值。
3. 接口不声明 @RequestBody 不代表 Spring 自动拒绝请求体：显式读取一个字节判断是否非空，不只依赖 Content-Length，因此不要求客户端提供已知长度；当前真实 HTTP 验收使用 JSON 请求体。
4. 分页响应的 total 是过滤后总数，不是本页 items.size()。越过最后一页是合法查询，返回 200 和空数组。
5. 当前在内存中过滤、排序、分页，便于验证业务契约；后续 PostgreSQL adapter 应将这些操作下推数据库，并保持同样的可见范围、排序和分页语义。

### 未解决技术债与范围

- 认证与授权尚未接入，创建接口仍使用固定演示用户 ID。管理员演示范围不是 ADMIN 角色校验，不能声称已实现员工知识库过滤。
- 仍为内存仓库，重启丢失数据；查重与保存不具备原子唯一性保障。Flyway / 表结构按 Day 12 推进，PostgreSQL adapter、事务和唯一约束冲突转换按 Day 13 推进。
- 分页参数空字符串、重复参数的策略与测试尚未补齐；本次验收不表示所有输入组合均已严格拒绝。绑定/校验先于 Controller 方法体执行，多个错误同时存在时的响应优先级尚未专门验证。
- 损坏 JSON、字段类型不匹配、未知 JSON 请求字段的专门处理仍是既有待办；本日完成的是列表未知 Query 参数检查。
- requestId 仍接受非空客户端值，其来源和格式限制与契约的统一、公开响应 Cache-Control: no-store 仍未完成。
- 尚无自动化的真实 HTTP / 数据库整链路回归；MockMvc 使用 mock Service，真实内存链路本次由用户操作 HTTP Client 验证。
- Maven 测试输出包含 Mockito 动态附加 Java agent 的已有提示；本次测试通过，将来 JDK 禁止动态加载时需按 Mockito 配置调整测试启动参数。

### Git、学习状态与下一步

Day 11 收尾时的基线为 6e23448（Day 10）。后续已核对，Day 11–12 实现和收尾文档一起提交在 28ccc77 中。此前 IDEA 暂存早期空类的问题已随完整文件提交消除。

用户按小步完成代码和测试，并确认全部真实请求与断言通过。用户曾询问未知参数检查在 list() 中的具体位置，已定位到 Service 调用前。尚未单独口述验收分页公式、排序和分层职责，不因测试通过就声称能独立实现。

协作约定继续保持：Java 用 IntelliJ IDEA；终端命令用 Git Bash；手动 API 验收用 .http；一次一个可验证小步骤，先核对实际代码，不重复 Day 1–11。

下一步按原手册进入 Day 12：先核对现有 Docker PostgreSQL、数据库设计、pom.xml 与 datasource 配置，解释 JDBC / JPA / Hibernate / Spring Data JPA 的关系，保持 Flyway 为唯一 schema migration 工具，逐步建立最小 migration、知识库表和唯一约束。区分 Domain record 与 JPA Entity；数据库 adapter 的完整替换属于 Day 13，不提前批量实现。

## Day 12 — PostgreSQL 持久化基础与 Flyway

日期：2026-11-24（Asia/Shanghai）。状态：已验收。

### 最终环境与配置

| 项目 | 实际值 |
| --- | --- |
| Docker Compose 服务 / 容器 | postgres / enterprise-postgres |
| 数据库 / 本地数据库账号 | enterprise_knowledge / MicroJason |
| Windows 连接入口 | 127.0.0.1:5333 |
| PostgreSQL 容器内端口 | 5432 |
| PostgreSQL / pgvector | 16.16 / 0.8.7 |
| JDBC URL | jdbc:postgresql://127.0.0.1:5333/enterprise_knowledge |
| Spring Boot / Java | 4.1.2 / 25.0.2 |
| 业务 schema / 迁移历史表 | core / core.flyway_schema_history |

原交接手册中的账号 app_user、端口 5432 不代表当前本机配置。Day 12 开始时 Compose 使用 MicroJason 和本机端口 5332，后因本机另一容器占用 5332，统一调整本项目 Compose 和 Spring 的默认地址为 5333。保留命名卷中的数据。

### 完成的任务与文件

- pom.xml 添加 spring-boot-starter-data-jpa、spring-boot-starter-flyway、flyway-database-postgresql、PostgreSQL JDBC 驱动；版本交由现有 Spring Boot BOM 管理，没有手动固定另一套版本。
- application.yaml 配置 datasource，URL/账号允许由环境变量覆盖，密码使用 ${DB_PASSWORD}；没有将真实密码写入配置或日志。
- JPA 使用 open-in-view=false、ddl-auto=validate、hibernate.default_schema=core。当前没有 JPA Entity，validate 不能据此证明字段映射已经完成。
- Flyway 使用 core 为默认 schema，扫描 classpath:db/migration，允许创建 schema，并保持 clean-disabled=true；Spring SQL 初始化关闭，Flyway 是唯一 schema migration 工具。
- V1__create_users.sql 按已有设计创建 core.users，包含 UUID 主键、username_key 生成列、账号唯一约束、用户名格式与角色 CHECK。没有实现注册、登录或插入真实用户。
- V2__create_knowledge_bases.sql 按已有设计创建 core.knowledge_bases，包含名称生成列、创建人外键、生命周期和删除元数据 CHECK，以及名称部分唯一索引和 created_by 普通索引。
- uq_live_kb_name 只对非 DELETED 记录约束 name_key 唯一；DELETING / DELETE_FAILED 继续占名。角色是否合法由 users CHECK 约束，某个创建人是否 ADMIN 仍需应用校验。
- 名称首尾控制字符/空格检查与现有 Java trim 规则对应；varchar(50/500) 与 char_length 按 PostgreSQL 字符计数。未通过修改 Domain 或 API 长度规则来迎合数据库。
- 新增 backend/spring-boot/day12-constraints.sql 作为手动验收脚本，放在迁移目录之外。事务内插入临时用户和知识库，验证指定唯一索引、外键，再回滚并检查剩余测试行。
- 解释 JDBC、JPA、Hibernate、Spring Data JPA、Flyway 的关系；说明 Domain record 与 JPA Entity 的职责、依赖和映射边界。本日未新增 Entity 或数据库 adapter。
- 收尾更新 README.md、docs/ARD.md 的本机数据库端口、docs/database-design.md 的实际实施状态，以及本日志和 Study_note/Day12.txt；已执行迁移文件内容保持原样。

主要新增或修改路径：

- backend/spring-boot/pom.xml
- backend/spring-boot/src/main/resources/application.yaml
- backend/spring-boot/src/main/resources/db/migration/V1__create_users.sql
- backend/spring-boot/src/main/resources/db/migration/V2__create_knowledge_bases.sql
- backend/spring-boot/day12-constraints.sql
- infrastructure/docker-compose.yml
- README.md、docs/ARD.md、docs/database-design.md、docs/daily-log.md、Study_note/Day12.txt

### 验收证据与边界

用户完成 ./mvnw test 后请求收尾。导师核对当前七份 Surefire 报告：71 tests，0 failures，0 errors，0 skipped。应用和测试代码在核对后未修改，收尾仅更新文档，没有为相同代码重复运行全量测试。

| 测试类 | 数量 | 本次含义 |
| --- | --- | --- |
| ApplicationTests | 1 | 真实数据库连接、Flyway 初始化和 JPA 上下文加载 |
| GlobalExceptionHandlerTest | 4 | 已有异常转换 |
| KnowledgeBaseTest | 9 | 已有领域规则和码点边界 |
| CreateKnowledgeBaseRequestTest | 9 | 已有创建请求校验 |
| KnowledgeBaseListRequestTest | 9 | 已有分页 DTO 校验 |
| KnowledgeBaseServiceTest | 15 | 创建与演示查询用例，仍基于内存/Mock Repository |
| KnowledgeBaseControllerTest | 24 | POST/GET 参数、响应与拒绝分支，Service 为 mock |
| 合计 | 71 | 全部通过；SQL 验收不计入这 71 项 |

数据库收尾查询确认 core 中只有 users、knowledge_bases、flyway_schema_history 三张表。迁移历史包含一条 SCHEMA 创建记录，以及下面两条成功的 SQL 记录：

| version | description | checksum | success |
| --- | --- | --- | --- |
| 1 | create users | 460812723 | t |
| 2 | create knowledge bases | -949879651 | t |

重复上下文启动后，SQL 迁移仍只有两条，校验和及 installed_on 与前次核对保持一致。实际表结构包含 fk_kb_created_by、ck_kb_lifecycle、ck_kb_live_name、ck_kb_deletion_metadata、uq_live_kb_name、idx_kb_created_by，与 V2 一致。

导师实际执行 day12-constraints.sql，用户随后也执行并反馈 PASS。脚本检查重名被指定的 uq_live_kb_name 拒绝、缺失创建人被指定的 fk_kb_created_by 拒绝；如果未拒绝或由其他约束拒绝，会执行失败。显示名称保留原大小写，name_key 自动生成小写。事务以 ROLLBACK 结束，脚本剩余测试行为 0/0；收尾时再查询，两张业务表总行数也均为 0。

已检查其余 CHECK 的实际定义，但本日 SQL 脚本没有逐一触发所有 CHECK、生命周期转换、Unicode 长度边界、删除后名称复用或并发冲突；不把结构核对描述为这些分支已完成行为验收。POST/GET 仍调用内存 Repository，尚未证明 API 写入 PostgreSQL 或重启后 API 数据仍存在。

### 遇到的问题与定位方式

1. Docker Desktop 引擎未运行：先启动 Docker Desktop，再使用现有 Compose 服务，无需重装环境。
2. 错误摘要只有 Failed to load ApplicationContext：读取 Surefire 报告的最底层 Caused by，并对照目标容器日志，不依赖 Maven 最后的通用汇总猜原因。
3. 5332 被另一容器 postgres-spring-boot 占用：其日志出现 Role "MicroJason" does not exist。项目容器没有正确发布该端口，将本项目 Windows 入口改为 5333 后，请求到达 enterprise-postgres。端口能建立 TCP 连接不代表连接到了正确实例。
4. 改端口后仍密码认证失败：配置中的密码经容器 TCP 认证可用，Compose 与项目容器的配置密码一致；将本地解析出的密码注入测试后，ApplicationTests 通过。采用从 Compose 解析配置的 Git Bash 加载方式，避免手动输入差异；没有重置数据库密码。
5. V1 最初位于 resources/dp/migration，测试通过但迁移历史没有 version=1：将目录改为 db/migration，与配置一致后执行成功。BUILD SUCCESS 不单独证明迁移被扫描和执行。

### 密码与启动约定

infrastructure/.env 存在且被 Git 忽略。Compose 读取该文件，但 Spring 不自动读取；Git Bash 的 export 只传给当前终端的子进程，已经打开的 IDEA 不会自动继承。IDEA 直接启动时需要在本地 Run Configuration 中提供连接环境变量。

README 与 Day12 笔记保留已验证的加载命令：先 unset DB_PASSWORD，避免旧终端值覆盖 .env，再用 docker compose config --format json 经 Python 提取 POSTGRES_PASSWORD，将输出接收到变量并 export，成功后才执行 Maven。命令不将密码输出到终端，也不把密码字面值写入 Bash 历史。不要把完整 Compose 配置或密码粘贴进聊天；修改 .env 不会修改已初始化数据库卷里的账号密码。

### 尚未实现与 Day 13 接入点

- KnowledgeBase 的 Domain record 保持原样；没有 @Entity / JpaRepository / PostgreSQL adapter，实际知识库 API 仍使用 InMemoryKnowledgeBaseRepository，接口创建的数据重启会丢失。
- Day 13 新增 Persistence Entity 并映射 Domain。读库时保留原始 ID、状态和时间，不能调用生成新 ID/时间的 KnowledgeBase.create() 来还原已有记录。
- 当前 users 表为空，而 Controller 使用固定演示创建人 UUID。替换数据库 adapter 前需明确演示用户初始化方式，使 created_by 外键可满足；不能将约束验收中的临时密码标记当作真实可登录账号，也不能用移除外键绕过这个依赖。
- adapter 中需要处理 generated name_key、deleted_at/删除元数据、非 DELETED 重名范围和 createdAt DESC / id ASC 排序。现有 Java equalsIgnoreCase 与数据库 lower(... COLLATE "C") 对非 ASCII 字符的行为可能不同，查询与最终唯一性应服从既有数据库契约。
- 数据库唯一约束只证明拒绝冲突；事务边界、并发时的唯一约束冲突转换为 KnowledgeBaseNameConflictException / HTTP 409、重启后 API 仍可查询，属于 Day 13。
- 迁移连接仍使用现有本地初始化账号；完整设计中的迁移账号与 Spring/FastAPI 最小权限运行账号、其他 core/ai 表、View、触发器和 Session 表尚未实施。
- vector 扩展已在此前本地环境启用；V1/V2 没有建立完整 pgvector/AI 迁移流程，本日不代表向量检索已接入。
- ApplicationTests 现在依赖运行中的本地 PostgreSQL 和环境变量，尚未建立隔离测试数据库或测试容器配置。
- Day 11 已记录的 JSON 协议分支、分页空值/重复值、requestId 来源和格式、Cache-Control: no-store 等待办继续保留。

### Git 与学习状态

Day 12 收尾时尚未提交的实现、验收文件和文档，后续已一起提交在 28ccc77 中。其提交消息提到接口持久化，但该提交的实际范围仍是连接与迁移基础；真正的 PostgreSQL adapter 和重启验收在 Day 13 的 931cbec 中完成。已执行 V1/V2 不为排版或重命名去修改内容。

用户逐步完成依赖、连接配置、迁移与验收，曾询问密码的安全查看方式，已讲解本地 .env 与环境变量。JDBC/JPA 分工、外键、部分唯一索引、事务回滚、Flyway 校验和、Domain/Entity 区别均已解释，但未独立口述验收，不根据 PASS 直接认定已能独立实现。

下一步按原路线进入 Day 13：从实际 Entity / Repository 起点核对，先做最小映射与测试，再逐步接入数据库 adapter、事务、冲突转换和真实 HTTP 持久化验收。继续使用 IDEA、Git Bash 和 .http，一次推进一个可验证步骤。

## Day 13 — 数据库 Repository 实现

日期：2026-11-26（Asia/Shanghai）。状态：已验收；实现提交 931cbec。

### 完成的任务

- 新增 KnowledgeBaseEntity，映射 core.knowledge_bases；name_key 为数据库生成的只读列，生命周期按字符串存储，删除元数据保持与既有表结构对应。
- fromNew() 仅接收 ACTIVE 新建对象；toDomain() 保留已有 UUID、状态和时间，不调用生成新身份的 create()。
- JpaKnowledgeBaseRepository 使用原生 EXISTS 查询，范围为非 DELETED，名称按数据库 lower(... COLLATE "C") 比较；adapter 对输入名称 trim。
- PostgresKnowledgeBaseRepository 实现领域 Repository 接口；查询事务只读，save() 为写事务。saveAndFlush() 在 try 内触发数据库检查；只将 SQLState 23505 且索引名 uq_live_kb_name 的错误转换为重名业务异常，其他完整性错误继续抛出。
- InMemoryKnowledgeBaseRepository 移除 @Repository，仅供手动构造的单元测试使用；Spring 实际业务链路使用 PostgreSQL adapter。
- day13-demo-user.sql 在迁移目录外显式初始化固定演示创建人；不修改 schema，已有相同期望身份可重复执行。password_hash 使用禁止登录的演示标记，不是有效密码哈希；认证接入时需采用正式初始化方式。
- 新增 day13-persistence.http；每次生成新名称，保存读库快照，按“创建/查询 → SQL 核对 → 重启 Spring → 查询/重名”执行。

### 文件与模块

- infrastructure.persistence：KnowledgeBaseEntity.java、JpaKnowledgeBaseRepository.java、PostgresKnowledgeBaseRepository.java；调整 InMemoryKnowledgeBaseRepository.java。
- 同包新增 KnowledgeBaseEntityTest.java、JpaKnowledgeBaseRepositoryTest.java、PostgresKnowledgeBaseRepositoryTest.java。
- backend/spring-boot/day13-demo-user.sql、backend/spring-boot/day13-persistence.http。
- 收尾文档：README.md、docs/database-design.md、本日志、Study_note/Day13.txt；更正 Day 10/11 历史脚本的运行前提和 Day 11/12 的实际提交状态。

### 测试与真实接口证据

导师于 2026-11-26 通过 Git Bash 执行 ./mvnw test：BUILD SUCCESS，85 tests，0 failures，0 errors，0 skipped。核对十份 Surefire XML 报告，数量一致。后续只更新文档和手动验收文件，没有改动 Java、依赖或迁移；Day 14 入口沿用此基线。

| 测试类 | 数量 | 主要证据 |
| --- | --- | --- |
| ApplicationTests | 1 | 应用上下文、真实数据库连接与迁移校验 |
| GlobalExceptionHandlerTest | 4 | 已有异常映射与通用内部错误 |
| KnowledgeBaseTest | 9 | 领域规则与 Unicode 码点长度 |
| CreateKnowledgeBaseRequestTest | 9 | 创建请求边界 |
| KnowledgeBaseListRequestTest | 9 | 查询 DTO 默认值与边界 |
| KnowledgeBaseServiceTest | 15 | 创建、状态过滤、稳定排序与内存分页 |
| KnowledgeBaseControllerTest | 24 | HTTP 绑定与响应；Service 为 mock |
| KnowledgeBaseEntityTest | 5 | 新建映射、还原字段、拒绝非 ACTIVE 新建 |
| JpaKnowledgeBaseRepositoryTest | 6 | flush/clear 后读库、生成列、四种生命周期查重范围 |
| PostgresKnowledgeBaseRepositoryTest | 3 | 领域接口写入/读取、数据库重名转换、外键失败保留 |
| 合计 | 85 | 全部通过 |

用户执行 day13-persistence.http 的四条请求，确认断言全部通过：

| 请求 | 结果 |
| --- | --- |
| 创建 | 201 / SUCCESS；规范化名称、原简介、演示创建人、Location 与 requestId 正确 |
| 重启前 GET | 200；按新 ID 找到记录，保存七个业务字段和 total |
| 仅重启 Spring 后 GET | 200；相同 ID、字段和时间均与此前读库结果相同 |
| 重启后大小写不同且带首尾空格的同名创建 | 409 / KNOWLEDGE_BASE_NAME_CONFLICT，data=null |

真实 HTTP 结果来自用户反馈，未保存响应导出。导师直接查询 PostgreSQL，确认两条本轮演示记录持久存在；V1/V2 迁移成功。完整测试后，随机测试用户与测试知识库均为零，原有演示记录保留。

上述 HTTP 重名是顺序请求；真实数据库约束转换由绕过 Service 查重的 adapter 集成测试补充，不据此声称完成并发压力测试。事务型集成测试最终回滚，持久提交与 Spring 重启证据来自实际 HTTP 和 SQL 验收。

### 问题与边界

- 已有 Instant 可带纳秒，PostgreSQL 时间精度为微秒。集成测试使用可精确往返的微秒夹具；HTTP 比较重启前后两次 GET 的读库快照，避免将 POST 内存时间与数据库时间的精度差当作重启丢失。
- 数据库 adapter 已生效，但 findAll() 仍读出全部记录，由 Java 过滤、排序与分页；数据库分页待后续评估。
- fromNew()/save() 目前用于新建；更新、删除及删除元数据维护尚未实现，不复用新建映射覆盖这些状态。
- Service 的预查重与保存不是一个原子操作；最终唯一性由数据库索引保证。异常转换精确匹配已知唯一索引，外键失败不会伪装为 409。
- 仍使用固定演示身份；认证、ADMIN 角色校验、员工授权过滤、数据库最小权限账号尚未接入。
- 未知 JSON 字段、损坏 JSON/类型错误、空值/重复 Query、requestId 来源限制、Cache-Control 等既有契约差异进入 Day 14 验收记录，不把已通过的路径扩大为完整 API 合规。
- 旧 Day 10/11 脚本使用固定名称，Day 11 还要求空知识库表；Spring 重启现在不会恢复这些条件。当前使用 Day 13/14 的验收文件。

### Git、学习状态与下一步

已核对用户提交 931cbec（Day13 完成数据库持久化接口验收），包含九个实现/测试/验收文件。Day 13 收尾与 Day 14 入口文档在该提交之后补充，等待后续文档提交。

理解复盘按用户要求推迟到整个项目完成后；当前不要求口述，不阻断项目推进，也不声称已独立掌握全部实现。

下一步直接进入 Day 14：核对分层、异常、校验、迁移、持久化、测试和 README，交付演示、测试记录、技术债与模块说明。

## Day 14 — 后端阶段验收（已验收）

日期：2026-11-26（Asia/Shanghai）。入口实现基线：931cbec。

已完成实际生产代码的分层与 Repository 装配核对；已建立 [后端阶段验收记录](day14-backend-acceptance.md)，列明模块职责、85 项测试基线、既有证据和待处理差异。本次未增加业务框架或调整既定路线。

用户已执行 backend/spring-boot/day14-boundaries.http 四条请求，确认空白名称、分页越界、未知查询参数和非空 GET Body 均按 422 / 422 / 422 / 400 返回，错误信封及 requestId 断言全部通过。证据来自用户反馈，没有保存响应导出。

导师此前在运行中的 Spring 上复现截断 JSON、name 为对象时都错误返回 500 / INTERNAL_ERROR。用户已在全局异常处理器中区分 JSON 格式损坏的 400 / INVALID_JSON 与字段结构不匹配的 422 / VALIDATION_ERROR，并添加两项 Controller 解析回归测试。核对实际代码及最新 Surefire 报告：KnowledgeBaseControllerTest 26 项、GlobalExceptionHandlerTest 4 项，定向回归合计 30 项，0 failures / errors / skipped，验证解析失败不调用 Service。

用户已执行 backend/spring-boot/day14-json.http，确认截断 JSON 返回 400 / INVALID_JSON、name 为对象返回 422 / VALIDATION_ERROR；固定文案、data=null、requestId 一致性断言全部通过。真实请求结果来自用户反馈，没有保存响应导出。当时以 85 项旧基线与定向回归记录该小步；本阶段最终完整回归为下文的 100 项。

拒绝未知 JSON 字段：导师此前使用现有名称加 role 字段 POST，观察到 409 / KNOWLEDGE_BASE_NAME_CONFLICT，确认字段被忽略后进入业务查重，未创建新记录。用户已启用 spring.jackson.deserialization.fail-on-unknown-properties，并新增加载真实应用配置的 KnowledgeBaseJsonIntegrationTest。导师核对 YAML、Java 和最新 Surefire 报告：role、createdBy、lifecycle 三项测试通过，0 failures / errors / skipped，验证 422、指定解析异常及不调用 Service。已有 standalone Controller 测试的装配方式保持原样。

用户已执行 backend/spring-boot/day14-unknown-fields.http 三条真实请求，确认 role、createdBy、lifecycle 均返回 422 / VALIDATION_ERROR，固定文案、data=null 与 requestId 一致性断言全部通过。结果来自用户反馈，没有保存响应导出。

严格检查 JSON 字符串字段的类型：导师此前用现有名称和数字 description 向当前服务 POST，观察到 409，确认数字被转成字符串后进入业务查重，未新增记录。用户已新增 JacksonConfiguration，通过 JsonMapperBuilderCustomizer 对 LogicalType.Textual 的 Integer、Float、Boolean 输入显式设置 CoercionAction.Fail；现有应用集成测试新增六项边界。导师核对实际代码和最新 Surefire 报告：该类九项测试全部通过，0 failures / errors / skipped，包含三项未知字段与六项字符串类型边界，验证解析失败不调用 Service。

用户已执行 backend/spring-boot/day14-json-types.http 六条真实请求，确认 name/description 接收整数、小数、布尔值均返回 422 / VALIDATION_ERROR，固定文案、data=null 与 requestId 一致性断言全部通过。结果来自用户反馈，没有保存响应导出；本阶段最终完整回归结果见下文收尾记录。

对齐响应头契约：导师此前以自定义 X-Request-Id 请求知识库列表的 200/422 分支，均发现 Cache-Control 缺失且客户端 ID 被沿用。用户已在 RequestIdFilter 中每次生成 UUID、写入响应头和 request attribute、设置 Cache-Control: no-store，并保留后续处理链；在已有 GlobalExceptionHandlerTest 中对 200/409/422/500 四个分支增加响应测试。导师核对实际代码和最新 Surefire 报告：该类八项测试通过，0 failures / errors / skipped。

用户已执行 backend/spring-boot/day14-response-headers.http 两条真实 GET 请求，确认 200/422 的 no-store、服务端 UUID、响应头/JSON 一致性全部通过；相同客户端 ID 的两次请求得到不同的服务端 ID。结果来自用户反馈，没有保存响应导出。

### 最终收尾（2026-11-26）

- 导师通过 Git Bash 执行 ./mvnw test：BUILD SUCCESS，100 tests，0 failures，0 errors，0 skipped；核对十一份 Surefire XML，数量一致。
- 相比 Day 13 的 85 项，新增 Controller JSON 解析 2 项、JSON 应用配置集成 9 项、响应头 4 项。最终统计见 [阶段验收记录](day14-backend-acceptance.md)，不将各次定向回归重复计数。
- 用户本阶段完成五个 .http 文件共 17 条请求及断言：4 条请求边界、2 条 JSON 格式/结构、3 条未知字段、6 条类型错误、2 条响应头。
- 导师再次 GET 核对当前服务的正常/非法分页响应均设置 no-store、生成服务端 ID，且 JSON/头一致；V1/V2 校验和保持原样，业务库保留两条 Day 13 演示记录，测试用户/知识库残留为零。
- 创建/查询/重启演示继续沿用已验收的 day13-persistence.http，并已在 README 说明执行顺序；未在本轮虚构新的 POST 或人工重启验收。
- 已交付模块职责、分层核对、测试与手动验收证据、启动/演示说明和技术债清单。Query 空值/重复值、认证授权、数据库分页、更新删除、隔离测试环境等保留明确边界，不宣称全部 API 或完整数据库设计已实现。
- 最近已提交基线仍为 931cbec；Day 13 补充文档、Day 14 实现/测试/验收文件待用户后续提交。部分新增 Java 的暂存内容仍是 IDEA 初建版本，最终提交前重新 git add 并审阅 git diff --cached。
- 按用户要求，理解复盘留到整个项目完成后，直接进入 Day 15。

## Day 15 — 项目结构和 React 基础（进行中）

日期：2026-11-26（Asia/Shanghai）。

已核对现有 frontend：React + TypeScript + Vite，main.tsx 渲染 App.tsx，已有基础首页和 CSS；不是空项目，不重新运行脚手架或安装已存在依赖。页面中的端口文字为静态介绍，Get Started 按钮尚无业务交互；尚无路由、API Client 或知识库业务 UI。

导师运行 npm run build（包含 tsc -b）和 npm run lint，均通过，作为前端接续基线；未改动前端源代码或依赖。此结果不等于浏览器端交互或真实业务联调已完成。

当前第一小步：由用户在 frontend/src 中建立 pages、components、api、types、hooks 目录，并为暂时空目录添加 .gitkeep；随后运行现有 Vite 首页，确认结构准备未影响页面。目录与浏览器验证待用户反馈，后续再逐步实践组件、props、state、事件和 JSX。前端负责展示和交互，权限由后端执行；保持既定 Day 16 路由、Day 17 API Client 等安排。
