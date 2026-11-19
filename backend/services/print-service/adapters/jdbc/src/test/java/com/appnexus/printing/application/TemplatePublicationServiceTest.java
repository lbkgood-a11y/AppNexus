package com.appnexus.printing.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.appnexus.contracts.printing.PrintContext;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 模板发布顺序测试，冲突不可生成历史版本，无权限不可探测模板。 */
class TemplatePublicationServiceTest {
  private final PrintContext context=new PrintContext(UUID.randomUUID().toString(),UUID.randomUUID().toString(),UUID.randomUUID().toString());
  private final String id=UUID.randomUUID().toString();
  /** 权限失败不读取模板；并发版本失败不读取契约、不发布。 */
  @Test void rejectsBeforePublishingOrReadingUnauthorizedTemplate(){
    var port=mock(TemplatePublicationPort.class);
    assertThatThrownBy(()->new TemplatePublicationService(port,(c,p)->false).publish(context,id,1)).hasMessage("PRINT_ACCESS_DENIED");
    verifyNoInteractions(port);
    when(port.lock(context,id)).thenReturn(new TemplatePublicationPort.Draft(2,3,"test.v1"));
    assertThatThrownBy(()->new TemplatePublicationService(port,(c,p)->true).publish(context,id,1)).hasMessage("PRINT_TEMPLATE_CONFLICT");
    verify(port,never()).publish(any(),anyString(),anyInt(),any());
    verify(port,never()).validatedSchema(any(),anyString(),anyString());
  }
  /** 发布版本只由锁定快照推进，契约校验在追加历史前执行。 */
  @Test void validatesSchemaBeforeAdvancingLockedVersion(){
    var port=mock(TemplatePublicationPort.class);
    when(port.lock(context,id)).thenReturn(new TemplatePublicationPort.Draft(2,3,"test.v1"));
    when(port.validatedSchema(context,id,"test.v1")).thenReturn("{}");
    when(port.result(context,id)).thenReturn(Map.of("published_version",4));
    assertThat(new TemplatePublicationService(port,(c,p)->true).publish(context,id,2)).containsEntry("published_version",4);
    var order=inOrder(port);order.verify(port).lock(context,id);order.verify(port).validatedSchema(context,id,"test.v1");order.verify(port).publish(context,id,4,"{}");
  }
}
