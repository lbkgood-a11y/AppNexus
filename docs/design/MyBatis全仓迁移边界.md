# MyBatis 全仓迁移边界

日期：2026-11-20。状态：实施中，不表示全仓迁移或数据权限整改完成。

普通业务 SQL 必须落在所属上下文固定 Mapper；不通过通用 execute(sql)、SQL 字符串 Provider 或 JdbcTemplate 外观兼容层“迁移”。事务、锁、CAS、审计、幂等及异常协议保持原业务语义。历史 Flyway 校验和不变；迁移辅助类和协议引擎底层连接也纳入清单，逐项判断实现方式并记录，不自动排除。

| 能力 | 业务/技术归属 | Maven及Java边界 | 依赖方向及验证 |
|---|---|---|---|
| 固定Mapper SQL范围元数据 | 无业务语义的持久化技术元数据 | building-blocks/mybatis-scope，artifactId=mybatis-scope，com.appnexus.persistence.mybatis | 只依赖JDK注解，不依赖租户、授权、Spring或业务表；各适配器与平台执行拦截器共同依赖 |
| SQL范围执行与可信请求 | 平台租户持久化适配器 | 现有platform-persistence-adapter，TenantSqlInterceptor | 读取中立注解；可信身份、租户校验仍在平台，不能迁入building-blocks |
| 主数据、打印、审批等存储 | 原业务上下文 | 各原adapter目录及固定Mapper，不新增平行业务模块 | 只依赖中立元数据及MyBatis技术库，不为使用注解让业务正式依赖平台持久化实现 |

提取三个现有scope注解用于支持后续跨业务Mapper迁移，保持注解参数和实际执行语义；全仓更新引用及各受影响POM，禁止保留双套注解或降级成无范围Mapper。building-blocks不存放主体、权限计划、业务资源、SQL执行器或任意SQL接口。部署仍为现有组合单体，无新增部署服务。

实施后须执行真实租户隔离、未声明Mapper拒绝、平台表白名单、同租户数据范围、业务事务及架构包说明验证。仅编译通过不能关闭迁移或安全缺口。
