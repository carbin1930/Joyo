# 后端编码规范（Spring Boot）

> 文档状态：已定稿 | 适用范围：`backend/` 全部代码

## 1. 基础约定

- Java 21（LTS）、Spring Boot 3.3+、Maven；构建产物 `joyo-backend.jar`
- 代码注释、JavaDoc、日志一律**中文**；标识符（类/方法/变量）英文
- 所有配置集中在 `application.yml`，自定义前缀 `joyo.*`（如 `joyo.token`、`joyo.result.max-rows`）

## 2. 包结构与分层

```
com.joyo.backend
├── JoyoBackendApplication.java
├── config/        # TokenAuthFilter、WebConfig、JacksonConfig
├── controller/    # XxxController：只做参数校验 + 委托 service + 包装响应
├── service/       # XxxService：全部业务逻辑（JDBC 操作也在此层）
├── model/         # XxxRequest / XxxResponse（纯 DTO，与 rest-api.md 一一对应）
├── exception/     # BizException(code, message)、ErrorCode 枚举、GlobalExceptionHandler
└── util/          # 无状态工具（SqlSplitter 等）
```

- 依赖方向：controller → service，**禁止反向**；service 之间禁止互相调用（需要复用则抽 util 或合并）
- DTO 命名严格对应接口：`QueryExecuteRequest` / `QueryExecuteResponse`
- 响应包装统一由 `ApiResponse<T>`（code/message/data）+ 全局 `@RestControllerAdvice` 处理，controller 返回 `ApiResponse.ok(data)` 或直接返回 data 由 advice 包装（选定一种后全项目一致）

## 3. 业务异常约定

```java
public class BizException extends RuntimeException {
    private final ErrorCode errorCode;   // 枚举，含 code + 默认中文 message
}
```

- service 层只抛 `BizException`；`GlobalExceptionHandler` 统一转成 `ApiResponse`（HTTP 200）
- 未捕获异常 → 1500，message 固定"发生内部错误"，**原始异常细节写日志不返回前端**
- 参数校验用 `jakarta.validation` 注解（`@NotNull`、`@Size` 等），失败 → 1001

## 4. JDBC 使用规范

- 一律 `PreparedStatement`，参数绑定，**禁止字符串拼接 SQL**（含标识符场景：表名拼接必须经反引号转义 `name.replace("`", "``")` 并校验合法字符）
- 元数据读取统一走 `information_schema` 表 + `SHOW CREATE TABLE`，禁用 `DatabaseMetaData`（行为不一致且信息不全）
- 连接管理：按 connectionId 缓存 `HikariDataSource`（`ConcurrentHashMap`），池配置 max=5、connectionTimeout=5s；连接失效（2004）时移除缓存
- 查询执行：`Statement.setQueryTimeout`；`SELECT` 结果全量拉取到内存缓存（遵守 `data-model.md` 的 LRU 上限），值统一 `getString()` 转字符串
- `Connection`/`Statement`/`ResultSet` 一律 try-with-resources

## 5. 日志

- 框架：**Log4j2**（用户指定）。Maven 配置：排除 `spring-boot-starter-web` 传递的 `spring-boot-starter-logging`，引入 `spring-boot-starter-log4j2`；配置文件 `log4j2.xml`；代码统一走 SLF4J API，输出到控制台（Electron Main 负责转发到文件）
- 级别：连接建立/断开 INFO，SQL 执行 DEBUG（**日志不得包含密码与完整结果集**）
- 异常日志必须带堆栈：`log.error("查询执行失败, connectionId={}", id, e)`

## 6. 测试

- 框架：JUnit 5 + AssertJ；service 层单测用 Mockito 隔离 JDBC
- 集成测试用 **Testcontainers**（`mysql:8.0` 镜像）覆盖真实 SQL 路径；本机无 Docker 时 `@Tag("docker")` 跳过
- `SqlSplitter`（分号拆分、注释/字符串感知）必须有完备单测——这是 spec-03 验收的关键
- 测试类命名 `XxxTest`，方法名用中文描述：`void 字符串中的分号不应被拆分()`

## 7. 其他

- 新增依赖前先确认无可复用的 JDK/Spring 自带能力；引入需在本文件登记并说明用途
- 禁止引入数据库 ORM（MyBatis/JPA）——本项目面向任意 MySQL 实例的动态操作，纯 JDBC 更合适
