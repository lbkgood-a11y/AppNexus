package com.appnexus.platform.security;

import com.appnexus.platform.authorization.RoleManagement;
import com.appnexus.platform.authorization.RoleManagementException;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 角色管理 BFF；平台、平台代管租户和租户自助入口共享契约，但分别构造可信管理上下文。
 * 前端路由可见性不作为授权依据，每个请求均由角色用例复核权威数据。
 */
@RestController
public class RoleManagementController {
    private final RoleManagement roles;
    public RoleManagementController(RoleManagement roles){this.roles=roles;}

    /** 创建请求不接受管理域、租户、角色类型、系统标识或状态。 */
    public record Create(String code,String name,String description,int sortOrder){RoleManagement.Create command(){return new RoleManagement.Create(code,name,description,sortOrder);}}
    /** 编辑只允许展示字段，稳定编码和安全字段不能通过反序列化修改。 */
    public record Update(String name,String description,int sortOrder,long version){RoleManagement.Update command(){return new RoleManagement.Update(name,description,sortOrder,version);}}
    /** 状态变更显式携带版本；禁用原因由用例强制必填。 */
    public record StatusChange(RoleManagement.Status status,String reason,long version){}
    /** 删除要求完整编码确认和原因，不依赖不稳定的 DELETE 请求体。 */
    public record Delete(String confirmationCode,String reason,long version){}

    @GetMapping("/bff/platform/roles") ResponseEntity<RoleManagement.Page> platformList(Authentication a,@RequestParam(defaultValue="") String query,@RequestParam(required=false) RoleManagement.Status status,@RequestParam(required=false) RoleManagement.RoleType roleType,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return ok(()->roles.list(platform(a),new RoleManagement.Query(query,status,roleType,page,pageSize)));}
    @PostMapping("/bff/platform/roles") ResponseEntity<RoleManagement.Role> platformCreate(Authentication a,@RequestBody Create input){return ok(()->roles.create(platform(a),Objects.requireNonNull(input).command()));}
    @PutMapping("/bff/platform/roles/{id}") ResponseEntity<RoleManagement.Role> platformUpdate(Authentication a,@PathVariable UUID id,@RequestBody Update input){return ok(()->roles.update(platform(a),id,Objects.requireNonNull(input).command()));}
    @PatchMapping("/bff/platform/roles/{id}/status") ResponseEntity<RoleManagement.Role> platformStatus(Authentication a,@PathVariable UUID id,@RequestBody StatusChange input){return ok(()->roles.changeStatus(platform(a),id,input.version(),input.status(),input.reason()));}
    @PostMapping("/bff/platform/roles/{id}/delete") ResponseEntity<Void> platformDelete(Authentication a,@PathVariable UUID id,@RequestBody Delete input){return empty(()->roles.delete(platform(a),id,input.version(),input.confirmationCode(),input.reason()));}

    @GetMapping("/bff/platform/tenants/{tenant}/roles") ResponseEntity<RoleManagement.Page> managedList(Authentication a,@PathVariable UUID tenant,@RequestParam(defaultValue="") String query,@RequestParam(required=false) RoleManagement.Status status,@RequestParam(required=false) RoleManagement.RoleType roleType,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return ok(()->roles.list(managed(a,tenant),new RoleManagement.Query(query,status,roleType,page,pageSize)));}
    @PostMapping("/bff/platform/tenants/{tenant}/roles") ResponseEntity<RoleManagement.Role> managedCreate(Authentication a,@PathVariable UUID tenant,@RequestBody Create input){return ok(()->roles.create(managed(a,tenant),Objects.requireNonNull(input).command()));}
    @PutMapping("/bff/platform/tenants/{tenant}/roles/{id}") ResponseEntity<RoleManagement.Role> managedUpdate(Authentication a,@PathVariable UUID tenant,@PathVariable UUID id,@RequestBody Update input){return ok(()->roles.update(managed(a,tenant),id,Objects.requireNonNull(input).command()));}
    @PatchMapping("/bff/platform/tenants/{tenant}/roles/{id}/status") ResponseEntity<RoleManagement.Role> managedStatus(Authentication a,@PathVariable UUID tenant,@PathVariable UUID id,@RequestBody StatusChange input){return ok(()->roles.changeStatus(managed(a,tenant),id,input.version(),input.status(),input.reason()));}
    @PostMapping("/bff/platform/tenants/{tenant}/roles/{id}/delete") ResponseEntity<Void> managedDelete(Authentication a,@PathVariable UUID tenant,@PathVariable UUID id,@RequestBody Delete input){return empty(()->roles.delete(managed(a,tenant),id,input.version(),input.confirmationCode(),input.reason()));}

    @GetMapping("/api/access/roles") ResponseEntity<RoleManagement.Page> tenantList(Authentication a,HttpServletRequest r,@RequestParam(defaultValue="") String query,@RequestParam(required=false) RoleManagement.Status status,@RequestParam(required=false) RoleManagement.RoleType roleType,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return ok(()->roles.list(tenant(a,r),new RoleManagement.Query(query,status,roleType,page,pageSize)));}
    @PostMapping("/api/access/roles") ResponseEntity<RoleManagement.Role> tenantCreate(Authentication a,HttpServletRequest r,@RequestBody Create input){return ok(()->roles.create(tenant(a,r),Objects.requireNonNull(input).command()));}
    @PutMapping("/api/access/roles/{id}") ResponseEntity<RoleManagement.Role> tenantUpdate(Authentication a,HttpServletRequest r,@PathVariable UUID id,@RequestBody Update input){return ok(()->roles.update(tenant(a,r),id,Objects.requireNonNull(input).command()));}
    @PatchMapping("/api/access/roles/{id}/status") ResponseEntity<RoleManagement.Role> tenantStatus(Authentication a,HttpServletRequest r,@PathVariable UUID id,@RequestBody StatusChange input){return ok(()->roles.changeStatus(tenant(a,r),id,input.version(),input.status(),input.reason()));}
    @PostMapping("/api/access/roles/{id}/delete") ResponseEntity<Void> tenantDelete(Authentication a,HttpServletRequest r,@PathVariable UUID id,@RequestBody Delete input){return empty(()->roles.delete(tenant(a,r),id,input.version(),input.confirmationCode(),input.reason()));}

    private RoleManagement.Context platform(Authentication a){return new RoleManagement.Context(actor(a),null,null);}
    private RoleManagement.Context managed(Authentication a,UUID tenant){return new RoleManagement.Context(actor(a),Objects.requireNonNull(tenant),null);}
    /** 当前租户及成员只来自服务端过滤器验证结果，不读取请求头或正文提供的身份。 */
    private RoleManagement.Context tenant(Authentication a,HttpServletRequest request){
        if(!(request.getAttribute(TenantExecutionFilter.ATTRIBUTE) instanceof VerifiedTenantContext c))throw AccountSettingsController.problem(HttpStatus.CONFLICT,"TENANT_REQUIRED","请选择有效租户");
        return new RoleManagement.Context(actor(a),c.tenantId(),c.memberId());
    }
    private UUID actor(Authentication a){return UUID.fromString(a.getName());}
    private <T> ResponseEntity<T> ok(Supplier<T> action){return result(()->ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(action.get()));}
    private ResponseEntity<Void> empty(Runnable action){return result(()->{action.run();return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();});}
    /** 错误响应保持稳定业务码，不暴露 SQL、索引名或其他管理域的数据存在性。 */
    private <T> ResponseEntity<T> result(Supplier<ResponseEntity<T>> action){
        try{return action.get();}
        catch(SecurityException denied){throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"ROLE_MANAGEMENT_DENIED","无角色管理权限");}
        catch(RoleManagementException business){HttpStatus status="ROLE_SCOPE_NOT_FOUND".equals(business.code())?HttpStatus.NOT_FOUND:HttpStatus.CONFLICT;throw AccountSettingsController.problem(status,business.code(),business.getMessage());}
        catch(DuplicateKeyException duplicate){throw AccountSettingsController.problem(HttpStatus.CONFLICT,"ROLE_CODE_ALREADY_EXISTS","角色编码已存在且不能复用");}
        catch(DataIntegrityViolationException conflict){throw AccountSettingsController.problem(HttpStatus.CONFLICT,"ROLE_DATA_CONFLICT","角色数据约束已变化");}
        catch(IllegalArgumentException|NullPointerException invalid){throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_ROLE_INPUT","角色参数不符合要求");}
    }
}
