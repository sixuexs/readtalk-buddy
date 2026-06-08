import type { RelationContact } from '@/types/relation'
import type { EChartsOption } from 'echarts'
import {
  getRelationPalette,
  intimacyToLineColor as lineColor,
  SELF_NODE_STYLE,
} from '@/constants/freshTheme'

const NODE_BORDER = '#FFFFFF'
const SELF_SIZE = 56
const PEER_SIZE = 44

/** 亲密度 → 连线颜色（导出给外部如需） */
export function intimacyToLineColor(
  intimacy: number,
  highlighted = false,
  relationType?: RelationContact['relationType'],
): string {
  return lineColor(intimacy, highlighted, relationType)
}

/** 亲密度 → 线宽 */
export function intimacyToLineWidth(intimacy: number, highlighted = false): number {
  const base = 1 + (intimacy / 100) * 4
  return highlighted ? base + 1.5 : base
}

/** 亲密度 → 与中心距离（像素，越大越远） */
export function intimacyToRadius(intimacy: number, minR: number, maxR: number): number {
  const t = 1 - intimacy / 100
  return minR + t * (maxR - minR)
}

/** 亲密度文案（列表等简短场景） */
export function intimacyLabel(intimacy: number): string {
  if (intimacy >= 80) return '非常亲密'
  if (intimacy >= 60) return '较为亲密'
  if (intimacy >= 40) return '一般'
  return '疏远'
}

/** 亲密度描述（详情卡片） */
export function intimacyDescription(intimacy: number): string {
  if (intimacy >= 90) return '亲密无间'
  if (intimacy >= 80) return '非常亲密'
  if (intimacy >= 60) return '较为亲密'
  if (intimacy >= 40) return '关系一般'
  return '略显疏远'
}

/** 星形布局坐标 */
function layoutPeerPositions(
  count: number,
  width: number,
  height: number,
  contacts: RelationContact[],
): Map<string, { x: number; y: number }> {
  const cx = width / 2
  const cy = height / 2
  const minR = Math.min(width, height) * 0.22
  const maxR = Math.min(width, height) * 0.38
  const map = new Map<string, { x: number; y: number }>()

  contacts.forEach((c, i) => {
    const angle = (i / count) * Math.PI * 2 - Math.PI / 2
    const r = intimacyToRadius(c.intimacy, minR, maxR)
    map.set(c.id, {
      x: cx + Math.cos(angle) * r,
      y: cy + Math.sin(angle) * r,
    })
  })
  return map
}

export interface BuildGraphOptionParams {
  selfName: string
  contacts: RelationContact[]
  width: number
  height: number
  selectedId?: string | null
  fontSize?: number
}

/** 构建 ECharts graph 配置 */
export function buildRelationGraphOption(params: BuildGraphOptionParams): EChartsOption {
  const {
    selfName,
    contacts,
    width,
    height,
    selectedId = null,
    fontSize = 12,
  } = params

  const positions = layoutPeerPositions(contacts.length, width, height, contacts)
  const cx = width / 2
  const cy = height / 2

  const nodes: Record<string, unknown>[] = [
    {
      id: 'me',
      name: selfName,
      x: cx,
      y: cy,
      symbolSize: SELF_SIZE,
      itemStyle: {
        color: SELF_NODE_STYLE.color,
        borderColor: SELF_NODE_STYLE.border,
        borderWidth: 3,
        shadowBlur: 14,
        shadowColor: SELF_NODE_STYLE.shadow,
      },
      label: {
        show: true,
        fontSize: fontSize + 1,
        color: '#fff',
        fontWeight: 'bold',
      },
    },
    ...contacts.map((c) => {
      const pos = positions.get(c.id)!
      const selected = c.id === selectedId
      const palette = getRelationPalette(c.relationType)
      return {
        id: c.id,
        name: c.name,
        x: pos.x,
        y: pos.y,
        symbolSize: PEER_SIZE,
        itemStyle: {
          color: palette.main,
          borderColor: selected ? palette.soft : NODE_BORDER,
          borderWidth: selected ? 4 : 2,
          shadowBlur: selected ? 18 : 8,
          shadowColor: selected ? palette.shadow : 'rgba(0, 0, 0, 0.06)',
        },
        label: {
          show: true,
          fontSize,
          color: '#fff',
          fontWeight: 'bold',
        },
      }
    }),
  ]

  const links = contacts.map((c) => {
    const highlighted = c.id === selectedId
    return {
      source: 'me',
      target: c.id,
      value: c.intimacy,
      label: {
        show: !selectedId || highlighted,
        formatter: c.relationType,
        fontSize: fontSize - 1,
        color: highlighted
          ? getRelationPalette(c.relationType).main
          : '#8E9DAB',
        fontWeight: (highlighted ? 'bold' : 'normal') as 'bold' | 'normal',
      },
      lineStyle: {
        color: intimacyToLineColor(c.intimacy, highlighted, c.relationType),
        width: intimacyToLineWidth(c.intimacy, highlighted),
        curveness: 0.08,
        opacity: 0.95,
      },
      symbol: ['none', 'arrow'],
      symbolSize: [0, highlighted ? 10 : 7],
    }
  })

  return {
    backgroundColor: 'transparent',
    animationDuration: 400,
    animationEasing: 'cubicOut',
    tooltip: {
      trigger: 'item',
      confine: true,
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(255, 126, 179, 0.25)',
      borderWidth: 1,
      textStyle: { color: '#2A3441', fontSize: fontSize },
      formatter: (p: unknown) => {
        const item = (Array.isArray(p) ? p[0] : p) as {
          dataType?: string
          data?: { id?: string; target?: string }
          name?: string
        }
        if (item.dataType === 'edge') {
          const edge = item.data as { target?: string } | undefined
          const c = contacts.find((x) => x.id === edge?.target)
          if (!c) return ''
          return `${c.name}\n关系：${c.relationType}\n亲密度：${c.intimacy}`
        }
        const id = (item.data as { id?: string } | undefined)?.id
        if (id === 'me') return `${selfName}\n关系网络中心`
        const c = contacts.find((x) => x.id === id)
        if (!c) return item.name || ''
        return `${c.name}\n${c.relationType} · 亲密度 ${c.intimacy}\n${intimacyLabel(c.intimacy)}`
      },
    },
    series: [
      {
        type: 'graph',
        layout: 'none',
        roam: true,
        scaleLimit: { min: 0.6, max: 2.5 },
        draggable: false,
        focusNodeAdjacency: false,
        data: nodes,
        links,
        edgeLabel: { show: true },
        emphasis: {
          focus: 'none',
          lineStyle: { width: 6 },
          itemStyle: { shadowBlur: 20 },
        },
      } as any,
    ],
  }
}

/** 按关系类型分组（通讯录列表） */
export function groupContactsByType(
  contacts: RelationContact[],
): { type: string; items: RelationContact[] }[] {
  const order = ['家人', '朋友', '同事', '同学']
  const map = new Map<string, RelationContact[]>()
  for (const c of contacts) {
    const list = map.get(c.relationType) || []
    list.push(c)
    map.set(c.relationType, list)
  }
  return order
    .filter((t) => map.has(t))
    .map((type) => ({
      type,
      items: (map.get(type) || []).sort((a, b) => b.intimacy - a.intimacy),
    }))
}
