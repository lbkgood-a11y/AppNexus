package com.appnexus.platform.persistence.configuration;

import com.appnexus.persistence.mybatis.PlatformScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 配置上下文对业务开放的字典引用映射；固定表及列，引用校验和记录共用业务事务。 */
@Mapper
@PlatformScoped(tables={"tenant_dictionary","tenant_dictionary_item","tenant_dictionary_item_reference"})
public interface BusinessValueMapper {
    /** @param tenant 可信租户 UUID @param batch 已声明字典编码 JSON 数组 @return 一次读取各字典的历史及启用选项 */
    @com.appnexus.persistence.mybatis.GuardedScope(tenant="tenant",serviceOperation="dictionary-reference:read-options",serviceKeys={"batch"})
    @Select("""
        SELECT d.code dictionary,i.item_value value,i.name,d.status dictionaryStatus,i.status itemStatus,i.code
        FROM JSON_TABLE(#{batch}, '$[*]' COLUMNS (dictionary VARCHAR(64) PATH '$')) requested
        JOIN tenant_dictionary d ON d.code=requested.dictionary AND d.tenant_id=UUID_TO_BIN(#{tenant})
        JOIN tenant_dictionary_item i ON i.dictionary_id=d.id AND i.tenant_id=d.tenant_id
        WHERE d.deleted_at IS NULL AND i.deleted_at IS NULL ORDER BY d.code,i.sort_order,i.id
        """)
    List<Map<String,Object>> options(@Param("tenant") String tenant,@Param("batch") String batch);
    /** @param tenant 可信租户 UUID @param batch 字典编码和业务值的固定 JSON 数组 @return 批量历史名称 */
    @com.appnexus.persistence.mybatis.GuardedScope(tenant="tenant",serviceOperation="dictionary-reference:resolve-names",serviceKeys={"batch"})
    @Select("""
        SELECT d.code dictionary,i.item_value value,i.name
        FROM JSON_TABLE(#{batch}, '$[*]' COLUMNS (dictionary VARCHAR(64) PATH '$.dictionary', value_code VARCHAR(200) PATH '$.value')) requested
        JOIN tenant_dictionary d ON d.code=requested.dictionary AND d.tenant_id=UUID_TO_BIN(#{tenant})
        JOIN tenant_dictionary_item i ON i.dictionary_id=d.id AND i.tenant_id=d.tenant_id AND i.item_value=requested.value_code
        WHERE d.deleted_at IS NULL AND i.deleted_at IS NULL
        """)
    List<Map<String,Object>> names(@Param("tenant") String tenant,@Param("batch") String batch);

    /** @param tenant 可信租户 UUID @param code 字典编码；调用方按编码排序加锁 @return 原字典状态，空集合表示不存在 */
    @Select("SELECT BIN_TO_UUID(id) id,status FROM tenant_dictionary WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND code=#{code} AND deleted_at IS NULL FOR UPDATE")
    List<Map<String,Object>> lockDictionary(@Param("tenant") String tenant,@Param("code") String code);
    /** @param tenant 可信租户 UUID @param dictionary 父字典 UUID @param value 稳定业务值 @return 当前租户未删除选项 */
    @Select("SELECT BIN_TO_UUID(id) id,status,code FROM tenant_dictionary_item WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND dictionary_id=UUID_TO_BIN(#{dictionary}) AND item_value=#{value} AND deleted_at IS NULL")
    List<Map<String,Object>> items(@Param("tenant") String tenant,@Param("dictionary") String dictionary,@Param("value") String value);
    /**
     * 记录历史引用；重复引用不修改原记录，不另开事务。
     * @param tenant 可信租户 UUID
     * @param item 已锁定父字典内的选项 UUID
     * @param consumer 声明的消费对象编码
     * @param reference 业务记录引用键
     * @param actor 可信账号 UUID；系统执行允许 null
     * @return 受影响行数
     */
    @Insert("INSERT INTO tenant_dictionary_item_reference(tenant_id,item_id,consumer,reference_key,created_by,created_at,updated_by,updated_at) VALUES(UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{item}),#{consumer},#{reference},UUID_TO_BIN(#{actor}),CURRENT_TIMESTAMP(6),UUID_TO_BIN(#{actor}),CURRENT_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE reference_key=reference_key")
    int reference(@Param("tenant") String tenant,@Param("item") String item,@Param("consumer") String consumer,@Param("reference") String reference,@Param("actor") String actor);
}
