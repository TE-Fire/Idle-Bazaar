/**
 * A7 内容管理的五张字典（business/03 F-A7）
 *
 * <p>F-A7 判据：这五张字典是**全端一致性的源头**，必须由后台下发，三端不得本地硬编码。
 * 所以这里虽然是 mock，也要按「后台返回」的形状写（带 id / sort / enabled），
 * 后端 `/admin/dict/*` 就绪后直接替换数据源，页面不用改。
 *
 * <p>其余模块的 mock 都通过 id 引用这些字典（如商品的 categoryId），
 * 改名时会一起变——这正是「字典是源头」的收益，也是为什么它要第一个做。
 */

import type {
  DictCampusItem,
  DictCategoryItem,
  DictCollegeItem,
  DictGradeItem,
  DictMeetPointItem
} from '@campus/shared'

/* ── 校区（dict_campus） ── */

export const CAMPUSES: DictCampusItem[] = [
  { id: 'cp1', name: '中心校区', enabled: true },
  { id: 'cp2', name: '东校区', enabled: true },
  { id: 'cp3', name: '南苑宿舍区', enabled: true },
  { id: 'cp4', name: '西校区', enabled: false }
]

/* ── 学院（dict_college）：报表「学院×品类」矩阵的行 ── */

export const COLLEGES: DictCollegeItem[] = [
  { id: 'co1', name: '计算机学院', campusId: 'cp1', enabled: true },
  { id: 'co2', name: '电子信息学院', campusId: 'cp1', enabled: true },
  { id: 'co3', name: '机械工程学院', campusId: 'cp2', enabled: true },
  { id: 'co4', name: '材料学院', campusId: 'cp2', enabled: true },
  { id: 'co5', name: '化学化工学院', campusId: 'cp2', enabled: true },
  { id: 'co6', name: '数学学院', campusId: 'cp1', enabled: true },
  { id: 'co7', name: '物理学院', campusId: 'cp1', enabled: true },
  { id: 'co8', name: '经济管理学院', campusId: 'cp3', enabled: true },
  { id: 'co9', name: '外国语学院', campusId: 'cp3', enabled: true },
  { id: 'co10', name: '艺术学院', campusId: 'cp3', enabled: true },
  { id: 'co11', name: '法学院', campusId: 'cp1', enabled: true },
  { id: 'co12', name: '医学院', campusId: 'cp4', enabled: false }
]

/* ── 分类树（dict_category）：两级 ── */

export const CATEGORIES: DictCategoryItem[] = [
  { id: 'ca1', name: '教材教辅', parentId: null, sort: 1, enabled: true, itemCount: 186 },
  { id: 'ca101', name: '公共课教材', parentId: 'ca1', sort: 1, enabled: true, itemCount: 72 },
  { id: 'ca102', name: '专业课教材', parentId: 'ca1', sort: 2, enabled: true, itemCount: 88 },
  { id: 'ca103', name: '考研资料', parentId: 'ca1', sort: 3, enabled: true, itemCount: 26 },

  { id: 'ca2', name: '数码电子', parentId: null, sort: 2, enabled: true, itemCount: 143 },
  { id: 'ca201', name: '手机平板', parentId: 'ca2', sort: 1, enabled: true, itemCount: 51 },
  { id: 'ca202', name: '电脑配件', parentId: 'ca2', sort: 2, enabled: true, itemCount: 63 },
  { id: 'ca203', name: '影音设备', parentId: 'ca2', sort: 3, enabled: true, itemCount: 29 },

  { id: 'ca3', name: '生活用品', parentId: null, sort: 3, enabled: true, itemCount: 212 },
  { id: 'ca301', name: '宿舍家具', parentId: 'ca3', sort: 1, enabled: true, itemCount: 97 },
  { id: 'ca302', name: '电器小家电', parentId: 'ca3', sort: 2, enabled: true, itemCount: 84 },
  { id: 'ca303', name: '日用杂物', parentId: 'ca3', sort: 3, enabled: true, itemCount: 31 },

  { id: 'ca4', name: '服饰鞋包', parentId: null, sort: 4, enabled: true, itemCount: 98 },
  { id: 'ca5', name: '运动户外', parentId: null, sort: 5, enabled: true, itemCount: 76 },
  { id: 'ca501', name: '自行车', parentId: 'ca5', sort: 1, enabled: true, itemCount: 44 },
  { id: 'ca502', name: '球类器材', parentId: 'ca5', sort: 2, enabled: true, itemCount: 32 },

  { id: 'ca6', name: '图书笔记', parentId: null, sort: 6, enabled: true, itemCount: 121 },
  { id: 'ca7', name: '乐器', parentId: null, sort: 7, enabled: true, itemCount: 34 },
  { id: 'ca8', name: '其他闲置', parentId: null, sort: 8, enabled: true, itemCount: 57 }
]

/** 成色 A–D（dict_grade）：配色取 design/01 §4.1，A/B 绿、C 橙、D 灰 */

export const GRADES: DictGradeItem[] = [
  {
    code: 'A',
    label: '全新未拆',
    desc: '未拆封 / 未使用，配件齐全',
    color: '#1D9E75',
    enabled: true
  },
  {
    code: 'B',
    label: '几乎全新',
    desc: '使用极少，无明显痕迹',
    color: '#34C759',
    enabled: true
  },
  {
    code: 'C',
    label: '轻微使用痕迹',
    desc: '有轻微磨损，功能完好',
    color: '#FF9F0A',
    enabled: true
  },
  {
    code: 'D',
    label: '明显使用痕迹',
    desc: '外观明显磨损，功能正常',
    color: '#8E8E93',
    enabled: true
  }
]

/* ── 面交点（dict_meet_point） ── */

export const MEET_POINTS: DictMeetPointItem[] = [
  { id: 'mp1', name: '中心校区·一食堂门口', campusId: 'cp1', openHours: '07:00–22:00', enabled: true },
  { id: 'mp2', name: '中心校区·图书馆东门', campusId: 'cp1', openHours: '08:00–22:30', enabled: true },
  { id: 'mp3', name: '中心校区·体育馆南侧', campusId: 'cp1', openHours: '06:30–21:30', enabled: true },
  { id: 'mp4', name: '东校区·三舍架空层', campusId: 'cp2', openHours: '全天', enabled: true },
  { id: 'mp5', name: '东校区·教学楼中庭', campusId: 'cp2', openHours: '07:30–21:00', enabled: true },
  { id: 'mp6', name: '南苑宿舍区·快递驿站旁', campusId: 'cp3', openHours: '09:00–21:00', enabled: true },
  { id: 'mp7', name: '南苑宿舍区·校门内侧', campusId: 'cp3', openHours: '07:00–23:00', enabled: false }
]

/* ── 取名用的索引：mock 里到处要 id → name ── */

export const campusName = (id: string): string =>
  CAMPUSES.find((c) => c.id === id)?.name ?? '未知校区'

export const collegeName = (id: string): string =>
  COLLEGES.find((c) => c.id === id)?.name ?? '未知学院'

export const categoryName = (id: string): string =>
  CATEGORIES.find((c) => c.id === id)?.name ?? '未分类'

export const meetPointName = (id: string): string =>
  MEET_POINTS.find((m) => m.id === id)?.name ?? '未指定'

/** 一级分类（筛选下拉与报表的品类轴都用它，避免把二级也混进去） */
export const ROOT_CATEGORIES = CATEGORIES.filter((c) => c.parentId === null)
