package com.appnexus.masterdata.adapter.persistence;

import com.appnexus.masterdata.api.masterdata.StatusHistory.Entry;
import com.appnexus.persistence.mybatis.PlatformScoped;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 经应用层复核对象准入后，按可信租户、固定类型和目标记录计算分页。 */
@Mapper
@PlatformScoped(tables = {"md_master_status_change"})
public interface MasterStatusHistoryMapper {
    String TARGET = " FROM md_master_status_change WHERE tenant_id=UUID_TO_BIN(#{tenant})"
            + " AND object_type=#{subject} AND object_id=UUID_TO_BIN(#{id})";

    /** 仅在同一事务的启停 CAS 确认修改一条记录后调用；目标状态相反即为已验证的旧状态。 */
    @Insert("""
        INSERT INTO md_master_status_change
            (tenant_id, object_type, object_id, to_version, before_enabled, after_enabled, created_by, updated_by)
        VALUES (UUID_TO_BIN(#{tenant}), #{subject}, UUID_TO_BIN(#{id}), #{version}+1,
                NOT #{enabled}, #{enabled}, UUID_TO_BIN(#{actor}), UUID_TO_BIN(#{actor}))
        """)
    int append(@Param("tenant") String tenant, @Param("subject") String subject, @Param("id") String id,
               @Param("version") long version, @Param("enabled") boolean enabled, @Param("actor") String actor);

    /** @return 当前对象的启停历史，按版本倒序 */
    @Select("SELECT to_version version,before_enabled beforeEnabled,after_enabled afterEnabled,"
            + "BIN_TO_UUID(created_by) createdBy,created_at createdAt" + TARGET
            + " ORDER BY to_version DESC LIMIT #{size} OFFSET #{offset}")
    List<Entry> list(@Param("tenant") String tenant, @Param("subject") String subject, @Param("id") String id,
                     @Param("offset") long offset, @Param("size") int size);

    /** @return 与分页相同条件的历史总数 */
    @Select("SELECT COUNT(*)" + TARGET)
    long count(@Param("tenant") String tenant, @Param("subject") String subject, @Param("id") String id);
}
