import { ref } from "vue";
import { formatInstant } from "@appnexus/localization";
import { currentTenant } from "@/utils/tenantContext";
import { http } from "@/utils/http";

/** 展示时区独立于身份及业务计算，缺省 Asia/Shanghai，不向浏览器保存凭据。 */
const timeZone = ref("Asia/Shanghai");
/** 读取当前租户非敏感展示设置；无租户时恢复默认，故障保留默认展示但不修改业务结果。 */
export async function refreshDisplayFormat() {
  timeZone.value = "Asia/Shanghai";
  if (!currentTenant.value) return;
  const tenant = currentTenant.value.tenantId;
  try {
    const values = await http.request<{ key: string; value: string }[]>("get", "/api/configuration/parameters");
    if (currentTenant.value?.tenantId !== tenant) return;
    timeZone.value = values.find(value => value.key === "display.time-zone")?.value ?? "Asia/Shanghai";
  } catch { /* 展示设置读取失败采用明确默认值，不改变认证或业务时间。 */ }
}
/** ISO 时间点按当前租户展示时区与语言格式化，不处理无偏移业务日期。 */
export function displayInstant(value: string, language: string) { return formatInstant(value, language, timeZone.value); }
