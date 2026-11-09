package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.configuration.MenuManagement;
import com.appnexus.platform.configuration.NavigationPages;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台菜单用例实现；校验、乐观锁写入和审计在同一事务提交。 */
@Service
public class MyBatisMenuManagement implements MenuManagement {
    private final MenuMapper mapper;
    public MyBatisMenuManagement(MenuMapper mapper) { this.mapper=mapper; }

    @Override public List<Menu> list(UUID actor) {
        require(actor);
        return mapper.list().stream().map(MyBatisMenuManagement::menu).toList();
    }

    @Override @Transactional public Menu create(UUID actor,Command command) {
        lock(actor);lockCatalog();validate(command,null);
        UUID id=com.fasterxml.uuid.Generators.timeBasedEpochGenerator().generate();
        mapper.create(id.toString(),text(command.parentId()),command.type().name(),command.code().trim(),command.titleZh().trim(),command.titleEn().trim(),blank(command.path()),blank(command.component()),blank(command.icon()),blank(command.permission()),command.sortOrder(),command.visible(),command.enabled(),command.scope().name(),blank(command.externalUrl()));
        mapper.audit(actor.toString(),"MENU_CREATED",id.toString(),0);
        return menu(Objects.requireNonNull(mapper.find(id.toString())));
    }

    @Override @Transactional public Menu update(UUID actor,UUID id,long expectedVersion,Command command) {
        lock(actor);lockCatalog();Objects.requireNonNull(id);if(expectedVersion<0)throw new IllegalArgumentException("Invalid version");
        validate(command,id);
        if(mapper.update(id.toString(),expectedVersion,text(command.parentId()),command.type().name(),command.code().trim(),command.titleZh().trim(),command.titleEn().trim(),blank(command.path()),blank(command.component()),blank(command.icon()),blank(command.permission()),command.sortOrder(),command.visible(),command.enabled(),command.scope().name(),blank(command.externalUrl()))!=1)
            throw new ConcurrentModificationException("Menu changed");
        mapper.audit(actor.toString(),"MENU_UPDATED",id.toString(),expectedVersion+1);
        return menu(Objects.requireNonNull(mapper.find(id.toString())));
    }

    @Override @Transactional public void delete(UUID actor,UUID id,long expectedVersion) {
        lock(actor);lockCatalog();Objects.requireNonNull(id);if(expectedVersion<0)throw new IllegalArgumentException("Invalid version");
        if(mapper.children(id.toString())>0)throw new IllegalArgumentException("Menu has children");
        if(mapper.delete(id.toString(),expectedVersion)!=1)throw new ConcurrentModificationException("Menu changed");
        mapper.audit(actor.toString(),"MENU_DELETED",id.toString(),expectedVersion);
    }

    /** 约束由服务端最终执行；BUTTON 不携带路由，父级不能是 BUTTON，移动时拒绝形成环。 */
    private void validate(Command command,UUID current) {
        Objects.requireNonNull(command);Objects.requireNonNull(command.type());
        if(command.code()==null||!command.code().matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}"))throw new IllegalArgumentException("Invalid code");
        required(command.titleZh(),100);required(command.titleEn(),100);
        limited(command.path(),200);limited(command.component(),200);limited(command.icon(),100);limited(command.permission(),100);
        if(command.sortOrder()<0||command.sortOrder()>9999)throw new IllegalArgumentException("Invalid sort order");
        if(command.type()==Type.BUTTON&&(blank(command.path())!=null||blank(command.component())!=null||blank(command.permission())==null))throw new IllegalArgumentException("Invalid button fields");
        validateEntry(command,current);
        if(current!=null&&mapper.children(current.toString())>0&&command.type()!=Type.DIRECTORY)throw new IllegalArgumentException("Only directories can have children");
        if(command.parentId()!=null){
            if(command.parentId().equals(current)||current!=null&&descendants(current).contains(command.parentId()))throw new IllegalArgumentException("Menu cycle");
            String parentType=mapper.lockParent(command.parentId().toString());
            if(!Type.DIRECTORY.name().equals(parentType))throw new IllegalArgumentException("Invalid parent");
            var parent=menu(mapper.find(command.parentId().toString()));
            if(parent.scope()!=command.scope())throw new IllegalArgumentException("Parent scope mismatch");
        }
    }
    /** 页面必须匹配构建契约；外链不嵌入第三方页面，禁止脚本、用户凭据和控制字符。 */
    private void validateEntry(Command command,UUID current) {
        if(command.type()==Type.MENU) {
            var page=NavigationPages.find(blank(command.component()));
            if(page==null||!page.path().equals(blank(command.path()))||page.scope()!=command.scope()
                    ||!Objects.equals(page.permission(),blank(command.permission())))throw new IllegalArgumentException("Unregistered page");
            boolean duplicate=mapper.list().stream().anyMatch(row -> !Objects.equals(uuid(row,"id"),current)
                    && Type.MENU.name().equals(row.get("type")) && page.path().equals(row.get("route_path")));
            if(duplicate)throw new IllegalArgumentException("Duplicate page route");
        } else if(command.type()==Type.DIRECTORY&&(blank(command.component())!=null||blank(command.permission())!=null||blank(command.path())!=null)) {
            throw new IllegalArgumentException("Directory cannot grant access");
        }
        if(command.type()==Type.EXTERNAL) {
            if(blank(command.path())!=null||blank(command.component())!=null||!safeExternal(command.externalUrl()))throw new IllegalArgumentException("Invalid external entry");
        } else if(blank(command.externalUrl())!=null)throw new IllegalArgumentException("Unexpected external address");
        if(blank(command.permission())!=null&&command.type()!=Type.MENU
                &&(command.scope()==Scope.AUTHENTICATED||!mapper.permissionExists(command.scope().name(),command.permission().trim())))throw new IllegalArgumentException("Unknown permission");
        if(current!=null&&mapper.list().stream().anyMatch(row -> current.equals(uuid(row,"parent_id"))
                &&!command.scope().name().equals(row.get("scope_type"))))throw new IllegalArgumentException("Child scope mismatch");
    }
    /** 地址仅用于浏览器跳转；不服务端抓取，不支持 iframe 或单点登录凭据拼接。 */
    static boolean safeExternal(String value) {
        if(value==null||value.length()>2000||!value.equals(value.trim())||value.chars().anyMatch(c -> c<=32||c==127||c=='\\'))return false;
        try {
            var uri=new java.net.URI(value);
            return ("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))&&uri.getHost()!=null&&uri.getRawUserInfo()==null;
        }catch(java.net.URISyntaxException invalid){return false;}
    }
    private void require(UUID actor){if(actor==null||!mapper.administrator(actor.toString()))throw new SecurityException("Platform authority required");}
    private void lock(UUID actor){if(actor==null||mapper.lockAdministrator(actor.toString())==null)throw new SecurityException("Platform authority required");}
    private void lockCatalog(){if(mapper.lockCatalog()==null)throw new IllegalStateException("Platform menu lock unavailable");}
    private Set<UUID> descendants(UUID current){
        var rows=mapper.list();var result=new HashSet<UUID>();boolean changed=true;
        while(changed){changed=false;for(var row:rows){UUID id=uuid(row,"id"),parent=uuid(row,"parent_id");if(parent!=null&&(parent.equals(current)||result.contains(parent))&&result.add(id))changed=true;}}
        return result;
    }
    private void required(String value,int max){if(value==null||value.isBlank()||value.length()>max)throw new IllegalArgumentException("Required text invalid");}
    private void limited(String value,int max){if(value!=null&&value.length()>max)throw new IllegalArgumentException("Text too long");}
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    private String text(UUID value){return value==null?null:value.toString();}
    static Menu menu(Map<String,Object> row){return new Menu(uuid(row,"id"),uuid(row,"parent_id"),Type.valueOf((String)row.get("type")),(String)row.get("code"),(String)row.get("title_zh"),(String)row.get("title_en"),(String)row.get("route_path"),(String)row.get("component_key"),(String)row.get("icon"),(String)row.get("permission_code"),((Number)row.get("sort_order")).intValue(),bool(row.get("visible")),bool(row.get("enabled")),((Number)row.get("version")).longValue(),Scope.valueOf((String)row.get("scope_type")),(String)row.get("external_url"));}
    private static UUID uuid(Map<String,Object> row,String key){Object value=row.get(key);return value==null?null:UUID.fromString((String)value);}
    private static boolean bool(Object value){return Boolean.TRUE.equals(value)||value instanceof Number n&&n.intValue()!=0;}
}
