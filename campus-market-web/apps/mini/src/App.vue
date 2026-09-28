<script setup lang="ts">
/**
 * 小程序根组件
 *
 * <p>页面栈最多 10 层（design/02 §二）——详情页 → 卖家主页 → 商品详情 这条链
 * 必须用 `redirectTo` 截断，否则用户来回点几次就再也跳不动了。
 * 该约定落在 `pages/item/detail.vue` 的跳转实现里。
 */
</script>

<style lang="scss">
/* 全局基础样式放这里（uni.scss 只放变量），页面样式各自 scoped。
   必须用 scss：$cm-* 变量由 uni.scss 注入，纯 CSS 块拿不到。 */
page {
  background: $cm-color-bg;
  color: $cm-color-text-1;
  /* 令牌里的尺寸已被 ×2 转 rpx，这里拿到的是 28rpx */
  font-size: $cm-font-size-base;
  font-family: -apple-system, 'PingFang SC', sans-serif;
}
</style>
