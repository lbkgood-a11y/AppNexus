package com.appnexus.printing.adapter.persistence;
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
