package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.*;
import java.lang.reflect.Array;
import java.util.*;
import org.springframework.stereotype.Service;

/** 通用字段投影执行器；同一次响应按资源读取一次限制，不修改原 DTO，不按字段名猜测算法。 */
@Service
public final class MyBatisFieldProtection implements FieldProtection,DataAccessDeclaration.Executor {
    private final FieldProtectionMapper mapper;
    private final DataAccessCatalog catalog;
    private record Binding(PermissionCatalog.ScopeType scope,DataAccessDeclaration.Resource resource) {}
    private final Map<Class<?>,Binding> bindings;
    /**
     * 自动投影绑定来自统一对象声明；启动时校验真实record及String字段，失配禁止服务启动。
     * @param mapper 有效角色策略查询；数据库异常必须向上传播，不能回退原文
     * @param catalog 只读代码目录，不接受客户端传入类名或字段路径
     */
    public MyBatisFieldProtection(FieldProtectionMapper mapper,DataAccessCatalog catalog){
        this.mapper=mapper;this.catalog=catalog;
        var result=new HashMap<Class<?>,Binding>();
        for(var scope:PermissionCatalog.ScopeType.values())for(var object:catalog.list(scope))for(var resource:object.resources()){
            if(resource.responseType().isEmpty())continue;
            try{
                var type=Class.forName(resource.responseType(),false,getClass().getClassLoader());
                // 响应类型来自代码中的对象声明，业务模块和平台均可声明自有记录。
                if(!type.isRecord()||!type.getName().startsWith("com.appnexus.")||resource.fields().isEmpty())throw new IllegalStateException("DATA_FIELD_RESPONSE_TYPE_INVALID:"+resource.resource());
                for(var field:resource.fields())if(Arrays.stream(type.getRecordComponents()).noneMatch(c->c.getName().equals(field.field())&&c.getType()==String.class))throw new IllegalStateException("DATA_FIELD_RESPONSE_COMPONENT_INVALID:"+resource.resource()+":"+field.field());
                if(result.putIfAbsent(type,new Binding(scope,resource))!=null)throw new IllegalStateException("DATA_FIELD_RESPONSE_BINDING_DUPLICATE:"+resource.resource());
            }catch(ClassNotFoundException error){throw new IllegalStateException("DATA_FIELD_RESPONSE_TYPE_MISSING:"+resource.resource(),error);}
        }
        bindings=Map.copyOf(result);
    }
    /** 仅报告已完成类型及字段校验的自动投影资源，不宣称支持业务显式执行器。 */
    @Override public Set<String> enforcedResources(){return bindings.values().stream().map(b->b.resource().resource()).collect(java.util.stream.Collectors.toUnmodifiableSet());}

    @Override public Map<String,FieldSecurity.Mode> restrictions(AccessAuthorization.Subject subject,String resource) {
        if(subject==null||subject.accountId()==null||(subject.tenantId()==null)!=(subject.memberId()==null))throw new SecurityException("Verified field protection subject required");
        var rows=subject.tenantId()==null?mapper.platform(subject.accountId().toString(),resource):mapper.tenant(subject.accountId().toString(),subject.tenantId().toString(),subject.memberId().toString(),resource);
        var result=new HashMap<String,FieldSecurity.Mode>();
        for(var row:rows)result.merge((String)row.get("field_code"),FieldSecurity.Mode.valueOf((String)row.get("display_mode")),FieldSecurity::stricter);
        return Map.copyOf(result);
    }

    @Override @SuppressWarnings("unchecked") public <T> T protect(AccessAuthorization.Subject subject,T value){return (T)project(subject,value,new HashMap<>(),0);}
    @Override public Map<String,Object> project(AccessAuthorization.Subject subject,String resource,Map<String,?> values){
        var modes=restrictions(subject,resource);
        var scope=subject.tenantId()==null?PermissionCatalog.ScopeType.PLATFORM:PermissionCatalog.ScopeType.TENANT;
        var result=new LinkedHashMap<String,Object>();
        var declared=catalog.fields(scope).stream().filter(field->field.resource().equals(resource)).toList();
        for(var field:declared){
            if(!values.containsKey(field.field()))continue;
            var mode=modes.get(field.field());Object value=values.get(field.field());
            if(mode!=null&&!field.modes().contains(mode))throw new SecurityException("DATA_FIELD_MODE_UNSUPPORTED");
            if(mode==FieldSecurity.Mode.HIDDEN)continue;
            if(mode==FieldSecurity.Mode.MASKED){
                if(value!=null&&!(value instanceof String))throw new SecurityException("DATA_FIELD_OUTPUT_DENIED");
                value=FieldSecurity.display(field.kind(),(String)value,mode);
            }
            result.put(field.field(),value);
        }
        return Collections.unmodifiableMap(result);
    }

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
        var binding=bindings.get(type);
        var resource=binding==null?null:binding.resource();
        Map<String,FieldSecurity.Mode> modes=Map.of();
        if(resource!=null) {
            var scope=subject!=null&&subject.tenantId()!=null?PermissionCatalog.ScopeType.TENANT:PermissionCatalog.ScopeType.PLATFORM;
            if(binding.scope()!=scope)throw new SecurityException("Protected response management scope mismatch");
            modes=cache.computeIfAbsent(resource.resource(),ignored->restrictions(subject,resource.resource()));
        }
        var components=type.getRecordComponents();var arguments=new Object[components.length];var types=new Class<?>[components.length];
        try {
            for(int i=0;i<components.length;i++) {
                var component=components[i];types[i]=component.getType();Object original=component.getAccessor().invoke(value);
                var field=resource==null?null:resource.fields().stream().filter(f->f.field().equals(component.getName())).findFirst().orElse(null);
                arguments[i]=field!=null&&resource!=null?FieldSecurity.display(field.kind(),(String)original,modes.get(component.getName())):project(subject,original,cache,depth+1);
                if(resource!=null&&component.getName().equals("restrictedFields")&&component.getType()==Set.class)arguments[i]=Set.copyOf(modes.keySet());
            }
            return type.getDeclaredConstructor(types).newInstance(arguments);
        }catch(ReflectiveOperationException error){throw new SecurityException("Cannot enforce protected response projection",error);}
    }
}
