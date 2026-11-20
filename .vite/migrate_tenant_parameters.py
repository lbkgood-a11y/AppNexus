from pathlib import Path
root=Path('backend/services/platform-service/adapters/security/src/main/java/com/appnexus/platform/security');p=root/'TenantParameters.java';s=p.read_text(encoding='utf-8')
import re
pat=re.compile(r'jdbc\.(queryForList|update)\("([^"]+)",((?:[^();]|\([^()]*\))*)\)');entries=list(pat.finditer(s));assert len(entries)==5,len(entries)
names=['list','lockTenant','create','update','audit'];methods=[]
for name,m in zip(names,entries):
 call,sql,args=m.groups();args=[x.strip() for x in args.split(',')];keys={'context.tenantId().toString()':'tenant','context.accountId().toString()':'actor'};unique=list(dict.fromkeys(args));params=[]
 for arg in args:sql=sql.replace('?', '#{'+keys.get(arg,arg)+'}',1)
 for arg in unique:
  k=keys.get(arg,arg);params.append('@Param("'+k+'") '+('int' if k in ['next','version'] else 'String')+' '+k)
 ann='Select' if call=='queryForList' else 'Insert' if sql.startswith('INSERT') else 'Update';ret='List<Map<String,Object>>' if call=='queryForList' else 'int'
 methods.append(' /** '+name+' 固定SQL；可信租户及实际操作者由用例验证，写入及审计保持同事务。 */\n @'+ann+'("'+sql+'")\n '+ret+' '+name+'('+','.join(params)+');')
 s=s.replace(m.group(0),'mapper.'+name+'('+','.join(unique)+')',1)
(root/'TenantParameterMapper.java').write_text('''package com.appnexus.platform.security;
import com.appnexus.persistence.mybatis.PlatformScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
/** 租户展示参数固定持久化；不提供系统安全配置或任意SQL入口。 */
@Mapper
@PlatformScoped(tables={"tenant_parameter","tenant_parameter_audit","platform_tenant"})
public interface TenantParameterMapper {
'''+ '\n\n'.join(methods)+'\n}\n',encoding='utf-8')
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;\n','').replace('private final JdbcTemplate jdbc;','private final TenantParameterMapper mapper;').replace('TenantParameters(JdbcTemplate jdbc,','TenantParameters(TenantParameterMapper mapper,').replace('{this.jdbc=jdbc;','{this.mapper=mapper;');p.write_text(s,encoding='utf-8')
p=root/'WebSecurityConfiguration.java';s=p.read_text(encoding='utf-8').replace('PasswordEncoder encoder,JdbcTemplate jdbc,','PasswordEncoder encoder,ClientSecretMapper clientSecrets,').replace('new RotatingClientPasswordEncoder(encoder,jdbc)','new RotatingClientPasswordEncoder(encoder,clientSecrets)');p.write_text(s,encoding='utf-8')
