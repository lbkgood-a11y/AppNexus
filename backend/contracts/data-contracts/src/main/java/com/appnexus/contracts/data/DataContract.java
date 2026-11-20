package com.appnexus.contracts.data;

import java.util.*;

/**
 * 不可变数据契约；编码和正整数版本共同标识结构，变更字段语义必须升级版本。
 * @param code 稳定编码，不包含租户信息
 * @param version 正整数版本；已发布版本不可改写
 * @param purpose 数据用途，如 PRINT 或 WORKFLOW，不表示执行完成方式
 * @param fields 允许交付的字段；不提供隐式类型转换和隐式默认值
 */
public record DataContract(String code, int version, String purpose, List<Field> fields) {
  /** 标量类型严格区分数值、布尔和文本，避免审批条件隐式转换。 */
  public enum Type { TEXT, NUMBER, BOOLEAN }
  /**
   * 字段路径按点分隔访问嵌套对象，不执行表达式。
   * @param path 必填字段路径；中间对象由提供方构造
   * @param type 实际值类型，不接受数值字符串冒充 NUMBER
   * @param required 是否必填；缺省值为禁止自动补齐，缺失可选字段保持缺失
   * @param unit 单位；空字符串表示无单位，不自动换算
   */
  public record Field(String path, Type type, boolean required, String unit) {}
  /** 拒绝重复路径或无效定义，避免运行时使用不确定结构。 */
  public DataContract {
    if(code==null||code.isBlank()||version<1||purpose==null||purpose.isBlank())throw new IllegalArgumentException("DATA_CONTRACT_INVALID");
    fields=List.copyOf(fields);var paths=new HashSet<String>();
    for(var f:fields)if(f.path()==null||!f.path().matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*")||f.type()==null||!paths.add(f.path()))throw new IllegalArgumentException("DATA_CONTRACT_FIELD_INVALID");
    for (String path : paths) if (paths.stream().anyMatch(other -> other.startsWith(path + ".")))
      throw new IllegalArgumentException("DATA_CONTRACT_FIELD_INVALID");
  }
  /**
   * 在执行边界校验完整交付数据；多余字段也拒绝，避免无关敏感数据进入快照。
   * @param data 业务授权后提供的数据对象；不读取业务表，不修改传入对象
   * @throws Violation 必填、类型或字段白名单不符；错误仅包含路径和稳定码
   */
  public void validate(Map<String,?> data) {
    if(data==null)throw new Violation("$","DATA_OBJECT_REQUIRED");
    for(var f:fields){
      Object value=data;for(String part:f.path().split("\\."))value=value instanceof Map<?,?> map?map.get(part):null;
      if(value==null){if(f.required())throw new Violation(f.path(),"DATA_FIELD_REQUIRED");continue;}
      boolean valid=switch(f.type()){case TEXT->value instanceof String s&&s.length()<=2000;case BOOLEAN->value instanceof Boolean;case NUMBER->value instanceof Number n&&Double.isFinite(n.doubleValue());};
      if(!valid)throw new Violation(f.path(),"DATA_FIELD_TYPE_INVALID");
    }
    inspect(data,"");
  }
  /**
   * 将已授权对象数据投影为本契约字段；审批和打印复用同一白名单，禁止透传整条记录。
   * @param source 已经完成记录与字段授权的业务数据；本方法不授予读取权限
   * @return 深度不可变的契约投影；可选空值保持缺失，必填缺失拒绝
   */
  public Map<String,Object> project(Map<String,?> source) {
    Objects.requireNonNull(source);
    var result = new LinkedHashMap<String,Object>();
    for (var field : fields) {
      Object value = pathValue(source, field.path());
      if (value != null) putPath(result, field.path(), value);
    }
    validate(result);
    return immutable(result);
  }

  /**
   * 按本契约生成审批字符串传输字段；字段类型和完整性先校验，不隐式补默认值。
   * @param source 已授权对象字段；只输出本契约声明字段
   * @return 键为完整字段路径的不可变标量映射
   */
  public Map<String,String> encode(Map<String,?> source) {
    var data = project(source);
    var result = new LinkedHashMap<String,String>();
    for (var field : fields) {
      Object value = pathValue(data, field.path());
      if (value != null) result.put(field.path(), value.toString());
    }
    return Map.copyOf(result);
  }

  /**
   * 按字段类型解码引擎字符串快照并严格验证；未知字段及非法布尔、数值失败关闭。
   * @param values 路径到字符串的受控传输映射，不能作为可信业务数据来源
   * @return 深度不可变的有类型数据；错误只包含字段路径和稳定码
   */
  public Map<String,Object> decode(Map<String,String> values) {
    Objects.requireNonNull(values);
    var declared = new HashMap<String,Field>();
    fields.forEach(field -> declared.put(field.path(), field));
    var result = new LinkedHashMap<String,Object>();
    for (var entry : values.entrySet()) {
      var field = declared.get(entry.getKey());
      if (field == null) throw new Violation(entry.getKey(), "DATA_FIELD_UNKNOWN");
      if (entry.getValue() == null) continue;
      Object value;
      try {
        value = switch (field.type()) {
          case TEXT -> entry.getValue();
          case NUMBER -> new java.math.BigDecimal(entry.getValue());
          case BOOLEAN -> {
            if (!Set.of("true", "false").contains(entry.getValue())) throw new IllegalArgumentException();
            yield Boolean.valueOf(entry.getValue());
          }
        };
      } catch (IllegalArgumentException error) {
        throw new Violation(field.path(), "DATA_FIELD_TYPE_INVALID");
      }
      putPath(result, field.path(), value);
    }
    validate(result);
    return immutable(result);
  }

  private static Object pathValue(Map<String,?> source, String path) {
    Object value = source;
    for (String part : path.split("\\.")) {
      if (value == null) return null;
      if (!(value instanceof Map<?,?> map)) throw new Violation(path, "DATA_FIELD_TYPE_INVALID");
      value = map.get(part);
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  private static void putPath(Map<String,Object> target, String path, Object value) {
    String[] parts = path.split("\\.");
    for (int index = 0; index < parts.length - 1; index++) {
      Object child = target.computeIfAbsent(parts[index], ignored -> new LinkedHashMap<String,Object>());
      if (!(child instanceof Map<?,?>)) throw new Violation(path, "DATA_FIELD_TYPE_INVALID");
      target = (Map<String,Object>) child;
    }
    target.put(parts[parts.length - 1], value);
  }

  private static Map<String,Object> immutable(Map<String,Object> source) {
    var copy = new LinkedHashMap<String,Object>();
    source.forEach((key,value) -> {
      if (value instanceof Map<?,?> nested) {
        var child = new LinkedHashMap<String,Object>();
        nested.forEach((childKey,childValue) -> child.put((String) childKey,childValue));
        copy.put(key, immutable(child));
      } else copy.put(key,value);
    });
    return Collections.unmodifiableMap(copy);
  }
  private void inspect(Map<?,?> data,String prefix){
    for(var entry:data.entrySet()){
      String path=prefix+entry.getKey();
      if(fields.stream().anyMatch(f->f.path().equals(path)))continue;
      if(fields.stream().noneMatch(f->f.path().startsWith(path+".")))throw new Violation(path,"DATA_FIELD_UNKNOWN");
      if(!(entry.getValue() instanceof Map<?,?> nested))throw new Violation(path,"DATA_FIELD_TYPE_INVALID");
      inspect(nested,path+".");
    }
  }
  /** 字段错误不包含真实数据，调用方可安全映射到字段反馈。 */
  public static final class Violation extends IllegalArgumentException {
    private final String path;
    public Violation(String path,String code){super(code);this.path=path;}
    /** @return 出错字段路径，不包含字段原值 */
    public String path(){return path;}
  }
}
