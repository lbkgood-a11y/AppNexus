/** 图标模块由应用构建生成，单据模块独立检查仅声明公开形状。 */
declare module "~icons/*" {
  import type { Component } from "vue";
  const icon: Component;
  export default icon;
}
/** 公共 UI 样式由公开入口加载。 */
declare module "*.css";
