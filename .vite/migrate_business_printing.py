from pathlib import Path
root=Path('backend/services/print-service/adapters/jdbc/src/main/java/com/appnexus/printing/adapter/jdbc')
p=root/'BusinessPrintingAdapter.java';s=p.read_text(encoding='utf-8')
import re
sqls=re.findall(r'jdbc\.(query|queryForObject|queryForList)\("([^"]+)"',s);assert len(sqls)==3
methods=[]
for i,(call,sql) in enumerate(sqls):
 args=[['tenant','contract'],['tenant'],['tenant','source','actor']][i]
 for arg in args:sql=sql.replace('?', '#{'+arg+'}',1)
 ret=['List<Map<String,Object>>','Boolean','List<Map<String,Object>>'][i]
 methods.append(' /** 固定打印查询；可信租户、机器或成员准入由BusinessPrinting适配器逐次校验。 */\n @Select("'+sql+'")\n '+ret+' query'+str(i)+'('+','.join('@Param("'+a+'") String '+a for a in args)+');')
(root/'BusinessPrintingMapper.java').write_text('''package com.appnexus.printing.adapter.jdbc;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 打印公开场景及历史固定SQL，后台/机器身份保持原平台契约，不额外授予业务数据权限。 */
@Mapper
@PlatformScoped(tables={"print_scene","print_template","print_template_version","print_agent_queue","print_output_request","print_job"})
public interface BusinessPrintingMapper {
'''+ '\n\n'.join(methods)+'\n}\n',encoding='utf-8')
start=s.index('return jdbc.query(');end=s.index(';',start);s=s[:start]+'return mapper.query0(c.tenantId(),contract).stream().map(row->new Scene((String)row.get("id"),(String)row.get("code"),(String)row.get("name"),(String)row.get("data_contract"))).toList()'+s[end:]
start=s.index('jdbc.queryForObject(');end=s.index('));',start);s=s[:start]+'mapper.query1(c.tenantId())'+s[end+1:]
start=s.index('jdbc.queryForList(');end=s.index(';',start);s=s[:start]+'mapper.query2(c.tenantId(),source,c.actorId())'+s[end:]
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('private final JdbcTemplate jdbc;','private final BusinessPrintingMapper mapper;').replace('BusinessPrintingAdapter(JdbcTemplate jdbc,','BusinessPrintingAdapter(BusinessPrintingMapper mapper,').replace('this.jdbc=jdbc','this.mapper=mapper');p.write_text(s,encoding='utf-8')
p=root/'PrintConfiguration.java';s=p.read_text(encoding='utf-8').replace('businessPrinting(DataSource source,','businessPrinting(BusinessPrintingMapper mapper,').replace('new BusinessPrintingAdapter(new JdbcTemplate(source),','new BusinessPrintingAdapter(mapper,');p.write_text(s,encoding='utf-8')
