package com.appnexus.platform.bootstrap.task;
import com.appnexus.platform.tenant.*;
import com.appnexus.platform.tenant.port.TaskOperations;
import org.springframework.context.annotation.*;
/** 平台任务公开用例装配，不承担任务状态决策。 */
@Configuration(proxyBeanMethods=false)
public class TaskManagementConfiguration {
 /** @param platform 平台管理资格公开端口 @param operations 可靠任务技术输出端口 @return 纯应用用例 */
 @Bean public TaskManagement taskManagement(PlatformAdministration platform,TaskOperations operations){return TaskManagement.create(platform,operations);}
}
