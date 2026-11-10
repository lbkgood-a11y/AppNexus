# 在线单据设计与打印

当前为 @appnexus/print 接入包，固定 vue-plugin-hiprint@0.0.62。提供隔离画布、模板快照、预览、返回设计和销毁接口；没有业务页面、后端保存、字段面板、静默打印或导出。

后续封装 HiPrint 候选设计核心，提供字段绑定、预览、模板发布和打印操作。布局、工具栏、属性表单、状态反馈、主题和国际化遵守 [前端 UI 规范](../../../docs/design/前端UI与代码生成规范.md)。

现有平台基础组件不具备物理纸张布局编辑能力，因此画布需要专用适配；外围复用现有组件，不引入演示项目的 Ant Design/Bootstrap。本期不挂载上游属性面板，避免原生控件和脚本入口直接进入业务界面。

许可、安全和 Office 导出门槛见 [设计记录](../../../docs/design/打印服务设计与选型记录.md)。

## 调用约定

通过公开入口 createPrintDesigner(container, options) 创建，options.template 必填且仅允许应用内受信任模板，language 缺省 cn、可选 en。createBlankTemplate() 返回 A4 空白模板。调用 snapshot() 获取副本、preview(authorizedData) 预览、design() 返回设计；页面离开必须 destroy()。

资源经 Vite ?url 由本站托管；上游在 sandbox="allow-scripts" 的独立 iframe 执行，不授予同源、弹窗、导航或下载权限。全局 jQuery、CSS 和快捷键随 iframe 销毁，不污染应用壳。加载前锁定 autoConnect=false，初始化再调用 disAutoConnect；CSP 禁止网络连接和外部图片/字体。

上游在 iframe 内仍需要 eval；模板基础检查不是完整恶意输入安全验证，不能开放客户任意模板上传。暂不暴露设备连接、Token、print2 和原始引擎，不将此次接入称为生产安全加固完成。

## 严格 JSON 与接口参数说明

| 参数 | 用途与约束 |
| --- | --- |
| package.name/private/type | @appnexus/print；private=true 防止误发布；module 沿用 ESM |
| package.exports | 仅 src/index.ts 为公开入口，不允许业务深层导入引擎 |
| dependencies | 精确锁定 0.0.62，升级需回归模板与隔离策略；不是生产安全认证 |
| 资产依赖 | jquery=3.7.1、jsbarcode=3.12.3、bwip-js=4.11.1，作为 iframe UMD 外部依赖；bwip 浏览器资产以模块本地安装路径引用，避免 exports 不开放 script 文件的问题 |
| devDependencies/scripts | TypeScript、Vitest、Vite 用于类型检查和测试；build 不产出文件，不改变仓库 Node 基线 |
| tsconfig target/lib | ES2022、DOM，仅用于现代浏览器 |
| tsconfig module/moduleResolution/types | ESNext、Bundler、vite/client，用于本地 ?url 资产 |
| tsconfig strict/include | 严格检查 src 下 TypeScript |
| template | 必填原生 panels JSON；最多 40 层、1000000 个 UTF-16 代码单元，限制不替代服务端校验 |
| language | 默认 cn，可选 en，调用方从已有国际化状态传入 |
| 超时 | 加载和每条命令各 15 秒，不自动重试；加载失败销毁 iframe |

许可证来源与待决项见 [第三方记录](THIRD_PARTY_NOTICES.md)。本期没有业务菜单或页面，不声称已完成移动端和主题验收。

当前依赖审计检出上游 jsPDF/DOMPurify 告警；适配器不加载 jsPDF、canvg、Socket.IO 的实现，对应能力使用禁用入口。这不是漏洞修复，依赖树与未来发布包仍需审查。Edge 无头浏览器已验证 A4 初始化、快照、预览/返回设计及销毁，主窗口没有 window.hiprint；尚未进行真实设备验收。
