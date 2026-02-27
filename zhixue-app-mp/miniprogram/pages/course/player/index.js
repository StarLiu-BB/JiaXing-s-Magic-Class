// pages/course/player/index.js
const app = getApp()
const { getCourseChapters, getCourseDetail } = require('../../../api/course')
const { updateStudyProgress } = require('../../../api/user')
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
    videoContext: null,
    wsManager: null
  },

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
    // 关闭WebSocket
    if (this.data.wsManager) {
      this.data.wsManager.close()
    }
    // 记录学习进度
    this.saveProgress()
  },

  /**
   * 初始化视频上下文
   */
  initVideoContext() {
    this.setData({
      videoContext: wx.createVideoContext('course-video')
    })
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
        this.setData({
          courseInfo: detailRes.data,
          chapters
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
    const wsManager = new WebSocketManager()
    
    wsManager.on('message', (data) => {
      if (data.type === 'danmaku') {
        this.addDanmaku(data.text, data.time)
      }
    })

    wsManager.connect(this.data.courseId).then(() => {
      wsManager.join(this.data.courseId)
      this.setData({ wsManager })
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

    if (!this.data.wsManager) {
      wx.showToast({
        title: '连接未建立',
        icon: 'none'
      })
      return
    }

    const currentTime = this.data.currentTime
    this.data.wsManager.sendDanmaku(text.trim(), currentTime)
    this.addDanmaku(text.trim(), currentTime)
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
      this.setData({
        currentChapter: nextChapter,
        currentLesson: nextLesson,
        videoUrl: nextLesson.videoUrl
      })
      
      // 重新加载视频
      this.data.videoContext.seek(0)
      this.data.videoContext.play()
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
    
    this.setData({
      currentChapter: chapter,
      currentLesson: lesson,
      videoUrl: lesson.videoUrl,
      showChapters: false
    })

    // 重新加载视频
    this.data.videoContext.seek(0)
    this.data.videoContext.play()
  },

  /**
   * 保存学习进度
   */
  async saveProgress() {
    if (!app.globalData.isLogin || !this.data.currentLesson) {
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

