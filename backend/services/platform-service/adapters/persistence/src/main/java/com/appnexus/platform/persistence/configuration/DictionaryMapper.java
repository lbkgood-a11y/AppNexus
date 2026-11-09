package com.appnexus.platform.persistence.configuration;

import com.appnexus.platform.persistence.TenantAggregateScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 字典受控 SQL；所有数据语句均显式绑定拦截器注入的已验证租户。 */
@Mapper
@TenantAggregateScoped(
 tables={"tenant_dictionary","tenant_dictionary_item","tenant_dictionary_item_reference","tenant_dictionary_audit","tenant_member","identity_account","platform_tenant","access_effective_tenant_permission"},
 statements={"lockMember","administrator","lockAdministrator","dictionaries","dictionary","lockDictionary","createDictionary","updateDictionary","deleteDictionary","dictionaryItemCount","items","item","lockItem","createItem","updateItem","deleteItem","childCount","referenceCount","audit","enabledItems"})
public interface DictionaryMapper {
 /** 锁定可信租户内有效成员，和成员角色授权替换串行；操作权限由统一判定器校验。 */
 @Select("SELECT BIN_TO_UUID(m.id) FROM tenant_member m JOIN identity_account a ON a.id=m.account_id JOIN platform_tenant t ON t.id=m.tenant_id WHERE m.tenant_id=#{__verifiedTenant} AND m.id=UUID_TO_BIN(#{member}) AND m.account_id=UUID_TO_BIN(#{actor}) AND m.enabled=TRUE AND a.enabled=TRUE AND t.enabled=TRUE FOR UPDATE")
 String lockMember(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("member") String member);
 @Select("SELECT EXISTS(SELECT 1 FROM access_effective_tenant_permission e WHERE e.tenant_id=#{__verifiedTenant} AND e.member_id=UUID_TO_BIN(#{member}) AND e.account_id=UUID_TO_BIN(#{actor}) AND e.permission_code='organization.permission:grant')")
 boolean administrator(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("member") String member);
 @Select("SELECT BIN_TO_UUID(m.id) FROM tenant_member m WHERE m.tenant_id=#{__verifiedTenant} AND m.id=UUID_TO_BIN(#{member}) AND m.account_id=UUID_TO_BIN(#{actor}) AND EXISTS(SELECT 1 FROM access_effective_tenant_permission e WHERE e.tenant_id=m.tenant_id AND e.member_id=m.id AND e.permission_code='organization.permission:grant') FOR UPDATE")
 String lockAdministrator(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("member") String member);
 @Select("SELECT BIN_TO_UUID(id) id,code,name,description,status,sort_order,version,updated_at FROM tenant_dictionary WHERE tenant_id=#{__verifiedTenant} AND deleted_at IS NULL ORDER BY sort_order,name,id")
 List<Map<String,Object>> dictionaries(@Param("__verifiedTenant") byte[] tenant);
 @Select("SELECT BIN_TO_UUID(id) id,code,name,description,status,sort_order,version,updated_at FROM tenant_dictionary WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND deleted_at IS NULL")
 Map<String,Object> dictionary(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);
 @Select("SELECT BIN_TO_UUID(id) id,code,name,description,status,sort_order,version,updated_at FROM tenant_dictionary WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND deleted_at IS NULL FOR UPDATE")
 Map<String,Object> lockDictionary(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);
 @Insert("INSERT INTO tenant_dictionary(id,tenant_id,code,name,description,status,sort_order,created_by,updated_by) VALUES(UUID_TO_BIN(#{id}),#{__verifiedTenant},#{code},#{name},#{description},'ENABLED',#{sort},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))")
 int createDictionary(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("code") String code,@Param("name") String name,@Param("description") String description,@Param("sort") int sort,@Param("actor") String actor);
 @Update("UPDATE tenant_dictionary SET name=#{name},description=#{description},status=#{status},sort_order=#{sort},updated_by=UUID_TO_BIN(#{actor}),version=version+1 WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND deleted_at IS NULL AND version=#{version}")
 int updateDictionary(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("name") String name,@Param("description") String description,@Param("status") String status,@Param("sort") int sort,@Param("version") long version,@Param("actor") String actor);
 @Update("UPDATE tenant_dictionary SET deleted_at=CURRENT_TIMESTAMP(6),deleted_by=UUID_TO_BIN(#{actor}),updated_by=UUID_TO_BIN(#{actor}),version=version+1 WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND deleted_at IS NULL AND version=#{version}")
 int deleteDictionary(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("version") long version,@Param("actor") String actor);
 @Select("SELECT COUNT(*) FROM tenant_dictionary_item WHERE tenant_id=#{__verifiedTenant} AND dictionary_id=UUID_TO_BIN(#{id}) AND deleted_at IS NULL")
 int dictionaryItemCount(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);
 @Select("SELECT BIN_TO_UUID(i.id) id,BIN_TO_UUID(i.dictionary_id) dictionary_id,BIN_TO_UUID(i.parent_id) parent_id,i.code,i.item_value,i.name,i.description,i.status,i.sort_order,i.version,i.updated_at,EXISTS(SELECT 1 FROM tenant_dictionary_item_reference r WHERE r.tenant_id=#{__verifiedTenant} AND r.item_id=i.id) referenced FROM tenant_dictionary_item i WHERE i.tenant_id=#{__verifiedTenant} AND i.dictionary_id=UUID_TO_BIN(#{dictionary}) AND i.deleted_at IS NULL ORDER BY i.sort_order,i.name,i.id")
 List<Map<String,Object>> items(@Param("__verifiedTenant") byte[] tenant,@Param("dictionary") String dictionary);
 @Select("SELECT BIN_TO_UUID(i.id) id,BIN_TO_UUID(i.dictionary_id) dictionary_id,BIN_TO_UUID(i.parent_id) parent_id,i.code,i.item_value,i.name,i.description,i.status,i.sort_order,i.version,i.updated_at,EXISTS(SELECT 1 FROM tenant_dictionary_item_reference r WHERE r.tenant_id=#{__verifiedTenant} AND r.item_id=i.id) referenced FROM tenant_dictionary_item i WHERE i.tenant_id=#{__verifiedTenant} AND i.dictionary_id=UUID_TO_BIN(#{dictionary}) AND i.id=UUID_TO_BIN(#{id}) AND i.deleted_at IS NULL")
 Map<String,Object> item(@Param("__verifiedTenant") byte[] tenant,@Param("dictionary") String dictionary,@Param("id") String id);
 @Select("SELECT BIN_TO_UUID(i.id) id,BIN_TO_UUID(i.dictionary_id) dictionary_id,BIN_TO_UUID(i.parent_id) parent_id,i.code,i.item_value,i.name,i.description,i.status,i.sort_order,i.version,i.updated_at,FALSE referenced FROM tenant_dictionary_item i WHERE i.tenant_id=#{__verifiedTenant} AND i.dictionary_id=UUID_TO_BIN(#{dictionary}) AND i.id=UUID_TO_BIN(#{id}) AND i.deleted_at IS NULL FOR UPDATE")
 Map<String,Object> lockItem(@Param("__verifiedTenant") byte[] tenant,@Param("dictionary") String dictionary,@Param("id") String id);
 @Insert("INSERT INTO tenant_dictionary_item(id,tenant_id,dictionary_id,parent_id,code,item_value,name,description,status,sort_order,created_by,updated_by) VALUES(UUID_TO_BIN(#{id}),#{__verifiedTenant},UUID_TO_BIN(#{dictionary}),UUID_TO_BIN(#{parent}),#{code},#{value},#{name},#{description},'ENABLED',#{sort},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))")
 int createItem(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("dictionary") String dictionary,@Param("parent") String parent,@Param("code") String code,@Param("value") String value,@Param("name") String name,@Param("description") String description,@Param("sort") int sort,@Param("actor") String actor);
 @Update("UPDATE tenant_dictionary_item SET parent_id=UUID_TO_BIN(#{parent}),name=#{name},description=#{description},status=#{status},sort_order=#{sort},updated_by=UUID_TO_BIN(#{actor}),version=version+1 WHERE tenant_id=#{__verifiedTenant} AND dictionary_id=UUID_TO_BIN(#{dictionary}) AND id=UUID_TO_BIN(#{id}) AND deleted_at IS NULL AND version=#{version}")
 int updateItem(@Param("__verifiedTenant") byte[] tenant,@Param("dictionary") String dictionary,@Param("id") String id,@Param("parent") String parent,@Param("name") String name,@Param("description") String description,@Param("status") String status,@Param("sort") int sort,@Param("version") long version,@Param("actor") String actor);
 @Update("UPDATE tenant_dictionary_item SET deleted_at=CURRENT_TIMESTAMP(6),deleted_by=UUID_TO_BIN(#{actor}),updated_by=UUID_TO_BIN(#{actor}),version=version+1 WHERE tenant_id=#{__verifiedTenant} AND dictionary_id=UUID_TO_BIN(#{dictionary}) AND id=UUID_TO_BIN(#{id}) AND deleted_at IS NULL AND version=#{version}")
 int deleteItem(@Param("__verifiedTenant") byte[] tenant,@Param("dictionary") String dictionary,@Param("id") String id,@Param("version") long version,@Param("actor") String actor);
 @Select("SELECT COUNT(*) FROM tenant_dictionary_item WHERE tenant_id=#{__verifiedTenant} AND dictionary_id=UUID_TO_BIN(#{dictionary}) AND parent_id=UUID_TO_BIN(#{id}) AND deleted_at IS NULL")
 int childCount(@Param("__verifiedTenant") byte[] tenant,@Param("dictionary") String dictionary,@Param("id") String id);
 @Select("SELECT COUNT(*) FROM tenant_dictionary_item_reference WHERE tenant_id=#{__verifiedTenant} AND item_id=UUID_TO_BIN(#{id})")
 int referenceCount(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);
 @Insert("INSERT INTO tenant_dictionary_audit(tenant_id,dictionary_id,item_id,actor_account_id,action) VALUES(#{__verifiedTenant},UUID_TO_BIN(#{dictionary}),UUID_TO_BIN(#{item}),UUID_TO_BIN(#{actor}),#{action})")
 int audit(@Param("__verifiedTenant") byte[] tenant,@Param("dictionary") String dictionary,@Param("item") String item,@Param("actor") String actor,@Param("action") String action);
 @Select("SELECT BIN_TO_UUID(i.id) id,BIN_TO_UUID(i.dictionary_id) dictionary_id,BIN_TO_UUID(i.parent_id) parent_id,i.code,i.item_value,i.name,i.description,i.status,i.sort_order,i.version,i.updated_at,FALSE referenced FROM tenant_dictionary_item i JOIN tenant_dictionary d ON d.tenant_id=i.tenant_id AND d.id=i.dictionary_id WHERE i.tenant_id=#{__verifiedTenant} AND d.code=#{code} AND d.status='ENABLED' AND d.deleted_at IS NULL AND i.status='ENABLED' AND i.deleted_at IS NULL ORDER BY i.sort_order,i.name,i.id")
 List<Map<String,Object>> enabledItems(@Param("__verifiedTenant") byte[] tenant,@Param("code") String code);
}
