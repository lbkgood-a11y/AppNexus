from pathlib import Path
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata')
(root/'GeographyMapper.java').write_text('''package com.appnexus.masterdata;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 固定地理目录持久化；历史资源不变，初始化只补缺失编码，父链计算仍由TenantGeography负责。 */
@Mapper
@PlatformScoped(tables={"md_country_region","md_subdivision"})
public interface GeographyMapper {
 /** @param tenant 可信租户 @param country true国别，false区划 @param lock 写事务固定顺序锁节点 @return 固定节点，不允许自由表名 */
 @Select({"<script>","SELECT code,name,parent_code,<choose><when test='country'>code country_code FROM md_country_region</when><otherwise>country_code FROM md_subdivision</otherwise></choose> WHERE tenant_id=UUID_TO_BIN(#{tenant}) ORDER BY code<if test='lock'> FOR UPDATE</if>","</script>"}) List<Map<String,Object>> nodes(@Param("tenant") String tenant,@Param("country") boolean country,@Param("lock") boolean lock);
 /** @param tenant 可信租户 @return 当前租户目录含状态，UNION每支独立限定租户 */
 @Select("SELECT code,name,code country_code,'' parent_code,enabled FROM md_country_region WHERE tenant_id=UUID_TO_BIN(#{tenant}) UNION ALL SELECT code,name,country_code,CASE WHEN parent_code='' THEN country_code ELSE parent_code END parent_code,enabled FROM md_subdivision WHERE tenant_id=UUID_TO_BIN(#{tenant}) ORDER BY code") List<Map<String,Object>> catalog(@Param("tenant") String tenant);
 /** @param tenant 可信租户 @return 已建区划国别事实 */
 @Select("SELECT code,country_code FROM md_subdivision WHERE tenant_id=UUID_TO_BIN(#{tenant})") List<Map<String,Object>> subdivisions(@Param("tenant") String tenant);
 /** @param tenant 可信租户 @param code 原区划编码 @param country 领域父链计算国别 @param actor 实际账号 @return 同事务推进国别/版本/审计 */
 @Update("UPDATE md_subdivision SET country_code=#{country},version=version+1,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND code=#{code}") int country(@Param("tenant") String tenant,@Param("code") String code,@Param("country") String country,@Param("actor") String actor);
 /** @param tenant 可信租户 @param code 不可变标准码 @param name 标准名称 @param alpha3 三字码 @param numeric 数字码文本 @param actor 开通实际账号，历史系统执行可空 @return 仅新增缺失编码，不覆盖租户定制 */
 @Insert("INSERT INTO md_country_region(id,tenant_id,code,name,english_name,alpha3_code,numeric_code,created_by,created_at,updated_by,updated_at) SELECT UUID_TO_BIN(UUID()),UUID_TO_BIN(#{tenant}),#{code},#{name},#{name},#{alpha3},#{numeric},UUID_TO_BIN(#{actor}),CURRENT_TIMESTAMP(6),UUID_TO_BIN(#{actor}),CURRENT_TIMESTAMP(6) WHERE NOT EXISTS(SELECT 1 FROM md_country_region WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND code=#{code})") int seedCountry(@Param("tenant") String tenant,@Param("code") String code,@Param("name") String name,@Param("alpha3") String alpha3,@Param("numeric") String numeric,@Param("actor") String actor);
 /** @param tenant 可信租户 @param code 标准码 @param name 标准名称 @param country 所属国家 @param type 固定区划类型 @param parent 标准父码 @param actor 可信账号，历史可空 @return 只补缺失，UTC审计由数据库生成 */
 @Insert("INSERT INTO md_subdivision(id,tenant_id,code,name,country_code,subdivision_type,parent_code,created_by,created_at,updated_by,updated_at) SELECT UUID_TO_BIN(UUID()),UUID_TO_BIN(#{tenant}),#{code},#{name},#{country},#{type},#{parent},UUID_TO_BIN(#{actor}),CURRENT_TIMESTAMP(6),UUID_TO_BIN(#{actor}),CURRENT_TIMESTAMP(6) WHERE NOT EXISTS(SELECT 1 FROM md_subdivision WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND code=#{code})") int seedSubdivision(@Param("tenant") String tenant,@Param("code") String code,@Param("name") String name,@Param("country") String country,@Param("type") String type,@Param("parent") String parent,@Param("actor") String actor);
}
''',encoding='utf-8')
p=root/'TenantGeography.java';s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('TenantGeography(JdbcTemplate jdbc,','TenantGeography(GeographyMapper mapper,').replace('@param jdbc','@param mapper')
start=s.index('      String table=country?');end=s.index('\n    }\n  }',start)
s=s[:start]+'''      for(var row:mapper.nodes(tenantId.toString(),country,lock)){
        String code=(String)row.get("code"),parent=(String)row.get("parent_code");
        if(parent.isEmpty())parent=items.containsKey(code)?items.get(code).parent():country?"":(String)row.get("country_code");
        String name=(String)row.get("name");if(name.equals(originals.get(code)))name=GeographyHierarchy.displayName(code,name);
        items.put(code,new Item(code,name,parent,country));
      }'''+s[end:];p.write_text(s,encoding='utf-8')
p=root/'JdbcGeographyCatalog.java';s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('JdbcGeographyCatalog','MyBatisGeographyCatalog').replace('private final JdbcTemplate jdbc;','private final GeographyMapper mapper;').replace('MyBatisGeographyCatalog(JdbcTemplate jdbc){this.jdbc=jdbc;}','MyBatisGeographyCatalog(GeographyMapper mapper){this.mapper=mapper;}').replace('new TenantGeography(jdbc,','new TenantGeography(mapper,').replace('@param jdbc','@param mapper');start=s.index('    return jdbc.query(');end=s.index('\n  }',start);s=s[:start]+'''    return mapper.catalog(tenantId.toString()).stream().map(row->new Node((String)row.get("code"),graph.name((String)row.get("code")),graph.country((String)row.get("code")),(String)row.get("parent_code"),Boolean.TRUE.equals(row.get("enabled")))).toList();'''+s[end:];p.with_name('MyBatisGeographyCatalog.java').write_text(s,encoding='utf-8');p.unlink()
p=root/'GeographyDefaults.java';p.write_text('''package com.appnexus.masterdata;
import java.util.UUID;
/** 标准地理初始化；固定Mapper只补缺失，不修改历史资源或覆盖租户定制。 */
public final class GeographyDefaults {
 private GeographyDefaults(){}
 /** @param mapper 同迁移/开通事务固定Mapper @param tenant 可信租户UUID @param actor 实际账号，历史迁移可空 */
 public static void seed(GeographyMapper mapper,UUID tenant,UUID actor){String account=actor==null?null:actor.toString();for(var entry:GeographyStandards.entries(false))mapper.seedCountry(tenant.toString(),entry.code(),entry.name(),entry.attributes().get("alpha3Code"),entry.attributes().get("numericCode"),account);for(var entry:GeographyStandards.entries(true))mapper.seedSubdivision(tenant.toString(),entry.code(),entry.name(),entry.attributes().get("countryCode"),entry.attributes().get("subdivisionType"),entry.attributes().get("parentCode"),account);}
}
''',encoding='utf-8')
p=root/'MasterDataConfiguration.java';s=p.read_text(encoding='utf-8').replace('geographyProvisioning(JdbcTemplate jdbc){return new GeographyProvisioning(jdbc);}','geographyProvisioning(GeographyMapper mapper){return new GeographyProvisioning(mapper);}').replace('record GeographyProvisioning(JdbcTemplate jdbc)','record GeographyProvisioning(GeographyMapper mapper)').replace('GeographyDefaults.seed(jdbc,','GeographyDefaults.seed(mapper,');p.write_text(s,encoding='utf-8')
p=Path('backend/applications/monolith-app/src/main/java/com/appnexus/platform/PlatformApplication.java');s=p.read_text(encoding='utf-8').replace('"com.appnexus.masterdata.adapter.jdbc"','"com.appnexus.masterdata"');p.write_text(s,encoding='utf-8')
