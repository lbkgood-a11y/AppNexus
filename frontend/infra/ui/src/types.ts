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
  /** 是否按单行溢出处理；默认普通列为 true、操作列为 false，多行名称或复合内容列须显式设为 false 以允许自然行高。 */
  showOverflow?: boolean;
}

/** 当前页 Excel 展示配置；仅使用列表已授权的响应，不执行查询。 */
export interface ManagementTableExcelExport<Row extends object> {
  /** 下载文件名；缺省使用列表标题，自动补齐 .xlsx 扩展名。 */
  fileName?: string;
  /**
   * 按列 key 配置展示值函数或用 false 排除。普通字段列缺省导出显示值，自定义插槽列须显式配置；
   * 函数必须复用格式化和脱敏后的结果，空值返回 null。标识和高精度数字用字符串。
   */
  columns?: Readonly<Partial<Record<string, false | ((row: Readonly<Row>) => string | number | boolean | null | undefined)>>>;
}
