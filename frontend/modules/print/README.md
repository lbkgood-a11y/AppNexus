# 在线单据设计与打印

2026-11-11：增加服务端字段目录、出仓单起始模板、固定文字、纸张快速设置、1—100 行动态明细预览、越界提示及统一快照撤销。操作与验证边界见 [自助制作说明](../../../docs/design/打印设计器自助制作说明.md)。

当前为 @appnexus/print 领域包，固定 vue-plugin-hiprint@0.0.62。提供隔离画布、模板快照、预览、返回设计和销毁接口，并提供模板、设备和任务管理页面；应用层通过真实 HTTP 接口保存草稿、发布版本、登记设备和执行补打。

设计器直接使用 HiPrint 完整原生能力：左侧默认元素库、中间物理纸张画布、右侧原生属性面板，以及拖拽、选中、表格、撤销重做和多面板能力。平台只提供 Element Plus 工具栏、弹窗、保存发布和权限边界；完整设计器运行在隔离 iframe 中，不暴露连接、静默打印或原始客户端接口。保存时仍执行模板白名单校验，HTML、脚本、格式化回调和外部资源不能进入发布版本。

字段面板必须由具体业务数据契约提供，禁止前端编造字段或绕过打印字段权限。布局、工具栏、属性表单、状态反馈、主题和国际化遵守 [前端 UI 规范](../../../docs/design/前端UI与代码生成规范.md)。

现有平台基础组件不具备物理纸张布局编辑能力，因此画布需要专用适配；外围复用现有组件，不引入演示项目的 Ant Design/Bootstrap。上游属性面板当前在隔离 iframe 内挂载，服务端与浏览器模板校验均禁止非空脚本回调及 HTML 扩展。

许可、安全和 Office 导出门槛见 [设计记录](../../../docs/design/打印服务设计与选型记录.md)。

## 调用约定

通过公开入口 createPrintDesigner(container, options) 创建，options.template 必填且仅允许应用内受信任模板，language 缺省 cn、可选 en。createBlankTemplate() 返回 A4 空白模板。调用 snapshot() 获取副本、preview(authorizedData) 预览、design() 返回设计；页面离开必须 destroy()。

资源经 Vite ?url 由本站托管；上游在 sandbox="allow-scripts" 的独立 iframe 执行，不授予同源、弹窗、导航或下载权限。全局 jQuery、CSS 和快捷键随 iframe 销毁，不污染应用壳。加载前锁定 autoConnect=false，初始化再调用 disAutoConnect；CSP 禁止网络连接和外部图片/字体。

上游在 iframe 内仍需要 eval；模板基础检查不是完整恶意输入安全验证，不能开放客户任意模板上传。暂不暴露设备连接、Token、print2 和原始引擎，不将此次接入称为生产安全加固完成。

## 严格 JSON 与接口参数说明

| 参数 | 用途与约束 |
| --- | --- |
| package.name/private/type | @appnexus/print；private=true 防止误发布；module 沿用 ESM |
| package.exports | 公开领域 API/设计器入口与 management 页面，不允许业务深层导入引擎 |
| dependencies | 精确锁定 0.0.62，升级需回归模板与隔离策略；不是生产安全认证 |
| 资产依赖 | jquery=3.7.1、jsbarcode=3.12.3、bwip-js=4.11.1，作为 iframe UMD 外部依赖；bwip 浏览器资产以模块本地安装路径引用，避免 exports 不开放 script 文件的问题 |
| devDependencies/scripts | TypeScript、Vitest、Vite 用于类型检查和测试；build 不产出文件，不改变仓库 Node 基线 |
| tsconfig target/lib | ES2022、DOM，仅用于现代浏览器 |
| tsconfig module/moduleResolution/types | ESNext、Bundler、vite/client，用于本地 ?url 资产 |
| tsconfig strict/include | 严格检查 src 下 TypeScript |
| template | 必填原生 panels JSON；最多 40 层、1000000 个 UTF-16 代码单元，限制不替代服务端校验 |
| language | 默认 cn，可选 en，调用方从已有国际化状态传入 |
| 超时 | 加载和每条命令各 15 秒，不自动重试；加载失败销毁 iframe |

许可证来源与待决项见 [第三方记录](THIRD_PARTY_NOTICES.md)。管理页已接入动态菜单、主题变量和响应式布局；真实浏览器及目标设备验收仍按验证清单执行。

当前依赖审计检出上游 jsPDF/DOMPurify 告警；适配器不加载 jsPDF、canvg、Socket.IO 的实现，对应能力使用禁用入口。这不是漏洞修复，依赖树与未来发布包仍需审查。Edge 无头浏览器已验证 A4 初始化、快照、预览/返回设计及销毁，主窗口没有 window.hiprint；尚未进行真实设备验收。


### 场景标识维护（2026-11-11）

场景列表沿用现有标准列表和 Element Plus 表格，新增场景编码列与复制操作；展开行只读展示并复制场景 ID，编辑弹窗同时提供只读 ID。复用 ManagedDialog、el-form、el-input、el-descriptions 和 el-button，不新增自定义组件。新建编码默认自动生成，可改为 1—64 位、字母开头的字母、数字、下划线、点或连字符；租户内唯一，保存后前后端均禁止修改，避免外部 sceneCode 引用失效。复制失败提示手动选择文本复制，不记录剪贴板内容。已有编码保持原值，多模板规则与输出策略仍属于后续能力。

本次验证：管理端类型检查、打印模块 16 项测试、管理端生产构建及 PrintingRuntimeTest 真实 MySQL/Valkey 集成回归通过，覆盖保存后改码拒绝和重复编码拒绝。浏览器访问本地站点停留在登录过期页，登录后视觉、主题、响应式和剪贴板操作尚未验收。
