# HANDOFF

> 下次加载本项目时先读这份文件，再读 `CLAUDE.md`。
> 状态：**已完成并全部验证通过**。最后一次验证 2026-09-10。

## 一、这个项目是什么

Apache Camel 入门教学工程 `camel-session-0to1`。目标是用一个能跑的 Spring Boot 应用，
把 Camel 的核心标签由浅入深讲清楚。`camel-context.xml` 通过 `@ImportResource` 接进 Spring Boot，
它只做装配；真正的路由按 API 拆在 `src/main/resources/camel/` 下的 8 个 `<routeContext>` /
`<restContext>` 片段文件里。

**`src/` 下的代码、XML、YAML 全部使用英文**（注释、Javadoc、日志、响应里的 message/hint）。

技术栈：Spring Boot 3.5.14 · Spring Cloud 2025.0.2（只引 BOM，未用具体 starter）·
Camel 4.18.2 · JDK 21 · Maven 3.9.16。
本地 Maven 仓库不在默认位置，实际是 `/Users/ethan/Desktop/workspace/repos`。

## 二、已交付内容

### 代码

| 文件 | 说明 |
|---|---|
| `pom.xml` | 三个 BOM + camel 各 starter + 链路追踪三件套（actuator / micrometer-tracing-bridge-brave / camel-observation-starter） |
| `src/main/java/com/example/camel/CamelSession0to1Application.java` | 入口，`@ImportResource` |
| `src/main/resources/camel-context.xml` | **装配入口**：`<import>` + `<camelContext>` + `<routeContextRef>`，不含业务路由 |
| `src/main/resources/camel/*.xml` | **教学核心**：8 个片段文件（rest-api / route-api1..6 / route-shared），共 16 条路由，含逐标签英文讲解注释 |
| `.idea/rest/API-requests.http` | IntelliJ HTTP Client 全量请求集，带断言；环境见同目录 `http-client.env.json` |
| `src/main/resources/application.yml` | 端口、日志格式（含 traceId）、`allow-circular-references`、mock 远程地址、`management.tracing.*` |
| `processor/` × 7 | `<process ref=...>` 引用的 Processor |
| `strategy/` × 2 | `<multicast>` / `<split>` 的 AggregationStrategy |
| `bean/RemoteCallService` | ProducerTemplate 两种用法 |
| `bean/MockRemoteApiController` | 模拟"外部系统"，让离线也能演示远程调用 |
| `test-apis.sh` | 一键回归 9 个用例 |

### 文档产物（`docs/`）

| 文件 | 说明 |
|---|---|
| `Camel-API6-复杂编排流程.pptx` | 4 页：封面 / 一页流程图 / 逐节点标签表 / 控制台轨迹 |
| `Camel-入门指南.pptx` | 18 页：全部知识点的入门级指南 |
| `api6-flow.html` | archify 生成的交互式流程图（深浅主题、可导出 PNG/SVG） |
| `api6-flow.workflow.json` | 上面 HTML 的源规格，改图改这个再 deliver |
| `slidekit.py` / `build_api6_deck.py` / `build_guide_deck.py` | PPT 生成脚本，可重复运行 |

## 三、六个 API 与验证结果

全部通过 `./test-apis.sh`（9 个用例）：

| API | 端点 | 演示标签 | 验证结果 |
|---|---|---|---|
| 1 | `POST /api/v1/orders/basic` | `<route> <from> <to> <process> <log>` | 200 `code=OK` |
| 2 | `POST /api/v1/orders/dispatch` | `<choice> <when> <otherwise> <simple> <jsonpath> <filter>` | 三种输入分别命中 when#1 / when#2 / otherwise；`amount<100` 时 `placed=false` 但仍 200 |
| 3 | `POST /api/v1/orders/quote` | ProducerTemplate + `<to uri="bean:">` | 远程汇率 7.24，本地路由算费成功 |
| 4 | `POST /api/v1/orders/check` | `<multicast>` + `aggregationStrategy` | 三路并行，`totalScore=270` |
| 5 | `POST /api/v1/orders/split` | `<split> <wireTap> <toD>` | 3 个 SKU 分别进 BOOK/FOOD/OTHER 三个仓 |
| 6 | `POST /api/v1/orders/fulfil` | 全部组合 | `amount=20000` → APPROVED + 3 条履约；`amount=90000` → REJECTED + 空履约，两者字段结构一致 |

## 四、构建过程中真实踩过的四个坑（都已修复，勿回退）

1. **XML 注释里出现 `--`** → `SAXParseException: The string "--" is not permitted within comments`。
   原来的对比表格分隔线 `|-----|` 全部换成了 `=`。
2. **`ProducerTemplate` 有两个 Bean** → `<camelContext>` 已自动注册名为 `template` 的 Bean，
   删掉了自建的 `CamelTemplateConfig`。
3. **循环依赖** → `camelSession0to1 ↔ *ComponentAutoConfiguration`。
   解法是 `RemoteCallService` 构造参数加 `@Lazy` **且** 保留
   `spring.main.allow-circular-references: true`（两者缺一不可，实测）。
4. **`camel.springboot.*` 开关不生效** → 自动装配已让位。`useMDCLogging` 等已改写到
   `<camelContext>` 属性上，`application.yml` 里留了说明注释。

## 四点五、链路追踪（2026-09-10 追加）

`spring-boot-starter-actuator` + `micrometer-tracing-bridge-brave` + `camel-observation-starter`
三个依赖接上后，控制台每行日志的第二个方括号是 traceId，一次请求内全线程一致
（multicast 三条并行子路由、wireTap 的 audit-log、split 的 item-* 都对得上），
API-3 调 `MockRemoteApiController` 时 traceId 也会随 W3C `traceparent` 头跨进去。

这里唯一的坑：**Camel 与 Brave 写的是两组不同的 MDC key**——Camel 的 `ActiveSpanManager` 写
`trace_id`/`span_id`（覆盖 Camel 线程），Brave 的 `MDCScopeDecorator` 写 `traceId`/`spanId`
（覆盖非 Camel 线程，如 MVC controller）。两者取值相同但都不能单独覆盖全部日志，
所以 `logging.pattern.console` 把两者拼接后用嵌套 `%replace` 去重、空值显示成 `~`。
细节见 `CLAUDE.md` 的「链路追踪（traceId）」一节。没有引 span 导出器，span 只落日志。

## 五、下次可以做的事

- 加 `onException` / `errorHandler` 章节（当前只有 `OrderValidateProcessor` 抛异常，未做统一异常路由）
- 加 Camel 单测（`camel-test-spring-junit5` 已在 pom 的 test scope 里，但还没写测试类）
- 接 Spring Cloud 的具体能力（目前只引了 BOM）
- 把 span 导出到 Zipkin（加 `io.zipkin.reporter2:zipkin-reporter-brave` + `management.zipkin.tracing.endpoint`）
- 把 `docs/api6-flow.html` 的图导出 PNG 后嵌进 PPT（现在 PPT 里的流程图是 python-pptx 原生绘制的，可编辑）

## 六、验证方式

```bash
mvn clean package -DskipTests
java -jar target/camel-session-0to1-1.0.0-SNAPSHOT.jar   # 另开终端
./test-apis.sh
```

对照控制台日志观察：traceId 前缀（同一次请求全线程一致）、`[route:xxx]` 前缀、
`Multicast` / `WireTap` 线程名、`[AGGREGATE]` 逐分支累加、`filter 放行 / 未放行`。

## 七、环境备注

- 已安装 skill：`archify`（`.agents/skills/archify`，`.claude/skills/archify` 是软链），
  锁文件 `skills-lock.json`。生成/修改流程图用它。
- 本机无 LibreOffice，PPT 用 `qlmanage -t -s 1500 -o <dir> <file>.pptx` 预览（只渲染第 1 页；
  要看第 N 页可先用 python-pptx 把该页移到首位再预览）。
- `python-pptx 1.0.2` 已安装。
