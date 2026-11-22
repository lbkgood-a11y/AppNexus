package com.appnexus.platform.security;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.DedicatedOperations;

import com.appnexus.contracts.businessobject.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** 组织单元唯一声明；平面、领域及摘要入口引用同一对象，数据范围从同一操作清单派生。 */
@Component
public final class OrganizationUnitDeclaration implements BusinessObjects.Provider {
    @Override public Descriptor descriptor(){
        var operations=List.of(
            operation("listFlat","组织单元：查看平面列表","organization.unit:read","GET","/api/organization/units"),
            operation("createFlat","组织单元：新增平面组织","organization.unit:create","POST","/api/organization/units"),
            operation("rename","组织单元：改名","organization.unit:update","PUT","/api/organization/units/{id}"),
            operation("delete","组织单元：删除","organization.unit:delete","DELETE","/api/organization/units/{id}"),
            operation("list","组织单元：查看列表","organization.unit:read","GET","/api/organization/domain/units"),
            operation("create","组织单元：新增","organization.unit:create","POST","/api/organization/domain/units"),
            operation("update","组织单元：修改","organization.unit:update","PUT","/api/organization/domain/units/{id}"),
            operation("move","组织单元：移动","organization.unit:update","POST","/api/organization/domain/units/{id}/move"),
            operation("inspectImpact","查看组织","organization.unit:read","GET","/api/organization/domain/units/{id}/impact"),
            operation("summary","组织单元：查看摘要","organization.unit:read","GET","/api/organization/summaries"),
                DedicatedOperations.operation("OrganizationUnit", "listLeaders", "组织负责人：查看列表", "Read organization units", "organization.unit:read",
                        new PageOperation("organization/index", "/organization/manage", "组织负责人：查看列表", "Read organization units", "GET", "/api/organization/domain/leaders")),
                DedicatedOperations.operation("OrganizationUnit", "setLeader", "组织负责人：设置", "Update organization units", "organization.unit:update",
                        new PageOperation("organization/index", "/organization/manage", "组织负责人：设置", "Update organization units", "PUT", "/api/organization/domain/leaders")));
        var scopes=new TreeMap<String,Set<String>>();
        var scopedOperations=new TreeSet<String>();
        for(var op:operations){
            if(!op.operationCode().equals("createFlat")){
                if(!op.operationCode().equals("create"))scopedOperations.add(op.operationCode());
                scopes.put(op.operationCode(),Set.of("ALL","ORGANIZATION","ORGANIZATION_SUBTREE","RULES"));
            }else scopes.put(op.operationCode(),Set.of("ALL"));
        }
        var access=new DataAccess("TENANT",List.of(new DataResource("organization.unit","","组织单元","Organization unit",List.of())),scopes,
            List.of(new DataDimension("organizationId","组织单元","Organization unit","UUID",Set.of("EQ","IN"),
                "ORGANIZATION","",scopedOperations,Set.of("CURRENT_ORGANIZATION","CURRENT_ORGANIZATION_SUBTREE")),
                new DataDimension("parentOrganizationId","目标上级组织","Target parent organization","UUID",Set.of("EQ","IN"),
                "ORGANIZATION","",Set.of("create"),Set.of("CURRENT_ORGANIZATION","CURRENT_ORGANIZATION_SUBTREE"))));
        return new Descriptor("OrganizationUnit","组织单元","Organization unit","organization",6,Category.MASTER_DATA,List.of(),operations,access);
    }
    /** 只保留已接通的直接执行；页面控制不能创建免鉴权入口，也不提供未接通审批或打印。 */
    private static Operation operation(String code,String zh,String permission,String method,String path){
        String id="OrganizationUnit."+code;
        String en=switch(code){case "listFlat"->"Read flat organization list";case "createFlat"->"Create flat organization";case "rename"->"Rename organization";case "delete"->"Delete organization";case "list"->"Read organizations";case "create"->"Create organization";case "update"->"Update organization";case "move"->"Move organization";case "inspectImpact"->"Inspect organization impact";case "summary"->"Read organization summary";default->throw new IllegalArgumentException("UNKNOWN_ORGANIZATION_OPERATION");};
        var interaction=new InteractionContract(id,1,"OrganizationUnit",code,zh,en,InteractionContract.Trigger.USER_ACTION,List.of(new InteractionContract.Capability(InteractionContract.Service.BUSINESS,InteractionContract.Completion.SYNCHRONOUS,id,"",List.of(),Set.of(),"organization.unit."+code,1)));
        return new Operation(code,zh,en,permission,id,new PageOperation("organization/index","/organization/manage","组织管理","Organization",method,path),List.of(),interaction);
    }
}
