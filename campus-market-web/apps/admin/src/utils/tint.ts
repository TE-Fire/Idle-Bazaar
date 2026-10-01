/**
 * 渐变占位的内联样式（骨架期）
 *
 * <p>MinIO 与上传接口就位前，商品图 / 头像 / 证件都用 `mock/tints.ts` 里的两色渐变代替。
 * 这里是把它转成 CSS 内联样式的小工具，集中一处是为了将来换成
 * `background-image: url(...)` 时只改这一个函数。
 */

export const tintStyle = (tints: readonly [string, string]): { background: string } => ({
  background: `linear-gradient(135deg, ${tints[0]}, ${tints[1]})`
})
