# 业务对象管理前端

package.json 的 dependencies 中，`@appnexus/ui` 提供公共业务组件，`@appnexus/icons` 提供页面使用的统一语义图标；两者均为必填的本地工作区依赖，`workspace:*` 跟随当前仓库版本，无外部版本默认值，修改后须同步锁文件并重新安装依赖。

平台目录与租户流程绑定复用 BusinessWorkspace、ManagementTable、ManagedDialog、Element Plus 表单，不新增 UI 库。平面类型目录采用标准列表，复杂绑定在统一弹窗中按流程及条件组合编辑。详情契约只读，不动态生成业务表单。

租户以业务行为选择策略，列表/详情入口共用 canonical operationId；旧别名由后端读取时规范化。工作台是条件、服务与流程/打印场景绑定的唯一维护入口。

平台“已接入操作”从代码自动登记，展示完整页面与 API，提供搜索及每页 10 条分页。关系本身只读；启停通过独立 page-controls 接口，必须填写 1—200 字原因，乐观锁和审计由服务端执行。历史允许列表由数据库迁移一次性转换为禁用覆盖；运行时不提供旧协议转换。复用现有表格、分页、输入框及确认组件，无新增组件。

package.json 的 private=true 禁止发布，type=module 使用 ESM；exports 为公开组件和类型入口；build/typecheck 执行 Vue 类型检查，test 执行 Vitest；peerDependencies 使用应用已有运行时。tsconfig 的 target=ES2022、module=ESNext、moduleResolution=Bundler 对齐 Vite，strict=true 开启严格检查，noImplicitAny=false 与现有领域包保持一致以兼容公共表格模板回调推导，lib 包含浏览器 DOM，include 仅编译模块源码。这些是开发配置，不新增运行时环境变量。


## 独立编码规则与绑定

`./coding-rules` 导出独立规则管理界面，宿主使用 BusinessWorkspace + ManagementTable 标准列表及 ManagedDialog 表单；沿用现有分段编辑器组合 Element Plus，不新增UI库。规则管理通过 `CodingRule.list/save` 和 `coding.rule:manage` 进入；对象配置中的 `CodingEncoder` 只维护目标字段、固定规则版本、名称和启停。

`CodingRuleInput.revision` 首次为-1，追加时为读取到的最新版本号；历史版本不可变。`EncoderInput.ruleKey/ruleVersion` 必填，不再提交内联rule。新规则版本不自动升级绑定；已发号绑定禁止换字段或规则引用，修改名称/启停允许。每个对象内编码器独立流水；复用同规则不等于共用计数器。

新增公开导出 `./coding-rules` 为构建入口，不包含部署配置、默认凭据或身份字段。第一方API与前端需同批发布；迁移说明及未验范围见 docs/validation/编码规则独立管理检查.md。

### 编码规则弹窗布局

表单按基本信息、编码段配置、只读预览组织。基本信息使用两列；编码段按类型、参数、操作三列对齐，弹窗内容宽度不超过600px时转为单列。保存和取消复用 ManagedDialog footer，内容滚动时保留操作入口。继续使用公共恒显边框标签、Element Plus 和主题变量，无新增组件或 UI 库；草稿、版本、权限和 API 契约沿用原实现。

编码段内边距使用 --app-content-padding，分段间距使用 --app-section-gap；操作区跟随公共边框标签顶部8px预留对齐，不覆盖公共表单字段底间距。验收状态见 docs/validation/编码规则弹窗布局检查.md。

已有编码规则的审计区域复用主数据 `detail-system` 卡片、标题和 `RecordAuditDetails inline` 横向四栏样板；系统信息标题沿用现有中英文文案，窄屏按公共样板折行，区块间距跟随全局设置。真实审计值及缺失值语义保持不变，不新增组件和查询入口。验收状态见 docs/validation/编码规则审计样式检查.md。

## 目录与业务配置分区

目录详情默认对象声明，集中展示基本信息、编码能力、字段和操作契约；平台管控单独页签保留入口控制与租户授权。编码能力只读投影同一Descriptor.codingFields，字段、关联操作及字符上限不由页面猜测。

租户配置按编码绑定、业务绑定、对象声明分区。编码绑定复用ManagementTable展示名称、字段、固定规则版本、启停，选择配置后在两列表单编辑；通过CodingEncoder.save单独保存。业务绑定沿用原配置版本/发布与就绪检查，底部保存/发布仅在该页签出现。无编码能力不显示绑定入口。页签保留草稿，普通弹窗仅外层滚动；公共卡片、明细表格及描述列表密度不作深层覆盖。

密度调整：目录声明和平台管控的细分分区采用语义section，使用全局内容padding及区块gap；不使用卡片header/body重复留白。基本信息直接呈现描述行，空编码/条件声明采用文字。编码绑定编辑器按容器宽度自动排列220px及以上字段，业务绑定240px及以上字段；窄屏回落单列，保留公共控件高度与校验底间距。
