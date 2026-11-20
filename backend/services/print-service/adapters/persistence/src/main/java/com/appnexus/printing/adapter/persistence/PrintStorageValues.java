package com.appnexus.printing.adapter.persistence;
import java.util.*;
/** 存储投影转换为稳定JDK数据，禁止将驱动时间对象传入业务用例。 */
final class PrintStorageValues {
 private PrintStorageValues(){}
 @SuppressWarnings("unchecked") static <T>T stable(T value){if(value instanceof java.sql.Timestamp timestamp)return (T)timestamp.toInstant();if(value instanceof Map<?,?> map){var out=new LinkedHashMap<Object,Object>();map.forEach((key,item)->out.put(key,stable(item)));return (T)out;}if(value instanceof List<?> list)return (T)list.stream().map(PrintStorageValues::stable).toList();return value;}
}
