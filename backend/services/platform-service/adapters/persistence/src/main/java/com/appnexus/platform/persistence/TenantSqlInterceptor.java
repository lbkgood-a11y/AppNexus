package com.appnexus.platform.persistence;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.persistence.mybatis.*;
import java.sql.Connection;
import java.util.*;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;

/**
 * 单表 SQL 执行门禁：仅允许租户 Mapper 中显式声明的固定语句形状。
 * 不尝试用正则改写任意 SQL；联表、子查询、批处理、动态 SQL 及租户列更新默认拒绝。
 * 当前请求属性由安全过滤器建立；后台任务尚无受控上下文入口，默认拒绝。
 */
@Component
@Intercepts({
 @Signature(type=StatementHandler.class,method="prepare",args={Connection.class,Integer.class}),
 @Signature(type=org.apache.ibatis.executor.Executor.class,method="update",args={org.apache.ibatis.mapping.MappedStatement.class,Object.class}),
 @Signature(type=org.apache.ibatis.executor.Executor.class,method="query",args={org.apache.ibatis.mapping.MappedStatement.class,Object.class,org.apache.ibatis.session.RowBounds.class,org.apache.ibatis.session.ResultHandler.class})
})
public final class TenantSqlInterceptor implements Interceptor {
    private final com.appnexus.platform.authorization.ScopedWriteGate scopedWrites;
    private final org.springframework.beans.factory.ObjectProvider<com.appnexus.platform.authorization.AccessAuthorization> authorization;
    /** @param scopedWrites 与当前事务绑定的标准记录许可
     * @param authorization 平台查询权限执行器，延迟解析以避免持久化装配循环 */
    public TenantSqlInterceptor(com.appnexus.platform.authorization.ScopedWriteGate scopedWrites,
        org.springframework.beans.factory.ObjectProvider<com.appnexus.platform.authorization.AccessAuthorization> authorization){
        this.scopedWrites=scopedWrites;
        this.authorization=authorization;
    }
    private void verifyScope(Class<?> mapper,String id,BoundSql sql,boolean select){
        String name=id.substring(id.lastIndexOf('.')+1);
        for (var method : mapper.getMethods()) {
            if (!method.getName().equals(name)) continue;
            for (var guard : method.getAnnotationsByType(GuardedScope.class)) {
                verifyGuard(guard,sql,select);
            }
        }
    }

    private void verifyGuard(GuardedScope guard,BoundSql sql,boolean select){
        var parameters=SystemMetaObject.forObject(sql.getParameterObject());
        if(!guard.when().isEmpty()&&!guard.whenValue().equals(String.valueOf(parameters.getValue(guard.when()))))return;
        if(!guard.platformActor().isEmpty()){
            String path=guard.platformActor();
            if(!select||!guard.tenant().isEmpty()||guard.platformPermission().isEmpty()
                ||sql.getParameterMappings().stream().noneMatch(param->param.getProperty().equals(path)))
                throw new SecurityException("PLATFORM_SQL_PERMISSION_REQUIRED");
            var actor=scopeTenant(parameters.getValue(path));
            if(!authorization.getObject().hasPermission(
                com.appnexus.platform.authorization.AccessAuthorization.Subject.platform(actor),guard.platformPermission()))
                throw new SecurityException("PLATFORM_SQL_PERMISSION_REQUIRED");
            return;
        }
        String binding=guard.predicate();
        boolean readFallback=!guard.readFallback().isEmpty();
        if(readFallback){
            if(!select||!binding.isEmpty()||guard.recordId().isEmpty()||!guard.target().isEmpty()||guard.parentKey().length>0
                ||!guard.serviceOperation().isEmpty()||!guard.catalogPublication().isEmpty()||guard.operationPermissions().length>0)
                throw new SecurityException("SCOPED_SQL_PLAN_REQUIRED");
            if(parameters.getValue(guard.readFallback())!=null)binding=guard.readFallback();
        }
        if(!binding.isEmpty()){
            if(!(parameters.getValue(binding) instanceof com.appnexus.platform.authorization.RowAccessControl.Predicate predicate)
                ||predicate.plan()==null||!guard.resource().equals(predicate.plan().resource())
                ||!sameScopeTenant(parameters.getValue(guard.tenant()),predicate.plan().subject().tenantId())
                ||!sql.getSql().contains(ScopedSql.fragment(predicate.sql())))throw new SecurityException("SCOPED_SQL_PLAN_REQUIRED");
            var subject=predicate.plan().subject();
            if(readFallback&&!predicate.plan().permissionCode().equals(guard.resource()+":read"))
                throw new SecurityException("SCOPED_SQL_PLAN_REQUIRED");
            if(!readFallback&&guard.actions().length>0&&Arrays.stream(guard.actions()).noneMatch(action->
                predicate.plan().permissionCode().equals(guard.resource()+":"+action)))throw new SecurityException("SCOPED_SQL_PLAN_REQUIRED");
            if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request
                &&request.getRequest().getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context
                &&(!context.accountId().equals(subject.accountId())||!context.memberId().equals(subject.memberId())
                ||!context.tenantId().equals(subject.tenantId())))throw new SecurityException("SCOPED_SQL_SUBJECT_MISMATCH");
            for(int i=0;i<predicate.parameters().size();i++){
                String expected=binding+".parameters["+i+"]";
                if(sql.getParameterMappings().stream().noneMatch(parameter->parameter.getProperty().equals(expected)))
                    throw new SecurityException("SCOPED_SQL_BINDING_REQUIRED");
            }
        }else{
            var tenant=scopeTenant(parameters.getValue(guard.tenant()));
            if(!guard.serviceOperation().isEmpty()){
                if(guard.serviceKeys().length==0||guard.operationPermissions().length>0
                    ||!guard.catalogPublication().isEmpty()||!guard.recordId().isEmpty()
                    ||!guard.target().isEmpty()||guard.parentKey().length>0
                    ||sql.getParameterMappings().stream().noneMatch(param->param.getProperty().equals(guard.tenant())))
                    throw new SecurityException("SCOPED_WRITE_PLAN_REQUIRED");
                var keys=new ArrayList<String>();
                for(String path:guard.serviceKeys()){
                    if(!(parameters.getValue(path) instanceof String key)
                        ||sql.getParameterMappings().stream().noneMatch(param->param.getProperty().equals(path)))
                        throw new SecurityException("SCOPED_WRITE_PLAN_REQUIRED");
                    keys.add(key);
                }
                scopedWrites.requireServiceOperation(tenant,guard.serviceOperation(),keys);
                return;
            }
            if(!guard.catalogPublication().isEmpty()){
                if(guard.operationPermissions().length>0||!guard.recordId().isEmpty()
                    ||!guard.target().isEmpty()||guard.parentKey().length>0)
                    throw new SecurityException("SCOPED_WRITE_PLAN_REQUIRED");
                scopedWrites.requireCatalogPublication(tenant,guard.catalogPublication());
                return;
            }
            if(guard.operationPermissions().length>0){
                if(!guard.recordId().isEmpty()||!guard.target().isEmpty()||guard.parentKey().length>0)
                    throw new SecurityException("SCOPED_WRITE_PLAN_REQUIRED");
                scopedWrites.requireOperation(tenant,guard.operationPermissions());
                return;
            }
            if(readFallback&&sql.getParameterMappings().stream().noneMatch(p->p.getProperty().equals(guard.recordId())))
                throw new SecurityException("SCOPED_WRITE_PLAN_REQUIRED");
            var record=UUID.fromString(String.valueOf(parameters.getValue(guard.recordId())));
            if(guard.parentKey().length>0){
                if(!guard.target().isEmpty())throw new SecurityException("SCOPED_WRITE_PLAN_REQUIRED");
                var parentKeys=new ArrayList<String>();
                for(String path:guard.parentKey()){
                    if(!(parameters.getValue(path) instanceof String key))
                        throw new SecurityException("SCOPED_WRITE_PLAN_REQUIRED");
                    parentKeys.add(key);
                }
                scopedWrites.requireParentKey(tenant,record,guard.resource(),guard.actions(),parentKeys);
                return;
            }
            boolean bindTarget=!guard.target().isEmpty();
            var target=bindTarget?parameters.getValue(guard.target()):null;
            scopedWrites.require(tenant,record,guard.resource(),guard.actions(),
                target==null?null:UUID.fromString(target.toString()),bindTarget);
        }
    }

    /** 组织聚合 SQL 使用二进制租户 UUID；主档 Mapper 使用规范文本 UUID。 */
    private UUID scopeTenant(Object value){
        if(value instanceof byte[] bytes && bytes.length==16){
            var buffer=java.nio.ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(),buffer.getLong());
        }
        return UUID.fromString(String.valueOf(value));
    }

    private boolean sameScopeTenant(Object parameter,UUID expected){
        if(expected==null)return false;
        if(parameter instanceof byte[] bytes)return Arrays.equals(bytes,java.nio.ByteBuffer.allocate(16)
            .putLong(expected.getMostSignificantBits()).putLong(expected.getLeastSignificantBits()).array());
        return expected.toString().equals(String.valueOf(parameter));
    }

    @Override public Object intercept(Invocation invocation) throws Throwable {
        if (invocation.getTarget() instanceof org.apache.ibatis.executor.Executor) {
            Object executor=invocation.getTarget();
            var executorMeta=SystemMetaObject.forObject(executor);
            if(executor instanceof org.apache.ibatis.executor.CachingExecutor) executor=executorMeta.getValue("delegate");
            if(!(executor instanceof org.apache.ibatis.executor.SimpleExecutor))
                throw new IllegalStateException("Only SIMPLE executor is supported");
            if(invocation.getMethod().getName().equals("update") && invocation.getArgs().length > 0 &&
                invocation.getArgs()[0] instanceof org.apache.ibatis.mapping.MappedStatement mapped &&
                mapped.getSqlCommandType() == org.apache.ibatis.mapping.SqlCommandType.FLUSH)
                throw new IllegalStateException("Explicit batch flush is not supported");
            return invocation.proceed();
        }
        var handler=(StatementHandler)invocation.getTarget();
        var meta=SystemMetaObject.forObject(handler);
        var statement=(org.apache.ibatis.mapping.MappedStatement)meta.getValue("delegate.mappedStatement");
        String namespace=statement.getId().substring(0,statement.getId().lastIndexOf('.'));
        Class<?> mapper=Class.forName(namespace);
        var declaration=mapper.getAnnotation(TenantScoped.class);
        var aggregate=mapper.getAnnotation(TenantAggregateScoped.class);
        verifyScope(mapper,statement.getId(),handler.getBoundSql(),
            statement.getSqlCommandType()==org.apache.ibatis.mapping.SqlCommandType.SELECT);
        var sql=handler.getBoundSql().getSql();
        String normalized=sql.trim().replaceAll("\s+"," ").toLowerCase(Locale.ROOT);
        if(declaration==null && aggregate!=null) {
            String statementName=statement.getId().substring(statement.getId().lastIndexOf('.')+1);
            if(!Set.of(aggregate.statements()).contains(statementName))
                throw new IllegalStateException("Tenant aggregate statement outside reviewed allowlist");
            var allowed=Arrays.stream(aggregate.tables()).map(value->value.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
            if(allowed.isEmpty() || allowed.stream().anyMatch(value->!value.matches("[a-z][a-z0-9_]*")))
                throw new IllegalStateException("Invalid tenant aggregate table allowlist");
            Set<String> referenced;
            try { referenced=net.sf.jsqlparser.util.TablesNamesFinder.findTables(normalized).stream()
                .map(value->value.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet()); }
            catch(net.sf.jsqlparser.JSQLParserException unsupported) {
                throw new IllegalStateException("Tenant aggregate SQL cannot be safely parsed",unsupported);
            }
            if(referenced.isEmpty() || !allowed.containsAll(referenced))
                throw new IllegalStateException("Tenant aggregate SQL table outside allowlist");
            var attributes=RequestContextHolder.getRequestAttributes();
            if(!(attributes instanceof ServletRequestAttributes request) ||
                !(request.getRequest().getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context))
                throw new IllegalStateException("Verified tenant context required");
            BoundSql boundSql=handler.getBoundSql();
            if(boundSql.getParameterMappings().stream().noneMatch(mapping->mapping.getProperty().equals("__verifiedTenant")))
                throw new IllegalStateException("Tenant aggregate binding missing");
            byte[] trustedTenant=java.nio.ByteBuffer.allocate(16)
                .putLong(context.tenantId().getMostSignificantBits()).putLong(context.tenantId().getLeastSignificantBits()).array();
            // 已迁移业务端口传递的是可信租户；不允许以A的业务权限执行B上下文重新绑定后的查询。
            if(aggregate.requireTenantMatch()){
                Object supplied=boundSql.getParameterObject() instanceof Map<?,?> parameters?parameters.get("__verifiedTenant"):null;
                if(!(supplied instanceof byte[] bytes)||!Arrays.equals(bytes,trustedTenant))
                    throw new SecurityException("Mapper tenant does not match verified context");
            }
            boundSql.setAdditionalParameter("__verifiedTenant",trustedTenant);
            return invocation.proceed();
        }
        if(declaration==null) {
            var platform = mapper.getAnnotation(PlatformScoped.class);
            if(platform != null) {
                var allowed = Set.of(platform.tables());
                if(allowed.isEmpty()) throw new IllegalStateException("Platform mapper table allowlist required");
                // 仅编码 Mapper 的固定数据库时钟可不访问表；不允许任意常量查询、会话变量或客户端 SQL。
                if(statement.getId().equals("com.appnexus.platform.persistence.businessobject.coding.CodingMapper.now")
                    &&statement.getSqlCommandType()==org.apache.ibatis.mapping.SqlCommandType.SELECT
                    &&normalized.equals("select current_timestamp(6)")&&handler.getBoundSql().getParameterMappings().isEmpty())
                    return invocation.proceed();
                Set<String> referenced;
                try { referenced = net.sf.jsqlparser.util.TablesNamesFinder.findTables(normalized).stream()
                    .map(name -> name.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet()); }
                catch (net.sf.jsqlparser.JSQLParserException unsupported) {
                    throw new IllegalStateException("Platform SQL cannot be safely parsed", unsupported);
                }
                if(referenced.isEmpty() || !allowed.containsAll(referenced))
                    throw new IllegalStateException("Platform SQL table outside allowlist");
                return invocation.proceed();
            }
            throw new IllegalStateException("Mapper scope declaration required");
        }
        var attributes=RequestContextHolder.getRequestAttributes();
        if(!(attributes instanceof ServletRequestAttributes request) ||
            !(request.getRequest().getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context))
            throw new IllegalStateException("Verified tenant context required");
        String table=declaration.table();
        if(!table.matches("[a-z][a-z0-9_]*")) throw new IllegalStateException("Invalid tenant table declaration");
        BoundSql boundSql=handler.getBoundSql();
        // 固定形状与占位符数量完全一致才执行，OR/UNION/注释/联表等不能夹带进入。
        var allowed=Set.of(
            "select id, name from "+table+" where tenant_id = ? order by name",
            "select bin_to_uuid(id) as id, name from "+table+" where tenant_id = ? order by name",
            "select bin_to_uuid(id) as id, name from "+table+" where tenant_id = ? and id = uuid_to_bin(?)",
            "select id, value from "+table+" where tenant_id = ? and id = ?",
            "insert into "+table+" (tenant_id, id, name) values (?, uuid_to_bin(?), ?)",
            "insert into "+table+" (tenant_id, id, value) values (?, ?, ?)",
            "update "+table+" set name = ? where tenant_id = ? and id = uuid_to_bin(?)",
            "update "+table+" set value = ? where tenant_id = ? and id = ?",
            "delete from "+table+" where tenant_id = ? and id = uuid_to_bin(?)",
            "delete from "+table+" where tenant_id = ? and id = ?");
        if(!allowed.contains(normalized)) throw new IllegalStateException("Unsupported tenant SQL shape");
        var mappings=boundSql.getParameterMappings();
        int tenantIndex=normalized.startsWith("update")?1:0;
        int expected=normalized.startsWith("select") && normalized.endsWith("order by name") ? 1 :
            (normalized.startsWith("insert")||normalized.startsWith("update")?3:2);
        if(mappings.size()!=expected || !mappings.get(tenantIndex).getProperty().equals("__verifiedTenant"))
            throw new IllegalStateException("Tenant binding missing");
        // additionalParameter 优先于客户端参数对象，不能从请求体覆盖租户条件。
        boundSql.setAdditionalParameter("__verifiedTenant",java.nio.ByteBuffer.allocate(16)
            .putLong(context.tenantId().getMostSignificantBits()).putLong(context.tenantId().getLeastSignificantBits()).array());
        return invocation.proceed();
    }
}
