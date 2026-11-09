package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.persistence.PlatformScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 平台菜单持久化 Mapper；所有表均为平台范围，不接受租户上下文或动态 SQL 片段。 */
@Mapper
@PlatformScoped(tables={"platform_menu","platform_menu_audit","platform_administrator","platform_administrator_bootstrap","identity_account","access_permission"})
public interface MenuMapper {
    /** 每次读请求复核权威平台身份，菜单展示角色不能作为授权依据。 */
    @Select("SELECT EXISTS(SELECT 1 FROM platform_administrator p JOIN identity_account a ON a.id=p.account_id WHERE p.account_id=UUID_TO_BIN(#{actor}) AND p.enabled=TRUE AND a.enabled=TRUE)")
    boolean administrator(String actor);
    /** 写事务锁定操作者权限，防止并发撤权后继续修改配置。 */
    @Select("SELECT BIN_TO_UUID(p.account_id) FROM platform_administrator p JOIN identity_account a ON a.id=p.account_id WHERE p.account_id=UUID_TO_BIN(#{actor}) AND p.enabled=TRUE AND a.enabled=TRUE FOR UPDATE")
    String lockAdministrator(String actor);
    /** 全局目录互斥锁使不同管理员的父级移动串行化，避免并发更新共同形成层级环。 */
    @Select("SELECT slot FROM platform_administrator_bootstrap WHERE slot=1 FOR UPDATE")
    Integer lockCatalog();
    @Select("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(parent_id) parent_id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,visible,enabled,version,scope_type,external_url FROM platform_menu ORDER BY COALESCE(BIN_TO_UUID(parent_id),''),sort_order,code,id")
    List<Map<String,Object>> list();
    @Select("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(parent_id) parent_id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,visible,enabled,version,scope_type,external_url FROM platform_menu WHERE id=UUID_TO_BIN(#{id})")
    Map<String,Object> find(String id);
    @Select("SELECT type FROM platform_menu WHERE id=UUID_TO_BIN(#{id}) FOR UPDATE")
    String lockParent(String id);
    @Select("SELECT COUNT(*) FROM platform_menu WHERE parent_id=UUID_TO_BIN(#{id})")
    int children(String id);
    @Insert("INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,visible,enabled,scope_type,external_url) VALUES(UUID_TO_BIN(#{id}),UUID_TO_BIN(#{parent}),#{type},#{code},#{titleZh},#{titleEn},#{path},#{component},#{icon},#{permission},#{sortOrder},#{visible},#{enabled},#{scope},#{externalUrl})")
    void create(@Param("id") String id,@Param("parent") String parent,@Param("type") String type,@Param("code") String code,@Param("titleZh") String titleZh,@Param("titleEn") String titleEn,@Param("path") String path,@Param("component") String component,@Param("icon") String icon,@Param("permission") String permission,@Param("sortOrder") int sortOrder,@Param("visible") boolean visible,@Param("enabled") boolean enabled,@Param("scope") String scope,@Param("externalUrl") String externalUrl);
    @Update("UPDATE platform_menu SET parent_id=UUID_TO_BIN(#{parent}),type=#{type},code=#{code},title_zh=#{titleZh},title_en=#{titleEn},route_path=#{path},component_key=#{component},icon=#{icon},permission_code=#{permission},sort_order=#{sortOrder},visible=#{visible},enabled=#{enabled},scope_type=#{scope},external_url=#{externalUrl},version=version+1 WHERE id=UUID_TO_BIN(#{id}) AND version=#{version}")
    int update(@Param("id") String id,@Param("version") long version,@Param("parent") String parent,@Param("type") String type,@Param("code") String code,@Param("titleZh") String titleZh,@Param("titleEn") String titleEn,@Param("path") String path,@Param("component") String component,@Param("icon") String icon,@Param("permission") String permission,@Param("sortOrder") int sortOrder,@Param("visible") boolean visible,@Param("enabled") boolean enabled,@Param("scope") String scope,@Param("externalUrl") String externalUrl);
    /** 当前登录账号必须启用，公开导航也不绕过账号状态。 */
    @Select("SELECT EXISTS(SELECT 1 FROM identity_account WHERE id=UUID_TO_BIN(#{actor}) AND enabled=TRUE)")
    boolean activeAccount(String actor);
    /** 入口只能引用当前管理域已启用的稳定权限码。 */
    @Select("SELECT EXISTS(SELECT 1 FROM access_permission WHERE code=#{code} AND scope_type=#{scope} AND enabled=TRUE)")
    boolean permissionExists(@Param("scope") String scope,@Param("code") String code);
    @Delete("DELETE FROM platform_menu WHERE id=UUID_TO_BIN(#{id}) AND version=#{version}")
    int delete(@Param("id") String id,@Param("version") long version);
    @Insert("INSERT INTO platform_menu_audit(actor_account_id,action,menu_id,menu_version) VALUES(UUID_TO_BIN(#{actor}),#{action},UUID_TO_BIN(#{menu}),#{version})")
    void audit(@Param("actor") String actor,@Param("action") String action,@Param("menu") String menu,@Param("version") long version);
}
