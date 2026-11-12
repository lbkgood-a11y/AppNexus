import { execFile } from "node:child_process";
import { promisify } from "node:util";

/** 客户端发现的系统队列；只表示操作系统已登记，不保证设备在线或纸张完成。 */
export interface LocalPrinter { name: string }

/**
 * 解析系统队列清单；不向平台自动登记，设备授权由平台管理流程决定。
 * @param output Windows 为 JSON 数组，Linux/macOS 为 LC_ALL=C 的 lpstat -p 输出。
 * @param platform 当前操作系统。
 * @returns 排序去重的队列名称，不包含驱动错误或设备凭据。
 */
export function parsePrinters(output: string, platform: NodeJS.Platform): LocalPrinter[] {
  let names: unknown[];
  if (platform === "win32") {
    const value: unknown = JSON.parse(output.replace(/^\uFEFF/, "").trim() || "[]");
    names = Array.isArray(value) ? value : [value];
    if (names.some(name => typeof name !== "string")) throw new Error("PRINT_DISCOVERY_INVALID");
  } else {
    names = output.split(/\r?\n/).flatMap(line => {
      const match = /^printer\s+(\S+)\s/.exec(line);
      return match ? [match[1]] : [];
    });
  }
  return [...new Set(names as string[])].filter(name => name.trim() && !/[\x00-\x1f\x7f]/.test(name))
    .sort().map(name => ({ name }));
}

/**
 * 只读发现当前客户端队列，不要求平台凭据；命令超时 10 秒，失败不输出系统原始日志。
 * @param platform 默认当前系统，支持 win32、linux、darwin。
 * @returns 可供用户选择的本机队列。
 * @throws Error 系统不支持、CUPS/PrintManagement 不可用或发现超时。
 */
export async function discoverPrinters(platform = process.platform): Promise<LocalPrinter[]> {
  if (!["win32", "linux", "darwin"].includes(platform)) throw new Error("PRINT_PLATFORM_UNSUPPORTED");
  try {
    const command = platform === "win32" ? "powershell.exe" : "lpstat";
    // 静态 PowerShell 脚本不拼接外部输入；显式 UTF-8 避免中文队列名被 OEM 编码破坏。
    const args = platform === "win32" ? ["-NoProfile", "-NonInteractive", "-Command",
      "$ErrorActionPreference='Stop';[Console]::OutputEncoding=[Text.UTF8Encoding]::new();ConvertTo-Json -Compress -InputObject @((Get-Printer).Name)"] : ["-p"];
    const { stdout } = await promisify(execFile)(command, args, {
      encoding: "utf8", timeout: 10_000, maxBuffer: 1_000_000, windowsHide: true,
      env: { ...process.env, LC_ALL: "C" }
    });
    return parsePrinters(stdout, platform);
  } catch {
    throw new Error("PRINT_DISCOVERY_FAILED");
  }
}
