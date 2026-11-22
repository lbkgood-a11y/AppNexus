package com.appnexus.platform;

import com.appnexus.contracts.operation.RecordAuditNames;
import com.appnexus.contracts.operation.ReferenceNames;
import com.appnexus.contracts.operation.ResponseReferenceNames;
import com.appnexus.masterdata.api.masterdata.MasterDataReferenceNames;
import com.appnexus.contracts.operation.RecordAuditSource;
import com.appnexus.platform.organization.PersonnelDirectory;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.json.JsonMapper;

/**
 * 组合层装配各模块声明的只读记录审计；先执行原接口授权及字段脱敏，再为已返回对象批量补充投影。
 * 不提供自由表查询，不遍历业务正文或扩展字段；每次查询独立绑定可信租户，避免同 ID 跨租户泄露。
 */
@Order(100)
@RestControllerAdvice
public final class RecordAuditResponseAdvice implements ResponseBodyAdvice<Object> {
    private final com.appnexus.platform.persistence.AuditProjectionMapper mapper;
    private final PersonnelDirectory directory;
    private final HttpServletRequest request;
    private final MasterDataReferenceNames masterNames;
    private final com.appnexus.platform.organization.PersonManagement people;
    private final com.appnexus.platform.configuration.BusinessValueCatalog dictionaries;
    private final com.appnexus.platform.configuration.GeographyCatalog geography;
    private final com.appnexus.platform.authorization.AccessAuthorization authorization;
    private final JsonMapper json=JsonMapper.builder().build();
    /** @param mapper 已统一装配的审计数据源
     * @param directory 权威账号目录，仅查询已授权审计块中的账号
     * @param request 仅读取安全链上下文及受控路由变量 */
    public RecordAuditResponseAdvice(com.appnexus.platform.persistence.AuditProjectionMapper mapper,PersonnelDirectory directory,HttpServletRequest request,com.appnexus.platform.authorization.AccessAuthorization authorization,MasterDataReferenceNames masterNames,
        com.appnexus.platform.configuration.BusinessValueCatalog dictionaries,
        com.appnexus.platform.configuration.GeographyCatalog geography,com.appnexus.platform.organization.PersonManagement people){
        this.mapper=mapper;
        this.directory=directory;
        this.request=request;
        this.authorization=authorization;
        this.masterNames=masterNames;
        this.dictionaries=dictionaries;
        this.geography=geography;
        this.people=people;
    }
    @Override public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter){
        return method.hasMethodAnnotation(RecordAuditSource.class)||method.hasMethodAnnotation(RecordAuditNames.class)||method.hasMethodAnnotation(ResponseReferenceNames.class);
    }
    @Override public Object beforeBodyWrite(Object body,MethodParameter method,MediaType type,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest source,ServerHttpResponse response){
        var declaration=method.getMethodAnnotation(RecordAuditSource.class);
        var embedded=method.getMethodAnnotation(RecordAuditNames.class);
        var reference=method.getMethodAnnotation(ResponseReferenceNames.class);
        if(body==null || !request.getMethod().equals("GET"))return body;
        if(response instanceof ServletServerHttpResponse servlet && servlet.getServletResponse().getStatus()>=300)return body;
        UUID requestTenant=request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context ? context.tenantId() : null;
        if(declaration==null){
            Object result=json.convertValue(body,Object.class);
            if(embedded!=null){
                UUID scope=embedded.platform()?platformActor():requestTenant;
                if(scope!=null){
                    var blocks=new ArrayList<Map<String,Object>>();
                    for(String path:embedded.paths())collectAuditBlocks(result,path.split("\\."),0,blocks);
                    attachNames(scope,embedded.platform(),embedded.event(),blocks);
                }
            }
            if(reference!=null && request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context){
                attachReferences(result,context,reference);
            }
            response.getHeaders().setCacheControl(CacheControl.noStore());
            return json.valueToTree(result);
        }
        // SQL 标识符只读构建注解，并再次验证；所有对象标识和租户都使用参数绑定。
        if(!declaration.table().matches("[a-z][a-z0-9_]*")||!declaration.key().matches("[a-z][a-z0-9_]*"))throw new IllegalStateException("Invalid audit source");
        String tenant=null;
        if(declaration.tenant()){
            if(!declaration.managedTenant().isEmpty()){
                Object variables=request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if(variables instanceof Map<?,?> map && map.get(declaration.managedTenant()) instanceof String id)tenant=UUID.fromString(id).toString();
            }else if(requestTenant!=null)tenant=requestTenant.toString();
            if(tenant==null)throw new IllegalStateException("Verified audit tenant required");
        }
        Object result=json.convertValue(body,Object.class),node=result;
        for(String segment:declaration.path().split("\\."))if(!segment.isEmpty())node=node instanceof Map<?,?> map?map.get(segment):null;
        var rows=new ArrayList<Map<String,Object>>();collect(node,rows);
        var ids=rows.stream().map(row->identifier(row,declaration.id())).filter(Objects::nonNull).map(Object::toString).distinct().toList();
        if(ids.isEmpty())return json.valueToTree(result);
        Map<String,Map<String,Object>> audits=new HashMap<>();
        // 每批最多 200 条，适配完整树而不生成无限 IN 参数；只返回最小审计元数据。
        for(int offset=0;offset<ids.size();offset+=200){
            var batch=ids.subList(offset,Math.min(ids.size(),offset+200));
            for(var raw:mapper.read(declaration,tenant,batch)){
                var audit=new LinkedHashMap<String,Object>();audit.put("createdBy",raw.get("creator"));audit.put("updatedBy",raw.get("updater"));
                for(String field:List.of("created_at","updated_at")){Object timestamp=raw.get(field);audit.put(field.equals("created_at")?"createdAt":"updatedAt",timestamp==null?null:((java.sql.Timestamp)timestamp).toInstant().toString());}
                audits.put(((String)raw.get("audit_id")).toLowerCase(Locale.ROOT),audit);
            }
        }
        UUID scope=declaration.tenant()?UUID.fromString(tenant):platformActor();
        if(scope!=null)attachNames(scope,!declaration.tenant(),false,audits.values());
        if(embedded!=null&&scope!=null){
            var blocks=new ArrayList<Map<String,Object>>();
            for(String path:embedded.paths())collectAuditBlocks(result,path.split("\\."),0,blocks);
            attachNames(scope,!declaration.tenant(),embedded.event(),blocks);
        }
        rows.forEach(row->{Object id=identifier(row,declaration.id());if(id!=null){var audit=audits.get(id.toString().toLowerCase(Locale.ROOT));if(audit!=null)row.put("audit",audit);}});
        if(reference!=null && request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context){
            attachReferences(result,context,reference);
        }
        response.getHeaders().setCacheControl(CacheControl.noStore());
        // 投影已将领域 DTO 转为 Map；返回 JSON 树，使消息转换器不再套用原 List<DTO>
        // 的元素序列化器，否则非空集合会把 Map 强转成 DTO 并返回 500，审计字段也无法写出。
        return json.valueToTree(result);
    }

    private record ReferenceTarget(Map<String,Object> row,ResponseReferenceNames.Binding binding) {}

    /** 汇总多个表头和明细路径；授权被拒或缺失字段不会以 ID 代替名称。 */
    private void attachReferences(Object body,VerifiedTenantContext context,ResponseReferenceNames reference){
        var targets=new ArrayList<ReferenceTarget>();
        for(var binding:reference.value()){
            var rows=new ArrayList<Map<String,Object>>();
            String[] path=binding.path().isEmpty()?new String[0]:binding.path().split("\\.");
            collectAuditBlocks(body,path,0,rows);
            for(var row:rows)if(binding.when().isEmpty()||binding.equalsValue().equals(row.get(binding.when()))){
                targets.add(new ReferenceTarget(row,binding));
            }
        }
        var keys=targets.stream().map(RecordAuditResponseAdvice::referenceKey).filter(Objects::nonNull).toList();
        var names=ReferenceNames.resolveKeys(this::loadNames,context,keys);
        for(var target:targets){
            var key=referenceKey(target);
            if(key==null)continue;
            String value=names.get(key);
            if(value!=null)target.row().put(target.binding().name(),value);
        }
    }

    /** 从构建内固定路径提取引用；掩码值及空编码不进入目录调用。 */
    private static ReferenceNames.Key referenceKey(ReferenceTarget target){
        Object raw=identifier(target.row(),target.binding().id());
        String value=target.binding().uuid()?accountId(raw):raw instanceof String code && !code.isBlank() && code.length()<=200?code:null;
        return value==null?null:new ReferenceNames.Key(target.binding().type(),value);
    }

    /** 每批按所有者分组，组织与岗位共用一次查询，字典编码和值一起绑定。 */
    private Map<ReferenceNames.Key,String> loadNames(VerifiedTenantContext context,List<ReferenceNames.Key> keys){
        var master=new ArrayList<ReferenceNames.Key>();
        var persons=new ArrayList<String>();
        var dictionary=new ArrayList<com.appnexus.platform.configuration.BusinessValueCatalog.NameKey>();
        var organizations=new ArrayList<PersonnelDirectory.OrganizationNameKey>();
        var geographical=new HashSet<String>();
        for(var key:keys){
            String type=key.type();
            if(type.equals("organization")||type.equals("position"))organizations.add(new PersonnelDirectory.OrganizationNameKey(type,key.value()));
            else if(type.equals("person"))persons.add(key.value());
            else if(type.equals("geography"))geographical.add(key.value());
            else if(type.startsWith("dictionary:"))dictionary.add(new com.appnexus.platform.configuration.BusinessValueCatalog.NameKey(type.substring(11),key.value()));
            else master.add(key);
        }
        var result=new HashMap<ReferenceNames.Key,String>();
        if(!persons.isEmpty()){
            try{people.names(context,persons).forEach((id,name)->result.put(new ReferenceNames.Key("person",id),name));}
            catch(com.appnexus.platform.organization.PersonData.Rejection denied){if(denied.status!=403)throw denied;}
        }
        if(!master.isEmpty())result.putAll(masterNames.load(context,master));
        if(!dictionary.isEmpty())dictionaries.names(context.tenantId(),dictionary).forEach((key,name)->
            result.put(new ReferenceNames.Key("dictionary:"+key.dictionary(),key.value()),name));
        if(!organizations.isEmpty())directory.organizationReferenceNames(context,organizations).forEach((key,name)->
            result.put(new ReferenceNames.Key(key.kind(),key.id()),name));
        if(!geographical.isEmpty())geography.names(context.tenantId(),List.copyOf(geographical)).forEach((code,name)->
            result.put(new ReferenceNames.Key("geography",code),name));
        return result;
    }

    /** 逐条匹配构建内路径，只在数组展开时递归，不搜索业务属性中的账号值。 */
    @SuppressWarnings("unchecked")
    private static void collectAuditBlocks(Object node,String[] path,int depth,List<Map<String,Object>> blocks){
        if(node instanceof List<?> list){
            list.forEach(item->collectAuditBlocks(item,path,depth,blocks));
        }else if(node instanceof Map<?,?> map){
            if(depth==path.length)blocks.add((Map<String,Object>)map);
            else collectAuditBlocks(map.get(path[depth]),path,depth+1,blocks);
        }
    }

    /** 同一响应内按 UUID 去重后查询。字段隐去、无效或不可见时不解析，也不回填 ID。 */
    private void attachNames(UUID context,boolean platform,boolean event,Collection<Map<String,Object>> blocks){
        var actorFields=event?List.of("actor"):List.of("createdBy","updatedBy");
        var fields=actorFields.stream().map(field->new ReferenceNames.Field<Map<String,Object>>(
            "account",block->accountId(block.get(field)))).toList();
        var resolved=new HashMap<ReferenceNames.Key,String>();
        ReferenceNames.resolve((scope,keys)->{
            var ids=keys.stream().map(ReferenceNames.Key::value).toList();
            var names=platform?directory.platformAccountNames(scope,ids):directory.accountNames(scope,ids);
            var result=new HashMap<ReferenceNames.Key,String>();
            names.forEach((id,name)->result.put(new ReferenceNames.Key("account",id),name));
            return result;
        },context,blocks,fields,resolved);
        for(var block:blocks){
            for(int i=0;i<fields.size();i++){
                String name=resolved.get(fields.get(i).key(block));
                if(name!=null)block.put(actorFields.get(i)+"Name",name);
            }
        }
    }

    /** 平台名称只向具备已登记审计读取权的认证账号返回。 */
    private UUID platformActor(){
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String id=auth!=null&&auth.isAuthenticated()?accountId(auth.getName()):null;
        if(id==null)return null;
        var actor=UUID.fromString(id);
        return authorization.hasPermission(com.appnexus.platform.authorization.AccessAuthorization.Subject.platform(actor),
            "audit.entry:read")?actor:null;
    }

    /** 只接受标准 UUID，字段脱敏后的文本不得进入账号目录。 */
    private static String accountId(Object value){
        if(!(value instanceof String id) || id.length()!=36)return null;
        try{return UUID.fromString(id).toString();}
        catch(IllegalArgumentException invalid){return null;}
    }

    /** 只递归已授权对象列表和树形 children，禁止把任意业务 JSON 内的 id 当审计查询依据。 */
    @SuppressWarnings("unchecked")
    private static void collect(Object node,List<Map<String,Object>> rows){
        if(node instanceof List<?> list)list.forEach(item->collect(item,rows));
        else if(node instanceof Map<?,?> map){rows.add((Map<String,Object>)map);collect(map.get("children"),rows);}
    }
    /** 固定注解可指向 descriptor.objectType 等包装字段，不搜索任意嵌套业务数据。 */
    private static Object identifier(Object node,String path){
        for(String segment:path.split("\\."))node=node instanceof Map<?,?> map?map.get(segment):null;
        return node;
    }
}
