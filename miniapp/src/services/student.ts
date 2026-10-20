import { request } from './request'
import type { StudentVo } from '@/types/api'

/** 我能访问的孩子（创建的 + 绑定的）。登录后的多孩选择靠它。 */
export function listStudents(): Promise<StudentVo[]> {
  return request<StudentVo[]>({ url: '/api/v1/students' })
}

/** 添加孩子。 */
export function createStudent(data: {
  name: string
  grade: number
  school?: string
}): Promise<StudentVo> {
  return request<StudentVo>({ url: '/api/v1/students', method: 'POST', data })
}

/** 凭 6 位孩子编号绑定，家人共享同一份错题数据。 */
export function bindStudent(code: string): Promise<StudentVo> {
  return request<StudentVo>({ url: '/api/v1/students/bind', method: 'POST', data: { code } })
}

/** 更新档案，仅创建者。字段可空，空值不覆盖。 */
export function updateStudent(
  id: number,
  data: { name?: string; grade?: number; school?: string },
): Promise<StudentVo> {
  return request<StudentVo>({ url: `/api/v1/students/${id}`, method: 'PUT', data })
}

/** 家人解绑自己（创建者不可）。 */
export function unbindStudent(id: number): Promise<void> {
  return request<void>({ url: `/api/v1/students/${id}/unbind`, method: 'POST' })
}
