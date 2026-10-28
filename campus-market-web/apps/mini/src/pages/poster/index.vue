<script setup lang="ts">
/**
 * 商品分享海报（notes/22 S8）：纯前端 canvas 合成，后端零改动。
 *
 * <p>关键字段随 query 从详情页带过来，不再请求一次详情——海报是「一键」动作，多一次网络等待就不一键了。
 *
 * <p>画布用固定像素尺寸（375×560）而不是 rpx：canvas 绘制坐标就是 CSS 像素，用 rpx 会让坐标系
 * 随屏幕宽漂移，同一份代码在宽屏上文字位置全错。导出时用 destWidth/destHeight 放大到 750×1120。
 *
 * <p>底部带二维码回链（指向 H5 商品详情）。码是纯点阵绘制，不依赖网络图片：
 * 主图跨域导致导出失败时会去掉主图重画一次，但二维码照旧——否则海报既没图又没入口。
 */

import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { drawQr, encodeQr } from '@campus/shared'

const CW = 375
const CH = 560
const IMG_H = 280
const PAD = 24
/** 二维码边长（含静区）与底栏高度 */
const QR_BOX = 84
const BAR_H = 115

/** 小程序端没有 location，分享域名走构建期变量；H5 端直接用当前 origin */
const SHARE_BASE = (import.meta.env.VITE_SHARE_BASE as string | undefined) ?? ''

const itemId = ref('')
const title = ref('')
const price = ref('')
const meta = ref('')
const image = ref('')

/** 导出后的图片路径；空表示还在画 */
const posterPath = ref('')
const drawing = ref(true)
/** 有图版导出失败后置真，避免无限重试 */
const degraded = ref(false)

onLoad((q) => {
  const query = q as Record<string, string | undefined>
  itemId.value = query.id ?? ''
  title.value = decodeURIComponent(query.title ?? '')
  price.value = query.price ?? ''
  meta.value = decodeURIComponent(query.meta ?? '')
  image.value = query.image ? decodeURIComponent(query.image) : ''
  uni.setNavigationBarTitle({ title: '分享海报' })
  // 等首帧把 canvas 挂上再画，否则 H5 取不到节点
  setTimeout(() => drawPoster(true, exportImage), 120)
})

/** H5 用当前域名；小程序里 location 不存在，回落到 VITE_SHARE_BASE（未配则二维码不画） */
function shareLink(): string {
  if (!itemId.value) return ''
  let base = SHARE_BASE
  // #ifdef H5
  base = location.origin
  // #endif
  if (!base) return ''
  return `${base}/#/pages/item/detail?id=${itemId.value}`
}

/** 中文按字数估宽断行；混排英文会略窄，视觉上无碍 */
function wrapByLen(text: string, perLine: number, maxLines: number): string[] {
  const lines: string[] = []
  let rest = text
  while (rest && lines.length < maxLines) {
    lines.push(rest.slice(0, perLine))
    rest = rest.slice(perLine)
  }
  if (rest && lines.length === maxLines) {
    lines[maxLines - 1] = lines[maxLines - 1].slice(0, perLine - 1) + '…'
  }
  return lines
}

function drawPoster(withImage: boolean, done: () => void): void {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const ctx: any = uni.createCanvasContext('poster')

  ctx.setFillStyle('#FFFFFF')
  ctx.fillRect(0, 0, CW, CH)

  if (withImage && image.value) {
    ctx.drawImage(image.value, 0, 0, CW, IMG_H)
  } else {
    ctx.setFillStyle('#ECEFF3')
    ctx.fillRect(0, 0, CW, IMG_H)
    ctx.setFillStyle('#9AA3AE')
    ctx.setFontSize(16)
    ctx.setTextAlign('center')
    ctx.fillText('校园二手', CW / 2, IMG_H / 2)
    ctx.setTextAlign('left')
  }

  // 版式自下而上推：先定底栏，再让文案长在底栏之上，避免二维码与标题抢位置
  const barTop = CH - BAR_H
  const metaY = barTop - 24
  const priceY = metaY - 34
  const titleBottom = priceY - 30

  const lines = wrapByLen(title.value || '校园闲置', 15, 2)
  const titleTop = titleBottom - (lines.length - 1) * 25
  ctx.setFillStyle('#1F1F26')
  ctx.setFontSize(18)
  lines.forEach((line, i) => ctx.fillText(line, PAD, titleTop + i * 25))

  ctx.setFillStyle('#FF5500')
  ctx.setFontSize(30)
  ctx.fillText(price.value, PAD, priceY)

  ctx.setFillStyle('#6B6B72')
  ctx.setFontSize(13)
  ctx.fillText(wrapByLen(meta.value, 26, 1)[0] ?? '', PAD, metaY)

  ctx.setStrokeStyle('#EDEDF0')
  ctx.setLineWidth(1)
  ctx.beginPath()
  ctx.moveTo(PAD, barTop)
  ctx.lineTo(CW - PAD, barTop)
  ctx.stroke()

  const matrix = encodeQr(shareLink())
  if (matrix) {
    drawQr(
      {
        fillRect: (x, y, w, h) => ctx.fillRect(x, y, w, h),
        setFill: (color) => ctx.setFillStyle(color)
      },
      matrix,
      CW - PAD - QR_BOX,
      barTop + 14,
      QR_BOX
    )
    ctx.setFillStyle('#8E8E93')
    ctx.setFontSize(11)
    ctx.setTextAlign('center')
    ctx.fillText('扫码查看', CW - PAD - QR_BOX / 2, barTop + 14 + QR_BOX + 15)
    ctx.setTextAlign('left')
  }

  ctx.setFillStyle('#FFD100')
  ctx.fillRect(PAD, barTop + 26, 4, 22)
  ctx.setFillStyle('#1F1F26')
  ctx.setFontSize(15)
  ctx.fillText('校园二手', PAD + 12, barTop + 43)
  ctx.setFillStyle('#8E8E93')
  ctx.setFontSize(12)
  ctx.fillText('同校面交 · 先验货后付款', PAD + 12, barTop + 64)

  ctx.draw(false, done)
}

function exportImage(): void {
  uni.canvasToTempFilePath({
    canvasId: 'poster',
    destWidth: 750,
    destHeight: 1120,
    success: (r) => {
      posterPath.value = r.tempFilePath
      drawing.value = false
    },
    fail: () => {
      // 有图版失败（多为画布被跨域污染）：去掉主图重画一次
      if (!degraded.value) {
        degraded.value = true
        drawPoster(false, exportImage)
      } else {
        drawing.value = false
        uni.showToast({ title: '海报生成失败', icon: 'none' })
      }
    }
  })
}

function save(): void {
  if (!posterPath.value) return
  const platform = (uni.getSystemInfoSync() as { uniPlatform?: string }).uniPlatform ?? ''
  if (platform === 'web') {
    uni.showToast({ title: '长按图片可保存', icon: 'none' })
    return
  }
  uni.saveImageToPhotosAlbum({
    filePath: posterPath.value,
    success: () => uni.showToast({ title: '已保存到相册', icon: 'success' }),
    fail: () => uni.showToast({ title: '保存失败，请允许相册权限', icon: 'none' })
  })
}
</script>

<template>
  <view class="page">
    <view class="stage">
      <view v-if="drawing" class="stage__ph"><text>海报生成中…</text></view>
      <image v-else-if="posterPath" class="stage__img" :src="posterPath" mode="widthFix" show-menu-by-longpress />
      <view v-else class="stage__ph"><text>生成失败，请返回重试</text></view>
    </view>

    <!-- 画布留在文档里但不占位：display:none 会让部分端取不到画布尺寸 -->
    <canvas canvas-id="poster" class="ghost" :style="{ width: CW + 'px', height: CH + 'px' }" />

    <view class="bar">
      <view class="bar__tip"><text>扫码可查看商品，直接发给同学即可</text></view>
      <view class="bar__btn" :class="{ 'bar__btn--off': !posterPath }" @click="save">保存到相册</view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  padding: $cm-space-4 $cm-space-4 calc(180rpx + env(safe-area-inset-bottom));
  background: $cm-color-bg;
}

.stage {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 600rpx;
  background: $cm-color-surface;
  border-radius: $cm-radius-xl;
  overflow: hidden;
}

.stage__img {
  width: 100%;
}

.stage__ph {
  padding: $cm-space-8 0;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.ghost {
  position: absolute;
  left: 0;
  top: 0;
  opacity: 0;
  pointer-events: none;
  z-index: -1;
}

.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: $cm-space-3 $cm-space-4 calc($cm-space-3 + env(safe-area-inset-bottom));
  background: $cm-color-surface;
  border-top: 1rpx solid $cm-color-border;
}

.bar__tip {
  margin-bottom: $cm-space-2;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
  text-align: center;
}

.bar__btn {
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $cm-font-size-base;
  font-weight: 700;
  color: $cm-color-text-1;
  background: $cm-color-brand;
  border-radius: $cm-radius-lg;
}

.bar__btn--off {
  opacity: 0.45;
}
</style>
