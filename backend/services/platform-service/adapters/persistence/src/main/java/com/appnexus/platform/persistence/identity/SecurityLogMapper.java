package com.appnexus.platform.persistence.identity;
import java.util.*;
import org.apache.ibatis.annotations.*;
/** 日志 SQL 显式带认证账号条件，不提供跨账号或敏感载荷字段。 */
@com.appnexus.platform.persistence.PlatformScoped(tables={"account_security_log"})
@Mapper
public interface SecurityLogMapper {
    /** 参数 action 仅接受服务端枚举名称。 */
    @Insert("INSERT INTO account_security_log(account_id,action) VALUES(UUID_TO_BIN(#{id}),#{action})")
    void append(@Param("id") String id, @Param("action") String action);
    /** offset 为非负偏移，size 在端口实现中限制为最多 50 条。 */
    @Select("SELECT id,action,occurred_at FROM account_security_log WHERE account_id=UUID_TO_BIN(#{id}) ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<Map<String,Object>> find(@Param("id") String id, @Param("offset") int offset, @Param("size") int size);
    /** 只统计指定主体本人的记录。 */
    @Select("SELECT COUNT(*) FROM account_security_log WHERE account_id=UUID_TO_BIN(#{id})")
    long count(String id);
}
