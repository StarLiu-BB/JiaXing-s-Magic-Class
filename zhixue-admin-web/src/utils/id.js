export function parseId(value) {
  if (typeof value === 'number' && !Number.isSafeInteger(value)) throw new Error('ID 已丢失精度，请使用字符串')
  const text = String(value ?? '').trim()
  if (!/^\d{1,19}$/.test(text) || BigInt(text) <= 0n || BigInt(text) > 9223372036854775807n) {
    throw new Error('请输入有效的正整数 ID')
  }
  return BigInt(text).toString()
}

export function parseIdList(input) {
  const values = String(input ?? '').split(',').map(value => value.trim()).filter(Boolean)
  if (!values.length) throw new Error('请输入至少一个 ID')
  return [...new Set(values.map(parseId))]
}
