package com.appnexus.platform.security;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** PolicyTemplates固定SQL映射；没有接收SQL、表名或列名的通用执行入口。 */
@Mapper
@PlatformScoped(tables={"access_policy_template"})
public interface PolicyTemplatesSqlMapper {
 /**
  * statement1 为原PolicyTemplates固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p3 原调用第4个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p4 原调用第5个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Insert("INSERT INTO access_policy_template(id,owner_id,name,policy_json,created_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),#{p2},#{p3},UUID_TO_BIN(#{p4}))")
 int statement1(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4);

 /**
  * statement2 为原PolicyTemplates固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT policy_json FROM access_policy_template WHERE id=UUID_TO_BIN(#{p0}) AND owner_id=UUID_TO_BIN(#{p1})")
 List<String> statement2(@Param("p0") Object p0,@Param("p1") Object p1);

 /** @param owner 平台或租户授权上下文确定的模板所有者UUID @return 最多100条模板名称，不返回可执行脚本 */
 @Select("SELECT BIN_TO_UUID(id) id,name FROM access_policy_template WHERE owner_id=UUID_TO_BIN(#{owner}) ORDER BY created_at DESC,id LIMIT 100") List<Map<String,Object>> summaries(@Param("owner") String owner);
}
