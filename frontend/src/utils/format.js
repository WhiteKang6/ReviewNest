// 金额格式化:后端金额以"分"存储(如 16800 = 168.00 元),统一转成"元"字符串。
// 旧 common.js 的实现字符串分支引用了未定义的变量 p.length(真实 bug),此处重写,行为与原数字分支一致。
export function formatPrice(val) {
  if (val === null || val === undefined || val === '') return null
  const n = Number(val)
  if (isNaN(n)) return null
  return (n / 100).toFixed(2)
}

export function formatTime(date) {
  return date.getFullYear() + "年" + (date.getMonth() + 1) + "月" + date.getDate() + "日 "
}

export function formatMinutes(m) {
  if (m < 10) m = "0" + m
  return m
}
