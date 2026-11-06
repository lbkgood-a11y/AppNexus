package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.configuration.MenuManagement;
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
        return mapper.list().stream().map(this::menu).toList();
    }

    @Override @Transactional public Menu create(UUID actor,Command command) {
        lock(actor);lockCatalog();validate(command,null);
        UUID id=com.fasterxml.uuid.Generators.timeBasedEpochGenerator().generate();
        mapper.create(id.toString(),text(command.parentId()),command.type().name(),command.code().trim(),command.titleZh().trim(),command.titleEn().trim(),blank(command.path()),blank(command.component()),blank(command.icon()),blank(command.permission()),command.sortOrder(),command.visible(),command.enabled());
        mapper.audit(actor.toString(),"MENU_CREATED",id.toString(),0);
        return menu(Objects.requireNonNull(mapper.find(id.toString())));
    }

    @Override @Transactional public Menu update(UUID actor,UUID id,long expectedVersion,Command command) {
        lock(actor);lockCatalog();Objects.requireNonNull(id);if(expectedVersion<0)throw new IllegalArgumentException("Invalid version");
        validate(command,id);
        if(mapper.update(id.toString(),expectedVersion,text(command.parentId()),command.type().name(),command.code().trim(),command.titleZh().trim(),command.titleEn().trim(),blank(command.path()),blank(command.component()),blank(command.icon()),blank(command.permission()),command.sortOrder(),command.visible(),command.enabled())!=1)
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
        if(command.parentId()!=null){
            if(command.parentId().equals(current)||current!=null&&descendants(current).contains(command.parentId()))throw new IllegalArgumentException("Menu cycle");
            String parentType=mapper.lockParent(command.parentId().toString());
            if(parentType==null||Type.BUTTON.name().equals(parentType))throw new IllegalArgumentException("Invalid parent");
        }
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
    private Menu menu(Map<String,Object> row){return new Menu(uuid(row,"id"),uuid(row,"parent_id"),Type.valueOf((String)row.get("type")),(String)row.get("code"),(String)row.get("title_zh"),(String)row.get("title_en"),(String)row.get("route_path"),(String)row.get("component_key"),(String)row.get("icon"),(String)row.get("permission_code"),((Number)row.get("sort_order")).intValue(),bool(row.get("visible")),bool(row.get("enabled")),((Number)row.get("version")).longValue());}
    private UUID uuid(Map<String,Object> row,String key){Object value=row.get(key);return value==null?null:UUID.fromString((String)value);}
    private boolean bool(Object value){return Boolean.TRUE.equals(value)||value instanceof Number n&&n.intValue()!=0;}
}
