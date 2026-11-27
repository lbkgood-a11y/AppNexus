package com.appnexus.platform.security;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.platform.authorization.*;
import java.util.*;
import com.appnexus.platform.tenant.*;
import org.springframework.stereotype.Component;

/** 业务对象授权桥接；目录和租户配置使用各自专属权限，不授予业务原文访问权。 */
@Component
public final class BusinessObjectPlatformBridge implements BusinessObjectPlatform,DataAccessDeclaration.Executor {
  private final AccessPlanning planning;
  private final RowAccessControl rows;
  private final FieldProtection fields;
  private final org.springframework.beans.factory.ObjectProvider<BusinessObjects.Provider> objects;
  private final PlatformAdministration platform;
  private final TenantAccess tenants;
  private final AccessAuthorization access;
  private final TenantDirectory directory;
  private final IntegrationClients integrations;
  private final org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository clients;
  /**
   * @param platform 平台管理公开端口
   * @param tenants 当前有效成员查询，不使用客户端memberId
   * @param access 实时功能授权，不缓存撤权前结果
   * @param directory 受控租户存在性端口，不直接读数据库
   * @param integrations 既有合作方解析资格，不授予记录执行权
   * @param clients 认证服务器权威客户端目录
   * @param planning 同一权威事务读取的数据范围与字段权限计划
   * @param rows 统一范围执行规则；本入口只接受已声明ALL或解析后的组织范围
   * @param fields 公共字段白名单和脱敏执行端口
   * @param personnel 选定人员与组织的实时任职关系验证
   * @param objects 延迟读取唯一对象声明，避免安全桥接与业务装配循环
   */
  public BusinessObjectPlatformBridge(PlatformAdministration platform,TenantAccess tenants,AccessAuthorization access,TenantDirectory directory,IntegrationClients integrations,org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository clients,AccessPlanning planning,RowAccessControl rows,FieldProtection fields,org.springframework.beans.factory.ObjectProvider<BusinessObjects.Provider> objects){this.platform=platform;this.tenants=tenants;this.access=access;this.directory=directory;this.integrations=integrations;this.clients=clients;this.planning=planning;this.rows=rows;this.fields=fields;this.objects=objects;}
  /** 所有通用能力共用目标对象操作准入；对象存取仍执行行范围和字段限制。 */
  @Override public boolean operationAllowed(BusinessObjects.Context c,String operationId,String permissionCode){
    // 合作方的解析资格不等于业务执行权；本入口只接受统一模型中的真实账号主体。
    UUID actor;
    try{actor=UUID.fromString(c.actorId());}catch(IllegalArgumentException error){return false;}
    return tenants.available(actor).stream().filter(m->m.tenantId().toString().equals(c.tenantId()))
      .anyMatch(m->access.hasPermission(AccessAuthorization.Subject.tenant(actor,m.tenantId(),m.memberId()),permissionCode));
  }
  @Override public boolean platformCommandAllowed(BusinessObjects.Context c,String permissionCode){
    if(c==null||c.tenantId()!=null||c.actorId()==null||permissionCode==null||permissionCode.isBlank())return false;
    UUID actor;
    try{actor=UUID.fromString(c.actorId());}catch(IllegalArgumentException invalid){return false;}
    return platform.isAdministrator(actor)&&access.hasPermission(AccessAuthorization.Subject.platform(actor),permissionCode);
  }

  /** 只报告确实经过本桥接的对象运行资源，不替代其他对象自己的执行器。 */
  @Override public Set<String> enforcedResources(){return objects.orderedStream().filter(p->p.runtime()!=null&&p.descriptor().dataAccess()!=null).flatMap(p->p.descriptor().dataAccess().resources().stream()).map(BusinessObjects.DataResource::resource).collect(java.util.stream.Collectors.toUnmodifiableSet());}
  /** 记录归属来自本租户真实重读；范围决策、组织集合和字段算法均使用统一模型。 */
  @Override public BusinessObjectRuntime.Snapshot authorizeRecord(BusinessObjects.Context c,BusinessObjects.Descriptor descriptor,String operationCode,BusinessObjectRuntime.Snapshot snapshot){
    try{
      var declaration=descriptor.dataAccess();
      if(declaration==null||!"TENANT".equals(declaration.scope())||!declaration.operationScopes().containsKey(operationCode))throw new SecurityException("DATA_SCOPE_UNDECLARED");
      var operation=descriptor.operations().stream().filter(op->op.operationCode().equals(operationCode)).findFirst().orElseThrow();
      UUID actor=UUID.fromString(c.actorId()),tenant=UUID.fromString(c.tenantId());
      var member=tenants.available(actor).stream().filter(m->m.tenantId().equals(tenant)).findFirst().orElseThrow(()->new SecurityException("MEMBER_UNAVAILABLE"));
      var subject=AccessAuthorization.Subject.tenant(actor,tenant,member.memberId());
      String resource=declaration.resources().getFirst().resource();
      var plan=planning.plan(subject,operation.permissionCode(),resource);
      boolean allowed;
      if(plan.tenantWide()){
        // 固定租户表的无组织打印记录必须提供实际记录键；执行器核对它与请求一致。
        allowed=snapshot.organizationId()==null
            ? operation.printing()!=null&&snapshot.recordId()!=null
            : rows.allowsOrganization(plan,UUID.fromString(snapshot.organizationId()));
      }else{
        allowed=snapshot.recordId()!=null&&rows.allowsRecordId(plan,UUID.fromString(snapshot.recordId()));
      }
      if(!allowed)throw new SecurityException("DATA_SCOPE_DENIED");
      var projected=fields.project(subject,resource,snapshot.values());
      var required=new HashSet<String>();descriptor.fields().stream().filter(BusinessObjects.Field::required).forEach(f->required.add(f.key()));
      operation.interaction().capabilities().forEach(cap->cap.fields().stream().filter(BusinessObjects.Field::required).forEach(f->required.add(f.key())));
      if(!projected.keySet().containsAll(required))throw new SecurityException("DATA_FIELD_OUTPUT_DENIED");
      return new BusinessObjectRuntime.Snapshot(snapshot.version(),snapshot.organizationId(),projected,snapshot.recordId());
    }catch(SecurityException|IllegalArgumentException error){throw new BusinessObjects.Rejection(403,"BUSINESS_OBJECT_DATA_ACCESS_DENIED");}
  }
  @Override public boolean tenantExists(String tenant){return directory.exists(UUID.fromString(tenant));}
  @Override public boolean platformAdministrator(String actor){return platform.isAdministrator(UUID.fromString(actor))&&access.hasPermission(AccessAuthorization.Subject.platform(UUID.fromString(actor)),"business.object.catalog:manage");}
  @Override public boolean active(BusinessObjects.Context c){
    if(c.actorId().startsWith("partner-")){var policy=integrations.policy(c.actorId());var client=clients.findById(c.actorId());return policy!=null&&policy.enabled()&&policy.tenant().toString().equals(c.tenantId())&&client!=null&&client.getScopes().contains("business-object:resolve");}
    return tenants.available(UUID.fromString(c.actorId())).stream().anyMatch(m->m.tenantId().toString().equals(c.tenantId()));
  }
  @Override public boolean tenantAdministrator(BusinessObjects.Context c){return tenants.available(UUID.fromString(c.actorId())).stream().filter(m->m.tenantId().toString().equals(c.tenantId())).anyMatch(m->access.hasPermission(AccessAuthorization.Subject.tenant(UUID.fromString(c.actorId()),m.tenantId(),m.memberId()),"business.object.configuration:manage"));}
}
