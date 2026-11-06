# Admin 运行时配置说明

`platform-config.json` 是严格 JSON，不能写入行内注释；本文件提供配置项的中文说明。配置在应用启动时加载，修改后需重新部署静态资源并刷新页面。不得在此文件或 JSON 中写入密码、Token、私钥等敏感值。

| 配置项 | 用途、默认值与影响 |
|---|---|
| Version | 模板版本展示信息；当前为 `7.0.0`，不参与接口协议协商。 |
| Title | 产品标题；默认 `AppNexus`，影响模板品牌文案。 |
| FixedHeader | 是否固定顶部导航；默认 `true`。 |
| HiddenSideBar | 是否隐藏后台侧栏；默认 `false`，仅用于特殊部署布局。 |
| MultiTagsCache | 是否持久化多标签页；默认 `false`，开启会在当前浏览器保存展示状态。 |
| KeepAlive | 是否缓存路由页面实例；默认 `true`，页面仍须在租户切换时清理敏感状态。 |
| Locale | 首次访问语言；默认 `zh`，用户切换后由现有偏好存储覆盖。 |
| Layout | 首次访问导航布局；默认 `vertical`，可选值服从 vue-pure-admin。 |
| Theme | 侧栏主题；默认 `light`，与内容区明暗模式相互独立。 |
| DarkMode | 兼容模板的解析后深色状态；默认 `false`。实际明暗由 `ThemeMode` 解析并同步，不作为用户偏好来源。 |
| ThemeMode | 首次访问主题模式；默认 `system`，可选 `light`、`dark`、`system`。保存的是用户选择，`system` 会实时响应操作系统主题。 |
| Grey | 是否启用全站灰度；默认 `false`，只用于明确的运营场景。 |
| Weak | 是否启用色弱辅助滤镜；默认 `false`。 |
| HideTabs | 是否隐藏页面标签栏；默认 `false`。 |
| HideFooter | 是否隐藏页脚；默认 `false`。 |
| Stretch | 是否使用内容拉伸模式；默认 `false`。 |
| SidebarStatus | 首次访问侧栏是否展开；默认 `true`，用户操作后由现有偏好存储覆盖。 |
| EpThemeColor | Element Plus 品牌主色；默认 `#409EFF`，修改会影响按钮、链接、选中及焦点状态，须验证浅色和深色对比度。 |
| ShowLogo | 是否在侧栏显示 Logo 与产品名；默认 `true`。 |
| Watermark | 是否启用模板水印；默认 `false`；启用时同时设置非敏感的 `WatermarkText`。 |
| TagsStyle | 标签页样式；默认 `chrome`，可选值服从当前模板。 |
| MenuArrowIconNoTransition | 是否关闭菜单箭头动画；默认 `false`。 |
| CachingAsyncRoutes | 是否缓存异步路由；默认 `false`，权限变化时必须确保缓存失效。 |
| TooltipEffect | Tooltip 明暗外观；默认 `light`，组件仍会随内容主题校验对比度。 |
| ResponsiveStorageNameSpace | 本地展示偏好键前缀；默认 `responsive-`。修改后旧偏好不会自动迁移，不得用于身份或租户安全上下文。 |
| MenuSearchHistory | 菜单搜索历史条数；默认 `6` 条，仅影响当前浏览器展示。 |
| MapConfigure | 模板地图演示配置；当前管理业务不依赖。若未来启用，`amapKey` 必须改为部署环境提供的受限公开 Web Key，不得放置服务端密钥。 |
