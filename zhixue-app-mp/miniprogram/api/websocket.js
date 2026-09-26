/**
 * WebSocket 封装（用于弹幕）。
 *
 * 与后端的契约（zhixue-interaction）：
 *  - 握手路径必须是 /ws。后端 WebSocketServerProtocolHandler("/ws", null, true, 65536)
 *    的 checkStartsWith=false，只接受精确匹配，/ws/danmaku 会被拒绝。
 *  - 上行消息体必须匹配 DanmakuMessageDTO：{ roomId, userId, content, timePoint }，
 *    缺少 roomId / userId / content 会被后端 validateMessage 拒绝。
 *  - 下行消息就是同结构的 DanmakuMessageDTO（由 Redis 按房间广播）。
 *
 * 注意：小程序运行时没有浏览器的 WebSocket 全局对象，
 * 不能使用 WebSocket.OPEN 判断状态，必须自行维护连接标志。
 */

const { getWsUrl } = require('../config')

class WebSocketManager {
  constructor() {
    this.socket = null
    this.reconnectTimer = null
    this.heartbeatTimer = null
    this.reconnectCount = 0
    this.maxReconnectCount = 5
    this.reconnectInterval = 3000
    /** 小程序无 WebSocket.OPEN，自行维护连接状态 */
    this.isOpen = false
    /** 记住房间与用户，重连后才能自动恢复 */
    this.roomId = null
    this.userId = null
    this.manualClosed = false
    this.listeners = {
      open: [],
      close: [],
      error: [],
      message: []
    }
  }

  /**
   * 连接 WebSocket。
   * @param {number|string} roomId 房间号（课程ID）
   * @param {number|string} userId 当前用户ID，后端要求必填
   */
  connect(roomId, userId) {
    this.roomId = roomId
    this.userId = userId
    this.manualClosed = false

    return new Promise((resolve, reject) => {
      if (this.socket && this.isOpen) {
        resolve()
        return
      }

      const url = `${getWsUrl()}?roomId=${roomId}`
      this.socket = wx.connectSocket({
        url,
        fail: (err) => {
          this.isOpen = false
          reject(err)
        }
      })

      if (!this.socket) {
        reject(new Error('wx.connectSocket 未返回连接对象'))
        return
      }

      this.setupListeners()
      // 以 onOpen 为准，而不是 connectSocket 的 success 回调
      this.listeners.open.push(function onceOpen() {
        resolve()
      })
    })
  }

  setupListeners() {
    this.socket.onOpen(() => {
      console.log('WebSocket连接成功')
      this.isOpen = true
      this.reconnectCount = 0
      this.startHeartbeat()
      this.listeners.open.forEach((cb) => cb())
    })

    this.socket.onClose(() => {
      console.log('WebSocket连接关闭')
      this.isOpen = false
      this.stopHeartbeat()
      this.listeners.close.forEach((cb) => cb())
      if (!this.manualClosed) {
        this.reconnect()
      }
    })

    this.socket.onError((err) => {
      console.error('WebSocket错误:', err)
      this.isOpen = false
      this.listeners.error.forEach((cb) => cb(err))
    })

    this.socket.onMessage((res) => {
      try {
        const data = JSON.parse(res.data)
        this.listeners.message.forEach((cb) => cb(data))
      } catch (error) {
        console.error('解析消息失败:', error)
      }
    })
  }

  send(data) {
    if (!this.socket || !this.isOpen) {
      console.warn('WebSocket 未连接，消息未发送')
      return false
    }
    this.socket.send({
      data: JSON.stringify(data),
      fail: (err) => {
        console.error('发送消息失败:', err)
      }
    })
    return true
  }

  /**
   * 发送弹幕。字段名必须与后端 DanmakuMessageDTO 一致。
   * @param {string} content 弹幕内容
   * @param {number} timePoint 视频时间点（秒）
   */
  sendDanmaku(content, timePoint) {
    return this.send({
      roomId: Number(this.roomId),
      userId: Number(this.userId),
      content,
      timePoint: Math.max(0, Math.floor(timePoint || 0))
    })
  }

  startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      if (this.socket && this.isOpen) {
        // 后端 IdleStateHandler 读空闲 60s，这里 30s 一次即可保活。
        // 使用 Netty 原生 ping 帧语义不可用，改发一次空操作由后端忽略。
        this.socket.send({ data: JSON.stringify({ type: 'ping' }), fail: () => {} })
      }
    }, 30000)
  }

  stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  /** 真实重连：用保存的 roomId/userId 重新建连，而不是只打日志。 */
  reconnect() {
    if (this.reconnectCount >= this.maxReconnectCount) {
      console.error('重连次数已达上限')
      return
    }
    if (this.roomId == null) {
      return
    }

    this.reconnectTimer = setTimeout(() => {
      this.reconnectCount++
      console.log(`尝试重连 ${this.reconnectCount}/${this.maxReconnectCount}`)
      this.socket = null
      this.connect(this.roomId, this.userId).catch((err) => {
        console.error('重连失败:', err)
      })
    }, this.reconnectInterval)
  }

  on(event, callback) {
    if (this.listeners[event]) {
      this.listeners[event].push(callback)
    }
  }

  off(event, callback) {
    if (this.listeners[event]) {
      const index = this.listeners[event].indexOf(callback)
      if (index > -1) {
        this.listeners[event].splice(index, 1)
      }
    }
  }

  close() {
    this.manualClosed = true
    this.stopHeartbeat()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    if (this.socket) {
      this.socket.close({})
      this.socket = null
    }
    this.isOpen = false
  }
}

module.exports = WebSocketManager
