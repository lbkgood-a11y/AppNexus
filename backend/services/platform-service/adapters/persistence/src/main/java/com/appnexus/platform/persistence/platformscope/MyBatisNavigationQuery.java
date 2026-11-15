package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.configuration.*;
import com.appnexus.platform.tenant.TenantAccess;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 导航读取与现有权限执行器共用权威源；不把有效权限解释或客户端角色当作安全依据。 */
@Service
public class MyBatisNavigationQuery implements NavigationQuery {
    private final MenuMapper menus;
    private final RoleMapper roles;
    private final AccessAuthorization authorization;
    private final TenantAccess tenants;
    private final PageOperationCatalog operationCatalog;

    public MyBatisNavigationQuery(MenuMapper menus,RoleMapper roles,AccessAuthorization authorization,TenantAccess tenants,PageOperationCatalog operationCatalog) {
        this.menus=menus;this.roles=roles;this.authorization=authorization;this.tenants=tenants;this.operationCatalog=operationCatalog;
    }

    /** 受控映射用于授权编辑视图，未关联菜单的权限由前端保留在独立资源分组。 */
    @Override @Transactional(readOnly=true)
    public List<AuthorizationNode> authorizationCatalog(MenuManagement.Scope scope) {
        var resources=new HashMap<UUID,List<String>>();
        for(var row:menus.resources())resources.compute(UUID.fromString((String)row.get("menu_id")),(id,current)->{
            var values=current==null?new ArrayList<String>():new ArrayList<>(current);values.add((String)row.get("resource_code"));return List.copyOf(values);});
        return menus.list().stream().map(MyBatisMenuManagement::menu)
            .filter(item -> item.scope()==scope&&item.type()!=MenuManagement.Type.BUTTON)
            .map(item -> new AuthorizationNode(item.id(),item.parentId(),item.titleZh(),item.titleEn(),item.sortOrder(),
                resources.getOrDefault(item.id(),List.of()),item.permission(),operationCatalog.permissions(item.component()))).toList();
    }

    @Override @Transactional(readOnly=true)
    public Snapshot current(UUID account,UUID tenant,UUID member,long version) {
        if(account==null||!menus.activeAccount(account.toString())||(tenant==null)!=(member==null)||version<0)throw new SecurityException("Verified navigation context required");
        if(tenant!=null&&tenants.available(account).stream().noneMatch(item -> item.tenantId().equals(tenant)&&item.memberId().equals(member)))throw new SecurityException("Invalid navigation membership");
        boolean platform=menus.administrator(account.toString());
        boolean tenantAdmin=tenant!=null&&roles.tenantAdministrator(account.toString(),tenant.toString(),member.toString());
        var rows=menus.list().stream().map(MyBatisMenuManagement::menu).toList();
        var byId=new HashMap<UUID,MenuManagement.Menu>();rows.forEach(item -> byId.put(item.id(),item));
        var allowed=new HashSet<UUID>();
        for(var item:rows) {
            if(reason(item,byId,account,tenant,member,platform,tenantAdmin)!=null)continue;
            allowed.add(item.id());
            var parent=item.parentId();
            while(parent!=null){allowed.add(parent);parent=byId.get(parent).parentId();}
        }
        var ordered=rows.stream().filter(item -> allowed.contains(item.id()))
                .sorted(Comparator.comparingInt(MenuManagement.Menu::sortOrder).thenComparing(MenuManagement.Menu::code).thenComparing(MenuManagement.Menu::id)).toList();
        return new Snapshot(tenant,version,ordered);
    }

    /** 运行时导航与诊断复用同一准入规则，不通过角色解释结果代替真实权限判定。 */
    private String reason(MenuManagement.Menu item,Map<UUID,MenuManagement.Menu> byId,UUID account,UUID tenant,UUID member,boolean platform,boolean tenantAdmin){
        if(item.type()==MenuManagement.Type.BUTTON||item.type()==MenuManagement.Type.DIRECTORY)return "NOT_PAGE_ENTRY";
        if(!item.enabled())return "MENU_DISABLED";
        if(!ancestorsEnabled(item,byId))return "ANCESTOR_DISABLED_OR_INVALID";
        if(item.scope()==MenuManagement.Scope.PLATFORM&&!platform)return "PLATFORM_IDENTITY_REQUIRED";
        if(item.scope()==MenuManagement.Scope.TENANT&&tenant==null)return "TENANT_REQUIRED";
        var subject=item.scope()==MenuManagement.Scope.TENANT?AccessAuthorization.Subject.tenant(account,tenant,member):AccessAuthorization.Subject.platform(account);
        if(item.permission()!=null&&!authorization.hasPermission(subject,item.permission()))return "PAGE_PERMISSION_MISSING";
        if(item.type()==MenuManagement.Type.MENU){var page=NavigationPages.find(item.component());
            if(page==null||!page.path().equals(item.path())||page.scope()!=item.scope()||!Objects.equals(page.permission(),item.permission()))return "PAGE_REGISTRATION_MISMATCH";
            if(page.gate()==NavigationPages.Gate.PLATFORM_ADMIN&&!platform)return "PLATFORM_IDENTITY_REQUIRED";
            if(page.gate()==NavigationPages.Gate.TENANT_ADMIN&&!tenantAdmin)return "TENANT_ADMIN_REQUIRED";
        }else if(!MyBatisMenuManagement.safeExternal(item.externalUrl()))return "EXTERNAL_URL_INVALID";
        return null;
    }
    /** 隐藏祖先不取消路由，但使菜单不可见；循环等问题已由准入规则拒绝。 */
    private boolean visible(MenuManagement.Menu item,Map<UUID,MenuManagement.Menu> byId){
        var seen=new HashSet<UUID>();for(var node=item;node!=null;node=node.parentId()==null?null:byId.get(node.parentId()))if(!node.visible()||!seen.add(node.id()))return false;return true;
    }
    @Override @Transactional(readOnly=true)
    public List<PageCheck> inspectMember(UUID tenant,UUID member){return inspect(accountForMember(tenant,member),tenant,member);}
    @Override public UUID accountForMember(UUID tenant,UUID member){var account=menus.memberAccount(tenant.toString(),member.toString());if(account==null)throw new SecurityException("Unknown managed member");return UUID.fromString(account);}
    @Override @Transactional(readOnly=true)
    public List<PageCheck> inspect(UUID account,UUID tenant,UUID member){
        boolean active=account!=null&&menus.activeAccount(account.toString());
        boolean membership=tenant==null?member==null:active&&tenants.available(account).stream().anyMatch(value->value.tenantId().equals(tenant)&&value.memberId().equals(member));
        boolean platform=active&&menus.administrator(account.toString()),tenantAdmin=membership&&tenant!=null&&roles.tenantAdministrator(account.toString(),tenant.toString(),member.toString());
        var rows=menus.list().stream().map(MyBatisMenuManagement::menu).toList();var byId=new HashMap<UUID,MenuManagement.Menu>();rows.forEach(item->byId.put(item.id(),item));
        return NavigationPages.all().stream().filter(page->tenant==null?page.scope()==MenuManagement.Scope.PLATFORM:page.scope()==MenuManagement.Scope.TENANT).map(page->{
            var entries=rows.stream().filter(item->page.key().equals(item.component())).toList();var reasons=new LinkedHashSet<String>();boolean accessible=false,shown=false;
            if(!active)reasons.add("ACCOUNT_DISABLED");else if(!membership)reasons.add("MEMBERSHIP_INACTIVE");else if(entries.isEmpty())reasons.add("MENU_NOT_CONFIGURED");
            else for(var item:entries){String blocked=reason(item,byId,account,tenant,member,platform,tenantAdmin);if(blocked!=null)reasons.add(blocked);else{accessible=true;if(visible(item,byId))shown=true;else reasons.add("MENU_OR_ANCESTOR_HIDDEN");}}
            if(shown)reasons.clear();return new PageCheck(page.key(),page.path(),accessible,shown,List.copyOf(reasons));
        }).toList();
    }

    /** 缺失父级、循环、禁用祖先和跨域祖先均拒绝；隐藏祖先只影响前端展示，不取消合法隐藏路由。 */
    private boolean ancestorsEnabled(MenuManagement.Menu item,Map<UUID,MenuManagement.Menu> items) {
        var visited=new HashSet<UUID>();var node=item;
        while(node!=null) {
            if(!node.enabled()||node.scope()!=item.scope()||!visited.add(node.id()))return false;
            if(node.parentId()==null)return true;
            node=items.get(node.parentId());
            if(node==null||node.type()!=MenuManagement.Type.DIRECTORY)return false;
        }
        return false;
    }
}
