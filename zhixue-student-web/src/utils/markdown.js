import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import sql from 'highlight.js/lib/languages/sql'
import xml from 'highlight.js/lib/languages/xml'

hljs.registerLanguage('bash', bash)
hljs.registerLanguage('java', java)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('json', json)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('xml', xml)

const SAFE_URL = /^https?:\/\//i
const LINK_ATTRS = 'target="_blank" rel="noopener noreferrer nofollow"'

/**
 * 回答渲染器。模型输出不可信，三条规则保证结果可以直接用于 v-html：
 * 1. html: false —— 原始 HTML 一律按文本转义；
 * 2. 链接只允许 http/https，新窗口打开并带 noopener noreferrer nofollow；
 * 3. 图片不加载（防追踪像素），只渲染为链接文字。
 */
const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  langPrefix: 'hljs language-',
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
      } catch {
        // 高亮失败时交给 markdown-it 转义原文
      }
    }
    return ''
  }
})

// markdown-it 对链接与图片地址都会调用 validateLink；不通过的保持为普通文本
md.validateLink = (url) => SAFE_URL.test(url.trim())

md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx].attrSet('target', '_blank')
  tokens[idx].attrSet('rel', 'noopener noreferrer nofollow')
  return self.renderToken(tokens, idx, options)
}

md.renderer.rules.image = (tokens, idx) => {
  const token = tokens[idx]
  const src = token.attrGet('src') || ''
  const alt = md.utils.escapeHtml(token.content || '图片')
  return `<a href="${md.utils.escapeHtml(src)}" ${LINK_ATTRS}>[图片] ${alt}</a>`
}

export function renderMarkdown(text) {
  return md.render(text || '')
}
