package com.appnexus.masterdata.organization;
import com.appnexus.masterdata.organization.PersonData;
import java.util.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.masterdata.organization.PersonData.*;
import com.appnexus.masterdata.organization.PersonnelData.*;
/** 人员公开业务用例，不允许调用方直接访问SQL或内部实现。 */
public interface PersonnelManagement {
 /** 人员公开操作；可信主体、独立权限、范围与事务约束由真实用例复核。 */
 Options options(VerifiedTenantContext c);
 /** 人员公开操作；可信主体、独立权限、范围与事务约束由真实用例复核。 */
 Profile read(VerifiedTenantContext c,String id);
 /** 人员公开操作；可信主体、独立权限、范围与事务约束由真实用例复核。 */
 Map<String,String> saveDetail(VerifiedTenantContext c,String person,String sectionCode,String id,DetailInput input);
 /** 人员公开操作；可信主体、独立权限、范围与事务约束由真实用例复核。 */
 void deleteDetail(VerifiedTenantContext c,String person,String sectionCode,String id,long version);
 /** 人员公开操作；可信主体、独立权限、范围与事务约束由真实用例复核。 */
 Map<String,String> assign(VerifiedTenantContext c,String person,String id,AssignmentInput input);
 /** 人员公开操作；可信主体、独立权限、范围与事务约束由真实用例复核。 */
 void unassign(VerifiedTenantContext c,String person,String id,long version);
 /** 人员公开操作；可信主体、独立权限、范围与事务约束由真实用例复核。 */
 boolean canRepresent(VerifiedTenantContext c,String person,String organization);
 /** 人员公开操作；可信主体、独立权限、范围与事务约束由真实用例复核。 */
 java.util.List<com.appnexus.masterdata.organization.PersonnelExecution.Option> identities(VerifiedTenantContext c);
 /** 人员公开操作；可信主体、独立权限、范围与事务约束由真实用例复核。 */
 void verifyIdentity(VerifiedTenantContext c,IdentitySelection input);
}
