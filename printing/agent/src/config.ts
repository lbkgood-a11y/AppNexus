import { isAbsolute } from "node:path";

/** 站点代理启动配置；只在启动入口读取环境，模块导入不得要求真实凭据。 */
export interface AgentConfig {
  api: string;
  site: string;
  agent: string;
  tenantVersion: string;
  tokenUrl: string;
  clientId: string;
  clientSecret: string;
  chromium: string;
  sumatra?: string;
  pollMs: number;
}

/**
 * 启动前拒绝不安全地址与无效部署参数，错误仅包含参数名，避免泄露凭据。
 * @param env 部署环境变量；支持 HTTP/HTTPS；HTTP 用于用户明确选择的本机或受信内网，传输不加密。
 * @param platform 目标操作系统；默认当前平台，Windows 必须配置 SumatraPDF。
 * @returns 已校验的配置；轮询默认 3000 毫秒，允许 1000—60000 毫秒整数。
 * @throws Error 必填值、地址、路径、租户版本或轮询间隔不合法。
 */
export function readConfig(env: NodeJS.ProcessEnv, platform = process.platform): AgentConfig {
  const needed = (name: string): string => {
    const value = env[name];
    if (!value?.trim()) throw new Error(`Invalid ${name}`);
    return value;
  };
  const address = (name: string): string => {
    let url: URL;
    try { url = new URL(needed(name)); } catch { throw new Error(`Invalid ${name}`); }
    if (!["http:", "https:"].includes(url.protocol) || url.username || url.password || url.hash || url.search) {
      throw new Error(`Invalid ${name}`);
    }
    return url.href.replace(/\/$/, "");
  };
  const executable = (name: string): string => {
    const value = needed(name);
    if (!isAbsolute(value)) throw new Error(`Invalid ${name}`);
    return value;
  };
  const site = needed("PRINT_SITE_CODE"), agent = needed("PRINT_AGENT_ID");
  const tenantVersion = env.PRINT_TENANT_CONTEXT_VERSION ?? "1";
  const pollMs = Number(env.PRINT_POLL_INTERVAL_MS ?? "3000");
  if (!/^[A-Za-z][A-Za-z0-9_.-]{0,63}$/.test(site)) throw new Error("Invalid PRINT_SITE_CODE");
  if (agent.length > 128 || /[\x00-\x1f\x7f]/.test(agent)) throw new Error("Invalid PRINT_AGENT_ID");
  if (!/^[1-9]\d*$/.test(tenantVersion) || !Number.isSafeInteger(Number(tenantVersion))) {
    throw new Error("Invalid PRINT_TENANT_CONTEXT_VERSION");
  }
  if (!Number.isInteger(pollMs) || pollMs < 1000 || pollMs > 60000) throw new Error("Invalid PRINT_POLL_INTERVAL_MS");
  return {
    api: address("PRINT_API_URL"), tokenUrl: address("PRINT_TOKEN_URL"), site, agent,
    tenantVersion, pollMs, chromium: executable("PRINT_CHROMIUM_PATH"),
    sumatra: platform === "win32" ? executable("PRINT_SUMATRA_PATH") : undefined,
    clientId: needed("PRINT_CLIENT_ID"), clientSecret: needed("PRINT_CLIENT_SECRET")
  };
}
