package com.appnexus.printing.adapter.jdbc;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 打印公开场景及历史固定SQL，后台/机器身份保持原平台契约，不额外授予业务数据权限。 */
@Mapper
@PlatformScoped(tables={"print_scene","print_template","print_template_version","print_agent_queue","print_output_request","print_job"})
public interface BusinessPrintingMapper {
 /** 固定打印查询；可信租户、机器或成员准入由BusinessPrinting适配器逐次校验。 */
 @Select("SELECT BIN_TO_UUID(s.id) id,s.code,s.name,s.data_contract FROM print_scene s JOIN print_template t ON t.tenant_id=s.tenant_id AND t.id=s.template_id JOIN print_template_version v ON v.tenant_id=t.tenant_id AND v.template_id=t.id AND v.version=t.published_version WHERE s.tenant_id=UUID_TO_BIN(#{tenant}) AND s.enabled=TRUE AND t.enabled=TRUE AND s.data_contract=#{contract} AND v.data_contract=s.data_contract")
 List<Map<String,Object>> query0(@Param("tenant") String tenant,@Param("contract") String contract);

 /** 固定打印查询；可信租户、机器或成员准入由BusinessPrinting适配器逐次校验。 */
 @Select("SELECT EXISTS(SELECT 1 FROM print_agent_queue WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND seen_at>DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 120 SECOND))")
 Boolean query1(@Param("tenant") String tenant);

 /** 固定打印查询；可信租户、机器或成员准入由BusinessPrinting适配器逐次校验。 */
 @Select("SELECT BIN_TO_UUID(r.id) id,r.created_at, j.state, BIN_TO_UUID(r.confirmed_job_id) confirmedJobId, delivery.state deliveryState, delivery.updated_at deliveryUpdatedAt FROM print_output_request r JOIN print_job j ON j.tenant_id=r.tenant_id AND j.id=r.job_id LEFT JOIN print_job delivery ON delivery.tenant_id=r.tenant_id AND delivery.id=r.confirmed_job_id WHERE r.tenant_id=UUID_TO_BIN(#{tenant}) AND j.source_id=#{source} AND r.owner=#{actor} ORDER BY r.created_at LIMIT 100")
 List<Map<String,Object>> query2(@Param("tenant") String tenant,@Param("source") String source,@Param("actor") String actor);
}
