import { describe, expect, it } from 'vitest'
import { parseId, parseIdList } from '@/utils/id'

describe('雪花 ID', () => {
  it('保留长 ID 字符串、去重并规范化前导零', () => {
    expect(parseIdList('2112366523950714882, 001003,1003,,')).toEqual(['2112366523950714882', '1003'])
    expect(parseId('9223372036854775807')).toBe('9223372036854775807')
  })
  it.each(['', '0', '-1', 'abc', '9223372036854775808', '1e3'])('拒绝不合法 ID %s', value => {
    expect(() => parseId(value)).toThrow()
  })
  it('拒绝已经丢失精度的 Number 而不伪装恢复', () => {
    expect(() => parseId(Number('2112366523950714882'))).toThrow()
  })
})
