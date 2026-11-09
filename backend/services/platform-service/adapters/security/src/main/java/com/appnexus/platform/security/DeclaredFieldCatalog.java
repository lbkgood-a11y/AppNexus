package com.appnexus.platform.security;

import com.appnexus.platform.authorization.*;
import java.util.*;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

/** 启动扫描资源响应声明，自动生成统一字段目录；标识冲突或无法执行的字段类型直接阻止启动。 */
@Component
public final class DeclaredFieldCatalog implements FieldCatalog {
    private final Map<PermissionCatalog.ScopeType,List<FieldSecurity.Descriptor>> catalog;

    public DeclaredFieldCatalog() {
        var scanner=new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ProtectedResource.class));
        var descriptors=new EnumMap<PermissionCatalog.ScopeType,Map<String,FieldSecurity.Descriptor>>(PermissionCatalog.ScopeType.class);
        for(BeanDefinition definition:scanner.findCandidateComponents("com.appnexus.platform")) {
            try {
                Class<?> type=Class.forName(definition.getBeanClassName(),false,getClass().getClassLoader());
                var resource=type.getAnnotation(ProtectedResource.class);
                if(!type.isRecord()||!resource.value().matches("[a-z][a-z0-9.]*"))throw new IllegalStateException("Invalid protected resource declaration");
                var fields=descriptors.computeIfAbsent(resource.scope(),ignored->new TreeMap<>());
                for(var component:type.getRecordComponents()) {
                    var field=component.getAnnotation(ProtectedField.class);if(field==null)continue;
                    if(component.getType()!=String.class||field.nameZh().isBlank()||field.nameEn().isBlank())throw new IllegalStateException("Protected fields must be documented String response components");
                    var descriptor=new FieldSecurity.Descriptor(resource.value(),component.getName(),field.nameZh(),field.nameEn(),field.kind(),List.of(FieldSecurity.Mode.HIDDEN,FieldSecurity.Mode.MASKED));
                    var previous=fields.putIfAbsent(resource.value()+":"+component.getName(),descriptor);
                    if(previous!=null&&!previous.equals(descriptor))throw new IllegalStateException("Conflicting protected field declarations");
                }
            } catch(ClassNotFoundException error){throw new IllegalStateException("Cannot load protected response declaration",error);}
        }
        var result=new EnumMap<PermissionCatalog.ScopeType,List<FieldSecurity.Descriptor>>(PermissionCatalog.ScopeType.class);
        descriptors.forEach((scope,fields)->result.put(scope,List.copyOf(fields.values())));catalog=Map.copyOf(result);
    }

    @Override public List<FieldSecurity.Descriptor> list(PermissionCatalog.ScopeType scope){return catalog.getOrDefault(Objects.requireNonNull(scope),List.of());}
}
