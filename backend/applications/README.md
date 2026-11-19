# 部署组合

此目录只定义系统如何运行，不拥有平台、主数据、工作流或打印业务规则。

- `monolith-app`：当前组合单体入口，显式装配各业务能力，持有运行配置和组合集成测试。现有验证业务仍由原入口装配，后续生产组合排除前必须完成依赖与回归审查。
- 独立服务启动工程在实际决定拆分后新增，不预建空启动模块。拆分前必须完成表所有权、跨模块事务、可信身份传播、超时、幂等、可靠事件与部署验证。
- 启动类 Java 包暂时保留 `com.appnexus.platform`，保证组件扫描及原测试自动发现语义不变；物理装配已与平台业务工程分离，包名不能作为业务归属依据。

从仓库根目录执行：

```powershell
mvn -f backend/pom.xml -pl applications/monolith-app -am install '-DskipTests'
mvn -f backend/pom.xml -pl applications/monolith-app spring-boot:run '-Dspring-boot.run.profiles=local'
mvn -f backend/pom.xml -pl architecture-tests -am test '-Dtest=ArchitectureRulesTest,ServiceLayerBoundaryTest,PackageDocumentationTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

凭据由既有本地初始化或部署密钥注入，不能复制到文档、命令行或日志。旧 `services/platform-service/application/target` 产物不代表本次源码与配置。
