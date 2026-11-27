package com.appnexus.platform;

import com.appnexus.masterdata.organization.OrganizationStructure;
import com.appnexus.masterdata.adapter.web.organization.OrganizationDomainController;
import com.appnexus.masterdata.adapter.web.organization.OrganizationDomainErrors;
import com.appnexus.masterdata.adapter.web.organization.OrganizationTypeDeclaration;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.security.EndpointOperationInterceptor;
import com.appnexus.platform.security.operation.OperationRegistry;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 验证真实 MVC 异常转换和对象声明的功能准入，数据库事务由独立集成测试覆盖。 */
class OrganizationErrorHttpTest {
    @Test void duplicateKeyReturnsConflictInsteadOfEscapingAdvice() throws Exception {
        verifyError(new DuplicateKeyException("sensitive database detail"), 409, "ORGANIZATION_CODE_CONFLICT");
    }
    @Test void concurrentChangeReturnsStableConflictCode() throws Exception {
        verifyError(new IllegalStateException("CONCURRENT_MODIFICATION"), 409, "ORGANIZATION_CHANGED");
    }
    @Test void missingAndInvalidInputsKeepTheirHttpSemantics() throws Exception {
        verifyError(new java.util.NoSuchElementException("internal id"), 404, "ORGANIZATION_OBJECT_NOT_FOUND");
        verifyError(new IllegalArgumentException("sensitive payload"), 400, "INVALID_ORGANIZATION_INPUT");
    }
    @Test void rangeDenialReturnsForbiddenInsteadOfServerError() throws Exception {
        verifyError(new SecurityException("internal range detail"), 403, "ORGANIZATION_DATA_ACCESS_DENIED");
    }
    /** 目录和端点从同一 Provider 取得权限，拒绝时不能调用业务读取。 */
    @Test void organizationTypePermissionComesFromRegisteredObject() throws Exception {
        var domain = mock(OrganizationStructure.class);
        var authorization = mock(AccessAuthorization.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OperationRegistry> registryProvider = mock(ObjectProvider.class);
        var mvc = MockMvcBuilders.standaloneSetup(new OrganizationDomainController(domain))
            .addInterceptors(new EndpointOperationInterceptor(authorization, registryProvider)).build();
        var mappings = mvc.getDispatcherServlet().getWebApplicationContext().getBean(RequestMappingHandlerMapping.class);
        // 独立 MVC 只装配本对象，其余控制器方法仍需原 Provider 才能注册。
        var registered = mock(RequestMappingHandlerMapping.class);
        var typeMappings = new java.util.LinkedHashMap<>(mappings.getHandlerMethods());
        typeMappings.entrySet().removeIf(entry -> entry.getKey().getPatternValues().stream()
            .noneMatch(path -> path.startsWith("/api/organization/domain/types")));
        when(registered.getHandlerMethods()).thenReturn(typeMappings);
        var registry = new OperationRegistry(registered, List.of(new OrganizationTypeDeclaration()));
        when(registryProvider.getObject()).thenReturn(registry);
        assertThat(registry.all()).extracting(OperationRegistry.Definition::id)
            .containsExactly("OrganizationType.create", "OrganizationType.list", "OrganizationType.update");

        var context = new VerifiedTenantContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, null, null);
        var subject = AccessAuthorization.Subject.tenant(context.accountId(), context.tenantId(), context.memberId());
        mvc.perform(get("/api/organization/domain/types"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TENANT_REQUIRED"));
        mvc.perform(get("/api/organization/domain/types").requestAttr(VerifiedTenantContext.class.getName(), context))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("OPERATION_NOT_AUTHORIZED"));
        verifyNoInteractions(domain);

        when(authorization.hasPermission(subject, "organization.unit:read")).thenReturn(true);
        when(domain.types()).thenReturn(List.of());
        mvc.perform(get("/api/organization/domain/types").requestAttr(VerifiedTenantContext.class.getName(), context))
            .andExpect(status().isOk());
        verify(domain).types();
    }

    /** 在真实控制器读取路径触发领域异常，检查 HTTP 状态、稳定码和禁止缓存契约。 */
    private void verifyError(RuntimeException failure, int status, String code) throws Exception {
        var domain = mock(OrganizationStructure.class);
        when(domain.types()).thenThrow(failure);
        var mvc = MockMvcBuilders.standaloneSetup(new OrganizationDomainController(domain))
            .setControllerAdvice(new OrganizationDomainErrors()).build();
        mvc.perform(get("/api/organization/domain/types"))
            .andExpect(status().is(status)).andExpect(jsonPath("$.status").value(status))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(failure.getMessage()))));
    }
}
