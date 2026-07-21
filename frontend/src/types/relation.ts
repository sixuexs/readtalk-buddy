/** 关系类型 */
export type RelationType = '家人' | '朋友' | '同事' | '同学'

/** 联系人（图谱节点，除中心「我」外） */
export interface RelationContact {
  id: string
  name: string
  relationType: RelationType
  /** 亲密度 0–100，后续由算法计算 */
  intimacy: number
  avatar?: string
  lastContactDays: number
  note?: string
  /** 性格特点 */
  personality?: string
  /** 兴趣爱好 */
  interests?: string[]
  /** 关系/身份标签 */
  labels?: string[]
  /** 书友圈分类 */
  category?: string
}

/** 视图模式 */
export type RelationViewMode = 'graph' | 'list'
