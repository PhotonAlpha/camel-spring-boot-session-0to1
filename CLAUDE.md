# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目定位

Apache Camel 入门教学工程。刻意用 **Spring XML DSL**（而非 Java DSL）描述全部路由，
所有教学内容以 `camel-context.xml`（装配入口）+ `camel/` 下按 API 拆分的路由片段 + 六个可运行 REST API 的形式呈现。
代码、XML 注释与日志一律使用英文；`CLAUDE.md` / `HANDOFF.md` 与 `docs/` 下的中文 PPT 保留中文。
`docs/` 下的 PPT 与流程图由脚本生成，是这些路由的可视化说明，改路由时要同步更新。

技术栈：Spring Boot 3.5.14 · Spring Cloud 2025.0.2（仅引 BOM）· Camel 4.18.2 · JDK 21。

## 常用命令

```bash
mvn clean package -DskipTests            # 构建
java -jar target/camel-session-0to1-1.0.0-SNAPSHOT.jar   # 运行（8080）
mvn spring-boot:run                      # 开发态运行

./test-apis.sh                           # 一键回归全部 9 个用例（需应用已启动）
./test-apis.sh host:port                 # 指定地址

mvn test -Dtest=SomeTest                 # 跑单个测试类
mvn test -Dtest=SomeTest#someMethod      # 跑单个测试方法

python3 docs/build_api6_deck.py          # 重新生成 API-6 流程图 PPT
python3 docs/build_guide_deck.py         # 重新生成入门指南 PPT（依赖 pip3 install python-pptx）
```

重新生成 `docs/api6-flow.html`（archify skill，命令须在 `.claude/skills/archify/` 下执行）：

```bash
node bin/archify.mjs validate workflow ../../../docs/api6-flow.workflow.json --quality showcase
node bin/archify.mjs deliver  workflow ../../../docs/api6-flow.workflow.json ../../../docs/api6-flow.html --quality showcase --json
node bin/archify.mjs visual-check ../../../docs/api6-flow.html --json
```

## 架构

### 装配链路（改动前务必理解）

`CamelSession0to1Application` 上的 `@ImportResource("classpath:camel-context.xml")`
让 Spring 解析 XML，其中的 `<camelContext>` 注册成 CamelContext Bean；
camel-spring-boot 的 `CamelAutoConfiguration` 因 `@ConditionalOnMissingBean(CamelContext)` 让位，
XML 上下文接管全部路由，生命周期仍由 Spring Boot 托管。

这条链路带来三个必须记住的约束：

1. **运行时开关只能写在 `<camelContext>` 属性上。** 自动装配已让位，`application.yml` 里
   `camel.springboot.*` 下的 `use-mdc-logging` / `tracing` / `stream-caching` 等一律无效。
   当前已在 XML 上开启 `useMDCLogging` `useBreadcrumb` `streamCache`。
   **例外**：`management.tracing.*`（Micrometer Tracing / Brave）与 `camel.observation.*`
   属于普通 Spring 自动装配，它们是把 CamelContext 当构造参数注入的，所以在 `application.yml`
   里配置**有效**，不受这条约束影响。
2. **不要再声明 `ProducerTemplate` Bean。** `<camelContext>` 已自动注册名为 `template` 的
   ProducerTemplate 和 `consumerTemplate`；自建 `@Bean` 会导致
   `NoUniqueBeanDefinitionException`。
3. **业务 Bean 注入 Camel 组件时加 `@Lazy`，并保留 `spring.main.allow-circular-references=true`。**
   否则业务 Bean 会把 CamelContext 拉到各 `*ComponentAutoConfiguration` 之前实例化，
   形成不可解的循环依赖，启动直接失败。见 `RemoteCallService` 构造器。

另外：**XML 注释里不能出现连续两个短横线**（`--`），画分隔线要用 `=`。

### 代码结构

- `src/main/resources/camel-context.xml` —— **装配入口**，不含业务路由。只做三件事：
  `<import>` 引入片段文件、声明 `<camelContext>` 与运行期开关、用 `<routeContextRef>` /
  `<restContextRef>` 把片段装配进上下文。
- `src/main/resources/camel/` —— **教学内容的核心**，按 API 拆成 8 个片段文件，
  每个文件是一个 `<routeContext>` 或 `<restContext>` Bean，路由前有讲解该组标签用途的英文注释块：

  | 文件 | 片段 id | 内容 |
  |---|---|---|
  | `rest-api.xml` | `restApiContext` | 六个 API 的 REST 端点声明 |
  | `route-api1-basic.xml` | `api1RouteContext` | API-1 |
  | `route-api2-dispatch.xml` | `api2RouteContext` | API-2 |
  | `route-api3-quote.xml` | `api3RouteContext` | API-3 |
  | `route-api4-multicast.xml` | `api4RouteContext` | API-4（风控子路由被 API-6 复用）|
  | `route-api5-split.xml` | `api5RouteContext` | API-5 |
  | `route-shared.xml` | `sharedRouteContext` | `item-BOOK/FOOD/OTHER` + `audit-log`（API-5、API-6 共用）|
  | `route-api6-complex.xml` | `api6RouteContext` | API-6 综合编排 |

  新增一个 API：在 `camel/` 下加一个 `<routeContext>` 文件 → 在 `camel-context.xml` 里
  `<import>` + `<routeContextRef>` → 在 `rest-api.xml` 里加 `<post>`。
  注意 XSD 规定 `<routeContextRef>` / `<restContextRef>` 必须写在 `<restConfiguration>` 之前。
- `processor/` —— `Processor` 实现，被 `<process ref="beanName"/>` 引用；Bean 名即 `@Component` 名。
- `strategy/` —— `AggregationStrategy` 实现，被 `<multicast>` / `<split>` 的
  `aggregationStrategy="beanName"` 属性引用（Camel 4 XML DSL 里它是**属性**，不是嵌套元素）。
- `bean/RemoteCallService` —— ProducerTemplate 的两种用法（远程 HTTP、回调本地路由）。
- `bean/MockRemoteApiController` —— 普通 Spring MVC Controller，扮演"外部系统"，
  让 API-3 在离线环境也能完整演示远程调用。

### 六个 API 的教学分工

| 路由 id | 端点 | 演示的标签 |
|---|---|---|
| `api1-basic-order` / `api1-enrich-order` | `POST /api/v1/orders/basic` | `<route> <from> <to> <process> <log>` |
| `api2-dispatch-order` | `POST /api/v1/orders/dispatch` | `<choice> <when> <otherwise> <simple> <jsonpath> <filter>` |
| `api3-quote-order` / `api3-calc-fee` | `POST /api/v1/orders/quote` | ProducerTemplate、`<to uri="bean:...">` |
| `api4-parallel-check` + 三条子路由 | `POST /api/v1/orders/check` | `<multicast>` + `aggregationStrategy` |
| `api5-split-items` + `item-*` + `audit-log` | `POST /api/v1/orders/split` | `<split> <wireTap> <toD>` |
| `api6-complex-fulfilment` | `POST /api/v1/orders/fulfil` | 以上全部组合 |

REST 层统一 `<to uri="direct:...">` 转交给业务路由，`direct:` 是同步内存端点。
API-4 的三条风控子路由被 API-6 复用；API-5 的 `item-BOOK/FOOD/OTHER` 与 `audit-log` 同样被 API-6 复用。

### 两个贯穿全项目的语义

- **`<filter>` 不终止路由。** 不匹配时只跳过 filter 内部的步骤，其后的节点照常执行。
  API-2 和 API-6 都把"收口 Processor"放在 filter **外面**，因此放行与拦截两条分支
  返回的 JSON 字段完全对齐。改这两条路由时不要把收口逻辑挪进 filter。
- **Body 会被反复改写，Property 不会。** API-6 第 2 步用
  `<setProperty name="originalOrder">` 备份原始报文，履约前再 `<setBody>` 还原，
  否则 multicast 之后拿不到 `items`。

### 调整业务阈值时

`ScoreProcessor` 的三项打分与 `RiskDecisionProcessor` 的 `PASS_LINE = 240` 共同决定
API-6 走哪条分支。当前设计保证：`amount <= 50000` → 270 分 → APPROVED；
`amount > 60000` → 165 分 → REJECTED。改动其一必须同步检查 `test-apis.sh` 里的两个
API-6 用例仍分别命中两条分支，以及 `docs/` 两份 PPT 中引用的分数。

## 语言约定

`src/` 下的 Java、XML、YAML 一律英文（注释、Javadoc、`<log message>`、以及响应里的
`message` / `hint` 字段）。改动时保持英文，不要混入中文。
`CLAUDE.md`、`HANDOFF.md`、`docs/Camel-入门指南.pptx`、`docs/Camel-API6-复杂编排流程.pptx`、
`docs/api6-flow.html` 是中文文档；`docs/` 下带 `-en` / 英文名的三份是它们的英文版，
改了其中一边要同步另一边。

## 手工调试 API

`.idea/rest/API-requests.http` 是 IntelliJ HTTP Client 的全量请求集（含断言脚本），
环境定义在 `.idea/rest/http-client.env.json`。`./test-apis.sh` 是同一批用例的 shell 版本。

## 日志与排查

`<camelContext useMDCLogging="true">` 把 routeId 写进 MDC，`application.yml` 的
`logging.pattern.console` 用 `%X{camel.routeId}` 取出，控制台每行都带 `[route:xxx]`。
`<log message="...">` 的 logger 名就是 routeId。需要逐 EIP 打点时把
`<camelContext trace="false">` 改成 `"true"`（很吵，用完改回）。

### 链路追踪（traceId）

三个依赖组成一条链：`spring-boot-starter-actuator`（带来 Micrometer Observation 与
tracing 自动装配）+ `micrometer-tracing-bridge-brave`（Brave 实现，负责生成 traceId/spanId）
+ `camel-observation-starter`（Camel 侧的桥：每条路由、每个 `<to>` 端点各开一个 span）。
控制台每行的第二个方括号就是 traceId，`~` 表示当前线程没有 span（例如启动阶段）。

**为什么日志格式里要写两组 MDC key。** 两边各写各的，且都不能单独覆盖所有线程：

| MDC key | 谁写的 | 覆盖范围 |
|---|---|---|
| `trace_id` / `span_id` | Camel 的 `ActiveSpanManager`（依赖 `useMDCLogging="true"`） | 所有 Camel 线程：multicast 子线程、wireTap 线程、split 子路由 |
| `traceId` / `spanId` | Brave 的 `MDCScopeDecorator`（span scope 打开期间） | 非 Camel 代码，例如 Spring MVC 的 `MockRemoteApiController` |

两者取值相同，所以 `logging.pattern.console` 把它们直接拼起来，再用内层 `%replace`
把重复的一段折掉、外层 `%replace` 把空值显示成 `~`。改日志格式时不要只留一组，
否则要么路由内看不到 traceId，要么"外部系统"那两行看不到。

**验证方式**：`./test-apis.sh` 后，同一次请求的所有日志行（含 `[route:api4-*]` 三条并行
子路由、`[route:audit-log]`、`[route:item-*]`）traceId 必须一致；API-3 的
`[MOCK-REMOTE]` 两行也应与调用方同一个 traceId —— 那是 Camel 把 W3C `traceparent`
注入出站 HTTP 请求的结果。

当前没有引 span 导出器，span 只落在日志里；要送 Zipkin 就加
`io.zipkin.reporter2:zipkin-reporter-brave` 与 `management.zipkin.tracing.endpoint`。
