export interface TenantSession {
  tenantId?: string;
  contextVersion: number;
}

export function createTenantSession(): TenantSession {
  return { contextVersion: 0 };
}

export function switchTenant(session: TenantSession, tenantId: string): TenantSession {
  return { tenantId, contextVersion: session.contextVersion + 1 };
}
