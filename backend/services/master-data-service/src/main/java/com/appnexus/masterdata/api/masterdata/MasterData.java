package com.appnexus.masterdata.api.masterdata;

import java.util.*;

/** 主数据固定类型及公开输入；客户端不能指定表名、租户、操作者或任意扩展字段。 */
public final class MasterData {
  private MasterData() {}

  /**
   * 类型到数据库表的构建内白名单；领域字段均为文本，不隐式转换金额或业务数量。
   * 人员不承载账号凭据和任职授权，仓库不承载库存，商品不承载公司价格。
   */
  public enum Kind {
    PARTNER("partner", "BusinessPartner", List.of("partnerType", "creditCode", "contactName", "phone", "address", "registeredPlace", "countryRegion", "nature", "listed", "chineseName", "englishName", "shortName", "customerLevel", "legalRepresentative", "legalId", "controller", "establishedOn", "registeredCurrency", "registeredCapital", "taxNumber", "organizationCode", "productsServices", "staffSize", "fax", "remarks")),
    WAREHOUSE("warehouse", "Warehouse", List.of("shortCode","shortName","warehouseType","totalArea","capacity","logisticsOrganization","contactName","contactTitle","phone","email","longitude","latitude","region","address","builtOn","openedOn","introduction","structureDescription","operatingMode","billAddress","remarks","partner","owner","ownershipCertificate","personalGuarantee","wholeLease","leaseChainDescription","subleaseDescription","otherStorageDescription","pledgee")),
    STORAGE_AREA("storage-area", "StorageArea", List.of("parentCode", "areaType", "capacity", "remarks")),
    STORAGE_LOCATION("storage-location", "StorageLocation", List.of("parentCode", "capacity", "remarks")),
    COUNTRY_REGION("country-region", "CountryRegion", List.of("englishName", "alpha3Code", "numericCode", "parentCode")),
    SUBDIVISION("subdivision", "Subdivision", List.of("countryCode", "subdivisionType", "parentCode"));

    /** 固定路由片段，不接受客户端自由扩展。 */
    public final String path;
    /** 全局业务对象编码，用于对象目录和操作标识。 */
    public final String objectType;
    /** 此类允许的领域字段集合；未知字段直接拒绝。 */
    public final List<String> fields;
    Kind(String path, String objectType, List<String> fields) {
      this.path=path; this.objectType=objectType; this.fields=List.copyOf(fields);
    }
    /**
   * @return 构建内固定表名，禁止拼接用户输入 */
    /** 固定类型的持久化字段白名单；禁止由请求扩展表或列。 */
    public List<String> fields(){return List.copyOf(fields);}
    public String table() { return "md_"+name().toLowerCase(Locale.ROOT); }
    /**
   * @param field 构建内领域字段名
   * @return 对应 snake_case 数据库列名，不接受请求字段作为 SQL 标识符 */
    public String column(String field) { if(!fields.contains(field))throw new IllegalArgumentException("UNKNOWN_MASTER_DATA_FIELD");return field.replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT); }
    /**
   * @return 此类独立权限资源，不借用组织、账号或配置管理权限 */
    public String resource() { return "masterdata."+path.replace("-",""); }
  }

  /**
   * 档案输入；编码创建后不可修改，停用通过单独操作，避免编辑权限隐式获得启停权。
   *
   * @param code 租户内同类唯一编码，1–64 个 ASCII 字母、数字、点、下划线或短横线；国别和区划允许自定义编码，创建后不可修改
   *
   * @param name 中文或业务名称，去除首尾空白后 1–200 字符，必填
   *
   * @param attributes 固定领域字段；缺省为全空文本，单项最长 500 字符，邮箱最长 200 字符
   *
   * @param organizationId 客商归属组织 UUID；新增客商必填，修改缺省保留原值，不允许将未知归属伪造为当前组织
   * @param contacts 客商联系人，最多100条；null 保留已有明细，空列表清空；非客商不得传入
   * @param bankAccounts 客商银行账户，最多100条；null 保留已有明细，空列表清空；审计只读
   * @param version 修改时必须传当前非负版本；新建使用 0，不影响新建结果
   */
  public record Input(String code,String name,Map<String,String> attributes,long version,List<DetailInput> contacts,List<DetailInput> bankAccounts,String organizationId) {
    /** 旧内部调用不携带归属；新增客商仍由执行器要求明确组织，不能据此回退全部。 */
    public Input(String code,String name,Map<String,String> attributes,long version,List<DetailInput> contacts,List<DetailInput> bankAccounts){this(code,name,attributes,version,contacts,bankAccounts,null);}
    /** 非客商调用与历史内部调用不传明细时保留原明细；空列表表示明确清空。 */
    public Input(String code,String name,Map<String,String> attributes,long version){this(code,name,attributes,version,null,null);}
  }
  /**
   * 明细写入只接收父对象下的标识及白名单字段；不接收审计或租户。
   * @param id 新增为空，修改为当前客商下真实 UUID；不能移动其他客商或租户的明细
   * @param values 固定白名单文本，单值最长 500 字符；布尔值为 true/false，邮箱最长 200 字符
   */
  public record DetailInput(String id,Map<String,String> values) {}
  /** 已授权客商明细，审计来自数据库可信账号及 UTC 时间。 */
  public record Detail(String id,Map<String,String> values,Audit audit) {}
  /**
   * 显式启停命令；停用仅阻止未来引用，不删除历史资料。
   *
   * @param enabled true 启用、false 停用，必填，无默认值
   *
   * @param version 当前非负版本，成功后递增 1；陈旧版本返回 409
   */
  public record Status(Boolean enabled,long version) {}
  /** 已授权档案；organizationId 为可信业务归属，fieldRestrictions 为服务端只读限制提示，不能作为放行凭据。version 为乐观锁版本。 */
  public record Row(String id,String code,String name,Map<String,String> attributes,boolean enabled,long version,Audit audit,List<Detail> contacts,List<Detail> bankAccounts,String organizationId,Map<String,Map<String,com.appnexus.platform.authorization.FieldSecurity.Mode>> fieldRestrictions) {
    /** 旧非保护资源构造不产生额外限制，客商响应由保护执行器构造完整投影。 */
    public Row(String id,String code,String name,Map<String,String> attributes,boolean enabled,long version,Audit audit,List<Detail> contacts,List<Detail> bankAccounts){this(id,code,name,attributes,enabled,version,audit,contacts,bankAccounts,null,Map.of());}
    /** 非客商摘要没有关联明细。 */
    public Row(String id,String code,String name,Map<String,String> attributes,boolean enabled,long version,Audit audit){this(id,code,name,attributes,enabled,version,audit,List.of(),List.of());}
    /** 内部摘要未读取审计时显式为空；不构造历史事实。 */
    public Row(String id,String code,String name,Map<String,String> attributes,boolean enabled,long version){this(id,code,name,attributes,enabled,version,null);}
  }
  /** UTC 审计时间点及实际账号 UUID；null 表示历史未知或无账号系统执行，全部只读。 */
  public record Audit(String createdBy,String createdAt,String updatedBy,String updatedAt) {}
  /** 服务端分页结果；total 为相同租户、权限和筛选条件下的真实总数。 */
  public record Page(List<Row> rows,long total) {}
  /** 稳定业务拒绝，不向客户端回显 SQL、联系方式或异常原文。 */
  public static final class Rejection extends RuntimeException {
    /** 对应 HTTP 状态码。 */
    public final int status;
    /**
   * @param status HTTP 状态码
   * @param code 公开稳定错误码 */
    public Rejection(int status,String code) { super(code);this.status=status; }
  }
}
