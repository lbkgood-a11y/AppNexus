package com.appnexus.platform.appapi.configuration;
import com.appnexus.platform.security.*;

import com.appnexus.platform.configuration.DictionaryManagement;
import com.appnexus.platform.configuration.DictionaryManagementException;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 租户字典 HTTP 入口；租户和成员只取自服务端验证上下文，禁止客户端指定。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.TENANT_BUSINESS)
@RequestMapping("/api/configuration/dictionaries")
public class DictionaryManagementController {
 private final DictionaryManagement dictionaries;
 private final com.appnexus.platform.authorization.AccessAuthorization authorization;
 public DictionaryManagementController(DictionaryManagement dictionaries,com.appnexus.platform.authorization.AccessAuthorization authorization){this.dictionaries=dictionaries;this.authorization=authorization;}
 /** 创建字典请求；编码在创建后不可修改。 */
 public record DictionaryCreate(String code,String name,String description,int sortOrder){DictionaryManagement.CreateDictionary command(){return new DictionaryManagement.CreateDictionary(code,name,description,sortOrder);}}
 /** 编辑字典请求，version 用于拒绝静默覆盖。 */
 public record DictionaryUpdate(String name,String description,DictionaryManagement.Status status,int sortOrder,long version){DictionaryManagement.UpdateDictionary command(){return new DictionaryManagement.UpdateDictionary(name,description,status,sortOrder,version);}}
 /** 创建字典项请求；value 是写入业务数据的稳定值。 */
 public record ItemCreate(UUID parentId,String code,String value,String name,String description,int sortOrder){DictionaryManagement.CreateItem command(){return new DictionaryManagement.CreateItem(parentId,code,value,name,description,sortOrder);}}
 /** 编辑字典项请求；编码和值不接受修改。 */
 public record ItemUpdate(UUID parentId,String name,String description,DictionaryManagement.Status status,int sortOrder,long version){DictionaryManagement.UpdateItem command(){return new DictionaryManagement.UpdateItem(parentId,name,description,status,sortOrder,version);}}


 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.BusinessObjectType(code="BusinessDictionary",nameZh="业务字典",nameEn="Business Dictionary",category=com.appnexus.contracts.operation.BusinessObjectType.Category.MASTER_DATA,module="configuration")
 @com.appnexus.contracts.operation.OperationBinding(id="BusinessDictionary.list",objectType="BusinessDictionary",operationCode="list",pages={"configuration/dictionaries"},nameZh="业务字典：查看列表",nameEn="Read dictionaries and items",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("configuration.dictionary:read") @com.appnexus.contracts.operation.RecordAuditSource(table="tenant_dictionary")
 @GetMapping ResponseEntity<List<DictionaryManagement.Dictionary>> list(Authentication a,HttpServletRequest r){return ok(()->dictionaries.dictionaries(context(a,r)));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="BusinessDictionary.create",objectType="BusinessDictionary",operationCode="create",pages={"configuration/dictionaries"},nameZh="业务字典：新增",nameEn="Create dictionaries",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("configuration.dictionary:create") @PostMapping ResponseEntity<DictionaryManagement.Dictionary> create(Authentication a,HttpServletRequest r,@RequestBody DictionaryCreate input){return ok(()->dictionaries.createDictionary(context(a,r),Objects.requireNonNull(input).command()));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="BusinessDictionary.update",objectType="BusinessDictionary",operationCode="update",pages={"configuration/dictionaries"},nameZh="业务字典：修改",nameEn="Update dictionaries",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("configuration.dictionary:update") @PutMapping("/{dictionaryId}") ResponseEntity<DictionaryManagement.Dictionary> update(Authentication a,HttpServletRequest r,@PathVariable UUID dictionaryId,@RequestBody DictionaryUpdate input){return ok(()->dictionaries.updateDictionary(context(a,r),dictionaryId,Objects.requireNonNull(input).command()));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="BusinessDictionary.delete",objectType="BusinessDictionary",operationCode="delete",pages={"configuration/dictionaries"},nameZh="业务字典：删除",nameEn="Delete dictionaries",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("configuration.dictionary:delete") @DeleteMapping("/{dictionaryId}") ResponseEntity<Void> delete(Authentication a,HttpServletRequest r,@PathVariable UUID dictionaryId,@RequestParam long version){return empty(()->dictionaries.deleteDictionary(context(a,r),dictionaryId,version));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="BusinessDictionary.items",objectType="BusinessDictionary",operationCode="items",pages={"configuration/dictionaries"},nameZh="业务字典：查看字典项",nameEn="Read dictionaries and items",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("configuration.dictionary:read") @com.appnexus.contracts.operation.RecordAuditSource(table="tenant_dictionary_item")
 @GetMapping("/{dictionaryId}/items") ResponseEntity<List<DictionaryManagement.Item>> items(Authentication a,HttpServletRequest r,@PathVariable UUID dictionaryId){return ok(()->dictionaries.items(context(a,r),dictionaryId));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="BusinessDictionary.lookup",objectType="BusinessDictionary",operationCode="lookup",pages={"configuration/dictionaries"},nameZh="业务字典：按编码查询",nameEn="Read dictionaries and items",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("configuration.dictionary:read") @GetMapping("/lookup/{code}") ResponseEntity<List<DictionaryManagement.Item>> lookup(Authentication a,HttpServletRequest r,@PathVariable String code){return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(dictionaries.enabledItems(context(a,r),code));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.BusinessObjectType(code="DictionaryItem",nameZh="字典项",nameEn="Dictionary Item",category=com.appnexus.contracts.operation.BusinessObjectType.Category.MASTER_DATA,module="configuration")
 @com.appnexus.contracts.operation.OperationBinding(id="DictionaryItem.create",objectType="DictionaryItem",operationCode="create",pages={"configuration/dictionaries"},nameZh="字典项：新增",nameEn="Create dictionary items",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("configuration.dictionary:item_create") @PostMapping("/{dictionaryId}/items") ResponseEntity<DictionaryManagement.Item> createItem(Authentication a,HttpServletRequest r,@PathVariable UUID dictionaryId,@RequestBody ItemCreate input){return ok(()->dictionaries.createItem(context(a,r),dictionaryId,Objects.requireNonNull(input).command()));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="DictionaryItem.update",objectType="DictionaryItem",operationCode="update",pages={"configuration/dictionaries"},nameZh="字典项：修改",nameEn="Update dictionary items",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("configuration.dictionary:item_update") @PutMapping("/{dictionaryId}/items/{itemId}") ResponseEntity<DictionaryManagement.Item> updateItem(Authentication a,HttpServletRequest r,@PathVariable UUID dictionaryId,@PathVariable UUID itemId,@RequestBody ItemUpdate input){return ok(()->dictionaries.updateItem(context(a,r),dictionaryId,itemId,Objects.requireNonNull(input).command()));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="DictionaryItem.delete",objectType="DictionaryItem",operationCode="delete",pages={"configuration/dictionaries"},nameZh="字典项：删除",nameEn="Delete dictionary items",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("configuration.dictionary:item_delete") @DeleteMapping("/{dictionaryId}/items/{itemId}") ResponseEntity<Void> deleteItem(Authentication a,HttpServletRequest r,@PathVariable UUID dictionaryId,@PathVariable UUID itemId,@RequestParam long version){return empty(()->dictionaries.deleteItem(context(a,r),dictionaryId,itemId,version));}

 /** 构造可信上下文；缺少租户选择时返回稳定冲突码而不是查询任意租户。 */
 private DictionaryManagement.Context context(Authentication a,HttpServletRequest request){if(!(request.getAttribute(TenantExecutionFilter.ATTRIBUTE) instanceof VerifiedTenantContext c))throw AccountSettingsController.problem(HttpStatus.CONFLICT,"TENANT_REQUIRED","请选择有效租户");return new DictionaryManagement.Context(UUID.fromString(a.getName()),c.tenantId(),c.memberId());}
 private <T> ResponseEntity<T> ok(Supplier<T> action){return result(()->ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(action.get()));}
 private ResponseEntity<Void> empty(Runnable action){return result(()->{action.run();return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();});}
 /** 错误响应隐藏 SQL 和跨租户数据存在性，仅暴露稳定业务码。 */
 private <T> ResponseEntity<T> result(Supplier<ResponseEntity<T>> action){try{return action.get();}catch(SecurityException denied){throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"DICTIONARY_MANAGEMENT_DENIED","无字典管理权限");}catch(DictionaryManagementException business){throw AccountSettingsController.problem(HttpStatus.CONFLICT,business.code(),business.getMessage());}catch(DuplicateKeyException duplicate){throw AccountSettingsController.problem(HttpStatus.CONFLICT,"DICTIONARY_CODE_CONFLICT","字典编码或值已存在");}catch(DataIntegrityViolationException conflict){throw AccountSettingsController.problem(HttpStatus.CONFLICT,"DICTIONARY_DATA_CONFLICT","字典数据约束已变化");}catch(IllegalArgumentException|NullPointerException invalid){throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_DICTIONARY_INPUT","字典参数不符合要求");}}
}
