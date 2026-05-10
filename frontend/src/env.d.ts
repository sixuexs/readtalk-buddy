/// <reference types="vite/client" />

// 声明 .vue 单文件组件模块类型，使 TypeScript 能正确识别 Vue SFC 导入
declare module '*.vue' {
  import { DefineComponent } from 'vue'
  // eslint-disable-next-line @typescript-eslint/no-explicit-any, @typescript-eslint/ban-types
  const component: DefineComponent<{}, {}, any>
  export default component
}
