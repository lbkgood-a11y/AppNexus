package com.appnexus.platform.persistence.organization.personnel;
import com.appnexus.platform.organization.PersonData;
import com.appnexus.platform.organization.PersonData.*;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 人员主档固定映射；字段构建内逐项列出，准入及字典引用继续由用例执行。 */
@Mapper
@PlatformScoped(tables={"md_person"})
public interface PersonRecordMapper {
 /** 已准入人员摘要；证件与家庭明细不进入列表。 */
 @Select({"<script>","SELECT BIN_TO_UUID(id) id,code,name,enabled,version,BIN_TO_UUID(created_by) audit_created_by,created_at,BIN_TO_UUID(updated_by) audit_updated_by,updated_at,english_name englishName,phone,email,employment_status employmentStatus FROM md_person WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND (LOCATE(#{keyword},code)&gt;0 OR LOCATE(#{keyword},name)&gt;0) <if test='enabled != null'> AND enabled=#{enabled}</if> ORDER BY code,id LIMIT #{size} OFFSET #{offset}","</script>"})
 List<Map<String,Object>> list(@Param("tenant") String tenant,@Param("keyword") String keyword,@Param("enabled") Boolean enabled,@Param("size") int size,@Param("offset") int offset);
 /** 列表COUNT与数据使用相同租户及过滤条件。 */
 @Select({"<script>","SELECT COUNT(*) FROM md_person WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND (LOCATE(#{keyword},code)&gt;0 OR LOCATE(#{keyword},name)&gt;0) <if test='enabled != null'> AND enabled=#{enabled}</if>","</script>"}) long count(@Param("tenant") String tenant,@Param("keyword") String keyword,@Param("enabled") Boolean enabled);
 /** 固定主档新增，账号来自可信上下文，UTC时间由数据库产生。 */
 @Insert("INSERT INTO md_person(id,tenant_id,code,name,english_name,phone,email,gender,birth_date,ethnicity,native_place,political_status,identity_type,identity_number,work_nature,blood_type,emergency_contact,emergency_phone,marital_status,hobbies,skill_level,education_level,employment_status,remarks,created_by,created_at,updated_by,updated_at) VALUES(UUID_TO_BIN(#{id}),UUID_TO_BIN(#{tenant}),#{input.code},#{input.name},#{values.englishName},#{values.phone},#{values.email},#{values.gender},#{values.birthDate},#{values.ethnicity},#{values.nativePlace},#{values.politicalStatus},#{values.identityType},#{values.identityNumber},#{values.workNature},#{values.bloodType},#{values.emergencyContact},#{values.emergencyPhone},#{values.maritalStatus},#{values.hobbies},#{values.skillLevel},#{values.educationLevel},#{values.employmentStatus},#{values.remarks},UUID_TO_BIN(#{actor}),CURRENT_TIMESTAMP(6),UUID_TO_BIN(#{actor}),CURRENT_TIMESTAMP(6))") int create(@Param("tenant") String tenant,@Param("id") String id,@Param("input") Input input,@Param("values") Map<String,String> values,@Param("actor") String actor);
 /** 固定主档CAS修改，编码不可变；失败整笔回滚。 */
 @Update("UPDATE md_person SET name=#{input.name},english_name=#{values.englishName},phone=#{values.phone},email=#{values.email},gender=#{values.gender},birth_date=#{values.birthDate},ethnicity=#{values.ethnicity},native_place=#{values.nativePlace},political_status=#{values.politicalStatus},identity_type=#{values.identityType},identity_number=#{values.identityNumber},work_nature=#{values.workNature},blood_type=#{values.bloodType},emergency_contact=#{values.emergencyContact},emergency_phone=#{values.emergencyPhone},marital_status=#{values.maritalStatus},hobbies=#{values.hobbies},skill_level=#{values.skillLevel},education_level=#{values.educationLevel},employment_status=#{values.employmentStatus},remarks=#{values.remarks},version=version+1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id}) AND version=#{input.version} AND code=#{input.code}") int update(@Param("tenant") String tenant,@Param("id") String id,@Param("input") Input input,@Param("values") Map<String,String> values);
 /** 独立启停操作及原版本校验。 */
 @Update("UPDATE md_person SET enabled=#{enabled},version=version+1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id}) AND version=#{version}") int status(@Param("tenant") String tenant,@Param("id") String id,@Param("enabled") boolean enabled,@Param("version") long version);
 /** 原主档值仅用于受控修改合并及字典引用校验。 */
 @Select("SELECT english_name englishName,phone phone,email email,gender gender,birth_date birthDate,ethnicity ethnicity,native_place nativePlace,political_status politicalStatus,identity_type identityType,identity_number identityNumber,work_nature workNature,blood_type bloodType,emergency_contact emergencyContact,emergency_phone emergencyPhone,marital_status maritalStatus,hobbies hobbies,skill_level skillLevel,education_level educationLevel,employment_status employmentStatus,remarks remarks FROM md_person WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})") List<Map<String,String>> previous(@Param("tenant") String tenant,@Param("id") String id);
 /** 修改审计与业务同事务，不覆盖创建信息。 */
 @Update("UPDATE md_person SET updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})") int audit(@Param("tenant") String tenant,@Param("id") String id,@Param("actor") String actor);
}
