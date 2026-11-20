package com.appnexus.platform.organization;
import com.appnexus.platform.organization.PersonData;
import java.util.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.platform.organization.PersonData.*;
import com.appnexus.platform.organization.PersonnelData.*;
/** 人员公开业务用例，不允许调用方直接访问SQL或内部实现。 */
public interface PersonManagement {
 /** 人员公开操作；可信主体、独立权限、范围与事务约束由真实用例复核。 */
 Page list(VerifiedTenantContext c,Kind kind,String keyword,Boolean enabled,int page,int size);
 /** 人员公开操作；可信主体、独立权限、范围与事务约束由真实用例复核。 */
 Map<String,String> create(VerifiedTenantContext c,Kind kind,Input input);
 /** 人员公开操作；可信主体、独立权限、范围与事务约束由真实用例复核。 */
 void update(VerifiedTenantContext c,Kind kind,String id,Input input);
 /** 人员公开操作；可信主体、独立权限、范围与事务约束由真实用例复核。 */
 void setStatus(VerifiedTenantContext c,Kind kind,String id,Status input);
}
