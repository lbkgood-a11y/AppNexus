import {FaultClosedSession} from './fault-closed-session';
const host=document.querySelector('#app') as HTMLElement;
const session=new FaultClosedSession(1,console.error);Object.assign(window,{faultSession:session});
host.textContent='Playwright 隔离会话探针；不连接正式API。';
