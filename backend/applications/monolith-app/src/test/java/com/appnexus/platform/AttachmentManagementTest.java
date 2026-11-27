package com.appnexus.platform;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap;
import com.appnexus.platform.configuration.port.AttachmentBlobStorage;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import javax.imageio.ImageIO;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.AdditionalMatchers.aryEq;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 真实 MySQL、Valkey 会话与统一权限链验证附件策略及本人和跨租户访问边界。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {"server.port=${attachment.browser.port:0}", "app.public-url=http://localhost:18891", "app.frontend-url=http://localhost:18891"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
class AttachmentManagementTest extends DatabaseTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired OrganizationAdministratorBootstrap tenants;
    @MockitoBean AttachmentBlobStorage storage;

    private static final ServerSocket SCANNER = scanner();

    /** 隔离测试协议端，逐块验证线上扫描客户端的传输格式。 */
    @DynamicPropertySource
    static void scannerProperties(DynamicPropertyRegistry properties) {
        properties.add("app.attachment.scan.host", () -> "127.0.0.1");
        properties.add("app.attachment.scan.port", SCANNER::getLocalPort);
        properties.add("app.public-url", () -> "http://localhost:18891");
        properties.add("app.frontend-url", () -> "http://localhost:18891");
    }

    private static ServerSocket scanner() {
        try {
            var server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
            Thread.ofVirtual().name("attachment-scan-fixture").start(() -> {
                while (!server.isClosed()) {
                    try (var socket = server.accept(); var input = new DataInputStream(socket.getInputStream())) {
                        byte[] command = input.readNBytes(10);
                        if (!new String(command, StandardCharsets.US_ASCII).equals("zINSTREAM\0")) continue;
                        for (int size; (size = input.readInt()) > 0;) input.skipNBytes(size);
                        socket.getOutputStream().write("stream: OK\0".getBytes(StandardCharsets.US_ASCII));
                    } catch (IOException failure) {
                        if (server.isClosed()) return;
                    }
                }
            });
            return server;
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @org.junit.jupiter.api.AfterAll
    static void stopScanner() throws IOException {
        SCANNER.close();
    }

    /** 策略修改只影响当前租户的列表和上传，台账、本人查询和计数均隔离。 */
    @Test void checksPolicyLedgerAndTenantEntry() throws Exception {
        var first = tenantWithMember();
        var second = tenantWithMember();
        var outsider = UUID.randomUUID().toString();
        insertAccount(outsider);
        String business = "ATTACH-" + UUID.randomUUID();
        insertAttachment(first, business);
        insertAttachment(second, business);
        var firstCookie = tenantCookie(first);
        var secondCookie = tenantCookie(second);
        String unprivilegedMember = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",
                unprivilegedMember, first.tenant(), outsider);
        var unprivileged = tenantCookie(new TestOwner(first.tenant(), outsider, unprivilegedMember));
        String rule = "/api/configuration/attachment-policies";
        String ledger = "/api/configuration/attachment-ledger";

        mvc.perform(get(rule)).andExpect(status().isUnauthorized());
        mvc.perform(get(ledger).with(authenticated(outsider, 0))).andExpect(status().isConflict());
        mvc.perform(get(ledger).cookie(unprivileged).header("X-Tenant-Context-Version", "1"))
                .andExpect(status().isForbidden());
        mvc.perform(get(rule).cookie(unprivileged).header("X-Tenant-Context-Version", "1"))
                .andExpect(status().isForbidden());
        mvc.perform(get(rule).cookie(firstCookie).header("X-Tenant-Context-Version", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(8));
        String body = "{\"enabled\":false,\"maxBytes\":10000,\"version\":0}";
        mvc.perform(put(rule + "/png").cookie(firstCookie).header("X-Tenant-Context-Version", "1")
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put(rule + "/png").cookie(firstCookie).header("X-Tenant-Context-Version", "1").with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(put(rule + "/png").cookie(firstCookie).header("X-Tenant-Context-Version", "1").with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(get(rule).cookie(secondCookie).header("X-Tenant-Context-Version", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[4].typeKey").value("png"))
                .andExpect(jsonPath("$[4].version").value(0)).andExpect(jsonPath("$[4].enabled").value(true));
        mvc.perform(get("/api/configuration/attachments/rules").cookie(firstCookie).header("X-Tenant-Context-Version", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].typeKey",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("png"))));
        mvc.perform(get("/api/configuration/attachments/rules").cookie(secondCookie).header("X-Tenant-Context-Version", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].typeKey", org.hamcrest.Matchers.hasItem("png")));
        mvc.perform(get(ledger).param("businessId", business).param("tenantId", second.tenant())
                .cookie(firstCookie).header("X-Tenant-Context-Version", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].tenantId").value(first.tenant()))
                .andExpect(jsonPath("$.rows[0].storagePath").doesNotExist());
        mvc.perform(get("/api/configuration/attachments").param("businessId", business)
                .cookie(tenantCookie(first)).header("X-Tenant-Context-Version", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].tenantId").value(first.tenant()))
                .andExpect(jsonPath("$.rows[0].storagePath").doesNotExist());
        mvc.perform(get("/api/configuration/attachments").param("businessId", business)
                .cookie(tenantCookie(second)).header("X-Tenant-Context-Version", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].tenantId").value(second.tenant()));
        mvc.perform(post("/api/configuration/attachments").cookie(tenantCookie(first))
                .header("X-Tenant-Context-Version", "1").with(csrf())
                .header("Idempotency-Key", UUID.randomUUID()).header("X-Attachment-Business-Id", business)
                .header("X-Attachment-Name", "invalid.png").contentType("application/octet-stream")
                .content("not a PNG")).andExpect(status().isBadRequest());
    }

    /** 同一附件 UUID 在其他租户中不可读，非可用附件及不存在的 UUID 返回 404。 */
    @Test void downloadsOnlyAvailableAttachmentsInCurrentTenant() throws Exception {
        var owner = tenantWithMember();
        var other = tenantWithMember();
        var cookie = tenantCookie(owner);
        String business = "DOWNLOAD-" + UUID.randomUUID();
        String id = insertAttachment(owner, business);
        String failed = insertAttachment(owner, business);
        jdbc.update("UPDATE platform_attachment SET upload_status='FAILED' WHERE id=UUID_TO_BIN(?)", failed);
        byte[] bytes = { 1, 2, 3 };
        when(storage.open("attachment-test/" + id)).thenReturn(new ByteArrayInputStream(bytes));
        String route = "/api/configuration/attachment-ledger/{id}/download";

        mvc.perform(get(route, id)).andExpect(status().isUnauthorized());
        mvc.perform(get(route, id).cookie(tenantCookie(other)).header("X-Tenant-Context-Version", "1")).andExpect(status().isNotFound());
        mvc.perform(get(route, failed).cookie(cookie).header("X-Tenant-Context-Version", "1")).andExpect(status().isNotFound());
        mvc.perform(get(route, UUID.randomUUID()).cookie(cookie).header("X-Tenant-Context-Version", "1")).andExpect(status().isNotFound());
        mvc.perform(get(route, id).cookie(cookie).header("X-Tenant-Context-Version", "1")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().contentType("application/octet-stream"))
                .andExpect(content().bytes(bytes));
        verify(storage).open("attachment-test/" + id);
    }

    /** 成功请求提交可见资料，重放不重复写原件，内容或关联变更按冲突处理。 */
    @Test void storesOneObjectForTheSameUpload() throws Exception {
        when(storage.writePath(org.mockito.ArgumentMatchers.any(UUID.class), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> "attachments/" + call.getArgument(0) + "/" + call.getArgument(1));
        var owner = tenantWithMember();
        byte[] png;
        try (var bytes = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
            png = bytes.toByteArray();
        }
        var key = UUID.randomUUID();
        String business = "UPLOAD-" + UUID.randomUUID();
        var cookie = tenantCookie(owner);
        var request = post("/api/configuration/attachments").cookie(cookie)
                .header("X-Tenant-Context-Version", "1").with(csrf())
                .header("X-Attachment-Name", "upload.png").header("Idempotency-Key", key)
                .header("X-Attachment-Business-Id", business).contentType("application/octet-stream").content(png);
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.sizeBytes").value(png.length))
                .andExpect(jsonPath("$.storagePath").doesNotExist());
        mvc.perform(request).andExpect(status().isOk());
        var storedPath = jdbc.queryForObject("SELECT storage_path FROM platform_attachment WHERE idempotency_key=UUID_TO_BIN(?)",
                String.class, key.toString());
        verify(storage).put(eq(storedPath), aryEq(png), eq("image/png"));
        mvc.perform(get("/api/configuration/attachments").param("businessId", business).cookie(cookie)
                .header("X-Tenant-Context-Version", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(post("/api/configuration/attachments").cookie(cookie)
                .header("X-Tenant-Context-Version", "1").with(csrf())
                .header("X-Attachment-Name", "upload.png").header("Idempotency-Key", key)
                .header("X-Attachment-Business-Id", business + "-changed")
                .contentType("application/octet-stream").content(png)).andExpect(status().isConflict());
    }

    /** 只在显式验收开关下，保留隔离数据库与真实 HTTP 宿主供浏览器验证。 */
    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "attachment.browser.qa", matches = "true")
    void servesBrowserFixture() throws Exception {
        when(storage.writePath(org.mockito.ArgumentMatchers.any(UUID.class), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> "attachments/" + call.getArgument(0) + "/" + call.getArgument(1));
        String admin = jdbc.queryForObject(
                "SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'", String.class);
        var owner = tenantWithMember(admin);
        // 隔离验收只发布本次变更的菜单，以兼容另一个工作树的前端路由快照。
        jdbc.update("UPDATE platform_menu SET enabled=FALSE WHERE type='MENU' AND component_key NOT IN "
                + "('configuration/attachments','configuration/attachment-policies')");
        String business = "BROWSER-ATTACH-001";
        insertAttachment(owner, business);
        Path directory = Path.of(System.getProperty("attachment.browser.dir"));
        Files.createDirectories(directory);
        Path cookie = directory.resolve("cookie.private");
        try {
            Files.writeString(cookie, tenantCookie(owner).getValue());
            long deadline = System.nanoTime() + java.time.Duration.ofMinutes(8).toNanos();
            while (!Files.exists(directory.resolve("stop")) && System.nanoTime() < deadline) {
                Thread.sleep(500);
            }
        } finally {
            Files.deleteIfExists(cookie);
        }
    }

    private TestOwner tenantWithMember() {
        String account = UUID.randomUUID().toString();
        insertAccount(account);
        return tenantWithMember(account);
    }

    private TestOwner tenantWithMember(String account) {
        String tenant = UUID.randomUUID().toString();
        String member = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),?)", tenant, "附件测试-" + tenant);
        jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",
                member, tenant, account);
        tenants.initialize(UUID.fromString(tenant), UUID.fromString(member));
        jdbc.update("""
            INSERT IGNORE INTO access_role_permission(role_id,permission_id,scope_type,granted_by,tenant_id)
            SELECT mr.role_id,p.id,'TENANT',UUID_TO_BIN(?),mr.tenant_id FROM access_member_role mr JOIN access_permission p
            WHERE mr.tenant_id=UUID_TO_BIN(?) AND mr.member_id=UUID_TO_BIN(?) AND p.code IN
                ('configuration.attachment:read','configuration.attachment:upload')
            """, account, tenant, member);
        return new TestOwner(tenant, account, member);
    }

    private void insertAccount(String account) {
        jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'附件测试','unused')",
                account, "attach-" + account);
    }

    private String insertAttachment(TestOwner owner, String business) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO platform_attachment(id,tenant_id,business_id,file_name,size_bytes,type_key,media_type,
                content_sha256,storage_path,upload_status,idempotency_key,created_by,updated_by)
            VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,'sample.png',3,'png','image/png',UNHEX(SHA2('fixture',256)),
                ?,'AVAILABLE',UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))
            """, id, owner.tenant(), business, "attachment-test/" + id, id, owner.account(), owner.account());
        return id;
    }

    private Cookie tenantCookie(TestOwner owner) {
        var session = sessionRepository.createSession();
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        var principal = org.springframework.security.core.userdetails.User.withUsername(owner.account())
                .password("").authorities("ACCOUNT").build();
        context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated(principal, null, principal.getAuthorities()));
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        session.setAttribute("appnexus.credentialVersion", 0L);
        session.setAttribute("appnexus.tenant", owner.tenant());
        session.setAttribute("appnexus.member", owner.member());
        session.setAttribute("appnexus.tenantVersion", 1L);
        sessionRepository.save(session);
        return new Cookie("JSESSIONID", Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8)));
    }

    private record TestOwner(String tenant, String account, String member) {}
}
