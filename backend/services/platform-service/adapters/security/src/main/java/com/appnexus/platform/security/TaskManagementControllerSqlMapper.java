package com.appnexus.platform.security;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** TaskManagementController固定SQL映射；没有接收SQL、表名或列名的通用执行入口。 */
@Mapper
@PlatformScoped(tables={"platform_administration_audit"})
public interface TaskManagementControllerSqlMapper {
 /**
  * statement1 为原TaskManagementController固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Insert("INSERT INTO platform_administration_audit(actor_account_id,action,target_id,tenant_id) VALUES(UUID_TO_BIN(#{p0}),'OUTBOX_RETRIED',UUID_TO_BIN(#{p1}),NULL)")
 int statement1(@Param("p0") Object p0,@Param("p1") Object p1);
}
