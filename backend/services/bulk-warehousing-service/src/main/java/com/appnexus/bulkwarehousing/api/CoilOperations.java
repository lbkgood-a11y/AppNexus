package com.appnexus.bulkwarehousing.api;

/** 整卷出仓操作的统一授权与对象目录。 */
public enum CoilOperations {
  IDENTIFY("identify","核验卷号","Identify coil"),
  READ_AUTHORIZATION_BALANCES("read_authorization_balances","查看提货授权余额","View pickup authorization balances"),
  ISSUE("issue","签发出仓通知","Issue outbound notice"),
  AUTHORIZE("authorize","签发提货授权","Authorize pickup"),
  PLAN("plan","设置车次配额","Plan trip quota"),
  PRE_RESERVE("pre_reserve","预留授权额度","Reserve authorization quota"),
  CLOSE("close","关闭提货授权","Close pickup authorization"),
  ALLOCATE("allocate","整卷配货","Allocate a whole coil"),
  VEHICLE_LIMITS("vehicle_limits","核验车辆限载","Verify vehicle limits"),
  REVIEW_WEIGHING("review_weighing","复核出仓计量","Review outbound weighing"),
  HANDOVER("handover","门岗确认交接","Confirm outbound handover"),
  ACCEPT_RETURN("accept_return","验收实际退回","Accept returned coils"),
  REVERSE_HANDOVER("reverse_handover","冲销出仓交接","Reverse outbound handover"),
  DISPATCH_RETURN("dispatch_return","登记返仓发运","Dispatch coils for return"),
  READ_RETURN_TRANSIT("read_return_transit","查看返仓在途","View return transit"),
  REROUTE_TRANSIT_COIL("reroute_transit_coil","批准返仓在途改仓","Reroute coil in return transit"),
  SETTLE_TRANSIT_LOSS("settle_transit_loss","批准返仓短损结清","Settle return transit loss"),
  REOPEN_TRANSIT_LOSS("reopen_transit_loss","批准短损补到重开","Reopen returned loss"),
  RETURN_COILS("return_coils","登记按卷退回","Post returned coils"),
  RELEASE_RETURNED_QUALITY("release_returned_quality","放行返仓卷","Release returned coil quality"),
  CANCEL("cancel","撤销整卷配货","Cancel coil allocation");

  public static final String OBJECT="BulkCoilOutbound";
  public static final String RESOURCE="warehousing.outbound.coil";
  private final String code;
  private final String zh;
  private final String en;

  CoilOperations(String code,String zh,String en) {
    this.code=code;
    this.zh=zh;
    this.en=en;
  }

  /** @return 对象操作编码 */
  public String code() { return code; }
  /** @return 租户权限码 */
  public String permission() { return RESOURCE+":"+code; }
  /** @return 中文名称 */
  public String zh() { return zh; }
  /** @return 英文名称 */
  public String en() { return en; }
}
