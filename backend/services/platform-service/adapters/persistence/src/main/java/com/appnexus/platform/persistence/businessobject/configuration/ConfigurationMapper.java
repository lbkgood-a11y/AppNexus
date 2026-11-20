package com.appnexus.platform.persistence.businessobject.configuration;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.appnexus.persistence.mybatis.PlatformScoped;

/** 平台对象配置固定 SQL 映射；不接收 SQL 或列名，锁、版本与审计复用用例事务。 */
@Mapper
@PlatformScoped(tables={"business_object_audit","business_object_configuration","business_object_configuration_version","business_object_entry_control_audit","business_object_page_relationships","business_object_resolution","business_object_tenant_grant","business_object_type"})
public interface ConfigurationMapper {
  /**
   * typeEnabled 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param type 已登记业务对象类型编码，不是表名
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT COALESCE(MAX(enabled),FALSE) FROM business_object_type WHERE object_type=#{type}")
  Boolean typeEnabled(@Param("type") String type);

  /**
   * publishedConfiguration 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT published FROM business_object_configuration WHERE tenant_id=#{tenant} AND object_type=#{type} AND published_version>0")
  List<Map<String, Object>> publishedConfiguration(@Param("tenant") String tenant, @Param("type") String type);

  /**
   * typeState 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param type 已登记业务对象类型编码，不是表名
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT enabled,revision FROM business_object_type WHERE object_type=#{type}")
  List<Map<String, Object>> typeState(@Param("type") String type);

  /**
   * pageRelationshipState 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param type 已登记业务对象类型编码，不是表名
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT revision,relationships FROM business_object_page_relationships WHERE object_type=#{type}")
  List<Map<String, Object>> pageRelationshipState(@Param("type") String type);

  /**
   * createPageRelationships 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param type 已登记业务对象类型编码，不是表名
   * @param relationships 已通过用例校验的页面关系 JSON
   * @param disabled 已通过用例校验的禁用操作 JSON
   * @param actor 可信账号 UUID；系统执行按原契约允许空值
   * @return 受影响行数；由调用方复核冲突及回滚
   */
  @Insert("INSERT IGNORE INTO business_object_page_relationships(object_type,relationships,disabled_operations,updated_by) VALUES(#{type},#{relationships},#{disabled},#{actor})")
  int createPageRelationships(@Param("type") String type, @Param("relationships") String relationships, @Param("disabled") String disabled, @Param("actor") String actor);

  /**
   * updatePageRelationships 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param relationships 已通过用例校验的页面关系 JSON
   * @param disabled 已通过用例校验的禁用操作 JSON
   * @param actor 可信账号 UUID；系统执行按原契约允许空值
   * @param type 已登记业务对象类型编码，不是表名
   * @param revision 期望修订号，用于 CAS 并发检查
   * @return 受影响行数；由调用方复核冲突及回滚
   */
  @Update("UPDATE business_object_page_relationships SET relationships=#{relationships},disabled_operations=#{disabled},updated_by=#{actor},revision=revision+1 WHERE object_type=#{type} AND revision=#{revision}")
  int updatePageRelationships(@Param("relationships") String relationships, @Param("disabled") String disabled, @Param("actor") String actor, @Param("type") String type, @Param("revision") long revision);

  /**
   * appendEntryControl 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param type 已登记业务对象类型编码，不是表名
   * @param operation 已登记业务操作编码
   * @param enabled 启用状态；false 不影响历史回执
   * @param reason 操作原因，不得记录凭据或业务敏感原文
   * @param actor 可信账号 UUID；系统执行按原契约允许空值
   * @return 受影响行数；由调用方复核冲突及回滚
   */
  @Insert("INSERT INTO business_object_entry_control_audit(object_type,operation_id,enabled,reason,actor) VALUES(#{type},#{operation},#{enabled},#{reason},#{actor})")
  int appendEntryControl(@Param("type") String type, @Param("operation") String operation, @Param("enabled") boolean enabled, @Param("reason") String reason, @Param("actor") String actor);

  /**
   * disabledOperations 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param type 已登记业务对象类型编码，不是表名
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT disabled_operations FROM business_object_page_relationships WHERE object_type=#{type}")
  List<Map<String, Object>> disabledOperations(@Param("type") String type);

  /**
   * ensureType 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param type 已登记业务对象类型编码，不是表名
   * @return 受影响行数；由调用方复核冲突及回滚
   */
  @Insert("INSERT IGNORE INTO business_object_type(object_type) VALUES(#{type})")
  int ensureType(@Param("type") String type);

  /**
   * updateTypeState 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param enabled 启用状态；false 不影响历史回执
   * @param type 已登记业务对象类型编码，不是表名
   * @param revision 期望修订号，用于 CAS 并发检查
   * @return 受影响行数；由调用方复核冲突及回滚
   */
  @Update("UPDATE business_object_type SET enabled=#{enabled},revision=revision+1 WHERE object_type=#{type} AND revision=#{revision}")
  int updateTypeState(@Param("enabled") boolean enabled, @Param("type") String type, @Param("revision") long revision);

  /**
   * tenantGrants 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param type 已登记业务对象类型编码，不是表名
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT tenant_id,enabled FROM business_object_tenant_grant WHERE object_type=#{type} ORDER BY tenant_id")
  List<Map<String, Object>> tenantGrants(@Param("type") String type);

  /**
   * saveTenantGrant 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @param enabled 启用状态；false 不影响历史回执
   * @return 受影响行数；由调用方复核冲突及回滚
   */
  @Insert("INSERT INTO business_object_tenant_grant(tenant_id,object_type,enabled) VALUES(#{tenant},#{type},#{enabled}) ON DUPLICATE KEY UPDATE enabled=VALUES(enabled)")
  int saveTenantGrant(@Param("tenant") String tenant, @Param("type") String type, @Param("enabled") boolean enabled);

  /**
   * configuration 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT revision,published_version,draft FROM business_object_configuration WHERE tenant_id=#{tenant} AND object_type=#{type}")
  List<Map<String, Object>> configuration(@Param("tenant") String tenant, @Param("type") String type);

  /**
   * createDraft 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @param draft 通过用例校验的配置草稿 JSON
   * @return 受影响行数；由调用方复核冲突及回滚
   */
  @Insert("INSERT IGNORE INTO business_object_configuration(tenant_id,object_type,draft) VALUES(#{tenant},#{type},#{draft})")
  int createDraft(@Param("tenant") String tenant, @Param("type") String type, @Param("draft") String draft);

  /**
   * updateDraft 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param draft 通过用例校验的配置草稿 JSON
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @param revision 期望修订号，用于 CAS 并发检查
   * @return 受影响行数；由调用方复核冲突及回滚
   */
  @Update("UPDATE business_object_configuration SET draft=#{draft},revision=revision+1 WHERE tenant_id=#{tenant} AND object_type=#{type} AND revision=#{revision}")
  int updateDraft(@Param("draft") String draft, @Param("tenant") String tenant, @Param("type") String type, @Param("revision") long revision);

  /**
   * lockConfiguration 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT * FROM business_object_configuration WHERE tenant_id=#{tenant} AND object_type=#{type} FOR UPDATE")
  List<Map<String, Object>> lockConfiguration(@Param("tenant") String tenant, @Param("type") String type);

  /**
   * appendConfigurationVersion 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @param version 配置或规则版本，沿用调用用例的版本检查
   * @param descriptorVersion 代码声明版本，用于配置兼容性验证
   * @param bindings 已校验的不可变绑定 JSON 快照
   * @param actor 可信账号 UUID；系统执行按原契约允许空值
   * @return 受影响行数；由调用方复核冲突及回滚
   */
  @Insert("INSERT INTO business_object_configuration_version(tenant_id,object_type,version,descriptor_version,bindings,actor) VALUES(#{tenant},#{type},#{version},#{descriptorVersion},#{bindings},#{actor})")
  int appendConfigurationVersion(@Param("tenant") String tenant, @Param("type") String type, @Param("version") int version, @Param("descriptorVersion") int descriptorVersion, @Param("bindings") String bindings, @Param("actor") String actor);

  /**
   * publishConfiguration 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param draft 通过用例校验的配置草稿 JSON
   * @param published 已校验的配置发布 JSON 快照
   * @param version 配置或规则版本，沿用调用用例的版本检查
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @return 受影响行数；由调用方复核冲突及回滚
   */
  @Update("UPDATE business_object_configuration SET draft=#{draft},published=#{published},published_version=#{version},revision=revision+1 WHERE tenant_id=#{tenant} AND object_type=#{type}")
  int publishConfiguration(@Param("draft") String draft, @Param("published") String published, @Param("version") int version, @Param("tenant") String tenant, @Param("type") String type);

  /**
   * resolutionByRequest 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param actor 可信账号 UUID；系统执行按原契约允许空值
   * @param request 当前账号与租户内的幂等请求 UUID
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT * FROM business_object_resolution WHERE tenant_id=#{tenant} AND actor=#{actor} AND request_key=#{request}")
  List<Map<String, Object>> resolutionByRequest(@Param("tenant") String tenant, @Param("actor") String actor, @Param("request") String request);

  /**
   * sharePublishedConfiguration 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT published,published_version FROM business_object_configuration WHERE tenant_id=#{tenant} AND object_type=#{type} AND published_version>0 FOR SHARE")
  List<Map<String, Object>> sharePublishedConfiguration(@Param("tenant") String tenant, @Param("type") String type);

  /**
   * descriptorVersion 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @param version 配置或规则版本，沿用调用用例的版本检查
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT descriptor_version FROM business_object_configuration_version WHERE tenant_id=#{tenant} AND object_type=#{type} AND version=#{version}")
  Integer descriptorVersion(@Param("tenant") String tenant, @Param("type") String type, @Param("version") Object version);

  /**
   * createResolution 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param id 服务端生成或已校验的记录 UUID
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param actor 可信账号 UUID；系统执行按原契约允许空值
   * @param request 当前账号与租户内的幂等请求 UUID
   * @param type 已登记业务对象类型编码，不是表名
   * @param object 已授权业务记录标识，不作为动态表名
   * @param basis 业务依据版本，沿用聚合版本协议
   * @param version 配置或规则版本，沿用调用用例的版本检查
   * @param definition 已发布专业流程定义标识
   * @param definitionVersion 已发布流程版本；非工作流按契约允许 null
   * @param facts 已授权投影的业务事实 JSON，不接受客户端自报事实
   * @param operation 已登记业务操作编码
   * @param service 受控专业服务编码
   * @param scene 已声明打印场景编码，非打印允许 null
   * @param contract 已声明数据契约编码，按专业能力可空
   * @return 受影响行数；由调用方复核冲突及回滚
   */
  @Insert("INSERT IGNORE INTO business_object_resolution(id,tenant_id,actor,request_key,object_type,object_id,basis_version,configuration_version,definition_id,definition_version,facts,operation_code,service,scene_code,data_contract) VALUES(#{id},#{tenant},#{actor},#{request},#{type},#{object},#{basis},#{version},#{definition},#{definitionVersion},#{facts},#{operation},#{service},#{scene},#{contract})")
  int createResolution(@Param("id") String id, @Param("tenant") String tenant, @Param("actor") String actor, @Param("request") String request, @Param("type") String type, @Param("object") String object, @Param("basis") long basis, @Param("version") Object version, @Param("definition") String definition, @Param("definitionVersion") Integer definitionVersion, @Param("facts") String facts, @Param("operation") String operation, @Param("service") String service, @Param("scene") String scene, @Param("contract") String contract);

  /**
   * requiredResolutionByRequest 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param actor 可信账号 UUID；系统执行按原契约允许空值
   * @param request 当前账号与租户内的幂等请求 UUID
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT * FROM business_object_resolution WHERE tenant_id=#{tenant} AND actor=#{actor} AND request_key=#{request}")
  Map<String, Object> requiredResolutionByRequest(@Param("tenant") String tenant, @Param("actor") String actor, @Param("request") String request);

  /**
   * publishedConfigurationState 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT published,published_version FROM business_object_configuration WHERE tenant_id=#{tenant} AND object_type=#{type} AND published_version>0")
  List<Map<String, Object>> publishedConfigurationState(@Param("tenant") String tenant, @Param("type") String type);

  /**
   * resolutionById 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param actor 可信账号 UUID；系统执行按原契约允许空值
   * @param id 服务端生成或已校验的记录 UUID
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT * FROM business_object_resolution WHERE tenant_id=#{tenant} AND actor=#{actor} AND id=#{id}")
  List<Map<String, Object>> resolutionById(@Param("tenant") String tenant, @Param("actor") String actor, @Param("id") String id);

  /**
   * tenantGranted 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT EXISTS(SELECT 1 FROM business_object_tenant_grant WHERE tenant_id=#{tenant} AND object_type=#{type} AND enabled=TRUE)")
  Boolean tenantGranted(@Param("tenant") String tenant, @Param("type") String type);

  /**
   * appendAudit 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param actor 可信账号 UUID；系统执行按原契约允许空值
   * @param type 已登记业务对象类型编码，不是表名
   * @param action 受控审计动作编码
   * @return 受影响行数；由调用方复核冲突及回滚
   */
  @Insert("INSERT INTO business_object_audit(tenant_id,actor,object_type,action) VALUES(#{tenant},#{actor},#{type},#{action})")
  int appendAudit(@Param("tenant") String tenant, @Param("actor") String actor, @Param("type") String type, @Param("action") String action);
}
