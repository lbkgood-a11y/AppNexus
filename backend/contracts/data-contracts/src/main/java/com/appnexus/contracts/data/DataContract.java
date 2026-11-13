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
