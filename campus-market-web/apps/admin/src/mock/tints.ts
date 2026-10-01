/**
 * 图片占位渐变（骨架期）
 *
 * <p>MinIO 与上传接口就位前，商品图、头像、证件都用「两色渐变块」代替。
 * 集中在一处是为了让同一条数据在不同页面（列表 / 抽屉 / 报表）颜色一致，
 * 也方便将来一次性替换成真实 URL。
 *
 * <p>取色沿用 charts/theme.ts 的 Apple 色板，但降到低饱和以免抢文字的注意力。
 */

export const TINT_PAIRS: ReadonlyArray<[string, string]> = [
  ['#DCE9F7', '#B5D4F4'],
  ['#DDF3E4', '#C0DD97'],
  ['#FBEEDC', '#FAC775'],
  ['#EFE4F7', '#CECBF6'],
  ['#DCEFF5', '#9FE1CB'],
  ['#FBE3E3', '#F7C1C1'],
  ['#E8E8ED', '#D3D1C7'],
  ['#E4EEFB', '#85B7EB']
]

/** 按字符串稳定取一对渐变：同一个 id 永远拿到同一组颜色，刷新不变 */
export const tintFor = (seed: string): [string, string] => {
  let hash = 0
  for (let i = 0; i < seed.length; i += 1) hash = (hash * 31 + seed.charCodeAt(i)) >>> 0
  return TINT_PAIRS[hash % TINT_PAIRS.length]
}
