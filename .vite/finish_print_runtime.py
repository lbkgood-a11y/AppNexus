from pathlib import Path
import re
ns={};source=Path('.vite/extract_literal_mappers.py').read_text(encoding='utf-8');exec(source[:source.index('targets=')],ns);split_args=ns['split_args']
root=Path('backend/services/print-service/adapters/jdbc/src/main/java/com/appnexus/printing/adapter/jdbc');p=root/'PrintRuntime.java';s=p.read_text(encoding='utf-8');entries=[]
for m in re.finditer(r'jdbc\.query\(',s):
 depth=1;i=m.end();q=None
 while depth:
  c=s[i]
  if q:
   if c=='\\':i+=2;continue
   if c==q:q=None
  elif c=='"':q=c
  elif c=='(':depth+=1
  elif c==')':depth-=1
  i+=1
 args=split_args(s[m.end():i-1]);raw=args.pop(0);callback=args.pop(0);assert raw.startswith('"') and raw.endswith('"');sql=raw[1:-1];assert sql.count('?')==len(args);entries.append((m.start(),i,sql,callback,args))
mapper=root/'PrintRuntimeSqlMapper.java';ms=mapper.read_text(encoding='utf-8');methods=[]
for n,(start,end,sql,callback,args) in enumerate(entries):
 for j in range(len(args)):sql=sql.replace('?', '#{p'+str(j)+'}',1)
 methods.append(' /** 固定打印读取；已验证租户及操作准入，结果保持原JSON投影和错误契约。 */\n @Select("'+sql+'")\n List<Map<String,Object>> read'+str(n)+'('+','.join('@Param("p'+str(j)+'") Object p'+str(j) for j in range(len(args)))+');')
for n,(start,end,sql,cb,args) in reversed(list(enumerate(entries))):
 call='statements.read'+str(n)+'('+','.join(args)+')'
 error=re.search(r'throw new PrintFailure\("([^"]+)",(\d+)\)',cb);err='"'+error.group(1)+'",'+error.group(2) if error else '"PRINT_TEMPLATE_NOT_FOUND",404'
 if '-> row(' in cb or re.search(r'\(rs,n\)\s*->\s*row\(',cb):replacement=call+'.stream().map(this::projectRow).toList()'
 elif 'return row(' in cb:replacement='projectRow(required('+call+','+err+'))'
 elif 'return rs.getBoolean(1)' in cb:replacement='Boolean.TRUE.equals(required('+call+','+err+').get("enabled"))'
 elif 'return rs.getBytes(1)' in cb:replacement='(byte[])required('+call+','+err+').get("pdf_bytes")'
 elif 'return rs.getString(1)' in cb:replacement='(String)required('+call+','+err+').get("id")'
 elif 'return Map.of("version"' in cb:replacement='required('+call+','+err+')'
 else:raise ValueError(cb)
 # published_version原回调重命名为version。
 if 'return Map.of("version"' in cb:methods[n]=methods[n].replace('SELECT published_version,enabled','SELECT published_version version,enabled')
 s=s[:start]+replacement+s[end:]
ms=ms.rstrip()[:-1]+'\n'+'\n\n'.join(methods)+'\n}\n';mapper.write_text(ms,encoding='utf-8')
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('private final JdbcTemplate jdbc;','').replace('JdbcTemplate jdbc,','').replace('this.jdbc = jdbc;','')
start=s.index('  private Map<String,Object> row(ResultSet');end=s.index('\n',start)
s=s[:start]+'''  /** 固定Mapper结果沿用原UTC和JSON响应契约，失败JSON仍不当作有效结果。 */
  private Map<String,Object> projectRow(Map<String,Object> row){var result=new LinkedHashMap<String,Object>();row.forEach((name,original)->{Object value=original;if(value instanceof Timestamp time)value=time.toInstant();if(value instanceof String text&&(name.endsWith("_json")||name.equals("data_snapshot")))try{value=mapper.readTree(text);}catch(Exception ignored){value=null;}result.put(name,value);});return result;}
  private Map<String,Object> required(List<Map<String,Object>> rows,String code,int status){if(rows.isEmpty())throw new PrintFailure(code,status);return rows.getFirst();}'''+s[end:]
p.write_text(s,encoding='utf-8')
for name in ['PrintFlow','PrintOutputs']:
 p=root/(name+'.java');s=p.read_text(encoding='utf-8');assert not re.search(r'\bjdbc\.(?:query|update)',s)
 s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('private final JdbcTemplate jdbc;','').replace('JdbcTemplate jdbc,','').replace('this.jdbc=jdbc;','').replace('this.jdbc = jdbc;','');p.write_text(s,encoding='utf-8')
p=root/'PrintConfiguration.java';s=p.read_text(encoding='utf-8').replace('new PrintRuntime(new JdbcTemplate(source),','new PrintRuntime(').replace('new PrintFlow(new JdbcTemplate(source),','new PrintFlow(').replace('new PrintOutputs(new JdbcTemplate(source),','new PrintOutputs(')
s=s.replace('PrintReceiptMapper receiptMapper){return new PrintRuntime','PrintReceiptMapper receiptMapper,PrintRuntimeSqlMapper statements){return new PrintRuntime').replace('build(),receiptMapper);','build(),receiptMapper,statements);')
s=s.replace('PrintRuntime runtime,PrintFlow flow){return new PrintOutputs','PrintRuntime runtime,PrintFlow flow,PrintOutputsSqlMapper statements){return new PrintOutputs').replace('runtime,flow,tools.jackson.databind.json.JsonMapper.builder().build());','runtime,flow,tools.jackson.databind.json.JsonMapper.builder().build(),statements);')
s=s.replace('java.util.List<com.appnexus.contracts.printing.PrintDataContractProvider> providers){var flow=','java.util.List<com.appnexus.contracts.printing.PrintDataContractProvider> providers,PrintFlowSqlMapper statements){var flow=').replace('runtime,tools.jackson.databind.json.JsonMapper.builder().build());flow.','runtime,tools.jackson.databind.json.JsonMapper.builder().build(),statements);flow.')
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;','');p.write_text(s,encoding='utf-8')
