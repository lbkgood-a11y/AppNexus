package com.appnexus.platform.persistence.businessobject;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.platform.businessobject.BusinessObjectUseCases;
import com.appnexus.platform.businessobject.port.ObjectRecordStore;
import java.util.*;

import org.springframework.stereotype.Component;

/** 对象所有者固定映射的JDBC编译器；只访问注入映射指定的自有表，禁止外部配置构造。 */
@Component
public final class MappedObjectRuntimeFactory implements ObjectRecordRuntimeFactory,ObjectRecordStore {
  private final ObjectRecordMapper mapper;
  private final com.appnexus.platform.authorization.ScopedWriteGate writes;
  /** @param mapper 对象执行器固定存储 @param writes 绑定原申请人和对象键的事务许可 */
  public MappedObjectRuntimeFactory(ObjectRecordMapper mapper,com.appnexus.platform.authorization.ScopedWriteGate writes){
    this.mapper=mapper;
    this.writes=writes;
  }
  @Override public BusinessObjectRuntime create(BusinessObjects.Provider provider,ObjectRecordMapping mapping,Hook hook){return BusinessObjectUseCases.mappedRuntime(provider,mapping,this,hook);}
  private static String select(ObjectRecordMapping.Column column){return column.encoding()==ObjectRecordMapping.Encoding.BINARY_UUID?"BIN_TO_UUID("+column.name()+")":column.name();}
  private static String parameter(ObjectRecordMapping.Column column){return column.encoding()==ObjectRecordMapping.Encoding.BINARY_UUID?"UUID_TO_BIN(?)":"?";}
  private static String where(ObjectRecordMapping mapping){return mapping.tenant().name()+"="+parameter(mapping.tenant())+" AND "+mapping.id().name()+"="+parameter(mapping.id());}
  @Override public Map<String,Object> read(ObjectRecordMapping m,BusinessObjects.Context c,String id,boolean lock){
    var rows=mapper.read(m,c,id,lock);
    if(rows.size()!=1)throw new BusinessObjects.Rejection(403,"BUSINESS_OBJECT_RECORD_DENIED");
    var row=new LinkedHashMap<String,Object>(rows.getFirst());
    m.fields().forEach((field,value)->{Object original=row.get("field_"+field);if(original==null)return;row.put("field_"+field,switch(value.transform()){
      case VALUE->original;
      case DATE_TEXT->original.toString();
      case GREATER_THAN->{if(!(original instanceof Number number))throw new IllegalArgumentException("OBJECT_MAPPING_NUMBER_REQUIRED");yield new java.math.BigDecimal(number.toString()).compareTo(java.math.BigDecimal.valueOf(value.threshold()))>0;}
    });});
    return row;
  }
  @Override public void transition(ObjectRecordMapping m,BusinessObjects.Context c,String id,long version,String from,String to,String invocation,String instance){
    // 原功能和审批实例准入由执行器完成；在同事务以权威记录复核申请人，不采用请求提供的所有者。
    var rows=mapper.read(m,c,id,true);
    writes.authorizeServiceOperation(UUID.fromString(c.tenantId()),"business-object.record:transition",
        List.of(id,c.actorId()),()->rows.size()==1&&Objects.equals(c.actorId(),rows.getFirst().get("applicant")));
    int changed=mapper.transition(m,c,id,version,from,to,invocation,instance);
    if(changed!=1)throw new BusinessObjects.Rejection(409,"BUSINESS_OBJECT_STATE_CONFLICT");
    if(invocation!=null&&m.receipt()!=null){var r=m.receipt();
      changed=mapper.receipt(m,c,id,version,to,invocation,instance);
      if(changed!=1)throw new BusinessObjects.Rejection(409,"BUSINESS_OBJECT_STATE_CONFLICT");
    }
  }
}
