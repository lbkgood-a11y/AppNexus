package com.appnexus.leave.adapter.persistence;
import java.util.*;
import java.time.LocalDate;
import com.appnexus.leave.application.LeaveRepository;
/** 固定请假SQL适配，日期及驱动类型在此边界处理，不决定业务授权。 */
public final class MyBatisLeaveRepository implements LeaveRepository {
 private final LeaveRecordMapper mapper;
 /** @param mapper 原固定请假SQL映射 */
 public MyBatisLeaveRepository(LeaveRecordMapper mapper){this.mapper=mapper;}
 /** {@inheritDoc} */
 public List<Map<String,Object>> appliedApprovals(String tenant,String id){return LeaveStorageValues.stable(mapper.appliedApprovals(tenant,id));}
 /** {@inheritDoc} */
 public int createDraft(String tenant,String id,String actor,String organization,String type,LocalDate start,LocalDate end,int days,String reason,String request,String person){return mapper.createDraft(tenant,id,actor,organization,type,start,end,days,reason,request,person);}
 /** {@inheritDoc} */
 public Map<String,Object> createdRequest(String tenant,String actor,String request){return LeaveStorageValues.stable(mapper.createdRequest(tenant,actor,request));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> ownRequests(String tenant,String actor,String person){return LeaveStorageValues.stable(mapper.ownRequests(tenant,actor,person));}
 /** {@inheritDoc} */
 public int markSubmitted(String tenant,String id){return mapper.markSubmitted(tenant,id);}
 /** {@inheritDoc} */
 public int recordSubmission(String tenant,String id,long basis,String resolution,String service){return mapper.recordSubmission(tenant,id,basis,resolution,service);}
 /** {@inheritDoc} */
 public int createApprovalCycle(String tenant,String id,String object,long basis,String instance,String resolution,int configuration,String type,String start,String end,int days,String reason,String organization){return mapper.createApprovalCycle(tenant,id,object,basis,instance,resolution,configuration,type,start,end,days,reason,organization);}
 /** {@inheritDoc} */
 public int bindInstance(String instance,String tenant,String id){return mapper.bindInstance(instance,tenant,id);}
 /** {@inheritDoc} */
 public int updateDraft(String organization,String type,LocalDate start,LocalDate end,int days,String reason,String tenant,String id){return mapper.updateDraft(organization,type,start,end,days,reason,tenant,id);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> approvalCycles(String tenant,String id){return LeaveStorageValues.stable(mapper.approvalCycles(tenant,id));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> request(String tenant,String id,boolean lock){return LeaveStorageValues.stable(mapper.request(tenant,id,lock));}
}
