/** Vite 图标虚拟模块由 admin 应用构建插件生成，领域包只声明编译期形状。 */
declare module "~icons/*" { import type {Component} from "vue"; const icon:Component; export default icon; }
/** 公共 UI 包的样式由其公开入口导入，领域包独立类型检查时保留副作用导入。 */
declare module "*.css";
