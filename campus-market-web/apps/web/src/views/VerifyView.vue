<script setup lang="ts">
/**
 * P12 学号认证（business/01 W-B9，规则见 notes/20 D5）
 *
 * <p>三种状态：已认证只展示结果、审核中给等待提示、未认证/未通过给表单。
 * 未通过时把管理员写的原因原样摆出来——那句话是学生能不能一次改对的关键。
 *
 * <p>学号唯一性不在提交时校验：填错可以重提，通过那一刻才查重。
 */

import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  checkImageFile,
  uploadImage,
  userAuthApplyApi,
  userDictApi,
  type UserAuthApplyVO
} from '@campus/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const latest = ref<UserAuthApplyVO | null>(null)
const colleges = ref<Array<{ id: string; name: string }>>([])
const submitting = ref(false)
const uploading = ref(false)
const form = reactive({ realName: '', studentNo: '', collegeId: '', credentialUrl: '' })

const passed = computed(() => userStore.authStatus === 2)
const pending = computed(() => !passed.value && (userStore.authStatus === 1 || latest.value?.status === 1))
const rejected = computed(
  () => !passed.value && !pending.value && (userStore.authStatus === 3 || latest.value?.status === 3)
)
const rejectReason = computed(() => (latest.value?.status === 3 ? latest.value.rejectReason ?? '' : ''))

onMounted(async () => {
  try {
    const d = await userDictApi.all()
    colleges.value = d.colleges ?? []
  } catch {
    // 字典拉不到时表单仍可填，只是学院选不了
  }
  try {
    const row = await userAuthApplyApi.latest()
    latest.value = row
    if (row) {
      form.realName = row.realName
      form.studentNo = row.studentNo
      form.collegeId = row.collegeId
      form.credentialUrl = row.credentialUrl ?? ''
    }
  } catch {
    // 未登录由 http 层跳登录
  }
})

async function onPick(file: File | undefined): Promise<void> {
  if (!file) return
  const err = checkImageFile(file)
  if (err) {
    ElMessage.warning(err)
    return
  }
  uploading.value = true
  try {
    form.credentialUrl = await uploadImage(file)
    ElMessage.success('凭证已上传')
  } finally {
    uploading.value = false
  }
}

async function submit(): Promise<void> {
  if (!form.realName.trim()) {
    ElMessage.warning('请填写真实姓名')
    return
  }
  if (!form.studentNo.trim()) {
    ElMessage.warning('请填写学号')
    return
  }
  if (!form.collegeId) {
    ElMessage.warning('请选择学院')
    return
  }
  submitting.value = true
  try {
    await userAuthApplyApi.apply({
      realName: form.realName.trim(),
      studentNo: form.studentNo.trim(),
      collegeId: form.collegeId,
      credentialUrl: form.credentialUrl || undefined
    })
    ElMessage.success('已提交，等待管理员审核')
    await userStore.refresh()
    latest.value = await userAuthApplyApi.latest()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="cm-shell verify">
    <header class="head">
      <h1 class="head__title">学号认证</h1>
      <p class="head__sub">认证后商品卡与个人主页会显示认证标识，真实姓名与学号不对外展示。</p>
    </header>

    <section v-if="passed" class="card card--ok">
      <p class="card__badge">已认证</p>
      <h2 class="card__title">你已完成学号认证</h2>
      <p class="card__line">{{ latest?.realName ?? '' }} · {{ latest?.studentNo ?? '' }}</p>
      <p class="card__note">如需修改认证信息，请联系管理员在后台驳回后重新提交。</p>
    </section>

    <section v-else-if="pending" class="card card--wait">
      <p class="card__badge">审核中</p>
      <h2 class="card__title">申请已提交，等待管理员审核</h2>
      <p class="card__line">姓名：{{ latest?.realName ?? form.realName }}</p>
      <p class="card__line">学号：{{ latest?.studentNo ?? form.studentNo }}</p>
      <p class="card__note">一般 1 个工作日内处理。</p>
    </section>

    <section v-else class="card">
      <div v-if="rejected" class="reject">
        <p class="reject__t">上次认证未通过</p>
        <p class="reject__r">{{ rejectReason || '未填写原因，请核对信息后重新提交' }}</p>
      </div>

      <el-form label-position="top" class="form">
        <el-form-item label="真实姓名">
          <el-input v-model="form.realName" maxlength="32" placeholder="与一卡通一致" />
        </el-form-item>
        <el-form-item label="学号">
          <el-input v-model="form.studentNo" maxlength="32" placeholder="校内学号" />
        </el-form-item>
        <el-form-item label="学院">
          <el-select v-model="form.collegeId" placeholder="请选择学院" style="width: 100%">
            <el-option v-for="c in colleges" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="认证凭证（选填）">
          <div class="upload">
            <img v-if="form.credentialUrl" :src="form.credentialUrl" alt="认证凭证" />
            <label v-else class="upload__pick">
              {{ uploading ? '上传中…' : '选择图片（一卡通或校园邮箱截图）' }}
              <input
                type="file"
                accept="image/*"
                hidden
                @change="onPick(($event.target as HTMLInputElement).files?.[0])"
              />
            </label>
          </div>
        </el-form-item>
      </el-form>

      <el-button type="primary" size="large" :loading="submitting" @click="submit">提交认证</el-button>
      <p class="card__note">
        提交后如需修改，等审核结果出来再重提；每次提交都会留一条记录，管理端能看到全部历史。
      </p>
    </section>

    <el-button link type="primary" class="back" @click="router.back()">返回上一页</el-button>
  </div>
</template>

<style scoped>
.verify {
  max-width: 620px;
  padding: 24px 0 64px;
}

.head__title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 700;
}

.head__sub {
  margin: 0 0 20px;
  font-size: 13px;
  color: var(--text-3, #8a8a92);
}

.card {
  padding: 24px;
  border: 1px solid var(--line, #ececf0);
  border-radius: 16px;
  background: var(--surface-1, #fff);
}

.card--ok {
  border-color: rgba(15, 159, 104, 0.24);
}

.card--wait {
  border-color: rgba(58, 122, 235, 0.24);
}

.card__badge {
  display: inline-block;
  margin: 0 0 10px;
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(15, 159, 104, 0.12);
  color: #0f9f68;
  font-size: 12px;
}

.card--wait .card__badge {
  background: rgba(58, 122, 235, 0.12);
  color: #3a7aeb;
}

.card__title {
  margin: 0 0 10px;
  font-size: 18px;
  font-weight: 700;
}

.card__line {
  margin: 4px 0;
  font-size: 14px;
  color: var(--text-2, #5a5a63);
}

.card__note {
  margin: 14px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-3, #8a8a92);
}

.reject {
  padding: 12px 14px;
  margin-bottom: 18px;
  border-radius: 10px;
  background: rgba(232, 84, 63, 0.08);
}

.reject__t {
  margin: 0 0 4px;
  font-size: 13px;
  font-weight: 600;
  color: #c9432f;
}

.reject__r {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #a63a29;
}

.upload {
  width: 100%;
}

.upload__pick {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 120px;
  border: 1px dashed var(--line, #d8d8de);
  border-radius: 12px;
  font-size: 13px;
  color: var(--text-3, #8a8a92);
  cursor: pointer;
}

.upload img {
  max-width: 100%;
  max-height: 240px;
  border-radius: 12px;
}

.back {
  margin-top: 16px;
}
</style>
