package com.appnexus.masterdata.adapter.persistence.masterdata;
import com.appnexus.masterdata.domain.geography.GeographyHierarchy;
import com.appnexus.masterdata.domain.geography.GeographyStandards;
import com.appnexus.masterdata.adapter.persistence.GeographyMapper;

import java.util.*;

import static com.appnexus.masterdata.api.masterdata.MasterData.*;

/** 租户地理图：标准目录作为底图，当前租户的名称及父级覆盖底图；不修改共享标准快照。 */
final class TenantGeography {
  /** 节点类型区分国别与区划，不能仅靠编码前缀猜测。 */
  record Item(String code,String name,String parent,boolean country) {}
  private final Map<String,Item> items=new LinkedHashMap<>();
  private final Map<String,String> originals=new HashMap<>();
  private final Map<String,String> paths=new HashMap<>();

  /**
   * @param mapper 租户绑定的数据访问器
   * @param tenantId 可信租户 UUID
   * @param lock 写入事务内为 true，按固定表和编码顺序锁定现有节点，防止并发移动形成环
   */
  TenantGeography(GeographyMapper mapper,UUID tenantId,boolean lock) {
    for(var node:GeographyHierarchy.children("",1,100).rows())items.put(node.code(),new Item(node.code(),node.name(),"",false));
    for(boolean subdivision:List.of(false,true))for(var entry:GeographyStandards.entries(subdivision)){
      originals.put(entry.code(),entry.name());
      String parent=entry.attributes().getOrDefault("parentCode","");
      if(parent.isEmpty())parent=subdivision?entry.attributes().get("countryCode"):"continent:"+GeographyHierarchy.parentHierarchyCode(entry.code());
      items.put(entry.code(),new Item(entry.code(),GeographyHierarchy.displayName(entry.code(),entry.name()),parent,!subdivision));
    }
    for(boolean country:List.of(true,false)){
      for(var row:mapper.nodes(tenantId.toString(),country,lock)){
        String code=(String)row.get("code"),parent=(String)row.get("parent_code");
        if(parent.isEmpty())parent=items.containsKey(code)?items.get(code).parent():country?"":(String)row.get("country_code");
        String name=(String)row.get("name");if(name.equals(originals.get(code)))name=GeographyHierarchy.displayName(code,name);
        items.put(code,new Item(code,name,parent,country));
      }
    }
  }

  /**
   * 校验候选父级并加入当前事务视图；缺省父级仅允许从已存在的标准或租户节点继承。
   * @param kind 当前地理对象类型
   * @param code 不可变的本级编码，最长64字符
   * @param name 本级业务名称
   * @param values 可写白名单字段；所属国别由真实父链补齐，不能由客户端伪造
   * @throws Rejection 父级不存在、类型错误、同码跨类型或自指／循环时拒绝
   */
  void put(Kind kind,String code,String name,Map<String,String> values){
    boolean country=kind==Kind.COUNTRY_REGION;var previous=items.get(code);
    // MySQL 编码唯一索引不区分大小写，图内也禁止通过大小写变体创建同码节点。
    if(items.keySet().stream().anyMatch(key->!key.equals(code)&&key.equalsIgnoreCase(code)))throw invalid();
    if(previous!=null&&(code.startsWith("continent:")||previous.country()!=country))throw invalid();
    String parent=values.getOrDefault("parentCode","");
    if(parent.isEmpty()&&previous!=null)parent=previous.parent();
    var target=items.get(parent);
    if(target==null||country!=parent.startsWith("continent:"))throw invalid();
    var seen=new HashSet<String>();seen.add(code);String cursor=parent;
    while(!cursor.isEmpty()){if(!seen.add(cursor))throw invalid();var item=items.get(cursor);if(item==null)throw invalid();cursor=item.parent();}
    items.put(code,new Item(code,name,parent,country));paths.clear();values.put("parentCode",parent);
    if(!country)values.put("countryCode",country(code));
  }

  /** 所属国家由父链确定，移动节点及后代共用此结果，避免跨国移动后遗留旧国别。 */
  String country(String code){var seen=new HashSet<String>();while(seen.add(code)){var item=items.get(code);if(item==null||code.startsWith("continent:"))throw invalid();if(item.country())return code;code=item.parent();}throw invalid();}
  /** 当前节点的真实父级，未知节点拒绝，避免把错误范围解释成全部资料。 */
  String parent(String code){var item=items.get(code);if(item==null)throw invalid();return item.parent();}
  /** 仅用于展示与查询；自定义节点使用完整编码片段，已有标准片段保持原格式。 */
  String path(String code){if(code.isEmpty())return "";return path(code,new HashSet<>());}
  private String path(String code,Set<String> seen){
    if(paths.containsKey(code))return paths.get(code);if(!seen.add(code))throw invalid();
    var item=items.get(code);if(item==null)throw invalid();String segment=code.startsWith("continent:")?code.substring(10):originals.containsKey(code)&&code.startsWith("CN-MCA-")?code.substring(7):originals.containsKey(code)&&code.contains("-")?code.substring(code.indexOf('-')+1):code;
    String value=item.parent().isEmpty()?segment:path(item.parent(),seen)+"-"+segment;paths.put(code,value);seen.remove(code);return value;
  }
  /** 名称优先展示租户维护值，标准底图继续使用既有中文显示规则。 */
  String name(String code){return items.get(code).name();}
  /** 祖先遍历而非字符串前缀匹配，避免自定义编码含短横线时误判范围。 */
  boolean within(String code,String scope){if(!items.containsKey(scope))throw invalid();var seen=new HashSet<String>();while(!code.isEmpty()&&seen.add(code)){if(code.equals(scope))return true;code=parent(code);}return false;}
  /** 原对象范围内的当前节点及真实后代编码，用于参数化 SQL 筛选。 */
  List<String> codes(String scope,boolean subdivision){return items.values().stream().filter(i->!i.code().startsWith("continent:")&&i.country()!=subdivision&&within(i.code(),scope)).map(Item::code).toList();}
  /** 关键词包含匹配租户层级编码与名称，不扩大权限或租户边界。 */
  List<String> aliases(String query,boolean subdivision){String q=query.toLowerCase(Locale.ROOT);return items.values().stream().filter(i->!i.code().startsWith("continent:")&&i.country()!=subdivision&&(path(i.code()).toLowerCase(Locale.ROOT).contains(q)||i.name().toLowerCase(Locale.ROOT).contains(q))).map(Item::code).toList();}
  /** 当前层分页；国别维护树不展开区划，区划维护树允许选择国别和区划父级。 */
  GeographyHierarchy.Page children(String parent,int page,int size,boolean countries){
    if(page<1||size<1||size>100||!parent.isEmpty()&&!items.containsKey(parent))throw invalid();
    var nodes=items.values().stream().filter(i->i.parent().equals(parent)&&(!countries||i.country()||i.code().startsWith("continent:"))).map(i->new GeographyHierarchy.Node(i.code(),i.name(),i.name(),path(i.code()),parent,path(parent),countries?i.country():items.values().stream().noneMatch(child->child.parent().equals(i.code())))).sorted(Comparator.comparing(GeographyHierarchy.Node::hierarchyCode)).toList();
    return new GeographyHierarchy.Page(parent,nodes.stream().skip((long)(page-1)*size).limit(size).toList(),nodes.size());
  }
  private static Rejection invalid(){return new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
}
