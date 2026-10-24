/**
 * tabBar 页的跳转意图传递。
 *
 * <p>switchTab 不能带 query（微信侧直接丢弃），首页金刚区要带着「选中哪个一级分类」
 * 进分类页，只能用模块级引用做一次性意图：写入方赋值，读取方 onShow 消费后立即清空，
 * 避免下次无意进入分类页时误选。
 */

import { ref } from 'vue'

/** 待选中一级分类 id；空串 = 无意图（走分类页默认选中第一个） */
export const pendingCategoryId = ref('')
