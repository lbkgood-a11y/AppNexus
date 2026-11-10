package com.appnexus.contracts;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.*;

/** 三接口面契约 lint 与保守兼容门禁；可选 API_BASE_REF 指定已发布或 PR 基准 Git revision。 */
class ApiContractChecksTest {
    private static final Set<String> METHODS=Set.of("get","post","put","patch","delete","head","options");
    private static final Set<String> DOCUMENTATION=Set.of("description","summary","example","examples","title","operationId","info","servers");
    @Test void contractsHaveValidNamesSecurityAndIsolatedSurfaces()throws Exception{
        for(String name:List.of("app","platform","partner-v1")){
            Path path=Path.of("src/main/resources/openapi-"+name+".yaml");
            var contract=parse(Files.readString(path));
            assertThat(contract.get("openapi").toString()).startsWith("3.");
            assertThat(contract.get("security")).isNotNull();
            var paths=map(contract.get("paths"));assertThat(paths).isNotEmpty();Set<String> operationIds=new HashSet<>();
            for(var entry:paths.entrySet()){
                String route=entry.getKey();
                assertThat(route.replaceAll("\\{[^}]+}","{id}")).matches("/[a-z0-9{}:/._-]+");
                if(name.equals("partner-v1"))assertThat(route).startsWith("/v1/");
                else if(name.equals("platform"))assertThat(route).startsWith("/bff/platform/");
                else assertThat(route).doesNotStartWith("/v1/").doesNotStartWith("/bff/platform/");
                for(var operation:map(entry.getValue()).entrySet())if(METHODS.contains(operation.getKey())){
                    var definition=map(operation.getValue());String id=(String)definition.get("operationId");
                    assertThat(id).isNotBlank();assertThat(operationIds.add(id)).as("operationId 必须唯一："+id).isTrue();
                    assertThat(map(definition.get("responses"))).isNotEmpty();
                }
            }
            if(name.equals("partner-v1"))for(String forbidden:List.of("passwordHash:","authorityVersion:","createdBy:","encryptedSecret:","tenantId:"))assertThat(Files.readString(path)).doesNotContain(forbidden);
            compareGitBaseline(path,contract);
        }
    }
    /** 兼容门禁的最小反例：删除路径、字段类型变化必须失败，可选字段增加允许。 */
    @Test void compatibilityCheckRejectsRemovalAndTypeChanges(){
        var before=Map.of("paths",Map.of("/v1/items",Map.of("get",Map.of("responses",Map.of("200",Map.of("type","string"))))));
        var removal=Map.of("paths",Map.of());List<String> errors=new ArrayList<>();compare(before,removal,"$",errors);assertThat(errors).isNotEmpty();
        errors.clear();compare(Map.of("type","string"),Map.of("type","integer"),"$",errors);assertThat(errors).isNotEmpty();
        errors.clear();compare(Map.of("properties",Map.of("name",Map.of("type","string"))),Map.of("properties",Map.of("name",Map.of("type","string"),"optional",Map.of("type","string"))),"$",errors);assertThat(errors).isEmpty();
    }
    /** Git 参数通过 ProcessBuilder 传递，不执行 shell；没有基准文件表示该接口面首次新增。 */
    private void compareGitBaseline(Path path,Map<String,Object> current)throws Exception{
        String base=System.getenv("API_BASE_REF");if(base==null||base.isBlank()||base.matches("0+"))return;
        if(!base.matches("[A-Za-z0-9_./-]{1,200}")||base.startsWith("-"))throw new IllegalArgumentException("API_BASE_REF 必须为安全 Git revision");
        String file="backend/contracts/partner-api-spec/"+path.toString().replace('\\','/');
        var process=new ProcessBuilder("git","show",base+":"+file).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String old=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);int exit=process.waitFor();
        if(exit!=0){
            var exists=new ProcessBuilder("git","rev-parse","--verify",base+"^{commit}").redirectErrorStream(true).start();exists.getInputStream().readAllBytes();
            assertThat(exists.waitFor()).as("兼容基准必须能解析，不能静默跳过未知 revision").isZero();return;
        }
        List<String> errors=new ArrayList<>();compare(parse(old),current,"$",errors);
        assertThat(errors).as("检测到保守判定的破坏性契约变更，须发布新主版本或显式迁移决策").isEmpty();
    }
    /** 保守比较：禁止删除原节点或改变约束值；文案和可选节点新增不触发，语义变更仍需人工评审。 */
    private void compare(Object before,Object after,String path,List<String> errors){
        if(before instanceof Map<?,?> old){
            if(!(after instanceof Map<?,?> next)){errors.add(path+" 类型变化");return;}
            old.forEach((key,value)->{if(!DOCUMENTATION.contains(key.toString())){
                if(!next.containsKey(key))errors.add(path+"/"+key+" 被删除");else compare(value,next.get(key),path+"/"+key,errors);
            }});
            if(next.containsKey("required")&&!old.containsKey("required")&&!Objects.equals(next.get("required"),List.of()))errors.add(path+" 增加必填约束");
        }else if(before instanceof List<?> list){if(!Objects.equals(list,after))errors.add(path+" 列表约束变化");}
        else if(!Objects.equals(before,after))errors.add(path+" 约束值变化");
    }
    private Map<String,Object> parse(String value){return map(new Yaml(new SafeConstructor(new LoaderOptions())).load(value));}
    @SuppressWarnings("unchecked") private Map<String,Object> map(Object value){assertThat(value).isInstanceOf(Map.class);return (Map<String,Object>)value;}
}
