# 管理页面布局组合

公开入口为 `@appnexus/ui`，导出 `PageHeader`、`ManagedDialog`、八类页面模板、`ManagementTable`、`ManagementColumn` 及管理页面排布样式。使用说明同时见 `docs/design/前端工程详细设计.md` 的“内容区布局优化”一节。

## 页面模板

| 编号 | 公共组件 | 布局职责与业务边界 |
|---|---|---|
| T01 | `StandardListTemplate` | 标题、查询、工具、表格、分页；查询和写入由业务页负责。 |
| T02 | `TreeListTemplate` | 独立页面顶部统一显示标题标签栏，单卡片承载可折叠、220–360px 可调宽左树与右侧查询/列表；嵌入已有导航工作区时用 `embedded` 并省略 `title`，树节点与查询联动由业务页负责。 |
| T03 | `VerticalSplitTemplate` | 可调高主区域、关联详情与未选择空态；主从选择由业务页负责。 |
| T04 | `DetailFormTemplate` | create/view/edit 模式、双列表单、操作区和浏览器离开提醒；字段规则与路由守卫由业务页负责。 |
| T05 | `SplitDetailTemplate` | 左侧摘要与右侧详情，不限制右侧 Tab、列表或操作记录组合。 |
| T06 | `TabbedDetailTemplate` | 对象摘要与多维 Tab；按需加载和查询状态由业务页负责。 |
| T07 | `CardGridTemplate` | 查询工具与自适应卡片网格；卡片内容和操作菜单由业务页提供。 |
| T08 | `DashboardTemplate` | 指标、图表、列表网格；不绑定图表库或数据源。 |

模板统一采用命名 slot，不调用 API、不判断权限、不存储业务数据。业务页面可以嵌套 `ManagementTable`、Element Plus 表单、树、Tab 或项目现有图表组件，也可以组合多个模板。租户管理使用 T01，组织成员与授权使用 T02；隐藏的 `/platform/template-lab` 用无持久化静态状态验证 T03/T04，不注册生产菜单。

## BusinessWorkspace 全局原则

业务页面必须先明确一个业务工作区，再组织内部区块。标准列表、左树右表、租户选择和个人中心由应用壳的路由标题、侧栏与标签页表达页面位置，内容区直接进入 `BusinessWorkspace`，不重复面包屑、标题和长描述。`PageHeader` 仅用于详情、创建、编辑等需要对象标题、状态或页面级操作的场景。工作区从业务导航开始，中部 `search` 表示当前业务的查询操作层，底部 `data` 表示同一业务的数据管理层。三者共享一个表面、圆角和外边界，内部只通过语义背景、间距和轻分隔线区分，禁止再次堆叠无意义卡片或阴影。只有成员管理等下级对象上下文才通过 `context` 展示对象名称与状态。

- Tab 切换必须同步工作区标题/说明、查询标题、列表标题、数据、主操作和查询状态。
- `ManagementTable embedded` 用于工作区数据层，移除普通页面的重复卡片边界；全屏仍生成独立表面。
- T01 可直接以工作区承载导航、查询和列表；T02 的右侧内容、T03 的主从业务、T04 的表单分组以及 T05–T08 的对象视图都应先确定工作区身份，再组合模板区块。
- `BusinessWorkspace` 不读取路由、权限或接口数据，领域页必须继续执行真实权限和 API 契约。
- 浅色、深色和 System 使用同一 DOM 层级，仅通过语义 Token 改变表面与分隔颜色。
- 桌面工作区采用紧凑密度：Tab 约 46px，高频区块使用 12/16/20px 间距，工具图标按钮约 34×32px；760px 以下恢复至少 44px 的触控高度。

## 选型与边界

已检查当前 admin 内保留的 ReDialog、ReIcon 及 Element Plus Dialog；ReDrawer 已按全站弹层规范移除。单独的 el-table 不包含密度、列显隐、列排序、固定和全屏工具栏，因此采用最小组合，复用 Element Plus Card、Table、Popover、Dropdown、Checkbox、Dialog 与 `@appnexus/icons`。`ManagedDialog` 仅扩展统一标题栏、全视口最大化、内容区内移动、关闭和桌面端手动调节宽高，不另写遮罩或焦点系统。不重新导入完整演示项目，不新增 UI 库。

平台租户、用户、成员目录、组织单元、组织成员、成员授权和租户选择共同使用该面板。业务请求、权限、筛选、分页及写入仍由领域页面负责。全屏使用 Element Plus 全屏弹窗与 Teleport 移动同一表格实例；不依赖嵌入式浏览器可能拒绝的原生 Fullscreen API。弹层使用组件现有焦点、Esc 和滚动管理。

## 调用约定

- `title`、`columns[].label`、`emptyText` 使用宿主 i18n 文案；宿主需提供 `management` 文案组。弹出框内传入 `headingLevel="h2"`。
- `rows` 必须是已通过后端授权的数据；列隐藏不构成字段级权限。调用者权限变化时移除无权列与数据。
- `columns[].key` 为稳定唯一标识，并对应单元格插槽。默认列宽下限为 140px，可按字段覆盖；操作列通常默认固定到右侧。
- `loading` 表示读取中，`busy` 表示写入中，均默认 false；二者禁止重复刷新。单元格业务按钮由调用者按实际状态禁用。
- `actions` 插槽放业务主操作；`before` 放必要上下文；`pagination` 放真实分页。`total` 只能传服务端总数，省略时不展示，不能用当前页行数伪造。没有分页契约的完整组织集合不虚构服务端分页。
- `ManagedDialog` 保持 Element Plus 初始打开位置，不在 `opened` 后二次修改坐标，避免出现窗口跳动；普通状态以 `.app-main` 为移动边界，特殊宿主可通过 `moveTarget` 指向稳定内容容器；最大化固定覆盖完整浏览器视口。`draggable`、`maximizable`、`resizable` 默认开启，写入期间通过 `busy` 同时阻止关闭、移动和重复操作；宽高调整只保留在当前实例内存，不持久化到浏览器或账号。
- 列设置支持显隐、拖放及键盘按钮排序、左右固定与重置；至少保留一列。密度使用 Element Plus small/default/large。展示偏好仅在当前组件内存生效，不持久化账号或租户信息。
- 普通表格按内容自然增长，不使用固定大高度制造空白，分页紧随表格底部。全屏表体使用剩余高度滚动，分页保持可见。

## package.json 参数说明

严格 JSON 入口不允许注释，参数说明在此维护。

| 参数 | 用途及约束 |
|---|---|
| name | 包名 `@appnexus/ui`；领域与应用通过此公开入口引用。 |
| private | 固定 true，禁止意外发布到包仓库。 |
| type | 固定 module，按 ESM 解析。 |
| exports["."] | 必填，指向 `src/index.ts`；导出组件、类型并加载布局样式，不注册全局业务状态。 |
| dependencies | `@appnexus/icons` 使用 workspace:*，只引用工作区本地语义图标入口。 |
| peerDependencies | Vue、Element Plus、vue-i18n 版本范围跟随 admin；复用宿主实例，避免重复状态与主题。 |

tenant、organization 的 dependencies 与 admin 的 dependencies 新增 `@appnexus/ui: workspace:*`；领域页面使用图标的包同时显式声明 `@appnexus/icons: workspace:*`。这些字段只连接本地模块，不引入运行环境参数或敏感配置。
