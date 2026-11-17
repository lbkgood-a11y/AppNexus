package com.appnexus.masterdata;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 层级目录覆盖、分页完整性与标准编码保持不变的回归测试。 */
class GeographyHierarchyTest {
  @Test void derivesCodesFromRealAncestorsWithoutChangingStandards(){
    assertThat(GeographyHierarchy.hierarchyCode("CN")).isEqualTo("AS-CN");
    assertThat(GeographyHierarchy.hierarchyCode("CN-FJ")).isEqualTo("AS-CN-FJ");
    assertThat(GeographyHierarchy.hierarchyCode("CN-MCA-350102")).isEqualTo("AS-CN-FJ-350100-350102");
    assertThat(GeographyHierarchy.hierarchyCode("US-CA")).isEqualTo("NA-US-CA");
    assertThat(GeographyHierarchy.hierarchyCode("AS")).isEqualTo("OC-AS");
    assertThat(GeographyHierarchy.hierarchyCode("AF")).isEqualTo("AS-AF");
    assertThat(GeographyHierarchy.hierarchyCode("legacy-code")).isEqualTo("legacy-code");
    assertThat(GeographyStandards.entries(true)).anyMatch(e->e.code().equals("CN-FJ"));
  }
  @Test void exposesParentHierarchyAndChineseOriginalNames(){
    assertThat(GeographyHierarchy.parentHierarchyCode("continent:AS")).isEmpty();
    assertThat(GeographyHierarchy.parentHierarchyCode("CN")).isEqualTo("AS");
    assertThat(GeographyHierarchy.parentHierarchyCode("CN-FJ")).isEqualTo("AS-CN");
    assertThat(GeographyHierarchy.parentHierarchyCode("CN-MCA-350100")).isEqualTo("AS-CN-FJ");
    var countries=GeographyHierarchy.children("continent:AS",1,100);
    assertThat(countries.parentCode()).isEqualTo("continent:AS");
    var china=countries.rows().stream().filter(n->n.code().equals("CN")).findFirst().orElseThrow();
    assertThat(china.name()).isEqualTo("中国");
    assertThat(china.originalName()).isEqualTo("China");
    assertThat(china.parentCode()).isEqualTo("continent:AS");
    assertThat(china.parentHierarchyCode()).isEqualTo("AS");
    assertThat(GeographyHierarchy.displayName("CN-MCA-350100","福州市")).isEqualTo("福州市");
    assertThat(GeographyHierarchy.displayName("US-CA","California")).isEqualTo("加利福尼亚州（California）");
  }
  @Test void allChinaNodesUseOnlyChineseAndForeignNamesKeepOriginal(){
    assertThat(GeographyHierarchy.displayName("CN","China")).isEqualTo("中国");
    for(var entry:GeographyStandards.entries(true))if("CN".equals(entry.attributes().get("countryCode"))){
      String display=GeographyHierarchy.displayName(entry.code(),entry.name());
      assertThat(display).as(entry.code()).containsPattern("[\\p{IsHan}]").doesNotContain("（");
    }
    assertThat(GeographyHierarchy.displayName("CN-FJ","Fujian")).isEqualTo("福建省");
    assertThat(GeographyHierarchy.displayName("US-CA","加利福尼亚州（California）")).isEqualTo("加利福尼亚州（California）");
    assertThat(GeographyHierarchy.displayName("US","United States")).isEqualTo("美国（United States）");
  }
  @Test void paginatedTraversalCoversEveryStandardExactlyOnce(){
    var queue=new ArrayDeque<String>();queue.add("");var seen=new HashSet<String>();var paths=new HashSet<String>();
    while(!queue.isEmpty()){
      String parent=queue.remove();long loaded=0;
      for(int page=1;;page++){
        var result=GeographyHierarchy.children(parent,page,20);
        assertThat(result.rows()).hasSizeLessThanOrEqualTo(20);
        for(var node:result.rows()){
          assertThat(node.parentCode()).isEqualTo(parent);
          assertThat(node.parentHierarchyCode()).isEqualTo(parent.isEmpty()?"":GeographyHierarchy.hierarchyCode(parent));
          assertThat(seen.add(node.code())).as("unique node %s",node.code()).isTrue();
          assertThat(paths.add(node.hierarchyCode())).as("unique hierarchy %s",node.hierarchyCode()).isTrue();
          if(!node.leaf())queue.add(node.code());
        }
        loaded+=result.rows().size();if(loaded>=result.total())break;
        assertThat(result.rows()).isNotEmpty();
      }
    }
    assertThat(seen).hasSize(7+GeographyStandards.entries(false).size()+GeographyStandards.entries(true).size());
  }
  @Test void scopesUseRealDescendantsAndDistinguishContinentsFromCountries(){
    assertThat(GeographyHierarchy.codes("CN-FJ",true)).contains("CN-FJ","CN-MCA-350100","CN-MCA-350102").doesNotContain("CN-BJ","US-CA");
    assertThat(GeographyHierarchy.codes("continent:AS",false)).contains("CN","AF").doesNotContain("AS","US");
    assertThat(GeographyHierarchy.codes("CN-FJ",false)).isEmpty();
    assertThatThrownBy(()->GeographyHierarchy.children("invalid",1,20)).isInstanceOf(MasterData.Rejection.class);
    assertThatThrownBy(()->GeographyHierarchy.children("",0,20)).isInstanceOf(MasterData.Rejection.class);
    assertThatThrownBy(()->GeographyHierarchy.children("",1,101)).isInstanceOf(MasterData.Rejection.class);
    assertThatThrownBy(()->GeographyHierarchy.codes("invalid",true)).isInstanceOf(MasterData.Rejection.class);
  }
}
