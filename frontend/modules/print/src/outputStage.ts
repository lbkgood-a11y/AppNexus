/** 输出阶段只解释已持久事实；设备提交不等同物理出纸，未知状态不得伪装成功。 */
export function outputStage(value:Record<string,unknown>):string {
  if(value.confirmedJobId){
    if(value.deliveryState==='SUBMITTED')return 'DEVICE_SUBMITTED';
    if(['FAILED','UNKNOWN','CANCELLED'].includes(String(value.deliveryState)))return String(value.deliveryState);
    return 'DEVICE_QUEUED';
  }
  if(['FAILED','UNKNOWN','CANCELLED'].includes(String(value.state)))return String(value.state);
  if(value.state==='SUCCEEDED'&&value.pdfReady)return 'PREVIEW_READY';
  if(value.rendererOnline===false)return 'RENDERER_OFFLINE';
  return value.state==='RUNNING'?'RENDERING':'WAITING_RENDER';
}
