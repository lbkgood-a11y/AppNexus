/** 管理页面公共入口：组合 VxeTable 与当前 Element Plus 组件，不依赖应用壳内部文件。 */
export { default as ManagementTable } from "./ManagementTable.vue";
export { default as PageHeader } from "./PageHeader.vue";
export { default as BusinessWorkspace } from "./BusinessWorkspace.vue";
export { default as ManagedDialog } from "./ManagedDialog.vue";
export { default as RecordAuditDetails } from "./RecordAuditDetails.vue";
export type { RecordAudit } from "./RecordAuditDetails.vue";
export { default as StandardListTemplate } from "./templates/StandardListTemplate.vue";
export { default as TreeListTemplate } from "./templates/TreeListTemplate.vue";
export { default as VerticalSplitTemplate } from "./templates/VerticalSplitTemplate.vue";
export { default as DetailFormTemplate } from "./templates/DetailFormTemplate.vue";
export { default as SplitDetailTemplate } from "./templates/SplitDetailTemplate.vue";
export { default as TabbedDetailTemplate } from "./templates/TabbedDetailTemplate.vue";
export { default as CardGridTemplate } from "./templates/CardGridTemplate.vue";
export { default as DashboardTemplate } from "./templates/DashboardTemplate.vue";
export type { ManagementColumn, ManagementTableExcelExport } from "./types";
import "vxe-pc-ui/lib/style.css";
import "vxe-table/lib/style.css";
import "./vxe-theme.css";
import "./management.css";
import "./templates.css";

export { default as InfoHint } from "./InfoHint.vue";
export { default as FloatingError } from "./FloatingError.vue";
export { reportError, clearErrorFeedback, setErrorFeedback, presentLoadError, type LoadIssue } from "./errorFeedback";
export { default as LoadErrorResult } from "./LoadErrorResult.vue";

export { default as AppPagination } from "./AppPagination.vue";
export { default as GeographyPicker } from "./GeographyPicker.vue";
export { geographyPath, geographyTree } from "./geography";
export type { GeographyNode, GeographySelection } from "./geography";

/** 全站表格公开入口；领域模块经此入口使用 VxeTable，不依赖应用壳。 */
export { VxeTable, VxeColumn } from "vxe-table";

import { VxeUI } from "vxe-pc-ui";
import zhCN from "vxe-pc-ui/es/language/zh-CN";
import enUS from "vxe-pc-ui/es/language/en-US";
/**
 * 同步表格内置空态、加载和工具提示语言，沿用应用现有国际化状态。
 * @param locale 应用语言标识；zh 前缀使用中文，其余使用英文。
 */
export function setTableLocale(locale: string) {
  VxeUI.setI18n("zh-CN", zhCN);
  VxeUI.setI18n("en-US", enUS);
  VxeUI.setLanguage(locale.startsWith("zh") ? "zh-CN" : "en-US");
}

export { referenceName } from "./referenceName";
export { default as AttachmentUpload } from "./AttachmentUpload.vue";
