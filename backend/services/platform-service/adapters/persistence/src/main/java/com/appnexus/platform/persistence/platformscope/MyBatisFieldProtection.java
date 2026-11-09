package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.*;
import java.lang.reflect.Array;
import java.util.*;
import org.springframework.stereotype.Service;

/** 通用字段投影执行器；同一次响应按资源读取一次限制，不修改原 DTO，不按字段名猜测算法。 */
@Service
public final class MyBatisFieldProtection implements FieldProtection {
    private final FieldProtectionMapper mapper;
    public MyBatisFieldProtection(FieldProtectionMapper mapper){this.mapper=mapper;}

    @Override public Map<String,FieldSecurity.Mode> restrictions(AccessAuthorization.Subject subject,String resource) {
        if(subject==null||subject.accountId()==null||(subject.tenantId()==null)!=(subject.memberId()==null))throw new SecurityException("Verified field protection subject required");
        var rows=subject.tenantId()==null?mapper.platform(subject.accountId().toString(),resource):mapper.tenant(subject.accountId().toString(),subject.tenantId().toString(),subject.memberId().toString(),resource);
        var result=new HashMap<String,FieldSecurity.Mode>();
        for(var row:rows)result.merge((String)row.get("field_code"),FieldSecurity.Mode.valueOf((String)row.get("display_mode")),(left,right)->left==FieldSecurity.Mode.HIDDEN?left:right);
        return Map.copyOf(result);
    }

    @Override @SuppressWarnings("unchecked") public <T> T protect(AccessAuthorization.Subject subject,T value){return (T)project(subject,value,new HashMap<>(),0);}

    /** 最大嵌套深度为 64 层；超限直接拒绝响应，不能在异常时返回未处理原文。 */
    private Object project(AccessAuthorization.Subject subject,Object value,Map<String,Map<String,FieldSecurity.Mode>> cache,int depth) {
        if(value==null)return null;
        if(depth>64)throw new SecurityException("Protected response nesting limit exceeded");
        if(value instanceof List<?> list)return list.stream().map(item->project(subject,item,cache,depth+1)).toList();
        if(value instanceof Set<?> set){var result=new LinkedHashSet<>();set.forEach(item->result.add(project(subject,item,cache,depth+1)));return result;}
        if(value instanceof Map<?,?> map){var result=new LinkedHashMap<>();map.forEach((key,item)->result.put(key,project(subject,item,cache,depth+1)));return result;}
        Class<?> type=value.getClass();
        if(type.isArray()&&!type.componentType().isPrimitive()){Object result=Array.newInstance(type.componentType(),Array.getLength(value));for(int i=0;i<Array.getLength(value);i++)Array.set(result,i,project(subject,Array.get(value,i),cache,depth+1));return result;}
        if(!type.isRecord()||!type.getName().startsWith("com.appnexus.platform."))return value;
        var resource=type.getAnnotation(ProtectedResource.class);
        Map<String,FieldSecurity.Mode> modes=Map.of();
        if(resource!=null) {
            var scope=subject!=null&&subject.tenantId()!=null?PermissionCatalog.ScopeType.TENANT:PermissionCatalog.ScopeType.PLATFORM;
            if(resource.scope()!=scope)throw new SecurityException("Protected response management scope mismatch");
            modes=cache.computeIfAbsent(resource.value(),ignored->restrictions(subject,resource.value()));
        }
        var components=type.getRecordComponents();var arguments=new Object[components.length];var types=new Class<?>[components.length];
        try {
            for(int i=0;i<components.length;i++) {
                var component=components[i];types[i]=component.getType();Object original=component.getAccessor().invoke(value);
                var field=component.getAnnotation(ProtectedField.class);
                arguments[i]=field!=null&&resource!=null?FieldSecurity.display(field.kind(),(String)original,modes.get(component.getName())):project(subject,original,cache,depth+1);
                if(resource!=null&&component.getName().equals("restrictedFields")&&component.getType()==Set.class)arguments[i]=Set.copyOf(modes.keySet());
            }
            return type.getDeclaredConstructor(types).newInstance(arguments);
        }catch(ReflectiveOperationException error){throw new SecurityException("Cannot enforce protected response projection",error);}
    }
}
