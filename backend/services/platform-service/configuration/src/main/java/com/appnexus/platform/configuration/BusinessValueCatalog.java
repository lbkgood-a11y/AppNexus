package com.appnexus.platform.configuration;
import java.util.*;
/** 业务字典公开读取及引用端口；调用方先完成业务对象权限、可信租户与版本校验，不能直接暴露任意字典查询。 */
public interface BusinessValueCatalog {
  /** 客商分类与字典编码的唯一绑定；币种为共享业务字典，国家地区继续使用独立主数据。 */
  Map<String,String> PARTNER=Map.of("partnerType","partner_type","nature","partner_nature","customerLevel","customer_level","contactType","partner_contact_type","registeredCurrency","currency","currency","currency","staffSize","staff_size");
  /** 商品计量单位，与实际业务计量值一致，不参与金额或数量换算。 */
  Map<String,String> PRODUCT=Map.of("unit","product_unit");
  /** 仓库类型与经营模式由租户维护；布尔状态、组织和合作方实体不属于分类字典。 */
  Map<String,String> WAREHOUSE=Map.of("warehouseType","warehouse_type","operatingMode","warehouse_operating_mode");
  /** 人员分类与字典编码绑定；布尔状态和权限、协议常量不属于可扩展字典。 */
  Map<String,String> PERSON=Map.ofEntries(Map.entry("gender","person_gender"),Map.entry("identityType","identity_type"),Map.entry("workNature","work_nature"),Map.entry("bloodType","blood_type"),Map.entry("maritalStatus","marital_status"),Map.entry("employmentStatus","employment_status"),Map.entry("certificateStatus","certificate_status"),Map.entry("recordType","reward_penalty_type"),Map.entry("educationLevel","education_level"),Map.entry("skillLevel","skill_level"),Map.entry("ethnicity","ethnicity"),Map.entry("politicalStatus","political_status"),Map.entry("certificateType","certificate_type"),Map.entry("contactType","person_contact_type"),Map.entry("addressType","address_type"),Map.entry("degree","academic_degree"));
  /** 字典选项含停用项以解析已授权历史值；停用项不能作为新的选择。 */
  record Option(String value,String name,boolean enabled){}
  /**
   * 只读字典，不初始化数据也不注册业务引用。
   * @param tenant 可信租户 UUID
   * @param bindings 构建内字段绑定，禁止直接传请求字段
   * @return 字段到真实字典选项；缺失字典为空，不回退硬编码
   */
  Map<String,List<Option>> options(UUID tenant,Map<String,String> bindings);

  /** 字典编码与持久化业务值共同标识名称。 */
  record NameKey(String dictionary,String value){}
  /** @param tenant 由已授权业务响应绑定的可信租户
   * @param keys 单批已声明的字典引用，最多 200 个
   * @return 含未删除的停用历史项名称，可见性沿用原业务读取 */
  Map<NameKey,String> names(UUID tenant,List<NameKey> keys);
  /**
   * 在业务写事务中复核启用状态并登记引用；已有且未变的停用值可保留，未知新值拒绝。
   * @param tenant 可信租户 UUID
   * @param actor 可信认证账号，系统执行可空，不伪造主体
   * @param consumer 固定业务对象编码，不含敏感正文
   * @param reference 业务对象 UUID 或父子组合标识，最长200字符
   * @param bindings 构建内字段到字典编码绑定
   * @param values 新业务字段值，多选字段由调用者拆分后分别校验
   * @param previous 授权后从数据库读取的旧字段值，不信任客户端旧值
   * @return 全部值合法时 true；停用、未知或其他租户新值返回 false
   */
  boolean reference(UUID tenant,UUID actor,String consumer,String reference,Map<String,String> bindings,Map<String,String> values,Map<String,String> previous);
}
