package com.appnexus.masterdata.adapter.persistence;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
/**
 * 商品父记录固定Mapper；租户由可信请求覆盖，锁和CAS参与原商品聚合事务。
 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,tables={"md_product_v1"},statements={"load","advance"})
public interface ProductRecordMapper {
  /**
 * @param tenant 可信租户二进制UUID
 * @param id 商品UUID
 * @param lock 写用例是否加FOR UPDATE锁，不向客户端开放
 * @return 当前租户父记录；未匹配为空
 */
  @Select({"<script>","SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(organization_id) organizationId,code,name,short_name shortName,description,brand,origin_text origin,BIN_TO_UUID(category_id) categoryId,BIN_TO_UUID(category_config_id) configId,product_form productForm,specification_mode specificationMode,BIN_TO_UUID(base_unit_id) unitId,status,structure_state structureState,draft_archived_at archivedAt,version,structure_revision structureRevision,legacy_enabled_snapshot legacyEnabled,legacy_specification_original legacySpecificationOriginal,legacy_specification_disposition legacyDisposition,barcode,legacy_unit_original legacyUnitOriginal,legacy_source_version legacySourceVersion,BIN_TO_UUID(created_by) createdBy,created_at createdAt,BIN_TO_UUID(updated_by) updatedBy,updated_at updatedAt FROM md_product_v1 WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})","<if test='lock'> FOR UPDATE</if>","</script>"})
  List<Map<String,Object>> load(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("lock") boolean lock);
  /**
 * @param tenant 可信租户二进制UUID
 * @param id 已锁定的商品UUID
 * @param actor 可信实际账号UUID
 * @param version 当前非负版本，CAS必须匹配
 * @return 成功更新行数，非1整笔回滚
 */
  @Update("UPDATE md_product_v1 SET version=version+1,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND version=#{version}")
  int advance(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("actor") String actor,@Param("version") long version);
}
