package com.appnexus.platform;

import com.appnexus.contracts.operation.RecordAuditSource;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;
    private final HttpServletRequest request;
    private final JsonMapper json=JsonMapper.builder().build();
    /** @param jdbc 已统一装配的数据源 @param request 仅读取安全链上下文及受控路由变量 */
    public RecordAuditResponseAdvice(JdbcTemplate jdbc,HttpServletRequest request){this.jdbc=jdbc;this.request=request;}
    @Override public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter){return method.hasMethodAnnotation(RecordAuditSource.class);}
    @Override public Object beforeBodyWrite(Object body,MethodParameter method,MediaType type,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest source,ServerHttpResponse response){
        var declaration=method.getMethodAnnotation(RecordAuditSource.class);
        if(body==null || declaration==null || !request.getMethod().equals("GET"))return body;
        if(response instanceof ServletServerHttpResponse servlet && servlet.getServletResponse().getStatus()>=300)return body;
        // SQL 标识符只读构建注解，并再次验证；所有对象标识和租户都使用参数绑定。
        if(!declaration.table().matches("[a-z][a-z0-9_]*")||!declaration.key().matches("[a-z][a-z0-9_]*"))throw new IllegalStateException("Invalid audit source");
        String tenant=null;
        if(declaration.tenant()){
            if(!declaration.managedTenant().isEmpty()){
                Object variables=request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if(variables instanceof Map<?,?> map && map.get(declaration.managedTenant()) instanceof String id)tenant=UUID.fromString(id).toString();
            }else if(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context)tenant=context.tenantId().toString();
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
            var args=new ArrayList<Object>();if(tenant!=null)args.add(tenant);args.addAll(batch);
            String key=declaration.binaryKey()?"BIN_TO_UUID("+declaration.key()+")":declaration.key();
            String where=tenant==null?"":"tenant_id="+(declaration.binaryTenant()?"UUID_TO_BIN(?)":"?")+" AND ";
            String placeholders=String.join(",",Collections.nCopies(batch.size(),declaration.binaryKey()?"UUID_TO_BIN(?)":"?"));
            jdbc.query("SELECT "+key+" audit_id,BIN_TO_UUID(created_by) creator,created_at,BIN_TO_UUID(updated_by) updater,updated_at FROM "+declaration.table()+" WHERE "+where+declaration.key()+" IN ("+placeholders+")",rs->{
                var audit=new LinkedHashMap<String,Object>();audit.put("createdBy",rs.getString("creator"));audit.put("updatedBy",rs.getString("updater"));
                for(String field:List.of("created_at","updated_at")){var timestamp=rs.getTimestamp(field);audit.put(field.equals("created_at")?"createdAt":"updatedAt",timestamp==null?null:timestamp.toInstant().toString());}
                audits.put(rs.getString("audit_id").toLowerCase(Locale.ROOT),audit);
            },args.toArray());
        }
        rows.forEach(row->{Object id=identifier(row,declaration.id());if(id!=null){var audit=audits.get(id.toString().toLowerCase(Locale.ROOT));if(audit!=null)row.put("audit",audit);}});
        response.getHeaders().setCacheControl(CacheControl.noStore());
        // 投影已将领域 DTO 转为 Map；返回 JSON 树，使消息转换器不再套用原 List<DTO>
        // 的元素序列化器，否则非空集合会把 Map 强转成 DTO 并返回 500，审计字段也无法写出。
        return json.valueToTree(result);
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
