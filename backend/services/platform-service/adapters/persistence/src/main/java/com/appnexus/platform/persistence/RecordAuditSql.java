package com.appnexus.platform.persistence;

import java.sql.SQLException;
import java.util.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.update.Update;

/**
 * 对已具备四个审计列的自有表补充缺省审计值；不改变业务谓词、参数顺序或已有显式审计表达式。
 * 使用 SQL 语法树处理 INSERT VALUES、INSERT SELECT、批量值及冲突更新，不能解析的业务写入拒绝执行。
 */
final class RecordAuditSql {
    private RecordAuditSql() {}

    /**
     * @param sql 服务端固定 SQL，不接收客户端 SQL
     * @param tables 启动迁移后从元数据确认的自有表白名单
     * @param actor 可信账号；后台任务或机器主体为空
     * @return 保持占位符数量及顺序的 SQL
     * @throws SQLException 自有业务写入无法安全解析时拒绝，避免静默漏审计
     */
    static String rewrite(String sql, Set<String> tables, UUID actor) throws SQLException {
        String lower=sql.stripLeading().toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("insert ") || lower.startsWith("update "))) return sql;
        // 仅用正则识别目标表以绕过上游 SQL 方言；真正改写仍由语法树执行，未知业务形状不会静默放行。
        var target=java.util.regex.Pattern.compile("(?is)^\\s*(?:insert\\s+(?:ignore\\s+)?into|update)\\s+`?([a-z0-9_]+)`?\\b").matcher(sql);
        if(target.find()&&!tables.contains(target.group(1).toLowerCase(Locale.ROOT)))return sql;
        try {
            var statement=CCJSqlParserUtil.parse(sql);
            String table=statement instanceof Insert i?i.getTable().getName():statement instanceof Update u?u.getTable().getName():"";
            if (!tables.contains(table.replace("`", "").toLowerCase(Locale.ROOT))) return sql;
            String who=actor==null?"NULL":"UUID_TO_BIN('"+actor+"')";
            var values=new LinkedHashMap<String,String>();
            if(statement instanceof Insert insert){
                if(insert.getColumns()==null) throw new SQLException("Audited INSERT requires explicit columns");
                var columns=new HashSet<String>();
                insert.getColumns().forEach(c->columns.add(c.getColumnName().replace("`", "").toLowerCase(Locale.ROOT)));
                for(String field:List.of("created_by","created_at","updated_by","updated_at"))
                    if(!columns.contains(field))values.put(field,field.endsWith("_by")?who:"CURRENT_TIMESTAMP(6)");
                for(var entry:values.entrySet()){
                    insert.addColumns(new Column(entry.getKey()));
                    if(insert.getSelect() instanceof net.sf.jsqlparser.statement.select.Values rows)append(rows.getExpressions(),entry.getValue());
                    else if(insert.getSelect() instanceof net.sf.jsqlparser.statement.select.PlainSelect select)select.addSelectItem(CCJSqlParserUtil.parseExpression(entry.getValue()));
                    else throw new SQLException("Unsupported audited INSERT SELECT shape");
                }
                if(insert.getDuplicateUpdateSets()!=null)fillUpdates(insert.getDuplicateUpdateSets(),who,null);
            } else if(statement instanceof Update update)fillUpdates(update.getUpdateSets(),who,update.getTable().getAlias()==null?null:update.getTable().getAlias().getName());
            return statement.toString();
        }catch(net.sf.jsqlparser.JSQLParserException invalid){throw new SQLException("Cannot parse audited write",invalid);}
    }

    /** VALUES 的单行和多行节点分别扩展，新增表达式不引入参数，不改变绑定序号。 */
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void append(ExpressionList expressions,String value) throws net.sf.jsqlparser.JSQLParserException {
        if(!expressions.isEmpty() && expressions.get(0) instanceof ExpressionList){
            for(Object row:expressions)((ExpressionList)row).add(CCJSqlParserUtil.parseExpression(value));
        }else expressions.add(CCJSqlParserUtil.parseExpression(value));
    }

    /** 业务显式绑定的审计参数来自原服务契约；仅填补缺失列，不能移除参数或改变调用约定。 */
    private static void fillUpdates(List<net.sf.jsqlparser.statement.update.UpdateSet> sets,String who,String alias) throws net.sf.jsqlparser.JSQLParserException {
        Set<String> columns=new HashSet<>();
        sets.forEach(s->s.getColumns().forEach(c->columns.add(c.getColumnName().replace("`", "").toLowerCase(Locale.ROOT))));
        for(String field:List.of("updated_by","updated_at"))if(!columns.contains(field))
            sets.add(new net.sf.jsqlparser.statement.update.UpdateSet(new Column(alias==null?field:alias+"."+field),CCJSqlParserUtil.parseExpression(field.endsWith("_by")?who:"CURRENT_TIMESTAMP(6)")));
    }
}
