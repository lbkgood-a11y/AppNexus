import { http } from "@/utils/http";

/** 平台事件摘要，时间由服务端生成，状态数量按投递汇总。 */
export interface PlatformEvent {
  id: string;
  tenant: string;
  type: string;
  subjectReference: string;
  revision: number;
  pending: number;
  processing: number;
  done: number;
  manual: number;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
  updatedBy?: string;
}

/** 投递与执行尝试只包含运维元数据，不含租约令牌。 */
export interface EventDelivery {
  id: string;
  consumer: string;
  status: string;
  attempts: number;
  totalAttempts: number;
  availableAt?: string;
  failureCode?: string;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
  updatedBy?: string;
}

export interface EventAttempt {
  deliveryId: string;
  attemptNo: number;
  outcome: string;
  failureCode?: string;
  createdAt: string;
  updatedAt: string;
}

export interface EventDetail {
  event: PlatformEvent;
  deliveries: EventDelivery[];
  attempts: EventAttempt[];
}

/** 平台域请求使用现有会话、CSRF 和统一错误处理。 */
export const eventWorkbenchApi = {
  list: (params: {tenant?: string; type?: string; status?: string; from?: string; until?: string; page: number}, signal: AbortSignal) =>
    http.request<{rows: PlatformEvent[]; total: number}>("get", "/bff/platform/events", { params, signal }),
  detail: (id: string, signal: AbortSignal) =>
    http.request<EventDetail>("get", "/bff/platform/events/" + encodeURIComponent(id), { signal }),
  async retry(deliveryId: string, reason: string) {
    const csrf = await http.request<{token: string; headerName: string}>("get", "/bff/csrf");
    return http.request<void>("post", "/bff/platform/event-deliveries/" + encodeURIComponent(deliveryId) + "/retry", {
      data: {reason}, headers: {[csrf.headerName]: csrf.token}
    });
  }
};
