import { View, Text, Image } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { useState } from 'react'
import { ensureActiveStudent, ensureLogin } from '@/services/auth'
import { uploadHomeworkImage } from '@/services/file'
import { createSubmit } from '@/services/homework'
import { userStore } from '@/store/user'
import { clearBizNo, currentBizNo } from '@/utils/id'
import { GRADES, SUBJECTS } from '@/types/api'
import './capture.scss'

const MAX_PAGES = 4

export default function Capture() {
  const [grade, setGrade] = useState<number>(3)
  const [images, setImages] = useState<string[]>([])
  const [subject, setSubject] = useState<string>('math')
  const [busy, setBusy] = useState(false)
  const [progressText, setProgressText] = useState('')
  const [kidName, setKidName] = useState('')

  // 兜底：用户可能在别处清过登录态，这里补一次登录并把年级带出来
  useDidShow(() => {
    void (async () => {
      try {
        await ensureLogin()
        // 多孩必选：没选孩子就跳选择页，本页不加载
        const student = await ensureActiveStudent()
        if (!student) return
        setGrade(student.grade)
        setKidName(student.name)
      } catch {
        // 登录失败时提交会再次报错，不在这里重复提示
      }
    })()
  })

  async function pickImage() {
    const remain = MAX_PAGES - images.length
    if (remain <= 0) {
      Taro.showToast({ title: `一次最多 ${MAX_PAGES} 页`, icon: 'none' })
      return
    }
    try {
      const res = await Taro.chooseMedia({
        count: remain,
        mediaType: ['image'],
        sourceType: ['camera', 'album'],
        // 交给服务端做摆正与压缩，这里只要压到能快速上传即可
        sizeType: ['compressed'],
      })
      setImages([...images, ...res.tempFiles.map((f) => f.tempFilePath)])
    } catch {
      // 用户主动取消，不提示
    }
  }

  function removeImage(index: number) {
    setImages(images.filter((_, i) => i !== index))
  }

  function previewImage(index: number) {
    Taro.previewImage({ current: images[index], urls: images })
  }

  async function submit() {
    if (images.length === 0) {
      Taro.showToast({ title: '请先拍照或选择作业照片', icon: 'none' })
      return
    }
    const current = userStore.getActiveStudent()
    if (!current) {
      Taro.showToast({ title: '请先选择孩子', icon: 'none' })
      Taro.navigateTo({ url: '/pages/child-select/child-select' })
      return
    }

    setBusy(true)
    try {
      // 幂等号在这一步之后才清除：万一中途失败，用户重试会命中同一个任务，
      // 既不会重复批改，也不会重复扣额度。
      const bizNo = currentBizNo()

      const fileIds: number[] = []
      for (let i = 0; i < images.length; i++) {
        setProgressText(`正在上传第 ${i + 1}/${images.length} 页…`)
        const uploaded = await uploadHomeworkImage(images[i])
        fileIds.push(uploaded.fileId)
      }

      setProgressText('正在提交批改…')
      const submit = await createSubmit({
        bizNo,
        studentId: current.id,
        subject,
        grade,
        fileIds,
      })

      clearBizNo()
      Taro.redirectTo({ url: `/pages/progress/progress?submitId=${submit.submitId}` })
    } catch {
      // 请求层已经 toast 过具体原因
    } finally {
      setBusy(false)
      setProgressText('')
    }
  }

  return (
    <View className="page capture">
      {kidName && (
        <View className="kid-strip" onClick={() => Taro.navigateTo({ url: '/pages/child-select/child-select' })}>
          <Text>给「{kidName}」批改</Text>
          <Text className="kid-strip-op">切换</Text>
        </View>
      )}

      <View className="card">
        <View className="row-between">
          <Text className="title">作业照片</Text>
          <Text className="muted">
            {images.length}/{MAX_PAGES} 页
          </Text>
        </View>

        <View className="thumbs mt16">
          {images.map((path, index) => (
            <View key={path} className="thumb">
              <Image className="thumb-img" src={path} mode="aspectFill" onClick={() => previewImage(index)} />
              <View className="thumb-del" onClick={() => removeImage(index)}>
                ×
              </View>
            </View>
          ))}
          {images.length < MAX_PAGES && (
            <View className="thumb thumb-add" onClick={pickImage}>
              <Text className="thumb-add-icon">＋</Text>
              <Text className="thumb-add-text">拍照/选图</Text>
            </View>
          )}
        </View>

        <View className="guide muted mt16">
          拍正、拍满、光线充足。答案写得清楚一点，识别会更准。
        </View>
      </View>

      <View className="card">
        <Text className="title">学科</Text>
        <View className="options mt16">
          {SUBJECTS.map((item) => (
            <View
              key={item.value}
              className={`option ${subject === item.value ? 'option-active' : ''}`}
              onClick={() => setSubject(item.value)}
            >
              {item.label}
            </View>
          ))}
        </View>

        <Text className="title mt24">年级</Text>
        <View className="options mt16">
          {GRADES.map((item) => (
            <View
              key={item.value}
              className={`option ${grade === item.value ? 'option-active' : ''}`}
              onClick={() => setGrade(item.value)}
            >
              {item.label}
            </View>
          ))}
        </View>
      </View>

      <View
        className={`btn-primary ${images.length === 0 || busy ? 'disabled' : ''}`}
        onClick={images.length === 0 || busy ? undefined : submit}
      >
        {busy ? progressText || '提交中…' : '开始批改'}
      </View>

      <View className="tips muted">
        提交后会自动识别题目、判对错，并把错题收进错题本。
      </View>
    </View>
  )
}
