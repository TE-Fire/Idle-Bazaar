<script setup lang="ts">
/**
 * 商品分享海报（notes/22 S8）：纯前端 canvas 合成，后端零改动。
 *
 * <p>三个约束：
 *   ① 不含二维码（扫码回链是二期），所以底部只放平台名与一句信任话术；
 *   ② 主图跨域加载失败、或图片把画布污染导致 toDataURL 抛错时，**降级成渐变色块**照常出图——
 *      海报是分享物料，宁可少一张图也不能让用户点完没反应；
 *   ③ 尺寸固定 750×1000（3:4），微信/小红书都吃这个比例。
 */

import { onMounted, ref } from 'vue'
import { CONDITION_LABELS, formatPrice } from '@campus/shared'
import type { ApiItemVO } from '@campus/api'

const props = defineProps<{ item: ApiItemVO }>()
const emit = defineEmits<{ close: [] }>()

const W = 750
const H = 1000
const IMG_H = 560
const PAD = 48

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

/** 文字区：标题、价格、元信息、底部品牌条 */
function paintText(ctx: CanvasRenderingContext2D): void {
  const it = props.item
  let y = IMG_H + 76

  ctx.textAlign = 'left'
  ctx.fillStyle = '#1F1F26'
  ctx.font = '600 36px "PingFang SC","Microsoft YaHei",sans-serif'
  const lines = wrap(ctx, it.title || '校园闲置', W - PAD * 2, 2)
  for (const line of lines) {
    ctx.fillText(line, PAD, y)
    y += 50
  }

  y += 22
  ctx.fillStyle = '#FF5500'
  ctx.font = '700 60px "PingFang SC","Microsoft YaHei",sans-serif'
  const price = formatPrice(Number(it.price))
  ctx.fillText(price, PAD, y)
  const priceW = ctx.measureText(price).width
  ctx.fillStyle = '#8E8E93'
  ctx.font = '400 26px "PingFang SC","Microsoft YaHei",sans-serif'
  ctx.fillText('同校面交', PAD + priceW + 20, y - 6)

  y += 56
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
  ctx.fillText(meta, PAD, y)

  // 分隔线
  ctx.strokeStyle = '#EDEDF0'
  ctx.lineWidth = 2
  ctx.beginPath()
  ctx.moveTo(PAD, H - 150)
  ctx.lineTo(W - PAD, H - 150)
  ctx.stroke()

  // 底部品牌条
  ctx.fillStyle = '#FFD100'
  ctx.fillRect(PAD, H - 112, 8, 44)
  ctx.fillStyle = '#1F1F26'
  ctx.font = '700 30px "PingFang SC","Microsoft YaHei",sans-serif'
  ctx.fillText('校园二手', PAD + 24, H - 78)
  ctx.fillStyle = '#8E8E93'
  ctx.font = '400 24px "PingFang SC","Microsoft YaHei",sans-serif'
  ctx.fillText('同校面交 · 先验货后付款', PAD + 24, H - 42)
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
