package com.appnexus.platform.security;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 机密客户端字典固定读取；Token范围和租户绑定由原入口逐次验证，返回最小公开DTO。 */
@Mapper
@PlatformScoped(tables={"tenant_dictionary","tenant_dictionary_item"})
public interface PartnerDictionaryMapper {
 /** @param tenant 已验证Token的固定租户UUID @param code 标准字典编码 @param size 每页1–100条 @param offset 从0开始偏移 @return 启用项业务值和名称，不返回内部审计 */
 @Select("SELECT i.code,i.item_value value,i.name FROM tenant_dictionary d JOIN tenant_dictionary_item i ON i.dictionary_id=d.id AND i.tenant_id=d.tenant_id WHERE d.tenant_id=UUID_TO_BIN(#{tenant}) AND i.tenant_id=UUID_TO_BIN(#{tenant}) AND d.code=#{code} AND d.status='ENABLED' AND d.deleted_at IS NULL AND i.status='ENABLED' AND i.deleted_at IS NULL ORDER BY i.sort_order,i.code LIMIT #{size} OFFSET #{offset}")
 List<Map<String,Object>> items(@Param("tenant") String tenant,@Param("code") String code,@Param("size") int size,@Param("offset") long offset);
 /** @param tenant 与列表同一Token租户 @param code 与列表同一字典 @return 相同租户及状态谓词下总数 */
 @Select("SELECT COUNT(*) FROM tenant_dictionary d JOIN tenant_dictionary_item i ON i.dictionary_id=d.id AND i.tenant_id=d.tenant_id WHERE d.tenant_id=UUID_TO_BIN(#{tenant}) AND i.tenant_id=UUID_TO_BIN(#{tenant}) AND d.code=#{code} AND d.status='ENABLED' AND d.deleted_at IS NULL AND i.status='ENABLED' AND i.deleted_at IS NULL") long count(@Param("tenant") String tenant,@Param("code") String code);
}
