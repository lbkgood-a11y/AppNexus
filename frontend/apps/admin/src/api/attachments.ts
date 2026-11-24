import type { RecordAudit } from "@appnexus/ui";
import { currentTenant } from "@/utils/tenantContext";
import { http } from "@/utils/http";

/** 附件资料响应。仅平台台账返回存储路径。 */
export interface AttachmentItem {
  id: string;
  tenantId: string;
  businessId: string;
  fileName: string;
  sizeBytes: number;
  mediaType: string;
  storagePath?: string;
  status: string;
  createdBy: string;
  createdAt: string;
  updatedBy?: string;
  updatedAt: string;
}

/** 上传规则的限额按字节传输，保存使用上次读取的版本。 */
export interface AttachmentRule {
  typeKey: string;
  enabled: boolean;
  maxBytes: number;
  hardLimitBytes: number;
  version: number;
  extensions: string[];
  audit?: RecordAudit | null;
}

/** 工作台服务端筛选，时间以 ISO 8601 传输。 */
export interface AttachmentFilter {
  businessId?: string;
  tenantId?: string;
  fileName?: string;
  typeKey?: string;
  createdBy?: string;
  from?: string;
  until?: string;
}

interface AttachmentPage {
  rows: AttachmentItem[];
  total: number;
}

const path = "/api/configuration/attachments";
const policyPath = "/bff/platform/attachment-policies";
const csrf = () => http.request<{ token: string; headerName: string }>("get", "/bff/csrf");

/** 复用当前会话、租户版本及 CSRF。上传失败后只由调用方以同一幂等键重试。 */
export const attachmentsApi = {
  rules: () => http.request<AttachmentRule[]>("get", path + "/rules"),
  list: (businessId: string, signal: AbortSignal, offset: number, limit: number) =>
    http.request<AttachmentPage>("get", path, { params: { businessId, offset, limit }, signal }),
  async upload(businessId: string, file: File, idempotencyKey: string, progress: (percent: number) => void) {
    const tenant = currentTenant.value;
    const token = await csrf();
    if (!tenant || tenant !== currentTenant.value) throw new Error("Tenant context changed");
    return http.request<AttachmentItem>("post", path, {
      data: file,
      timeout: 90000,
      headers: {
        "Content-Type": "application/octet-stream",
        "X-Attachment-Business-Id": encodeURIComponent(businessId),
        "X-Attachment-Name": encodeURIComponent(file.name),
        "Idempotency-Key": idempotencyKey,
        [token.headerName]: token.token
      },
      onUploadProgress: event => {
        if (event.total) progress(Math.min(99, Math.round(event.loaded / event.total * 100)));
      }
    });
  },
  ledger: (params: AttachmentFilter & { offset: number; limit: number }, signal: AbortSignal) =>
    http.request<AttachmentPage>("get", "/bff/platform/attachments", { params, signal }),
  policies: () => http.request<AttachmentRule[]>("get", policyPath),
  async savePolicy(rule: AttachmentRule) {
    const token = await csrf();
    return http.request<AttachmentRule>("put", policyPath + "/" + encodeURIComponent(rule.typeKey), {
      data: { enabled: rule.enabled, maxBytes: rule.maxBytes, version: rule.version },
      headers: { [token.headerName]: token.token }
    });
  }
};
