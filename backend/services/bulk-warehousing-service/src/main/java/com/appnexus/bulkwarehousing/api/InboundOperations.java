package com.appnexus.bulkwarehousing.api;

/** 入仓对象唯一操作标识；授权执行与业务对象声明复用同一编码。 */
public enum InboundOperations {
  ISSUE("issue", "签发入仓通知", "Issue inbound notice"),
  TAKE_CUSTODY("take_custody", "签认接管", "Take custody"),
  RECEIVE("receive", "确认实收并过账", "Confirm and post receipt"),
  REGISTER_TITLE("BulkInboundTitleRegistration", "warehousing.inbound.title", "register",
      "登记入仓货权", "Register inbound title"),
  RELEASE_QUALITY("BulkInboundQualityRelease", "warehousing.inbound.quality", "release",
      "批准质量放行", "Release inbound quality");

  public static final String OBJECT = "BulkInboundReceipt";
  public static final String RESOURCE = "warehousing.inbound";
  private final String object;
  private final String resource;
  private final String code;
  private final String nameZh;
  private final String nameEn;

  InboundOperations(String code, String nameZh, String nameEn) {
    this(OBJECT, RESOURCE, code, nameZh, nameEn);
  }

  InboundOperations(String object, String resource, String code, String nameZh, String nameEn) {
    this.object = object;
    this.resource = resource;
    this.code = code;
    this.nameZh = nameZh;
    this.nameEn = nameEn;
  }

  /** @return 对象内的操作编码 */
  public String code() { return code; }
  /** @return 唯一对象编码 */
  public String object() { return object; }
  /** @return 权限资源 */
  public String resource() { return resource; }
  /** @return 租户权限码 */
  public String permission() { return resource + ":" + code; }
  /** @return 中文操作名称 */
  public String nameZh() { return nameZh; }
  /** @return 英文操作名称 */
  public String nameEn() { return nameEn; }
}
