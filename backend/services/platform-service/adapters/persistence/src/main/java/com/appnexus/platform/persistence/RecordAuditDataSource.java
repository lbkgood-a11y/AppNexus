package com.appnexus.platform.persistence;

import com.appnexus.platform.audit.RecordAuditActor;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * JDBC 公共审计装配，同时覆盖 JdbcTemplate、MyBatis 及后台任务；复用原连接及事务。
 * 仅四列已齐备的自有表启用，Flyway 和第三方引擎原始表不改写；不使用数据库触发器或连接会话变量。
 */
@Component
public final class RecordAuditDataSource implements BeanPostProcessor {
    private final ObjectProvider<RecordAuditActor> actors;
    private volatile boolean active;
    private volatile Set<String> auditedTables;
    /** 启动迁移结束后才启用，历史迁移不得被运行时审计补写，启动初始化任务仍受审计。 */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationStartedEvent.class)
    public void activate(){active=true;}
    /** @param actors 延迟读取可信主体，避免在数据源初始化时提前创建安全组件 */
    public RecordAuditDataSource(ObjectProvider<RecordAuditActor> actors){this.actors=actors;}

    @Override public Object postProcessAfterInitialization(Object bean,String name){
        if(!(bean instanceof DataSource source))return bean;
        return new DelegatingDataSource(source){
            @Override public Connection getConnection() throws SQLException{return wrap(super.getConnection());}
            @Override public Connection getConnection(String user,String password) throws SQLException{return wrap(super.getConnection(user,password));}
        };
    }

    /** 启动迁移结束后首次借用连接读取列元数据；仅缓存固定结构，不缓存或跨线程传递主体。 */
    private Connection wrap(Connection connection) throws SQLException {
        if(!active)return connection;
        Set<String> known=auditedTables;
        if(known==null){
        Set<String> tables=new HashSet<>();
        Map<String,Set<String>> columns=new HashMap<>();
        try(var result=connection.getMetaData().getColumns(connection.getCatalog(),null,"%","%")){
            while(result.next()){
                String table=result.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                if(table.startsWith("act_")||table.startsWith("flw_")||table.startsWith("oauth2_")||table.startsWith("flyway_"))continue;
                columns.computeIfAbsent(table,k->new HashSet<>()).add(result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        columns.forEach((table,fields)->{if(fields.containsAll(List.of("created_by","created_at","updated_by","updated_at")))tables.add(table);});
        auditedTables=known=Set.copyOf(tables);
        }
        final Set<String> tables=known;
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
            if((method.getName().equals("prepareStatement")||method.getName().equals("prepareCall"))&&args!=null&&args[0] instanceof String sql){
                var actor=actors.getIfAvailable();args[0]=RecordAuditSql.rewrite(sql,tables,actor==null?null:actor.accountId());
            }
            Object result=invoke(connection,method,args);
            if(method.getName().equals("createStatement")&&result instanceof Statement statement)
                return Proxy.newProxyInstance(Statement.class.getClassLoader(),new Class<?>[]{Statement.class},(p,m,a)->{
                    if((m.getName().startsWith("execute")||m.getName().equals("addBatch"))&&a!=null&&a.length>0&&a[0] instanceof String sql){
                        var actor=actors.getIfAvailable();a[0]=RecordAuditSql.rewrite(sql,tables,actor==null?null:actor.accountId());
                    }
                    return invoke(statement,m,a);
                });
            return result;
        });
    }

    /** 保留原 JDBC 异常类型，让事务回滚和现有唯一键/冲突映射继续生效。 */
    private static Object invoke(Object target,Method method,Object[] args) throws Throwable {
        try{return method.invoke(target,args);}catch(InvocationTargetException error){throw error.getCause();}
    }
}
