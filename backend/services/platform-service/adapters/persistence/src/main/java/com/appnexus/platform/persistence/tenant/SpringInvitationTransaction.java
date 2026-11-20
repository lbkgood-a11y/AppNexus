package com.appnexus.platform.persistence.tenant;
import com.appnexus.platform.tenant.port.InvitationTransaction;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
/** 邀请同数据源REQUIRED事务，不独立提交成员或审计。 */
@Component
public final class SpringInvitationTransaction implements InvitationTransaction {
 private final TransactionTemplate tx;
 /** @param manager 原宿主事务管理器 */
 public SpringInvitationTransaction(PlatformTransactionManager manager){tx=new TransactionTemplate(manager);}
 public <T>T required(Supplier<T> work){return tx.execute(status->work.get());}
}
