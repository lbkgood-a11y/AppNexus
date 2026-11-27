package com.appnexus.persistence.mybatis;

import java.util.List;
import java.util.Objects;

/** 将公共权限编译结果绑定到固定 MyBatis 参数路径；不接收客户端 SQL。 */
public final class ScopedSql {
    private static final String START=" AND /*appnexus-scope*/ ";
    private ScopedSql(){}

    /**
     * @param sql 公共权限编译器的 AND 谓词；空串表示显式 ALL
     * @param parameters 按顺序绑定的标量
     * @param path 固定 Mapper 参数路径
     * @return 可供 MyBatis 解析的受控附加条件
     */
    public static String bind(String sql,List<?> parameters,String path){
        Objects.requireNonNull(parameters,"ROW_ACCESS_PARAMETERS_REQUIRED");
        if(path==null||!path.matches("[a-zA-Z][a-zA-Z0-9]*(\\.[a-zA-Z][a-zA-Z0-9]*)*"))
            throw new IllegalArgumentException("ROW_ACCESS_PARAMETER_PATH_INVALID");
        String result=fragment(sql);
        int offset=0;
        var bound=new StringBuilder();
        for(int i=0;i<parameters.size();i++){
            int next=result.indexOf('?',offset);
            if(next<0)throw invalid();
            bound.append(result,offset,next).append("#{").append(path).append(".parameters[").append(i).append("]}");
            offset=next+1;
        }
        if(result.indexOf('?',offset)>=0)throw invalid();
        return bound.append(result.substring(offset)).toString();
    }

    /**
     * @param sql 可信编译器谓词
     * @return 执行前必须在固定 SQL 中出现的模式，包含绑定问号
     */
    public static String fragment(String sql){
        Objects.requireNonNull(sql,"ROW_ACCESS_PREDICATE_REQUIRED");
        if(sql.isEmpty())return START+"1=1";
        if(!sql.startsWith(" AND "))throw invalid();
        return START+sql.substring(5);
    }

    /**
     * 自助查询的固定本人范围，支持历史未知人员的记录。
     * @param accountColumn 对象声明中的申请人列
     * @param personColumn 对象声明中的人员列；空表示只限定账号
     * @param account 当前请求的账号文本，生产环境必须匹配认证上下文中的 UUID
     * @param person 当前已选人员文本，可空；生产环境必须匹配认证上下文中的人员 UUID
     */
    public record OwnerPredicate(String accountColumn,String personColumn,String account,String person) {
        public OwnerPredicate {
            if (!identifier(accountColumn)||(personColumn!=null&&!identifier(personColumn))
                ||(personColumn==null&&person!=null)) throw invalid();
            if(account==null||account.isBlank()||(person!=null&&person.isBlank()))throw invalid();
        }

        /** @return 本人账号和当前人员的交集条件，保留历史空人员记录 */
        public String sql(){
            String accountFilter=" AND "+accountColumn+" = ?";
            if(personColumn==null)return accountFilter;
            return accountFilter+" AND "+(person==null
                ? personColumn+" IS NULL" : "("+personColumn+" = ? OR "+personColumn+" IS NULL)");
        }

        /** @return 按占位顺序排列的可信身份值 */
        public List<String> parameters(){return person==null?List.of(account):List.of(account,person);}

        private static boolean identifier(String column){return column!=null&&column.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)?");}
    }

    private static SecurityException invalid(){return new SecurityException("ROW_ACCESS_BINDING_MISMATCH");}
}
