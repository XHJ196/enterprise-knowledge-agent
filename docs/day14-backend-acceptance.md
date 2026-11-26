# Day 14：后端阶段验收记录

日期：2026-11-26（Asia/Shanghai）。状态：本阶段已验收；按既定路线进入 Day 15。

本阶段按既有手册检查分层、异常响应、校验、迁移、持久化、单元/集成测试和 README，交付知识库创建与查询演示、测试记录、技术债清单和模块说明。理解复盘依用户要求留到整个项目完成后。

## 1. 最终验收结果

- 已提交入口基线：931cbec（Day13 完成数据库持久化接口验收）；本阶段修改及收尾文档尚未提交。
- 最终完整回归：100 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS；2026-11-26 18:18（Asia/Shanghai）导师通过 Git Bash 运行并核对十一份 Surefire XML。
- Day 13 原有 85 项保持通过，Day 14 增加 Controller JSON 解析 2 项、应用 JSON 配置集成 9 项、响应头 4 项，共 15 项。
- 用户完成本阶段五个 HTTP 文件的全部 17 条请求及断言：请求边界 4、JSON 读取异常 2、未知字段 3、字段类型 6、响应头 2。真实接口结果来自用户反馈，无响应导出。
- 导师另外 GET 核对当前服务的 200/422 均返回 no-store、生成新 requestId 且 JSON/头一致；数据库 V1/V2 校验和保持原样，随机测试数据回滚，原有两条持久化演示记录保留。
- 当前数据库入口为 127.0.0.1:5333，数据库 enterprise_knowledge，业务 schema 为 core；Spring 运行依赖本地数据库及 DB_PASSWORD，加载方法见 [README](../README.md)。

| 测试类 | 数量 |
| --- | --- |
| ApplicationTests | 1 |
| GlobalExceptionHandlerTest | 8 |
| KnowledgeBaseServiceTest | 15 |
| KnowledgeBaseTest | 9 |
| JpaKnowledgeBaseRepositoryTest | 6 |
| KnowledgeBaseEntityTest | 5 |
| PostgresKnowledgeBaseRepositoryTest | 3 |
| CreateKnowledgeBaseRequestTest | 9 |
| KnowledgeBaseControllerTest | 26 |
| KnowledgeBaseJsonIntegrationTest | 9 |
| KnowledgeBaseListRequestTest | 9 |
| 合计 | 100 |

下文保留各小步的诊断、定向测试和手动验收过程；早期的 85 项基线、30 项或 9 项定向结果不能与本表重复相加。

## 2. 模块职责与依赖核对

生产代码位于 backend/spring-boot/src/main/java/com/enterprise/knowledge。

| 模块 | 当前职责 | 本次核对 |
| --- | --- | --- |
| knowledgebase.interfaces.rest | 请求绑定/校验、HTTP 状态、Location、响应 DTO 映射 | Controller 调用 Service，不直接访问 JPA、SQL 或具体数据库 adapter |
| knowledgebase.application | 创建用例、预查重、演示列表的过滤/排序/分页 | 依赖领域 Repository 接口，不导入 infrastructure、HTTP DTO 或 JPA Entity；保留 Spring @Service |
| knowledgebase.domain | KnowledgeBase record、业务规则、生命周期、Repository 接口 | 生产代码不依赖 Spring、JPA、Controller 或数据库实现 |
| knowledgebase.infrastructure.persistence | Entity 映射、Spring Data JPA、PostgreSQL adapter、约束翻译和事务 | 实现领域 Repository；查询只读，保存写事务；仅已知名称唯一索引转换为重名异常 |
| InMemoryKnowledgeBaseRepository | 手动构造的单元测试存储 | 无 @Repository，不参与运行时 Spring Repository 装配 |
| common | ApiResponse、RequestIdFilter、统一异常处理、Jackson 字符串类型配置 | 过滤器继续调用后续处理链；已实现 400/409/422/500 的指定分支；JSON 类型配置由应用集成测试覆盖 |
| resources/db/migration | Flyway V1/V2 结构与约束 | ddl-auto=validate，sql.init.mode=never，Flyway 为唯一结构迁移方式 |

测试中的 Service 单元测试可以显式构造内存 adapter；这不等于生产 application 层依赖具体 infrastructure。实体转换返回领域对象，HTTP 层再映射响应 DTO。

核对结论：当前创建/查询纵向切片的生产依赖方向符合既定分层。以上是实际源码审阅结果，尚未建立自动化架构约束测试。

## 3. 已有证据及其范围

| 项目 | 已有证据 | 证据边界 |
| --- | --- | --- |
| 请求/领域规则 | DTO、Domain、Service、Controller 单元测试 | Controller 测试的 Service 为 mock，不单独证明真实数据库链路 |
| 数据库映射 | JPA 集成测试 flush 后 clear，再读库对比字段与生成列 | 测试事务回滚；时间夹具采用微秒精度 |
| 唯一约束转换 | adapter 直接保存重名，断言业务异常；缺失创建人仍是完整性异常 | 绕过 Service 查重验证约束翻译，不是并发压力测试 |
| 迁移 | Day 12/13 SQL 核对 V1/V2 成功、结构及索引，后续启动校验通过 | 仅实施 users/knowledge_bases；完整设计中的其他表没有因此完成 |
| 提交与重启持久化 | 用户完成 day13-persistence.http 四条断言，并按 SQL 核对 ID | 真实 HTTP 证据来自用户反馈，无响应导出；导师另外核对数据库中的演示记录 |
| 测试数据清理 | 完整回归后 SQL 核对随机测试用户/知识库均为零，演示记录保留 | 未清空业务表或删除演示记录 |

## 4. 真实 HTTP 请求边界（已通过）

文件：[day14-boundaries.http](../backend/spring-boot/day14-boundaries.http)。在已连接当前 PostgreSQL 的 Spring Boot 上，使用 IDEA HTTP Client 依次运行 1–4。请求不依赖空表、固定知识库名称或此前 HTTP Client 变量，适用于当前已有演示数据的数据库。

| 请求 | 场景 | HTTP / code | 执行状态 |
| --- | --- | --- | --- |
| 1 | POST 空白名称 | 422 / VALIDATION_ERROR | 通过，用户反馈 |
| 2 | GET pageSize=101 | 422 / VALIDATION_ERROR | 通过，用户反馈 |
| 3 | GET 额外 size 参数 | 422 / VALIDATION_ERROR | 通过，用户反馈 |
| 4 | GET 非空 JSON Body | 400 / INVALID_REQUEST_BODY | 通过，用户反馈 |

每条请求检查 message 非空、data=null、JSON requestId 非空且与 X-Request-Id 一致。用户已确认四条请求及断言全部通过，未保存响应导出。

### 4.1 JSON 读取异常归类与定向回归

导师在当前运行中的 Spring 上发送两条不会形成合法创建对象的诊断请求：截断的 JSON `{"name":`，以及合法 JSON 但字段结构错误的 `{"name":{}}`。两者实际都返回 500 / INTERNAL_ERROR，data=null，requestId 与响应头一致；与 API 的 400 / INVALID_JSON、422 / VALIDATION_ERROR 存在差异。

已核对 Spring Framework 7.0.10 将解析异常作为 HttpMessageNotReadableException 的直接 cause；Jackson 3.1.7 分别产生 UnexpectedEndOfInputException、MismatchedInputException。用户已在既有 GlobalExceptionHandler 中增加处理分支，并在 KnowledgeBaseControllerTest 中补两项请求解析测试，验证错误归类且不调用 Service。导师核对实际代码与两份最新报告：Controller 26 项、GlobalExceptionHandler 4 项，合计 30 项，0 failures / errors / skipped。该测试使用 standalone MockMvc，Service 为 mock；其测试证据与第 4.2 节真实 HTTP 结果分别记录。

该小步先通过定向回归，未知字段拒绝及标量转换随后在第 4.3–4.6 节补齐；全部改动最终由第 1 节的 100 项完整回归覆盖。

### 4.2 JSON 错误的真实 HTTP 验收（已通过）

文件：[day14-json.http](../backend/spring-boot/day14-json.http)。先保存代码并重启 Spring，再在 IDEA HTTP Client 执行两条请求。第一条请求体故意截断为 `{"name":`，保持原样。

| 请求 | 场景 | HTTP / code | 执行状态 |
| --- | --- | --- | --- |
| 1 | 截断 JSON | 400 / INVALID_JSON | 通过，用户反馈 |
| 2 | name 为对象 | 422 / VALIDATION_ERROR | 通过，用户反馈 |

两条均检查固定 message、data=null、requestId 非空且与 X-Request-Id 一致。用户确认两条请求的断言全部通过，未保存响应导出。文件可重复运行，不依赖现有业务记录或 HTTP Client 全局变量。

### 4.3 拒绝未知 JSON 字段的配置与集成测试（已通过）

导师先通过 GET 读取一个现有知识库名称，再携带该名称与额外 role 字段 POST；实际返回 409 / KNOWLEDGE_BASE_NAME_CONFLICT，说明未知字段被忽略后进入了业务查重，未新增记录。API 要求未知请求字段在进入用例前返回 422 / VALIDATION_ERROR。

用户已在 application.yaml 中启用 spring.jackson.deserialization.fail-on-unknown-properties，并新增 KnowledgeBaseJsonIntegrationTest：使用 @SpringBootTest、@AutoConfigureMockMvc 和 @MockitoBean 加载真实应用 JSON 配置，对 role、createdBy、lifecycle 三个未知字段参数化验收，检查 422 信封、UnrecognizedPropertyException 及不调用 Service。导师核对实际代码、YAML 和最新 Surefire 报告，3 tests，0 failures / errors / skipped。现有 standalone MockMvc 测试不读取 application.yaml，此处应用集成测试补充覆盖配置是否实际生效。

Jackson 3.1.7 的 UnrecognizedPropertyException 属于 MismatchedInputException，复用现有异常处理分支即可。测试需要当前 PostgreSQL 与数据库环境变量；Service 为 mock，测试请求不会创建知识库。该小步先确认集成测试，最终完整回归结果见第 1 节。

### 4.4 未知字段的真实 HTTP 验收（已通过）

文件：[day14-unknown-fields.http](../backend/spring-boot/day14-unknown-fields.http)。启动加载最新配置的 Spring Boot 后，在 IDEA HTTP Client 运行三条请求。

| 请求 | 场景 | HTTP / code | 执行状态 |
| --- | --- | --- | --- |
| 1 | 附加 role | 422 / VALIDATION_ERROR | 通过，用户反馈 |
| 2 | 附加 createdBy | 422 / VALIDATION_ERROR | 通过，用户反馈 |
| 3 | 附加 lifecycle | 422 / VALIDATION_ERROR | 通过，用户反馈 |

三条均检查固定 message、data=null、requestId 非空且与 X-Request-Id 一致。用户确认三条请求断言全部通过，未保存响应导出；与 MockMvc 集成测试证据分别记录。

### 4.5 JSON 字符串字段的配置与集成测试（已通过）

导师使用现有知识库名称和数字 description=123 向当前 Spring POST，实际进入业务查重并返回 409，说明该数字被作为字符串接收；未创建新记录。创建请求声明 name/description 为字符串，此行为需要归一为 422 / VALIDATION_ERROR。

已用当前 Jackson 3.1.7 验证：默认会将整数、小数、布尔值转换成 String，单独禁用 ALLOW_COERCION_OF_SCALARS 仍不能阻止这些到 String 的转换。使用 LogicalType.Textual 的 CoercionConfig，分别将 Integer、Float、Boolean 设置为 CoercionAction.Fail，可对六种字段/输入组合产生 MismatchedInputException，并仍接受合法字符串、null 或省略 description。上述是本地 Jackson 行为核对，尚不等同于应用配置已实现。

用户已新增 common.config.JacksonConfiguration，通过 Spring Boot 4.1.2 的 JsonMapperBuilderCustomizer 设置这些转换规则，保留现有自动配置与 YAML；在 KnowledgeBaseJsonIntegrationTest 中增加六项请求类型回归，检查 422、指定字段映射异常、data=null 和不调用 Service。导师核对实际配置类、测试代码和最新 Surefire 报告：9 tests（已有三项未知字段 + 六项类型边界），0 failures / errors / skipped。本次应用配置与集成测试已通过，真实 HTTP 及完整回归另行记录。

### 4.6 字符串类型错误的真实 HTTP 验收（已通过）

文件：[day14-json-types.http](../backend/spring-boot/day14-json-types.http)。启动加载最新 JacksonConfiguration 的 Spring Boot，在 IDEA HTTP Client 运行六条请求。

| 请求 | 场景 | HTTP / code | 执行状态 |
| --- | --- | --- | --- |
| 1 | name 为整数 | 422 / VALIDATION_ERROR | 通过，用户反馈 |
| 2 | name 为小数 | 422 / VALIDATION_ERROR | 通过，用户反馈 |
| 3 | name 为布尔值 | 422 / VALIDATION_ERROR | 通过，用户反馈 |
| 4 | description 为整数 | 422 / VALIDATION_ERROR | 通过，用户反馈 |
| 5 | description 为小数 | 422 / VALIDATION_ERROR | 通过，用户反馈 |
| 6 | description 为布尔值 | 422 / VALIDATION_ERROR | 通过，用户反馈 |

六条均检查固定 message、data=null、requestId 非空且与 X-Request-Id 一致。用户确认六条请求的断言全部通过，未保存响应导出。

### 4.7 响应头与 requestId 来源的修复与测试（已通过）

导师向当前知识库 GET 接口发送客户端头 X-Request-Id: client-supplied-day14，分别取得 200 / SUCCESS 与 422 / VALIDATION_ERROR。两者 Cache-Control 均缺失，JSON 和响应头的 requestId 均原样沿用客户端值，与 API 第 1.1 节的 Spring 生成 requestId、公开响应 no-store 约定存在差异。

用户已修改已有 RequestIdFilter：每次外部请求生成 UUID，不使用客户端头作为本次 ID；在调用处理链前设置 X-Request-Id、Cache-Control: no-store 及同值 request attribute，保留 filterChain.doFilter。导师核对实际过滤器和测试代码。此处覆盖当前同步 Servlet 接口；未来异步流、错误分派和内部链路的追踪策略仍需在对应阶段验证。

已有 GlobalExceptionHandlerTest 的测试 Controller 增加成功响应，并以 200/409/422/500 四个分支验证 no-store、忽略客户端给定 UUID、生成规范 UUID、响应头与 JSON 同值。导师核对最新 Surefire 报告：8 tests，0 failures / errors / skipped，其中原四项加四项响应头测试。测试使用 standalone MockMvc 和测试 Controller，真实运行服务及当前版本的完整回归另行验收。

### 4.8 真实 HTTP 响应头验收（已通过）

文件：[day14-response-headers.http](../backend/spring-boot/day14-response-headers.http)。启动加载最新过滤器的 Spring 后，在 IDEA HTTP Client 依次运行两条 GET；两次均传入同一个合法的客户端 UUID。

| 请求 | 场景 | 预期 | 执行状态 |
| --- | --- | --- | --- |
| 1 | 正常查询 | 200 / SUCCESS；no-store；生成新 UUID；JSON/头同值 | 通过，用户反馈 |
| 2 | 分页越界 | 422 / VALIDATION_ERROR；no-store；生成另一个新 UUID；JSON/头同值 | 通过，用户反馈 |

请求 1 保存服务端 ID，请求 2 验证不同请求的 ID 不同；两者都不能沿用传入的客户端 ID。用户确认两条请求的全部断言通过，未保存响应导出。导师另外请求运行中的服务，核对 200/422 响应均符合上述响应头规则。

## 5. 契约差异与技术债

| 项目 | 当前状态 | 后续处理边界 |
| --- | --- | --- |
| 损坏 JSON、JSON 字段类型错误、未知 JSON 字段 | 两项读取异常回归、九项应用 JSON 集成测试及对应的 2/3/6 条真实 HTTP 均已通过 | 记录已覆盖的输入范围，不将局部回归扩大为全部 JSON 契约已满足 |
| requestId、Cache-Control | 当前同步接口每请求生成 UUID 并设置 no-store；四项响应测试、真实 200/422 和完整回归已通过 | 未来内部链路、异步流及 Servlet 错误分派时再验证对应追踪和响应行为 |
| Query 空字符串/重复字段 | 策略和测试尚未补齐，属于公开保留的契约边界 | 后续先明确接口行为，再追加针对性验证；不扩展本阶段范围 |
| 认证、ADMIN 校验、员工授权 | 固定服务端演示创建人；尚无登录与授权实现 | 当前明确演示范围，后续按原有路线实现；不宣称管理员或员工权限已生效 |
| 演示密码与数据库运行账号 | demo user 使用不可登录标记；未分离最小权限数据库账号 | 认证/部署阶段采用正式初始化与权限配置 |
| 数据库分页 | findAll 后在 Java 中过滤、排序、分页 | 保留现有契约，数据规模需要时再下推数据库 |
| 保存与生命周期更新 | fromNew/save 仅覆盖创建，未实现更新/删除流程 | 更新时设计元数据维护，避免用新建映射覆盖已有删除状态 |
| 时间精度 | POST 返回值可能保留纳秒，PostgreSQL 存储微秒 | 当前重启验收比较 GET 读库快照；后续评估统一 API 时间精度 |
| 测试隔离与自动化范围 | 集成测试依赖本地数据库并回滚；真实 API 验收仍由 .http 手动执行 | 后续评估隔离数据库与真实整链路自动化，不追加无必要框架 |
| 旧验收脚本 | Day 10/11 固定名称，Day 11 假设初始空表 | 已更正运行说明；当前使用 Day 13/14 文件，重启不恢复历史脚本前提 |

## 6. 阶段交付进度

- [x] 分层、模块职责和 Repository 装配核对。
- [x] 固定 Day 13 入口基线并完成当前 100 项全量回归，说明单元/集成/手动证据范围。
- [x] 建立已知契约差异与技术债清单。
- [x] 完成第 4 节四条真实 HTTP 边界请求，用户确认全部断言通过。
- [x] 复现并处理本阶段需要补齐的 JSON/响应契约差异；五个 HTTP 文件共 17 条断言通过。
- [x] 核对最终 README 与可重复的知识库创建/查询演示，汇总最终验收证据。

可重复演示沿用 [day13-persistence.http](../backend/spring-boot/day13-persistence.http)：创建和查询 → SQL 核对 ID → 仅重启 Spring → 查询同一记录与拒绝重名，详细顺序见文件和 README。该演示的用户执行记录在 Day 13，最终数据库查询再次确认演示记录仍存在；本阶段没有再次声称执行过人工重启。

Day 14 按约定范围验收完成，技术债继续保留。Day 15 从已存在的 React + TypeScript 项目继续，不重复初始化环境，也不要求先完成口述复盘。
