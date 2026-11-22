package com.appnexus.platform.persistence.organization.personnel;
import com.appnexus.persistence.mybatis.PlatformScoped;
import com.appnexus.persistence.mybatis.GuardedScope;
import org.apache.ibatis.annotations.*;
import com.appnexus.platform.authorization.RowAccessControl;
import java.util.List;
import java.util.Map;
/** PersonnelService固定SQL映射；不接收SQL或标识符，原用例逐次验证可信租户及操作。 */
@Mapper
@PlatformScoped(tables={"md_person","md_person_account","md_person_detail"})
public interface PersonnelMapper {
 /** 固定业务语句1；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @GuardedScope(resource="masterdata.person",tenant="p0",recordId="p1",actions="detail_update")
 @Select("SELECT content FROM md_person_detail WHERE tenant_id=UUID_TO_BIN(#{p0}) AND person_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2}) AND section_code=#{p3}")
 List<String> readDetailContent(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定业务语句2；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @GuardedScope(resource="masterdata.person",tenant="p0",recordId="p2",actions="detail_create")
 @Insert("INSERT INTO md_person_detail(tenant_id,id,person_id,section_code,content) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),#{p3},#{p4})")
 int createDetail(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4);

 /** 固定业务语句3；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @GuardedScope(resource="masterdata.person",tenant="p1",recordId="p2",actions="detail_update")
 @Update("UPDATE md_person_detail SET content=#{p0} WHERE tenant_id=UUID_TO_BIN(#{p1}) AND person_id=UUID_TO_BIN(#{p2}) AND id=UUID_TO_BIN(#{p3}) AND section_code=#{p4}")
 int updateDetail(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4);

 /** 固定业务语句4；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @GuardedScope(resource="masterdata.person",tenant="p0",recordId="p1",actions="detail_delete")
 @Delete("DELETE FROM md_person_detail WHERE tenant_id=UUID_TO_BIN(#{p0}) AND person_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2}) AND section_code=#{p3}")
 int deleteDetail(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定业务语句5；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @GuardedScope(resource="masterdata.person",tenant="p0",recordId="p2",actions="assign")
 @Insert("INSERT INTO md_person_account(tenant_id,id,person_id,member_id,created_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),UUID_TO_BIN(#{p4}))")
 int createAssignment(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4);

 /** 固定业务语句6；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @GuardedScope(resource="masterdata.person",tenant="p1",recordId="p2",actions="assign")
 @Update("UPDATE md_person_account SET member_id=UUID_TO_BIN(#{p0}) WHERE tenant_id=UUID_TO_BIN(#{p1}) AND person_id=UUID_TO_BIN(#{p2}) AND id=UUID_TO_BIN(#{p3})")
 int updateAssignment(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定业务语句7；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @GuardedScope(resource="masterdata.person",tenant="p0",recordId="p1",actions="assign")
 @Delete("DELETE FROM md_person_account WHERE tenant_id=UUID_TO_BIN(#{p0}) AND person_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2})")
 int deleteAssignment(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句8；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COUNT(*) FROM md_person_account pu JOIN md_person p ON p.tenant_id=pu.tenant_id AND p.id=pu.person_id WHERE pu.tenant_id=UUID_TO_BIN(#{p0}) AND pu.person_id=UUID_TO_BIN(#{p1}) AND pu.member_id=UUID_TO_BIN(#{p2}) AND p.enabled=TRUE AND p.employment_status IN ('ACTIVE','PROBATION')")
 Integer countActiveIdentity(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句9；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @GuardedScope(resource="masterdata.person",tenant="p1",recordId="p2",actions={"detail_create","detail_update","detail_delete","assign"})
 @Update("UPDATE md_person SET version=version+1,updated_by=UUID_TO_BIN(#{p0}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2}) AND version=#{p3}")
 int advanceVersion(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** @param tenant 可信租户UUID @param id 已授权人员UUID @return 固定字段与同人员明细；不扩展独立读取资格 */
 @GuardedScope(predicate="access",resource="masterdata.person",tenant="tenant",actions="read")
 @SelectProvider(type=PersonRecordMapper.ReadSql.class,method="profile") List<Map<String,Object>> profile(@Param("tenant") String tenant,@Param("id") String id,@Param("access") RowAccessControl.Predicate access);
 /** @param tenant 可信租户UUID @param id 已授权人员UUID @return 固定字段与同人员明细；不扩展独立读取资格 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.person",tenant="tenant",recordId="id",actions="read")
 @Select("SELECT BIN_TO_UUID(created_by) created_by,created_at,BIN_TO_UUID(updated_by) updated_by,updated_at FROM md_person WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})") List<Map<String,Object>> audit(@Param("tenant") String tenant,@Param("id") String id);
 /** @param tenant 可信租户UUID @param id 已授权人员UUID @return 固定字段与同人员明细；不扩展独立读取资格 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.person",tenant="tenant",recordId="id",actions="read")
 @Select("SELECT BIN_TO_UUID(id) id,section_code,CAST(content AS CHAR) content FROM md_person_detail WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND person_id=UUID_TO_BIN(#{id}) AND section_code NOT IN ('organizations','positions') ORDER BY section_code,id") List<Map<String,Object>> details(@Param("tenant") String tenant,@Param("id") String id);
 /** @param tenant 可信租户UUID @param id 已授权人员UUID @return 固定字段与同人员明细；不扩展独立读取资格 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.person",tenant="tenant",recordId="id",actions="read")
 @Select("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(member_id) member_text FROM md_person_account WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND person_id=UUID_TO_BIN(#{id}) ORDER BY created_at,id") List<Map<String,Object>> assignments(@Param("tenant") String tenant,@Param("id") String id);
}
