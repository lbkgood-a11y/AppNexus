from pathlib import Path
import re
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata');rules=(root/'domain/partner/PartnerRules.java').read_text(encoding='utf-8');fields={name:re.findall(r'"([^"]+)"',re.search(name+r'=List.of\(([^\n]+)\)',rules).group(1)) for name in ['CONTACT','BANK']};snake=lambda s:re.sub(r'([a-z0-9])([A-Z])',r'\1_\2',s).lower()
xml=['''<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.appnexus.masterdata.PartnerDetailMapper">''']
for op,tag in [('read','select'),('create','insert'),('update','update'),('delete','update')]:
 xml.append('<'+tag+' id="'+op+'"'+(' resultType="map"' if op=='read' else '')+'><choose>')
 for name,keys in fields.items():
  table='md_partner_bank_account' if name=='BANK' else 'md_partner_contact';cols=[snake(k) for k in keys];condition='tenant_id=UUID_TO_BIN(#{tenant}) AND partner_id=UUID_TO_BIN(#{parent})'
  if op=='read':sql='SELECT BIN_TO_UUID(id) id,'+','.join(c+' '+k for c,k in zip(cols,keys))+',BIN_TO_UUID(created_by) creator,created_at,BIN_TO_UUID(updated_by) updater,updated_at FROM '+table+' WHERE '+condition+' AND deleted=FALSE ORDER BY created_at,id'
  elif op=='create':sql='INSERT INTO '+table+'('+','.join(cols)+',created_by,updated_by,id,tenant_id,partner_id) VALUES('+','.join('#{values.'+k+'}' for k in keys)+',UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{id}),UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{parent}))'
  elif op=='update':sql='UPDATE '+table+' SET '+','.join(c+'=#{values.'+k+'}' for c,k in zip(cols,keys))+',updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE '+condition+' AND id=UUID_TO_BIN(#{id})'
  else:sql='UPDATE '+table+' SET deleted=TRUE,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE '+condition+' AND id=UUID_TO_BIN(#{id})'
  xml.append(('<when test="bank">' if name=='BANK' else '<otherwise>')+sql+('</when>' if name=='BANK' else '</otherwise>')) if name=='BANK' else None
 # 保证when在otherwise前。
 if name=='BANK':
  contact=keys
 # 重新生成每个操作采用独立函数输出，避免遍历顺序影响choose。
 xml.pop() if False else None
 xml.append('</choose></'+tag+'>')
# 上面CONTACT先遍历；补全固定CONTACT分支置于when之后。
for op,tag in [('read','select'),('create','insert'),('update','update'),('delete','update')]:
 keys=fields['CONTACT'];cols=[snake(k) for k in keys];table='md_partner_contact';condition='tenant_id=UUID_TO_BIN(#{tenant}) AND partner_id=UUID_TO_BIN(#{parent})'
 if op=='read':sql='SELECT BIN_TO_UUID(id) id,'+','.join(c+' '+k for c,k in zip(cols,keys))+',BIN_TO_UUID(created_by) creator,created_at,BIN_TO_UUID(updated_by) updater,updated_at FROM '+table+' WHERE '+condition+' AND deleted=FALSE ORDER BY created_at,id'
 elif op=='create':sql='INSERT INTO '+table+'('+','.join(cols)+',created_by,updated_by,id,tenant_id,partner_id) VALUES('+','.join('#{values.'+k+'}' for k in keys)+',UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{id}),UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{parent}))'
 elif op=='update':sql='UPDATE '+table+' SET '+','.join(c+'=#{values.'+k+'}' for c,k in zip(cols,keys))+',updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE '+condition+' AND id=UUID_TO_BIN(#{id})'
 else:sql='UPDATE '+table+' SET deleted=TRUE,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE '+condition+' AND id=UUID_TO_BIN(#{id})'
 i=xml.index('</choose></'+tag+'>');xml[i]='<otherwise>'+sql+'</otherwise>\n'+xml[i]
xml.append('</mapper>');out=Path('backend/services/master-data-service/src/main/resources/com/appnexus/masterdata');out.mkdir(parents=True,exist_ok=True);(out/'PartnerDetailMapper.xml').write_text('\n'.join(xml)+'\n',encoding='utf-8')
params='@Param("tenant") String tenant,@Param("parent") String parent,@Param("bank") boolean bank'
(root/'PartnerDetailMapper.java').write_text('''package com.appnexus.masterdata;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 客商联系人/银行账户固定SQL，父行授权与字段保护先在同聚合事务完成。 */
@Mapper
@PlatformScoped(tables={"md_partner_contact","md_partner_bank_account"})
public interface PartnerDetailMapper {
 /** 已授权父档案明细；不提供按任意明细ID直接查询入口。 */
 List<Map<String,Object>> read('''+params+''');
 /** 原创建与修改账号来自可信上下文，时间由数据库UTC生成。 */
 int create('''+params+''',@Param("id") String id,@Param("values") Map<String,String> values,@Param("actor") String actor);
 /** 修改限定租户、父档案及明细，保留创建审计。 */
 int update('''+params+''',@Param("id") String id,@Param("values") Map<String,String> values,@Param("actor") String actor);
 /** 逻辑删除保留历史，受限字段删除由调用者先验证。 */
 int delete('''+params+''',@Param("id") String id,@Param("actor") String actor);
}
''',encoding='utf-8')
p=root/'PartnerDetails.java';s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('JdbcTemplate jdbc','PartnerDetailMapper mapper').replace('saveRows(jdbc,','saveRows(mapper,').replace('read(jdbc,','read(mapper,')
start=s.index('    return jdbc.query(');end=s.index('\n  }',start);s=s[:start]+'''    return mapper.read(c.tenantId().toString(),parent,bank).stream().map(row->{var values=new LinkedHashMap<String,String>();for(var field:bank?BANK:CONTACT)values.put(field,(String)row.get(field));return new Detail((String)row.get("id"),values,new Audit((String)row.get("creator"),utc(row.get("created_at")),(String)row.get("updater"),utc(row.get("updated_at"))));}).toList();'''+s[end:]
start=s.index('      var args=new ArrayList<Object>');end=s.index('\n  }\n  private static Rejection',start);s=s[:start]+'''      if(fresh)mapper.create(c.tenantId().toString(),parent,bank,id,values,c.accountId().toString());else mapper.update(c.tenantId().toString(),parent,bank,id,values,c.accountId().toString());
    }
    for(var id:remaining)mapper.delete(c.tenantId().toString(),parent,bank,id,c.accountId().toString());'''+s[end:]
s=s.rstrip()[:-1]+''' private static String utc(Object value){return value==null?null:((java.sql.Timestamp)value).toInstant().toString();}
}\n''';p.write_text(s,encoding='utf-8')
