package com.appnexus.masterdata;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 地理导航与层级编码；基于固定洲归属和标准父级生成，不改写历史标准编码。 */
public final class GeographyHierarchy {
  private GeographyHierarchy() {}
  private static final Map<String,String> CONTINENTS=Map.of("continent:AS","亚洲（Asia）","continent:EU","欧洲（Europe）","continent:AF","非洲（Africa）","continent:NA","北美洲（North America）","continent:SA","南美洲（South America）","continent:OC","大洋洲（Oceania）","continent:AN","南极洲（Antarctica）");
  private static final Map<String,String> COUNTRIES=loadContinents();
  private static final Map<String,GeographyStandards.Entry> ENTRIES=entries();
  /** 构建只读直接子级索引，避免懒加载一层时反复扫描八千余个节点。 */
  private static final Map<String,List<GeographyStandards.Entry>> CHILDREN=childrenIndex();
  /** 中文快照按标准编码匹配，不能按同名地区猜测归属。 */
  private static final Map<String,String> CHINESE_NAMES=loadChineseNames();
  /** 导航节点；name 为中文（原名），originalName 保留来源，父级键及层级码显式返回，根上级为空。 */
  public record Node(String code,String name,String originalName,String hierarchyCode,String parentCode,String parentHierarchyCode,boolean leaf) {}
  /** 当前层分页；total 为此父级直接子节点数，不拆散节点的父子关系。 */
  public record Page(String parentCode,List<Node> rows,long total) {}
  /**
   * 获取当前父级的直接子节点，不预加载后代。
   * @param parent 父节点标准键；空串表示七洲根节点
   * @param page 页码，从 1 开始
   * @param size 每页条数，范围 1–100，默认 20
   * @return 按层级编码排序的当前层分页
   * @throws MasterData.Rejection 无效节点或分页参数返回 400
   */
  public static Page children(String parent,int page,int size) {
    if(parent==null||page<1||size<1||size>100)throw invalid();
    List<Node> nodes;
    if(parent.isEmpty())nodes=CONTINENTS.entrySet().stream().map(e->new Node(e.getKey(),e.getValue(),e.getValue().substring(e.getValue().indexOf("（")+1,e.getValue().length()-1),e.getKey().substring(10),"","",false)).toList();
    else {
      if(!CONTINENTS.containsKey(parent)&&!ENTRIES.containsKey(parent))throw invalid();
      nodes=CHILDREN.getOrDefault(parent,List.of()).stream().map(e->new Node(e.code(),displayName(e.code(),e.name()),e.name(),hierarchyCode(e.code()),parent(e.code()),parentHierarchyCode(e.code()),!CHILDREN.containsKey(e.code()))).toList();
    }
    var sorted=nodes.stream().sorted(Comparator.comparing(Node::hierarchyCode)).toList();
    return new Page(parent,sorted.stream().skip((long)(page-1)*size).limit(size).toList(),sorted.size());
  }
  /**
   * 层级编码由祖先标准片段组成，省市县不按名称或列表顺序另编序号。
   * @param code 标准国家或区划编码；未知历史码保留原值
   * @return 如 AS-CN-FJ-350100-350102；仅用于层级展示和查询
   */
  public static String hierarchyCode(String code) {
    if(CONTINENTS.containsKey(code))return code.substring(10);
    var entry=ENTRIES.get(code);if(entry==null)return code;
    var path=new ArrayDeque<String>();var seen=new HashSet<String>();String current=code;
    while(ENTRIES.containsKey(current)) {
      if(!seen.add(current))throw new IllegalStateException("GEOGRAPHY_HIERARCHY_CYCLE");
      String segment=current.startsWith("CN-MCA-")?current.substring(7):current.contains("-")?current.substring(current.indexOf('-')+1):current;
      path.addFirst(segment);current=parent(current);
    }
    path.addFirst(current.startsWith("continent:")?current.substring(10):current);return String.join("-",path);
  }
  /**
   * 由真实父节点解析上级层级码，不能简单截断可能含分隔符的标准码。
   * @param code 标准节点键；根节点和未知历史码返回空
   * @return 国家为所属洲，如 AS；福建为 AS-CN
   */
  public static String parentHierarchyCode(String code){return ENTRIES.containsKey(code)?hierarchyCode(parent(code)):"";}
  /**
   * @param code 标准编码，中文名称按此唯一匹配
   * @param original 未收录历史节点的名称；已收录节点始终使用标准目录原文，避免重复拼接
   * @return 中国及所属区划仅中文；其他节点为中文（标准原文），缺译时保留原文
   */
  public static String displayName(String code,String original){
    var entry=ENTRIES.get(code);
    String source=entry==null?original:entry.name();
    String chinese=CHINESE_NAMES.get(code);
    // 中国归属来自标准 countryCode，不能因显示语言或名称相似误判；港澳台 ISO 地区不擅自改写目录关系。
    boolean china=code.equals("CN")||entry!=null&&"CN".equals(entry.attributes().get("countryCode"));
    if(china)return chinese==null?source:chinese;
    return chinese==null||chinese.equals(source)?source:chinese+"（"+source+"）";
  }
  /** 中文快照损坏即失败，不能静默用错位行或名称推断替代。 */
  private static Map<String,String> loadChineseNames(){
    var result=new HashMap<String,String>();
    try(var stream=GeographyHierarchy.class.getResourceAsStream("/geography/names-zh.tsv")){
      if(stream==null)throw new IllegalStateException("GEOGRAPHY_CHINESE_NAMES_MISSING");
      try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){for(String line;(line=reader.readLine())!=null;){var parts=line.split("\t",-1);if(parts.length!=2||parts[1].isBlank()||!ENTRIES.containsKey(parts[0])||result.putIfAbsent(parts[0],parts[1])!=null)throw new IllegalStateException("GEOGRAPHY_CHINESE_NAMES_INVALID");}}
    }catch(IOException cause){throw new IllegalStateException("GEOGRAPHY_CHINESE_NAMES_UNREADABLE",cause);}
    return Map.copyOf(result);
  }
  /**
   * 将导航范围解析为标准编码集合，查询端仍必须叠加租户和对象授权。
   * @param scope 洲、国家或区划标准键；空串不限制
   * @param subdivision true 返回区划，false 返回国家
   * @return 包含当前节点及真实后代的编码；无匹配返回空集合
   * @throws MasterData.Rejection 未知范围返回 400
   */
  public static List<String> codes(String scope,boolean subdivision) {
    if(scope==null||scope.isEmpty())return List.of();
    if(!CONTINENTS.containsKey(scope)&&!ENTRIES.containsKey(scope))throw invalid();
    String prefix=hierarchyCode(scope);
    return GeographyStandards.entries(subdivision).stream().filter(e->{String path=hierarchyCode(e.code());return path.equals(prefix)||path.startsWith(prefix+"-");}).map(GeographyStandards.Entry::code).toList();
  }
  /** 标准上级缺省时挂所属国家；国家挂所属洲，不虚构中间层级。 */
  private static String parent(String code) {
    if(COUNTRIES.containsKey(code))return COUNTRIES.get(code);
    var entry=ENTRIES.get(code);return entry.attributes().getOrDefault("parentCode","").isEmpty()?entry.attributes().get("countryCode"):entry.attributes().get("parentCode");
  }
  /** 索引只保存构建快照关系，不含租户数据或授权结果。 */
  private static Map<String,List<GeographyStandards.Entry>> childrenIndex(){
    var index=new HashMap<String,List<GeographyStandards.Entry>>();
    for(var entry:ENTRIES.values())index.computeIfAbsent(parent(entry.code()),key->new ArrayList<>()).add(entry);
    index.replaceAll((key,value)->List.copyOf(value));return Map.copyOf(index);
  }
  private static Map<String,GeographyStandards.Entry> entries(){var map=new TreeMap<String,GeographyStandards.Entry>();GeographyStandards.entries(false).forEach(e->map.put(e.code(),e));GeographyStandards.entries(true).forEach(e->map.put(e.code(),e));return Collections.unmodifiableMap(map);}
  /** 固定快照必须完整覆盖 249 个国别，缺失时启动失败，不通过名称猜测洲归属。 */
  private static Map<String,String> loadContinents(){
    var map=new HashMap<String,String>();
    try(var stream=GeographyHierarchy.class.getResourceAsStream("/geography/country-continents.tsv")){
      if(stream==null)throw new IllegalStateException("GEOGRAPHY_CONTINENTS_MISSING");
      try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){for(String line;(line=reader.readLine())!=null;){var parts=line.split("\t");if(parts.length!=2||!CONTINENTS.containsKey("continent:"+parts[1])||map.putIfAbsent(parts[0],"continent:"+parts[1])!=null)throw new IllegalStateException("GEOGRAPHY_CONTINENTS_INVALID");}}
    }catch(IOException e){throw new IllegalStateException("GEOGRAPHY_CONTINENTS_UNREADABLE",e);}
    if(!map.keySet().equals(new HashSet<>(GeographyStandards.entries(false).stream().map(GeographyStandards.Entry::code).toList())))throw new IllegalStateException("GEOGRAPHY_CONTINENTS_INCOMPLETE");
    return Map.copyOf(map);
  }
  private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"MASTER_DATA_INPUT_INVALID");}
}
