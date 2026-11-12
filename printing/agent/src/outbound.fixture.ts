/**
 * 出仓单排版验证样本；仅供测试，不注册生产模板或写入业务数据库。
 * 金额与数量采用已格式化字符串，打印层不重算业务值；尺寸沿用 HiPrint 的 pt 坐标。
 */
export function outboundFixture(): { template: Record<string, unknown>; data: Record<string, unknown> } {
  const text = (field: string, title: string, top: number, left: number, width: number, fontSize = 12) => ({
    options: { field, title, hideTitle: true, top, left, width, height: 30, fontSize, testData: "" },
    printElementType: { title: "文本", type: "text" }
  });
  return {
    template: {
      panels: [{ index: 0, width: 210, height: 297, paperType: "A4", printElements: [
        text("documentTitle", "出仓单", 24, 200, 240, 20),
        text("documentNumber", "单号", 70, 28, 260),
        text("outboundDate", "出仓日期", 70, 320, 240),
        text("warehouseName", "仓库", 102, 28, 260),
        text("recipientName", "收货方", 102, 320, 240),
        text("lineHeading", "明细", 150, 28, 530),
        text("lineOne", "第一行", 184, 28, 530),
        text("lineTwo", "第二行", 216, 28, 530),
        text("totalQuantity", "合计数量", 260, 28, 530),
        text("signature", "签收", 320, 28, 530)
      ] }]
    },
    data: {
      documentTitle: "出仓单（排版测试）", documentNumber: "单号：TEST-OUT-001",
      outboundDate: "出仓日期：2026-11-11", warehouseName: "仓库：测试仓库",
      recipientName: "收货方：测试客户", lineHeading: "序号    物料编码      品名规格                 数量    单位",
      lineOne: "1       TEST-001      测试物料 A               12      件",
      lineTwo: "2       TEST-002      测试物料 B               3       箱",
      totalQuantity: "合计：12 件、3 箱（不同单位不合并）", signature: "制单：测试员    复核：________    签收：________"
    }
  };
}
