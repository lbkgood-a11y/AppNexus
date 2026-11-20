from pathlib import Path
import re
tables=set()
for p in Path('backend').rglob('*.java'):
 if any(x.startswith('target') for x in p.parts):continue
 tables.update(re.findall(r'RecordAuditSource\(table="([a-z_]+)"',p.read_text(encoding='utf-8-sig')))
root=Path('backend/services/platform-service/adapters/persistence/src/main/java/com/appnexus/platform/persistence')
mapper='''package com.appnexus.platform.persistence;
import com.appnexus.persistence.mybatis.PlatformScoped;
import com.appnexus.contracts.operation.RecordAuditSource;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 已授权响应的固定最小审计投影；代码注解与表白名单共同限定，不提供自由查询接口。 */
@Mapper
@PlatformScoped(tables={'''+','.join('"'+t+'"' for t in sorted(tables))+'''})
public interface AuditProjectionMapper {
 /** @param declaration 真实处理方法的构建注解，不来自请求反序列化 @param tenant 安全链或已授权平台代管租户 @param ids 原接口已返回记录键，每批最多200个 @return 仅四列审计及记录键，不读取正文 */
 @SelectProvider(type=Sql.class,method="read") List<Map<String,Object>> read(@Param("declaration") RecordAuditSource declaration,@Param("tenant") String tenant,@Param("ids") List<String> ids);
 /** 只支持已审查来源和简单标识符，参数全部绑定；调用前原业务必须已完成授权。 */
 final class Sql {
  private static final java.util.Set<String> TABLES=java.util.Set.of('''+','.join('"'+t+'"' for t in sorted(tables))+''');
  private Sql(){}
  /** @param parameters 构建注解及安全投影键 @return 最小参数化审计SQL，未知来源拒绝 */
  public static String read(Map<String,Object> parameters){var declaration=(RecordAuditSource)parameters.get("declaration");var ids=(List<?>)parameters.get("ids");if(!TABLES.contains(declaration.table())||!declaration.key().matches("[a-z][a-z0-9_]*")||ids.isEmpty()||ids.size()>200)throw new SecurityException("AUDIT_SOURCE_INVALID");String key=declaration.binaryKey()?"BIN_TO_UUID("+declaration.key()+")":declaration.key();var values=new java.util.ArrayList<String>();for(int i=0;i<ids.size();i++)values.add(declaration.binaryKey()?"UUID_TO_BIN(#{ids["+i+"]})":"#{ids["+i+"]}");return "SELECT "+key+" audit_id,BIN_TO_UUID(created_by) creator,created_at,BIN_TO_UUID(updated_by) updater,updated_at FROM "+declaration.table()+" WHERE "+(parameters.get("tenant")==null?"":"tenant_id="+(declaration.binaryTenant()?"UUID_TO_BIN(#{tenant})":"#{tenant}")+" AND ")+declaration.key()+" IN ("+String.join(",",values)+")";}
 }
}
'''
(root/'AuditProjectionMapper.java').write_text(mapper,encoding='utf-8')
p=Path('backend/applications/monolith-app/src/main/java/com/appnexus/platform/RecordAuditResponseAdvice.java');s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('private final JdbcTemplate jdbc;','private final com.appnexus.platform.persistence.AuditProjectionMapper mapper;').replace('RecordAuditResponseAdvice(JdbcTemplate jdbc,','RecordAuditResponseAdvice(com.appnexus.platform.persistence.AuditProjectionMapper mapper,').replace('this.jdbc=jdbc','this.mapper=mapper').replace('@param jdbc','@param mapper')
start=s.index('            var args=new ArrayList<Object>');end=s.index('\n        }\n        rows.forEach',start)
s=s[:start]+'''            for(var raw:mapper.read(declaration,tenant,batch)){
                var audit=new LinkedHashMap<String,Object>();audit.put("createdBy",raw.get("creator"));audit.put("updatedBy",raw.get("updater"));
                for(String field:List.of("created_at","updated_at")){Object timestamp=raw.get(field);audit.put(field.equals("created_at")?"createdAt":"updatedAt",timestamp==null?null:((java.sql.Timestamp)timestamp).toInstant().toString());}
                audits.put(((String)raw.get("audit_id")).toLowerCase(Locale.ROOT),audit);
            }'''+s[end:];p.write_text(s,encoding='utf-8')
