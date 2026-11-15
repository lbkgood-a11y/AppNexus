import { defineConfig } from '@playwright/test';
/** 本机验证端口5179；不访问正式API，默认由测试启动隔离服务。 */
export default defineConfig({testDir:'tests',workers:1,use:{baseURL:'http://127.0.0.1:5179',channel:'chrome'},webServer:{command:'npm run dev -- --port 5179 --strictPort',url:'http://127.0.0.1:5179',reuseExistingServer:false}});
