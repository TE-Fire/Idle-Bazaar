/**
 * 二维码点阵（海报扫码回链，notes/22 二期）。
 *
 * <p>为什么放在 shared 而不是各端各写一份：同一张海报在 Web 与小程序里都必须扫得出同一个地址，
 * 两份实现迟早会因为版本、字符集或纠错级别漂移。这里只产出**点阵**，绘制由各端自己做——
 * Web 是 `CanvasRenderingContext2D`，小程序是 `uni.createCanvasContext` 返回的旧版上下文，
 * 两者取色方式不同，共用一个绘制函数反而要写一堆分支。
 *
 * <p>依赖 `qrcode-generator`（MIT，无运行时依赖）：自己写 RS 纠错与掩码选择容易在边界
 * （中文、URL 长度跨版本）出错，而二维码出错的代价是「海报发出去了但扫不开」。
 */

import qrcode from 'qrcode-generator'

/** 二维码点阵：只暴露「多大」与「哪格是黑」，不暴露内部实现 */
export interface QrMatrix {
  /** 每边格子数（不含静区） */
  size: number
  isDark(row: number, col: number): boolean
}

/**
 * 绘制入参。两端取色方式不同（Web 是 `ctx.fillStyle = c`，小程序是 `ctx.setFillStyle(c)`），
 * 所以不让调用方传原始上下文，而是传两个已经绑好方法的小函数。
 */
export interface QrPen {
  fillRect(x: number, y: number, w: number, h: number): void
  setFill(color: string): void
}

/**
 * 生成点阵。文本过长（超出 40 版本容量）时返回 null，调用方按「不画码」降级。
 *
 * 纠错级别取 M（15%）：海报会被截图、压缩、斜面拍摄，L 级（7%）在这些场景下扫不出；
 * 提到 Q/H 又会让码变密，小尺寸下反而更难扫。
 */
export function encodeQr(text: string, ec: 'L' | 'M' | 'Q' | 'H' = 'M'): QrMatrix | null {
  if (!text) return null
  try {
    const qr = qrcode(0, ec)
    qr.addData(text, 'Byte')
    qr.make()
    const size = qr.getModuleCount()
    return { size, isDark: (row, col) => qr.isDark(row, col) }
  } catch {
    return null
  }
}

/**
 * 在画布上画二维码（先铺白底再逐格填黑），返回实际占用的边长。
 *
 * <p>逐格 `fillRect` 而不是 `createDataURL` 贴图：小程序 canvas 1.0 没有
 * `renderTo2dContext`，把 dataURL 转成临时文件又要走异步导出，逐格画是唯一两端都稳的路子。
 * 单次调用约 30×30 次，同步执行，代价可忽略。
 *
 * @param box   期望边长（含静区），实际边长会按格子取整
 * @param quiet 静区格数，标准要求 ≥4；贴在海报白底上取 2 已足够被识别
 */
export function drawQr(
  pen: QrPen,
  matrix: QrMatrix,
  x: number,
  y: number,
  box: number,
  quiet = 2
): number {
  const cells = matrix.size + quiet * 2
  const unit = Math.max(1, Math.floor(box / cells))
  const side = unit * cells

  pen.setFill('#FFFFFF')
  pen.fillRect(x, y, side, side)

  pen.setFill('#1F1F26')
  for (let r = 0; r < matrix.size; r += 1) {
    for (let c = 0; c < matrix.size; c += 1) {
      if (matrix.isDark(r, c)) {
        pen.fillRect(
          x + (c + quiet) * unit,
          y + (r + quiet) * unit,
          unit,
          unit
        )
      }
    }
  }
  return side
}
