# 单据公开集成契约

`Documents.Provider` 由业务模块实现，登记稳定类型、字段类型和流程结果约定。登记后平台在单据管理激活并向租户授权；租户管理员配置发布流程绑定。注册本身不授予任何业务权限。

`DocumentBindings.resolve` 供已授权业务用例调用，传入可信租户/账号、UUID 请求键、对象 UUID、非负依据版本和服务端重读的非敏感字段。相同租户、操作者、请求键只能绑定同一对象及相同内容；返回 resolutionId、配置版本和具体流程版本。`verify` 由工作流适配器核对已保存解析与再次读取的业务依据；解析 ID 不作为凭证。

当前仅交付组合部署 Java JAR 接入，未开放任意浏览器 facts 解析端点、远程注册 API 或跨进程恢复 SDK。业务必须持久化流程关联，审批结果必须与当前实例及依据版本匹配。未来远程协议以《单据业务接入闭环协议》为基线，不能把本地事务保障视为远程保证。

业务行为通过 Action 声明 actionCode、operationKey、权限和 supportedModes；resolveAction 首期接受 submit，返回 DIRECT/APPROVAL。DIRECT 没有流程目标（definitionId=null、definitionVersion=0），由业务处理状态变化；verify 仅允许 APPROVAL 用于可信流程启动。旧 Binding 缺省 submit/APPROVAL，避免配置缺字段变成免审批。业务页面从业务 API 取得同一声明和 allowed，单据中心不代理业务调用。
