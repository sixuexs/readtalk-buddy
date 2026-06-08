<script setup lang="ts">
import { onLaunch, onShow, onHide } from "@dcloudio/uni-app";

// 应用初始化
onLaunch(() => {
  console.log("App Launch");
});

// 应用从后台进入前台
onShow(() => {
  console.log("App Show");
});

// 应用进入后台
onHide(() => {
  console.log("App Hide");
});
</script>
<style>
page {
  background-color: #fff6fa;
}

/* H5 宽屏：取消默认「手机壳」限宽，让关系图谱等页面可铺满浏览器 */
@media (min-width: 768px) {
  html,
  body,
  #app {
    width: 100%;
    max-width: none;
    min-height: 100%;
    margin: 0;
  }

  uni-app,
  uni-page,
  uni-page-wrapper,
  uni-page-body {
    width: 100% !important;
    max-width: none !important;
    min-height: 100%;
  }
}
</style>
