package com.appnexus.persistence.migration;

import java.sql.Connection;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransaction;
import org.apache.ibatis.transaction.TransactionFactory;

/** 历史Java迁移专用MyBatis会话；复用Flyway拥有的连接，禁止提交、回滚或关闭该连接。 */
public final class MigrationSession implements AutoCloseable {
    private final SqlSession session;
    /**
     * @param connection Flyway当前迁移连接，事务及生命周期仍由Flyway/迁移主体拥有
     * @param mapperTypes 固定历史Mapper类型，不接收SQL、外部类名或运行时HTTP输入
     */
    public MigrationSession(Connection connection,Class<?>... mapperTypes){
        var configuration=new Configuration();configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);configuration.setCacheEnabled(false);configuration.setCallSettersOnNulls(true);
        var transaction=new JdbcTransaction(connection){@Override public void commit(){}@Override public void rollback(){}@Override public void close(){}};
        TransactionFactory factory=new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(){@Override public org.apache.ibatis.transaction.Transaction newTransaction(Connection ignored){return transaction;}};
        configuration.setEnvironment(new Environment("historical-migration",factory,new javax.sql.DataSource(){
            public Connection getConnection(){return connection;}public Connection getConnection(String user,String password){throw new UnsupportedOperationException();}public java.io.PrintWriter getLogWriter(){return null;}public void setLogWriter(java.io.PrintWriter writer){}public void setLoginTimeout(int seconds){}public int getLoginTimeout(){return 0;}public java.util.logging.Logger getParentLogger(){return java.util.logging.Logger.getLogger("migration");}public <T>T unwrap(Class<T> type){throw new UnsupportedOperationException();}public boolean isWrapperFor(Class<?> type){return false;}
        }));
        for(var type:mapperTypes)configuration.addMapper(type);
        session=new SqlSessionFactoryBuilder().build(configuration).openSession(connection);
    }
    /** @param type 构造时登记的固定Mapper @return 在原迁移连接执行的Mapper，不使用生产HTTP拦截器 */
    public <T>T mapper(Class<T> type){return session.getMapper(type);}
    /** 只释放MyBatis会话资源；不提交、回滚或关闭Flyway连接。 */
    @Override public void close(){session.close();}
}
