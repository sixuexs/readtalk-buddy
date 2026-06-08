/**
 * WxCanvas — 微信小程序 Canvas 适配器（源自 echarts-for-weixin）
 * 将微信 Canvas 2D 节点包装为 ECharts 可识别的标准 Canvas 接口
 */
export default class WxCanvas {
  ctx: CanvasRenderingContext2D
  canvasId: string
  chart: any = null
  isNew: boolean
  canvasNode: any
  event: Record<string, (e: any) => void> = {}

  constructor(
    ctx: CanvasRenderingContext2D,
    canvasId: string,
    isNew: boolean,
    canvasNode: any,
  ) {
    this.ctx = ctx
    this.canvasId = canvasId
    this.isNew = isNew
    if (isNew) {
      this.canvasNode = canvasNode
    } else {
      this._initStyle(ctx)
    }
    this._initEvent()
  }

  getContext(contextType: string): CanvasRenderingContext2D | undefined {
    if (contextType === '2d') {
      return this.ctx
    }
    return undefined
  }

  setChart(chart: any) {
    this.chart = chart
  }

  addEventListener() {
    // noop — 小程序不需要 DOM 事件监听
  }

  attachEvent() {
    // noop
  }

  detachEvent() {
    // noop
  }

  _initStyle(ctx: any) {
    ctx.createRadialGradient = () => {
      return ctx.createCircularGradient(...arguments)
    }
  }

  _initEvent() {
    this.event = {}
    const eventNames = [
      { wxName: 'touchStart', ecName: 'mousedown' },
      { wxName: 'touchMove', ecName: 'mousemove' },
      { wxName: 'touchEnd', ecName: 'mouseup' },
      { wxName: 'touchEnd', ecName: 'click' },
    ]
    eventNames.forEach((name) => {
      this.event[name.wxName] = (e: any) => {
        const touch = e.touches[0]
        this.chart.getZr().handler.dispatch(name.ecName, {
          zrX: name.wxName === 'tap' ? touch.clientX : touch.x,
          zrY: name.wxName === 'tap' ? touch.clientY : touch.y,
          preventDefault: () => {},
          stopImmediatePropagation: () => {},
          stopPropagation: () => {},
        })
      }
    })
  }

  set width(w: number) {
    if (this.canvasNode) this.canvasNode.width = w
  }

  set height(h: number) {
    if (this.canvasNode) this.canvasNode.height = h
  }

  get width(): number {
    if (this.canvasNode) return this.canvasNode.width
    return 0
  }

  get height(): number {
    if (this.canvasNode) return this.canvasNode.height
    return 0
  }
}
