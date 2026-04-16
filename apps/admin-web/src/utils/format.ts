import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

// 格式化日期时间
export function formatDateTime(date: string | number | Date, format = 'YYYY-MM-DD HH:mm:ss'): string {
  if (!date) return '-'
  return dayjs(date).format(format)
}

// 格式化为相对时间
export function formatRelativeTime(date: string | number | Date): string {
  if (!date) return '-'
  return dayjs(date).fromNow()
}

// 格式化文件大小
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 格式化百分比
export function formatPercent(value: number, digits = 1): string {
  if (value === null || value === undefined) return '-'
  return (value * 100).toFixed(digits) + '%'
}

// 格式化推理耗时
export function formatInferenceTime(ms: number): string {
  if (ms < 1000) return `${ms.toFixed(1)}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

// 格式化置信度
export function formatConfidence(value: number): string {
  return (value * 100).toFixed(1) + '%'
}
