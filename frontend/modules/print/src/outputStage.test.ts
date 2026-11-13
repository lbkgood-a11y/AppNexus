import {describe,it,expect} from 'vitest';
import {outputStage} from './outputStage';
/** 阶段解释不能把预览成功或队列接收当物理打印完成。 */
describe('output stages',()=>{
  it('reports offline separately from configuration',()=>expect(outputStage({state:'READY',rendererOnline:false})).toBe('RENDERER_OFFLINE'));
  it('requires the real PDF artifact',()=>{expect(outputStage({state:'SUCCEEDED',pdfReady:true})).toBe('PREVIEW_READY');expect(outputStage({state:'READY',rendererOnline:true})).toBe('WAITING_RENDER');});
  it('keeps rendering and failure explicit',()=>{expect(outputStage({state:'RUNNING',rendererOnline:true})).toBe('RENDERING');expect(outputStage({state:'FAILED'})).toBe('FAILED');});
  it('does not claim physical completion',()=>expect(outputStage({confirmedJobId:'job',deliveryState:'SUBMITTED'})).toBe('DEVICE_SUBMITTED'));
});
