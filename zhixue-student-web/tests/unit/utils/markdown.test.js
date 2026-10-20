import { describe, expect, it } from 'vitest'
import { renderMarkdown } from '@/utils/markdown'

describe('renderMarkdown 的安全规则', () => {
  it('原始 HTML 一律转义，不会变成标签', () => {
    const html = renderMarkdown('<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>')
    expect(html).not.toContain('<script')
    expect(html).not.toContain('<img')
    expect(html).toContain('&lt;script&gt;')
  })

  it('只允许 http/https 链接，且新窗口打开、不传来源', () => {
    const html = renderMarkdown('[官网](https://example.com) [坏链接](javascript:alert(1)) [文件](file:///etc/passwd)')
    expect(html).toContain('<a href="https://example.com" target="_blank" rel="noopener noreferrer nofollow">官网</a>')
    expect(html).not.toMatch(/href="javascript/i)
    expect(html).not.toMatch(/href="file/i)
  })

  it('裸网址自动成为安全链接', () => {
    expect(renderMarkdown('见 https://docs.oracle.com/javase/8/')).toContain('target="_blank" rel="noopener noreferrer nofollow"')
  })

  it('图片不加载，只显示为链接文字', () => {
    const html = renderMarkdown('![架构图](https://t.example.com/p.png)')
    expect(html).not.toContain('<img')
    expect(html).toContain('<a href="https://t.example.com/p.png" target="_blank" rel="noopener noreferrer nofollow">[图片] 架构图</a>')
  })

  it('非 http(s) 的图片地址不成为任何元素', () => {
    const html = renderMarkdown('![x](data:image/png;base64,AAAA)')
    expect(html).not.toContain('<img')
    expect(html).not.toContain('href="data:')
  })

  it('Java 代码块高亮，未知语言原样转义', () => {
    const java = renderMarkdown('```java\npublic class A { int a = 1; }\n```')
    expect(java).toContain('class="hljs language-java"')
    expect(java).toContain('hljs-keyword')
    const unknown = renderMarkdown('```brainfuck\n<b>x</b>\n```')
    expect(unknown).toContain('&lt;b&gt;x&lt;/b&gt;')
    expect(unknown).not.toContain('<b>')
  })

  it('单个换行保留为换行（模型常用单换行分点）', () => {
    expect(renderMarkdown('第一行\n第二行')).toContain('第一行<br>\n第二行')
  })

  it('空内容渲染为空串', () => {
    expect(renderMarkdown('')).toBe('')
    expect(renderMarkdown(null)).toBe('')
  })
})
