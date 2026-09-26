// pages/course/player/index.js
const app = getApp()
const { getCourseChapters, getCourseDetail } = require('../../../api/course')
const { updateStudyProgress, ERR_NOT_IMPLEMENTED } = require('../../../api/user')
const WebSocketManager = require('../../../api/websocket')

Page({
  /**
   * 页面的初始数据
   */
  data: {
    courseId: null,
    chapterId: null,
    courseInfo: null,
    chapters: [],
    currentChapter: null,
    currentLesson: null,
    videoUrl: '',
    danmakuList: [],
    showDanmaku: true,
    showChapters: false,
    progress: 0,
    duration: 0,
    currentTime: 0,
    isPurchased: false
  },

  // videoContext 与 wsManager 都是不可序列化的运行时对象，
  // 必须挂在 this 上而不是 data 里，否则 setData 后方法调用会失效。
  videoContext: null,
  wsManager: null,

  /**
   * 生命周期函数--监听页面加载
   */
  onLoad(options) {
    const { courseId, chapterId, lessonId } = options
    if (!courseId) {
      wx.showToast({
        title: '参数错误',
        icon: 'none'
      })
      setTimeout(() => {
        wx.navigateBack()
      }, 1500)
      return
    }

    this.setData({
      courseId,
      chapterId: chapterId || null
    })

    this.initVideoContext()
    this.loadCourseData()
  },

  /**
   * 生命周期函数--监听页面显示
   */
  onShow() {
    // 设置全屏
    wx.setNavigationBarColor({
      frontColor: '#ffffff',
      backgroundColor: '#000000'
    })
  },

  /**
   * 生命周期函数--监听页面隐藏
   */
  onHide() {
    // 记录学习进度
    this.saveProgress()
  },

  /**
   * 生命周期函数--监听页面卸载
   */
  onUnload() {
    // 关闭WebSocket（实例挂在 this 上，不放进 data：类实例无法被 setData 序列化）
    if (this.wsManager) {
      this.wsManager.close()
      this.wsManager = null
    }
    // 记录学习进度
    this.saveProgress()
  },

  /**
   * 初始化视频上下文
   */
  initVideoContext() {
    // VideoContext 不可序列化，放进 setData 后取回的对象已失去方法，
    // 必须直接挂在页面实例上。
    this.videoContext = wx.createVideoContext('course-video')
  },

  /**
   * 加载课程数据
   */
  async loadCourseData() {
    try {
      const [detailRes, chaptersRes] = await Promise.all([
        getCourseDetail(this.data.courseId),
        getCourseChapters(this.data.courseId)
      ])

      if (detailRes.code === 200) {
        const chapters = chaptersRes.data || []
        const detail = detailRes.data || {}
        this.setData({
          courseInfo: detail,
          chapters,
          isPurchased: !!(detail.isPurchased || detail.canStudy || Number(detail.price || 0) === 0)
        })

        // 设置当前章节和课时
        this.setCurrentLesson()

        // 连接WebSocket
        this.connectWebSocket()
      }
    } catch (error) {
      console.error('加载课程数据失败:', error)
    }
  },

  /**
   * 设置当前课时
   */
  setCurrentLesson() {
    const { chapters, chapterId } = this.data
    let targetChapter = null
    let targetLesson = null

    if (chapterId) {
      targetChapter = chapters.find(ch => ch.id === chapterId)
    } else {
      targetChapter = chapters[0]
    }

    if (targetChapter && targetChapter.lessons && targetChapter.lessons.length > 0) {
      targetLesson = targetChapter.lessons[0]
    }

    if (targetLesson) {
      if (!this.canPlayLesson(targetLesson)) {
        const playableLesson = (targetChapter.lessons || []).find((lesson) => this.canPlayLesson(lesson))
        if (!playableLesson) {
          this.promptPurchase()
          return
        }
        targetLesson = playableLesson
      }
      this.setData({
        currentChapter: targetChapter,
        currentLesson: targetLesson,
        videoUrl: targetLesson.videoUrl
      })
    }
  },

  /**
   * 连接WebSocket
   */
  connectWebSocket() {
    const userId = app.globalData.userInfo?.userId || app.globalData.userId
    if (!userId) {
      console.warn('[player] 未登录，跳过弹幕连接')
      return
    }

    const wsManager = new WebSocketManager()

    // 下行消息就是 DanmakuMessageDTO：{ roomId, userId, content, timePoint }
    wsManager.on('message', (data) => {
      if (data && data.content) {
        this.addDanmaku(data.content, data.timePoint)
      }
    })

    wsManager.connect(this.data.courseId, userId).then(() => {
      // 房间归属由后端在收到首条弹幕时按 roomId 登记，无需单独 join
      this.wsManager = wsManager
    }).catch((error) => {
      console.error('WebSocket连接失败:', error)
    })
  },

  /**
   * 添加弹幕
   */
  addDanmaku(text, time) {
    const danmakuList = [...this.data.danmakuList]
    danmakuList.push({
      id: Date.now(),
      text,
      time,
      color: '#ffffff'
    })
    
    // 只保留最近100条弹幕
    if (danmakuList.length > 100) {
      danmakuList.shift()
    }

    this.setData({ danmakuList })
  },

  /**
   * 发送弹幕
   */
  onSendDanmaku(e) {
    const { text } = e.detail
    if (!text || !text.trim()) {
      wx.showToast({
        title: '请输入弹幕内容',
        icon: 'none'
      })
      return
    }

    if (!this.wsManager) {
      wx.showToast({
        title: '连接未建立',
        icon: 'none'
      })
      return
    }

    // 不做本地回显：后端会把经过敏感词过滤的内容广播回本房间（含发送者），
    // 本地再插一条会重复，且会绕过服务端的过滤结果。
    const sent = this.wsManager.sendDanmaku(text.trim(), this.data.currentTime)
    if (!sent) {
      wx.showToast({
        title: '发送失败，连接已断开',
        icon: 'none'
      })
    }
  },

  /**
   * 视频播放
   */
  onVideoPlay() {
    console.log('视频播放')
  },

  /**
   * 视频暂停
   */
  onVideoPause() {
    this.saveProgress()
  },

  /**
   * 视频时间更新
   */
  onVideoTimeUpdate(e) {
    const { currentTime, duration } = e.detail
    this.setData({
      currentTime,
      duration,
      progress: duration > 0 ? (currentTime / duration) * 100 : 0
    })

    // 过滤并显示当前时间的弹幕
    this.filterDanmaku(currentTime)
  },

  /**
   * 过滤弹幕（显示当前时间附近的弹幕）
   */
  filterDanmaku(currentTime) {
    // 这里可以实现弹幕过滤逻辑
    // 例如只显示当前时间±2秒的弹幕
  },

  /**
   * 视频播放结束
   */
  onVideoEnded() {
    this.saveProgress()
    // 自动播放下一节
    this.playNextLesson()
  },

  /**
   * 播放下一节
   */
  playNextLesson() {
    const { chapters, currentChapter, currentLesson } = this.data
    if (!currentChapter || !currentLesson) return

    const chapterIndex = chapters.findIndex(ch => ch.id === currentChapter.id)
    const lessonIndex = currentChapter.lessons.findIndex(les => les.id === currentLesson.id)

    let nextLesson = null
    let nextChapter = currentChapter

    // 先找当前章节的下一个课时
    if (lessonIndex < currentChapter.lessons.length - 1) {
      nextLesson = currentChapter.lessons[lessonIndex + 1]
    } else if (chapterIndex < chapters.length - 1) {
      // 当前章节没有下一节，找下一个章节的第一个课时
      nextChapter = chapters[chapterIndex + 1]
      if (nextChapter.lessons && nextChapter.lessons.length > 0) {
        nextLesson = nextChapter.lessons[0]
      }
    }

    if (nextLesson) {
      if (!this.canPlayLesson(nextLesson)) {
        this.promptPurchase()
        return
      }
      this.setData({
        currentChapter: nextChapter,
        currentLesson: nextLesson,
        videoUrl: nextLesson.videoUrl
      })
      
      // 重新加载视频
      this.videoContext?.seek(0)
      this.videoContext?.play()
    } else {
      wx.showToast({
        title: '已经是最后一节',
        icon: 'none'
      })
    }
  },

  /**
   * 切换章节侧边栏
   */
  onToggleChapters() {
    this.setData({
      showChapters: !this.data.showChapters
    })
  },

  /**
   * 切换弹幕显示
   */
  onToggleDanmaku() {
    this.setData({
      showDanmaku: !this.data.showDanmaku
    })
  },

  /**
   * 选择章节
   */
  onSelectChapter(e) {
    const { chapter, lesson } = e.currentTarget.dataset
    if (!this.canPlayLesson(lesson)) {
      this.promptPurchase()
      return
    }
    
    this.setData({
      currentChapter: chapter,
      currentLesson: lesson,
      videoUrl: lesson.videoUrl,
      showChapters: false
    })

    // 重新加载视频
    this.videoContext?.seek(0)
    this.videoContext?.play()
  },

  canPlayLesson(lesson) {
    if (!lesson) {
      return false
    }
    if (this.data.isPurchased) {
      return true
    }
    return !!(lesson.isFree || lesson.preview)
  },

  promptPurchase() {
    wx.showModal({
      title: '试看已结束',
      content: '购买后可学习完整课程内容。',
      confirmText: '去购买',
      success: (res) => {
        if (res.confirm) {
          wx.navigateTo({
            url: `/pages/course/detail/index?id=${this.data.courseId}`
          })
        }
      }
    })
  },

  /**
   * 保存学习进度
   */
  async saveProgress() {
    if (!app.globalData.isLogin || !this.data.currentLesson) {
      return
    }

    // 后端尚无学习进度接口，已知能力缺口：只告警一次，避免播放过程中日志刷屏。
    // 不做任何"假装已保存"的处理。
    if (Page.__studyProgressUnavailable) {
      return
    }

    try {
      await updateStudyProgress({
        courseId: this.data.courseId,
        chapterId: this.data.currentChapter.id,
        lessonId: this.data.currentLesson.id,
        progress: this.data.progress,
        currentTime: this.data.currentTime,
        duration: this.data.duration
      })
    } catch (error) {
      if (String(error?.message || '').includes(ERR_NOT_IMPLEMENTED)) {
        Page.__studyProgressUnavailable = true
        console.warn('[player] 后端暂未提供学习进度接口，本次播放不再上报进度')
        return
      }
      console.error('保存学习进度失败:', error)
    }
  },

  /**
   * 全屏播放
   */
  onFullscreenChange(e) {
    const { fullScreen } = e.detail
    if (fullScreen) {
      // 全屏时隐藏其他UI
    } else {
      // 退出全屏时显示UI
    }
  }
})
