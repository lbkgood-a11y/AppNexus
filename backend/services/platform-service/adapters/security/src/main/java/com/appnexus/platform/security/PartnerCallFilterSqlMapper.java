package com.appnexus.platform.security;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** PartnerCallFilter固定SQL映射；没有接收SQL、表名或列名的通用执行入口。 */
@Mapper
@PlatformScoped(tables={"integration_call_audit"})
public interface PartnerCallFilterSqlMapper {
 /**
  * statement1 为原PartnerCallFilter固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Update("UPDATE integration_call_audit SET status=#{p0} WHERE id=#{p1}")
 int statement1(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 生成键仅来自数据库，不使用SELECT LAST_INSERT_ID跨连接查询。 */
 final class Start {
  private Long id;public Long getId(){return id;}public void setId(Long id){this.id=id;}
  public final String tenant,client,operation,trace;
  /** @param tenant Token验证租户 @param client 验证机密客户端 @param operation 固定操作摘要 @param trace 服务端追踪码 */
  public Start(String tenant,String client,String operation,String trace){this.tenant=tenant;this.client=client;this.operation=operation;this.trace=trace;}
 }
 /** @param start 可信调用审计起始摘要，状态0表示结果未确认 @return 插入行数，生成键回填start.id */
 @Insert("INSERT INTO integration_call_audit(tenant_id,client_id,operation,trace_id,status) VALUES(UUID_TO_BIN(#{tenant}),#{client},#{operation},#{trace},0)")
 @Options(useGeneratedKeys=true,keyProperty="id") int start(Start start);
}
