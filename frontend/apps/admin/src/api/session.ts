import { http } from "@/utils/http";
import { setSessionProfile } from "@/utils/auth";
export async function restoreSession() {
  const response = await http.request<import("@/utils/auth").DataInfo>("get", "/bff/session");
  if (!response?.username) throw new Error("Invalid session response");
  setSessionProfile(response);
}
export function beginLogin() { window.location.assign("/oauth2/authorization/appnexus"); }
export async function endSession() {
  const csrf = await http.request<{token: string; headerName: string}>("get", "/bff/csrf");
  await http.request("post", "/bff/logout", { headers: { [csrf.headerName]: csrf.token } });
}
