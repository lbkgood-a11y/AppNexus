package com.appnexus.workflow.application;

import com.appnexus.contracts.workflow.*;
import com.appnexus.workflow.core.WorkflowModel;
import static com.appnexus.workflow.core.WorkflowModel.*;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 草稿校验应用边界；不启动引擎即可验证能力字段与业务终态要求。 */
class WorkflowModelValidationTest {
  private Node node(String id, Kind kind, String capability, Map<String,String> input) {
    return new Node(id,id,kind,List.of(),Completion.ALL,false,capability,Set.of(),input,2,3600,null,true);
  }
  /** 未声明输入和未知数值字段必须被拒绝；跨业务输出不能伪装为本单据 NUMBER。 */
  @Test void rejectsUndeclaredInputsAndNumericFields() {
    var model = new WorkflowModel("测试","Document","approval",Mode.BUSINESS,"entry",List.of(),
        List.of(node("start",Kind.START,null,Map.of()),node("work",Kind.ACTION,"entry",Map.of("secret","x")),node("end",Kind.END,null,Map.of())),
        List.of(new Edge("start","work","amount","1",false,"GT"),new Edge("work","end",null,null,false)),Map.of());
    var capability = new WorkflowBusiness.Capability("entry","入口","test:execute","/test",Set.of("safe"),Set.of("amount"));
    var errors = WorkflowModelValidation.validate(model,List.of(capability),Map.of());
    assertThat(errors).contains("INPUT_FIELD_INVALID:work","NUMERIC_FIELD_REQUIRED:start");
  }
  /** 审批模型即使图有效，也必须声明真实通过、拒绝终态处理能力。 */
  @Test void requiresRegisteredApprovalOutcomes() {
    var model = new WorkflowModel("测试","Document","approval",Mode.APPROVAL,"entry",List.of(),
        List.of(node("start",Kind.START,null,Map.of()),node("end",Kind.END,null,Map.of())),
        List.of(new Edge("start","end",null,null,false)),Map.of());
    var capability = new WorkflowBusiness.Capability("entry","入口","test:execute","/test",Set.of(),Set.of());
    var profile = new WorkflowBusinessProfile("Document","approval","单据","Document","","",List.of(),
        List.of(new WorkflowBusinessProfile.Outcome("APPROVED","entry","通过","Approved")),null);
    assertThat(WorkflowModelValidation.validate(model,List.of(capability),Map.of("entry",profile)))
        .contains("BUSINESS_OUTCOME_REQUIRED:APPROVED","BUSINESS_OUTCOME_REQUIRED:REJECTED");
  }
  /** 旧草稿缺少撤回必须在发布用校验中拒绝，完整契约可通过且不误判对象和用途。 */
  @Test void requiresWithdrawalBeforePublication() {
    var capability = new WorkflowBusiness.Capability("entry","入口","test:execute","/test",Set.of(),Set.of());
    var profile = new WorkflowBusinessProfile("Document","approval","单据","Document","","",List.of(),
        List.of(new WorkflowBusinessProfile.Outcome("APPROVED","entry","通过","Approved"),
          new WorkflowBusinessProfile.Outcome("REJECTED","entry","驳回","Rejected"),
          new WorkflowBusinessProfile.Outcome("WITHDRAWN","entry","撤回","Withdrawn")),"entry");
    var base = new WorkflowModel("测试","Document","approval",Mode.APPROVAL,"entry",List.of(),
        List.of(node("start",Kind.START,null,Map.of()),node("end",Kind.END,null,Map.of())),
        List.of(new Edge("start","end",null,null,false)),Map.of("APPROVED","entry","REJECTED","entry"));
    assertThat(WorkflowModelValidation.validate(base,List.of(capability),Map.of("entry",profile)))
        .contains("BUSINESS_OUTCOME_REQUIRED:WITHDRAWN","BUSINESS_WITHDRAWAL_INVALID");
    var complete = new WorkflowModel(base.name(),base.objectType(),base.purpose(),base.mode(),base.entryCapability(),base.matches(),
        base.nodes(),base.edges(),Map.of("APPROVED","entry","REJECTED","entry","WITHDRAWN","entry"),List.of(),"entry");
    assertThat(WorkflowModelValidation.validate(complete,List.of(capability),Map.of("entry",profile)))
        .doesNotContain("BUSINESS_OUTCOME_REQUIRED:WITHDRAWN","BUSINESS_WITHDRAWAL_INVALID","BUSINESS_PROFILE_MISMATCH");
  }

}
