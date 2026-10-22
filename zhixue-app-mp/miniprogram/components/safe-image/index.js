Component({
  options: { virtualHost: true },
  externalClasses: ['image-class'],
  properties: {
    src: { type: String, value: '' },
    fallback: { type: String, value: '/images/course-default.png' },
    mode: { type: String, value: 'aspectFill' },
    lazyLoad: { type: Boolean, value: false }
  },
  data: { resolvedSrc: '', generation: 0, frames: [] },
  observers: {
    'src, fallback': function () { this.updateSource() }
  },
  lifetimes: {
    attached() { this.updateSource() }
  },
  methods: {
    updateSource() {
      const src = String(this.properties.src || '').trim()
      const resolved = !src || src === 'null' || src === 'undefined' ? this.properties.fallback : src
      this.loadSource(resolved)
    },
    loadSource(src) {
      const generation = this.data.generation + 1
      this.setData({ resolvedSrc: src, generation, frames: [{ src, generation }] })
    },
    onError(event) {
      if (event && event.currentTarget.dataset.generation !== this.data.generation) {
        return
      }
      if (this.data.resolvedSrc !== this.properties.fallback) {
        this.loadSource(this.properties.fallback)
      }
    }
  }
})
