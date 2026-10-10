import { QUESTION_TYPE_LABEL } from '@/types/api'

const SUBJECT_LABEL: Record<string, string> = {
  math: '数学',
  english: '英语',
  chinese: '语文',
  physics: '物理',
  chemistry: '化学',
}

export function subjectLabel(subject?: string): string {
  if (!subject) return '未知'
  return SUBJECT_LABEL[subject] || subject
}

export function gradeLabel(grade?: number): string {
  if (!grade) return ''
  const names = ['一', '二', '三', '四', '五', '六', '七', '八', '九']
  if (grade >= 1 && grade <= 9) return `${names[grade - 1]}年级`
  if (grade === 10) return '高一'
  if (grade === 11) return '高二'
  if (grade === 12) return '高三'
  return `${grade}年级`
}

export function questionTypeLabel(type: number): string {
  return QUESTION_TYPE_LABEL[type] || '其他'
}

/** 后端返回 yyyy-MM-dd HH:mm:ss */
export function formatDateTime(value?: string): string {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 16)
}

export function formatDate(value?: string): string {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 10)
}

/** 掌握度 0-5 显示成星级，比数字直观 */
export function masteryStars(level: number): string {
  const safe = Math.max(0, Math.min(5, level))
  return '★'.repeat(safe) + '☆'.repeat(5 - Math.max(0, Math.min(5, safe)))
}

export function percentText(part?: number, total?: number): string {
  if (!total) return '0%'
  return `${Math.round(((part ?? 0) / total) * 100)}%`
}
