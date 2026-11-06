/** 管理页面公共入口：仅组合当前 Element Plus 组件，不依赖应用壳内部文件。 */
export { default as ManagementTable } from "./ManagementTable.vue";
export { default as PageHeader } from "./PageHeader.vue";
export { default as BusinessWorkspace } from "./BusinessWorkspace.vue";
export { default as ManagedDialog } from "./ManagedDialog.vue";
export { default as StandardListTemplate } from "./templates/StandardListTemplate.vue";
export { default as TreeListTemplate } from "./templates/TreeListTemplate.vue";
export { default as VerticalSplitTemplate } from "./templates/VerticalSplitTemplate.vue";
export { default as DetailFormTemplate } from "./templates/DetailFormTemplate.vue";
export { default as SplitDetailTemplate } from "./templates/SplitDetailTemplate.vue";
export { default as TabbedDetailTemplate } from "./templates/TabbedDetailTemplate.vue";
export { default as CardGridTemplate } from "./templates/CardGridTemplate.vue";
export { default as DashboardTemplate } from "./templates/DashboardTemplate.vue";
export type { ManagementColumn } from "./types";
import "./management.css";
import "./templates.css";
