/**
 * WebSocket 封装（用于弹幕）
 */
const WS_URL = 'ws://192.168.211.175:9999/ws/danmaku'

class WebSocketManager {
  constructor() {
    this.socket = null
    this.reconnectTimer = null
    this.heartbeatTimer = null
    this.reconnectCount = 0
    this.maxReconnectCount = 5
    this.reconnectInterval = 3000
    this.listeners = {
      open: [],
      close: [],
      error: [],
      message: []
    }
  }

  /**
   * 连接WebSocket
   * @param {string} courseId 课程ID
   */
  connect(courseId) {
    return new Promise((resolve, reject) => {
      if (this.socket && this.socket.readyState === WebSocket.OPEN) {
        resolve()
        return
      }

      const url = `${WS_URL}?courseId=${courseId}`
      this.socket = wx.connectSocket({
        url,
        success: () => {
          this.setupListeners()
          resolve()
        },
        fail: (err) => {
          reject(err)
        }
      })
    })
  }

  /**
   * 设置监听器
   */
  setupListeners() {
    this.socket.onOpen(() => {
      console.log('WebSocket连接成功')
      this.reconnectCount = 0
      this.startHeartbeat()
      this.listeners.open.forEach(cb => cb())
    })

    this.socket.onClose(() => {
      console.log('WebSocket连接关闭')
      this.stopHeartbeat()
      this.listeners.close.forEach(cb => cb())
      this.reconnect()
    })

    this.socket.onError((err) => {
      console.error('WebSocket错误:', err)
      this.listeners.error.forEach(cb => cb(err))
    })

    this.socket.onMessage((res) => {
      try {
        const data = JSON.parse(res.data)
        this.listeners.message.forEach(cb => cb(data))
      } catch (error) {
        console.error('解析消息失败:', error)
      }
    })
  }

  /**
   * 发送消息
   */
  send(data) {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send({
        data: JSON.stringify(data),
        success: () => {},
        fail: (err) => {
          console.error('发送消息失败:', err)
        }
      })
    }
  }

  /**
   * 发送弹幕
   */
  sendDanmaku(text, time) {
    this.send({
      type: 'danmaku',
      text,
      time
    })
  }

  /**
   * 加入房间
   */
  join(courseId) {
    this.send({
      type: 'join',
      courseId
    })
  }

  /**
   * 离开房间
   */
  leave() {
    this.send({
      type: 'leave'
    })
  }

  /**
   * 开始心跳
   */
  startHeartbeat() {
    this.heartbeatTimer = setInterval(() => {
      if (this.socket && this.socket.readyState === WebSocket.OPEN) {
        this.send({ type: 'ping' })
      }
    }, 30000) // 30秒心跳
  }

  /**
   * 停止心跳
   */
  stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  /**
   * 重连
   */
  reconnect() {
    if (this.reconnectCount >= this.maxReconnectCount) {
      console.error('重连次数已达上限')
      return
    }

    this.reconnectTimer = setTimeout(() => {
      this.reconnectCount++
      // 这里需要重新获取courseId，暂时跳过
      console.log(`尝试重连 ${this.reconnectCount}/${this.maxReconnectCount}`)
    }, this.reconnectInterval)
  }

  /**
   * 监听事件
   */
  on(event, callback) {
    if (this.listeners[event]) {
      this.listeners[event].push(callback)
    }
  }

  /**
   * 移除监听
   */
  off(event, callback) {
    if (this.listeners[event]) {
      const index = this.listeners[event].indexOf(callback)
      if (index > -1) {
        this.listeners[event].splice(index, 1)
      }
    }
  }

  /**
   * 关闭连接
   */
  close() {
    this.stopHeartbeat()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
    }
    if (this.socket) {
      this.leave()
      this.socket.close()
      this.socket = null
    }
  }
}

module.exports = WebSocketManager

