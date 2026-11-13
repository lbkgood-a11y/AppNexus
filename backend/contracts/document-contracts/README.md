# 单据与通用服务公开契约

`Documents.Provider` 登记单据类型及行为。每个 `Action.interaction` 是唯一交互契约，显式定义编码、版本、触发来源和服务能力。`Descriptor` 不再保存工作流 entry/purpose/outcomes；专业服务不能单独登记第二份业务契约。

`InteractionContract.Capability` 定义目标服务、完成方式、能力引用、用途、输入字段及结果。当前支持 BUSINESS/SYNCHRONOUS、WORKFLOW/CALLBACK、PRINT/CONFIRMATION。MESSAGE 和事件/定时触发仅为协议扩展点，没有相应执行器，不能配置为可执行绑定。

`DocumentBindings.resolveAction` 必须显式传入行为编码、可信租户和操作者、UUID 请求键、单据 UUID、非负依据版本及服务端重读的非敏感事实。返回唯一发布服务策略与目标版本；BUSINESS 没有流程目标，业务自己改变状态。工作流通过 `verifyApproval` 复核当前行为、用途、入口和依据后精确启动，不允许业务回退到自行匹配流程。

`Binding.service` 是唯一服务字段，取值 BUSINESS/WORKFLOW/PRINT。旧 mode 字段、默认 submit 解析 API、缺字段的 Binding 构造器及旧工作流声明入口均已移除。V67 一次性转换存量配置和执行快照；运行时不解析旧协议。

打印目录从统一交互能力的 fields/reference 投影，`PrintDataContractProvider` 是装配适配端口，业务不再另写字段目录。远程打印必须显式携带 dataContract，不从场景猜测契约。`OperationAlias` 将多个真实页面操作映射到同一业务行为，不增加第二条执行路径。

平台负责类型启停、租户配置授权及页面关联；租户管理员维护发布服务绑定。配置资格不代替业务权限。工作台不生成业务表单、不保存业务状态、不执行任意 URL 或脚本。

进程内使用公开 JAR，远程使用 document-client OAuth SDK。业务服务负责授权、取数、业务事务、状态及结果应用；远程回调执行器尚未交付，不能将本地共享事务保证视为跨服务保证。
