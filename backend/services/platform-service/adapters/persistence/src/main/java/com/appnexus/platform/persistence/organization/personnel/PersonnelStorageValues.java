package com.appnexus.platform.persistence.organization.personnel;
import java.util.*;
/** 在数据库边界将驱动时间转换为JDK Instant，禁止将JDBC对象传入业务用例。 */
final class PersonnelStorageValues {
 private PersonnelStorageValues(){}
 @SuppressWarnings("unchecked") static <T>T stable(T value){
  if(value instanceof java.sql.Timestamp timestamp)return (T)timestamp.toInstant();
  if(value instanceof Map<?,?> map){var out=new LinkedHashMap<Object,Object>();map.forEach((key,item)->out.put(key,stable(item)));return (T)out;}
  if(value instanceof List<?> list)return (T)list.stream().map(PersonnelStorageValues::stable).toList();
  return value;
 }
}
