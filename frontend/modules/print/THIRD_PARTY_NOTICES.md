# HiPrint 第三方组件记录

日期：2026-11-10。用途：内部开发验证，尚未完成商业分发准入。

vue-plugin-hiprint 固定版本 0.0.62；npm 根 LICENSE 标记 MIT，但上游核心 hiprint.bundle.js 和 README 保留原始 HiPrint 的 LGPL 或商业许可声明，不能将整体认定为 MIT。

来源：https://github.com/CcSimple/vue-plugin-hiprint 。发布包由 pnpm-lock.yaml 的 integrity 校验；上游完整 LGPL 版本、对应源码交付及社区修改的授权链仍待确认。发布前保留适用许可证并输出完整依赖和资源清单。

本期没有安装 electron-hiprint、node-hiprint-pdf 或 sv-print，不推定已获得其授权。

直接作为浏览器资产使用的依赖：jQuery 3.7.1（MIT）、JsBarcode 3.12.3（MIT）、bwip-js 4.11.1（项目根 MIT；包含的条码实现与字体等仍须保留各自适用声明）。上述摘要不替代发布时对完整依赖包及嵌入资源的许可审查。

2026-11-13 新增 pdfjs-dist 6.5.438（Apache-2.0，来源 https://github.com/mozilla/pdf.js），用于内置浏览器无原生 PDF 插件时本地渲染真实预览。Worker 由 Vite 打包，不把业务 PDF 发送到第三方查看器；保留依赖包 LICENSE/NOTICE，版本由工作区锁文件固定。
