package com.appnexus.masterdata;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.appnexus.masterdata.MasterData.*;

/** 租户图回归：自定义码、祖先关系、类型边界以及跨国移动不依赖字符串前缀。 */
class TenantGeographyTest {
  @Test void customNodesMoveWithDescendantsAndRejectCycles(){
    var graph=new TenantGeography(mock(JdbcTemplate.class),UUID.randomUUID(),false);
    var values=new HashMap<>(Map.of("parentCode","CN-FJ"));
    graph.put(Kind.SUBDIVISION,"CUSTOM.CITY","自定义城市",values);
    assertThat(values).containsEntry("countryCode","CN");
    graph.put(Kind.SUBDIVISION,"CUSTOM.COUNTY","自定义县",new HashMap<>(Map.of("parentCode","CUSTOM.CITY")));
    assertThat(graph.codes("CN-FJ",true)).contains("CUSTOM.CITY","CUSTOM.COUNTY");
    assertThatThrownBy(()->graph.put(Kind.SUBDIVISION,"CUSTOM.CITY","自定义城市",new HashMap<>(Map.of("parentCode","CUSTOM.COUNTY")))) .isInstanceOf(Rejection.class);
    graph.put(Kind.SUBDIVISION,"CUSTOM.CITY","自定义城市",new HashMap<>(Map.of("parentCode","US")));
    assertThat(graph.country("CUSTOM.COUNTY")).isEqualTo("US");
    assertThat(graph.codes("CN-FJ",true)).doesNotContain("CUSTOM.CITY","CUSTOM.COUNTY");
    assertThat(graph.codes("US",true)).contains("CUSTOM.CITY","CUSTOM.COUNTY");
    assertThat(graph.path("CUSTOM.COUNTY")).isEqualTo("NA-US-CUSTOM.CITY-CUSTOM.COUNTY");
  }
  @Test void countryAndParentBoundariesAreExplicit(){
    var graph=new TenantGeography(mock(JdbcTemplate.class),UUID.randomUUID(),false);
    graph.put(Kind.COUNTRY_REGION,"CUSTOM","自定义国别",new HashMap<>(Map.of("parentCode","continent:AS")));
    graph.put(Kind.SUBDIVISION,"LOCAL","本地区划",new HashMap<>(Map.of("parentCode","CUSTOM")));
    assertThat(graph.country("LOCAL")).isEqualTo("CUSTOM");
    assertThat(graph.children("CUSTOM",1,50,false).rows()).extracting(GeographyHierarchy.Node::code).containsExactly("LOCAL");
    for(String parent:List.of("missing","LOCAL","CUSTOM"))assertThatThrownBy(()->graph.put(Kind.COUNTRY_REGION,"NEW","新国别",new HashMap<>(Map.of("parentCode",parent)))).isInstanceOf(Rejection.class);
    assertThatThrownBy(()->graph.put(Kind.SUBDIVISION,"NEW","新区划",new HashMap<>(Map.of("parentCode","continent:AS")))).isInstanceOf(Rejection.class);
    assertThatThrownBy(()->graph.put(Kind.SUBDIVISION,"CN","同码",new HashMap<>(Map.of("parentCode","US")))).isInstanceOf(Rejection.class);
    assertThatThrownBy(()->graph.codes("missing",true)).isInstanceOf(Rejection.class);
  }
}
