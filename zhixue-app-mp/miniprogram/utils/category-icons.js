const ICONS = {
  100: 'code',
  110: 'java',
  120: 'frontend',
  200: 'growth',
  210: 'teaching'
}

function withCategoryIcon(category) {
  const name = ICONS[String(category.id)]
  return {
    ...category,
    icon: name ? `/images/categories/${name}.png` : '/images/category-default.png'
  }
}

module.exports = { withCategoryIcon }
