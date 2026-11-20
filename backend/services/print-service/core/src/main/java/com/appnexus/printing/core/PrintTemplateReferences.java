package com.appnexus.printing.core;
import java.util.*;
import com.appnexus.contracts.data.DataContract;
/** 模板field引用纯规则；只消费解析后的对象和数组，不执行任意字段路径。 */
public final class PrintTemplateReferences {
 private PrintTemplateReferences(){}
 /** @param node 原固定模板JDK对象 @param fields 原发布契约允许字段键，包含同一父契约下明细列 */
 public static void validate(Object node,Set<String> fields){if(node instanceof Map<?,?> map){for(var property:map.entrySet()){Object value=property.getValue();if(property.getKey().equals("field")&&value instanceof String name&&!name.isBlank()&&!fields.contains(name))throw new DataContract.Violation(name,"DATA_FIELD_UNKNOWN");validate(value,fields);}}else if(node instanceof List<?> list)for(Object child:list)validate(child,fields);}
}
