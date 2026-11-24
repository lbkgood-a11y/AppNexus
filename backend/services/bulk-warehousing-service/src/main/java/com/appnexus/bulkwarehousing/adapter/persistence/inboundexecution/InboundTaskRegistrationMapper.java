package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import static com.appnexus.bulkwarehousing.api.inboundexecution.CreateInboundTaskUseCase.PERMISSION;

import com.appnexus.persistence.mybatis.GuardedScope;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 只通过统一登记许可访问可信租户内的通知、模板和车次。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true, tables={"bw_inbound_task","bw_inbound_notice","bw_inbound_notice_line","bw_commodity_template"},
        statements={"notice","lockNotice","previousTemplates","templates","template","matchingTemplates","tasks","existing","bindTemplate","insert"})
public interface InboundTaskRegistrationMapper {
    record NoticeRow(String status, String templateId) {}
    record TemplateRow(String id, String code, int version, String qualityStrategy) {}
    record TaskRow(String id, String number, String status, String stage) {}
    record ExistingRow(String id, String number, String status, String stage, String noticeId,
                       String templateId, String identity, String identifier) {}

    String NOTICE = """
            SELECT status,BIN_TO_UUID(execution_template_id) templateId
            FROM bw_inbound_notice WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
            """;
    String TEMPLATE = """
            SELECT BIN_TO_UUID(id) id,template_code code,version,quality_gate_strategy qualityStrategy
            FROM bw_commodity_template WHERE tenant_id=#{__verifiedTenant} AND status='PUBLISHED'
            AND quality_gate_strategy IN ('SAMPLED','QUALIFIED_AND_UNLOADED','DOCUMENT_AND_APPEARANCE')
            """;
    String TASK = """
            SELECT BIN_TO_UUID(id) id,task_no number,status,queue_stage stage
            """;

    @GuardedScope(tenant="__verifiedTenant",operationPermissions=PERMISSION)
    @Select(NOTICE)
    NoticeRow notice(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

    @GuardedScope(tenant="__verifiedTenant",operationPermissions=PERMISSION)
    @Select(NOTICE + " FOR UPDATE")
    NoticeRow lockNotice(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

    @GuardedScope(tenant="__verifiedTenant",operationPermissions=PERMISSION)
    @Select("""
            SELECT DISTINCT BIN_TO_UUID(template_id)
            FROM bw_inbound_task WHERE tenant_id=#{__verifiedTenant} AND notice_id=UUID_TO_BIN(#{id}) LIMIT 2
            """)
    List<String> previousTemplates(@Param("__verifiedTenant") byte[] tenant,@Param("id") String notice);

    @GuardedScope(tenant="__verifiedTenant",operationPermissions=PERMISSION)
    @Select(TEMPLATE + " AND JSON_EXTRACT(definition,'$.noticeProductIds') IS NULL ORDER BY template_code,version DESC")
    List<TemplateRow> templates(@Param("__verifiedTenant") byte[] tenant);

    @GuardedScope(tenant="__verifiedTenant",operationPermissions=PERMISSION)
    @Select(TEMPLATE + " AND id=UUID_TO_BIN(#{id}) FOR SHARE")
    TemplateRow template(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

    /** 只接受覆盖来源通知全部商品的已发布模板，模板的商品关联由定义快照提供。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=PERMISSION)
    @Select("""
            SELECT BIN_TO_UUID(c.id) id,c.template_code code,c.version,c.quality_gate_strategy qualityStrategy
            FROM bw_commodity_template c JOIN bw_inbound_notice_line l ON l.tenant_id=c.tenant_id
            WHERE c.tenant_id=#{__verifiedTenant} AND l.notice_id=UUID_TO_BIN(#{notice}) AND c.status='PUBLISHED'
              AND c.quality_gate_strategy IN ('SAMPLED','QUALIFIED_AND_UNLOADED','DOCUMENT_AND_APPEARANCE')
            GROUP BY c.id,c.template_code,c.version,c.quality_gate_strategy
            HAVING COUNT(*)=SUM(IF(JSON_CONTAINS(c.definition,JSON_QUOTE(LOWER(BIN_TO_UUID(l.product_id))),
              '$.noticeProductIds')=1,1,0))
            ORDER BY c.template_code,c.version DESC
            """)
    List<TemplateRow> matchingTemplates(@Param("__verifiedTenant") byte[] tenant,@Param("notice") String notice);

    @GuardedScope(tenant="__verifiedTenant",operationPermissions=PERMISSION)
    @Select(TASK + """
            FROM bw_inbound_task WHERE tenant_id=#{__verifiedTenant} AND notice_id=UUID_TO_BIN(#{id})
            ORDER BY created_at DESC,id DESC LIMIT 20
            """)
    List<TaskRow> tasks(@Param("__verifiedTenant") byte[] tenant,@Param("id") String notice);

    @GuardedScope(tenant="__verifiedTenant",operationPermissions=PERMISSION)
    @Select(TASK + """
            ,BIN_TO_UUID(notice_id) noticeId,BIN_TO_UUID(template_id) templateId,vehicle_identity_type identity,
             COALESCE(vehicle_plate,temporary_vehicle_no) identifier
            FROM bw_inbound_task WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
            """)
    ExistingRow existing(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

    @GuardedScope(tenant="__verifiedTenant",operationPermissions=PERMISSION)
    @Update("""
            UPDATE bw_inbound_notice SET execution_template_id=UUID_TO_BIN(#{template}), version=version+1,
              updated_by=UUID_TO_BIN(#{actor}), updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{notice}) AND execution_template_id IS NULL
            """)
    int bindTemplate(@Param("__verifiedTenant") byte[] tenant,@Param("notice") String notice,
                     @Param("template") String template,@Param("actor") String actor);

    @GuardedScope(tenant="__verifiedTenant",operationPermissions=PERMISSION)
    @Insert("""
            INSERT INTO bw_inbound_task(tenant_id,id,task_no,notice_id,template_id,vehicle_identity_type,
              vehicle_plate,temporary_vehicle_no,status,queue_stage,stage_started_at,arrived_at,created_by,updated_by)
            VALUES(#{__verifiedTenant},UUID_TO_BIN(#{id}),#{number},UUID_TO_BIN(#{notice}),UUID_TO_BIN(#{template}),
              #{identity},IF(#{identity}='PLATED',#{identifier,jdbcType=VARCHAR},NULL),
              IF(#{identity}='UNPLATED',#{identifier,jdbcType=VARCHAR},NULL),#{status},#{stage},CURRENT_TIMESTAMP(6),
              CURRENT_TIMESTAMP(6),UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
            """)
    int insert(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("number") String number,
               @Param("notice") String notice,@Param("template") String template,@Param("identity") String identity,
               @Param("identifier") String identifier,@Param("status") String status,@Param("stage") String stage,
               @Param("actor") String actor);
}
