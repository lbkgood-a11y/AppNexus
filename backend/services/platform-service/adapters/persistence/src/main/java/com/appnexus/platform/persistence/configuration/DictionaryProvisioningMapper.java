package com.appnexus.platform.persistence.configuration;

import com.appnexus.persistence.mybatis.PlatformScoped;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 已准入租户开通事务的固定字典预置；允许平台上下文，不能要求尚未创建的租户成员请求。 */
@Mapper
@PlatformScoped(tables={"tenant_dictionary","tenant_dictionary_item"})
public interface DictionaryProvisioningMapper {
    /** @param id 稳定字典UUID @param tenant 新开通可信租户UUID @param code 标准字典编码 @param name 默认中文名称 @param order 默认顺序，从1开始 @param actor 真实开通账号UUID，系统执行可空 @return 新增行数，不覆盖既有配置 */
    @Insert("INSERT INTO tenant_dictionary(id,tenant_id,code,name,description,status,sort_order,created_by,updated_by) SELECT UUID_TO_BIN(#{id}),UUID_TO_BIN(#{tenant}),#{code},#{name},'统一标准预置；编码和业务值创建后不可修改。','ENABLED',#{order},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}) WHERE NOT EXISTS(SELECT 1 FROM tenant_dictionary WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND code=#{code})")
    int createDictionary(@Param("id") String id,@Param("tenant") String tenant,@Param("code") String code,@Param("name") String name,@Param("order") int order,@Param("actor") String actor);
    /** @param tenant 新开通可信租户UUID @param code 标准字典编码 @return 未删除字典UUID；不存在时不创建明细 */
    @Select("SELECT BIN_TO_UUID(id) FROM tenant_dictionary WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND code=#{code} AND deleted_at IS NULL")
    List<String> dictionary(@Param("tenant") String tenant,@Param("code") String code);
    /** @param id 稳定字典项UUID @param tenant 可信租户UUID @param dictionary 本租户父字典UUID @param code 标准项编码 @param value 标准业务值 @param name 默认名称 @param order 从1开始的默认顺序 @param actor 真实开通账号UUID，系统执行可空 @return 新增行数；永久占用编码和值不复用，不恢复停用项 */
    @Insert("INSERT INTO tenant_dictionary_item(id,tenant_id,dictionary_id,code,item_value,name,status,sort_order,created_by,updated_by) SELECT UUID_TO_BIN(#{id}),UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{dictionary}),#{code},#{value},#{name},'ENABLED',#{order},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}) WHERE NOT EXISTS(SELECT 1 FROM tenant_dictionary_item WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND dictionary_id=UUID_TO_BIN(#{dictionary}) AND (code=#{code} OR item_value=#{value}))")
    int createItem(@Param("id") String id,@Param("tenant") String tenant,@Param("dictionary") String dictionary,@Param("code") String code,@Param("value") String value,@Param("name") String name,@Param("order") int order,@Param("actor") String actor);
}
