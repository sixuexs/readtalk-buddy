// 使用 SSR 应用创建函数，兼容服务端渲染与客户端挂载
import { createSSRApp } from "vue";
import App from "./App.vue";

// uni-app 入口：创建并返回 Vue 应用实例
export function createApp() {
  const app = createSSRApp(App);
  return {
    app,
  };
}
