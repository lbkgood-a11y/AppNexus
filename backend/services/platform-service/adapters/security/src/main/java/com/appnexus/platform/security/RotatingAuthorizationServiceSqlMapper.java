package com.appnexus.platform.security;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** RotatingAuthorizationService固定SQL映射；没有接收SQL、表名或列名的通用执行入口。 */
@Mapper
@PlatformScoped(tables={"identity_account","identity_refresh_history","oauth2_authorization"})
public interface RotatingAuthorizationServiceSqlMapper {
 /**
  * statement1 为原RotatingAuthorizationService固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p3 原调用第4个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Insert("INSERT IGNORE INTO identity_refresh_history(token_hash,authorization_id,registered_client_id,expires_at) VALUES(#{p0},#{p1},#{p2},#{p3})")
 int statement1(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /**
  * statement2 为原RotatingAuthorizationService固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT id FROM identity_account WHERE id=UUID_TO_BIN(#{p0}) AND enabled=TRUE FOR UPDATE")
 List<Map<String,Object>> statement2(@Param("p0") Object p0);

 /**
  * statement3 为原RotatingAuthorizationService固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT id FROM oauth2_authorization WHERE id=#{p0} FOR UPDATE")
 List<Map<String,Object>> statement3(@Param("p0") Object p0);

 /**
  * statement4 为原RotatingAuthorizationService固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT authorization_id,registered_client_id,expires_at FROM identity_refresh_history WHERE token_hash=#{p0} FOR UPDATE")
 List<Map<String,Object>> statement4(@Param("p0") Object p0);

 /**
  * statement5 为原RotatingAuthorizationService固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT principal_name FROM oauth2_authorization WHERE id=#{p0}")
 List<Map<String,Object>> statement5(@Param("p0") Object p0);

 /**
  * statement6 为原RotatingAuthorizationService固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT id FROM identity_account WHERE id=UUID_TO_BIN(#{p0}) AND enabled=TRUE FOR UPDATE")
 List<Map<String,Object>> statement6(@Param("p0") Object p0);

 /**
  * statement7 为原RotatingAuthorizationService固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT id FROM oauth2_authorization WHERE id=#{p0} FOR UPDATE")
 List<Map<String,Object>> statement7(@Param("p0") Object p0);
}
