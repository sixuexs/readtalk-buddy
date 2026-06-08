/** 关系洞察 Tab */
export type RelationInsightTab = 'analysis' | 'suggest' | 'warning'

export interface RelationInsightTabItem {
  id: RelationInsightTab
  label: string
}

export interface RelationWarningItem {
  contactId: string
  name: string
  level: 'high' | 'medium'
  message: string
}
