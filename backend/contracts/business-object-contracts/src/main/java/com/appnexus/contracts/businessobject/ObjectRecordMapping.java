package com.appnexus.contracts.businessobject;

import java.util.*;

/** 构建期对象存储映射；只由对象所有者代码声明，禁止从JSON、租户配置或HTTP接收。 */
public record ObjectRecordMapping(String table,Column tenant,Column id,Column organization,Column applicant,
    String version,String state,String instance,String delivery,Column person,Map<String,Value> fields,
    Map<String,Transition> transitions,Receipt receipt,boolean auditColumns) {
  /** 身份存储格式；BINARY_UUID仅对参数执行UUID_TO_BIN，TEXT用于既有文本主键。 */
  public enum Encoding { TEXT,BINARY_UUID }
  /** @param name 代码固定列标识 @param encoding 身份真实存储格式 */
  public record Column(String name,Encoding encoding){public Column{identifier(name);Objects.requireNonNull(encoding);}}
  /** 可复用字段投影；不支持任意表达式。GREATER_THAN只用于非敏感数字规则。 */
  public enum Transform { VALUE,DATE_TEXT,GREATER_THAN }
  /** @param column 代码固定来源列 @param transform VALUE/DATE_TEXT/GREATER_THAN @param threshold GREATER_THAN阈值，其他情况为0 */
  public record Value(Column column,Transform transform,long threshold){public Value{Objects.requireNonNull(column);Objects.requireNonNull(transform);}}
  /** @param allowedStates 允许执行的当前状态 @param targetState BUSINESS直接执行后的状态，可空 @param requirePerson 是否要求匹配记录人员和当前组织 */
  public record Transition(Set<String> allowedStates,String targetState,boolean requirePerson){public Transition{allowedStates=Set.copyOf(allowedStates);if(allowedStates.isEmpty())throw new IllegalArgumentException("OBJECT_TRANSITION_REQUIRED");}}
  /** 已有轮次表的受控回执映射；不创建新一套授权或业务状态表。 */
  public record Receipt(String table,Column tenant,Column objectId,String version,String instance,String delivery,String result,String completed){
    public Receipt{identifier(table);Objects.requireNonNull(tenant);Objects.requireNonNull(objectId);identifier(version);identifier(instance);identifier(delivery);identifier(result);identifier(completed);}
  }
  /** 验证全部固定标识并深复制；未知表达式和SQL片段在装配时拒绝。 */
  public ObjectRecordMapping{
    identifier(table);identifier(version);identifier(state);identifier(instance);identifier(delivery);
    Objects.requireNonNull(tenant);Objects.requireNonNull(id);Objects.requireNonNull(organization);Objects.requireNonNull(applicant);
    fields=Map.copyOf(fields);transitions=Map.copyOf(transitions);
    for(String field:fields.keySet())if(!field.matches("[A-Za-z][A-Za-z0-9]*"))throw new IllegalArgumentException("OBJECT_FIELD_MAPPING_INVALID");
    for(var entry:transitions.entrySet()){
      if(!entry.getKey().matches("[a-z][A-Za-z0-9]*"))throw new IllegalArgumentException("OBJECT_OPERATION_MAPPING_INVALID");
      if(entry.getValue().requirePerson()&&person==null)throw new IllegalArgumentException("OBJECT_PERSON_MAPPING_REQUIRED");
    }
  }
  private static void identifier(String value){if(value==null||!value.matches("[a-z][a-z0-9_]*"))throw new IllegalArgumentException("OBJECT_FIXED_IDENTIFIER_REQUIRED");}
}
