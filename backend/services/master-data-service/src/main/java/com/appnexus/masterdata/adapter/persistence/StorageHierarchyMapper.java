package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
/** 仓库三层固定SQL；层级由构建枚举选择，不接收自由表名。 */
@Mapper
@PlatformScoped(tables={"md_warehouse","md_storage_area","md_storage_location"})
public interface StorageHierarchyMapper {
 /** @param tenant 可信租户 @param kind STORAGE_AREA/STORAGE_LOCATION @param code 直属父编码 @return 父启用状态，写事务行锁直到提交 */
 @Select({"<script>","SELECT enabled FROM <choose><when test=\"kind == 'STORAGE_AREA'\">md_warehouse</when><otherwise>md_storage_area</otherwise></choose> WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND code=#{code} FOR UPDATE","</script>"}) List<Boolean> parent(@Param("tenant") String tenant,@Param("kind") String kind,@Param("code") String code);
 /**
  * @param tenant 可信租户 UUID
  * @param kind 固定库区或库位类型
  * @param code 请求指定的直属父编码
  * @return 当前租户下的根仓库 UUID；无有效父链时为空
  */
 @Select({"<script>","SELECT BIN_TO_UUID(w.id) FROM md_warehouse w <if test=\"kind == 'STORAGE_LOCATION'\">JOIN md_storage_area a ON a.tenant_id=w.tenant_id AND a.parent_code=w.code</if> WHERE w.tenant_id=UUID_TO_BIN(#{tenant}) AND <choose><when test=\"kind == 'STORAGE_AREA'\">w.code</when><otherwise>a.code</otherwise></choose>=#{code}","</script>"})
 List<String> rootForParent(@Param("tenant") String tenant,@Param("kind") String kind,@Param("code") String code);
 /** 当前行父码不能修改，锁顺序保持父再子。 */
 @Select({"<script>","SELECT parent_code FROM <choose><when test=\"kind == 'STORAGE_AREA'\">md_storage_area</when><otherwise>md_storage_location</otherwise></choose> WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})<if test='lock'> FOR UPDATE</if>","</script>"}) List<String> parentCode(@Param("tenant") String tenant,@Param("kind") String kind,@Param("id") String id,@Param("lock") boolean lock);
 /** 固定仓库或库区节点锁，只用于已准入停用用例。 */
 @Select({"<script>","SELECT code FROM <choose><when test=\"kind == 'WAREHOUSE'\">md_warehouse</when><otherwise>md_storage_area</otherwise></choose> WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id}) FOR UPDATE","</script>"}) List<String> code(@Param("tenant") String tenant,@Param("kind") String kind,@Param("id") String id);
 /** 已准入停用时检查启用直属下级，不允许留下启用孤立节点。 */
 @Select({"<script>","SELECT id FROM <choose><when test=\"kind == 'WAREHOUSE'\">md_storage_area</when><otherwise>md_storage_location</otherwise></choose> WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND parent_code=#{code} AND enabled=TRUE LIMIT 1 FOR UPDATE","</script>"}) List<byte[]> children(@Param("tenant") String tenant,@Param("kind") String kind,@Param("code") String code);
}
