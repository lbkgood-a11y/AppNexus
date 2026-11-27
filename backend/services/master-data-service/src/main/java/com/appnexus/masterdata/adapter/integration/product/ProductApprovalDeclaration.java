package com.appnexus.masterdata.adapter.integration.product;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.*;
import java.util.*;
import static com.appnexus.contracts.businessobject.ObjectRecordMapping.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
/** 商品审批唯一对象声明与公共运行接入；保持原对象、操作、版本及数据契约，不承担业务提交或SQL。 */
@Component
@ConditionalOnProperty(name="app.workflow.engine.enabled",havingValue="true")
public final class ProductApprovalDeclaration implements Provider {
 private static final List<Field> FIELDS=List.of(new Field("productVersion","商品版本","Product version","NUMBER","",true));
 private static final Operation SUBMIT=new Operation("submit","提交商品审核","Submit product approval","masterdata.product.approval:submit","ProductApproval.submit",new PageOperation("master-data/product","/master-data/product","商品档案","Products","POST","/api/master-data/product/{id}/approval"),List.of(),new InteractionContract("ProductApproval.submit",1,"ProductApproval","submit","提交商品审核","Submit product approval",InteractionContract.Trigger.USER_ACTION,List.of(new InteractionContract.Capability(InteractionContract.Service.WORKFLOW,InteractionContract.Completion.CALLBACK,"productApproval","approval",FIELDS,Set.of("APPROVED","REJECTED","WITHDRAWN"),"product.approval",1))));
 @Override public Descriptor descriptor(){return new Descriptor("ProductApproval","商品审核申请","Product approval","master-data",2,Category.TRANSACTION,FIELDS,List.of(SUBMIT),new DataAccess("TENANT",List.of(new DataResource("masterdata.product.approval","","商品审核申请","Product approvals",List.of(new DataField("productVersion","商品版本","Product version","TEXT",Set.of("READ_ONLY","HIDDEN"))))),Map.of("submit",Set.of("ALL"))));}

 private BusinessObjectRuntime runtime;
 /** Spring装配与测试共用固定对象运行器，不再手写审批取数及回执SQL。 */
 @org.springframework.beans.factory.annotation.Autowired
 public void runtimeFactory(ObjectRecordRuntimeFactory factory){runtime=factory.create(this,recordMapping(),(c,id,operation,service)->{});}
 @Override public BusinessObjectRuntime runtime(){return Objects.requireNonNull(runtime,"OBJECT_RUNTIME_NOT_ASSEMBLED");}
 /** 商品审核使用二进制UUID与现有冻结版本，不改变商品正文所有权。 */
 public static ObjectRecordMapping recordMapping(){
   var binary=Encoding.BINARY_UUID;return new ObjectRecordMapping("md_product_approval",null,null,new Column("organization_id",binary),new Column("applicant_id",binary),
      "product_version","state","instance_id","delivery_id",null,Map.of("productVersion",new Value(new Column("product_version",Encoding.TEXT),Transform.VALUE,0)),
      Map.of("submit",new Transition(Set.of("SUBMITTED"),null,false)),null,true);
 }

}
