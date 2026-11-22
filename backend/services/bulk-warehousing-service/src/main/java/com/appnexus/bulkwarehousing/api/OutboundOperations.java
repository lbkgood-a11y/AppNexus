package com.appnexus.bulkwarehousing.api;

/** 出仓库存及冻结的统一操作目录。服务与 Provider 沿用相同权限码。 */
public enum OutboundOperations {
  CHECK("check","校验可发","Check stock eligibility",false),
  RESERVE("reserve","占用库存","Reserve stock",false),
  RELEASE("release","释放占用","Release reservation",false),
  READ_RESTRICTIONS("read","查看库存监管记录","View stock restrictions",true),
  FREEZE("freeze","冻结库存","Freeze stock",true),
  LIFT("lift","解除冻结","Lift stock restriction",true);

  public static final String STOCK = "BulkOutboundStock";
  public static final String RESTRICTION = "BulkStockRestriction";

  private final String code;
  private final String nameZh;
  private final String nameEn;
  private final boolean restriction;

  OutboundOperations(String code,String nameZh,String nameEn,boolean restriction) {
    this.code=code;
    this.nameZh=nameZh;
    this.nameEn=nameEn;
    this.restriction=restriction;
  }

  /** @return 唯一声明对象 */
  public String object() { return restriction ? RESTRICTION : STOCK; }
  /** @return 统一租户资源编码 */
  public String resource() { return restriction ? "warehousing.stock.restriction" : "warehousing.outbound.stock"; }
  /** @return 对象操作编码 */
  public String code() { return code; }
  /** @return 权限目录编码 */
  public String permission() { return resource()+":"+code; }
  /** @return 中文操作说明 */
  public String nameZh() { return nameZh; }
  /** @return 英文操作说明 */
  public String nameEn() { return nameEn; }
}
