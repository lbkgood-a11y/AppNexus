package com.appnexus.masterdata.organization.port.personnel;
import java.util.function.Supplier;
/** 人员完整用例事务输出端口，仅允许平台持久化适配器实现。 */
public interface PersonnelTransaction {
 /** REQUIRED同连接事务；嵌套加入当前事务，异常整笔回滚。 @param work 业务、明细、字典引用与审计工作 @param <T> 结果类型 @return 事务成功结果 */
 <T>T required(Supplier<T> work);
 /** REQUIRED无返回事务；异常整笔回滚，不独立提交明细。 @param work 完整业务工作 */
 void run(Runnable work);
 /** 中立唯一性冲突，技术适配器保留原异常作为原因，不回传数据库细节。 */
 final class DuplicateRecord extends RuntimeException {
  /** @param cause 数据库唯一约束冲突，仅供服务端追溯 */
  public DuplicateRecord(Throwable cause){super("PERSON_RECORD_DUPLICATE",cause);}
 }
}
