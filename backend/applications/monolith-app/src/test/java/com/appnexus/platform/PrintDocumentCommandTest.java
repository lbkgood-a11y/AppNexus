package com.appnexus.platform;

import com.appnexus.contracts.businessobject.BusinessObjectExecution;
import com.appnexus.contracts.printing.PrintContext;
import com.appnexus.platform.authorization.RoleManagement;
import com.appnexus.platform.authorization.RolePermissionManagement;
import com.appnexus.platform.authorization.SubjectRoleManagement;
import com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap;
import com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration;
import com.appnexus.printing.api.PrintData.DocumentRequest;
import com.appnexus.printing.api.PrintData.SceneRequest;
import com.appnexus.printing.api.PrintData.TemplateDraft;
import com.appnexus.printing.api.PrintSceneManagement;
import com.appnexus.printing.application.PrintRuntimeService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用真实角色、HTTP 会话、场景及 MySQL 验证本人单据的公共打印命令。 */
@SpringBootTest
@ActiveProfiles("local")
@AutoConfigureMockMvc
class PrintDocumentCommandTest extends DatabaseTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PrintRuntimeService templates;
    @Autowired PrintSceneManagement scenes;
    @Autowired OrganizationAdministratorBootstrap bootstrap;
    @Autowired MyBatisPlatformAdministration administration;
    @Autowired RoleManagement roles;
    @Autowired RolePermissionManagement permissions;
    @Autowired SubjectRoleManagement subjects;
    private final ObjectMapper json=new ObjectMapper();

    /** 已保存单据选择真实场景和契约，缺绑定、配置不完整或无法访问时不留下打印任务。 */
    @Test void commandReusesSavedDocumentSceneAndOwnScope()throws Exception{
        UUID tenant=UUID.randomUUID(),foreign=UUID.randomUUID(),owner=account(),other=account();
        UUID otherMember=UUID.randomUUID();
        for(UUID id:List.of(tenant,foreign)){
            jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'打印命令验收')",id.toString());
            UUID ownerMember=UUID.randomUUID();
            addMember(id,ownerMember,owner);
            bootstrap.initialize(id,ownerMember);
        }
        addMember(tenant,otherMember,other);
        var ownerCookie=select(owner,tenant);
        var foreignCookie=select(owner,foreign);
        var otherCookie=select(other,tenant);
        var context=new PrintContext(tenant.toString(),owner.toString(),id());
        String contract="saved_ticket_v1";
        scenes.createContract(context,Map.of("code",contract,"nameZh","已保存单据","nameEn","Saved document",
            "fields",List.of(Map.of("key","number","labelZh","编号","labelEn","Number","sample","SAMPLE"))));
        String template=templates.saveTemplate(context,null,new TemplateDraft("saved_ticket","已保存单据","A4",
            contract,Map.of(),Map.of("panels",List.of()),-1)).get("id").toString();
        templates.publish(context,template,0);
        String scene=scenes.saveScene(context,null,new SceneRequest("saved_ticket","单据场景",contract,template,1,true,-1))
            .getFirst().get("id").toString();
        String document=scenes.saveDocument(context,new DocumentRequest(scene,"TICKET-1",Map.of("number","SERVER-FACT")))
            .getFirst().get("id").toString();
        var options=Map.<String,Object>of("outputType","PDF","copies",1);

        mvc.perform(command(otherCookie,document,0,id(),options)).andExpect(status().isForbidden());
        grantSubmit(tenant,otherMember);
        otherCookie=select(other,tenant);
        for(Cookie outsider:List.of(otherCookie,foreignCookie))
            mvc.perform(command(outsider,document,0,id(),options)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRINT_BUSINESS_OBJECT_NOT_FOUND"));
        mvc.perform(command(ownerCookie,document,0,id(),null)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BUSINESS_OBJECT_COMMAND_REQUIRED"));
        mvc.perform(command(ownerCookie,document,0,id(),Map.of("outputType","PDF","copies",1,"sceneCode","other")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BUSINESS_OBJECT_COMMAND_INVALID"));
        mvc.perform(command(ownerCookie,document,1,id(),options)).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("BUSINESS_OBJECT_VERSION_CONFLICT"));
        mvc.perform(command(ownerCookie,document,0,id(),Map.of("outputType","PRINTER","copies",1)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRINT_DEVICE_REQUIRED"));
        assertThat(jobs(tenant)).isZero();

        String key=id();
        var response=mvc.perform(command(ownerCookie,document,0,key,options)).andExpect(status().isOk())
            .andReturn().getResponse();
        String job=json.readTree(response.getContentAsString()).path("output").path("result").path("id").asText();
        mvc.perform(command(ownerCookie,document,0,key,options)).andExpect(status().isOk())
            .andExpect(jsonPath("$.output.result.id").value(job));
        mvc.perform(post("/api/printing/documents/"+document+"/print").cookie(ownerCookie).with(csrf())
            .header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsString(options)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(job));
        assertThat(jobs(tenant)).isEqualTo(1);
        var row=jdbc.queryForMap("""
            SELECT source_id,BIN_TO_UUID(template_id) AS template_id,CAST(data_snapshot AS CHAR) AS snapshot,
                   JSON_UNQUOTE(JSON_EXTRACT(data_contract_schema,'$.code')) AS contract
            FROM print_job WHERE id=UUID_TO_BIN(?)
            """,job);
        assertThat(row).containsEntry("source_id",document).containsEntry("template_id",template).containsEntry("contract",contract);
        assertThat(json.readTree(row.get("snapshot").toString())).isEqualTo(json.createObjectNode().put("number","SERVER-FACT"));

        scenes.saveScene(context,scene,new SceneRequest("saved_ticket","单据场景",contract,template,1,false,0));
        mvc.perform(command(ownerCookie,document,0,id(),options)).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PRINT_SCENE_UNAVAILABLE"));
        scenes.saveScene(context,scene,new SceneRequest("saved_ticket","单据场景",contract,template,1,true,1));
        // 模拟配置缺失的隔离数据，证明不会使用历史模板的旧快照绕过契约存在检查。
        jdbc.update("DELETE FROM print_data_contract WHERE tenant_id=UUID_TO_BIN(?) AND code=?",tenant.toString(),contract);
        mvc.perform(command(ownerCookie,document,0,id(),options)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PRINT_CONTRACT_INVALID"));
        assertThat(jobs(tenant)).isEqualTo(1);
        assertThat(jobs(foreign)).isZero();
    }

    /** 真正通过公共和专用 HTTP 入口验证创建输出、确认预览及补打的快照和重放。 */
    @Test void outputAndReprintCommandsPreservePreviewAndFrozenJob()throws Exception{
        UUID tenant=UUID.randomUUID(),foreign=UUID.randomUUID(),owner=account(),unprivileged=account();
        for(UUID id:List.of(tenant,foreign)){
            jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'输出命令验收')",id.toString());
            var member=UUID.randomUUID();
            addMember(id,member,owner);
            bootstrap.initialize(id,member);
        }
        UUID unprivilegedMember=UUID.randomUUID();
        addMember(tenant,unprivilegedMember,unprivileged);
        Cookie operator=select(owner,tenant),outside=select(owner,foreign),denied=select(unprivileged,tenant);
        var context=new PrintContext(tenant.toString(),owner.toString(),id());
        String contract="output_ticket_v1";
        scenes.createContract(context,Map.of("code",contract,"nameZh","输出单据","nameEn","Output document",
            "fields",List.of(Map.of("key","number","labelZh","编号","labelEn","Number","sample","SAMPLE"))));
        String template=templates.saveTemplate(context,null,new TemplateDraft("output_ticket","输出验收","A4",
            contract,Map.of(),Map.of("panels",List.of()),-1)).get("id").toString();
        templates.publish(context,template,0);
        String scene=scenes.saveScene(context,null,new SceneRequest("output_ticket","输出验收",contract,template,1,true,-1))
            .getFirst().get("id").toString();
        String document=scenes.saveDocument(context,new DocumentRequest(scene,"OUT-1",Map.of("number","FROZEN-FACT")))
            .getFirst().get("id").toString();

        var options=Map.<String,Object>of("mode","PREVIEW","copies",3);
        String create="PrintOutput.create",confirm="PrintOutput.confirm@entry1",reprint="PrintJob.reprint";
        mvc.perform(printCommand(denied,"PrintOutput","create",create,document,id(),options))
            .andExpect(status().isForbidden());
        mvc.perform(printCommand(outside,"PrintOutput","create",create,document,id(),options))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRINT_BUSINESS_OBJECT_NOT_FOUND"));
        scenes.saveScene(context,scene,new SceneRequest("output_ticket","输出验收",contract,template,1,false,0));
        mvc.perform(printCommand(operator,"PrintOutput","create",create,document,id(),options))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRINT_SCENE_UNAVAILABLE"));
        assertThat(jobs(tenant)).isZero();
        scenes.saveScene(context,scene,new SceneRequest("output_ticket","输出验收",contract,template,1,true,1));

        String createKey=id();
        var created=mvc.perform(printCommand(operator,"PrintOutput","create",create,document,createKey,options))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result.mode").value("PREVIEW"))
            .andReturn().getResponse();
        var output=json.readTree(created.getContentAsString()).path("output").path("result");
        String outputId=output.path("id").asText(),preview=output.path("jobId").asText();
        mvc.perform(post("/api/printing/documents/"+document+"/outputs").cookie(operator).with(csrf())
            .header("Idempotency-Key",createKey).contentType("application/json").content(json.writeValueAsString(options)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(outputId));
        mvc.perform(printCommand(operator,"PrintOutput","create",create,document,createKey,Map.of("mode","PREVIEW","copies",2)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRINT_IDEMPOTENCY_CONFLICT"));

        mvc.perform(printCommand(operator,"PrintOutput","confirm",confirm,outputId,id(),Map.of()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRINT_PREVIEW_NOT_READY"));
        mvc.perform(printCommand(operator,"PrintOutput","confirm","PrintOutput.confirm",outputId,id(),Map.of()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BUSINESS_OBJECT_COMMAND_UNAVAILABLE"));
        mvc.perform(printCommand(denied,"PrintOutput","confirm",confirm,outputId,id(),Map.of()))
            .andExpect(status().isForbidden());
        grantSubmit(tenant,unprivilegedMember);
        var submitOnly=select(unprivileged,tenant);
        mvc.perform(printCommand(submitOnly,"PrintOutput","confirm",confirm,outputId,id(),Map.of()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PRINT_ACCESS_DENIED"));
        mvc.perform(printCommand(outside,"PrintOutput","confirm",confirm,outputId,id(),Map.of()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRINT_OUTPUT_NOT_FOUND"));

        // 在隔离数据库准备已渲染预览状态，不调用设备代理或输出字节端点。
        jdbc.update("UPDATE print_job SET state='SUCCEEDED' WHERE id=UUID_TO_BIN(?)",preview);
        jdbc.update("UPDATE print_document SET data_json=JSON_OBJECT('number','CHANGED-FACT') WHERE id=UUID_TO_BIN(?)",document);
        mvc.perform(printCommand(operator,"PrintOutput","confirm",confirm,outputId,id(),Map.of()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRINT_DEVICE_REQUIRED"));
        var deviceDraft=new com.appnexus.printing.api.PrintData.DeviceDraft(
            "command_device","命令设备","default","isolated-queue",Map.of(),true);
        String device=templates.saveDevice(context,null,deviceDraft).get("id").toString();
        jdbc.update("UPDATE print_device SET enabled=FALSE WHERE id=UUID_TO_BIN(?)",device);
        mvc.perform(printCommand(operator,"PrintOutput","confirm",confirm,outputId,id(),Map.of("deviceId",device)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRINT_DEVICE_DISABLED"));
        assertThat(jobs(tenant)).isEqualTo(1);
        jdbc.update("UPDATE print_device SET enabled=TRUE WHERE id=UUID_TO_BIN(?)",device);
        var confirmed=mvc.perform(printCommand(operator,"PrintOutput","confirm",confirm,outputId,id(),Map.of("deviceId",device)))
            .andExpect(status().isOk()).andReturn().getResponse();
        String printed=json.readTree(confirmed.getContentAsString()).path("output").path("result").path("confirmedJobId").asText();
        mvc.perform(post("/api/printing/outputs/"+outputId+"/confirm").cookie(operator).with(csrf())
            .header("Idempotency-Key",id()).contentType("application/json").content(json.writeValueAsString(Map.of("deviceId",device))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.confirmedJobId").value(printed));
        assertThat(jobs(tenant)).isEqualTo(2);
        assertCopy(printed,preview,"PRINTER",3);

        mvc.perform(printCommand(submitOnly,"PrintJob","reprint",reprint,printed,id(),Map.of("reason","复核")))
            .andExpect(status().isForbidden());
        mvc.perform(printCommand(outside,"PrintJob","reprint",reprint,printed,id(),Map.of("reason","复核")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRINT_JOB_NOT_FOUND"));
        mvc.perform(printCommand(operator,"PrintJob","reprint",reprint,printed,id(),Map.of("reason"," ")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRINT_REPRINT_REASON_REQUIRED"));
        mvc.perform(printCommand(operator,"PrintJob","reprint",reprint,printed,id(),
            Map.of("reason","尝试覆盖","data",Map.of("number","REQUEST-FACT"))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BUSINESS_OBJECT_COMMAND_INVALID"));
        String reprintKey=id();
        var copied=mvc.perform(printCommand(operator,"PrintJob","reprint",reprint,printed,reprintKey,Map.of("reason","复核补打")))
            .andExpect(status().isOk()).andReturn().getResponse();
        String copy=json.readTree(copied.getContentAsString()).path("output").path("result").path("id").asText();
        mvc.perform(post("/api/printing/jobs/"+printed+"/reprint").cookie(operator).with(csrf())
            .header("Idempotency-Key",reprintKey).contentType("application/json").content(json.writeValueAsString(Map.of("reason","复核补打"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(copy));
        assertCopy(copy,printed,"PRINTER",3);
        assertThat(jdbc.queryForObject("SELECT reprint_reason FROM print_job WHERE id=UUID_TO_BIN(?)",String.class,copy)).isEqualTo("复核补打");
        assertThat(jobs(tenant)).isEqualTo(3);
        assertThat(jobs(foreign)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM print_output_request WHERE tenant_id=UUID_TO_BIN(?)",
            Integer.class,tenant.toString())).isEqualTo(1);
    }

    /** 人员预检与状态走原查询及权限，全程不创建新的打印任务或输出。 */
    @Test void outputReadCommandsRetainReadOnlyAndPermissionBoundaries()throws Exception{
        UUID tenant=UUID.randomUUID(),foreign=UUID.randomUUID(),owner=account(),other=account(),otherMember=UUID.randomUUID();
        for(UUID id:List.of(tenant,foreign)){
            jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'打印只读命令')",id.toString());
            UUID member=UUID.randomUUID();
            addMember(id,member,owner);
            bootstrap.initialize(id,member);
        }
        addMember(tenant,otherMember,other);
        Cookie operator=select(owner,tenant),outside=select(owner,foreign),others=select(other,tenant);
        var context=new PrintContext(tenant.toString(),owner.toString(),id());
        String contract="output_read_v1";
        scenes.createContract(context,Map.of("code",contract,"nameZh","只读验收","nameEn","Read output",
            "fields",List.of(Map.of("key","number","labelZh","编号","labelEn","Number","sample","SAMPLE"))));
        String template=templates.saveTemplate(context,null,new TemplateDraft("read_ticket","只读验收","A4",
            contract,Map.of(),Map.of("panels",List.of()),-1)).get("id").toString();
        templates.publish(context,template,0);
        String scene=scenes.saveScene(context,null,new SceneRequest("read_ticket","只读验收",contract,template,1,true,-1))
            .getFirst().get("id").toString();
        String document=scenes.saveDocument(context,new DocumentRequest(scene,"READ-1",Map.of("number","SAVED")))
            .getFirst().get("id").toString();
        var seeded=mvc.perform(post("/api/printing/documents/"+document+"/outputs").cookie(operator).with(csrf())
            .header("Idempotency-Key",id()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("mode","PREVIEW","copies",1))))
            .andExpect(status().isOk()).andReturn().getResponse();
        String output=json.readTree(seeded.getContentAsString()).path("id").asText();
        String inspect="PrintOutput.inspect",statusEntry="PrintOutput.readStatus@personnel";
        var preview=Map.<String,Object>of("mode","PREVIEW");

        mvc.perform(printCommand(operator,"PrintOutput","inspect",inspect,document,id(),preview))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result[0].layer").value("SCENE_TEMPLATE"))
            .andExpect(jsonPath("$.output.result[0].status").value("READY"))
            .andExpect(jsonPath("$.output.result[2].status").value("NOT_APPLICABLE"));
        mvc.perform(printCommand(operator,"PrintOutput","readStatus",statusEntry,output,id(),Map.of()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result.id").value(output))
            .andExpect(jsonPath("$.output.result.mode").value("PREVIEW"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/printing/outputs/"+output)
            .cookie(operator)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(output));
        mvc.perform(printCommand(operator,"PrintOutput","readStatus","PrintOutput.readStatus",output,id(),Map.of()))
            .andExpect(status().isForbidden());
        mvc.perform(printCommand(others,"PrintOutput","inspect",inspect,document,id(),preview))
            .andExpect(status().isForbidden());
        mvc.perform(printCommand(others,"PrintOutput","readStatus",statusEntry,output,id(),Map.of()))
            .andExpect(status().isForbidden());
        mvc.perform(printCommand(outside,"PrintOutput","inspect",inspect,document,id(),preview))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRINT_DOCUMENT_NOT_FOUND"));
        mvc.perform(printCommand(outside,"PrintOutput","readStatus",statusEntry,output,id(),Map.of()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRINT_OUTPUT_NOT_FOUND"));
        mvc.perform(printCommand(operator,"PrintOutput","readStatus",statusEntry,output,id(),null))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BUSINESS_OBJECT_COMMAND_REQUIRED"));
        mvc.perform(printCommand(operator,"PrintOutput","inspect",inspect,document,id(),Map.of("mode","SILENT")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRINT_DEVICE_REQUIRED"));
        mvc.perform(printCommand(operator,"PrintOutput","inspect",inspect,document,id(),Map.of("mode","SILENT","deviceId",id())))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRINT_DEVICE_UNAVAILABLE"));

        grantPrintPermission(tenant,otherMember,"print.job:read");
        var readOnly=select(other,tenant);
        mvc.perform(printCommand(readOnly,"PrintOutput","readStatus",statusEntry,output,id(),Map.of()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result.id").value(output));
        mvc.perform(printCommand(readOnly,"PrintOutput","inspect",inspect,document,id(),preview))
            .andExpect(status().isForbidden());
        grantSubmit(tenant,otherMember);
        var submitOnly=select(other,tenant);
        mvc.perform(printCommand(submitOnly,"PrintOutput","inspect",inspect,document,id(),preview))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRINT_DOCUMENT_NOT_FOUND"));
        mvc.perform(printCommand(submitOnly,"PrintOutput","readStatus",statusEntry,output,id(),Map.of()))
            .andExpect(status().isForbidden());

        scenes.saveScene(context,scene,new SceneRequest("read_ticket","只读验收",contract,template,1,false,0));
        mvc.perform(printCommand(operator,"PrintOutput","inspect",inspect,document,id(),preview))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRINT_TARGET_INVALID"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .get("/api/printing/documents/"+document+"/preflight").cookie(operator).param("mode","PREVIEW"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("BLOCKED"));
        mvc.perform(printCommand(operator,"PrintOutput","readStatus",statusEntry,output,id(),Map.of()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result.id").value(output));
        assertThat(jobs(tenant)).isEqualTo(1);
        assertThat(jobs(foreign)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM print_output_request WHERE tenant_id=UUID_TO_BIN(?)",
            Integer.class,tenant.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_object_resolution WHERE tenant_id=?",
            Integer.class,tenant.toString())).isZero();
    }

    /** 用原权限、本人范围及已存在的场景契约验收单据创建和两个只读列表。 */
    @Test void documentAndJobListCommandsKeepOriginalWriteAndReadScopes()throws Exception{
        UUID tenant=UUID.randomUUID(),foreign=UUID.randomUUID(),owner=account(),other=account();
        UUID otherMember=UUID.randomUUID();
        for(UUID id:List.of(tenant,foreign)){
            jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'单据目录命令')",id.toString());
            UUID member=UUID.randomUUID();
            addMember(id,member,owner);
            bootstrap.initialize(id,member);
        }
        addMember(tenant,otherMember,other);
        Cookie operator=select(owner,tenant),outside=select(owner,foreign),unprivileged=select(other,tenant);
        var context=new PrintContext(tenant.toString(),owner.toString(),id());
        var outsideContext=new PrintContext(foreign.toString(),owner.toString(),id());
        String scene=catalogScene(context,"doc_commands");
        String foreignScene=catalogScene(outsideContext,"foreign_doc_commands");
        String docList="PrintDocument.list",create="PrintDocument.create",jobList="PrintJob.list";
        var first=Map.<String,Object>of("sceneId",scene,"number","OWNER-PRINT","data",Map.of("number","OWNER-FACT"));

        mvc.perform(printCommand(unprivileged,"PrintDocument","list",docList,null,id(),Map.of()))
            .andExpect(status().isForbidden());
        mvc.perform(printCommand(unprivileged,"PrintDocument","create",create,null,id(),first))
            .andExpect(status().isForbidden());
        mvc.perform(printCommand(unprivileged,"PrintJob","list",jobList,null,id(),Map.of()))
            .andExpect(status().isForbidden());
        mvc.perform(printCommand(operator,"PrintDocument","create",create,null,id(),
            Map.of("sceneId",foreignScene,"number","CROSS","data",Map.of("number","X"))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRINT_SCENE_UNAVAILABLE"));
        mvc.perform(printCommand(operator,"PrintDocument","create",create,null,id(),
            Map.of("sceneId",scene,"number","INVALID","data",Map.of("unknown","X"))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRINT_CONTRACT_INVALID"));
        assertThat(documents(tenant)).isZero();
        assertThat(documents(foreign)).isZero();

        var created=mvc.perform(printCommand(operator,"PrintDocument","create",create,null,id(),first))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result[0].number").value("OWNER-PRINT"))
            .andReturn().getResponse();
        String document=json.readTree(created.getContentAsString()).path("output").path("result").get(0).path("id").asText();
        assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(created_by) FROM print_document WHERE id=UUID_TO_BIN(?)",
            String.class,document)).isEqualTo(owner.toString());
        mvc.perform(printCommand(operator,"PrintDocument","create",create,null,id(),first))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRINT_DUPLICATE"));
        assertThat(documents(tenant)).isEqualTo(1);
        assertThat(jobs(tenant)).isZero();

        grantSubmit(tenant,otherMember);
        var submit=select(other,tenant);
        mvc.perform(post("/api/printing/documents").cookie(submit).with(csrf()).header("Idempotency-Key",id())
            .contentType("application/json").content(json.writeValueAsString(Map.of(
                "sceneId",scene,"number","OTHER-PRINT","data",Map.of("number","OTHER-FACT")))))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].number").value("OTHER-PRINT"));
        String foreignDocument=scenes.saveDocument(outsideContext,
            new DocumentRequest(foreignScene,"FOREIGN-PRINT",Map.of("number","FOREIGN-FACT")))
            .getFirst().get("id").toString();
        var options=new com.appnexus.printing.api.PrintData.DocumentPrintRequest(null,"PDF",1);
        String ownJob=scenes.print(context,document,options).get("id").toString();
        String foreignJob=scenes.print(outsideContext,foreignDocument,options).get("id").toString();

        mvc.perform(printCommand(operator,"PrintDocument","list",docList,null,id(),Map.of()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result.length()").value(1))
            .andExpect(jsonPath("$.output.result[0].id").value(document));
        mvc.perform(printCommand(submit,"PrintDocument","list",docList,null,id(),Map.of()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result.length()").value(1))
            .andExpect(jsonPath("$.output.result[0].number").value("OTHER-PRINT"));
        mvc.perform(printCommand(outside,"PrintDocument","list",docList,null,id(),Map.of()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result.length()").value(1))
            .andExpect(jsonPath("$.output.result[0].id").value(foreignDocument));
        mvc.perform(printCommand(submit,"PrintJob","list",jobList,null,id(),Map.of()))
            .andExpect(status().isForbidden());
        grantPrintPermission(tenant,otherMember,"print.job:read");
        var reader=select(other,tenant);
        mvc.perform(printCommand(reader,"PrintJob","list",jobList,null,id(),Map.of()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result.length()").value(1))
            .andExpect(jsonPath("$.output.result[0].id").value(ownJob));
        mvc.perform(printCommand(reader,"PrintDocument","list",docList,null,id(),Map.of()))
            .andExpect(status().isForbidden());
        mvc.perform(printCommand(outside,"PrintJob","list",jobList,null,id(),Map.of()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result.length()").value(1))
            .andExpect(jsonPath("$.output.result[0].id").value(foreignJob));
        mvc.perform(printCommand(operator,"PrintDocument","list",docList,foreignDocument,id(),Map.of()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRINT_INPUT_INVALID"));
        mvc.perform(printCommand(operator,"PrintJob","list",jobList,null,id(),Map.of("tenantId",foreign.toString())))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BUSINESS_OBJECT_COMMAND_INVALID"));
        mvc.perform(printCommand(operator,"PrintDocument","list",docList,null,id(),null))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BUSINESS_OBJECT_COMMAND_REQUIRED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/printing/documents").cookie(operator))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(document));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/printing/jobs").cookie(operator))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(ownJob));

        assertThat(documents(tenant)).isEqualTo(2);
        assertThat(documents(foreign)).isEqualTo(1);
        assertThat(jobs(tenant)).isEqualTo(1);
        assertThat(jobs(foreign)).isEqualTo(1);
        for(UUID id:List.of(tenant,foreign)){
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM print_output_request WHERE tenant_id=UUID_TO_BIN(?)",
                Integer.class,id.toString())).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_object_resolution WHERE tenant_id=?",
                Integer.class,id.toString())).isZero();
        }
    }

    /** 真实 MySQL 验证原任选读取权限、字段白名单、模板契约和场景版本检查。 */
    @Test void contractAndSceneCommandsPreserveOriginalAuthorizationAndValidation()throws Exception{
        UUID tenant=UUID.randomUUID(),foreign=UUID.randomUUID(),owner=account(),operator=account();
        UUID operatorMember=UUID.randomUUID();
        for(UUID id:List.of(tenant,foreign)){
            jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'打印配置命令')",id.toString());
            UUID member=UUID.randomUUID();
            addMember(id,member,owner);
            bootstrap.initialize(id,member);
        }
        addMember(tenant,operatorMember,operator);
        var ownerCookie=select(owner,tenant);
        var context=new PrintContext(tenant.toString(),owner.toString(),id());
        var otherContext=new PrintContext(foreign.toString(),owner.toString(),id());
        String contract="command_contract_v1",contractList="PrintDataContract.list",contractCreate="PrintDataContract.create";
        String sceneList="PrintScene.list",sceneCreate="PrintScene.create",sceneUpdate="PrintScene.update";
        var definition=contractDefinition(contract,"number");
        var unprivileged=select(operator,tenant);
        mvc.perform(printCommand(unprivileged,"PrintDataContract","list",contractList,null,id(),Map.of()))
            .andExpect(status().isForbidden());
        mvc.perform(printCommand(unprivileged,"PrintScene","list",sceneList,null,id(),Map.of()))
            .andExpect(status().isForbidden());
        mvc.perform(printCommand(ownerCookie,"PrintDataContract","create",contractCreate,null,id(),
            contractDefinition("invalid_contract_v1","number.invalid")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRINT_INPUT_INVALID"));
        assertThat(configurationRows(tenant)).containsExactly(0,0,0);

        mvc.perform(printCommand(ownerCookie,"PrintDataContract","create",contractCreate,null,id(),definition))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result[*].code")
                .value(org.hamcrest.Matchers.hasItem(contract)));
        mvc.perform(printCommand(ownerCookie,"PrintDataContract","create",contractCreate,null,id(),definition))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRINT_DUPLICATE"));
        scenes.createContract(context,contractDefinition("other_command_v1","number"));
        scenes.createContract(otherContext,contractDefinition("foreign_command_v1","number"));
        String template=publishedTemplate(context,"command_template",contract);
        String foreignTemplate=publishedTemplate(otherContext,"foreign_template","foreign_command_v1");
        String otherScene=scenes.saveScene(otherContext,null,new SceneRequest("foreign_scene","外租户场景",
            "foreign_command_v1",foreignTemplate,1,true,-1)).getFirst().get("id").toString();

        grantPrintPermission(tenant,operatorMember,"print.template:read");
        var reader=select(operator,tenant);
        var before=configurationRows(tenant);
        mvc.perform(printCommand(reader,"PrintDataContract","list",contractList,null,id(),Map.of()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result[*].code")
                .value(org.hamcrest.Matchers.hasItem(contract)))
            .andExpect(jsonPath("$.output.result[*].code")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("foreign_command_v1"))));
        mvc.perform(printCommand(reader,"PrintScene","list",sceneList,null,id(),Map.of()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result").isEmpty());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/printing/design-contracts")
            .cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$[*].code")
                .value(org.hamcrest.Matchers.hasItem(contract)));
        var draft=sceneInput("command_scene",contract,template,"人员场景",-1);
        mvc.perform(printCommand(reader,"PrintDataContract","create",contractCreate,null,id(),definition))
            .andExpect(status().isForbidden());
        mvc.perform(printCommand(reader,"PrintScene","create",sceneCreate,null,id(),draft))
            .andExpect(status().isForbidden());
        mvc.perform(printCommand(reader,"PrintScene","update",sceneUpdate,id(),id(),
            sceneInput("command_scene",contract,template,"无权编辑",0))).andExpect(status().isForbidden());
        assertThat(configurationRows(tenant)).isEqualTo(before);

        grantPrintPermission(tenant,operatorMember,"print.template:publish");
        var publisher=select(operator,tenant);
        mvc.perform(printCommand(publisher,"PrintDataContract","create",contractCreate,null,id(),definition))
            .andExpect(status().isForbidden());
        mvc.perform(printCommand(publisher,"PrintScene","create",sceneCreate,null,id(),
            sceneInput("bad_binding","other_command_v1",template,"错误契约",-1)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRINT_SCENE_TEMPLATE_INVALID"));
        mvc.perform(printCommand(publisher,"PrintScene","create",sceneCreate,null,id(),
            sceneInput("foreign_binding",contract,foreignTemplate,"外租户模板",-1)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRINT_SCENE_TEMPLATE_INVALID"));
        assertThat(configurationRows(tenant)).isEqualTo(before);

        var created=mvc.perform(printCommand(publisher,"PrintScene","create",sceneCreate,null,id(),draft))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result[0].code").value("command_scene"))
            .andReturn().getResponse();
        String scene=json.readTree(created.getContentAsString()).path("output").path("result").get(0).path("id").asText();
        var change=sceneInput("command_scene",contract,template,"场景修改",0);
        mvc.perform(printCommand(publisher,"PrintScene","update",sceneUpdate,otherScene,id(),change))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRINT_SCENE_CONFLICT"));
        mvc.perform(printCommand(publisher,"PrintScene","update",sceneUpdate,scene,id(),change))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result[0].name").value("场景修改"));
        mvc.perform(printCommand(publisher,"PrintScene","update",sceneUpdate,scene,id(),change))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRINT_SCENE_CONFLICT"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/printing/scenes").cookie(publisher))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(scene));

        grantSubmit(tenant,operatorMember);
        var submit=select(operator,tenant);
        var after=configurationRows(tenant);
        mvc.perform(printCommand(submit,"PrintScene","list",sceneList,null,id(),Map.of()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result.length()").value(1))
            .andExpect(jsonPath("$.output.result[0].id").value(scene));
        mvc.perform(printCommand(submit,"PrintDataContract","list",contractList,null,id(),Map.of()))
            .andExpect(status().isOk());
        mvc.perform(printCommand(submit,"PrintScene","list",sceneList,null,id(),null))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BUSINESS_OBJECT_COMMAND_REQUIRED"));
        assertThat(after).containsExactly(2,1,0);
        assertThat(configurationRows(tenant)).isEqualTo(after);
        assertThat(jdbc.queryForObject("SELECT revision FROM print_scene WHERE id=UUID_TO_BIN(?)",
            Long.class,scene)).isEqualTo(1L);
    }

    private Map<String,Object> contractDefinition(String code,String field){
        return Map.of("code",code,"nameZh","命令契约","nameEn","Command contract",
            "fields",List.of(Map.of("key",field,"labelZh","编号","labelEn","Number","sample","SAMPLE")));
    }

    private String publishedTemplate(PrintContext context,String code,String contract){
        String template=templates.saveTemplate(context,null,new TemplateDraft(code,"配置验收","A4",
            contract,Map.of(),Map.of("panels",List.of()),-1)).get("id").toString();
        templates.publish(context,template,0);
        return template;
    }

    private Map<String,Object> sceneInput(String code,String contract,String template,String name,long revision){
        return Map.of("code",code,"name",name,"contract",contract,"templateId",template,"copies",1,
            "enabled",true,"revision",revision);
    }

    private List<Integer> configurationRows(UUID tenant){
        return List.of(jdbc.queryForObject("SELECT COUNT(*) FROM print_data_contract WHERE tenant_id=UUID_TO_BIN(?)",
                Integer.class,tenant.toString()),
            jdbc.queryForObject("SELECT COUNT(*) FROM print_scene WHERE tenant_id=UUID_TO_BIN(?)",
                Integer.class,tenant.toString()),jobs(tenant));
    }

    private String catalogScene(PrintContext context,String code){
        String contract=code+"_v1";
        scenes.createContract(context,Map.of("code",contract,"nameZh","目录单据","nameEn","Catalog document",
            "fields",List.of(Map.of("key","number","labelZh","编号","labelEn","Number","sample","SAMPLE"))));
        String template=templates.saveTemplate(context,null,new TemplateDraft(code,"目录单据","A4",
            contract,Map.of(),Map.of("panels",List.of()),-1)).get("id").toString();
        templates.publish(context,template,0);
        return scenes.saveScene(context,null,new SceneRequest(code,"目录单据",contract,template,1,true,-1))
            .getFirst().get("id").toString();
    }

    private int documents(UUID tenant){
        return jdbc.queryForObject("SELECT COUNT(*) FROM print_document WHERE tenant_id=UUID_TO_BIN(?)",
            Integer.class,tenant.toString());
    }

    private void assertCopy(String id,String parent,String output,int copies){
        var row=jdbc.queryForMap("""
            SELECT BIN_TO_UUID(parent_job_id) AS parent,output_type,copies,template_version,
                   CAST(data_snapshot AS CHAR) AS snapshot,
                   JSON_UNQUOTE(JSON_EXTRACT(data_contract_schema,'$.code')) AS contract
              FROM print_job WHERE id=UUID_TO_BIN(?)
            """,id);
        assertThat(row).containsEntry("parent",parent).containsEntry("output_type",output)
            .containsEntry("copies",copies).containsEntry("template_version",1).containsEntry("contract","output_ticket_v1");
        assertThat(json.readTree(row.get("snapshot").toString())).isEqualTo(json.createObjectNode().put("number","FROZEN-FACT"));
    }

    private MockHttpServletRequestBuilder printCommand(Cookie cookie,String type,String operation,String entry,
            String objectId,String key,Map<String,Object> options){
        var input=new BusinessObjectExecution.Request(type,objectId,operation,entry,0,options);
        return post("/api/business-object-executions").cookie(cookie).with(csrf()).header("Idempotency-Key",key)
            .contentType("application/json").content(json.writeValueAsString(input));
    }

    private void grantSubmit(UUID tenant,UUID member){grantPrintPermission(tenant,member,"print.job:submit");}

    private void grantPrintPermission(UUID tenant,UUID member,String code){
        administration.initializeAdministrator("admin-test");
        UUID actor=UUID.fromString(jdbc.queryForObject(
            "SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));
        var scope=new RoleManagement.Context(actor,tenant,null);
        var role=roles.create(scope,new RoleManagement.Create("print_cmd_"+id().replace("-",""),"单据打印验收",null,100));
        UUID submit=UUID.fromString(jdbc.queryForObject(
            "SELECT BIN_TO_UUID(id) FROM access_permission WHERE code=?",String.class,code));
        permissions.replaceAuthorization(scope,role.id(),role.version(),Set.of(submit),Set.of(),"单据本人范围验收");
        var state=subjects.memberRoles(scope,member);
        subjects.replaceMemberRoles(scope,member,state.authorityVersion(),Set.of(role.id()),"单据本人范围验收");
    }

    private UUID account(){
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'打印验收','unused')",
            id.toString(),"print-command-"+id);
        return id;
    }

    private void addMember(UUID tenant,UUID member,UUID account){
        jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",
            member.toString(),tenant.toString(),account.toString());
    }

    private Cookie select(UUID actor,UUID tenant)throws Exception{
        return mvc.perform(post("/bff/tenants/current").with(authenticated(actor.toString(),0)).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(Map.of("tenantId",tenant.toString()))))
            .andExpect(status().isOk()).andReturn().getResponse().getCookie("JSESSIONID");
    }

    private MockHttpServletRequestBuilder command(Cookie cookie,String document,long basis,String key,Map<String,Object> options){
        var input=new BusinessObjectExecution.Request("PrintDocument",document,"print","PrintDocument.print",basis,options);
        return post("/api/business-object-executions").cookie(cookie).with(csrf()).header("Idempotency-Key",key)
            .contentType("application/json").content(json.writeValueAsString(input));
    }

    private int jobs(UUID tenant){
        return jdbc.queryForObject("SELECT COUNT(*) FROM print_job WHERE tenant_id=UUID_TO_BIN(?)",Integer.class,tenant.toString());
    }

    private static String id(){return UUID.randomUUID().toString();}
}
