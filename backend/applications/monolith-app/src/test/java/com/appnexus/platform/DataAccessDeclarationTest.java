package com.appnexus.platform;

import com.appnexus.platform.authorization.*;
import com.appnexus.masterdata.BusinessPartnerDeclaration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 声明标准拒绝重复对象、非法资源路径与尚无语义的范围，不将异常转为空目录。 */
class DataAccessDeclarationTest {
    @Test void duplicateAndUnsupportedDeclarationsFailClosed(){
        var object=new BusinessPartnerDeclaration();
        var root=DataAccessCatalog.fromObjects(List.of(object)).list(PermissionCatalog.ScopeType.TENANT).getFirst();
        var provider=root;
        assertThatThrownBy(()->new DataAccessCatalog(List.of(provider,provider))).isInstanceOf(IllegalStateException.class);
        var invalid=new DataAccessDeclaration.Descriptor(root.objectType(),1,root.scope(),root.resources(),List.of(new DataAccessDeclaration.Operation("BusinessPartner.list","masterdata.partner:read",Set.of(DataScopeAuthorization.ScopeType.SELF))));
        assertThatThrownBy(()->new DataAccessCatalog(List.of(invalid))).isInstanceOf(IllegalStateException.class);
        var sql=new DataAccessDeclaration.Descriptor(root.objectType(),1,root.scope(),List.of(new DataAccessDeclaration.Resource("md_partner WHERE 1=1","","客商","Partner",List.of())),root.operations());
        assertThatThrownBy(()->new DataAccessCatalog(List.of(sql))).isInstanceOf(IllegalStateException.class);
    }
    /** 统一对象操作是唯一来源；目录投影不允许再为同一客商注册独立数据提供者。 */
    @Test void unifiedObjectOwnsIdentityOperationsAndDataProjection(){
        var object=new BusinessPartnerDeclaration().descriptor();
        var projection=DataAccessCatalog.fromObjects(List.of(()->object)).list(PermissionCatalog.ScopeType.TENANT).getFirst();
        assertThat(projection.objectType()).isEqualTo(object.objectType());
        assertThat(projection.version()).isEqualTo(object.version());
        assertThat(projection.operations()).hasSize(4);
        for(var operation:object.operations())assertThat(projection.operations()).anySatisfy(value->{assertThat(value.operationId()).isEqualTo(operation.operationId());assertThat(value.permissionCode()).isEqualTo(operation.permissionCode());});
        assertThatThrownBy(()->DataAccessCatalog.fromObjects(List.of(()->object,()->object))).isInstanceOf(IllegalStateException.class);
        var invalid=new com.appnexus.contracts.businessobject.BusinessObjects.DataAccess("TENANT",object.dataAccess().resources(),Map.of("unknown",Set.of("ALL")));
        assertThatThrownBy(()->new com.appnexus.contracts.businessobject.BusinessObjects.Descriptor(object.objectType(),object.nameZh(),object.nameEn(),object.module(),object.version(),object.category(),object.fields(),object.operations(),invalid)).isInstanceOf(IllegalArgumentException.class).hasMessage("BUSINESS_OBJECT_DATA_OPERATION_UNDECLARED");
    }
    @Test void restrictiveFieldMergeCannotReleaseAnotherRolesRestriction(){
        assertThat(FieldSecurity.stricter(FieldSecurity.Mode.MASKED,FieldSecurity.Mode.READ_ONLY)).isEqualTo(FieldSecurity.Mode.MASKED);
        assertThat(FieldSecurity.stricter(FieldSecurity.Mode.READ_ONLY,FieldSecurity.Mode.HIDDEN)).isEqualTo(FieldSecurity.Mode.HIDDEN);
        assertThat(FieldSecurity.display(FieldSecurity.Kind.PHONE,"13800138000",FieldSecurity.Mode.READ_ONLY)).isEqualTo("13800138000");
    }
    /** 历史字段键和模式保留，自动响应绑定失配必须在启动阶段拒绝，不能漏保护。 */
    @Test void migratedFieldsAndResponseBindingsAreValidated(){
        var catalog=DataAccessCatalog.fromObjects(List.of(new com.appnexus.platform.security.AccountDeclaration(),new com.appnexus.platform.security.TenantMemberDeclaration(),new com.appnexus.platform.security.PersonDirectoryDeclaration()));
        assertThat(catalog.fields(PermissionCatalog.ScopeType.PLATFORM)).extracting(f->f.resource()+":"+f.field()).containsExactlyInAnyOrder("identity.account:email","identity.account:phoneNumber","identity.member:displayName");
        assertThat(catalog.fields(PermissionCatalog.ScopeType.TENANT)).extracting(f->f.resource()+":"+f.field()).containsExactly("organization.member:displayName");
        var executor=new com.appnexus.platform.persistence.platformscope.MyBatisFieldProtection(null,catalog);
        assertThat(executor.enforcedResources()).containsExactlyInAnyOrder("identity.account","identity.member","organization.member");
        var field=new DataAccessDeclaration.Field("missing","字段","Field",FieldSecurity.Kind.TEXT,Set.of(FieldSecurity.Mode.HIDDEN));
        var resource=new DataAccessDeclaration.Resource("test.invalid","","测试","Test",List.of(field),com.appnexus.platform.tenant.PlatformAdministration.User.class.getName());
        var invalid=new DataAccessCatalog(List.of(new DataAccessDeclaration.Descriptor("InvalidFixture",1,PermissionCatalog.ScopeType.PLATFORM,List.of(resource),List.of())));
        assertThatThrownBy(()->new com.appnexus.platform.persistence.platformscope.MyBatisFieldProtection(null,invalid)).isInstanceOf(IllegalStateException.class).hasMessageStartingWith("DATA_FIELD_RESPONSE_COMPONENT_INVALID:");
    }
}
