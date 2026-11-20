package com.appnexus.platform.persistence.businessobject.coding;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.appnexus.persistence.mybatis.PlatformScoped;

/** 编码规则、绑定、流水及回执固定 SQL；调用方先通过编码用例准入，不向管理端开放 SQL。 */
@Mapper
@PlatformScoped(tables={"business_object_encoder","business_object_code_issue","business_object_code_counter","coding_rule_version"})
public interface CodingMapper {
  /**
   * configurations 固定存储操作；租户、锁、版本及事务沿用编码端口契约。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT encoder_key AS encoderKey,field_key AS field,name,rule_key AS ruleKey,rule_version AS ruleVersion,enabled,revision, BIN_TO_UUID(created_by) AS createdBy,created_at AS createdAt,BIN_TO_UUID(updated_by) AS updatedBy,updated_at AS updatedAt FROM business_object_encoder WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND object_type=#{type} ORDER BY encoder_key")
  List<Map<String,Object>> configurations(@Param("tenant") String tenant,@Param("type") String type);

  /**
   * options 固定存储操作；租户、锁、版本及事务沿用编码端口契约。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @param field 对象已声明的编码字段键
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT encoder_key AS encoderKey,name FROM business_object_encoder WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND object_type=#{type} AND field_key=#{field} AND enabled=TRUE ORDER BY encoder_key")
  List<Map<String,Object>> options(@Param("tenant") String tenant,@Param("type") String type,@Param("field") String field);

  /**
   * lock 固定存储操作；租户、锁、版本及事务沿用编码端口契约。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @param key 租户内稳定编码器或规则键，不接受 SQL 标识符
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT field_key,enabled,revision,rule_key,rule_version FROM business_object_encoder WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND object_type=#{type} AND encoder_key=#{key} FOR UPDATE")
  List<Map<String,Object>> lock(@Param("tenant") String tenant,@Param("type") String type,@Param("key") String key);

  /**
   * hasIssues 固定存储操作；租户、锁、版本及事务沿用编码端口契约。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @param key 租户内稳定编码器或规则键，不接受 SQL 标识符
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT EXISTS(SELECT 1 FROM business_object_code_issue WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND object_type=#{type} AND encoder_key=#{key})")
  Boolean hasIssues(@Param("tenant") String tenant,@Param("type") String type,@Param("key") String key);

  /**
   * insertEncoder 固定存储操作；租户、锁、版本及事务沿用编码端口契约。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @param key 租户内稳定编码器或规则键，不接受 SQL 标识符
   * @param field 对象已声明的编码字段键
   * @param name 显示名称，沿用业务用例的长度及非空约束
   * @param enabled 启用状态；false 不影响历史回执
   * @param ruleKey 租户内已发布的编码规则键
   * @param ruleVersion 明确规则版本，正整数，不自动追随最新版本
   * @param actor 可信账号 UUID；系统执行按原契约允许空值
   * @return 受影响行数；由调用方复核冲突及回滚
   */
  @Insert("INSERT INTO business_object_encoder(tenant_id,object_type,encoder_key,field_key,name,enabled,rule_key,rule_version,created_by,updated_by) VALUES(UUID_TO_BIN(#{tenant}),#{type},#{key},#{field},#{name},#{enabled},#{ruleKey},#{ruleVersion},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))")
  int insertEncoder(@Param("tenant") String tenant,@Param("type") String type,@Param("key") String key,@Param("field") String field,@Param("name") String name,@Param("enabled") boolean enabled,@Param("ruleKey") String ruleKey,@Param("ruleVersion") long ruleVersion,@Param("actor") String actor);

  /**
   * updateEncoder 固定存储操作；租户、锁、版本及事务沿用编码端口契约。
   * @param field 对象已声明的编码字段键
   * @param name 显示名称，沿用业务用例的长度及非空约束
   * @param enabled 启用状态；false 不影响历史回执
   * @param ruleKey 租户内已发布的编码规则键
   * @param ruleVersion 明确规则版本，正整数，不自动追随最新版本
   * @param actor 可信账号 UUID；系统执行按原契约允许空值
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @param key 租户内稳定编码器或规则键，不接受 SQL 标识符
   * @return 受影响行数；由调用方复核冲突及回滚
   */
  @Update("UPDATE business_object_encoder SET field_key=#{field},name=#{name},enabled=#{enabled},rule_key=#{ruleKey},rule_version=#{ruleVersion},revision=revision+1,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND object_type=#{type} AND encoder_key=#{key}")
  int updateEncoder(@Param("field") String field,@Param("name") String name,@Param("enabled") boolean enabled,@Param("ruleKey") String ruleKey,@Param("ruleVersion") long ruleVersion,@Param("actor") String actor,@Param("tenant") String tenant,@Param("type") String type,@Param("key") String key);

  /**
   * rules 固定存储操作；租户、锁、版本及事务沿用编码端口契约。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT rule_key AS ruleKey,rule_version AS ruleVersion,name,rule_json AS ruleJson,BIN_TO_UUID(created_by) AS createdBy,created_at AS createdAt,BIN_TO_UUID(updated_by) AS updatedBy,updated_at AS updatedAt FROM coding_rule_version WHERE tenant_id=UUID_TO_BIN(#{tenant}) ORDER BY rule_key,rule_version DESC")
  List<Map<String,Object>> rules(@Param("tenant") String tenant);

  /**
   * latestRuleVersion 固定存储操作；租户、锁、版本及事务沿用编码端口契约。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param key 租户内稳定编码器或规则键，不接受 SQL 标识符
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT rule_version FROM coding_rule_version WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND rule_key=#{key} ORDER BY rule_version DESC FOR UPDATE")
  List<Long> latestRuleVersion(@Param("tenant") String tenant,@Param("key") String key);

  /**
   * rule 固定存储操作；租户、锁、版本及事务沿用编码端口契约。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param key 租户内稳定编码器或规则键，不接受 SQL 标识符
   * @param version 配置或规则版本，沿用调用用例的版本检查
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT rule_json FROM coding_rule_version WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND rule_key=#{key} AND rule_version=#{version}")
  List<Map<String,Object>> rule(@Param("tenant") String tenant,@Param("key") String key,@Param("version") long version);

  /**
   * insertRule 固定存储操作；租户、锁、版本及事务沿用编码端口契约。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param key 租户内稳定编码器或规则键，不接受 SQL 标识符
   * @param version 配置或规则版本，沿用调用用例的版本检查
   * @param name 显示名称，沿用业务用例的长度及非空约束
   * @param ruleJson 通过编码规则校验的不可变 JSON
   * @param actor 可信账号 UUID；系统执行按原契约允许空值
   * @return 受影响行数；由调用方复核冲突及回滚
   */
  @Insert("INSERT INTO coding_rule_version(tenant_id,rule_key,rule_version,name,rule_json,created_by,updated_by) VALUES(UUID_TO_BIN(#{tenant}),#{key},#{version},#{name},#{ruleJson},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))")
  int insertRule(@Param("tenant") String tenant,@Param("key") String key,@Param("version") long version,@Param("name") String name,@Param("ruleJson") String ruleJson,@Param("actor") String actor);

  /**
   * issued 固定存储操作；租户、锁、版本及事务沿用编码端口契约。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param actor 可信账号 UUID；系统执行按原契约允许空值
   * @param request 当前账号与租户内的幂等请求 UUID
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT object_type,encoder_key,code FROM business_object_code_issue WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND actor_id=UUID_TO_BIN(#{actor}) AND request_key=UUID_TO_BIN(#{request})")
  List<Map<String,Object>> issued(@Param("tenant") String tenant,@Param("actor") String actor,@Param("request") String request);

  /**
   * now 固定存储操作；租户、锁、版本及事务沿用编码端口契约。
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT CURRENT_TIMESTAMP(6)")
  java.sql.Timestamp now();

  /**
   * ensureCounter 固定存储操作；租户、锁、版本及事务沿用编码端口契约。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @param key 租户内稳定编码器或规则键，不接受 SQL 标识符
   * @param period 服务端按规则与数据库 UTC 时间计算的流水周期
   * @param actor 可信账号 UUID；系统执行按原契约允许空值
   * @return 受影响行数；由调用方复核冲突及回滚
   */
  @Insert("INSERT INTO business_object_code_counter(tenant_id,object_type,encoder_key,period_key,created_by,updated_by) VALUES(UUID_TO_BIN(#{tenant}),#{type},#{key},#{period},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor})) ON DUPLICATE KEY UPDATE sequence_value=sequence_value")
  int ensureCounter(@Param("tenant") String tenant,@Param("type") String type,@Param("key") String key,@Param("period") String period,@Param("actor") String actor);

  /**
   * sequence 固定存储操作；租户、锁、版本及事务沿用编码端口契约。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @param key 租户内稳定编码器或规则键，不接受 SQL 标识符
   * @param period 服务端按规则与数据库 UTC 时间计算的流水周期
   * @return 原存储契约定义的查询结果；范围与字段保护由用例执行
   */
  @Select("SELECT sequence_value FROM business_object_code_counter WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND object_type=#{type} AND encoder_key=#{key} AND period_key=#{period} FOR UPDATE")
  Long sequence(@Param("tenant") String tenant,@Param("type") String type,@Param("key") String key,@Param("period") String period);

  /**
   * advanceCounter 固定存储操作；租户、锁、版本及事务沿用编码端口契约。
   * @param next 持有行锁后计算的下一个流水值，失败整笔回滚
   * @param actor 可信账号 UUID；系统执行按原契约允许空值
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param type 已登记业务对象类型编码，不是表名
   * @param key 租户内稳定编码器或规则键，不接受 SQL 标识符
   * @param period 服务端按规则与数据库 UTC 时间计算的流水周期
   * @return 受影响行数；由调用方复核冲突及回滚
   */
  @Update("UPDATE business_object_code_counter SET sequence_value=#{next},updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND object_type=#{type} AND encoder_key=#{key} AND period_key=#{period}")
  int advanceCounter(@Param("next") long next,@Param("actor") String actor,@Param("tenant") String tenant,@Param("type") String type,@Param("key") String key,@Param("period") String period);

  /**
   * insertIssue 固定存储操作；租户、锁、版本及事务沿用编码端口契约。
   * @param tenant 服务端可信租户 UUID，不可由页面自报覆盖
   * @param actor 可信账号 UUID；系统执行按原契约允许空值
   * @param request 当前账号与租户内的幂等请求 UUID
   * @param type 已登记业务对象类型编码，不是表名
   * @param key 租户内稳定编码器或规则键，不接受 SQL 标识符
   * @param field 对象已声明的编码字段键
   * @param code 已生成并校验唯一性的业务编码，失败不保存回执
   * @return 受影响行数；由调用方复核冲突及回滚
   */
  @Insert("INSERT INTO business_object_code_issue(tenant_id,actor_id,request_key,object_type,encoder_key,field_key,code,created_by,updated_by) VALUES(UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{request}),#{type},#{key},#{field},#{code},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))")
  int insertIssue(@Param("tenant") String tenant,@Param("actor") String actor,@Param("request") String request,@Param("type") String type,@Param("key") String key,@Param("field") String field,@Param("code") String code);
}
