const fs = require('node:fs')
const path = require('node:path')
const { h } = require('vue')
const { renderToString } = require('vue/server-renderer')
const icons = require('@element-plus/icons-vue')
const { Resvg } = require('@resvg/resvg-js')
const root = path.resolve(__dirname, '../miniprogram/images')
const blue = '#1890ff'

async function svgFor(name, color) {
  if (!icons[name]) throw new Error(`Unknown icon: ${name}`)
  return (await renderToString(h(icons[name]))).replace(/<!--[\s\S]*?-->/g, '').replace(/currentColor/g, color)
}

function save(file, svg, size) {
  const target = path.join(root, file)
  fs.mkdirSync(path.dirname(target), { recursive: true })
  fs.writeFileSync(target, new Resvg(svg, { fitTo: { mode: 'width', value: size } }).render().asPng())
}

async function main() {
  const tabs = { home: 'House', category: 'Grid', study: 'Reading', my: 'User' }
  for (const [file, name] of Object.entries(tabs)) {
    save(`tabbar/${file}.png`, await svgFor(name, '#737d8c'), 81)
    save(`tabbar/${file}-active.png`, await svgFor(name, blue), 81)
  }
  const categories = { code: 'Cpu', java: 'CoffeeCup', frontend: 'Monitor', teaching: 'Reading', growth: 'TrendCharts' }
  for (const [file, name] of Object.entries(categories)) save(`categories/${file}.png`, await svgFor(name, blue), 96)
  const actions = { favorite: 'Star', 'favorite-active': 'StarFilled', menu: 'Menu', 'danmaku-on': 'ChatDotSquare', 'danmaku-off': 'ChatSquare', camera: 'Camera', cart: 'ShoppingCart', paid: 'Wallet', complete: 'CircleCheck', refund: 'Refresh', records: 'Reading', courses: 'Collection', coupon: 'Ticket', ai: 'ChatLineSquare', contact: 'Service', feedback: 'EditPen', about: 'InfoFilled', settings: 'Setting', calendar: 'Calendar', flash: 'Lightning' }
  for (const [file, name] of Object.entries(actions)) {
    const color = ['menu', 'danmaku-on', 'danmaku-off', 'camera', 'flash'].includes(file) ? '#ffffff' : blue
    save(`actions/${file}.png`, await svgFor(name, color), 96)
  }
  save('icon-search.png', await svgFor('Search', '#737d8c'), 96)
  save('category-default.png', await svgFor('Grid', blue), 96)
  const user = await svgFor('User', '#7e9ec4')
  const book = await svgFor('Reading', '#75a4d8')
  const box = await svgFor('Collection', '#75a4d8')
  function placeholder(icon, width, height) {
    const inner = icon.replace(/^<svg[^>]*>/, '').replace(/<\/svg>$/, '')
    const size = Math.round(height * 0.44)
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><rect width="${width}" height="${height}" rx="${Math.round(height * 0.12)}" fill="#edf4fc"/><svg x="${(width-size)/2}" y="${(height-size)/2}" width="${size}" height="${size}" viewBox="0 0 1024 1024">${inner}</svg></svg>`
  }
  save('avatar-default.png', placeholder(user, 160, 160), 160)
  save('course-default.png', placeholder(book, 600, 340), 600)
  save('empty.png', placeholder(box, 320, 240), 320)
  save('empty-study.png', placeholder(book, 320, 240), 320)
  const packageRoot = path.dirname(require.resolve('@element-plus/icons-vue/package.json'))
  fs.copyFileSync(path.join(packageRoot, 'LICENSE'), path.join(root, 'LICENSE'))
  console.log('Generated local navigation, category, action and fallback images.')
}

main().catch(error => { console.error(error.message); process.exitCode = 1 })
