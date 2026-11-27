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
    @Autowired com.appnexus.masterdata.adapter.persistence.MasterStatusHistoryMapper statusHistory;
    @Autowired com.appnexus.platform.authorization.AccessAuthorization authorization;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    @Autowired com.appnexus.masterdata.organization.PersonnelDirectory directory;
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

    /** 固定接口禁止无归属的历史入口；未来新增对象须同步登记查询入口。 */
    @Test void masterStatusHistoryRoutesHaveOwningOperations() {
        var expected = java.util.Arrays.stream(com.appnexus.masterdata.api.masterdata.StatusHistory.Subject.values())
            .map(subject -> switch (subject) {
                case ATTRIBUTE_GROUP -> "ProductAttribute.readGroupStatusHistory";
                case CONVERSION -> "ProductUnit.readConversionStatusHistory";
                default -> subject.code + ".readStatusHistory";
            }).toList();
        var actual = mappings.getHandlerMethods().values().stream()
            .filter(handler -> handler.getBeanType().getSimpleName().equals("MasterStatusHistoryController"))
            .map(handler -> handler.getMethodAnnotation(com.appnexus.contracts.operation.OperationBinding.class))
            .map(binding -> binding.id()).toList();
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);

        var historyPaths = mappings.getHandlerMethods().entrySet().stream()
            .filter(entry -> entry.getValue().getBeanType().getSimpleName().equals("MasterStatusHistoryController"))
            .flatMap(entry -> entry.getKey().getPatternValues().stream()).toList();
        var independentStatusPaths = mappings.getHandlerMethods().keySet().stream()
            .flatMap(mapping -> mapping.getPatternValues().stream())
            .filter(path -> path.startsWith("/api/master-data/") && path.endsWith("/{id}/status"))
            .filter(path -> !path.equals("/api/master-data/product/{id}/status"))
            .filter(path -> !path.equals("/api/master-data/product/{product}/structure-records/{kind}/{id}/status"))
            .map(path -> path + "-history").toList();
        assertThat(historyPaths).containsAll(independentStatusPaths);
    }

    /** 普通保存、版本冲突和事务回滚不会留下启停事实，成功状态事件保留可信操作者。 */
    @Test void statusHistoryTracksOnlyCommittedTransitions() {
        var id = UUID.randomUUID().toString();
        var tenant = UUID.randomUUID().toString();
        var actor = UUID.randomUUID().toString();
        var tx = new org.springframework.transaction.support.TransactionTemplate(manager);
        jdbc.update("""
            INSERT INTO md_country_region(id,tenant_id,code,name)
            VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,'历史验证')
            """, id, tenant, "H-" + id);
        String update = """
            UPDATE md_country_region SET enabled=?,version=version+1,updated_by=UUID_TO_BIN(?)
            WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=?
            """;
        String history = """
            SELECT CONCAT(before_enabled,after_enabled,':',to_version,':',BIN_TO_UUID(created_by))
            FROM md_master_status_change WHERE tenant_id=UUID_TO_BIN(?) AND object_id=UUID_TO_BIN(?)
            """;
        assertThat(jdbc.update(update, true, actor, tenant, id, 0)).isOne();
        assertThat(jdbc.update(update, false, actor, tenant, id, 0)).isZero();
        tx.executeWithoutResult(status -> {
            assertThat(jdbc.update(update, false, actor, tenant, id, 1)).isOne();
            assertThat(statusHistory.append(tenant, "CountryRegion", id, 1, false, actor)).isOne();
            assertThat(jdbc.queryForList(history, String.class, tenant, id)).containsExactly("10:2:" + actor);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForList(history, String.class, tenant, id)).isEmpty();
        tx.executeWithoutResult(status -> {
            assertThat(jdbc.update(update, false, actor, tenant, id, 1)).isOne();
            assertThat(statusHistory.append(tenant, "CountryRegion", id, 1, false, actor)).isOne();
        });
        assertThat(jdbc.queryForList(history, String.class, tenant, id)).containsExactly("10:2:" + actor);
        assertThat(jdbc.queryForList(history, String.class, UUID.randomUUID().toString(), id)).isEmpty();
    }

    /** 检查真实迁移后的自有基础表。复合业务键由独立唯一约束保留。 */
    @Test void ownedTablesHaveSingleIdPrimaryKey() {
        var invalidTables = jdbc.queryForList("""
            SELECT t.table_name
            FROM information_schema.tables t
            LEFT JOIN information_schema.key_column_usage k
              ON k.table_schema=t.table_schema AND k.table_name=t.table_name
              AND k.constraint_name='PRIMARY'
            WHERE t.table_schema=DATABASE() AND t.table_type='BASE TABLE'
              AND LOWER(t.table_name) NOT REGEXP '^(act_|flw_|oauth2_|flyway_)'
            GROUP BY t.table_name
            HAVING COUNT(k.column_name) <> 1
                OR SUM(CASE WHEN BINARY k.column_name='id' THEN 1 ELSE 0 END) <> 1
            ORDER BY t.table_name
            """, String.class);
        assertThat(invalidTables).as("未使用单列 id 主键的自有表").isEmpty();
    }

    /** 租户子记录必须与父角色归属一致，平台角色的子记录保持空归属。 */
    @Test void roleFieldRestrictionPreservesParentScopeAndBusinessKey() {
        var tenant = UUID.randomUUID().toString();
        var otherTenant = UUID.randomUUID().toString();
        var role = UUID.randomUUID().toString();
        var platformRole = UUID.randomUUID().toString();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(manager);
        transaction.executeWithoutResult(status -> {
            status.setRollbackOnly();
            jdbc.update("""
                INSERT INTO access_role(id,scope_type,tenant_id,role_type,code,name)
                VALUES(UUID_TO_BIN(?),'TENANT',UUID_TO_BIN(?),'CUSTOM','key_review','主键复核'),
                      (UUID_TO_BIN(?),'PLATFORM',NULL,'CUSTOM','key_review','主键复核')
                """, role, tenant, platformRole);
            var insert = """
                INSERT INTO access_role_field_security(role_id,tenant_id,resource_type,field_code,display_mode)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'key_review','field','HIDDEN')
                """;
            assertThat(jdbc.update(insert, role, tenant)).isEqualTo(1);
            assertThat(jdbc.update(insert, platformRole, null)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                SELECT COUNT(DISTINCT id) FROM access_role_field_security
                WHERE role_id IN (UUID_TO_BIN(?),UUID_TO_BIN(?))
                """, Integer.class, role, platformRole)).isEqualTo(2);
            assertThatThrownBy(() -> jdbc.update(insert, role, tenant))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
            assertThatThrownBy(() -> jdbc.update("""
                UPDATE access_role_field_security SET tenant_id=UUID_TO_BIN(?) WHERE role_id=UUID_TO_BIN(?)
                """, otherTenant, role)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("""
                UPDATE access_role_field_security SET tenant_id=NULL WHERE role_id=UUID_TO_BIN(?)
                """, role)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        });
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
            jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'审计账号名','unused')",actor.toString(),"audit-"+actor);
            var request=new org.springframework.mock.web.MockHttpServletRequest("GET","/test-audit");
            request.setAttribute(com.appnexus.platform.tenant.VerifiedTenantContext.class.getName(),new com.appnexus.platform.tenant.VerifiedTenantContext(actor,UUID.fromString(tenant),UUID.randomUUID(),1));
            var advice=new RecordAuditResponseAdvice(MapperFixtures.mapper(jdbc,com.appnexus.platform.persistence.AuditProjectionMapper.class),directory,request,authorization,null,null,null,null);
            var method=new org.springframework.core.MethodParameter(getClass().getDeclaredMethod("auditedRows"),-1);
            var response=new org.springframework.http.server.ServletServerHttpResponse(new org.springframework.mock.web.MockHttpServletResponse());
            Object projected=advice.beforeBodyWrite(List.of(Map.of("id",id)),method,org.springframework.http.MediaType.APPLICATION_JSON,null,null,response);
            var audit=((tools.jackson.databind.JsonNode)projected).get(0).get("audit");
            assertThat(audit.get("createdBy").asString()).isEqualToIgnoringCase(actor.toString());
            assertThat(audit.has("createdByName")).isFalse();
            jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",UUID.randomUUID().toString(),tenant,actor.toString());
            projected=advice.beforeBodyWrite(List.of(Map.of("id",id)),method,org.springframework.http.MediaType.APPLICATION_JSON,null,null,response);
            audit=((tools.jackson.databind.JsonNode)projected).get(0).get("audit");
            assertThat(audit.get("createdByName").asString()).isEqualTo("审计账号名");
            assertThat(audit.get("updatedByName").asString()).isEqualTo("审计账号名");
            UUID deputy=UUID.randomUUID();
            jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'另一位操作人','unused')",deputy.toString(),"audit-"+deputy);
            jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",UUID.randomUUID().toString(),tenant,deputy.toString());
            var transaction=new org.springframework.transaction.support.TransactionTemplate(manager);
            transaction.executeWithoutResult(status->{
                long before=selectCount();
                assertThat(directory.accountNames(UUID.fromString(tenant),List.of(actor.toString(),deputy.toString(),UUID.randomUUID().toString())))
                    .containsExactlyInAnyOrderEntriesOf(Map.of(actor.toString(),"审计账号名",deputy.toString(),"另一位操作人"));
                assertThat(selectCount()-before).isEqualTo(1);
            });
            request.setAttribute(com.appnexus.platform.tenant.VerifiedTenantContext.class.getName(),new com.appnexus.platform.tenant.VerifiedTenantContext(actor,UUID.randomUUID(),UUID.randomUUID(),1));
            projected=advice.beforeBodyWrite(List.of(Map.of("id",id)),method,org.springframework.http.MediaType.APPLICATION_JSON,null,null,response);
            assertThat(((tools.jackson.databind.JsonNode)projected).get(0).has("audit")).isFalse();
        }finally{SecurityContextHolder.clearContext();}
    }
    /** 使用真实会话 SQL 计数，验证 1000 个不同引用严格按 200 上限分批。 */
    @Test void thousandAuditReferencesUseFiveSelects(){
        UUID tenant=UUID.randomUUID();
        jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'名称批量规模')",tenant.toString());
        var ids=java.util.stream.IntStream.range(0,1000).mapToObj(ignored->UUID.randomUUID().toString()).toList();
        jdbc.batchUpdate("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'批量账号','unused')",
            ids.stream().map(id->new Object[]{id,"batch-audit-"+id}).toList());
        jdbc.batchUpdate("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",
            ids.stream().map(id->new Object[]{UUID.randomUUID().toString(),tenant.toString(),id}).toList());
        var keys=ids.stream().map(id->new com.appnexus.contracts.operation.ReferenceNames.Key("account",id)).toList();
        new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(status->{
            long before=selectCount(),start=System.nanoTime();
            var result=com.appnexus.contracts.operation.ReferenceNames.resolveKeys((scope,batch)->{
                var names=directory.accountNames(scope,batch.stream().map(com.appnexus.contracts.operation.ReferenceNames.Key::value).toList());
                var values=new HashMap<com.appnexus.contracts.operation.ReferenceNames.Key,String>();
                batch.forEach(key->values.put(key,names.get(key.value())));
                return values;
            },tenant,keys);
            long millis=(System.nanoTime()-start)/1_000_000;
            assertThat(selectCount()-before).isEqualTo(5);
            assertThat(result).hasSize(1000).containsValue("批量账号").doesNotContainValue(null);
            System.out.println("Audit references: 1000 accounts, 5 SELECT, "+millis+" ms");
        });
    }

    /** 会话状态查询本身不计入 SELECT，统计批量目录真实 SQL 次数。 */
    private long selectCount(){
        return Objects.requireNonNull(jdbc.queryForObject("SHOW SESSION STATUS LIKE 'Com_select'",
            (row,index)->row.getLong("Value")));
    }

    /** 仅测试方法元数据，模拟模块构建内声明；不暴露 HTTP 测试入口。 */
    @com.appnexus.contracts.operation.RecordAuditSource(table="md_warehouse")
    private void auditedRows(){}

    /** 测试认证账号仅用于审计主体，业务授权仍由实际服务入口独立复核。 */
    private void authenticate(UUID actor){SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor.toString(),null,List.of()));}
    private Map<String,Object> read(String tenant,String id){return jdbc.queryForMap("SELECT BIN_TO_UUID(created_by) creator,created_at,BIN_TO_UUID(updated_by) updater,updated_at FROM md_warehouse WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant,id);}
}
