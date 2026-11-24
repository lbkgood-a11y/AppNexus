package com.appnexus.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.appnexus.platform.authorization.DataScopeAuthorization;
import com.appnexus.platform.authorization.RoleManagement;
import com.appnexus.platform.authorization.RolePermissionManagement;
import com.appnexus.platform.authorization.SubjectRoleManagement;
import com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration;
import com.appnexus.contracts.data.TransactionalBusinessEvents;
import com.appnexus.contracts.businessobject.BusinessObjects.ScheduledOperations;
import com.appnexus.platform.security.AuthorityEventWorker;
import com.appnexus.platform.security.ReliableEvents;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 用真实会话、授权配置、Mapper 和 MySQL 验证作业队列及计数的边界。 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class InboundWorkbenchHttpTest extends DatabaseTestSupport {
    private static final String QUEUE = "/api/bulk-inbound-workbench/queue";
    private static final String RESOURCE = "warehousing.inboundtask";
    private static final String PERMISSION = RESOURCE + ":read_workbench_queue";

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired MyBatisPlatformAdministration administration;
    @Autowired RoleManagement roles;
    @Autowired SubjectRoleManagement subjects;
    @Autowired RolePermissionManagement permissions;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired ReliableEvents outbox;
    @Autowired ScheduledOperations scheduled;
    @Autowired List<TransactionalBusinessEvents.Consumer> consumers;
    @org.springframework.boot.test.web.server.LocalServerPort int httpPort;

    @Test
    void appliesRealTenantGrantAndReturnsOnlyQueueFields() throws Exception {
        administration.initializeAdministrator("admin-test");
        UUID admin = UUID.fromString(jdbc.queryForObject(
                "SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'", String.class));
        UUID owner = account(), reader = account();
        UUID tenant = administration.createTenant(admin, UUID.randomUUID(), "作业队列测试", owner).id();
        UUID foreign = UUID.randomUUID();
        jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'隔离作业队列测试')", foreign.toString());
        administration.addMember(admin, tenant, reader);
        UUID member = UUID.fromString(jdbc.queryForObject("""
                SELECT BIN_TO_UUID(id) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)
                """, String.class, tenant.toString(), reader.toString()));
        String samplingTask = seed(tenant, "WAIT_QUALITY", "ISSUED");
        seed(tenant, "WAIT_QUALITY", "RECEIVING");
        seed(tenant, "WAIT_TARE", "ISSUED");
        seed(tenant, "WAIT_QUALITY", "CLOSED");
        String foreignTask = seed(foreign, "WAIT_QUALITY", "ISSUED");

        var selected = mvc.perform(post("/bff/tenants/current").with(authenticated(reader.toString(), 0))
                .with(csrf()).contentType("application/json").content("{\"tenantId\":\"" + tenant + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("JSESSIONID");
        assertThat(selected).isNotNull();
        mvc.perform(get(QUEUE).cookie(selected)).andExpect(status().isForbidden());
        mvc.perform(get("/api/bulk-inbound-workbench/tasks/"+samplingTask).cookie(selected)).andExpect(status().isForbidden());

        var context = new RoleManagement.Context(admin, tenant, null);
        var role = roles.create(context, new RoleManagement.Create("queue_" + id().replace("-", ""),
                "队列测试角色", null, 100));
        UUID permission = UUID.fromString(jdbc.queryForObject(
                "SELECT BIN_TO_UUID(id) FROM access_permission WHERE code=?", String.class, PERMISSION));
        var granted = permissions.replaceAuthorization(context, role.id(), role.version(), Set.of(permission),
                Set.of(new RolePermissionManagement.DataPolicy(permission, DataScopeAuthorization.ScopeType.ALL,
                        RESOURCE, Set.of())), "队列集成验证");
        var links = subjects.memberRoles(context, member);
        subjects.replaceMemberRoles(context, member, links.authorityVersion(), Set.of(role.id()), "测试队列读取");

        var page = mvc.perform(get(QUEUE).cookie(selected).param("stage", "WAIT_QUALITY").param("limit", "1"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].stage").value("WAIT_QUALITY"))
                .andExpect(jsonPath("$.rows[0].vehiclePlate").doesNotExist())
                .andExpect(jsonPath("$.rows[0].driverName").doesNotExist())
                .andExpect(jsonPath("$.rows[0].supplierId").doesNotExist())
                .andExpect(jsonPath("$.rows[0].noticeId").doesNotExist())
                .andExpect(jsonPath("$.counts.WAIT_QUALITY").value(2))
                .andExpect(jsonPath("$.counts.WAIT_TARE").value(1))
                .andExpect(jsonPath("$.nextAfterId").isString())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).doesNotContain("PRIVATE-PLATE", "PRIVATE-DRIVER");
        String cursor = com.jayway.jsonpath.JsonPath.read(page, "$.nextAfterId");
        mvc.perform(get(QUEUE).cookie(selected).param("stage", "WAIT_QUALITY").param("limit", "1")
                .param("afterId", cursor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.counts.WAIT_QUALITY").value(2))
                .andExpect(jsonPath("$.nextAfterId").doesNotExist());
        var revoked = permissions.replaceAuthorization(context, role.id(), granted.roleVersion(), Set.of(), Set.of(), "撤销队列读取");
        mvc.perform(get(QUEUE).cookie(selected)).andExpect(status().isForbidden());
        verifySampling(context, role.id(), revoked.roleVersion(), selected, tenant, samplingTask, foreignTask);
        verifyReceiptDraft(owner, selected, tenant, samplingTask);
        verifyDetailProjection(context,role.id(),selected,samplingTask,foreignTask);
        verifyIndependentQuality(context, role.id(), selected, samplingTask);
        verifyIndependentDocuments(owner, tenant, foreign, foreignTask, selected);
    }

    /** 验证三单独立搜索与详情的真实投影，以及已授权用户对外租户已存在单据仍不可见。 */
    private void verifyIndependentDocuments(UUID owner, UUID tenant, UUID foreign, String foreignTask, Cookie lab) throws Exception {
        var session = mvc.perform(post("/bff/tenants/current").with(authenticated(owner.toString(), 0))
                .with(csrf()).contentType("application/json").content("{\"tenantId\":\"" + tenant + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("JSESSIONID");
        String foreignQuality = id();
        jdbc.update("""
                INSERT INTO bw_quality_order(tenant_id,id,task_id,quality_no,version,is_active,sample_barcode,status)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'FOREIGN-QUALITY',1,1,'FOREIGN-SAMPLE','SAMPLED')
                """, foreign.toString(), foreignQuality, foreignTask);
        for (String kind : List.of("quality", "metering", "receipt")) {
            String path = "/api/bulk-inbound-" + kind + "-orders";
            String result = mvc.perform(get(path).cookie(session))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].rowVersion").isNumber())
                    .andExpect(jsonPath("$.rows[0].taskVersion").isNumber())
                    .andReturn().getResponse().getContentAsString();
            String orderId = com.jayway.jsonpath.JsonPath.read(result, "$.rows[0].id");
            String number = com.jayway.jsonpath.JsonPath.read(result, "$.rows[0].number");
            var detail = mvc.perform(get(path + "/" + orderId).cookie(session))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.number").value(number));
            if ("receipt".equals(kind)) detail.andExpect(jsonPath("$.lines[0].accountingKg").isString());
            mvc.perform(get(path).cookie(session).param("number", number))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
            mvc.perform(get(path).cookie(session).param("page", "2").param("pageSize", "1").param("number", number))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.rows").isEmpty())
                    .andExpect(jsonPath("$.total").value(1));
        }
        mvc.perform(get("/api/bulk-inbound-quality-orders/" + foreignQuality).cookie(session))
                .andExpect(status().isNotFound());
        var browserScript = System.getProperty("inbound.documents.browser.script");
        if (browserScript != null) {
            // 验收浏览器使用同一隔离数据库及真实会话，凭证只经进程标准输入短暂传送。
            var evidence = java.nio.file.Files.createTempFile("inbound-document-browser-", ".log");
            var process = new ProcessBuilder("python", browserScript).redirectErrorStream(true)
                    .redirectOutput(evidence.toFile());
            process.environment().put("PYTHONIOENCODING", "utf-8");
            var browser = process.start();
            try {
                try (var input = browser.outputWriter()) {
                    input.write(httpPort + "\n" + session.getValue() + "\n" + lab.getValue() + "\n");
                }
                assertThat(browser.waitFor(150, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                String results = java.nio.file.Files.readString(evidence);
                System.out.print(results);
                assertThat(browser.exitValue()).as(results).isZero();
            } finally {
                if (browser.isAlive()) browser.destroyForcibly().waitFor();
                java.nio.file.Files.deleteIfExists(evidence);
            }
        }
    }

    /** 化验岗位可独立查看样品，不能使用工作台、跨岗位或绕过详情授权。 */
    private void verifyIndependentQuality(RoleManagement.Context context, UUID role, Cookie reader, String task) throws Exception {
        String base = "/api/bulk-inbound-quality-orders";
        String resource = "warehousing.inboundquality";
        String qualityId = jdbc.queryForObject("""
                SELECT BIN_TO_UUID(id) FROM bw_quality_order
                WHERE task_id=UUID_TO_BIN(?) AND is_active=1
                """, String.class, task);
        UUID list = lookupPermission(resource + ":list_documents");
        UUID read = lookupPermission(resource + ":read_document");
        var current = permissions.getAuthorization(context, role);
        var listed = permissions.replaceAuthorization(context, role, current.roleVersion(), Set.of(list),
                Set.of(new RolePermissionManagement.DataPolicy(list, DataScopeAuthorization.ScopeType.ALL, resource, Set.of())),
                "只授权质检列表");
        mvc.perform(get(base).cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].supplierId").doesNotExist())
                .andExpect(jsonPath("$.rows[0].driverName").doesNotExist());
        mvc.perform(get(base + "/" + qualityId).cookie(reader)).andExpect(status().isForbidden());
        mvc.perform(get("/api/bulk-inbound-metering-orders").cookie(reader)).andExpect(status().isForbidden());
        mvc.perform(get("/api/bulk-inbound-receipt-orders").cookie(reader)).andExpect(status().isForbidden());
        mvc.perform(get(QUEUE).cookie(reader)).andExpect(status().isForbidden());
        permissions.replaceAuthorization(context, role, listed.roleVersion(), Set.of(list, read),
                Set.of(new RolePermissionManagement.DataPolicy(list, DataScopeAuthorization.ScopeType.ALL, resource, Set.of()),
                        new RolePermissionManagement.DataPolicy(read, DataScopeAuthorization.ScopeType.ALL, resource, Set.of())),
                "授权质检列表和详情");
        mvc.perform(get(base + "/" + qualityId).cookie(reader)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.decision").value("PASS"))
                .andExpect(jsonPath("$.vehiclePlate").doesNotExist())
                .andExpect(jsonPath("$.driverName").doesNotExist())
                .andExpect(jsonPath("$.supplierId").doesNotExist());
        mvc.perform(get(base + "/" + UUID.randomUUID()).cookie(reader)).andExpect(status().isNotFound());
        mvc.perform(get(base).cookie(reader).param("number", "NO-SUCH-QUALITY-ORDER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    private void verifyDetailProjection(RoleManagement.Context context,UUID role,Cookie reader,String task,
                                        String foreignTask) throws Exception {
        String taskResource="warehousing.inboundtask",qualityResource="warehousing.inboundquality";
        UUID detail=lookupPermission(taskResource+":read_workbench_detail");
        UUID quality=lookupPermission(qualityResource+":read_workbench_quality");
        var current=permissions.getAuthorization(context,role);
        permissions.replaceAuthorization(context,role,current.roleVersion(),Set.of(detail,quality),
                Set.of(new RolePermissionManagement.DataPolicy(detail,DataScopeAuthorization.ScopeType.ALL,taskResource,Set.of()),
                        new RolePermissionManagement.DataPolicy(quality,DataScopeAuthorization.ScopeType.ALL,qualityResource,Set.of())),
                "仅查质检卡片");
        mvc.perform(get("/api/bulk-inbound-workbench/tasks/"+task).cookie(reader))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quality.decision").value("PASS"))
                .andExpect(jsonPath("$.identity").isEmpty()).andExpect(jsonPath("$.metering").isEmpty())
                .andExpect(jsonPath("$.receipt").isEmpty()).andExpect(jsonPath("$.totals").isEmpty());
        mvc.perform(get("/api/bulk-inbound-workbench/tasks/"+task+"/quality").cookie(reader))
                .andExpect(status().isOk()).andExpect(jsonPath("$.decision").value("PASS"));
        mvc.perform(get("/api/bulk-inbound-workbench/tasks/"+task+"/metering").cookie(reader))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/bulk-inbound-workbench/tasks/"+foreignTask).cookie(reader))
                .andExpect(status().isNotFound());
    }

    private UUID lookupPermission(String code) {
        return UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM access_permission WHERE code=?",String.class,code));
    }

    private void verifyReceiptDraft(UUID owner, Cookie reader, UUID tenant, String task) throws Exception {
        String t = tenant.toString(), area = id(), location = id(), product = id(), line = id();
        var source = jdbc.queryForMap("""
                SELECT BIN_TO_UUID(n.id) noticeId,BIN_TO_UUID(n.owner_id) ownerId,BIN_TO_UUID(n.warehouse_id) warehouseId
                FROM bw_inbound_task a JOIN bw_inbound_notice n ON n.tenant_id=a.tenant_id AND n.id=a.notice_id
                WHERE a.tenant_id=UUID_TO_BIN(?) AND a.id=UUID_TO_BIN(?)
                """, t, task);
        String warehouse = (String) source.get("warehouseId");
        jdbc.update("INSERT INTO md_warehouse(id,tenant_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'RT-W','草稿仓')",
                warehouse, t);
        jdbc.update("""
                INSERT INTO md_storage_area(id,tenant_id,code,name,parent_code)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'RT-A','草稿库区','RT-W')
                """, area, t);
        jdbc.update("""
                INSERT INTO md_storage_location(id,tenant_id,code,name,parent_code)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'RT-L','草稿库位','RT-A')
                """, location, t);
        jdbc.update("""
                INSERT INTO md_partner(id,tenant_id,code,name,partner_type)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'RT-OWNER','草稿货主','CUSTOMER')
                """, source.get("ownerId"), t);
        jdbc.update("""
                INSERT INTO md_product_unit_group(tenant_id,code,name) VALUES(UUID_TO_BIN(?),'MASS','质量')
                ON DUPLICATE KEY UPDATE code=code
                """, t);
        jdbc.update("""
                INSERT INTO md_product_unit(id,tenant_id,code,name,dimension_code)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'KILOGRAM','千克','MASS') ON DUPLICATE KEY UPDATE code=code
                """, id(), t);
        String unit = jdbc.queryForObject("""
                SELECT BIN_TO_UUID(id) FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(?) AND code='KILOGRAM'
                """, String.class, t);
        jdbc.update("""
                INSERT INTO md_product_v1(id,tenant_id,code,name,status,product_form,specification_mode,structure_state,base_unit_id)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'RT-P','草稿散货','ENABLED','PHYSICAL','NONE','COMPLETE',UUID_TO_BIN(?))
                """, product, t, unit);
        jdbc.update("""
                INSERT INTO bw_inbound_notice_line(tenant_id,id,notice_id,product_id,identity_kind,identity_id,
                    product_version,base_unit_id,approved_quantity)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'PRODUCT',UUID_TO_BIN(?),
                    0,UUID_TO_BIN(?),2000)
                """, t, line, source.get("noticeId"), product, product, unit);
        var session = mvc.perform(post("/bff/tenants/current").with(authenticated(owner.toString(), 0))
                        .with(csrf()).contentType("application/json").content("{\"tenantId\":\"" + tenant + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("JSESSIONID");
        String path = "/api/bulk-inbound-receipt-orders:create-bulk-draft";
        String input = """
                {"taskId":"%s","taskVersion":4,"meteringVersion":0,"noticeLineId":"%s",
                  "receiptNo":"RT-001","batchNo":"RT-BATCH","kilogramUnitId":"%s",
                  "position":{"warehouseId":"%s","areaId":"%s","locationId":"%s",
                    "warehouseVersion":0,"areaVersion":0,"locationVersion":0}}
                """.formatted(task, line, unit, warehouse, area, location);
        mvc.perform(post(path).cookie(reader).with(csrf()).contentType("application/json").content(input))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).cookie(session).param("taskNo", task)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskId").value(task)).andExpect(jsonPath("$[0].taskVersion").value(4))
                .andExpect(jsonPath("$[0].meteringVersion").value(0));
        String response = mvc.perform(post(path).cookie(session).with(csrf()).contentType("application/json").content(input))
                .andExpect(status().isOk()).andExpect(jsonPath("$.qualityState").value("PENDING_LAB"))
                .andExpect(jsonPath("$.taskVersion").value(5)).andReturn().getResponse().getContentAsString();
        String receipt = com.jayway.jsonpath.JsonPath.read(response, "$.receiptId");
        assertThat(jdbc.queryForObject("""
                SELECT inventory_quantity FROM bw_receipt_order_line
                WHERE tenant_id=UUID_TO_BIN(?) AND receipt_order_id=UUID_TO_BIN(?)
                """, java.math.BigDecimal.class, t, receipt)).isEqualByComparingTo("1250.125");
        assertThat(jdbc.queryForObject("""
                SELECT received_quantity FROM bw_inbound_notice_line WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                """, java.math.BigDecimal.class,t,line)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM bw_stock_receipt WHERE tenant_id=UUID_TO_BIN(?)
                """, Long.class,t)).isZero();
        mvc.perform(post(path).cookie(session).with(csrf()).contentType("application/json").content(input))
                .andExpect(status().isConflict());
        verifyPosting(reader, session, tenant, task, line, receipt);
        verifyReversalAndReopen(reader, session, tenant, task, line, receipt, input);
        verifySteelInspection(reader, session, owner, t, task, input);
        verifyNoticeToReceipt(session,reader,t,String.valueOf(source.get("ownerId")),warehouse,area,location,product,unit);
    }

    /** 通过真实签发和车次入口验证同通知的两个到场作业、模板固定及库存过账。 */
    private void verifyNoticeToReceipt(Cookie owner,Cookie reader,String tenant,String partner,String warehouse,
                                       String area,String location,String product,String unit) throws Exception {
        String notice=id(),line=id(),template=id(),task=id(),second=id();
        jdbc.update("""
                INSERT INTO bw_commodity_template(tenant_id,id,template_code,version,status,
                    metering_strategy,quality_gate_strategy,deduction_strategy,stock_granularity,definition)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'QA-ARRIVAL',1,'PUBLISHED','SCALE_OR_EXTERNAL','SAMPLED','ZERO','BULK',
                    JSON_OBJECT('noticeProductIds',JSON_ARRAY(?)))
                """,tenant,template,product);
        String issue="""
                {"id":"%s","warehouseId":"%s","ownerPartnerId":"%s","noticeNo":"ARR-E2E",
                 "lines":[{"id":"%s","item":{"productId":"%s","identityId":"%s","kind":"PRODUCT","productVersion":0},
                  "baseUnitId":"%s","limit":1000}]}
                """.formatted(notice,warehouse,partner,line,product,product,unit);
        mvc.perform(post("/api/bulk-inbound-receipts:issue").cookie(owner).with(csrf())
                .contentType("application/json").content(issue)).andExpect(status().isNoContent());
        String arrivals="/api/bulk-inbound-notices/"+notice+"/tasks";
        mvc.perform(get(arrivals).cookie(reader)).andExpect(status().isForbidden());
        mvc.perform(post(arrivals).cookie(reader).with(csrf()).contentType("application/json")
                .content("{\"requestId\":\""+id()+"\",\"identity\":\"OTHER\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(arrivals).cookie(owner)).andExpect(status().isOk()).andExpect(jsonPath("$.template.id").value(template));
        mvc.perform(post(arrivals).cookie(owner).with(csrf()).contentType("application/json")
                .content("{\"requestId\":\""+id()+"\",\"identity\":\"OTHER\",\"templateId\":\""+id()+"\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INBOUND_TASK_TEMPLATE_CHANGED"));
        String register="{\"requestId\":\""+task+"\",\"identity\":\"PLATED\",\"identifier\":\"QA-PRIVATE\"}";
        for(int retry=0;retry<2;retry++){
            mvc.perform(post(arrivals).cookie(owner).with(csrf()).contentType("application/json").content(register))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(task))
                    .andExpect(jsonPath("$.stage").value("WAIT_QUALITY"));
        }
        mvc.perform(post(arrivals).cookie(owner).with(csrf()).contentType("application/json")
                .content(register.replace("QA-PRIVATE","DIFFERENT-PLATE")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INBOUND_TASK_REQUEST_CONFLICT"));
        mvc.perform(post(arrivals).cookie(owner).with(csrf()).contentType("application/json")
                .content("{\"requestId\":\""+second+"\",\"identity\":\"OTHER\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(second));
        mvc.perform(get(arrivals).cookie(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(2)).andExpect(jsonPath("$.template.id").value(template));
        mvc.perform(get(QUEUE).cookie(owner).param("stage","WAIT_QUALITY")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[*].taskId").value(org.hamcrest.Matchers.hasItem(task)));
        String number="T-"+task.replace("-","");
        String opening="/api/bulk-inbound-receipt-orders:open-draft";
        String openInput="{\"taskId\":\""+task+"\",\"taskVersion\":0}";
        mvc.perform(get(QUEUE).cookie(owner).param("unopened","true")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[*].taskId").value(org.hamcrest.Matchers.hasItem(task)));
        mvc.perform(post(opening).cookie(reader).with(csrf()).contentType("application/json").content(openInput))
                .andExpect(status().isForbidden());
        String opened=mvc.perform(post(opening).cookie(owner).with(csrf()).contentType("application/json").content(openInput))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String openedId=com.jayway.jsonpath.JsonPath.read(opened,"$.receiptId");
        String openedNumber=com.jayway.jsonpath.JsonPath.read(opened,"$.receiptNo");
        mvc.perform(post(opening).cookie(owner).with(csrf()).contentType("application/json").content(openInput))
                .andExpect(status().isOk()).andExpect(jsonPath("$.receiptId").value(openedId));
        mvc.perform(get(QUEUE).cookie(owner).param("unopened","true")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[*].taskId").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(task))));
        mvc.perform(post("/api/bulk-inbound-receipt-orders/"+openedId+":post").cookie(owner).with(csrf())
                .header("Idempotency-Key","open-not-postable").contentType("application/json")
                .content("{\"taskId\":\""+task+"\",\"taskVersion\":0,\"receiptVersion\":0}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("""
                SELECT received_quantity FROM bw_inbound_notice_line
                WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                """,java.math.BigDecimal.class,tenant,line)).isZero();
        String samplePath="/api/bulk-inbound-quality-orders:register-sample";
        mvc.perform(get(samplePath).cookie(owner).param("taskNo",number)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskId").value(task));
        mvc.perform(post(samplePath).cookie(owner).with(csrf()).contentType("application/json")
                .content(sample(task,0,"Q-ARRIVAL","S-ARRIVAL",null))).andExpect(status().isOk());
        String meter="/api/bulk-inbound-metering-orders:register-external";
        mvc.perform(get(meter).cookie(owner).param("taskNo",number)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskVersion").value(1));
        mvc.perform(post(meter).cookie(owner).with(csrf()).contentType("application/json")
                .content(externalNet(task,1,"M-ARRIVAL","RAIL-ARRIVAL").replace("1250.125","100.000")))
                .andExpect(status().isOk());
        String draft="/api/bulk-inbound-receipt-orders:create-bulk-draft";
        mvc.perform(get("/api/bulk-inbound-receipt-orders/"+openedId).cookie(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.collecting").isNotEmpty()).andExpect(jsonPath("$.taskVersion").value(2))
                .andExpect(jsonPath("$.meteringVersion").value(0));
        String response=mvc.perform(post(draft).cookie(owner).with(csrf()).contentType("application/json")
                .content("""
                        {"taskId":"%s","taskVersion":2,"meteringVersion":0,"noticeLineId":"%s",
                         "receiptNo":"%s","batchNo":"B-ARRIVAL","kilogramUnitId":"%s",
                         "position":{"warehouseId":"%s","areaId":"%s","locationId":"%s",
                           "warehouseVersion":0,"areaVersion":0,"locationVersion":0}}
                        """.formatted(task,line,openedNumber,unit,warehouse,area,location)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.receiptId").value(openedId))
                .andExpect(jsonPath("$.receiptVersion").value(1)).andReturn().getResponse().getContentAsString();
        String receipt=com.jayway.jsonpath.JsonPath.read(response,"$.receiptId");
        mvc.perform(post("/api/bulk-inbound-receipt-orders/"+receipt+":post").cookie(owner).with(csrf())
                .header("Idempotency-Key","arrival-e2e-post").contentType("application/json")
                .content("{\"taskId\":\""+task+"\",\"taskVersion\":3,\"receiptVersion\":1}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                SELECT received_quantity FROM bw_inbound_notice_line
                WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                """,java.math.BigDecimal.class,tenant,line)).isEqualByComparingTo("100.0000");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM bw_inbound_task WHERE tenant_id=UUID_TO_BIN(?) AND notice_id=UUID_TO_BIN(?)
                  AND template_id=UUID_TO_BIN(?)
                """,Long.class,tenant,notice,template)).isEqualTo(2L);
    }

    private void verifyPosting(Cookie reader, Cookie owner, UUID tenant, String task, String line,
                               String receipt) throws Exception {
        String t = tenant.toString();
        String path = "/api/bulk-inbound-receipt-orders/" + receipt + ":post";
        String command = "{\"taskId\":\"" + task + "\",\"taskVersion\":5,\"receiptVersion\":0}";
        mvc.perform(post(path).cookie(reader).with(csrf()).header("Idempotency-Key", "post-once")
                        .contentType("application/json").content(command))
                .andExpect(status().isForbidden());
        String quality = jdbc.queryForObject("""
                SELECT BIN_TO_UUID(quality_id) FROM bw_receipt_order WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                """, String.class, t, receipt);
        String lab = "{\"taskId\":\"" + task + "\",\"taskVersion\":5,\"qualityRowVersion\":0,\"metrics\":{\"moisture\":12.5}}";
        mvc.perform(post("/api/bulk-inbound-quality-orders/" + quality + ":decide").cookie(owner).with(csrf())
                        .contentType("application/json").content(lab))
                .andExpect(status().isOk()).andExpect(jsonPath("$.taskVersion").value(6));
        String current = command.replace("\"taskVersion\":5", "\"taskVersion\":6");
        jdbc.update("UPDATE bw_inbound_notice_line SET approved_quantity=1000 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",t,line);
        mvc.perform(post(path).cookie(owner).with(csrf()).header("Idempotency-Key", "post-once")
                        .contentType("application/json").content(current))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INBOUND_RECEIPT_POST_QUOTA_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_receipt_execution WHERE tenant_id=UUID_TO_BIN(?)",
                Long.class,t)).isZero();
        jdbc.update("UPDATE bw_inbound_notice_line SET approved_quantity=2000 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",t,line);
        for (int repeat = 0; repeat < 2; repeat++) {
            mvc.perform(post(path).cookie(owner).with(csrf()).header("Idempotency-Key", "post-once")
                            .contentType("application/json").content(current))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.receiptVersion").value(1));
        }
        mvc.perform(post(path).cookie(owner).with(csrf()).header("Idempotency-Key", "post-once")
                        .contentType("application/json").content(command))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INBOUND_EXECUTION_IDEMPOTENCY_CONFLICT"));
        assertThat(jdbc.queryForObject("""
                SELECT received_quantity FROM bw_inbound_notice_line WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                """, java.math.BigDecimal.class,t,line)).isEqualByComparingTo("1250.125");
        assertThat(jdbc.queryForObject("""
                SELECT inbound_quantity FROM bw_stock_receipt WHERE tenant_id=UUID_TO_BIN(?)
                """, java.math.BigDecimal.class,t)).isEqualByComparingTo("1250.125");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM bw_receipt_execution WHERE tenant_id=UUID_TO_BIN(?) AND operation_code='POST'
                """, Long.class,t)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT status FROM bw_inbound_task WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                """, String.class,t,task)).isEqualTo("RECEIVED");
    }

    private void verifyReversalAndReopen(Cookie reader, Cookie owner, UUID tenant, String task, String line,
                                         String receipt, String draft) throws Exception {
        String t = tenant.toString();
        String path = "/api/bulk-inbound-receipt-orders/" + receipt + ":reverse";
        String command = "{\"taskId\":\"" + task + "\",\"taskVersion\":7,\"receiptVersion\":1}";
        mvc.perform(post(path).cookie(reader).with(csrf()).header("Idempotency-Key", "reverse-once")
                        .contentType("application/json").content(command))
                .andExpect(status().isForbidden());
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            // 公共审计会记录库存修改，模拟的发出与审计必须整体回滚才能恢复未动过的库存。
            jdbc.update("UPDATE bw_stock_receipt SET shipped_quantity=1 WHERE tenant_id=UUID_TO_BIN(?)",t);
            try {
                mvc.perform(post(path).cookie(owner).with(csrf()).header("Idempotency-Key", "reverse-once")
                                .contentType("application/json").content(command))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("INBOUND_RECEIPT_STOCK_NOT_REVERSIBLE"));
            } catch (Exception e) {
                throw new AssertionError("已发出库存的冲红验证失败", e);
            } finally {
                status.setRollbackOnly();
            }
        });
        for (int repeat = 0; repeat < 2; repeat++) {
            mvc.perform(post(path).cookie(owner).with(csrf()).header("Idempotency-Key", "reverse-once")
                            .contentType("application/json").content(command))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.receiptVersion").value(2));
        }
        assertThat(jdbc.queryForObject("""
                SELECT received_quantity FROM bw_inbound_notice_line WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                """, java.math.BigDecimal.class,t,line)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM bw_stock_receipt WHERE tenant_id=UUID_TO_BIN(?) AND reversed_at IS NULL
                """, Long.class,t)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT inbound_quantity FROM bw_stock_receipt WHERE tenant_id=UUID_TO_BIN(?) AND reversed_at IS NOT NULL
                """, java.math.BigDecimal.class,t)).isEqualByComparingTo("1250.125");
        mvc.perform(get("/api/bulk-warehouse-inventory").cookie(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(0));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM bw_receipt_execution WHERE tenant_id=UUID_TO_BIN(?) AND operation_code='REVERSE'
                """, Long.class,t)).isEqualTo(1L);
        String priorQuality = jdbc.queryForObject("""
                SELECT BIN_TO_UUID(id) FROM bw_quality_order WHERE tenant_id=UUID_TO_BIN(?) AND active_task_id=UUID_TO_BIN(?)
                """, String.class,t,task);
        String sampled = mvc.perform(post("/api/bulk-inbound-quality-orders:register-sample").cookie(owner).with(csrf())
                        .contentType("application/json").content(sample(task, 8, "Q-PENDING", "S-PENDING", priorQuality)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.taskVersion").value(9))
                .andReturn().getResponse().getContentAsString();
        String pendingSample = com.jayway.jsonpath.JsonPath.read(sampled, "$.qualityId");
        String reopened = draft.replace("\"taskVersion\":4", "\"taskVersion\":9")
                .replace("\"receiptNo\":\"RT-001\"", "\"receiptNo\":\"RT-002\",\"replacesReceiptId\":\"" + receipt + "\"");
        var result = mvc.perform(post("/api/bulk-inbound-receipt-orders:create-bulk-draft").cookie(owner).with(csrf())
                        .contentType("application/json").content(reopened))
                .andExpect(status().isOk()).andExpect(jsonPath("$.taskVersion").value(10))
                .andExpect(jsonPath("$.qualityState").value("PENDING_LAB"))
                .andReturn().getResponse().getContentAsString();
        String replacement = com.jayway.jsonpath.JsonPath.read(result, "$.receiptId");
        assertThat(jdbc.queryForObject("""
                SELECT BIN_TO_UUID(replaces_receipt_id) FROM bw_receipt_order
                WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND active_task_id=task_id
                """, String.class,t,replacement)).isEqualTo(receipt);
        verifyAsyncQuality(owner, t, task, replacement, pendingSample);
        verifyQualityFreeze(owner, t, task, draft);
        verifyGroundWeighing(reader, owner, t, task);
    }

    private void verifyAsyncQuality(Cookie owner, String tenant, String task, String receipt, String sample) throws Exception {
        String posting = "{\"taskId\":\"" + task + "\",\"taskVersion\":10,\"receiptVersion\":0}";
        mvc.perform(post("/api/bulk-inbound-receipt-orders/" + receipt + ":post").cookie(owner).with(csrf())
                        .header("Idempotency-Key", "post-pending").contentType("application/json").content(posting))
                .andExpect(status().isOk());
        String stockSql = """
                SELECT s.quality_state FROM bw_stock_receipt s JOIN bw_receipt_order_line l
                  ON l.tenant_id=s.tenant_id AND l.id=s.receipt_id
                WHERE s.tenant_id=UUID_TO_BIN(?) AND l.receipt_order_id=UUID_TO_BIN(?)
                """;
        assertThat(jdbc.queryForObject(stockSql,String.class,tenant,receipt)).isEqualTo("PENDING_LAB");
        mvc.perform(get("/api/bulk-inbound-workbench/tasks/"+task).cookie(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blind").value(true))
                .andExpect(jsonPath("$.identity").isEmpty())
                .andExpect(jsonPath("$.identity.driverName").doesNotExist())
                .andExpect(jsonPath("$.identity.ownerId").doesNotExist())
                .andExpect(jsonPath("$.totals.qualityState").value("PENDING_LAB"))
                .andExpect(jsonPath("$.metering.physicalNetKg").value("1250.1250"));
        mvc.perform(get("/api/bulk-inbound-workbench/tasks/"+task+"/identity").cookie(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.driverName").doesNotExist());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM platform_outbox WHERE tenant_id=UUID_TO_BIN(?) AND aggregate_id=?
                  AND event_type='INBOUND_RECEIPT_POSTED' AND status='PENDING'
                """,Long.class,tenant,receipt)).isEqualTo(1L);
        String input = "{\"taskId\":\"" + task + "\",\"taskVersion\":11,\"qualityRowVersion\":0,\"metrics\":{\"moisture\":12.5}}";
        mvc.perform(post("/api/bulk-inbound-quality-orders/" + sample + ":decide").cookie(owner).with(csrf())
                        .contentType("application/json").content(input))
                .andExpect(status().isOk()).andExpect(jsonPath("$.taskVersion").value(11));
        assertThat(jdbc.queryForObject(stockSql,String.class,tenant,receipt)).isEqualTo("PENDING_LAB");
        var worker = new AuthorityEventWorker(outbox, scheduled, true, consumers);
        worker.process();
        worker.process();
        assertThat(jdbc.queryForObject(stockSql,String.class,tenant,receipt)).isEqualTo("QUALIFIED");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM bw_stock_receipt WHERE tenant_id=UUID_TO_BIN(?) AND quality_state='QUALIFIED'
                  AND reversed_at IS NULL AND pending_quantity=0 AND quality_version=3
                  AND JSON_EXTRACT(quality_snapshot,'$.metrics.moisture')=12.5
                """,Long.class,tenant)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM bw_inbound_task WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                  AND status='RECEIVED' AND row_version=11 AND queue_stage IS NULL
                """,Long.class,tenant,task)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM platform_event_consumption c JOIN platform_outbox o ON o.id=c.event_id
                JOIN platform_event_delivery d ON d.event_id=o.id AND d.tenant_id=c.tenant_id
                  AND d.consumer_id=c.consumer
                WHERE c.tenant_id=UUID_TO_BIN(?) AND c.consumer='inbound-batch-quality'
                  AND o.event_type='INBOUND_QUALITY_DECIDED' AND o.aggregate_id=? AND d.status='DONE'
                """,Long.class,tenant,sample)).isEqualTo(1L);
        String timestampSql = """
                SELECT s.updated_at FROM bw_stock_receipt s JOIN bw_receipt_order_line l
                  ON l.tenant_id=s.tenant_id AND l.id=s.receipt_id
                WHERE s.tenant_id=UUID_TO_BIN(?) AND l.receipt_order_id=UUID_TO_BIN(?)
                """;
        var appliedAt = jdbc.queryForObject(timestampSql,java.sql.Timestamp.class,tenant,receipt);
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            outbox.publish(UUID.fromString(tenant), TransactionalBusinessEvents.Type.INBOUND_QUALITY_DECIDED, UUID.fromString(sample));
            outbox.publish(UUID.randomUUID(), TransactionalBusinessEvents.Type.INBOUND_QUALITY_DECIDED, UUID.fromString(sample));
        });
        worker.process();
        assertThat(jdbc.queryForObject(timestampSql,java.sql.Timestamp.class,tenant,receipt)).isEqualTo(appliedAt);
    }

    private void verifyQualityFreeze(Cookie owner, String tenant, String sourceTask, String draft) throws Exception {
        String task = id();
        jdbc.update("""
                INSERT INTO bw_inbound_task(tenant_id,id,task_no,notice_id,template_id,vehicle_identity_type,status,
                  queue_stage,stage_started_at)
                SELECT tenant_id,UUID_TO_BIN(?),'T-FREEZE',notice_id,template_id,'OTHER','WORKING','WAIT_QUALITY',CURRENT_TIMESTAMP(6)
                FROM bw_inbound_task WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                """,task,tenant,sourceTask);
        String sampled = mvc.perform(post("/api/bulk-inbound-quality-orders:register-sample").cookie(owner).with(csrf())
                        .contentType("application/json").content(sample(task, 0, "Q-FREEZE", "S-FREEZE", null)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String sample = com.jayway.jsonpath.JsonPath.read(sampled, "$.qualityId");
        String gross="{\"taskId\":\""+task+"\",\"taskVersion\":1,\"grossMeteringNo\":\"M-FREEZE\",\"ticket\":"
                + groundTicket("GROSS-FREEZE","2025-01-02T01:00:00Z","650") + "}";
        mvc.perform(post("/api/bulk-inbound-metering-orders:record-gross").cookie(owner).with(csrf())
                        .contentType("application/json").content(gross))
                .andExpect(status().isOk());
        String tare="{\"taskId\":\""+task+"\",\"taskVersion\":2,\"meterVersion\":0,\"ticket\":"
                + groundTicket("TARE-FREEZE","2025-01-02T02:00:00Z","150") + "}";
        mvc.perform(post("/api/bulk-inbound-metering-orders:record-tare").cookie(owner).with(csrf())
                        .contentType("application/json").content(tare)).andExpect(status().isOk())
                .andExpect(jsonPath("$.netKg").value("500.0000"));
        String created = mvc.perform(post("/api/bulk-inbound-receipt-orders:create-bulk-draft").cookie(owner).with(csrf())
                        .contentType("application/json").content(draft.replace(sourceTask,task)
                                .replace("\"taskVersion\":4","\"taskVersion\":3")
                                .replace("\"meteringVersion\":0","\"meteringVersion\":1")
                                .replace("RT-001","RT-FREEZE")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String receipt = com.jayway.jsonpath.JsonPath.read(created, "$.receiptId");
        String posting = "{\"taskId\":\"" + task + "\",\"taskVersion\":4,\"receiptVersion\":0}";
        mvc.perform(post("/api/bulk-inbound-receipt-orders/" + receipt + ":post").cookie(owner).with(csrf())
                        .header("Idempotency-Key", "post-frozen").contentType("application/json").content(posting))
                .andExpect(status().isOk());
        String input = "{\"taskId\":\"" + task + "\",\"taskVersion\":5,\"qualityRowVersion\":0,\"metrics\":{\"moisture\":20}}";
        mvc.perform(post("/api/bulk-inbound-quality-orders/" + sample + ":decide").cookie(owner).with(csrf())
                        .contentType("application/json").content(input))
                .andExpect(status().isOk()).andExpect(jsonPath("$.decision").value("FAIL"))
                .andExpect(jsonPath("$.taskVersion").value(5));
        new AuthorityEventWorker(outbox, scheduled, true, consumers).process();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM bw_stock_receipt s JOIN bw_receipt_order_line l ON l.tenant_id=s.tenant_id AND l.id=s.receipt_id
                WHERE s.tenant_id=UUID_TO_BIN(?) AND l.receipt_order_id=UUID_TO_BIN(?)
                  AND s.quality_state='QUALITY_FROZEN' AND s.quality_version=1 AND s.pending_quantity=500
                """,Long.class,tenant,receipt)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM bw_inbound_task WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                  AND status='RECEIVED' AND row_version=5 AND queue_stage IS NULL
                """,Long.class,tenant,task)).isEqualTo(1L);
    }

    private void verifyGroundWeighing(Cookie reader, Cookie owner, String tenant, String sourceTask) throws Exception {
        String template=id(), task=id(), returned=id();
        jdbc.update("""
                INSERT INTO bw_commodity_template(tenant_id,id,template_code,version,status,metering_strategy,
                  quality_gate_strategy,deduction_strategy,stock_granularity,definition)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'GROUND',1,'PUBLISHED','SCALE','QUALIFIED_AND_UNLOADED','ZERO','BULK',
                  JSON_OBJECT('qualityMetrics',JSON_OBJECT('moisture',JSON_OBJECT('unit','pct',
                    'minimum',0,'maximum',100,'acceptMinimum',0,'acceptMaximum',14))))
                """,tenant,template);
        for(String target : List.of(task,returned)) {
            jdbc.update("""
                    INSERT INTO bw_inbound_task(tenant_id,id,task_no,notice_id,template_id,vehicle_identity_type,status)
                    SELECT tenant_id,UUID_TO_BIN(?),?,notice_id,UUID_TO_BIN(?),'OTHER','ARRIVED'
                    FROM bw_inbound_task WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                    """,target,target,template,tenant,sourceTask);
        }
        String root="/api/bulk-inbound-metering-orders:";
        mvc.perform(get(root+"record-gross").cookie(owner).param("taskNo", task))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].taskId").value(task))
                .andExpect(jsonPath("$[0].taskVersion").value(0));
        String gross = "{\"taskId\":\"" + task + "\",\"taskVersion\":0,\"grossMeteringNo\":\"M-GROSS\",\"ticket\":"
                + groundTicket("GROSS-FIRST","2025-01-01T01:00:00Z","100") + "}";
        mvc.perform(post(root+"record-gross").cookie(reader).with(csrf()).contentType("application/json").content(gross))
                .andExpect(status().isForbidden());
        mvc.perform(post(root+"record-gross").cookie(owner).with(csrf()).contentType("application/json").content(gross))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("GROSS_RECORDED"));
        mvc.perform(get(root+"record-gross").cookie(owner).param("taskNo", task))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        String tare = "{\"taskId\":\"" + task + "\",\"taskVersion\":1,\"meterVersion\":0,\"ticket\":"
                + groundTicket("TARE-FIRST","2025-01-01T01:30:00Z","30") + "}";
        mvc.perform(post(root+"record-tare").cookie(owner).with(csrf()).contentType("application/json").content(tare))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUALITY_OR_UNLOAD_REQUIRED"));
        String unload="{\"taskVersion\":1,\"meterVersion\":0,\"outcome\":\"ACCEPTED_WITH_DEDUCTION\",\"extraDeductionKg\":\"1.50\"}";
        mvc.perform(post("/api/bulk-inbound-tasks/"+task+":confirm-unloading").cookie(owner).with(csrf())
                        .contentType("application/json").content(unload)).andExpect(status().isOk());
        String sampleBody = mvc.perform(post("/api/bulk-inbound-quality-orders:register-sample").cookie(owner).with(csrf())
                        .contentType("application/json").content(sample(task,2,"Q-GROUND","S-GROUND",null)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String quality = com.jayway.jsonpath.JsonPath.read(sampleBody,"$.qualityId");
        String lab="{\"taskId\":\""+task+"\",\"taskVersion\":3,\"qualityRowVersion\":0,\"metrics\":{\"moisture\":11.5}}";
        mvc.perform(post("/api/bulk-inbound-quality-orders/"+quality+":decide").cookie(owner).with(csrf())
                        .contentType("application/json").content(lab)).andExpect(status().isOk());
        String ready=tare.replace("\"taskVersion\":1","\"taskVersion\":4");
        mvc.perform(post(root+"record-tare").cookie(owner).with(csrf()).contentType("application/json")
                        .content(ready.replace("TARE-FIRST","gross-first")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INBOUND_SCALE_TICKET_CONFLICT"));
        mvc.perform(post(root+"record-tare").cookie(owner).with(csrf()).contentType("application/json").content(ready))
                .andExpect(status().isOk()).andExpect(jsonPath("$.netKg").value("70.0000"));
        assertThat(jdbc.queryForObject("""
                SELECT unload_deduction_kg FROM bw_inbound_task WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                """,java.math.BigDecimal.class,tenant,task)).isEqualByComparingTo("1.50");

        mvc.perform(post(root+"record-gross").cookie(owner).with(csrf()).contentType("application/json")
                        .content(gross.replace(task,returned).replace("M-GROSS","M-RETURN").replace("GROSS-FIRST","GROSS-RETURN")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/bulk-inbound-tasks/"+returned+":confirm-unloading").cookie(owner).with(csrf())
                        .contentType("application/json").content(unload.replace("ACCEPTED_WITH_DEDUCTION","RETURN").replace("1.50","0")))
                .andExpect(status().isOk());
        String exit=tare.replace(task,returned).replace("\"taskVersion\":1","\"taskVersion\":2")
                .replace("TARE-FIRST","TARE-RETURN").replace("\"30\"","\"120\"");
        mvc.perform(post(root+"record-tare").cookie(owner).with(csrf()).contentType("application/json").content(exit))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("RETURN_RECORDED"))
                .andExpect(jsonPath("$.netKg").doesNotExist());
        assertThat(jdbc.queryForObject("""
                SELECT status FROM bw_inbound_task WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                """,String.class,tenant,returned)).isEqualTo("RETURNED");
    }

    private static String groundTicket(String number,String at,String kg) {
        return "{\"station\":\"STATION-1\",\"ticketNo\":\""+number+"\",\"observedAt\":\""+at+"\",\"kg\":\""+kg+"\"}";
    }

    private void verifySteelInspection(Cookie reader,Cookie inspector,UUID actor,String tenant,String sourceTask,
                                       String sourceDraft) throws Exception {
        String template=id(), task=id(), mtc=id(), photo=id(), unrelated=id();
        jdbc.update("""
                INSERT INTO bw_commodity_template(tenant_id,id,template_code,version,status,metering_strategy,
                  quality_gate_strategy,deduction_strategy,stock_granularity,definition)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'STEEL-INSPECT',1,'PUBLISHED','DUAL','DOCUMENT_AND_APPEARANCE',
                  'ZERO','LPN',JSON_OBJECT('maxVarianceRatio',0.05,'requireBothWeights',true))
                """,tenant,template);
        jdbc.update("""
                INSERT INTO bw_inbound_task(tenant_id,id,task_no,notice_id,template_id,vehicle_identity_type,status)
                SELECT tenant_id,UUID_TO_BIN(?),'T-STEEL',notice_id,UUID_TO_BIN(?),'OTHER','ARRIVED'
                FROM bw_inbound_task WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                """,task,template,tenant,sourceTask);
        attachment(tenant,actor,mtc,"inbound-task:"+task,"pdf","application/pdf");
        attachment(tenant,actor,photo,"inbound-task:"+task,"png","image/png");
        attachment(tenant,actor,unrelated,"inbound-task:"+sourceTask,"png","image/png");
        String path="/api/bulk-inbound-quality-orders:inspect-steel";
        mvc.perform(get(path).cookie(inspector).param("taskNo", "T-STEEL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].taskId").value(task))
                .andExpect(jsonPath("$[0].taskVersion").value(0));
        String input="""
                {"taskId":"%s","taskVersion":0,"steelQualityNo":"QI-STEEL","heatNo":"HEAT-01",
                   "certificateNo":"MTC-01","certificateId":"%s","appearanceId":"%s",
                   "certificateVerified":true,"appearanceVerified":true}
                """.formatted(task,mtc,photo);
        mvc.perform(post(path).cookie(reader).with(csrf()).contentType("application/json").content(input))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).cookie(inspector).with(csrf()).contentType("application/json")
                        .content(input.replace(photo,unrelated))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INBOUND_STEEL_ATTACHMENTS_INVALID"));
        mvc.perform(post(path).cookie(inspector).with(csrf()).contentType("application/json").content(input))
                .andExpect(status().isOk()).andExpect(jsonPath("$.decision").value("PASS"))
                .andExpect(jsonPath("$.taskVersion").value(1));
        assertThat(jdbc.queryForObject("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(result_snapshot,'$.heatNo'))
                FROM bw_quality_order WHERE tenant_id=UUID_TO_BIN(?) AND task_id=UUID_TO_BIN(?) AND sampled_at IS NULL
                """,String.class,tenant,task)).isEqualTo("HEAT-01");
        mvc.perform(post(path).cookie(inspector).with(csrf()).contentType("application/json").content(input))
                .andExpect(status().isConflict());
        verifySteelPosting(reader,inspector,tenant,task,sourceDraft);
    }

    private void verifySteelPosting(Cookie reader,Cookie owner,String tenant,String task,String sourceDraft) throws Exception {
        String meter="/api/bulk-inbound-metering-orders:confirm-steel";
        mvc.perform(get(meter).cookie(owner).param("taskNo", "T-STEEL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].taskId").value(task))
                .andExpect(jsonPath("$[0].taskVersion").value(1));
        String measured="""
                {"taskId":"%s","taskVersion":1,"steelMeteringNo":"SM-001","basis":"SCALE","packages":3,
                  "theoreticalKg":"100.0000","gross":%s,"tare":%s}
                """.formatted(task,groundTicket("STEEL-G","2025-01-03T01:00:00Z","130.0001"),
                groundTicket("STEEL-T","2025-01-03T02:00:00Z","30"));
        mvc.perform(post(meter).cookie(owner).with(csrf()).contentType("application/json")
                        .content(measured.replace("\"100.0000\"","\"80.0000\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INBOUND_STEEL_MEASUREMENT_INVALID_OR_VARIANCE_EXCEEDED"));
        mvc.perform(post(meter).cookie(owner).with(csrf()).contentType("application/json").content(measured))
                .andExpect(status().isOk()).andExpect(jsonPath("$.physicalNetKg").value("100.0001"))
                .andExpect(jsonPath("$.taskVersion").value(2));

        String line=com.jayway.jsonpath.JsonPath.read(sourceDraft,"$.noticeLineId");
        String kilogram=com.jayway.jsonpath.JsonPath.read(sourceDraft,"$.kilogramUnitId");
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
        String position=mapper.writeValueAsString(com.jayway.jsonpath.JsonPath.read(sourceDraft,"$.position"));
        var lines=new java.util.ArrayList<String>();
        for(int index=1;index<=3;index++){
            lines.add("""
                    {"noticeLineId":"%s","lpn":"LPN-%d","heatNo":"HEAT-01","specification":"S-01",
                     "material":"MA-01","theoreticalKg":"%s","position":%s,"batchNo":"B-STEEL","kilogramUnitId":"%s"}
                    """.formatted(line,index,index==3?"40":"30",position,kilogram));
        }
        String draft="""
                {"taskId":"%s","taskVersion":2,"meteringVersion":0,"steelReceiptNo":"SR-001","lines":[%s]}
                """.formatted(task,String.join(",",lines));
        String create="/api/bulk-inbound-receipt-orders:create-steel-draft";
        mvc.perform(get(create).cookie(owner).param("taskNo", "T-STEEL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].taskId").value(task))
                .andExpect(jsonPath("$[0].taskVersion").value(2))
                .andExpect(jsonPath("$[0].meteringVersion").value(0));
        mvc.perform(post(create).cookie(reader).with(csrf()).contentType("application/json").content(draft))
                .andExpect(status().isForbidden());
        mvc.perform(post(create).cookie(owner).with(csrf()).contentType("application/json").content(draft.replace("LPN-2","lpn-1")))
                .andExpect(status().isBadRequest());
        String response=mvc.perform(post(create).cookie(owner).with(csrf()).contentType("application/json").content(draft))
                .andExpect(status().isOk()).andExpect(jsonPath("$.taskVersion").value(3))
                .andReturn().getResponse().getContentAsString();
        String receipt=com.jayway.jsonpath.JsonPath.read(response,"$.receiptId");
        assertThat(jdbc.queryForObject("""
                SELECT SUM(accounting_weight_kg) FROM bw_receipt_order_line WHERE tenant_id=UUID_TO_BIN(?)
                  AND receipt_order_id=UUID_TO_BIN(?) AND heat_no='HEAT-01' AND lpn IS NOT NULL
                """,java.math.BigDecimal.class,tenant,receipt)).isEqualByComparingTo("100.0001");
        assertThat(jdbc.queryForObject("""
                SELECT accounting_weight_kg-CAST(JSON_UNQUOTE(JSON_EXTRACT(measurement_snapshot,'$.theoreticalKg')) AS DECIMAL(18,4))
                FROM bw_receipt_order_line WHERE tenant_id=UUID_TO_BIN(?) AND receipt_order_id=UUID_TO_BIN(?)
                ORDER BY id DESC LIMIT 1
                """,java.math.BigDecimal.class,tenant,receipt)).isEqualByComparingTo("0.0001");
        String postPath="/api/bulk-inbound-receipt-orders/"+receipt+":post";
        String input="{\"taskId\":\""+task+"\",\"taskVersion\":3,\"receiptVersion\":0}";
        jdbc.update("UPDATE bw_inbound_notice_line SET approved_quantity=1800 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant,line);
        mvc.perform(post(postPath).cookie(owner).with(csrf()).contentType("application/json").header("Idempotency-Key","steel-post")
                        .content(input)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INBOUND_RECEIPT_POST_QUOTA_CONFLICT"));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM bw_stock_receipt WHERE tenant_id=UUID_TO_BIN(?) AND serial LIKE 'LPN-%'
                """,Long.class,tenant)).isZero();
        jdbc.update("UPDATE bw_inbound_notice_line SET approved_quantity=2000 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant,line);
        mvc.perform(post(postPath).cookie(owner).with(csrf()).contentType("application/json").header("Idempotency-Key","steel-post")
                        .content(input)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                SELECT SUM(inbound_quantity) FROM bw_stock_receipt WHERE tenant_id=UUID_TO_BIN(?) AND serial LIKE 'LPN-%'
                """,java.math.BigDecimal.class,tenant)).isEqualByComparingTo("100.0001");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM bw_stock_receipt WHERE tenant_id=UUID_TO_BIN(?) AND active_inbound_lpn LIKE 'LPN-%'
                """,Long.class,tenant)).isEqualTo(3L);
        mvc.perform(get("/api/bulk-inbound-workbench/tasks/"+task).cookie(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blind").value(false))
                .andExpect(jsonPath("$.receipt.lines.length()").value(3))
                .andExpect(jsonPath("$.totals.accountingKg").value("100.0001"))
                .andExpect(jsonPath("$.quality.decision").value("PASS"));
    }

    private void attachment(String tenant,UUID actor,String attachment,String business,String type,String media) {
        jdbc.update("""
                INSERT INTO platform_attachment(id,tenant_id,business_id,file_name,size_bytes,type_key,media_type,
                  content_sha256,storage_path,upload_status,idempotency_key,created_by)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,'test-file',10,?,?,UNHEX(SHA2(?,256)),?,'AVAILABLE',
                  UUID_TO_BIN(?),UUID_TO_BIN(?))
                """,attachment,tenant,business,type,media,attachment,attachment,id(),actor.toString());
    }

    private void verifySampling(RoleManagement.Context context, UUID role, long roleVersion, Cookie selected,
                                UUID tenant, String task, String foreignTask) throws Exception {
        String path = "/api/bulk-inbound-quality-orders:register-sample";
        String first = sample(task, 0, "Q-ONE", "S-ONE", null);
        mvc.perform(get(path).cookie(selected)).andExpect(status().isForbidden());
        mvc.perform(post(path).cookie(selected).with(csrf()).contentType("application/json").content(first))
                .andExpect(status().isForbidden());
        String resource = "warehousing.inboundquality";
        UUID permission = UUID.fromString(jdbc.queryForObject(
                "SELECT BIN_TO_UUID(id) FROM access_permission WHERE code=?", String.class, resource + ":register_sample"));
        var samplingGrant = permissions.replaceAuthorization(context, role, roleVersion, Set.of(permission),
                Set.of(new RolePermissionManagement.DataPolicy(permission, DataScopeAuthorization.ScopeType.ALL,
                        resource, Set.of())), "独立质检授权");
        mvc.perform(post(path).cookie(selected).with(csrf()).contentType("application/json")
                        .content(sample(foreignTask, 0, "Q-FOREIGN", "S-FOREIGN", null)))
                .andExpect(status().isNotFound());
        String choices = mvc.perform(get(path).cookie(selected).param("taskNo", task))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].taskId").value(task)).andExpect(jsonPath("$[0].taskNo").value(task))
                .andExpect(jsonPath("$[0].taskVersion").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(choices).doesNotContain("PRIVATE-PLATE", "PRIVATE-DRIVER", "supplierId", "ownerId", "waybillNo");
        mvc.perform(get(path).cookie(selected).param("taskNo", foreignTask))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(path).cookie(selected).param("taskNo", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/bulk-inbound-metering-orders:record-gross").cookie(selected))
                .andExpect(status().isForbidden());
        String result = mvc.perform(post(path).cookie(selected).with(csrf()).contentType("application/json").content(first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.qualityVersion").value(1))
                .andExpect(jsonPath("$.taskVersion").value(1))
                .andReturn().getResponse().getContentAsString();
        String quality = com.jayway.jsonpath.JsonPath.read(result, "$.qualityId");
        mvc.perform(post(path).cookie(selected).with(csrf()).contentType("application/json").content(first))
                .andExpect(status().isConflict());
        String decisionPath = "/api/bulk-inbound-quality-orders/" + quality + ":decide";
        String labInput = "{\"taskId\":\"" + task + "\",\"taskVersion\":1,\"qualityRowVersion\":0,\"metrics\":{\"moisture\":12.5}}";
        mvc.perform(post(decisionPath).cookie(selected).with(csrf()).contentType("application/json").content(labInput))
                .andExpect(status().isForbidden());
        UUID decidePermission = UUID.fromString(jdbc.queryForObject(
                "SELECT BIN_TO_UUID(id) FROM access_permission WHERE code=?", String.class, resource + ":decide_quality"));
        var labGrant = permissions.replaceAuthorization(context, role, samplingGrant.roleVersion(), Set.of(permission, decidePermission),
                Set.of(new RolePermissionManagement.DataPolicy(permission, DataScopeAuthorization.ScopeType.ALL,
                                resource, Set.of()),
                        new RolePermissionManagement.DataPolicy(decidePermission, DataScopeAuthorization.ScopeType.ALL,
                                resource, Set.of())), "独立判定授权");
        mvc.perform(post(decisionPath).cookie(selected).with(csrf()).contentType("application/json")
                        .content(labInput.replace("moisture", "unconfigured")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INBOUND_LAB_METRICS_INVALID"));
        mvc.perform(post(decisionPath).cookie(selected).with(csrf()).contentType("application/json").content(labInput))
                .andExpect(status().isOk()).andExpect(jsonPath("$.decision").value("PASS"))
                .andExpect(jsonPath("$.taskVersion").value(2)).andExpect(jsonPath("$.qualityRowVersion").value(1));
        mvc.perform(post(decisionPath).cookie(selected).with(csrf()).contentType("application/json").content(labInput))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(result_snapshot,'$.rules.moisture.unit')) FROM bw_quality_order
                WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                """, String.class, tenant.toString(), quality)).isEqualTo("pct");
        mvc.perform(post(path).cookie(selected).with(csrf()).contentType("application/json")
                        .content(sample(task, 2, "Q-TWO", "S-ONE", quality)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INBOUND_SAMPLE_IDENTIFIER_CONFLICT"));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM bw_quality_order WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND is_active=1
                """, Integer.class, tenant.toString(), quality)).isEqualTo(1);
        mvc.perform(post(path).cookie(selected).with(csrf()).contentType("application/json")
                        .content(sample(task, 2, "Q-TWO", "S-TWO", quality)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.qualityVersion").value(2))
                .andExpect(jsonPath("$.taskVersion").value(3));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM bw_quality_order WHERE tenant_id=UUID_TO_BIN(?) AND task_id=UUID_TO_BIN(?)
                """, Integer.class, tenant.toString(), task)).isEqualTo(2);
        verifyExternalMetering(context, role, labGrant.roleVersion(), selected, tenant, task, permission);
    }

    private void verifyExternalMetering(RoleManagement.Context context, UUID role, long roleVersion,
                                        Cookie selected, UUID tenant, String task, UUID samplePermission) throws Exception {
        String path = "/api/bulk-inbound-metering-orders:register-external";
        String first = externalNet(task, 3, "M-FIRST", "RAIL-ONE");
        mvc.perform(post(path).cookie(selected).with(csrf()).contentType("application/json").content(first))
                .andExpect(status().isForbidden());
        String resource = "warehousing.inboundmetering";
        UUID permission = UUID.fromString(jdbc.queryForObject(
                "SELECT BIN_TO_UUID(id) FROM access_permission WHERE code=?", String.class, resource + ":register_external"));
        permissions.replaceAuthorization(context, role, roleVersion, Set.of(samplePermission, permission),
                Set.of(new RolePermissionManagement.DataPolicy(permission, DataScopeAuthorization.ScopeType.ALL,
                                resource, Set.of()),
                        new RolePermissionManagement.DataPolicy(samplePermission, DataScopeAuthorization.ScopeType.ALL,
                                "warehousing.inboundquality", Set.of())), "独立计量授权");
        String other = seed(tenant, "WAIT_QUALITY", "ISSUED");
        mvc.perform(get(path).cookie(selected).param("taskNo", other))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(path).cookie(selected).param("taskNo", task))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].taskId").value(task))
                .andExpect(jsonPath("$[0].taskVersion").value(3));
        mvc.perform(post(path).cookie(selected).with(csrf()).contentType("application/json")
                        .content(externalNet(other, 0, "M-OTHER", "RAIL-ONE")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SAMPLE_REQUIRED"));
        mvc.perform(post(path).cookie(selected).with(csrf()).contentType("application/json").content(first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.taskVersion").value(4))
                .andExpect(jsonPath("$.netKg").value("1250.1250"));
        assertThat(jdbc.queryForObject("""
                SELECT queue_stage FROM bw_inbound_task WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                """, String.class, tenant.toString(), task)).isEqualTo("WAIT_RECEIPT");
        mvc.perform(post("/api/bulk-inbound-quality-orders:register-sample").cookie(selected).with(csrf())
                        .contentType("application/json").content(sample(other, 0, "Q-OTHER", "S-OTHER", null)))
                .andExpect(status().isOk());
        mvc.perform(post(path).cookie(selected).with(csrf()).contentType("application/json")
                        .content(externalNet(other, 1, "M-OTHER", "rail-one")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INBOUND_METER_IDENTIFIER_CONFLICT"));
        assertThat(jdbc.queryForObject("""
                SELECT row_version FROM bw_inbound_task WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                """, Long.class, tenant.toString(), other)).isEqualTo(1L);
    }

    private static String externalNet(String task, long version, String number, String reference) {
        return "{\"taskId\":\"" + task + "\",\"taskVersion\":" + version + ",\"meteringNo\":\"" + number
                + "\",\"source\":\"RAIL_SCALE\",\"externalReference\":\"" + reference + "\",\"netKg\":\"1250.1250\"}";
    }

    private static String sample(String task, long version, String qualityNo, String barcode, String priorId) {
        String prior = priorId == null ? "" : ",\"priorQualityId\":\"" + priorId + "\",\"priorRowVersion\":1";
        return "{\"taskId\":\"" + task + "\",\"taskVersion\":" + version + ",\"qualityNo\":\"" + qualityNo
                + "\",\"barcode\":\"" + barcode + "\"" + prior + "}";
    }

    private UUID account() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity_account(id,username,display_name,password_hash)
                VALUES(UUID_TO_BIN(?),?,'Queue test','unused')
                """, id.toString(), "queue-" + id);
        return id;
    }

    private String seed(UUID tenant, String stage, String noticeStatus) {
        String key = tenant.toString(), template = id(), notice = id(), task = id(), reference = id();
        jdbc.update("""
                INSERT INTO bw_inbound_notice(tenant_id,id,notice_no,warehouse_id,owner_id,flow_version,status,driver_name)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),UUID_TO_BIN(?),2,?,'PRIVATE-DRIVER')
                """, key, notice, notice, reference, reference, noticeStatus);
        jdbc.update("""
                INSERT INTO bw_commodity_template(tenant_id,id,template_code,version,status,
                  metering_strategy,quality_gate_strategy,deduction_strategy,stock_granularity,definition)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,1,'PUBLISHED','SCALE_OR_EXTERNAL','SAMPLED','ZERO','BULK',
                  JSON_OBJECT('qualityMetrics',JSON_OBJECT('moisture',JSON_OBJECT('unit','pct',
                    'minimum',0,'maximum',100,'acceptMinimum',0,'acceptMaximum',14))))
                """, key, template, template);
        jdbc.update("""
                INSERT INTO bw_inbound_task(tenant_id,id,task_no,notice_id,template_id,vehicle_identity_type,
                  vehicle_plate,status,queue_stage,stage_started_at)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),UUID_TO_BIN(?),'PLATED',
                  'PRIVATE-PLATE','WORKING',?,CURRENT_TIMESTAMP(6))
                """, key, task, task, notice, template, stage);
        return task;
    }

    private static String id() { return UUID.randomUUID().toString(); }
}
