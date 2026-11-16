package com.appnexus.platform;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/** 真实 MySQL 校验全库四列覆盖、JDBC 批量/冲突写入、主体切换及跨租户失败不污染审计。 */
@SpringBootTest
@ActiveProfiles("local")
class GlobalRecordAuditTest extends DatabaseTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;

    /** 每个已接入 HTTP 投影的键及租户编码都必须与真实表结构一致，防止文本键误用 UUID_TO_BIN。 */
    @Test void projectionDeclarationsMatchActualColumnTypes(){
        for(var handler:mappings.getHandlerMethods().values()){
            var source=handler.getMethodAnnotation(com.appnexus.contracts.operation.RecordAuditSource.class);
            if(source==null)continue;
            String type=jdbc.queryForObject("SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?",String.class,source.table(),source.key());
            assertThat(type.equalsIgnoreCase("binary")).as(handler.toString()).isEqualTo(source.binaryKey());
            if(source.tenant()){
                type=jdbc.queryForObject("SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name='tenant_id'",String.class,source.table());
                assertThat(type.equalsIgnoreCase("binary")).as(handler.toString()).isEqualTo(source.binaryTenant());
            }
        }
    }

    @Test void ownedTablesHaveAllFourColumns(){
        var missing=jdbc.queryForList("""
            SELECT t.table_name FROM information_schema.tables t
            LEFT JOIN information_schema.columns c ON c.table_schema=t.table_schema AND c.table_name=t.table_name
              AND c.column_name IN ('created_by','created_at','updated_by','updated_at')
            WHERE t.table_schema=DATABASE() AND t.table_type='BASE TABLE'
              AND LOWER(t.table_name) NOT REGEXP '^(act_|flw_|oauth2_|flyway_)'
            GROUP BY t.table_name HAVING COUNT(c.column_name)<>4
            """,String.class);
        assertThat(missing).isEmpty();
    }

    @Test void createUpdateBatchAndSystemExecutionUseActualActor(){
        String tenant=UUID.randomUUID().toString(),id=UUID.randomUUID().toString();
        UUID creator=UUID.randomUUID(),updater=UUID.randomUUID();
        try{
            authenticate(creator);
            jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'审计独立测试')",tenant);
            jdbc.update("INSERT INTO md_warehouse(id,tenant_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'AUDIT','仓库')",id,tenant);
            var initial=read(tenant,id);
            assertThat(initial.get("creator").toString()).isEqualToIgnoringCase(creator.toString());
            assertThat(initial.get("updater").toString()).isEqualToIgnoringCase(creator.toString());
            assertThat(initial.get("created_at")).isNotNull();
            authenticate(updater);
            assertThat(jdbc.update("UPDATE md_warehouse SET name='修改',version=version+1 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=0",UUID.randomUUID().toString(),id)).isZero();
            assertThat(read(tenant,id)).isEqualTo(initial);
            jdbc.update("UPDATE md_warehouse SET name=?,version=version+1 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=0","修改",tenant,id);
            var changed=read(tenant,id);
            assertThat(changed.get("creator")).isEqualTo(initial.get("creator"));
            assertThat(changed.get("created_at")).isEqualTo(initial.get("created_at"));
            assertThat(changed.get("updater").toString()).isEqualToIgnoringCase(updater.toString());
            jdbc.update("INSERT INTO md_warehouse(id,tenant_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'AUDIT','冲突') ON DUPLICATE KEY UPDATE name=VALUES(name)",id,tenant);
            assertThat(read(tenant,id).get("creator")).isEqualTo(initial.get("creator"));
            jdbc.batchUpdate("INSERT INTO md_warehouse(id,tenant_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?)",List.of(new Object[]{UUID.randomUUID().toString(),tenant,"B1","批量一"},new Object[]{UUID.randomUUID().toString(),tenant,"B2","批量二"}));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_warehouse WHERE tenant_id=UUID_TO_BIN(?) AND updated_by=UUID_TO_BIN(?)",Integer.class,tenant,updater.toString())).isEqualTo(3);
            jdbc.update("INSERT INTO md_warehouse(id,tenant_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'M1','多行一'),(UUID_TO_BIN(?),UUID_TO_BIN(?),'M2','多行二')",UUID.randomUUID().toString(),tenant,UUID.randomUUID().toString(),tenant);
            jdbc.update("INSERT INTO md_warehouse(id,tenant_id,code,name) SELECT UUID_TO_BIN(?),tenant_id,'COPY',name FROM md_warehouse WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",UUID.randomUUID().toString(),tenant,id);
            jdbc.update("UPDATE md_warehouse w JOIN platform_tenant t ON t.id=w.tenant_id SET w.name='联表修改' WHERE w.tenant_id=UUID_TO_BIN(?) AND w.id=UUID_TO_BIN(?)",tenant,id);
            assertThat(read(tenant,id).get("updater").toString()).isEqualToIgnoringCase(updater.toString());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_warehouse WHERE tenant_id=UUID_TO_BIN(?) AND created_by=UUID_TO_BIN(?)",Integer.class,tenant,updater.toString())).isEqualTo(5);
            SecurityContextHolder.clearContext();
            jdbc.update("UPDATE md_warehouse SET name='后台任务' WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant,id);
            assertThat(read(tenant,id).get("updater")).isNull();
            assertThat(read(tenant,id).get("creator")).isEqualTo(initial.get("creator"));
        }finally{SecurityContextHolder.clearContext();}
    }

    /** 业务异常回滚时四列与正文一起回滚，不产生独立提交的审计修改。 */
    @Test void auditRollsBackWithBusinessChange(){
        String tenant=UUID.randomUUID().toString();
        UUID creator=UUID.randomUUID();
        try{
            authenticate(creator);
            jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'回滚前')",tenant);
            var original=jdbc.queryForMap("SELECT name,created_by,created_at,updated_by,updated_at FROM platform_tenant WHERE id=UUID_TO_BIN(?)",tenant);
            authenticate(UUID.randomUUID());
            var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.support.JdbcTransactionManager(jdbc.getDataSource()));
            assertThatThrownBy(()->tx.executeWithoutResult(status->{jdbc.update("UPDATE platform_tenant SET name='应回滚' WHERE id=UUID_TO_BIN(?)",tenant);throw new IllegalStateException("ROLLBACK_TEST");})).hasMessage("ROLLBACK_TEST");
            var restored=jdbc.queryForMap("SELECT name,created_by,created_at,updated_by,updated_at FROM platform_tenant WHERE id=UUID_TO_BIN(?)",tenant);
            assertThat(restored).usingRecursiveComparison().isEqualTo(original);
        }finally{SecurityContextHolder.clearContext();}
    }

    /** 投影只允许原响应里的对象，且每次重新绑定可信租户；同 ID 跨租户不能得到审计事实。 */
    @Test void projectionUsesVerifiedTenantAndDoesNotExposeOtherRows() throws Exception {
        String tenant=UUID.randomUUID().toString(),id=UUID.randomUUID().toString();
        UUID actor=UUID.randomUUID();
        try{
            authenticate(actor);
            jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'审计读取测试')",tenant);
            jdbc.update("INSERT INTO md_warehouse(id,tenant_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'READ','读取')",id,tenant);
            var request=new org.springframework.mock.web.MockHttpServletRequest("GET","/test-audit");
            request.setAttribute(com.appnexus.platform.tenant.VerifiedTenantContext.class.getName(),new com.appnexus.platform.tenant.VerifiedTenantContext(actor,UUID.fromString(tenant),UUID.randomUUID(),1));
            var advice=new RecordAuditResponseAdvice(jdbc,request);
            var method=new org.springframework.core.MethodParameter(getClass().getDeclaredMethod("auditedRows"),-1);
            var response=new org.springframework.http.server.ServletServerHttpResponse(new org.springframework.mock.web.MockHttpServletResponse());
            Object projected=advice.beforeBodyWrite(List.of(Map.of("id",id)),method,org.springframework.http.MediaType.APPLICATION_JSON,null,null,response);
            var audit=(Map<?,?>)((Map<?,?>)((List<?>)projected).getFirst()).get("audit");
            assertThat(audit.get("createdBy").toString()).isEqualToIgnoringCase(actor.toString());
            request.setAttribute(com.appnexus.platform.tenant.VerifiedTenantContext.class.getName(),new com.appnexus.platform.tenant.VerifiedTenantContext(actor,UUID.randomUUID(),UUID.randomUUID(),1));
            projected=advice.beforeBodyWrite(List.of(Map.of("id",id)),method,org.springframework.http.MediaType.APPLICATION_JSON,null,null,response);
            assertThat(((Map<?,?>)((List<?>)projected).getFirst()).containsKey("audit")).isFalse();
        }finally{SecurityContextHolder.clearContext();}
    }
    /** 仅测试方法元数据，模拟模块构建内声明；不暴露 HTTP 测试入口。 */
    @com.appnexus.contracts.operation.RecordAuditSource(table="md_warehouse")
    private void auditedRows(){}

    /** 测试认证账号仅用于审计主体，业务授权仍由实际服务入口独立复核。 */
    private void authenticate(UUID actor){SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor.toString(),null,List.of()));}
    private Map<String,Object> read(String tenant,String id){return jdbc.queryForMap("SELECT BIN_TO_UUID(created_by) creator,created_at,BIN_TO_UUID(updated_by) updater,updated_at FROM md_warehouse WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant,id);}
}
