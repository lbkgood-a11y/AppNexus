package com.appnexus.platform.organization;

import java.util.*;

/** 人员固定类型及公开输入；客户端不能指定表名、租户、操作者或任意扩展字段。 */
public final class PersonData {
  private PersonData() {}

  /**
   * 固定人员类型与字段契约；领域字段均为文本，不携带数据库表列映射。
   * 人员不承载账号凭据和任职授权，仓库不承载库存，商品不承载公司价格。
   */
  public enum Kind {
    PERSON("person", "Person", List.of("englishName", "phone", "email", "gender", "birthDate", "ethnicity", "nativePlace", "politicalStatus", "identityType", "identityNumber", "workNature", "bloodType", "emergencyContact", "emergencyPhone", "maritalStatus", "hobbies", "skillLevel", "educationLevel", "employmentStatus", "remarks"));
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
   * @return 此类独立权限资源，不借用组织、账号或配置管理权限 */
    public String resource() { return "masterdata."+path.replace("-",""); }
  }

  /**
   * 档案输入；编码创建后不可修改，停用通过单独操作，避免编辑权限隐式获得启停权。
   *
   * @param code 租户内同类唯一编码，1–64 个 ASCII 字母、数字、点、下划线或短横线；国别地区为两个大写字母
   *
   * @param name 中文或业务名称，去除首尾空白后 1–200 字符，必填
   *
   * @param attributes 固定领域字段；缺省为全空文本，单项最长 500 字符，邮箱最长 200 字符
   *
   * @param version 修改时必须传当前非负版本；新建使用 0，不影响新建结果
   */
  public record Input(String code,String name,Map<String,String> attributes,long version) {}
  /**
   * 显式启停命令；停用仅阻止未来引用，不删除历史资料。
   *
   * @param enabled true 启用、false 停用，必填，无默认值
   *
   * @param version 当前非负版本，成功后递增 1；陈旧版本返回 409
   */
  public record Status(Boolean enabled,long version) {}
  /** 已授权档案；version 为乐观锁版本，不接受客户端覆盖。 */
  public record Row(String id,String code,String name,Map<String,String> attributes,boolean enabled,long version,Audit audit) {
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
