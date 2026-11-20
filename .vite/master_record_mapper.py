from pathlib import Path
import re
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata');s=(root/'MasterData.java').read_text(encoding='utf-8');kinds=re.findall(r'\b([A-Z_]+)\("[^"]+", "[^"]+", List.of\(([^\n]+)\)\)',s)
snake=lambda x:re.sub(r'([a-z0-9])([A-Z])',r'\1_\2',x).lower()
xml=['''<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.appnexus.masterdata.adapter.jdbc.MasterRecordMapper">
<!-- 固定枚举逐类展开SQL；不接受表名、列名或SQL片段。 -->''']
operations={'create':'insert','update':'update','status':'update','previous':'select','audit':'update'}
for op,tag in operations.items():
 xml.append('<'+tag+' id="'+op+'"'+(' resultType="map"' if op=='previous' else '')+'> <choose>')
 for kind,fields in kinds:
  fields=re.findall(r'"([^"]+)"',fields);table='md_'+kind.lower();columns=[snake(f) for f in fields]
  if op=='create':sql='INSERT INTO '+table+'(id,tenant_id,code,name,'+','.join(columns)+',created_by,created_at,updated_by,updated_at) VALUES(UUID_TO_BIN(#{id}),UUID_TO_BIN(#{tenant}),#{input.code},#{input.name},'+','.join('#{values.'+f+'}' for f in fields)+',UUID_TO_BIN(#{actor}),CURRENT_TIMESTAMP(6),UUID_TO_BIN(#{actor}),CURRENT_TIMESTAMP(6))'
  elif op=='update':sql='UPDATE '+table+' SET name=#{input.name},'+','.join(col+'=#{values.'+f+'}' for col,f in zip(columns,fields))+',version=version+1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id}) AND version=#{input.version} AND code=#{input.code}'
  elif op=='status':sql='UPDATE '+table+' SET enabled=#{enabled},version=version+1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id}) AND version=#{version}'
  elif op=='audit':sql='UPDATE '+table+' SET updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})'
  else:sql='SELECT '+','.join(c+' AS '+f for c,f in zip(columns,fields))+' FROM '+table+' WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})'
  xml.append('<when test="kind.name() == \''+kind+'\'">'+sql+'</when>')
 xml.append('</choose></'+tag+'>')
xml.append('''<update id="organization">UPDATE md_partner SET organization_id=UUID_TO_BIN(#{organization}) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})</update>
</mapper>''')
out=Path('backend/services/master-data-service/src/main/resources/com/appnexus/masterdata/adapter/jdbc');out.mkdir(parents=True,exist_ok=True);(out/'MasterRecordMapper.xml').write_text('\n'.join(xml)+'\n',encoding='utf-8')
params='@Param("tenant") String tenant,@Param("kind") com.appnexus.masterdata.MasterData.Kind kind,@Param("id") String id'
(root/'adapter/jdbc/MasterRecordMapper.java').write_text('''package com.appnexus.masterdata.adapter.jdbc;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
import com.appnexus.masterdata.MasterData.*;
/** 主数据固定枚举展开的Mapper；XML无自由表列替换，原用例负责行/字段准入和完整事务。 */
@Mapper
@PlatformScoped(tables={'''+','.join('"md_'+k.lower()+'"' for k,_ in kinds)+'''})
public interface MasterRecordMapper {
 /** 固定对象新增，创建与修改审计来自实际主体，SQL时间为UTC。 */
 int create('''+params+''',@Param("input") Input input,@Param("values") Map<String,String> values,@Param("actor") String actor);
 /** 固定对象CAS修改；编码不可改变，失败返回冲突。 */
 int update('''+params+''',@Param("input") Input input,@Param("values") Map<String,String> values);
 /** 固定对象独立启停，不能通过普通编辑隐式获得。 */
 int status('''+params+''',@Param("enabled") boolean enabled,@Param("version") long version);
 /** 授权父记录中的原始字段用于写保护合并，不返回未授权原文。 */
 List<Map<String,String>> previous('''+params+''');
 /** 当前业务修改同事务推进修改审计，不覆盖创建信息。 */
 int audit('''+params+''',@Param("actor") String actor);
 /** 客商归属已分别验证原记录及目标范围。 */
 int organization(@Param("tenant") String tenant,@Param("id") String id,@Param("organization") String organization);
}
''',encoding='utf-8')
p=root/'adapter/jdbc/JdbcMasterDataRecords.java';old=p.read_text(encoding='utf-8')
new='''package com.appnexus.masterdata.adapter.jdbc;
import com.appnexus.masterdata.application.MasterDataRecords;
import com.appnexus.masterdata.MasterData.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
/** 主数据存储端口MyBatis适配；固定枚举映射，版本、审计与原聚合事务保持。 */
public final class MyBatisMasterDataRecords implements MasterDataRecords {
 private final MasterRecordMapper mapper;
 /** @param mapper 同数据源及事务的固定主数据Mapper */
 public MyBatisMasterDataRecords(MasterRecordMapper mapper){this.mapper=Objects.requireNonNull(mapper);}
 @Override public void create(VerifiedTenantContext c,Kind kind,String id,Input input,Map<String,String> values){mapper.create(c.tenantId().toString(),kind,id,input,values,c.accountId().toString());}
 @Override public void update(VerifiedTenantContext c,Kind kind,String id,Input input,Map<String,String> values){if(mapper.update(c.tenantId().toString(),kind,id,input,values)!=1)throw new Rejection(409,"MASTER_DATA_CHANGED");}
 @Override public void status(VerifiedTenantContext c,Kind kind,String id,Status input){if(mapper.status(c.tenantId().toString(),kind,id,input.enabled(),input.version())!=1)throw new Rejection(409,"MASTER_DATA_CHANGED");}
 @Override public void organization(VerifiedTenantContext c,String id,String organization){mapper.organization(c.tenantId().toString(),id,organization);}
 @Override public Map<String,String> previous(VerifiedTenantContext c,Kind kind,String id){var rows=mapper.previous(c.tenantId().toString(),kind,id);return rows.isEmpty()?Map.of():rows.getFirst();}
 @Override public void audit(VerifiedTenantContext c,Kind kind,String id){mapper.audit(c.tenantId().toString(),kind,id,c.accountId().toString());}
}
''';p.with_name('MyBatisMasterDataRecords.java').write_text(new,encoding='utf-8');p.unlink()
