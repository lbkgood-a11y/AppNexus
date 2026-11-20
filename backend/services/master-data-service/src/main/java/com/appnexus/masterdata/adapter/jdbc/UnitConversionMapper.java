package com.appnexus.masterdata.adapter.jdbc;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.*;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
/**
 * 单位换算固定多表Mapper；只访问本主数据上下文表，不接受SQL或动态列。
 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,tables={"md_product_unit_conversion","md_product_unit"},statements={"list","lockUnit","reverse","create","relation","status","calculation"})
public interface UnitConversionMapper {
  /**
 * list 固定SQL；可信租户由执行拦截器复核，行锁及CAS沿用业务事务。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
  @Select("SELECT BIN_TO_UUID(x.id) id,BIN_TO_UUID(source_unit_id) sourceUnitId,BIN_TO_UUID(target_unit_id) targetUnitId,s.code sourceCode,t.code targetCode,CAST(factor AS CHAR) factor,x.enabled,x.version FROM md_product_unit_conversion x JOIN md_product_unit s ON s.tenant_id=x.tenant_id AND s.id=x.source_unit_id JOIN md_product_unit t ON t.tenant_id=x.tenant_id AND t.id=x.target_unit_id WHERE x.tenant_id=#{__verifiedTenant} ORDER BY s.code,t.code")
  List<Map<String,Object>> list(@Param("__verifiedTenant") byte[] tenant);

  /**
 * lockUnit 固定SQL；可信租户由执行拦截器复核，行锁及CAS沿用业务事务。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param id 已由调用用例校验的记录UUID
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
  @Select("SELECT dimension_code dimensionCode,enabled FROM md_product_unit WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) FOR UPDATE")
  List<Map<String,Object>> lockUnit(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /**
 * reverse 固定SQL；可信租户由执行拦截器复核，行锁及CAS沿用业务事务。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param source 当前租户来源单位UUID
 * @param target 当前租户目标单位UUID
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
  @Select("SELECT factor FROM md_product_unit_conversion WHERE tenant_id=#{__verifiedTenant} AND source_unit_id=UUID_TO_BIN(#{source}) AND target_unit_id=UUID_TO_BIN(#{target}) AND enabled=TRUE")
  List<Map<String,Object>> reverse(@Param("__verifiedTenant") byte[] tenant,@Param("source") String source,@Param("target") String target);

  /**
 * create 固定SQL；可信租户由执行拦截器复核，行锁及CAS沿用业务事务。
 * @param id 已由调用用例校验的记录UUID
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param source 当前租户来源单位UUID
 * @param target 当前租户目标单位UUID
 * @param factor 严格正数换算系数，精度及上下限由领域规则校验
 * @param actor 来自可信上下文的实际账号UUID，不冒充被代表人员
 * @return 原存储契约定义的结果；并发或引用冲突由调用用例复核
 */
  @Insert("INSERT INTO md_product_unit_conversion(id,tenant_id,source_unit_id,target_unit_id,factor,created_by,updated_by) VALUES(UUID_TO_BIN(#{id}),#{__verifiedTenant},UUID_TO_BIN(#{source}),UUID_TO_BIN(#{target}),#{factor},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))")
  int create(@Param("id") String id,@Param("__verifiedTenant") byte[] tenant,@Param("source") String source,@Param("target") String target,@Param("factor") BigDecimal factor,@Param("actor") String actor);

  /**
 * relation 固定SQL；可信租户由执行拦截器复核，行锁及CAS沿用业务事务。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param id 已由调用用例校验的记录UUID
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
  @Select("SELECT BIN_TO_UUID(source_unit_id) sourceId,BIN_TO_UUID(target_unit_id) targetId,factor FROM md_product_unit_conversion WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})")
  List<Map<String,Object>> relation(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /**
 * status 固定SQL；可信租户由执行拦截器复核，行锁及CAS沿用业务事务。
 * @param enabled 目标启用状态，不改变其他租户关系
 * @param actor 来自可信上下文的实际账号UUID，不冒充被代表人员
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param id 已由调用用例校验的记录UUID
 * @param version 当前聚合非负版本，用于CAS校验
 * @return 原存储契约定义的结果；并发或引用冲突由调用用例复核
 */
  @Update("UPDATE md_product_unit_conversion x SET enabled=#{enabled},version=version+1,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND version=#{version} AND enabled<>#{enabled} AND (#{enabled}=FALSE OR (EXISTS(SELECT 1 FROM md_product_unit u WHERE u.tenant_id=x.tenant_id AND u.id=x.source_unit_id AND u.enabled=TRUE) AND EXISTS(SELECT 1 FROM md_product_unit u WHERE u.tenant_id=x.tenant_id AND u.id=x.target_unit_id AND u.enabled=TRUE)))")
  int status(@Param("enabled") boolean enabled,@Param("actor") String actor,@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("version") long version);

  /**
 * calculation 固定SQL；可信租户由执行拦截器复核，行锁及CAS沿用业务事务。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param id 已由调用用例校验的记录UUID
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
  @Select("SELECT x.factor,t.precision_scale scale FROM md_product_unit_conversion x JOIN md_product_unit s ON s.tenant_id=x.tenant_id AND s.id=x.source_unit_id JOIN md_product_unit t ON t.tenant_id=x.tenant_id AND t.id=x.target_unit_id WHERE x.tenant_id=#{__verifiedTenant} AND x.id=UUID_TO_BIN(#{id}) AND x.enabled=TRUE AND s.enabled=TRUE AND t.enabled=TRUE")
  List<Map<String,Object>> calculation(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);
}
