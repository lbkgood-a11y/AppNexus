package com.appnexus.poc.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.appnexus.poc.PocApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = PocApplication.class)
@AutoConfigureMockMvc
class OpenApiSurfaceIsolationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void appContractDoesNotExposePartnerOrPlatformPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs/app").with(user("poc-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/organizations']").exists())
                .andExpect(jsonPath("$.paths['/v1/organizations']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/tenants']").doesNotExist());
    }

    @Test
    void partnerContractOnlyExposesVersionedPartnerResources() throws Exception {
        mockMvc.perform(get("/v3/api-docs/partner-v1").with(user("poc-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/v1/organizations']").exists())
                .andExpect(jsonPath("$.paths['/api/organizations']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/tenants']").doesNotExist());
    }

    @Test
    void platformContractDoesNotLeakIntoOtherSurfaces() throws Exception {
        mockMvc.perform(get("/v3/api-docs/platform").with(user("poc-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/tenants']").exists())
                .andExpect(jsonPath("$.paths['/v1/organizations']").doesNotExist());
    }
}
