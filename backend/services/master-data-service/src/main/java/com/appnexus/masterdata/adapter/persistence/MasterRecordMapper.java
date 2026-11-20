package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.masterdata.api.masterdata.MasterData.*;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 主数据固定枚举展开的Mapper；XML无自由表列替换，原用例负责行/字段准入和完整事务。 */
@Mapper
@PlatformScoped(tables={"md_partner","md_warehouse","md_storage_area","md_storage_location","md_country_region","md_subdivision"})
public interface MasterRecordMapper {
 /** 固定对象新增，创建与修改审计来自实际主体，SQL时间为UTC。 */
 int create(@Param("tenant") String tenant,@Param("kind") com.appnexus.masterdata.api.masterdata.MasterData.Kind kind,@Param("id") String id,@Param("input") Input input,@Param("values") Map<String,String> values,@Param("actor") String actor);
 /** 固定对象CAS修改；编码不可改变，失败返回冲突。 */
 int update(@Param("tenant") String tenant,@Param("kind") com.appnexus.masterdata.api.masterdata.MasterData.Kind kind,@Param("id") String id,@Param("input") Input input,@Param("values") Map<String,String> values);
 /** 固定对象独立启停，不能通过普通编辑隐式获得。 */
 int status(@Param("tenant") String tenant,@Param("kind") com.appnexus.masterdata.api.masterdata.MasterData.Kind kind,@Param("id") String id,@Param("enabled") boolean enabled,@Param("version") long version);
 /** 授权父记录中的原始字段用于写保护合并，不返回未授权原文。 */
 List<Map<String,String>> previous(@Param("tenant") String tenant,@Param("kind") com.appnexus.masterdata.api.masterdata.MasterData.Kind kind,@Param("id") String id);
 /** 当前业务修改同事务推进修改审计，不覆盖创建信息。 */
 int audit(@Param("tenant") String tenant,@Param("kind") com.appnexus.masterdata.api.masterdata.MasterData.Kind kind,@Param("id") String id,@Param("actor") String actor);
 /** 客商归属已分别验证原记录及目标范围。 */
 int organization(@Param("tenant") String tenant,@Param("id") String id,@Param("organization") String organization);
}
