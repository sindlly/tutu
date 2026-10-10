import { request, uploadFile } from './request'
import type { PresignVo, UploadedFileVo } from '@/types/api'

/** 业务类型，与后端 AppFile 常量一致 */
export const BizType = {
  HOMEWORK_IMAGE: 1,
  MISTAKE_CROP: 2,
  MISTAKE_CARD: 3,
  AVATAR: 4,
} as const

/** 上传作业原图。服务端会做 EXIF 摆正与压缩，前端不必重复处理尺寸。 */
export function uploadHomeworkImage(filePath: string): Promise<UploadedFileVo> {
  return uploadFile(filePath, BizType.HOMEWORK_IMAGE) as Promise<UploadedFileVo>
}

/** 取私有读地址（预签名，带时效）。图片地址不要长期缓存。 */
export function presign(fileId: number): Promise<PresignVo> {
  return request<PresignVo>({ url: `/api/v1/files/presign?fileId=${fileId}` })
}
