package com.appnexus.platform.security;

import com.appnexus.platform.configuration.MenuManagement;
import com.appnexus.platform.configuration.NavigationPages;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 平台菜单管理 BFF；前端角色仅控制入口可见性，每个请求仍由用例复核平台管理员权威源。 */
@RestController
@RequestMapping("/bff/platform/menus")
public class MenuManagementController {
    private final MenuManagement service;
    public MenuManagementController(MenuManagement service){this.service=service;}

    /** 更新请求显式携带乐观锁版本，禁止静默覆盖其他管理员的修改。 */
    public record Update(long version,UUID parentId,MenuManagement.Type type,String code,String titleZh,String titleEn,String path,String component,String icon,String permission,int sortOrder,boolean visible,boolean enabled,MenuManagement.Scope scope,String externalUrl){
        MenuManagement.Command command(){return new MenuManagement.Command(parentId,type,code,titleZh,titleEn,path,component,icon,permission,sortOrder,visible,enabled,scope,externalUrl);}
    }
    /** 创建请求不接受客户端节点 ID 或版本，标识由服务端生成。 */
    public record Create(UUID parentId,MenuManagement.Type type,String code,String titleZh,String titleEn,String path,String component,String icon,String permission,int sortOrder,boolean visible,boolean enabled,MenuManagement.Scope scope,String externalUrl){
        MenuManagement.Command command(){return new MenuManagement.Command(parentId,type,code,titleZh,titleEn,path,component,icon,permission,sortOrder,visible,enabled,scope,externalUrl);}
    }
    /** 页面选择目录复用菜单管理授权，不允许通过输入路径注册任意组件。 */
    @GetMapping("/pages")
    ResponseEntity<List<NavigationPages.Page>> pages(Authentication authentication){return result(()->{service.list(actor(authentication));return NavigationPages.all();});}
    @GetMapping
    ResponseEntity<List<MenuManagement.Menu>> list(Authentication authentication){return result(()->service.list(actor(authentication)));}
    @PostMapping
    ResponseEntity<MenuManagement.Menu> create(Authentication authentication,@RequestBody Create input){return result(()->service.create(actor(authentication),Objects.requireNonNull(input).command()));}
    @PutMapping("/{id}")
    ResponseEntity<MenuManagement.Menu> update(Authentication authentication,@PathVariable UUID id,@RequestBody Update input){return result(()->service.update(actor(authentication),id,input.version(),input.command()));}
    @DeleteMapping("/{id}")
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
