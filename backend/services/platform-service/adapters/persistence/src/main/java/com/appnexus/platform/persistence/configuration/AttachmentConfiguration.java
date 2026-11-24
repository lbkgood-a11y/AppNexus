package com.appnexus.platform.persistence.configuration;

import com.appnexus.platform.configuration.AttachmentPolicies;
import com.appnexus.platform.configuration.AttachmentRecords;
import com.appnexus.platform.configuration.port.AttachmentBlobStorage;
import com.appnexus.platform.configuration.port.AttachmentInspection;
import com.appnexus.platform.configuration.port.AttachmentStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 将附件专用用例装配至当前数据库、内容检查与部署对象存储。 */
@Configuration(proxyBeanMethods = false)
class AttachmentConfiguration {
    /** @return 可供入站 HTTP 适配使用的附件业务用例 */
    @Bean
    AttachmentRecords attachmentRecords(AttachmentStore records, AttachmentPolicies policies,
                                        AttachmentBlobStorage storage, AttachmentInspection inspection) {
        return AttachmentRecords.assemble(records, policies, storage, inspection);
    }
}
