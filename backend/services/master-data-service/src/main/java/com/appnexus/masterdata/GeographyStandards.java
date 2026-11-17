package com.appnexus.masterdata;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** ISO 与民政部地理编码快照；只从构建资源加载，运行时不依赖外部服务，国内代码不冒充 ISO 编码。 */
public final class GeographyStandards {
  private GeographyStandards() {}
  /** 标准选项；attributes 为由标准编码确定的只读属性，名称允许租户本地化。 */
  public record Entry(String code, String name, Map<String,String> attributes) {}
  /** 分页目录；只返回当前页与筛选后的真实总数。 */
  public record Page(List<Entry> rows,long total) {}
  /**
   * 服务端按标准码、层级码和名称检索并分页，沿用标准编码排序。
   * @param subdivision true 为行政区划，false 为国别
   * @param keyword 编码或名称子串，空串或 NULL 表示不筛选，不区分大小写
   * @param page 页码，从 1 开始，接口默认 1
   * @param size 每页条数，范围 1–100，接口默认 20
   * @return 当前页及匹配总数
   * @throws MasterData.Rejection 非法分页参数返回 400
   */
  public static Page page(boolean subdivision,String keyword,int page,int size) {
    if(page<1||size<1||size>100)throw new MasterData.Rejection(400,"MASTER_DATA_INPUT_INVALID");
    String query=keyword==null?"":keyword.trim().toLowerCase(Locale.ROOT);
    var filtered=entries(subdivision).stream().filter(e->e.code().toLowerCase(Locale.ROOT).contains(query)||e.name().toLowerCase(Locale.ROOT).contains(query)||GeographyHierarchy.hierarchyCode(e.code()).toLowerCase(Locale.ROOT).contains(query)||GeographyHierarchy.displayName(e.code(),e.name()).toLowerCase(Locale.ROOT).contains(query)).toList();
    return new Page(filtered.stream().skip((long)(page-1)*size).limit(size).map(e->{
      var attributes=new LinkedHashMap<>(e.attributes());attributes.put("hierarchyCode",GeographyHierarchy.hierarchyCode(e.code()));attributes.put("parentHierarchyCode",GeographyHierarchy.parentHierarchyCode(e.code()));attributes.put("displayName",GeographyHierarchy.displayName(e.code(),e.name()));
      return new Entry(e.code(),e.name(),Map.copyOf(attributes));
    }).toList(),filtered.size());
  }
  private static final Map<String,Entry> COUNTRIES=load(false);
  private static final Map<String,Entry> SUBDIVISIONS=load(true);

  /**
   * @param subdivision true 为 ISO 3166-2 与民政部中国地县级目录，false 为 ISO 3166-1
   * @return 按标准代码排序的不可变快照；不代表已启用的租户业务目录
   */
  public static List<Entry> entries(boolean subdivision) { return List.copyOf((subdivision?SUBDIVISIONS:COUNTRIES).values()); }

  /**
   * 标准属性由服务端补齐；非空冲突值拒绝，避免仅格式正确却串用其他国别的编码。
   * @param kind 固定主数据类型，仅国别和行政区划适用
   * @param code 标准编码，区分大小写，不接受保留码或自定义码
   * @param values 已做白名单校验的属性；缺省标准属性在原映射内补齐
   * @throws MasterData.Rejection 未收录编码或不匹配属性返回 400
   */
  public static void apply(MasterData.Kind kind,String code,Map<String,String> values) {
    var entry=(kind==MasterData.Kind.COUNTRY_REGION?COUNTRIES:SUBDIVISIONS).get(code);
    if(entry==null)throw new MasterData.Rejection(400,"MASTER_DATA_INPUT_INVALID");
    entry.attributes().forEach((key,value)->{
      String supplied=values.getOrDefault(key,"");
      if(!supplied.isEmpty()&&!supplied.equals(value))throw new MasterData.Rejection(400,"MASTER_DATA_INPUT_INVALID");
      values.put(key,value);
    });
  }

  /** 快照缺失或格式错误时启动失败，不能静默降级为任意编码。 */
  private static Map<String,Entry> load(boolean subdivision) {
    String resource="/geography/iso3166-"+(subdivision?"2":"1")+".tsv";
    var result=new TreeMap<String,Entry>();
    try(var stream=GeographyStandards.class.getResourceAsStream(resource)) {
      if(stream==null)throw new IllegalStateException("GEOGRAPHY_SNAPSHOT_MISSING");
      try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
        for(String line;(line=reader.readLine())!=null;) {
          String[] parts=line.split("\t",-1);
          Map<String,String> attributes=subdivision?Map.of("countryCode",parts[0].substring(0,2),"subdivisionType",parts[2],"parentCode",parts[3]):Map.of("alpha3Code",parts[1],"numericCode",parts[2]);
          result.put(parts[0],new Entry(parts[0],subdivision?parts[1]:parts[3],attributes));
        }
      }
    } catch(IOException e) { throw new IllegalStateException("GEOGRAPHY_SNAPSHOT_UNREADABLE",e); }
    if(subdivision)loadChina(result);
    return Collections.unmodifiableMap(result);
  }

  /** 国内编码使用 CN-MCA 命名空间，父级直接引用既有 ISO 省级节点，不创建重复省份。 */
  private static void loadChina(Map<String,Entry> result) {
    try(var stream=GeographyStandards.class.getResourceAsStream("/geography/cn-mca-2025.tsv")) {
      if(stream==null)throw new IllegalStateException("CHINA_GEOGRAPHY_SNAPSHOT_MISSING");
      try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
        for(String line;(line=reader.readLine())!=null;) {
          var p=line.split("\t",-1);
          if(p.length!=6||!p[0].equals("CN-MCA-"+p[2])||!p[2].matches("[0-9]{6}")||!Set.of("2","3").contains(p[5]))
            throw new IllegalStateException("CHINA_GEOGRAPHY_SNAPSHOT_INVALID");
          var entry=new Entry(p[0],p[1],Map.of("countryCode","CN","subdivisionType",p[3],"parentCode",p[4]));
          if(result.putIfAbsent(p[0],entry)!=null)throw new IllegalStateException("CHINA_GEOGRAPHY_DUPLICATE");
        }
      }
      for(var entry:result.values())if(entry.code().startsWith("CN-MCA-")) {
        var visited=new HashSet<String>();var current=entry;
        while(current!=null&&!current.attributes().getOrDefault("parentCode","").isEmpty()) {
          if(!visited.add(current.code()))throw new IllegalStateException("CHINA_GEOGRAPHY_CYCLE");
          current=result.get(current.attributes().get("parentCode"));
          if(current==null||!"CN".equals(current.attributes().get("countryCode")))throw new IllegalStateException("CHINA_GEOGRAPHY_PARENT_INVALID");
        }
      }
    } catch(IOException e) {throw new IllegalStateException("CHINA_GEOGRAPHY_SNAPSHOT_UNREADABLE",e);}
  }
}
