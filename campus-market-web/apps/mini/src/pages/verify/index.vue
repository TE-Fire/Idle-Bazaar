<script setup lang="ts">
/**
 * M10 学号认证（business/02 F-M5，规则见 notes/20 D5）
 *
 * <p>页面分三种状态：已认证只展示结果；审核中展示等待提示；未认证/未通过给表单，
 * 未通过时把管理员写的原因原样摆出来——那句话是学生能不能一次改对的关键。
 *
 * <p>学号填错不用怕：提交阶段不校验唯一性，被驳回后改一下再提交即可（每次提交留一条记录）。
 */

import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { userAuthApplyApi, userDictApi, type UserAuthApplyVO } from '@/api/user'
import { getSession } from '@/utils/session'
import { uploadImage } from '@/utils/upload'

/** app_user.auth_status：0未认证 1审核中 2已认证 3未通过 */
const authStatus = ref<number>(getSession().authStatus ?? 0)
const latest = ref<UserAuthApplyVO | null>(null)
const colleges = ref<Array<{ id: string; name: string }>>([])
const submitting = ref(false)
const uploading = ref(false)

const form = reactive({
  realName: '',
  studentNo: '',
  collegeId: '',
  credentialUrl: ''
})

const collegeIndex = computed(() => colleges.value.findIndex((c) => c.id === form.collegeId))

/** 审核中：以服务端最近一次申请为准，避免本地会话状态滞后 */
const pending = computed(() => authStatus.value === 1 || latest.value?.status === 1)
const passed = computed(() => authStatus.value === 2)
const rejected = computed(() => !pending.value && !passed.value && (authStatus.value === 3 || latest.value?.status === 3))

const rejectReason = computed(() =>
  latest.value?.status === 3 ? (latest.value.rejectReason ?? '') : ''
)

onLoad(() => {
  void userDictApi
    .all()
    .then((d) => {
      colleges.value = d.colleges ?? []
    })
    .catch(() => undefined)

  // 最近一次申请回填：已认证/审核中拿它展示，未通过拿它显示原因
  void userAuthApplyApi
    .latest()
    .then((row) => {
      latest.value = row
      if (row) {
        form.realName = row.realName
        form.studentNo = row.studentNo
        form.collegeId = row.collegeId
        form.credentialUrl = row.credentialUrl ?? ''
      }
    })
    .catch(() => undefined)
})

/** 学院选择：uni picker 返回下标 */
function onCollegePick(e: { detail: { value: number } }): void {
  const c = colleges.value[e.detail.value]
  if (c) form.collegeId = c.id
}

/** 凭证：一卡通或校园邮箱截图，选填；走既有票据上传拿 URL */
function chooseCredential(): void {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: (res) => {
      const path = res.tempFilePaths[0]
      const files = res.tempFiles as Array<{ size: number }> | undefined
      const size = files?.[0]?.size ?? 0
      if (!path) return
      uploading.value = true
      uploadImage(path, size)
        .then((url) => {
          form.credentialUrl = url
          uni.showToast({ title: '已上传', icon: 'none' })
        })
        .catch(() => undefined)
        .finally(() => (uploading.value = false))
    }
  })
}

function submit(): void {
  if (!getSession().tokenValue) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    void uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  if (!form.realName.trim()) {
    uni.showToast({ title: '请填写真实姓名', icon: 'none' })
    return
  }
  if (!form.studentNo.trim()) {
    uni.showToast({ title: '请填写学号', icon: 'none' })
    return
  }
  if (!form.collegeId) {
    uni.showToast({ title: '请选择学院', icon: 'none' })
    return
  }
  if (submitting.value) return
  submitting.value = true
  userAuthApplyApi
    .apply({
      realName: form.realName.trim(),
      studentNo: form.studentNo.trim(),
      collegeId: form.collegeId,
      credentialUrl: form.credentialUrl || undefined
    })
    .then(() => {
      authStatus.value = 1
      uni.showToast({ title: '已提交，等待审核', icon: 'none' })
      void userAuthApplyApi.latest().then((row) => (latest.value = row)).catch(() => undefined)
    })
    .catch(() => undefined)
    .finally(() => (submitting.value = false))
}
</script>

<template>
  <view class="verify">
    <!-- 已认证：只展示结果，不再给表单 -->
    <view v-if="passed" class="card card--ok">
      <view class="card__badge">已认证</view>
      <text class="card__title">你已完成学号认证</text>
      <text class="card__sub">
        {{ latest?.realName ?? '' }} · {{ latest?.studentNo ?? '' }}
      </text>
      <text class="card__note">认证标识会显示在你的商品卡与个人主页上；真实姓名与学号不对外展示。</text>
    </view>

    <!-- 审核中 -->
    <view v-else-if="pending" class="card card--wait">
      <view class="card__badge">审核中</view>
      <text class="card__title">申请已提交，等待管理员审核</text>
      <text class="card__sub">一般 1 个工作日内处理，通过后商品卡会出现认证标识。</text>
      <view class="card__row">
        <text class="card__k">姓名</text>
        <text class="card__v">{{ latest?.realName ?? form.realName }}</text>
      </view>
      <view class="card__row">
        <text class="card__k">学号</text>
        <text class="card__v">{{ latest?.studentNo ?? form.studentNo }}</text>
      </view>
    </view>

    <!-- 表单：未认证 / 未通过 -->
    <view v-else class="form">
      <view v-if="rejected" class="reject">
        <text class="reject__t">上次认证未通过</text>
        <text class="reject__r">{{ rejectReason || '未填写原因，请核对信息后重新提交' }}</text>
      </view>

      <view class="field">
        <text class="field__label">真实姓名</text>
        <input v-model="form.realName" class="field__input" placeholder="与一卡通一致" maxlength="32" />
      </view>

      <view class="field">
        <text class="field__label">学号</text>
        <input v-model="form.studentNo" class="field__input" placeholder="校内学号" maxlength="32" />
      </view>

      <view class="field">
        <text class="field__label">学院</text>
        <picker mode="selector" :range="colleges" range-key="name" :value="collegeIndex" @change="onCollegePick">
          <view class="field__input field__input--pick">
            {{ form.collegeId ? colleges.find((c) => c.id === form.collegeId)?.name : '请选择学院' }}
          </view>
        </picker>
      </view>

      <view class="field">
        <text class="field__label">认证凭证（选填）</text>
        <view class="upload" @click="chooseCredential">
          <image v-if="form.credentialUrl" class="upload__img" :src="form.credentialUrl" mode="aspectFill" />
          <text v-else class="upload__hint">{{ uploading ? '上传中…' : '一卡通或校园邮箱截图' }}</text>
        </view>
      </view>

      <view class="submit" hover-class="submit--active" @click="submit">
        {{ submitting ? '提交中…' : '提交认证' }}
      </view>

      <text class="tip">
        认证信息仅用于身份核验，真实姓名与学号不会公开展示。提交后如需修改，等审核结果出来再重提。
      </text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.verify {
  min-height: 100vh;
  padding: $cm-space-4 $cm-space-4 calc(#{$cm-space-6} + env(safe-area-inset-bottom));
  background: $cm-color-bg;
  box-sizing: border-box;
}

.card {
  padding: $cm-space-5;
  border-radius: $cm-radius-lg;
  background: $cm-color-surface;
}

.card--ok {
  border: 1rpx solid rgba(15, 159, 104, 0.24);
}

.card--wait {
  border: 1rpx solid rgba(58, 122, 235, 0.24);
}

.card__badge {
  display: inline-block;
  padding: 4rpx 16rpx;
  border-radius: $cm-radius-pill;
  background: rgba(15, 159, 104, 0.12);
  color: #0f9f68;
  font-size: 22rpx;
}

.card--wait .card__badge {
  background: rgba(58, 122, 235, 0.12);
  color: #3a7aeb;
}

.card__title {
  display: block;
  margin-top: 16rpx;
  font-size: 32rpx;
  font-weight: $cm-font-weight-bold;
  color: $cm-color-text-1;
}

.card__sub {
  display: block;
  margin-top: 8rpx;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
}

.card__note {
  display: block;
  margin-top: 20rpx;
  font-size: 22rpx;
  color: $cm-color-text-3;
  line-height: 1.6;
}

.card__row {
  display: flex;
  justify-content: space-between;
  margin-top: 16rpx;
  font-size: $cm-font-size-sm;
}

.card__k {
  color: $cm-color-text-3;
}

.card__v {
  color: $cm-color-text-1;
}

.form {
  padding: $cm-space-4;
  border-radius: $cm-radius-lg;
  background: $cm-color-surface;
}

.reject {
  padding: $cm-space-3;
  margin-bottom: $cm-space-4;
  border-radius: $cm-radius-md;
  background: rgba(232, 84, 63, 0.08);
}

.reject__t {
  display: block;
  font-size: $cm-font-size-sm;
  font-weight: $cm-font-weight-medium;
  color: #c9432f;
}

.reject__r {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #a63a29;
  line-height: 1.6;
}

.field {
  margin-bottom: $cm-space-4;
}

.field__label {
  display: block;
  margin-bottom: 10rpx;
  font-size: 24rpx;
  color: $cm-color-text-3;
}

.field__input {
  width: 100%;
  height: 88rpx;
  padding: 0 $cm-space-3;
  border-radius: $cm-radius-md;
  background: $cm-color-bg;
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
  box-sizing: border-box;
}

.field__input--pick {
  display: flex;
  align-items: center;
}

.upload {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 240rpx;
  border-radius: $cm-radius-md;
  background: $cm-color-bg;
  overflow: hidden;
}

.upload__img {
  width: 100%;
  height: 100%;
}

.upload__hint {
  font-size: 24rpx;
  color: $cm-color-text-3;
}

.submit {
  margin-top: $cm-space-5;
  height: 92rpx;
  line-height: 92rpx;
  text-align: center;
  border-radius: $cm-radius-pill;
  background: $cm-color-brand;
  color: #1b1b21;
  font-size: $cm-font-size-md;
  font-weight: $cm-font-weight-medium;
}

.submit--active {
  opacity: 0.86;
}

.tip {
  display: block;
  margin-top: $cm-space-4;
  font-size: 22rpx;
  line-height: 1.7;
  color: $cm-color-text-3;
}
</style>
