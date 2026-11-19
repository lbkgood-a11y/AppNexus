package com.appnexus.platform;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 从正式 Controller 生成契约并验证接口面隔离，产物仅写 target，不能自动改写发布基线。 */
@SpringBootTest
@ActiveProfiles("local")
@AutoConfigureMockMvc
class ApiContractTest extends DatabaseTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MyBatisPlatformAdministration administration;
    private UUID actor;
    @BeforeEach void initialize(){administration.initializeAdministrator("admin-test");actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));}
    @Test void allThreeContractsHaveSeparatePathsAndCanBeExported()throws Exception{
        for(String group:List.of("app","platform","partner-v1")){
            String json=mvc.perform(get("/v3/api-docs/"+group).with(authenticated(actor.toString(),0))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
            Map<String,Object> contract=mapper.readValue(json,new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});
            @SuppressWarnings("unchecked") var paths=(Map<String,Object>)contract.get("paths");
            assertThat(paths).isNotEmpty();
            for(String path:paths.keySet()){
                if(group.equals("partner-v1"))assertThat(path).startsWith("/v1/");
                else if(group.equals("platform"))assertThat(path).startsWith("/bff/platform/");
                else assertThat(path).doesNotStartWith("/v1/").doesNotStartWith("/bff/platform/");
            }
            if(group.equals("partner-v1"))assertThat(json).doesNotContain("passwordHash","createdBy","actorAccountId","authorityVersion","encryptedSecret");
            assertThat(contract.get("security")).isNotNull();
            var options=new org.yaml.snakeyaml.DumperOptions();options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
            var directory=Path.of("target","contracts");Files.createDirectories(directory);
            Files.writeString(directory.resolve("openapi-"+group+".yaml"),"# 从正式 Controller 生成，禁止手工修改；权限说明见专项设计。\n"+new org.yaml.snakeyaml.Yaml(options).dump(contract));
        }
    }
    @Test void contractMetadataCannotBeReadByAnonymousOrOrdinaryAccount()throws Exception{
        mvc.perform(get("/v3/api-docs/platform")).andExpect(status().isUnauthorized());
        UUID ordinary=UUID.randomUUID();jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'Contract test','unused')",ordinary.toString(),"contract-"+ordinary);
        mvc.perform(get("/v3/api-docs/platform").with(authenticated(ordinary.toString(),0))).andExpect(status().isForbidden());
    }
}
