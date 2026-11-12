import { spawn } from "node:child_process";

/** 队列调用失败阶段；启动后超时或退出异常不能证明没有出纸，必须按 UNKNOWN 处理。 */
export class QueueFailure extends Error {
  constructor(public readonly uncertain: boolean) {
    super(uncertain ? "QUEUE_RESULT_UNKNOWN" : "QUEUE_NOT_STARTED");
  }
}

/**
 * 按客户端选定队列构造跨平台打印命令；不使用操作系统默认打印机。
 * @param platform Windows、Linux 或 macOS；其他系统失败关闭。
 * @param file 本次任务 PDF 绝对路径。
 * @param printer 客户端选择并在平台登记的系统队列名称，必填。
 * @param copies 份数，整数 1—99。
 * @param sumatra Windows 必填的 SumatraPDF 绝对路径，其他系统忽略。
 * @returns 不经过 shell 的可执行程序及参数数组。
 */
export function queueCommand(platform: NodeJS.Platform, file: string, printer: string, copies: number, sumatra?: string): { command: string; args: string[] } {
  if (!printer.trim() || /[\x00-\x1f\x7f]/.test(printer) || !Number.isInteger(copies) || copies < 1 || copies > 99) {
    throw new QueueFailure(false);
  }
  if (platform === "win32") {
    if (!sumatra) throw new QueueFailure(false);
    return { command: sumatra, args: ["-silent", "-print-to", printer, "-print-settings", `${copies}x`, file] };
  }
  if (platform !== "linux" && platform !== "darwin") throw new QueueFailure(false);
  return { command: "lp", args: ["-d", printer, "-n", String(copies), "--", file] };
}

/**
 * 在限定时间内调用系统打印程序；不记录 stderr，防止驱动输出文档或设备敏感信息。
 * @param command 受部署配置约束的可执行程序；不经过 shell。
 * @param args 已分离的队列、份数和 PDF 路径参数。
 * @param timeoutMs 默认 30000 毫秒；超时杀死子进程但不能撤销已提交队列。
 * @returns 程序正常退出；仅代表队列接受，不代表纸张完成。
 * @throws QueueFailure 启动失败为确定失败，其余失败均为结果未知。
 */
export async function runQueue(command: string, args: string[], timeoutMs = 30_000): Promise<void> {
  await new Promise<void>((resolve, reject) => {
    let started = false;
    const child = spawn(command, args, { stdio: "ignore", shell: false, windowsHide: true });
    const timer = setTimeout(() => {
      child.kill();
      reject(new QueueFailure(started));
    }, timeoutMs);
    child.once("spawn", () => { started = true; });
    child.once("error", () => { clearTimeout(timer); reject(new QueueFailure(started)); });
    child.once("exit", code => {
      clearTimeout(timer);
      if (code === 0) resolve(); else reject(new QueueFailure(started));
    });
  });
}
