# 管理页面布局组合

公开入口为 `@appnexus/ui`，导出 `ManagementTable`、`ManagementColumn` 及管理页面排布样式。使用说明同时见 `docs/design/前端工程详细设计.md` 的“内容区布局优化”一节。

## 选型与边界

已检查当前 admin 内保留的组件：ReDialog、ReDrawer、ReIcon 等仍在；增强表格和表格工具栏没有保留。单独的 el-table 不包含密度、列显隐、列排序、固定和全屏工具栏，因此采用最小组合，复用 Element Plus Card、Table、Popover、Dropdown、Checkbox、Dialog 与 `@appnexus/icons`。不重新导入完整演示项目，不新增 UI 库。

平台租户、用户、成员目录、组织单元、组织成员、成员授权和租户选择共同使用该面板。业务请求、权限、筛选、分页及写入仍由领域页面负责。全屏使用 Element Plus 全屏弹窗与 Teleport 移动同一表格实例；不依赖嵌入式浏览器可能拒绝的原生 Fullscreen API。弹层使用组件现有焦点、Esc 和滚动管理。

## 调用约定

- `title`、`columns[].label`、`emptyText` 使用宿主 i18n 文案；宿主需提供 `management` 文案组。抽屉内传入 `headingLevel="h2"`。
- `rows` 必须是已通过后端授权的数据；列隐藏不构成字段级权限。调用者权限变化时移除无权列与数据。
- `columns[].key` 为稳定唯一标识，并对应单元格插槽。默认列宽下限为 140px，可按字段覆盖；操作列通常默认固定到右侧。
- `loading` 表示读取中，`busy` 表示写入中，均默认 false；二者禁止重复刷新。单元格业务按钮由调用者按实际状态禁用。
- `actions` 插槽放业务主操作；`before` 放必要上下文；`pagination` 放真实分页。没有分页契约的完整组织集合不虚构服务端分页。
- 列设置支持显隐、拖放及键盘按钮排序、左右固定与重置；至少保留一列。密度使用 Element Plus small/default/large。展示偏好仅在当前组件内存生效，不持久化账号或租户信息。
- 页面默认内容最小高度为 `min(540px, calc(100dvh - 300px))`，普通表格按内容增长，分页在数据区底部；600px 以下取消最小高度。全屏表体使用剩余高度滚动，分页保留在底部。

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
