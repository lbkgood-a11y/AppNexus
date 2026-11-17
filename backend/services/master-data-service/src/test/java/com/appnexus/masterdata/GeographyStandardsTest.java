package com.appnexus.masterdata;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** 标准目录分页验收，覆盖完整遍历、搜索及非法边界。 */
class GeographyStandardsTest {
  @Test void pagesAreBoundedAndPreserveDirectoryOrder() {
    var expected=GeographyStandards.entries(true);
    var actual=new java.util.ArrayList<GeographyStandards.Entry>();
    for(int page=1;actual.size()<expected.size();page++) {
      var result=GeographyStandards.page(true,"",page,20);
      assertThat(result.total()).isEqualTo(expected.size());
      assertThat(result.rows()).hasSizeLessThanOrEqualTo(20).isNotEmpty();
      actual.addAll(result.rows());
    }
    assertThat(actual.stream().map(GeographyStandards.Entry::code)).containsExactlyElementsOf(expected.stream().map(GeographyStandards.Entry::code).toList());
    for(int index=0;index<expected.size();index++){
      assertThat(actual.get(index).attributes()).containsAllEntriesOf(expected.get(index).attributes()).containsKey("hierarchyCode");
    }
    assertThat(GeographyStandards.page(true,"",Integer.MAX_VALUE,20).rows()).isEmpty();
  }
  @Test void searchFiltersBeforePagination() {
    var result=GeographyStandards.page(true,"cn-mca-",1,20);
    assertThat(result.total()).isGreaterThan(20);
    assertThat(result.rows()).allMatch(e->e.code().startsWith("CN-MCA-"));
    assertThat(GeographyStandards.page(true,"不存在的行政区域",1,20).total()).isZero();
    assertThat(GeographyStandards.page(false,"china",1,20).rows()).anyMatch(e->e.code().equals("CN"));
  }
  @Test void searchesLocalizedNamesWithoutRewritingOriginal(){
    var result=GeographyStandards.page(true,"福建",1,20);
    assertThat(result.rows()).anySatisfy(e->{assertThat(e.code()).isEqualTo("CN-FJ");assertThat(e.name()).isEqualTo(GeographyStandards.entries(true).stream().filter(item->item.code().equals("CN-FJ")).findFirst().orElseThrow().name());assertThat(e.attributes()).containsEntry("displayName","福建省");});
    assertThat(GeographyStandards.page(false,"中国",1,20).rows()).anyMatch(e->e.code().equals("CN"));
  }
  @Test void rejectsInvalidPagination() {
    assertThatThrownBy(()->GeographyStandards.page(true,"",0,20)).isInstanceOf(MasterData.Rejection.class);
    assertThatThrownBy(()->GeographyStandards.page(true,"",1,101)).isInstanceOf(MasterData.Rejection.class);
    assertThatThrownBy(()->GeographyStandards.page(true,"",1,0)).isInstanceOf(MasterData.Rejection.class);
  }
}
