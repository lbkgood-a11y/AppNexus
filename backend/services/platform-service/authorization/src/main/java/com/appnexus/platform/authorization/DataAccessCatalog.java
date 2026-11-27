package com.appnexus.platform.authorization;

import java.util.*;

/** 不可变数据权限目录；代码声明为唯一来源，数据库元数据仅为受控投影。 */
public final class DataAccessCatalog {
    private final List<DataAccessDeclaration.Descriptor> descriptors;
    /**
     * 校验并聚合声明。冲突、非法路径、未定义父资源和无执行语义范围均阻止装配。
     * @param projections 由统一对象声明派生的只读投影；缺省空目录，不猜测资源
     */
    public DataAccessCatalog(List<DataAccessDeclaration.Descriptor> projections) {
        var resources=new HashSet<String>();var operations=new HashSet<String>();var objects=new HashSet<String>();
        var result=new ArrayList<DataAccessDeclaration.Descriptor>();
        for(var projection:projections) {
            var d=Objects.requireNonNull(projection);
            if(d.version()<1||d.objectType()==null||!d.objectType().matches("[A-Z][A-Za-z0-9]*")||d.scope()==null||!objects.add(d.scope()+":"+d.objectType())||d.resources().isEmpty())throw invalid();
            String root=d.resources().getFirst().resource();
            var dimensionKeys=new HashSet<String>();
            for(var dimension:d.dimensions()) {
                if(dimension.field()==null||!dimension.field().matches("[a-z][A-Za-z0-9]*")||!dimensionKeys.add(dimension.field())
                    ||dimension.nameZh()==null||dimension.nameZh().isBlank()||dimension.nameEn()==null||dimension.nameEn().isBlank()
                    ||!Set.of("UUID","TEXT").contains(dimension.valueType())||dimension.operators().isEmpty()
                    ||!Set.of("EQ","IN").containsAll(dimension.operators()))throw invalid();
                if(!validSource(dimension)||!validParameters(dimension)||!dimension.operationCodes().stream().allMatch(code->d.operations().stream()
                    .anyMatch(op->op.operationId().equals(d.objectType()+"."+code)
                        &&op.scopes().contains(DataScopeAuthorization.ScopeType.RULES))))throw invalid();
            }
            for(int i=0;i<d.resources().size();i++) {
                var r=d.resources().get(i);
                if(r.resource()==null||!r.resource().matches("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*")||!resources.add(r.resource())||r.nameZh()==null||r.nameZh().isBlank()||r.nameEn()==null||r.nameEn().isBlank()||!(i==0?"".equals(r.parentResource()):root.equals(r.parentResource())))throw invalid();
                var fields=new HashSet<String>();
                for(var f:r.fields())if(f.field()==null||!f.field().matches("[a-z][A-Za-z0-9]*")||!fields.add(f.field())||f.nameZh()==null||f.nameZh().isBlank()||f.nameEn()==null||f.nameEn().isBlank()||f.kind()==null||f.modes().isEmpty())throw invalid();
            }
            for(var op:d.operations())if(op.operationId()==null||!op.operationId().startsWith(d.objectType()+".")||!operations.add(op.operationId())||op.permissionCode()==null||!op.permissionCode().startsWith(root+":")||op.scopes().stream().anyMatch(scope->!scope.configurable()))throw invalid();
            result.add(d);
        }
        descriptors=List.copyOf(result);
    }
    /**
     * 从统一业务对象声明派生安全投影，身份、版本、操作和权限码只读取所属Descriptor。
     * @param objects 业务对象唯一声明提供者
     * @return 只读安全目录；转换失败直接阻止装配
     */
    public static DataAccessCatalog fromObjects(List<com.appnexus.contracts.businessobject.BusinessObjects.Provider> objects){
        var projections=new ArrayList<DataAccessDeclaration.Descriptor>();
        var seenObjects=new HashSet<String>();
        for(var provider:objects){
            var object=provider.descriptor();if(object.dataAccess()==null)continue;
            if(!seenObjects.add(object.objectType()))throw invalid();
            for(var access:object.dataAccess().domains()){
                var resources=access.resources().stream().map(r->new DataAccessDeclaration.Resource(r.resource(),r.parentResource(),r.nameZh(),r.nameEn(),r.fields().stream().map(f->new DataAccessDeclaration.Field(f.field(),f.nameZh(),f.nameEn(),FieldSecurity.Kind.valueOf(f.kind()),f.modes().stream().map(FieldSecurity.Mode::valueOf).collect(java.util.stream.Collectors.toSet()))).toList(),r.responseType())).toList();
                var operations=object.operations().stream().filter(op->access.operationScopes().containsKey(op.operationCode())).map(op->new DataAccessDeclaration.Operation(op.operationId(),op.permissionCode(),access.operationScopes().get(op.operationCode()).stream().map(DataScopeAuthorization.ScopeType::valueOf).collect(java.util.stream.Collectors.toSet()))).toList();
                var projection=new DataAccessDeclaration.Descriptor(object.objectType(),object.version(),PermissionCatalog.ScopeType.valueOf(access.scope()),resources,operations,access.dimensions());
                projections.add(projection);
            }
        }
        return new DataAccessCatalog(projections);
    }
    /** 目录版本令牌按对象及声明版本排序；修改安全语义必须提升声明版本并迁移已有策略。 */
    public String version(){return descriptors.stream().map(d->d.scope().name()+":"+d.objectType()+"@"+d.version()).sorted().collect(java.util.stream.Collectors.joining(";"));}
    /** 返回指定管理域的只读目录；不包含 SQL 或真实业务数据。 */
    public List<DataAccessDeclaration.Descriptor> list(PermissionCatalog.ScopeType scope){return descriptors.stream().filter(d->d.scope()==scope).toList();}
    /** 检查主资源与权限的精确绑定，不根据操作后缀借用其他资源。 */
    public boolean supports(String permission,String resource,DataScopeAuthorization.ScopeType type){return descriptors.stream().filter(d->d.resources().getFirst().resource().equals(resource)).flatMap(d->d.operations().stream()).anyMatch(op->op.permissionCode().equals(permission)&&op.scopes().contains(type));}
    /** 判断本次操作是否已登记；无行范围声明不能被当作 ALL。 */
    public boolean declared(String permission,String resource){return descriptors.stream().filter(d->d.resources().getFirst().resource().equals(resource)).flatMap(d->d.operations().stream()).anyMatch(op->op.permissionCode().equals(permission)&&!op.scopes().isEmpty());}
    /** 从同一权威声明投影字段目录，供保存校验和响应执行复用。 */
    public List<FieldSecurity.Descriptor> fields(PermissionCatalog.ScopeType scope){return list(scope).stream().flatMap(d->d.resources().stream()).flatMap(r->r.fields().stream().map(f->new FieldSecurity.Descriptor(r.resource(),f.field(),f.nameZh(),f.nameEn(),f.kind(),f.modes().stream().sorted().toList()))).toList();}
    private static boolean validSource(com.appnexus.contracts.businessobject.BusinessObjects.DataDimension dimension){
        if(dimension.source()==null||dimension.dictionary()==null)return false;
        return switch(dimension.source()){
            case "TEXT" -> dimension.dictionary().isEmpty();
            case "ACCOUNT","PERSON" -> "UUID".equals(dimension.valueType())&&dimension.dictionary().isEmpty();
            case "ORGANIZATION" -> "UUID".equals(dimension.valueType())&&dimension.dictionary().isEmpty();
            case "DICTIONARY" -> "TEXT".equals(dimension.valueType())&&dimension.dictionary().matches("[a-z][a-z0-9_]{0,63}");
            default -> false;
        };
    }
    private static boolean validParameters(com.appnexus.contracts.businessobject.BusinessObjects.DataDimension dimension){
        var allowed=switch(dimension.source()){
            case "ACCOUNT" -> "createdBy".equals(dimension.field())?Set.of("CURRENT_ACCOUNT"):Set.<String>of();
            case "PERSON" -> Set.of("CURRENT_PERSON");
            case "ORGANIZATION" -> Set.of("CURRENT_ORGANIZATION","CURRENT_ORGANIZATION_SUBTREE");
            default -> Set.<String>of();
        };
        return allowed.containsAll(dimension.parameters())&&(dimension.parameters().isEmpty()||dimension.operators().contains("EQ"))
            &&(!Set.of("ACCOUNT","PERSON").contains(dimension.source())||!dimension.parameters().isEmpty());
    }
    private static IllegalStateException invalid(){return new IllegalStateException("DATA_ACCESS_DECLARATION_INVALID");}
    /**
     * @param permission 统一目录操作权限码
     * @param resource 统一主资源键
     * @return 当前操作开放的条件维度；无匹配权限时返回空
     */
    public List<com.appnexus.contracts.businessobject.BusinessObjects.DataDimension> dimensions(String permission,String resource){
        return descriptors.stream().filter(d->d.resources().getFirst().resource().equals(resource))
            .flatMap(d->d.dimensions().stream().filter(dimension->d.operations().stream().anyMatch(op->
                op.permissionCode().equals(permission)&&op.scopes().contains(DataScopeAuthorization.ScopeType.RULES)
                    &&(dimension.operationCodes().isEmpty()||dimension.operationCodes().stream().anyMatch(code->
                        op.operationId().equals(d.objectType()+"."+code)))))).toList();
    }
    /** 校验条件仅使用统一声明中真实支持的维度和运算，禁止管理配置提供任意字段。 */
    public void validateRules(String permission,String resource,List<DataGrantRules.Rule> rules) {
        if(!supports(permission,resource,DataScopeAuthorization.ScopeType.RULES))throw new IllegalArgumentException("DATA_SCOPE_UNSUPPORTED");
        var dimensions=dimensions(permission,resource);
        var checked=DataGrantRules.checked(rules);
        // 创建前不存在记录 UUID，不能从已有记录选择推导新增权限。
        if(permission.endsWith(":create")&&checked.stream().anyMatch(rule->!rule.recordIds().isEmpty()))
            throw new IllegalArgumentException("DATA_GRANT_CREATE_RECORDS_UNSUPPORTED");
        // 未声明条件维度的组合授权只表达明确的记录集合，全部记录使用独立 ALL。
        if(dimensions.isEmpty()&&(checked.size()!=1||checked.stream().anyMatch(rule->
            rule.allRecords()||rule.recordIds().isEmpty()||rule.subjectType()!=DataGrantRules.SubjectType.ROLE_MEMBERS)))
            throw new IllegalArgumentException("DATA_GRANT_RECORDS_REQUIRED");
        for(var rule:checked)for(var condition:rule.conditions()) {
            var dimension=dimensions.stream().filter(d->d.field().equals(condition.field())).findFirst()
                .orElseThrow(()->new IllegalArgumentException("DATA_GRANT_FIELD_UNSUPPORTED"));
            if(!dimension.operators().contains(condition.operator().name()))throw new IllegalArgumentException("DATA_GRANT_OPERATOR_UNSUPPORTED");
            if(condition.parameter()!=null&&!dimension.parameters().contains(condition.parameter().name())
                ||condition.parameter()==null&&Set.of("ACCOUNT","PERSON").contains(dimension.source()))
                throw new IllegalArgumentException("DATA_GRANT_PARAMETER_UNSUPPORTED");
            if("UUID".equals(dimension.valueType()))for(String value:condition.values())
                if(!UUID.fromString(value).toString().equals(value))throw new IllegalArgumentException("DATA_GRANT_UUID_INVALID");
        }
    }
}
