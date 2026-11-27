package com.appnexus.platform.businessobject.internal.application.execution;

import com.appnexus.contracts.businessobject.BusinessObjects.*;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.platform.businessobject.port.ObjectRecordStore;
import java.util.*;

/** 从对象唯一声明和固定存储映射执行标准记录生命周期，不包含对象类型分支。 */
public final class MappedObjectRuntime implements BusinessObjectRuntime {
  private final Provider provider;
  private final ObjectRecordMapping mapping;
  private final ObjectRecordStore store;
  private final ObjectRecordRuntimeFactory.Hook hook;

  /**
   * @param provider 唯一对象声明 @param mapping 构建期映射 @param store 与公共执行器共事务的存储 @param
   *                 hook 特有业务约束
   */
  public MappedObjectRuntime(Provider provider, ObjectRecordMapping mapping, ObjectRecordStore store,
      ObjectRecordRuntimeFactory.Hook hook) {
    this.provider = Objects.requireNonNull(provider);
    this.mapping = Objects.requireNonNull(mapping);
    this.store = Objects.requireNonNull(store);
    this.hook = Objects.requireNonNull(hook);
    var descriptor = provider.descriptor();
    var operations = descriptor.operations().stream().map(Operation::operationCode)
        .collect(java.util.stream.Collectors.toSet());
    if (!operations.containsAll(mapping.transitions().keySet()))
      throw new IllegalArgumentException("OBJECT_OPERATION_MAPPING_UNDECLARED");
    var fields = new HashSet<String>();
    descriptor.fields().forEach(field -> fields.add(field.key()));
    descriptor.operations().forEach(operation -> operation.interaction().capabilities()
        .forEach(capability -> capability.fields().forEach(field -> fields.add(field.key()))));
    if (!mapping.fields().keySet().containsAll(fields))
      throw new IllegalArgumentException("OBJECT_FIELD_MAPPING_MISSING");
  }

  private Operation operation(String code) {
    return provider.descriptor().operations().stream().filter(op -> op.operationCode().equals(code)).findFirst()
        .orElseThrow(() -> new Rejection(403, "BUSINESS_OBJECT_OPERATION_UNAVAILABLE"));
  }

  @Override
  public Snapshot read(Context c, String type, String id, String code, long version, Set<String> fields) {
    if (!provider.descriptor().objectType().equals(type))
      throw new Rejection(403, "BUSINESS_OBJECT_RECORD_DENIED");
    operation(code);
    var transition = mapping.transitions().get(code);
    if (transition == null)
      throw new Rejection(403, "BUSINESS_OBJECT_OPERATION_UNAVAILABLE");
    var row = store.read(mapping, c, id, true);
    if (!Objects.equals(c.actorId(), row.get("applicant")))
      throw new Rejection(403, "BUSINESS_OBJECT_RECORD_DENIED");
    if (((Number) row.get("version")).longValue() != version)
      throw new Rejection(409, "BUSINESS_OBJECT_VERSION_CONFLICT");
    if (!transition.allowedStates().contains(row.get("state")))
      throw new Rejection(409, "BUSINESS_OBJECT_STATE_CONFLICT");
    if (transition.requirePerson() && (!Objects.equals(c.personId(), row.get("person"))
        || !Objects.equals(c.organizationId(), row.get("organization"))))
      throw new Rejection(409, "PERSON_CONTEXT_REQUIRED");
    var data = values(row);
    if (!data.keySet().containsAll(fields))
      throw new Rejection(403, "BUSINESS_OBJECT_FIELD_DENIED");
    data.keySet().retainAll(fields);
    return new Snapshot(version, (String) row.get("organization"), data);
  }

  @Override
  public Result execute(Context c, String type, String id, String code, long version, Map<String, ?> values) {
    if (!values.isEmpty())
      throw new Rejection(403, "BUSINESS_OBJECT_INPUT_DENIED");
    read(c, type, id, code, version, Set.of());
    var transition = mapping.transitions().get(code);
    if (transition.targetState() == null)
      throw new Rejection(403, "BUSINESS_OBJECT_DIRECT_UNSUPPORTED");
    var row = store.read(mapping, c, id, true);
    store.transition(mapping, c, id, version, row.get("state").toString(), transition.targetState(), null, null);
    return new Result(version, Map.of("state", transition.targetState()));
  }

  @Override
  public void beforeCapability(Context c, String id, String code, InteractionContract.Service service) {
    hook.before(c, id, code, service);
  }

  @Override
  public ApprovalRecord approvalRecord(Context c, String id, boolean lock) {
    var row = store.read(mapping, c, id, lock);
    return new ApprovalRecord(((Number) row.get("version")).longValue(), (String) row.get("organization"),
        (String) row.get("applicant"), (String) row.get("state"), (String) row.get("instance"),
        (String) row.get("delivery"), values(row));
  }

  @Override
  public void completeApproval(Context c, String id, long version, String invocation, String instance, String result) {
    boolean allowed = provider.descriptor().operations().stream()
        .anyMatch(op -> op.approval() != null && op.approval().outcomes().contains(result));
    if (!allowed)
      throw new Rejection(403, "BUSINESS_OBJECT_RESULT_UNDECLARED");
    store.transition(mapping, c, id, version, "SUBMITTED", result, invocation, instance);
  }

  private LinkedHashMap<String, Object> values(Map<String, Object> row) {
    var data = new LinkedHashMap<String, Object>();
    for (String field : mapping.fields().keySet())
      if (row.get("field_" + field) != null)
        data.put(field, row.get("field_" + field));
    return data;
  }
}
