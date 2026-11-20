package com.appnexus.printing.application;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.appnexus.contracts.printing.PrintContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
/** 模板草稿编辑应用测试；授权前不解析JSON，CAS失败不查询结果或假成功。 */
class TemplateEditingServiceTest {
  private final PrintContext context=new PrintContext(UUID.randomUUID().toString(),UUID.randomUUID().toString(),UUID.randomUUID().toString());
  /** 无设计权限不处理客户端模板内容。 */
  @Test void deniedDoesNotDecodeTemplate(){
    var port=mock(TemplateEditingPort.class);
    assertThatThrownBy(()->new TemplateEditingService(port,(c,p)->false).save(context,null,null)).hasMessage("PRINT_ACCESS_DENIED");verifyNoInteractions(port);
  }
  /** 版本冲突不能返回草稿或将内容标为保存成功。 */
  @Test void conflictDoesNotReturnDraft(){
    var port=mock(TemplateEditingPort.class);String id=UUID.randomUUID().toString();
    var draft=new TemplateEditingPort.Draft("code","名称","A4","test.v1",1);
    assertThatThrownBy(()->new TemplateEditingService(port,(c,p)->true).save(context,id,draft)).hasMessage("PRINT_TEMPLATE_CONFLICT");
    verify(port,never()).result(any(),anyString());
  }
}
