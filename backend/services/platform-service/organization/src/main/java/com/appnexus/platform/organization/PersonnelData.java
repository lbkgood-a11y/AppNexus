package com.appnexus.platform.organization;
import com.appnexus.platform.organization.PersonData;
import java.util.*;
import com.appnexus.platform.configuration.BusinessValueCatalog;
import com.appnexus.platform.organization.PersonData.*;
/** 人员公开用例稳定数据契约；不包含HTTP、数据库或内部实现类型。 */
public final class PersonnelData {
 private PersonnelData(){}
/**
   * 全量详情输入仅保存固定字段；父档案版本用于所有明细写入，避免不同页签并发覆盖。
   *
   * @param version 当前人员档案非负版本，无默认值；成功递增 1
   *
   * @param values 固定明细白名单文本值；缺省空，必填规则由分类声明
   */
  public record DetailInput(long version,Map<String,String> values){}
/**
   * 人员与租户账号关联，不创建组织任职，也不复制其角色权限。
   *
   *
   * @param memberId 当前租户启用账号的成员 UUID，必填；同用户可关联多个人员
   *
   * @param version 当前人员版本，非负，无默认值
   */
  public record AssignmentInput(String memberId,long version){}
/** 已授权明细，id 不作为单独访问凭据，必须同时限定租户、人员及分类。 */
  public record Detail(String id,String section,Map<String,String> values){}
/** 账号人员关联摘要；active 是当前组织目录复核结果，不等于业务授权。 */
  public record Assignment(String id,String memberId,String userName,boolean active){}
/** 人员详情的真实审计信息；历史未记录字段为空，不伪造创建人或创建时间。 */
  public record Audit(String createdBy,String createdByName,String createdAt,String updatedBy,String updatedByName,String updatedAt){}
/** 详情聚合只在具有人员读取权限时返回，不在普通列表加载身份证件、家庭及履历等正文。 */
  public record Profile(Row person,Audit audit,List<Detail> details,List<Assignment> assignments,
      Map<String,com.appnexus.platform.authorization.FieldSecurity.Mode> detailFieldRestrictions){
    /** 保留原单域人员详情构造。 */
    public Profile(Row person,Audit audit,List<Detail> details,List<Assignment> assignments){
      this(person,audit,details,assignments,Map.of());
    }
  }
/** 只读人员目录和字典，不需要字典维护权限。 */
  public record Options(List<PersonnelDirectory.Organization> organizations,List<PersonnelDirectory.Position> positions,List<PersonnelDirectory.User> users,Map<String,List<BusinessValueCatalog.Option>> dictionaries){}
/** 选择本次人员与组织；均为规范 UUID，无缺省自动选择。 */
  public record IdentitySelection(String personId,String organizationId){}
}
