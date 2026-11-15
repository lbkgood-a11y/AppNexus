# 页面操作公开契约

无运行配置。OperationBinding 由业务接口显式声明操作标识、中英文名称、页面、权限表达式及额外约束。声明只提供元数据，不替代业务授权。页面键使用前端显式注册标识，权限组为 OR-of-AND（任一组全部满足）；空组只允许声明真实的本人身份或对象归属策略，不能解释为无条件授权。

## 唯一声明来源

已由业务对象 Provider 声明的操作，真实接口使用 `@OperationBinding(id="…", reference=true)` 引用，禁止重复提供对象、名称、页面或权限。组合应用从 Provider 读取唯一元数据，并从 MVC 读取实际路由。引用缺失、重复或混合声明阻止启动。

平台接口权限使用 `EndpointOperation` 唯一声明；同一接口的 OperationBinding 不再重复保存 permissions，聚合时按真实管理域读取 EndpointOperation 的普通权限或 platformPermission。未采用该平台注解的独立业务服务在自身操作契约中声明权限，运行时仍由原业务执行器复核。此规则不提供旧声明回退。
