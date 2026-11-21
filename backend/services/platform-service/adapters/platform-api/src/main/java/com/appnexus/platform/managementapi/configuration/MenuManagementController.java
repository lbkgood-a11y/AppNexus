package com.appnexus.platform.managementapi.configuration;
import com.appnexus.platform.security.*;

import com.appnexus.platform.configuration.MenuManagement;
import com.appnexus.platform.configuration.NavigationPages;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 平台菜单管理 BFF；前端角色仅控制入口可见性，每个请求仍由用例复核平台管理员权威源。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.PLATFORM_ADMINISTRATION)
@RequestMapping("/bff/platform/menus")
public class MenuManagementController {
    private final MenuManagement service;
    private final com.appnexus.platform.configuration.PageOperationCatalog operationCatalog;
    private final com.appnexus.platform.authorization.AccessAuthorization authorization;
    public MenuManagementController(MenuManagement service,com.appnexus.platform.authorization.AccessAuthorization authorization,com.appnexus.platform.configuration.PageOperationCatalog operationCatalog){this.service=service;this.authorization=authorization;this.operationCatalog=operationCatalog;}


    /** 更新请求显式携带乐观锁版本，禁止静默覆盖其他管理员的修改。 */
    public record Update(long version,UUID parentId,MenuManagement.Type type,String code,String titleZh,String titleEn,String path,String component,String icon,String permission,int sortOrder,boolean visible,boolean enabled,MenuManagement.Scope scope,String externalUrl){
        MenuManagement.Command command(){return new MenuManagement.Command(parentId,type,code,titleZh,titleEn,path,component,icon,permission,sortOrder,visible,enabled,scope,externalUrl);}
    }
    /** 创建请求不接受客户端节点 ID 或版本，标识由服务端生成。 */
    public record Create(UUID parentId,MenuManagement.Type type,String code,String titleZh,String titleEn,String path,String component,String icon,String permission,int sortOrder,boolean visible,boolean enabled,MenuManagement.Scope scope,String externalUrl){
        MenuManagement.Command command(){return new MenuManagement.Command(parentId,type,code,titleZh,titleEn,path,component,icon,permission,sortOrder,visible,enabled,scope,externalUrl);}
    }
    /**
     * 构建内功能快照；操作码直接来自页面登记，不推断按钮名称或租户授权状态。
     * @param page 代码登记的页面及最低访问约束
     * @param operations 该页面明确登记的操作权限码，多个按钮可能共用同一权限
     */
    public record Feature(NavigationPages.Page page,List<String> operations) {}

    /**
     * 返回只读功能目录，复用菜单读取授权并复核平台身份；不接受租户或账号身份输入。
     * @param authentication 服务端认证会话，不由客户端指定
     * @return 无缓存的构建内页面与操作快照，不代表任何租户已获授权
     */

 @com.appnexus.contracts.operation.OperationBinding(id="NavigationMenu.features", reference=true)
 @EndpointOperation("configuration.menu:read") @GetMapping("/features")
    ResponseEntity<List<Feature>> features(Authentication authentication){
        return result(()->{service.list(actor(authentication));var pages=new ArrayList<>(NavigationPages.all());
            // 个人中心属于固定自助路由，仅加入功能目录，不加入可配置的菜单页面选择目录。
            pages.add(new NavigationPages.Page("account/profile","/account/profile","个人中心","Account profile",MenuManagement.Scope.AUTHENTICATED,null,NavigationPages.Gate.PERMISSION));
            return pages.stream().map(page->new Feature(page,operationCatalog.permissions(page.key()))).toList();});
    }

    /** 页面选择目录复用菜单管理授权，不允许通过输入路径注册任意组件。 */
    @com.appnexus.contracts.operation.OperationBinding(id="NavigationMenu.pageOptions", reference=true)
 @EndpointOperation("configuration.menu:read") @GetMapping("/pages")
    ResponseEntity<List<NavigationPages.Page>> pages(Authentication authentication){return result(()->{service.list(actor(authentication));return NavigationPages.all();});}
    /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
    @com.appnexus.contracts.operation.OperationBinding(id="NavigationMenu.list", reference=true)
 @EndpointOperation("configuration.menu:read") @com.appnexus.contracts.operation.RecordAuditSource(table="platform_menu",tenant=false)
 @GetMapping
    ResponseEntity<List<MenuManagement.Menu>> list(Authentication authentication){return result(()->service.list(actor(authentication)));}
    /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
    @com.appnexus.contracts.operation.OperationBinding(id="NavigationMenu.create", reference=true)
 @EndpointOperation("configuration.menu:create") @PostMapping
    ResponseEntity<MenuManagement.Menu> create(Authentication authentication,@RequestBody Create input){return result(()->service.create(actor(authentication),Objects.requireNonNull(input).command()));}
    /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
    @com.appnexus.contracts.operation.OperationBinding(id="NavigationMenu.update", reference=true)
 @EndpointOperation("configuration.menu:update") @PutMapping("/{id}")
    ResponseEntity<MenuManagement.Menu> update(Authentication authentication,@PathVariable UUID id,@RequestBody Update input){return result(()->service.update(actor(authentication),id,input.version(),input.command()));}
    /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
    @com.appnexus.contracts.operation.OperationBinding(id="NavigationMenu.delete", reference=true)
 @EndpointOperation("configuration.menu:delete") @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(Authentication authentication,@PathVariable UUID id,@RequestParam long version){return result(()->{service.delete(actor(authentication),id,version);return null;});}
    private UUID actor(Authentication authentication){return UUID.fromString(authentication.getName());}
    /** 错误使用稳定业务码；冲突不返回数据库约束名、SQL 或完整请求正文。 */
    private <T> ResponseEntity<T> result(Supplier<T> action){
        try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(action.get());}
        catch(SecurityException denied){throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"PLATFORM_DENIED","无平台管理权限");}
        catch(ConcurrentModificationException changed){throw AccountSettingsController.problem(HttpStatus.CONFLICT,"MENU_CHANGED","菜单已被其他管理员修改，请刷新后重试");}
        catch(org.springframework.dao.DuplicateKeyException duplicate){throw AccountSettingsController.problem(HttpStatus.CONFLICT,"MENU_CODE_CONFLICT","菜单标识已存在");}
        catch(org.springframework.dao.DataIntegrityViolationException conflict){throw AccountSettingsController.problem(HttpStatus.CONFLICT,"MENU_CONFLICT","菜单仍被引用或数据约束已变化");}
        catch(IllegalArgumentException|NullPointerException invalid){throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_MENU_INPUT","菜单参数或层级约束不满足要求");}
    }
}
