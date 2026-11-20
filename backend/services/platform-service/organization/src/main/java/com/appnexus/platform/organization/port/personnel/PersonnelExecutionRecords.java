package com.appnexus.platform.organization.port.personnel;
import java.util.*;
/** 人员固定存取输出端口；仅允许平台持久化适配器实现，租户、授权及完整事务由调用用例约束。 */
public interface PersonnelExecutionRecords {
 
 
 int statement1( Object p0, Object p1, Object p2);

 
 
 int statement2( Object p0, Object p1);
}
