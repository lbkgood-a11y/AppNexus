/** 管理表格列契约；不承载权限判定，调用者必须先按真实能力构造可用列。 */
export interface ManagementColumn {
  /** 稳定列标识，也是自定义单元格插槽名；同一表格内必须唯一。 */
  key: string;
  /** 已按当前语言翻译的列标题。 */
  label: string;
  /** 数据字段名；未提供时由同名插槽呈现。 */
  prop?: string;
  /** 最小宽度，单位 px；缺省 140，长表格仅在自身容器内横向滚动。 */
  minWidth?: number;
  /** 固定宽度，单位 px；缺省由内容及最小宽度决定。 */
  width?: number;
  /** 默认固定方向；缺省不固定，通常将操作列固定到 right。 */
  fixed?: "left" | "right";
}
