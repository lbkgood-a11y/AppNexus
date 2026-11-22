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

    private static SecurityException invalid(){return new SecurityException("ROW_ACCESS_BINDING_MISMATCH");}
}
