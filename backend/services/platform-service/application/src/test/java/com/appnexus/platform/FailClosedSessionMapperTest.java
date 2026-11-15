package com.appnexus.platform;
import com.appnexus.platform.security.FailClosedSessionMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/** 会话解析只拒绝不完整结构，不伪造生命周期，也不吞掉不相关的异常。 */
class FailClosedSessionMapperTest {
 @Test void requiresAllLifecycleFactsAndPreservesCompleteSession(){
  var mapper=new FailClosedSessionMapper();var values=new HashMap<String,Object>();long now=System.currentTimeMillis();
  values.put("creationTime",now);values.put("lastAccessedTime",now);values.put("maxInactiveInterval",1800);values.put("sessionAttr:marker","original");
  assertThat(mapper.apply("complete",values).getAttribute("marker").toString()).isEqualTo("original");
  for(var key:List.of("creationTime","lastAccessedTime","maxInactiveInterval")){var partial=new HashMap<>(values);partial.remove(key);assertThat(mapper.apply("partial",partial)).isNull();}
  assertThat(mapper.apply("missing",Map.of())).isNull();
 }
}
