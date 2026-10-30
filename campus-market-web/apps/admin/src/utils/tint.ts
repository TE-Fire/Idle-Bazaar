/**
 * 头像占位渐变的内联样式
 *
 * <p>学生没有头像时按用户 id 取一组两色渐变画底色，比灰圆更像「有人」。
 * 抽成一个函数是为了将来统一换成 `background-image: url(...)` 时只改这一处。
 */

export const tintStyle = (tints: readonly [string, string]): { background: string } => ({
  background: `linear-gradient(135deg, ${tints[0]}, ${tints[1]})`
})
