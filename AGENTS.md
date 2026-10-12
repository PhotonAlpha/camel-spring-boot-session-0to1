# AGENTS.md

This file provides guidance to Codex when working with code in this repository.

## 项目定位

Apache Camel 入门教学工程。刻意用 **Spring XML DSL**（而非 Java DSL）描述全部路由，
所有教学内容以 `camel-context.xml`（装配入口）+ `camel/` 下按 API 拆分的路由片段 + 七个可运行 REST API 的形式呈现。
代码、XML 注释与日志一律使用英文；`AGENTS.md` / `HANDOFF.md` 与 `docs/` 下的中文 PPT 保留中文。
`docs/` 下的 PPT 与流程图由脚本生成，是这些路由的可视化说明，改路由时要同步更新。

技术栈：Spring Boot 4.1.1（Spring Framework 7）· Spring Cloud 2025.1.3（仅引 BOM）· Camel 4.22.1 · springdoc 3.1.1 · JDK 21。

升级 Boot / Camel 时版本要成对选：camel-spring-boot 的 Boot 版本写在 `org.apache.camel:camel-dependencies`
的 `<spring-boot-version>` 里（4.18 → Boot 3.5，4.19/4.20 → 4.0，4.21+ → 4.1）。Spring Cloud 对应 Boot 4.1 的
2026.0 尚未 GA，暂用 2025.1.x —— 项目不用任何 Cloud 组件，Boot BOM 先导入、冲突时以它为准。
Boot 4 下要注意的几处：
- **Jackson 3**：Spring 侧自动装配的是 `tools.jackson.databind.json.JsonMapper`，注入
  `com.fasterxml...ObjectMapper` 会找不到 Bean。Camel 的 REST `bindingMode=json` 仍走 `camel-jackson`（Jackson 2），两者并存。
- **模块拆分**：`RestClient.Builder` 要 `spring-boot-starter-restclient`；tracing 自动装配要
  `spring-boot-micrometer-tracing-brave`，且它不传递 Brave，`micrometer-tracing-bridge-brave` 仍须显式声明。
- 启动时两条 Camel 弃用告警（`camel-observation` → `camel-micrometer-observability`，
  `useMDCLogging` → `camel-mdc`）是已知的，尚未迁移 —— 迁移会改变 `trace_id` MDC 的写入方，要连日志格式一起验。

## 常用命令

```bash
mvn clean package -DskipTests            # 构建
java -jar target/camel-session-0to1-1.0.0-SNAPSHOT.jar   # 运行（8080）
mvn spring-boot:run                      # 开发态运行

./test-apis.sh                           # 一键回归全部 10 个用例（需应用已启动）
./test-apis.sh host:port                 # 指定地址

open http://localhost:8080/swagger-ui.html   # Swagger UI，右上角下拉框切分组
curl -s localhost:8080/openapi               # 分组索引
curl -s localhost:8080/openapi/business.yaml # 某个分组的 spec

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
- `src/main/resources/camel/` —— **教学内容的核心**，按 API 拆成 9 个片段文件，
  每个文件是一个 `<routeContext>` 或 `<restContext>` Bean，路由前有讲解该组标签用途的英文注释块：

  | 文件 | 片段 id | 内容 |
  |---|---|---|
  | `rest-api.xml` | `restApiContext` | 七个 API 的 REST 端点声明 |
  | `route-api1-basic.xml` | `api1RouteContext` | API-1 |
  | `route-api2-dispatch.xml` | `api2RouteContext` | API-2 |
  | `route-api3-quote.xml` | `api3RouteContext` | API-3 |
  | `route-api4-multicast.xml` | `api4RouteContext` | API-4（风控子路由被 API-6 复用）|
  | `route-api5-split.xml` | `api5RouteContext` | API-5 |
  | `route-shared.xml` | `sharedRouteContext` | `item-BOOK/FOOD/OTHER` + `audit-log`（API-5、API-6 共用）|
  | `route-api6-complex.xml` | `api6RouteContext` | API-6 综合编排 |
  | `route-api7-httpexchange.xml` | `api7RouteContext` | API-7 |

  新增一个 API：在 `camel/` 下加一个 `<routeContext>` 文件 → 在 `camel-context.xml` 里
  `<import>` + `<routeContextRef>` → 在 `rest-api.xml` 里加 `<post>`。
  注意 XSD 规定 `<routeContextRef>` / `<restContextRef>` 必须写在 `<restConfiguration>` 之前。
- `processor/` —— `Processor` 实现，被 `<process ref="beanName"/>` 引用；Bean 名即 `@Component` 名。
- `strategy/` —— `AggregationStrategy` 实现，被 `<multicast>` / `<split>` 的
  `aggregationStrategy="beanName"` 属性引用（Camel 4 XML DSL 里它是**属性**，不是嵌套元素）。
- `src/main/resources/templates/` —— Freemarker 模板。API-3 和 API-7 各用一对做出/入站报文转换：
  API-3 的 `request.ftl` / `response.ftl`，API-7 的 `artisan-request.ftl` / `artisan-response.ftl`。
  API-3 的那对说明：
  `request.ftl` 把内部订单渲染成外部系统的 `header`/`payload` 信封，
  `response.ftl` 把外部应答拍平成本应用的业务格式。外部系统的报文格式只写在这两个文件里，
  改格式不需要动 Java。
- `bean/RemoteCallService` —— ProducerTemplate 的四种调用：两次 `freemarker:`、一次 `http://`、
  一次 `direct:`。
- `src/main/resources/openapi.yaml` + `openapi/` —— 手写的 API 契约与它的分组机制，
  见下面《API 文档与分组》。
- `bean/MockRemoteApiController` —— 普通 Spring MVC Controller，扮演"外部系统"，
  让 API-3 在离线环境也能完整演示远程调用。它**故意用另一套报文方言**
  （`header`/`payload` 信封 + `ccy`/`amt`/`fxRate` 缩写字段），否则两个 `.ftl` 就没有东西可翻译。
  改它的格式时，两个模板和 `.idea/rest/API-requests.http` 里直连 mock 的用例都要同步改。

### 七个 API 的教学分工

| 路由 id | 端点 | 演示的标签 |
|---|---|---|
| `api1-basic-order` / `api1-enrich-order` | `POST /api/v1/orders/basic` | `<route> <from> <to> <process> <log>` |
| `api2-dispatch-order` | `POST /api/v1/orders/dispatch` | `<choice> <when> <otherwise> <simple> <jsonpath> <filter>` |
| `api3-quote-order` / `api3-calc-fee` | `POST /api/v1/orders/quote` | ProducerTemplate、`<to uri="bean:...">`、`freemarker:` 出入站报文转换 |
| `api4-parallel-check` + 三条子路由 | `POST /api/v1/orders/check` | `<multicast>` + `aggregationStrategy` |
| `api5-split-items` + `item-*` + `audit-log` | `POST /api/v1/orders/split` | `<split> <wireTap> <toD>` |
| `api6-complex-fulfilment` | `POST /api/v1/orders/fulfil` | 以上全部组合 |
| `api7-account-lookup` | `POST /api/v1/orders/accounts` | 自定义组件 `httpexchange:` + Spring `@HttpExchange` |

REST 层统一 `<to uri="direct:...">` 转交给业务路由，`direct:` 是同步内存端点。
API-4 的三条风控子路由被 API-6 复用；API-5 的 `item-BOOK/FOOD/OTHER` 与 `audit-log` 同样被 API-6 复用。

#### 自定义组件 `httpexchange:`（API-7）

API-3 用 `ProducerTemplate` + `http://` 调外部系统；API-7 是同一件事的**声明式**版本，
两者刻意并存，方便对照。

- `component/httpexchange/` —— 组件本体（Component / Endpoint / Producer / FreemarkerRenderer）。
  scheme 注册在 `src/main/resources/META-INF/services/org/apache/camel/component/httpexchange`，
  这是 Camel 的标准发现机制，不需要声明 Spring Bean。
- `client/ArtisanClient` —— Spring HTTP Interface，URL / 动词 / 出入参类型都由编译器检查。
- `client/HttpServiceClientConfig` —— 手工建代理，每个客户端一个 `@Bean`。这是 Boot 3 时代的写法；
  升到 Boot 4 后 `@ImportHttpServices` 已可用，但这里刻意保留显式装配，方便看清代理是怎么建出来的。
  其中 `embeddedValueResolver` 是 `@HttpExchange("${demo.remote.base-url}/...")` 能取到配置的原因。

URI 形态与选项：

```
httpexchange:<beanName>/<methodName>
    ?requestTemplate=...     调用前渲染，结果绑到 @RequestBody 参数
    &responseTemplate=...    调用后渲染，输入是拆掉 ResponseEntity 的返回值
    &unmarshalTo=...         把渲染结果解析回对象，供 REST bindingMode=json 使用
    &forwardHeaders=a,b      哪些 Exchange header 转成 HTTP header（不填=转发所有非 Camel 内部的）
    &queryParamsHeader=...   哪个 Exchange header 装着 @RequestParam Map 的内容
    &normalizeResponse=true  渲染响应模板前先用 Jackson 拍成 Map/List（默认 true）
```

参数绑定按接口上**本来就有的** Spring 注解走：`@RequestBody` ← 渲染结果；
`@RequestHeader Map`/`MultiValueMap` ← Exchange header；`@RequestParam Map` ← `queryParamsHeader`
指定的 header；`@PathVariable` ← 同名 header。

**改这个组件前必读的两个坑**（都真实踩过）：

1. **方法要从接口上找，不能从代理类上找。** `HttpServiceProxyFactory` 返回 JDK 动态代理，
   代理类生成的方法**不携带参数注解**，在 `proxy.getClass().getMethods()` 上查到的签名里
   `@RequestBody` 等全都消失，所有参数绑成 null，Spring 报 `RequestBody is required`。
   见 `HttpExchangeProducer#findMethods`。
2. **必须开 `-parameters`**（`pom.xml` 的 `maven.compiler.parameters`）。
   `@PathVariable String id` 这类没写 name 的注解要靠参数名反射，
   缺了会报 `parameter name information not available via reflection`。
   `spring-boot-starter-parent` 默认带这个 flag，本项目只导入 BOM 所以要手工加。
3. **接口上的请求头参数不能用 `HttpHeaders`。** Framework 7 起 `HttpHeaders` 不再实现 `MultiValueMap`，
   HTTP Interface 客户端会把 `@RequestHeader HttpHeaders` 当成单个头值去转 String，报
   `No converter found ... HttpHeaders to String`。要写 `@RequestHeader MultiValueMap<String, String>`
   （或 `Map<String, String>`）。服务端 Controller 上的 `@RequestHeader HttpHeaders` 不受影响。

另外 `FreemarkerRenderer` 用的 model 变量名（`body` / `headers` / `exchange`）和 camel-freemarker
完全一致，所以同一个 `.ftl` 在 `freemarker:` 端点和本组件下渲染结果相同。

## 两个贯穿全项目的语义

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

## 验证产物放哪

**一律写到项目内的 `tmp/`，不要用 `/tmp` 或系统临时目录** —— 项目外的路径每次都要用户确认。
`tmp/` 已在 `.gitignore` 里，可随时 `rm -rf tmp/*`。

```bash
java -jar target/*.jar > tmp/app.log 2>&1 & echo $! > tmp/app.pid   # 跑应用
./test-apis.sh > tmp/regression.txt 2>&1                            # 跑回归
python3 tmp/peek.py docs/X.pptx 12 tmp/preview/s.pptx               # 预览 PPT 第 N 页
```

`tmp/peek.py` 把 pptx 的第 N 页移到首位，再用 `qlmanage -t -s 1400 -o tmp/preview <file>`
截图（本机没有 LibreOffice，Quick Look 只渲染第 1 页，所以要先挪页）。

## 语言约定

`src/` 下的 Java、XML、YAML 一律英文（注释、Javadoc、`<log message>`、以及响应里的
`message` / `hint` 字段）。改动时保持英文，不要混入中文。
`AGENTS.md`、`HANDOFF.md` 与 `docs/` 下的中文 PPT / 图是中文文档；`docs/` 下带 `-en`
或英文名的是它们的英文版，改了其中一边要同步另一边。

## docs/ 里有什么

| 文件 | 生成脚本 | 说明 |
|---|---|---|
| `Camel-入门指南.pptx` | `build_guide_deck.py` | **主力手册**，26 页：前半按 API 递进，后半是速查表（Simple / URI / Header / Java DSL / 标签总表）|
| `Camel-Getting-Started-Guide.pptx` | `build_guide_deck_en.py` | 上一份的英文版，页面结构完全一致 |
| `Camel-API6-复杂编排流程.pptx` | `build_api6_deck.py` | API-6 一页流程图 + 逐节点标签表 + 控制台轨迹，4 页 |
| `Camel-API6-Complex-Orchestration.pptx` | `build_api6_deck_en.py` | 上一份的英文版 |
| `Camel-入门指南-通俗版.pptx` | `build_guide_deck_simple.py` | 类比+图的通俗改写版，19 页。**已被弃用**（用户选择了主力手册），保留仅供参考 |
| `api6-flow.html` / `api6-flow.en.html` | archify（源规格是同名 `.workflow.json`） | 交互式流程图，中/英各一份 |

### 《入门指南》的中英同构机制

**这两份 PPT 共用一套版式代码，不要各写一份。**

- `docs/guide_deck.py` —— 版式引擎，26 页的所有几何都在这里。改结构只改这一个文件，中英同时生效。
- `docs/guide_text_zh.py` / `docs/guide_text_en.py` —— 纯文案字典，两边的 key 必须一一对应。
- `docs/build_guide_deck.py` / `build_guide_deck_en.py` —— 只负责传入文案包和排版参数
  （字体、代码行高、note 每行字数、note 标签宽度、用哪份流程图）。

改文案：只动对应的 `guide_text_*.py`，两份都要改。
校验结构是否对齐：

```bash
python3 -c "
import sys;sys.path.insert(0,'docs')
import guide_text_zh as z, guide_text_en as e
assert set(z.TEXT)==set(e.TEXT)
for k in z.TEXT: assert set(z.TEXT[k])==set(e.TEXT[k]), k
print('ok')"
```

排版参数差异的原因：中文行盒更高（`code_lh` 0.235 vs 0.20），英文每行塞得下更多字符
（`note_chars` 62 vs 137），英文的 note 标签是整个单词而非两个汉字（`label_w` 0.75 vs 1.20）。

所有 PPT 共用 `docs/slidekit.py`（主题、版式、表格、代码块、流程图节点与箭头），改样式改这一个。
`guide_deck.py` 通过 `flow="zh"/"en"` 选择 `build_api6_deck.py` 还是 `build_api6_deck_en.py` 的
`draw_flow`，所以改 `draw_flow` 会同时影响对应语言的两份 PPT。

## 手工调试 API

**项目里没有 `src/test`**，回归完全靠 `./test-apis.sh`（应用须先启动）。

`.idea/rest/API-requests.http` 是 IntelliJ HTTP Client 的全量请求集（含断言脚本），
环境定义在 `.idea/rest/http-client.env.json`。
`docs/rest/` 下有一份**进了 git 的旧副本**（不含 API-7），`.idea/` 未入库；以 `.idea/rest/` 为准，
改用例时视需要同步过去。`./test-apis.sh` 是同一批用例的 shell 版本。

## API 文档与分组

`src/main/resources/openapi.yaml` 是**手写**的 OpenAPI 3.0 契约，9 条路径（7 个业务 API +
2 个 mock 外部系统），每个请求样例都对应 `.idea/rest/API-requests.http` 里的一个用例。

**为什么手写**：springdoc 的扫描器只认 `@RestController`，看不见 `camel/rest-api.xml` 里的
`<rest>` / `<post>`。放任它扫，出来的文档只有两个 mock Controller，反而误导。所以
`springdoc.packages-to-scan` 指向一个不存在的包，`/v3/api-docs` 故意是空的，
真正的文档全部由 `openapi/OpenApiGroupController` 从这份 yaml 过滤后提供。

**分组写在 `openapi.yaml` 自己的根级 `x-groups` 里**，就在它引用的 `tags:` 上面几行。
（下拉框不是 OpenAPI 的概念，是 Swagger UI 从 springdoc 的 `swagger-config` 读的；
但 `x-` 扩展在根级合法，别的工具会忽略，所以分组可以跟 tag 放在一起。）
`application.yml` 的 `demo.openapi.groups` 是**覆盖层**：非空时整份顶掉 `x-groups`，
留给"某个环境要发布另一套分组"用，平时是注释掉的。启动日志会打印这次用的是哪一边。

一个分组写四种条件，**它们是 AND 关系，不写就是不约束**（四种都不写 = 整份文档）：

| 条件 | 含义 |
|---|---|
| `tags` | operation 带其中任一 tag 就留 |
| `paths` | 路径匹配任一 Ant 通配（`/api/v1/orders/**`）就留 |
| `methods` | HTTP 方法在列表里就留（本项目全是 POST，基本用不上）|
| `excludeInternal` | 丢掉 operation 上打了 `x-internal: true` 的端点 |

当前六个分组，每个演示一种选法：

| 分组 | 选法 | 结果 |
|---|---|---|
| `all` | 不写条件 | 9 条 |
| `business` | 按 tag | 七个业务 API |
| `mock` | 按 tag | 两个 mock 外部系统 |
| `remote-calls` | 按 tag（多个）| API-3 + API-7 + 它们调用的 mock |
| `public` | `excludeInternal` | 除 `x-internal` 外的全部 = 七个业务 API |
| `orders` | 按 path + method | 同 `business`，但用 URL 表达 |

`public` 和 `orders` 跟 `business` 结果相同是故意的 —— 三种维度可以表达同一件事，
区别在扩展性：新增一个 API 时 `public` 自动包含它（除非标 `x-internal`），
`business` 则要记得往 tag 列表里加一项。

**`x-swagger-ui`** 块放 Swagger UI 的显示开关，键名是 camelCase 的 Swagger UI 选项名。
它是用 `BeanWrapper` 通用绑定到 `SwaggerUiConfigProperties` 上的，所以那个类上
**标量 / 枚举 / 列表型**的属性都能写：`filter` `docExpansion` `displayRequestDuration`
`tryItOutEnabled` `operationsSorter` `tagsSorter` `defaultModelsExpandDepth`
`displayOperationId` `maxDisplayedTags` `showExtensions` `persistAuthorization`
`layout` `supportedSubmitMethods` …（这批都实测进过 `swagger-config`）。

同样是 `application.yml` 优先：只有 `springdoc.swagger-ui.*` 没设的项才从 spec 填
（那些字段都是包装类型，没设就是 null）。四类写不进去，都只告警不失败：

| 写不进去的 | 原因 |
|---|---|
| `urls` / `urlsPrimaryName` / `configUrl` / `path` | 应用自己管，被 spec 改了下拉框就废了 |
| 嵌套块 `csrf` / `syntaxHighlight` | 天生非 null，分不清"默认值"还是"设过了"；且 Map 转不过去 |
| 原始类型 `disableSwaggerDefaultUrl` / `enabled` / `useRootPath` | 同上，`false` 不是 null |
| 不认识的键 | 拼错了 |

**`documentTitle` 是个坑**：能绑定上，但 springdoc 2.8.17 里根本没有代码消费它（升到 3.1.1 后未复验）
（整个 jar 里只有属性类自己提到这个名字），页面 `<title>` 始终是 "Swagger UI"。

**改不了外观**。配色、字体、logo、自定义 CSS/JS、布局都不经过 `swagger-config`，
要改只能重写 swagger-ui 的 `index.html`。springdoc 的 `indexPageTransformer` 是
`@ConditionalOnMissingBean`，所以可以自己提供一个 `SwaggerIndexTransformer` Bean 注入
`<style>` —— 现在没做。另外 "Select a definition" 那行字是 Swagger UI 内置文案，改不了。

每个分组产出 `/openapi/<name>.yaml`，并在 `/swagger-ui.html` 的
"Select a definition" 下拉框里占一项（`/openapi` 返回分组索引）。
`OpenApiGroupService` 按 tag 过滤 operation → 丢掉没有 operation 剩下的 path →
裁剪顶层 `tags` → 递归回收没人再引用的 schema，所以每个分组都是自洽的文档，
`$ref` 不会悬空（`x-groups` 和 `x-swagger-ui` 都不会出现在发出去的文档里，
`x-internal` 会保留 —— 它讲的是这个端点本身）。
**加分组只改 `openapi.yaml`，不用写代码。**

两种出错方式的处理不同：tag 写错**直接启动失败**并列出全部合法 tag（那必然是拼写错误）；
路径/方法筛出 0 条只**告警**（可能是有意为之）。启动日志每个分组打一行，带上条件和命中条数。

下拉框的**顺序由 springdoc 按显示名字母序排**，不是 `x-groups` 里的顺序，改不了；
初始选中哪一个由 `urlsPrimaryName` 决定（取第一个分组）。

改这块要知道的四个坑（都真实踩过）：

1. **`springdoc.api-docs.enabled` 不能关。** `SwaggerUiConfigProperties` 上挂着
   `@ConditionalOnBean(SpringDocConfiguration.class)`，而后者由这个开关控制 ——
   关掉它 Swagger UI 整个起不来。要让 springdoc 闭嘴得用 `packages-to-scan`。
2. **`urls.primaryName` 要填 display name。** `SwaggerUrl` 把 `displayName` 序列化成 JSON 的
   `name`（真正的 `name` 字段是 `@JsonIgnore`），Swagger UI 拿 `primaryName` 跟下拉框里
   看得见的那个名字比。见 `SwaggerUiGroupConfig`。
3. **描述里的 EIP 标签要用反引号包起来。** Swagger UI 按 Markdown 渲染 description，
   裸写 `<route>` `<choice>` 会被当成 HTML 标签吃掉，页面上什么都不剩。
4. **yaml 打进 jar 里**，改完要重新 `mvn package` 才生效。

改路由或改响应字段时，这份 yaml 要一起改。核对办法是把每个样例真打一遍，
比对字段集与 enum（`tmp/` 下写个脚本即可），别只靠肉眼。

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
