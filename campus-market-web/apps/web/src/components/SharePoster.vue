<script setup lang="ts">
/**
 * 商品分享海报（notes/22 S8）：纯前端 canvas 合成，后端零改动。
 *
 * <p>四个约束：
 *   ① 底部带二维码回链，指向本商品详情页——扫开即可下单，海报才不只是张图片；
 *      二维码是纯前端点阵绘制（`@campus/shared` 的 `encodeQr`），不需要后端签小程序码；
 *   ② 主图跨域加载失败、或图片把画布污染导致 toDataURL 抛错时，**降级成渐变色块**照常出图——
 *      海报是分享物料，宁可少一张图也不能让用户点完没反应；
 *   ③ 画布污染时二维码仍要画（它是 fillRect，不涉及跨域图片），否则连回链都没了；
 *   ④ 尺寸固定 750×1000（3:4），微信/小红书都吃这个比例。
 */

import { onMounted, ref } from 'vue'
import { CONDITION_LABELS, drawQr, encodeQr, formatPrice } from '@campus/shared'
import type { ApiItemVO } from '@campus/api'

const props = defineProps<{ item: ApiItemVO }>()
const emit = defineEmits<{ close: [] }>()

const W = 750
const H = 1100
const IMG_H = 560
const PAD = 48
/** 底栏：二维码 168 + 上方留白 + 下方说明 */
const QR_BOX = 168
const BAR_H = 240

const poster = ref('')
const drawing = ref(true)

onMounted(() => {
  void draw()
})

/** 带 CORS 头加载；失败返回 null，由调用方降级 */
function loadImage(src: string): Promise<HTMLImageElement | null> {
  return new Promise((resolve) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => resolve(img)
    img.onerror = () => resolve(null)
    img.src = src
  })
}

/** 按像素宽度断行（中文无空格，只能逐字量） */
function wrap(ctx: CanvasRenderingContext2D, text: string, maxWidth: number, maxLines: number): string[] {
  const lines: string[] = []
  let cur = ''
  for (const ch of text) {
    if (ctx.measureText(cur + ch).width > maxWidth) {
      lines.push(cur)
      cur = ch
      if (lines.length === maxLines) break
    } else {
      cur += ch
    }
  }
  if (lines.length < maxLines && cur) lines.push(cur)
  // 超出行数：末行截断加省略号
  if (lines.length === maxLines && lines.join('').length < text.length) {
    let last = lines[maxLines - 1]
    while (last && ctx.measureText(last + '…').width > maxWidth) last = last.slice(0, -1)
    lines[maxLines - 1] = last + '…'
  }
  return lines
}

function paintBase(ctx: CanvasRenderingContext2D): void {
  ctx.fillStyle = '#FFFFFF'
  ctx.fillRect(0, 0, W, H)
}

function paintImage(ctx: CanvasRenderingContext2D, img: HTMLImageElement): void {
  const scale = Math.max(W / img.width, IMG_H / img.height)
  const dw = img.width * scale
  const dh = img.height * scale
  ctx.save()
  ctx.beginPath()
  ctx.rect(0, 0, W, IMG_H)
  ctx.clip()
  ctx.drawImage(img, (W - dw) / 2, (IMG_H - dh) / 2, dw, dh)
  ctx.restore()
}

function paintPlaceholder(ctx: CanvasRenderingContext2D): void {
  const g = ctx.createLinearGradient(0, 0, W, IMG_H)
  g.addColorStop(0, '#ECEFF3')
  g.addColorStop(1, '#C9D2DC')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, W, IMG_H)
  ctx.fillStyle = 'rgba(255,255,255,.75)'
  ctx.font = '600 30px "PingFang SC","Microsoft YaHei",sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText('校园二手', W / 2, IMG_H / 2)
  ctx.textAlign = 'left'
}

/** 二维码指向的地址：Web 端是 history 路由的商品详情（无 #），扫码即落到当前部署 */
function shareLink(): string {
  if (!props.item.id || typeof window === 'undefined') return ''
  return `${window.location.origin}/item/${props.item.id}`
}

/** 文字区：标题、价格、元信息、底部品牌条 + 扫码回链 */
function paintText(ctx: CanvasRenderingContext2D): void {
  const it = props.item
  // 版式自下而上推：先定底栏，再让文案长在底栏之上，二维码与标题不会抢位置
  const barTop = H - BAR_H
  const metaY = barTop - 44
  const priceY = metaY - 64
  const titleBottom = priceY - 56

  ctx.textAlign = 'left'
  ctx.fillStyle = '#1F1F26'
  ctx.font = '600 36px "PingFang SC","Microsoft YaHei",sans-serif'
  const lines = wrap(ctx, it.title || '校园闲置', W - PAD * 2, 2)
  const titleTop = titleBottom - (lines.length - 1) * 50
  lines.forEach((line, i) => ctx.fillText(line, PAD, titleTop + i * 50))

  ctx.fillStyle = '#FF5500'
  ctx.font = '700 60px "PingFang SC","Microsoft YaHei",sans-serif'
  const price = formatPrice(Number(it.price))
  ctx.fillText(price, PAD, priceY)
  const priceW = ctx.measureText(price).width
  ctx.fillStyle = '#8E8E93'
  ctx.font = '400 26px "PingFang SC","Microsoft YaHei",sans-serif'
  ctx.fillText('同校面交', PAD + priceW + 20, priceY - 6)

  const gradeText = CONDITION_LABELS[it.grade as 'A']
  const meta = [
    it.grade ? `${it.grade} 级${gradeText ? ` · ${gradeText}` : ''}` : '',
    it.campusName ?? '',
    it.meetPointName ?? ''
  ]
    .filter(Boolean)
    .join(' · ')
  ctx.fillStyle = '#6B6B72'
  ctx.font = '400 26px "PingFang SC","Microsoft YaHei",sans-serif'
  ctx.fillText(meta, PAD, metaY)

  ctx.strokeStyle = '#EDEDF0'
  ctx.lineWidth = 2
  ctx.beginPath()
  ctx.moveTo(PAD, barTop)
  ctx.lineTo(W - PAD, barTop)
  ctx.stroke()

  const qrX = W - PAD - QR_BOX
  const qrY = barTop + 24
  const matrix = encodeQr(shareLink())
  if (matrix) {
    drawQr(
      {
        fillRect: (x, y, w, h) => ctx.fillRect(x, y, w, h),
        setFill: (color) => {
          ctx.fillStyle = color
        }
      },
      matrix,
      qrX,
      qrY,
      QR_BOX
    )
    ctx.fillStyle = '#8E8E93'
    ctx.font = '400 20px "PingFang SC","Microsoft YaHei",sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText('扫码查看', qrX + QR_BOX / 2, qrY + QR_BOX + 30)
    ctx.textAlign = 'left'
  }

  // 品牌条：有码时收窄到左侧，用裁剪兜住长文案，避免压到二维码
  const brandW = matrix ? qrX - PAD - 16 : W - PAD * 2
  ctx.save()
  ctx.beginPath()
  ctx.rect(PAD, barTop, brandW, 240)
  ctx.clip()
  ctx.fillStyle = '#FFD100'
  ctx.fillRect(PAD, barTop + 40, 8, 44)
  ctx.fillStyle = '#1F1F26'
  ctx.font = '700 30px "PingFang SC","Microsoft YaHei",sans-serif'
  ctx.fillText('校园二手', PAD + 24, barTop + 74)
  ctx.fillStyle = '#8E8E93'
  ctx.font = '400 24px "PingFang SC","Microsoft YaHei",sans-serif'
  ctx.fillText('同校面交 · 先验货后付款', PAD + 24, barTop + 110)
  ctx.restore()
}

async function draw(): Promise<void> {
  drawing.value = true
  const canvas = document.createElement('canvas')
  canvas.width = W
  canvas.height = H
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    drawing.value = false
    return
  }

  const src = props.item.mainImage
  const img = src ? await loadImage(src) : null

  const render = (useImage: boolean): string => {
    paintBase(ctx)
    if (useImage && img) paintImage(ctx, img)
    else paintPlaceholder(ctx)
    paintText(ctx)
    return canvas.toDataURL('image/png')
  }

  try {
    // 有主图就走带图版本；画布被污染时 toDataURL 会抛，落到下面的降级分支
    poster.value = render(Boolean(img))
  } catch {
    try {
      poster.value = render(false)
    } catch {
      poster.value = ''
    }
  } finally {
    drawing.value = false
  }
}

function download(): void {
  if (!poster.value) return
  const a = document.createElement('a')
  a.href = poster.value
  a.download = `${props.item.title || '校园好物'}-海报.png`
  a.click()
}
</script>

<template>
  <div class="poster-mask" @click.self="emit('close')">
    <div class="poster">
      <div class="poster__hd">
        <h3>分享海报</h3>
        <button type="button" class="poster__x" @click="emit('close')">✕</button>
      </div>

      <div class="poster__body">
        <div v-if="drawing" class="poster__ph">海报生成中…</div>
        <img v-else-if="poster" class="poster__img" :src="poster" alt="商品海报" />
        <div v-else class="poster__ph">生成失败，请稍后再试</div>
      </div>

      <div class="poster__ft">
        <span class="poster__hint">长按图片保存，或直接下载</span>
        <el-button type="primary" :disabled="!poster" @click="download">下载海报</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.poster-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--cm-space-4);
  background: rgba(0, 0, 0, 0.45);
}

.poster {
  width: 100%;
  max-width: 420px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  padding: var(--cm-space-4);
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-2xl);
  box-shadow: var(--cm-shadow-2);
}

.poster__hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--cm-space-3);
}

.poster__hd h3 {
  margin: 0;
  font-size: var(--cm-font-size-md);
  font-weight: 600;
}

.poster__x {
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 50%;
  background: var(--cm-color-bg);
  color: var(--cm-color-text-3);
  cursor: pointer;
  font-size: 12px;
}

.poster__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  border-radius: var(--cm-radius-md);
  background: var(--cm-color-bg);
}

.poster__img {
  display: block;
  width: 100%;
  border-radius: var(--cm-radius-md);
}

.poster__ph {
  padding: var(--cm-space-8) var(--cm-space-4);
  text-align: center;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.poster__ft {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--cm-space-3);
}

.poster__hint {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}
</style>
