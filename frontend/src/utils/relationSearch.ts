import type { RelationContact } from '@/types/relation'

/** 按关键词匹配联系人（名字、关系、标签、备注、兴趣） */
export function matchContactsByKeyword(
  contacts: RelationContact[],
  keyword: string,
): RelationContact[] {
  const q = keyword.trim().toLowerCase()
  if (!q) return contacts

  return contacts.filter((c) => {
    const fields = [
      c.name,
      c.relationType,
      c.note ?? '',
      ...(c.labels ?? []),
      ...(c.interests ?? []),
      c.personality ?? '',
    ]
    return fields.some((f) => f.toLowerCase().includes(q))
  })
}
