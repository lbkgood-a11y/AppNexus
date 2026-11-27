package com.appnexus.platform.persistence;
import com.appnexus.persistence.mybatis.PlatformScoped;
import com.appnexus.contracts.operation.RecordAuditSource;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 已授权响应的固定最小审计投影；代码注解与表白名单共同限定，不提供自由查询接口。 */
@Mapper
@PlatformScoped(tables={"access_role","business_object_type","bw_commodity_template","bw_metering_order","bw_receipt_order","identity_account","integration_client","leave_poc_request","md_product_approval","md_product_attribute","md_product_attribute_group","md_product_image","md_product_template","md_product_unit_conversion","md_product_v1","md_warehouse","platform_menu","platform_outbox","platform_tenant","print_device","print_document","print_job","print_scene","print_template","tenant_dictionary","tenant_dictionary_item","tenant_invitation","tenant_member","tenant_organization_position","tenant_organization_relation","tenant_organization_type","tenant_organization_unit","tenant_parameter","tenant_person_position","wf_definition","wf_instance","wf_task"})
public interface AuditProjectionMapper {
 /** @param declaration 真实处理方法的构建注解，不来自请求反序列化 @param tenant 安全链或已授权平台代管租户 @param ids 原接口已返回记录键，每批最多200个 @return 仅四列审计及记录键，不读取正文 */
 @SelectProvider(type=Sql.class,method="read") List<Map<String,Object>> read(@Param("declaration") RecordAuditSource declaration,@Param("tenant") String tenant,@Param("ids") List<String> ids);
 /** 只支持已审查来源和简单标识符，参数全部绑定；调用前原业务必须已完成授权。 */
 final class Sql {
  private static final java.util.Set<String> TABLES=java.util.Set.of("access_role","business_object_type","bw_commodity_template","bw_metering_order","bw_receipt_order","identity_account","integration_client","leave_poc_request","md_product_approval","md_product_attribute","md_product_attribute_group","md_product_image","md_product_template","md_product_unit_conversion","md_product_v1","md_warehouse","platform_menu","platform_outbox","platform_tenant","print_device","print_document","print_job","print_scene","print_template","tenant_dictionary","tenant_dictionary_item","tenant_invitation","tenant_member","tenant_organization_position","tenant_organization_relation","tenant_organization_type","tenant_organization_unit","tenant_parameter","tenant_person_position","wf_definition","wf_instance","wf_task");
  private Sql(){}
  /** @param parameters 构建注解及安全投影键 @return 最小参数化审计SQL，未知来源拒绝 */
  public static String read(Map<String,Object> parameters){var declaration=(RecordAuditSource)parameters.get("declaration");var ids=(List<?>)parameters.get("ids");if(!TABLES.contains(declaration.table())||!declaration.key().matches("[a-z][a-z0-9_]*")||ids.isEmpty()||ids.size()>200)throw new SecurityException("AUDIT_SOURCE_INVALID");String key=declaration.binaryKey()?"BIN_TO_UUID("+declaration.key()+")":declaration.key();var values=new java.util.ArrayList<String>();for(int i=0;i<ids.size();i++)values.add(declaration.binaryKey()?"UUID_TO_BIN(#{ids["+i+"]})":"#{ids["+i+"]}");return "SELECT "+key+" audit_id,BIN_TO_UUID(created_by) creator,created_at,BIN_TO_UUID(updated_by) updater,updated_at FROM "+declaration.table()+" WHERE "+(parameters.get("tenant")==null?"":"tenant_id="+(declaration.binaryTenant()?"UUID_TO_BIN(#{tenant})":"#{tenant}")+" AND ")+declaration.key()+" IN ("+String.join(",",values)+")";}
 }
}
