function createSseCodec(onEvent, { captureRaw = () => false } = {}) {
  const MAX_RESPONSE_BYTES = 1024 * 1024
  const MAX_EVENT_CHARS = 128 * 1024
  let receivedBytes = 0
  let pending = []
  let text = ''
  let raw = ''

  function decode(bytes, final) {
    const data = pending.concat(Array.from(bytes))
    pending = []
    let result = ''
    for (let i = 0; i < data.length;) {
      const first = data[i]
      const size = first < 128 ? 1 : first >= 194 && first <= 223 ? 2 : first >= 224 && first <= 239 ? 3 : first >= 240 && first <= 244 ? 4 : 0
      if (!size) { result += '\ufffd'; i++; continue }
      if (i + size > data.length) {
        if (final) result += '\ufffd'
        else pending = data.slice(i)
        break
      }
      let point = first & (size === 1 ? 127 : (1 << (7 - size)) - 1)
      let valid = true
      for (let k = 1; k < size; k++) {
        if ((data[i + k] & 192) !== 128) { valid = false; break }
        point = (point << 6) | (data[i + k] & 63)
      }
      if (!valid || (size === 2 && point < 128) || (size === 3 && point < 2048) || (size === 4 && point < 65536) || point > 1114111 || (point >= 55296 && point <= 57343)) {
        result += '\ufffd'; i++; continue
      }
      result += String.fromCodePoint(point)
      i += size
    }
    return result
  }

  function block(source) {
    let event = 'message'
    const data = []
    for (const line of source.split(/\r\n|\r|\n/)) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
    }
    if (!data.length) return
    let payload
    try { payload = JSON.parse(data.join('\n')) } catch { payload = null }
    onEvent({ event, data: payload })
  }

  function drain(final) {
    let boundary
    while ((boundary = /\r\n\r\n|\n\n|\r\r/.exec(text))) {
      if (boundary.index > MAX_EVENT_CHARS) throw new Error('SSE event too large')
      block(text.slice(0, boundary.index))
      text = text.slice(boundary.index + boundary[0].length)
    }
    if (text.length > MAX_EVENT_CHARS) throw new Error('SSE event too large')
    if (final && text.trim()) { block(text); text = '' }
  }

  return {
    push(buffer) {
      const bytes = new Uint8Array(buffer)
      receivedBytes += bytes.byteLength
      if (receivedBytes > MAX_RESPONSE_BYTES) throw new Error('SSE response too large')
      const value = decode(bytes, false)
      if (captureRaw()) raw += value
      text += value
      drain(false)
    },
    finish() {
      const tail = decode([], true)
      text += tail
      if (captureRaw()) raw += tail
      drain(true)
      return raw
    }
  }
}

module.exports = { createSseCodec }
