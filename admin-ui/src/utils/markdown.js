import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

const md = new MarkdownIt({
  html: false,        // 禁用 HTML 标签（防 XSS）
  linkify: true,      // 自动识别链接
  typographer: false,
  highlight(str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${hljs.highlight(str, { language: lang }).value}</code></pre>`
      } catch (_) { /* fallback */ }
    }
    return `<pre class="hljs"><code>${md.utils.escapeHtml(str)}</code></pre>`
  }
})

/**
 * 渲染 Markdown 为 HTML
 * @param {string} text - Markdown 文本
 * @returns {string} 安全的 HTML
 */
export function renderMarkdown(text) {
  if (!text) return ''
  return md.render(text)
}

/**
 * 渲染行内 Markdown（不包裹 <p>）
 */
export function renderInline(text) {
  if (!text) return ''
  return md.renderInline(text)
}

export default md
