package com.appnexus.printing.adapter.jdbc;

import java.util.List;
import java.util.Map;

/**
 * 设计字段契约注册表；只包含非敏感元数据与合成示例，不读取业务数据库。
 * 出仓模块尚未接入，因此 designOnly 固定为 true，不能据此宣称真实单据可打印。
 */
final class PrintDesignCatalog {
  private PrintDesignCatalog() {}

  /** 返回当前受支持的设计契约；字段编码由服务端定义，前端不自行推断业务字段。 */
  static List<Map<String,Object>> contracts(){
    return List.of(Map.of("code","warehouse.outbound.v1","nameZh","出仓单","nameEn","Outbound delivery","designOnly",true,
        "fields",List.of(
            field("documentNumber","单号","Document number","TEST-OUT-001"),
            field("outboundDate","出仓日期","Outbound date","2026-11-11"),
            field("warehouseName","仓库","Warehouse","测试仓库 / Sample warehouse"),
            field("recipientName","收货方","Recipient","测试客户 / Sample recipient"),
            field("preparedBy","制单人","Prepared by","测试员 / Sample operator"),
            field("remark","备注","Remark","仅用于排版预览 / Layout preview only"),
            Map.of("key","items","labelZh","出仓明细","labelEn","Line items","sample","","columns",List.of(
                field("lineNumber","序号","No.","1"),field("itemCode","物料编码","Item code","TEST-001"),
                field("itemName","物料名称","Item name","测试物料 / Sample item"),field("specification","规格","Specification","Standard"),
                field("quantity","数量","Quantity","12.00"),field("unit","单位","Unit","件 / pcs"))))));
  }

  /** 字段示例均为显式合成数据；数量使用字符串，打印层不重算或混合不同计量单位。 */
  private static Map<String,Object> field(String key,String zh,String en,String sample){return Map.of("key",key,"labelZh",zh,"labelEn",en,"sample",sample);}
}
