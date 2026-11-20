package com.appnexus.platform;

import com.appnexus.platform.organization.OrganizationStructure;
import com.appnexus.platform.appapi.organization.OrganizationDomainController;
import com.appnexus.platform.appapi.organization.OrganizationDomainErrors;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 仅验证真实 MVC 异常转换链；授权与数据库测试由独立集成测试承担。 */
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
