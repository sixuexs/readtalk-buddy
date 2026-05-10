export {}

// 将 uni-app 的 App 和 Page 生命周期钩子合并到 Vue 组件选项类型中，
// 使 TypeScript 在 .vue 文件中能正确推断 onLaunch、onShow 等 uni 生命周期
declare module "vue" {
  type Hooks = App.AppInstance & Page.PageInstance;
  interface ComponentCustomOptions extends Hooks {}
}