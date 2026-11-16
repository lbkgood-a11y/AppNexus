package com.appnexus.platform.organization;
import java.util.*;
/** 组织模块只通过公开人员目录验证任职人员，不直接读取主数据实体表。 */
public interface PersonnelCatalog {
 /** 当前租户启用在职人员的最小目录。 */
 record Person(UUID id,String displayName){}
 /**
 * @return 同租户启用且在职人员；不返回联系方式、证件、履历或账号凭据 */
 List<Person> active(UUID tenantId);
 /** 在已授权组织事务中锁定并推进人员版本，保留实际账号审计；人员不可用抛出异常。 */
 void touch(UUID tenant,UUID person,UUID actor);
}
