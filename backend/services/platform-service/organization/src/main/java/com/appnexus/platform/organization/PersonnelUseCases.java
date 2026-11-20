package com.appnexus.platform.organization;
import com.appnexus.platform.organization.port.personnel.*;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.configuration.BusinessValueCatalog;
/** 无框架人员用例装配入口；外部模块不导入internal实现。 */
public final class PersonnelUseCases {
 private PersonnelUseCases(){}
 /** @param records 人员记录输出端口 @param access 权威授权 @param transaction 完整用例事务 @param dictionaries 字典公开契约 @return 人员基础管理用例 */
 public static PersonManagement persons(PersonRepository records,AccessAuthorization access,PersonnelTransaction transaction,BusinessValueCatalog dictionaries){return new com.appnexus.platform.organization.internal.application.personnel.PersonDataService(records,access,transaction,dictionaries);}
 /** @param access 权威授权 @param directory 组织公开目录 @param transaction 完整用例事务 @param dictionaries 字典公开契约 @param records 人员明细存储 @param content 固定明细编解码 @param identities 权威可代表身份 @return 人员明细及关联管理用例 */
 public static PersonnelManagement personnel(AccessAuthorization access,PersonnelDirectory directory,PersonnelTransaction transaction,BusinessValueCatalog dictionaries,PersonnelRepository records,PersonnelContent content,PersonnelExecution identities){return new com.appnexus.platform.organization.internal.application.personnel.PersonnelService(access,directory,transaction,dictionaries,records,content,identities);}
}
