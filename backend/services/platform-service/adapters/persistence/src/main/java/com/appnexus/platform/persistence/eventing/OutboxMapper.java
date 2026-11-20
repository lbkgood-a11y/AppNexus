package com.appnexus.platform.persistence.eventing;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** ReliableEvents固定SQL映射；没有接收SQL、表名或列名的通用执行入口。 */
@Mapper
@PlatformScoped(tables={"SKIP","platform_event_consumption","platform_outbox"})
public interface OutboxMapper {
 /**
  * statement1 为原ReliableEvents固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p3 原调用第4个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Insert("INSERT INTO platform_outbox(id,tenant_id,event_type,aggregate_id) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),#{p2},#{p3})")
 int statement1(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /**
  * statement2 为原ReliableEvents固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Update("UPDATE platform_outbox SET status='MANUAL',failure_code='ATTEMPTS_EXHAUSTED',lease_token=NULL,lease_until=NULL WHERE attempts>=5 AND (status='PENDING' OR (status='PROCESSING' AND lease_until<=CURRENT_TIMESTAMP(6)))")
 int statement2();

 /**
  * statement3 为原ReliableEvents固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(tenant_id) tenant,event_type,aggregate_id,attempts FROM platform_outbox WHERE attempts<5 AND ((status='PENDING' AND available_at<=CURRENT_TIMESTAMP(6)) OR (status='PROCESSING' AND lease_until<=CURRENT_TIMESTAMP(6))) ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED")
 List<Map<String,Object>> statement3();

 /**
  * statement4 为原ReliableEvents固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Update("UPDATE platform_outbox SET status='PROCESSING',attempts=attempts+1,lease_token=#{p0},lease_until=CURRENT_TIMESTAMP(6)+INTERVAL 30 SECOND WHERE id=UUID_TO_BIN(#{p1})")
 int statement4(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * statement5 为原ReliableEvents固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT BIN_TO_UUID(tenant_id) tenant FROM platform_outbox WHERE id=UUID_TO_BIN(#{p0}) AND status='PROCESSING' AND lease_token=#{p1} AND lease_until>CURRENT_TIMESTAMP(6) FOR UPDATE")
 List<Map<String,Object>> statement5(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * statement6 为原ReliableEvents固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Insert("INSERT IGNORE INTO platform_event_consumption(tenant_id,event_id,consumer) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),#{p2})")
 int statement6(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /**
  * statement7 为原ReliableEvents固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Update("UPDATE platform_outbox SET status='DONE',lease_token=NULL,lease_until=NULL,failure_code=NULL WHERE id=UUID_TO_BIN(#{p0}) AND lease_token=#{p1}")
 int statement7(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * statement8 为原ReliableEvents固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p3 原调用第4个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p4 原调用第5个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Update("UPDATE platform_outbox SET status=#{p0},failure_code=#{p1},available_at=TIMESTAMPADD(SECOND,#{p2},CURRENT_TIMESTAMP(6)),lease_token=NULL,lease_until=NULL WHERE id=UUID_TO_BIN(#{p3}) AND lease_token=#{p4} AND status='PROCESSING'")
 int statement8(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4);

 /**
  * statement9 为原ReliableEvents固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Update("UPDATE platform_outbox SET status='PENDING',attempts=0,available_at=CURRENT_TIMESTAMP(6),failure_code=NULL,lease_token=NULL,lease_until=NULL WHERE id=UUID_TO_BIN(#{p0}) AND status='MANUAL'")
 int statement9(@Param("p0") Object p0);

 /** @param limit 最多100条，由平台任务读取准入校验 @return 任务摘要，不返回租约或业务正文 */
 @Select("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(tenant_id) tenant,event_type,status,attempts,failure_code FROM platform_outbox ORDER BY created_at DESC,id DESC LIMIT #{limit}") List<Map<String,Object>> summaries(@Param("limit") int limit);
}
