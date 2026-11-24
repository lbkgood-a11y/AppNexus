package com.appnexus.masterdata.adapter.integration.partner;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.masterdata.domain.partner.PartnerRules;
import com.appnexus.masterdata.api.masterdata.MasterData;

import com.appnexus.contracts.businessobject.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** 客商唯一业务对象声明：身份、操作、页面、交互和数据权限集中在同一Descriptor；Controller只引用操作。 */
@Component
public final class BusinessPartnerDeclaration implements BusinessObjects.Provider {
    /** 客商主档稳定资源标识，必须与既有功能权限资源一致。 */
    public static final String ROOT="masterdata.partner";
    /** 联系人字段资源，行权限继承主档，不产生平行功能授权。 */
    public static final String CONTACT=ROOT+".contact";
    /** 银行账户字段资源，行权限继承主档，账号始终作为文本处理。 */
    public static final String BANK=ROOT+".bank";
    /** 同一声明快照供对象目录、操作目录和数据权限目录读取，元数据不可按请求身份变化。 */
    @Override public Descriptor descriptor(){
        var operations=List.of(operation("list","查询客商管理","Read business partners","read","GET",""),
            operation("create","新增客商管理","Create business partner","create","POST",""),
            operation("update","编辑客商管理","Update business partner","update","PUT","/{id}"),
            operation("readStatusHistory","查看启停历史","Read status history","read","GET","/{id}/status-history"),
            operation("setStatus","启停客商管理","Change business partner status","set_status","PUT","/{id}/status"));
        var dataAccess=new DataAccess("TENANT",List.of(
            new DataResource(ROOT,"","客商主档","Business partner",fields(MasterData.Kind.PARTNER.fields)),
            new DataResource(CONTACT,ROOT,"客商联系人","Partner contacts",fields(PartnerRules.CONTACT)),
            new DataResource(BANK,ROOT,"客商银行账户","Partner bank accounts",fields(PartnerRules.BANK))),
            operations.stream().collect(java.util.stream.Collectors.toMap(Operation::operationCode,op->Set.of("ALL","ORGANIZATION","ORGANIZATION_SUBTREE","RULES"))),
            List.of(new DataDimension("code","客商编码","Partner code","TEXT",Set.of("EQ","IN"),"TEXT","",Set.of("list")),
                new DataDimension("name","客商名称","Partner name","TEXT",Set.of("EQ","IN"),"TEXT","",Set.of("list")),
                new DataDimension("organizationId","归属组织","Owning organization","UUID",Set.of("EQ","IN"),"ORGANIZATION","",Set.of("list","create","update","setStatus"),
                    Set.of("CURRENT_ORGANIZATION","CURRENT_ORGANIZATION_SUBTREE")),
                new DataDimension("createdBy","创建人账号","Created by account","UUID",Set.of("EQ"),"ACCOUNT","",
                    Set.of("list"),Set.of("CURRENT_ACCOUNT")),
                new DataDimension("partnerType","客商类型","Partner type","TEXT",Set.of("EQ","IN"),"DICTIONARY",
                    com.appnexus.platform.configuration.BusinessValueCatalog.PARTNER.get("partnerType"),Set.of("list"))))
            .withStandardCrud("organizationId",Set.of("list","create","update","setStatus"));
        return new Descriptor("BusinessPartner","客商管理","Business partners","master-data",6,Category.MASTER_DATA,List.of(),operations,dataAccess,List.of(new CodingField("code","create",64)));
    }
    /** 仅声明已实现的直接业务能力，不提供未接通审批或打印入口；契约字段为空，不复制敏感业务正文。 */
    private static Operation operation(String code,String zh,String en,String action,String method,String path){
        String id="BusinessPartner."+code;
        var interaction=new InteractionContract(id,1,"BusinessPartner",code,zh,en,InteractionContract.Trigger.USER_ACTION,
            List.of(new InteractionContract.Capability(InteractionContract.Service.BUSINESS,InteractionContract.Completion.SYNCHRONOUS,id,"",List.of(),Set.of(),"masterdata.partner."+code,1)));
        return new Operation(code,zh,en,ROOT+":"+action,id,
            new PageOperation("master-data/partner","/master-data/partner","客商管理","Business partners",method,"/api/master-data/partner"+path),List.of(),interaction);
    }
    /** 必填分类与结构布尔字段保持业务可用性，不开放隐藏；其他文本均接通三种强制限制。 */
    private static List<DataField> fields(List<String> keys){
        return keys.stream().filter(key->!Set.of("partnerType","contactType","currency","taxAccount","basicAccount","isDefault").contains(key)).map(key->{
            var kind=switch(key){case "phone"->"PHONE";case "email"->"EMAIL";case "legalId"->"IDENTIFIER";case "accountNumber"->"BANK_ACCOUNT";case "contactName","legalRepresentative"->"PERSON_NAME";default->"TEXT";};
            var names=Map.of("phone","电话","email","邮箱","legalId","法人身份证","accountNumber","银行账号","contactName","联系人","legalRepresentative","法人代表","creditCode","统一社会信用代码","bankName","银行名称","remarks","备注");
            return new DataField(key,names.containsKey(key)?names.get(key):fieldName(key),key,kind,Set.of("READ_ONLY","MASKED","HIDDEN"));
        }).toList();
    }
    /** 字段业务名称由固定代码维护，不能把客户端标签作为目录的唯一来源。 */
    private static String fieldName(String key){return switch(key){
        case "address"->"地址";case "registeredPlace"->"注册地";case "countryRegion"->"国别地区";case "nature"->"企业性质";case "listed"->"是否上市";
        case "chineseName"->"中文名称";case "englishName"->"英文名称";case "shortName"->"简称";case "customerLevel"->"客户等级";case "controller"->"实际控制人";
        case "establishedOn"->"成立日期";case "registeredCurrency"->"注册币种";case "registeredCapital"->"注册资本";case "taxNumber"->"税号";case "organizationCode"->"组织机构代码";
        case "productsServices"->"产品与服务";case "staffSize"->"人员规模";case "fax"->"传真";case "accountAlias"->"账户别名";case "cnaps"->"联行号";case "swiftCode"->"SWIFT代码";default->throw new IllegalStateException("客商字段缺少中文名称："+key);};}
}
