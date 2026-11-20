package com.appnexus.leave.adapter.persistence;
import java.util.*;
/** 驱动日期转为无时区LocalDate，时间点转Instant，业务层不接触JDBC类型。 */
final class LeaveStorageValues {
 private LeaveStorageValues(){}
 @SuppressWarnings("unchecked") static <T>T stable(T value){if(value instanceof java.sql.Date date)return (T)date.toLocalDate();if(value instanceof java.sql.Timestamp time)return (T)time.toInstant();if(value instanceof Map<?,?> map){var out=new LinkedHashMap<Object,Object>();map.forEach((key,item)->out.put(key,stable(item)));return (T)out;}if(value instanceof List<?> list)return (T)list.stream().map(LeaveStorageValues::stable).toList();return value;}
}
