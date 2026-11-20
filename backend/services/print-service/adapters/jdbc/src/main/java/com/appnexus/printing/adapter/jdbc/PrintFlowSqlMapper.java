package com.appnexus.printing.adapter.jdbc;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** PrintFlow固定SQL映射；不接收SQL或标识符，原用例逐次验证可信租户及操作。 */
@Mapper
@PlatformScoped(tables={"print_agent_queue","print_data_contract","print_document","print_scene","print_template","print_template_version"})
public interface PrintFlowSqlMapper {
 /** 固定业务语句1；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(id) id,code,name,data_contract,BIN_TO_UUID(template_id) template_id,copies,enabled,revision FROM print_scene WHERE tenant_id=UUID_TO_BIN(#{p0}) ORDER BY name")
 List<Map<String,Object>> statement1(@Param("p0") Object p0);

 /** 固定业务语句2；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT code FROM print_scene WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) FOR UPDATE")
 List<Map<String,Object>> statement2(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句3；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT v.data_contract FROM print_template t JOIN print_template_version v ON v.tenant_id=t.tenant_id AND v.template_id=t.id AND v.version=t.published_version WHERE t.tenant_id=UUID_TO_BIN(#{p0}) AND t.id=UUID_TO_BIN(#{p1}) AND t.enabled=TRUE")
 List<Map<String,Object>> statement3(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句4；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO print_scene(tenant_id,id,code,name,data_contract,template_id,copies,enabled,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),#{p2},#{p3},#{p4},UUID_TO_BIN(#{p5}),#{p6},#{p7},UUID_TO_BIN(#{p8}))")
 int statement4(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8);

 /** 固定业务语句5；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE print_scene SET code=#{p0},name=#{p1},data_contract=#{p2},template_id=UUID_TO_BIN(#{p3}),copies=#{p4},enabled=#{p5},revision=revision+1,updated_by=UUID_TO_BIN(#{p6}) WHERE tenant_id=UUID_TO_BIN(#{p7}) AND id=UUID_TO_BIN(#{p8}) AND revision=#{p9}")
 int statement5(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9);

 /** 固定业务语句6；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(d.id) id,BIN_TO_UUID(d.scene_id) scene_id,d.number,d.data_json,d.revision,d.created_at,s.name scene_name FROM print_document d JOIN print_scene s ON s.tenant_id=d.tenant_id AND s.id=d.scene_id WHERE d.tenant_id=UUID_TO_BIN(#{p0}) AND d.created_by=UUID_TO_BIN(#{p1}) ORDER BY d.created_at DESC LIMIT 200")
 List<Map<String,Object>> statement6(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句7；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO print_document(tenant_id,id,scene_id,number,data_json,created_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),#{p3},CAST(#{p4} AS JSON),UUID_TO_BIN(#{p5}))")
 int statement7(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5);

 /** 固定业务语句8；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(scene_id) scene_id,data_json FROM print_document WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) AND created_by=UUID_TO_BIN(#{p2})")
 List<Map<String,Object>> statement8(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句9；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT v.data_contract FROM print_template t JOIN print_template_version v ON v.tenant_id=t.tenant_id AND v.template_id=t.id AND v.version=t.published_version WHERE t.tenant_id=UUID_TO_BIN(#{p0}) AND t.id=UUID_TO_BIN(#{p1}) AND t.enabled=TRUE")
 List<Map<String,Object>> statement9(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句10；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT v.data_contract FROM print_template t JOIN print_template_version v ON v.tenant_id=t.tenant_id AND v.template_id=t.id AND v.version=t.published_version WHERE t.tenant_id=UUID_TO_BIN(#{p0}) AND t.id=UUID_TO_BIN(#{p1}) AND t.enabled=TRUE")
 List<Map<String,Object>> statement10(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句11；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT definition_json FROM print_data_contract WHERE tenant_id=UUID_TO_BIN(#{p0}) ORDER BY code")
 List<String> statement11(@Param("p0") Object p0);

 /** 固定业务语句12；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO print_data_contract(tenant_id,code,definition_json,created_by) VALUES(UUID_TO_BIN(#{p0}),#{p1},CAST(#{p2} AS JSON),UUID_TO_BIN(#{p3}))")
 int statement12(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定业务语句13；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Delete("DELETE FROM print_agent_queue WHERE tenant_id=UUID_TO_BIN(#{p0}) AND site_code=#{p1} AND agent_id=#{p2}")
 int statement13(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句14；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO print_agent_queue(tenant_id,site_code,agent_id,queue_name) VALUES(UUID_TO_BIN(#{p0}),#{p1},#{p2},#{p3})")
 int statement14(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定业务语句15；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT site_code,agent_id,queue_name,seen_at FROM print_agent_queue WHERE tenant_id=UUID_TO_BIN(#{p0}) AND seen_at>DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 120 SECOND) ORDER BY site_code,queue_name")
 List<Map<String,Object>> statement15(@Param("p0") Object p0);

 /** 固定业务语句16；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(id) id,data_contract,BIN_TO_UUID(template_id) template_id FROM print_scene WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) AND enabled=TRUE FOR SHARE")
 List<Map<String,Object>> statement16(@Param("p0") Object p0,@Param("p1") Object p1);
}
