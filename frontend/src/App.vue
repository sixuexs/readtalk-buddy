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
<style></style>
