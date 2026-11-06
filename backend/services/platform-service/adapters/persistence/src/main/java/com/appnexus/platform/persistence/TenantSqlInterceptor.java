package com.appnexus.platform.persistence;
import com.appnexus.platform.tenant.VerifiedTenantContext;
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
        var sql=handler.getBoundSql().getSql();
        String normalized=sql.trim().replaceAll("\s+"," ").toLowerCase(Locale.ROOT);
        if(declaration==null) {
            var platform = mapper.getAnnotation(PlatformScoped.class);
            if(platform != null) {
                var allowed = Set.of(platform.tables());
                if(allowed.isEmpty()) throw new IllegalStateException("Platform mapper table allowlist required");
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
