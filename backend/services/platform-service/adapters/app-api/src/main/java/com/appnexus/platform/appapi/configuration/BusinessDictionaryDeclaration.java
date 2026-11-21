package com.appnexus.platform.appapi.configuration;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.DedicatedOperations;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 业务字典专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class BusinessDictionaryDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("configuration.dictionary", "", "业务字典", "Business dictionaries", List.of()),
                new DataResource("configuration.dictionary.item", "configuration.dictionary", "字典项", "Dictionary items", List.of())), Map.of());
        return new Descriptor("BusinessDictionary", "业务字典", "Business Dictionary", "configuration", 2, Category.MASTER_DATA, List.of(), List.of(
                operation("BusinessDictionary", "list", "业务字典：查看列表", "Read dictionaries and items", "configuration.dictionary:read",
                        new PageOperation("configuration/dictionaries", "/configuration/dictionaries", "业务字典：查看列表", "Read dictionaries and items", "GET", "/api/configuration/dictionaries")),
                operation("BusinessDictionary", "create", "业务字典：新增", "Create dictionaries", "configuration.dictionary:create",
                        new PageOperation("configuration/dictionaries", "/configuration/dictionaries", "业务字典：新增", "Create dictionaries", "POST", "/api/configuration/dictionaries")),
                operation("BusinessDictionary", "update", "业务字典：修改", "Update dictionaries", "configuration.dictionary:update",
                        new PageOperation("configuration/dictionaries", "/configuration/dictionaries", "业务字典：修改", "Update dictionaries", "PUT", "/api/configuration/dictionaries/{dictionaryId}")),
                operation("BusinessDictionary", "delete", "业务字典：删除", "Delete dictionaries", "configuration.dictionary:delete",
                        new PageOperation("configuration/dictionaries", "/configuration/dictionaries", "业务字典：删除", "Delete dictionaries", "DELETE", "/api/configuration/dictionaries/{dictionaryId}")),
                operation("BusinessDictionary", "items", "业务字典：查看字典项", "Read dictionaries and items", "configuration.dictionary:read",
                        new PageOperation("configuration/dictionaries", "/configuration/dictionaries", "业务字典：查看字典项", "Read dictionaries and items", "GET", "/api/configuration/dictionaries/{dictionaryId}/items")),
                operation("BusinessDictionary", "lookup", "业务字典：按编码查询", "Read dictionaries and items", "configuration.dictionary:read",
                        new PageOperation("configuration/dictionaries", "/configuration/dictionaries", "业务字典：按编码查询", "Read dictionaries and items", "GET", "/api/configuration/dictionaries/lookup/{code}")),
                DedicatedOperations.operation("BusinessDictionary", "createItem", "字典项：新增", "Create dictionary items", "configuration.dictionary:item_create",
                        new PageOperation("configuration/dictionaries", "/configuration/dictionaries", "字典项：新增", "Create dictionary items", "POST", "/api/configuration/dictionaries/{dictionaryId}/items")),
                DedicatedOperations.operation("BusinessDictionary", "updateItem", "字典项：修改", "Update dictionary items", "configuration.dictionary:item_update",
                        new PageOperation("configuration/dictionaries", "/configuration/dictionaries", "字典项：修改", "Update dictionary items", "PUT", "/api/configuration/dictionaries/{dictionaryId}/items/{itemId}")),
                DedicatedOperations.operation("BusinessDictionary", "deleteItem", "字典项：删除", "Delete dictionary items", "configuration.dictionary:item_delete",
                        new PageOperation("configuration/dictionaries", "/configuration/dictionaries", "字典项：删除", "Delete dictionary items", "DELETE", "/api/configuration/dictionaries/{dictionaryId}/items/{itemId}")),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("BusinessDictionary", "partnerItems", "合作方字典项", "Partner dictionary items", "SCOPE_dictionaries:read")), access);
    }
}
