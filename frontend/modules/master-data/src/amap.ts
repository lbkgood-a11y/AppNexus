/** 高德 JS API 最小契约，仅用于地图选点；坐标为 GCJ-02，经度在前、纬度在后。 */
export interface MapPoint { lng: number; lat: number }
/** 地图实例在弹窗关闭时销毁，避免事件与 WebGL 资源残留。 */
export interface AmapInstance {
  on(event: "click", handler: (event: { lnglat: MapPoint }) => void): void;
  destroy(): void;
  setZoomAndCenter(zoom:number,center:number[]):void;
}
/** 只声明实际使用的 SDK 能力，不将第三方对象放入 Vue 深层响应式状态。 */
export interface PlaceResult { id:string; name:string; address?:string; location?:MapPoint }
interface AmapSdk {
  PlaceSearch:new(options:{pageSize:number})=>{search(keyword:string,callback:(status:string,result:{poiList?:{pois:PlaceResult[]}})=>void):void};
  Map: new (container: HTMLElement, options: { zoom: number; center: number[]; resizeEnable: boolean }) => AmapInstance;
  Marker: new (options: { map: AmapInstance; position: number[] }) => { setPosition(point: number[]): void };
}
let signature = "";
let activeScript: HTMLScriptElement | undefined;
let pending: Promise<AmapSdk> | undefined;
/**
 * 按需加载固定高德官方地址；20 秒超时后允许重试，不记录 Key 或安全配置。
 * 部署使用受域名限制的公开 Web Key；安全密钥通过同源代理保留在服务端。
 * @param configuration 当前租户公开 Web Key 与同源代理路径，空值表示未配置；每次打开重新读取。
 * @throws SDK 下载失败、超时或部署缺少配置时拒绝加载。
 */
export function loadAmap(configuration: {key:string;serviceHost:string}): Promise<AmapSdk> {
  const key = configuration.key.trim();
  const serviceHost = configuration.serviceHost.trim();
  const nextSignature = JSON.stringify([key, serviceHost]);
  if (pending && signature === nextSignature) return pending;
  if (!key || !serviceHost || !serviceHost.startsWith("/") || serviceHost.startsWith("//")) return Promise.reject(new Error("configuration"));
  signature = nextSignature;
  activeScript?.remove();
  const host = window as Window & { AMap?: AmapSdk; _AMapSecurityConfig?: { serviceHost: string } };
  delete host.AMap;
  host._AMapSecurityConfig = { serviceHost };
  pending = new Promise<AmapSdk>((resolve, reject) => {
    const script = document.createElement("script");
    activeScript = script;
    const timer = window.setTimeout(() => finish(false), 20000);
    function finish(success: boolean) {
      window.clearTimeout(timer);
      script.onload = script.onerror = null;
      if (signature !== nextSignature) { reject(new Error("unavailable")); return; }
      if (success && host.AMap) resolve(host.AMap);
      else { script.remove(); pending = undefined; reject(new Error("unavailable")); }
    }
    script.src = `https://webapi.amap.com/maps?v=2.0&plugin=AMap.PlaceSearch&key=${encodeURIComponent(key)}`;
    script.onload = () => finish(true);
    script.onerror = () => finish(false);
    document.head.appendChild(script);
  });
  return pending;
}
