package com.appnexus.platform.security;
import com.appnexus.contracts.businessobject.BusinessObjects.*;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.platform.configuration.NavigationPages;
import java.util.*;
import org.springframework.stereotype.Component;

/** 任职人员目录唯一声明；保留真实权限和接口，字段目录及响应投影共用本对象的数据权限定义。 */
@Component
public final class PersonDirectoryDeclaration implements BusinessObjects.Provider {
    @Override public Descriptor descriptor(){
        var operations=List.of(
            operation("list","任职人员：查看列表","Read organization units","organization.unit:read","GET","/api/organization/domain/persons",List.of("organization/index")));
        var access=new DataAccess("TENANT",List.of(new DataResource("organization.member","","任职人员目录","Personnel directory",List.of(new DataField("displayName","人员姓名","Person name","PERSON_NAME",Set.of("MASKED","HIDDEN"))),"com.appnexus.platform.organization.OrganizationStructure$Person")),Map.of());
        return new Descriptor("PersonDirectory","任职人员目录","Personnel directory","organization",1,Category.MASTER_DATA,List.of(),operations,access);
    }
    /** 页面别名保留原调用关系；仅声明已执行的直接业务能力，不隐式开放行范围或字段写入。 */
    private static Operation operation(String code,String zh,String en,String permission,String method,String path,List<String> pages){
        String id="PersonDirectory"+"."+code;
        var interaction=new InteractionContract(id,1,"PersonDirectory",code,zh,en,InteractionContract.Trigger.USER_ACTION,List.of(new InteractionContract.Capability(InteractionContract.Service.BUSINESS,InteractionContract.Completion.SYNCHRONOUS,id,"",List.of(),Set.of(),id,1)));
        var views=pages.stream().map(key->{var page=Objects.requireNonNull(NavigationPages.find(key));return new PageOperation(key,page.path(),page.titleZh(),page.titleEn(),method,path);}).toList();
        return new Operation(code,zh,en,permission,id,views.getFirst(),views.stream().skip(1).map(view->new OperationAlias(id,view)).toList(),interaction);
    }
}
