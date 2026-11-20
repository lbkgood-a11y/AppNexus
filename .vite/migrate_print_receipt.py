from pathlib import Path
root=Path('backend/services/print-service/adapters/jdbc/src/main/java/com/appnexus/printing/adapter/jdbc')
(root/'PrintReceiptMapper.java').write_text('''package com.appnexus.printing.adapter.jdbc;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.appnexus.persistence.mybatis.PlatformScoped;
/** 打印设备回执固定SQL；准入由PrintPlatform负责，支持后台租约，不伪造HTTP租户成员。 */
@Mapper
@PlatformScoped(tables={"print_job"})
public interface PrintReceiptMapper {
 /** @param tenant 平台验证的租户UUID @param id 租户内任务UUID @return 原事务锁定状态，缺失由存储端口返回404 */
 @Select("SELECT state,claimed_by,CAST(result_json AS CHAR) resultJson,(claim_expires_at<CURRENT_TIMESTAMP(6)) expired FROM print_job WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id}) FOR UPDATE")
 List<Map<String,Object>> lock(@Param("tenant") String tenant,@Param("id") String id);
 /** @param tenant 可信租户UUID @param id 已锁任务UUID @param state 领域计算的回执状态 @param result 已校验JSON结果 @return 更新行数，与回执和状态同事务 */
 @Update("UPDATE print_job SET state=#{state},result_json=CAST(#{result} AS JSON),claim_expires_at=NULL WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})")
 int update(@Param("tenant") String tenant,@Param("id") String id,@Param("state") String state,@Param("result") String result);
}
''',encoding='utf-8')
p=root/'JdbcPrintReceiptStore.java';s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;\n','').replace('JdbcPrintReceiptStore','MyBatisPrintReceiptStore').replace('private final JdbcTemplate jdbc;','private final PrintReceiptMapper statements;').replace('@param jdbc 与完整回执事务共用连接','@param statements 与完整回执事务共用连接的固定Mapper').replace('MyBatisPrintReceiptStore(JdbcTemplate jdbc,','MyBatisPrintReceiptStore(PrintReceiptMapper statements,').replace('{this.jdbc=jdbc;','{this.statements=statements;')
start=s.index('    return jdbc.query(');end=s.index('\n  }',start)
s=s[:start]+'''    var rows=statements.lock(tenant,id);if(rows.isEmpty())throw new PrintFailure("PRINT_JOB_NOT_FOUND",404);
    var row=rows.getFirst();Object expired=row.get("expired");
    boolean ended=Boolean.TRUE.equals(expired)||expired instanceof Number value&&value.intValue()!=0;
    Object previous=null;String text=(String)row.get("resultJson");
    if(text!=null)try{previous=mapper.readTree(text);}catch(Exception ignored){/* 损坏结果不能当作相同回执。 */}
    return new Current((String)row.get("state"),(String)row.get("claimed_by"),ended,Objects.equals(previous,mapper.readTree(result)));'''+s[end:]
start=s.index('    jdbc.update(');end=s.index(';',start);s=s[:start]+'    statements.update(tenant,id,state,result)'+s[end:]
(root/'MyBatisPrintReceiptStore.java').write_text(s,encoding='utf-8');p.unlink()
# PrintOutputs负责原回执用例装配，注入固定Mapper。
p=root/'PrintOutputs.java';s=p.read_text(encoding='utf-8').replace('new JdbcPrintReceiptStore(jdbc,json)','new MyBatisPrintReceiptStore(receiptMapper,json)')
s=s.replace('private final JdbcTemplate jdbc;','private final PrintReceiptMapper receiptMapper;\n private final JdbcTemplate jdbc;')
import re
match=re.search(r'public PrintOutputs\(([^\n]+)\)\s*\{',s);assert match
sig=match.group(0);s=s.replace(sig,sig[:-1].replace('){','){') if False else sig,1)
s=s.replace(match.group(0),match.group(0).replace('){',',PrintReceiptMapper receiptMapper){').replace(') {',',PrintReceiptMapper receiptMapper) {')+'this.receiptMapper=receiptMapper;',1)
p.write_text(s,encoding='utf-8')
p=root/'PrintConfiguration.java';s=p.read_text(encoding='utf-8').replace('PrintRuntime runtime,PrintFlow flow){return new PrintOutputs','PrintRuntime runtime,PrintFlow flow,PrintReceiptMapper receiptMapper){return new PrintOutputs').replace('flow,tools.jackson.databind.json.JsonMapper.builder().build());','flow,tools.jackson.databind.json.JsonMapper.builder().build(),receiptMapper);');p.write_text(s,encoding='utf-8')
for p in Path('backend').rglob('*.java'):
 if any(x.startswith('target') for x in p.parts):continue
 s=p.read_text(encoding='utf-8-sig')
 if 'JdbcPrintReceiptStore' in s:p.write_text(s.replace('JdbcPrintReceiptStore','MyBatisPrintReceiptStore'),encoding='utf-8')
