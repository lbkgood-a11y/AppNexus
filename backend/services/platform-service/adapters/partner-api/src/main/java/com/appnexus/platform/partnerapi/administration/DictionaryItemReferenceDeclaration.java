package com.appnexus.platform.partnerapi.administration;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.InteractionContract;
import java.util.List;
import java.util.Map;
import com.appnexus.platform.configuration.PartnerReferences;
import org.springframework.stereotype.Component;

/** 外部字典引用的机器 API 声明；客户端和固定租户边界由集成准入及原用例执行。 */
@Component
public final class DictionaryItemReferenceDeclaration implements Provider {
    /** 机器身份必须持有的 OAuth 授权；登记与读取沿用同一个已发布权限范围。 */
    static final String REQUIRED_AUTHORITY = "SCOPE_"+PartnerReferences.REQUIRED_SCOPE;

    /** @return 由固定 Controller 执行的操作契约，空能力集合表示无公共命令入口 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource(PartnerReferences.RESOURCE, "", "外部字典引用", "Dictionary item references", List.of())), Map.of());
        return new Descriptor("DictionaryItemReference", "外部字典引用", "Dictionary item reference", "configuration", 1,
                Category.LEDGER, List.of(), List.of(
                        operation("create", "登记字典引用", "Create dictionary item reference", REQUIRED_AUTHORITY),
                        operation("read", "查询字典引用", "Read dictionary item reference", REQUIRED_AUTHORITY)), access);
    }

    private Operation operation(String code, String nameZh, String nameEn, String permission) {
        String id = "DictionaryItemReference." + code;
        var interaction = new InteractionContract(id, 1, "DictionaryItemReference", code, nameZh, nameEn,
                InteractionContract.Trigger.USER_ACTION, List.of());
        return new Operation(code, nameZh, nameEn, permission, id, null, List.of(), interaction);
    }
}
