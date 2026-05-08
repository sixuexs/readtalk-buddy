import { defineConfig } from "vite";
import uni from "@dcloudio/vite-plugin-uni";

// Vite 配置文件：加载 uni-app 构建插件，支持编译为微信小程序等多端产物
// https://vitejs.dev/config/
export default defineConfig({
  plugins: [uni()],
});
