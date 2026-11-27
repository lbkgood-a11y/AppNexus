package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import static com.appnexus.bulkwarehousing.api.inboundexecution.CommodityTemplates.*;

import com.appnexus.persistence.mybatis.GuardedScope;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 模板维护固定 SQL，不把模板权限扩展到业务执行或其他租户。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,tables={"bw_commodity_template"},
        statements={"list","count","lock","find","lockCode","insert","update","retire","publish"})
public interface CommodityTemplateMapper {
    record Row(String id,String code,int version,String status,long rowVersion,String profile,String definition) {}
    record Write(String id,String code,int version,String metering,String quality,String deduction,String grain,String definition) {}
    String SELECT = """
            SELECT BIN_TO_UUID(id) id,template_code code,version,status,row_version rowVersion,
              CASE WHEN stock_granularity='LPN' THEN 'STEEL_LPN' WHEN deduction_strategy='QUALITY_LINEAR'
              THEN 'QUALITY_DEDUCTION' ELSE 'SAMPLED_BULK' END profile,CAST(definition AS CHAR) definition
            FROM bw_commodity_template WHERE tenant_id=#{__verifiedTenant}
            """;
    String UPDATE = "UPDATE bw_commodity_template SET updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6),row_version=row_version+1,";
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=READ)
    @Select(SELECT+" ORDER BY template_code,version DESC LIMIT #{limit} OFFSET #{offset}")
    List<Row> list(@Param("__verifiedTenant") byte[] tenant,@Param("offset") int offset,@Param("limit") int limit);
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=READ)
    @Select("SELECT COUNT(*) FROM bw_commodity_template WHERE tenant_id=#{__verifiedTenant}")
    long count(@Param("__verifiedTenant") byte[] tenant);
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=WRITE)
    @Select(SELECT+" AND id=UUID_TO_BIN(#{id}) FOR UPDATE")
    Row lock(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=PUBLISH)
    @Select(SELECT+" AND id=UUID_TO_BIN(#{id})")
    Row find(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=PUBLISH)
    @Select(SELECT+" AND template_code=#{code} ORDER BY version FOR UPDATE")
    List<Row> lockCode(@Param("__verifiedTenant") byte[] tenant,@Param("code") String code);
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=WRITE)
    @Insert("""
            INSERT INTO bw_commodity_template(tenant_id,id,template_code,version,status,metering_strategy,
             quality_gate_strategy,deduction_strategy,stock_granularity,definition,created_by,updated_by)
            VALUES(#{__verifiedTenant},UUID_TO_BIN(#{row.id}),#{row.code},#{row.version},'DRAFT',#{row.metering},
             #{row.quality},#{row.deduction},#{row.grain},#{row.definition},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
            """)
    void insert(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("row") Write row);
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=WRITE)
    @Update(UPDATE+"""
            metering_strategy=#{row.metering},quality_gate_strategy=#{row.quality},deduction_strategy=#{row.deduction},
              stock_granularity=#{row.grain},definition=#{row.definition}
            WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{row.id}) AND status='DRAFT' AND row_version=#{version}
            """)
    int update(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("row") Write row,@Param("version") long version);
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=PUBLISH)
    @Update(UPDATE+"""
            status='DISABLED' WHERE tenant_id=#{__verifiedTenant} AND template_code=#{code}
               AND id<>UUID_TO_BIN(#{id}) AND status='PUBLISHED'
            """)
    void retire(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("code") String code,@Param("id") String id);
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=PUBLISH)
    @Update(UPDATE+"""
            status='PUBLISHED' WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
              AND status='DRAFT' AND row_version=#{version}
            """)
    int publish(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("id") String id,@Param("version") long version);
}
