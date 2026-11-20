package com.appnexus.platform.persistence.platformscope;

import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 授权失效事件的固定写入映射；只访问平台 Outbox，不承担授权决策。 */
@Mapper
@PlatformScoped(tables={"platform_outbox"})
public interface AuthorityEventMapper {
    /**
     * 在现有授权事务追加失效事件；调用方必须使用 MANDATORY 事务。
     * @param id 服务端生成的事件 UUID
     * @param tenant 服务端租户 UUID；平台事件沿用既有全零租户标记
     * @param aggregate 被修改的授权聚合 UUID，不可缺省
     * @return 插入行数；失败传播以回滚原授权写入
     */
    @Insert("INSERT INTO platform_outbox(id,tenant_id,event_type,aggregate_id) VALUES(UUID_TO_BIN(#{id}),UUID_TO_BIN(#{tenant}),'AUTHORITY_INVALIDATED',#{aggregate})")
    int append(@Param("id") String id,@Param("tenant") String tenant,@Param("aggregate") String aggregate);
}
