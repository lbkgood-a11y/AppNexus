import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
/** 独立实验配置；不加载正式应用入口。 */
export default defineConfig({ plugins:[vue()] });
