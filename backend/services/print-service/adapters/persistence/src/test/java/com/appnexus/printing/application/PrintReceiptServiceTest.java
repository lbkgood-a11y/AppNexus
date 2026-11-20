package com.appnexus.printing.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.appnexus.contracts.printing.PrintContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 回执应用用例反向验证，确保先授权、同代理幂等及非法转换不写入。 */
class PrintReceiptServiceTest {
  private final PrintContext context=new PrintContext(UUID.randomUUID().toString(),UUID.randomUUID().toString(),UUID.randomUUID().toString());
  private final String id=UUID.randomUUID().toString();
  /** 拒绝主体不得读取或编解码结果，也不得查询任务存在性。 */
  @Test void deniedIdentityDoesNotReadOrDecode() {
    var store=mock(PrintReceiptStore.class);
    var service=new PrintReceiptService(store,(c,p)->false);
    assertThatThrownBy(()->service.accept(context,id,"SUBMITTED","agent",()->{throw new AssertionError("不能读取无权请求正文");}))
        .hasMessage("PRINT_ACCESS_DENIED");
    verifyNoInteractions(store);
  }
  /** 相同结果重放不更新，换代理及已提交后声称安全失败都拒绝。 */
  @Test void replayRequiresSameAgentAndIllegalTransitionsNeverWrite() {
    var store=mock(PrintReceiptStore.class);
    var service=new PrintReceiptService(store,(c,p)->true);
    when(store.lock(context.tenantId(),id,"{}")).thenReturn(new PrintReceiptStore.Current("SUBMITTED","agent",false,true));
    service.accept(context,id,"SUBMITTED","agent",()->"{}");
    assertThatThrownBy(()->service.accept(context,id,"SUBMITTED","other",()->"{}")).hasMessage("PRINT_JOB_RECEIPT_CONFLICT");
    assertThatThrownBy(()->service.accept(context,id,"FAILED","agent",()->"{}")).hasMessage("PRINT_JOB_RECEIPT_CONFLICT");
    verify(store,never()).update(anyString(),anyString(),anyString(),anyString());
  }
}
