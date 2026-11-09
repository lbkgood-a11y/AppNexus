# 审批服务工程

审批与 platform-service 平级，初期由平台现有 application 组合启动，没有独立进程或新增端口。
服务实现只能由启动装配层引用；其他调用方只能依赖 contracts/approval-contracts。

## 当前内容

- core：审批核心边界与引擎端口，无 Spring、Flowable 或平台实现依赖。
- adapters/flowable：Flowable 8.0.0 的内嵌装配，不引入 Flowable REST 应用或 Boot Starter。
- architecture-tests：检查服务实现隔离、契约独立和引擎依赖归属。
- 跨服务调用上下文位于 contracts/approval-contracts，显式携带租户、操作者和请求标识。

当前没有审批写入口、工作台、流程设计器、审批表、权限授予或事件投递实现。
不注册空接口或模拟成功响应；API、持久化、平台能力适配在对应首个用例落地时增加。

## 启用约束

配置 app.approval.engine.enabled 默认 false，可通过 APP_APPROVAL_ENGINE_ENABLED 覆盖。
当前关闭引擎不会创建引擎表、访问引擎数据库或启动引擎后台执行器。
启用前必须由受控迁移准备与锁定版本一致的引擎表；运行时不自动建表或升级。
启用开关仅装配引擎，不代表中国式审批和安全链路已完成，不应作为生产业务功能开关使用。

初期复用平台 DataSource 和 PlatformTransactionManager；审批表、引擎表归审批服务独占。
未来审批领域持久化必须使用同一数据源与事务管理器，才能与引擎共享本地事务。
业务单据属于业务服务，不依赖此本地事务，也不能直接修改审批表。

## 验证

在 backend 下执行：

```powershell
mvn -pl services/approval-service/architecture-tests -am test
mvn -pl services/approval-service/adapters/flowable -am test "-Dtest.excluded-groups="
```

第二条包括真实 MySQL 8.4 容器验证，需要 Docker；仅一次性测试库允许引擎自动建表。
该验证覆盖引擎启动、BPMN 部署、租户条件查询与 Spring 事务回滚，不等于完整租户安全验收。

设计基线见 docs/design/审批服务详细设计.md。
