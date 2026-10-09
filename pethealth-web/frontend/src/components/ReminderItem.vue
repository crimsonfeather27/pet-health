<template>
  <div
    class="reminder-item"
    :class="{ 'reminder-flash': reminder.id === flashId }"
    :data-rid="reminder.id"
  >
    <div class="reminder-item-main">
      <b>{{ reminder.title || reminder.type }}</b>
      <span v-if="reminder.description" class="vet-meta" style="margin-left: 0.5rem">
        {{ truncate(reminder.description, 40) }}
      </span>
    </div>
    <div class="reminder-item-meta">
      <span class="vet-meta">{{ reminder.petName || '通用' }}</span>
      <span class="vet-meta">{{ timeText }}</span>
      <span class="pet-badge" :style="badgeStyle">{{ statusText }}</span>
      <div class="reminder-actions">
        <template v-if="reminder.status === 'PENDING'">
          <button class="btn btn-tiny btn-secondary" @click.stop="emit('edit', reminder.id)">编辑</button>
          <button class="btn btn-tiny btn-primary" @click.stop="emit('acknowledge', reminder.id)">确认</button>
          <button class="btn btn-tiny btn-secondary" @click.stop="emit('cancel', reminder.id)">取消</button>
          <button class="btn btn-tiny btn-danger" @click.stop="emit('delete', reminder.id)">删除</button>
        </template>
        <template v-else-if="reminder.status === 'SENT'">
          <button class="btn btn-tiny btn-primary" @click.stop="emit('acknowledge', reminder.id)">确认</button>
          <button class="btn btn-tiny btn-danger" @click.stop="emit('delete', reminder.id)">删除</button>
        </template>
        <template v-else>
          <button class="btn btn-tiny btn-danger" @click.stop="emit('delete', reminder.id)">删除</button>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Reminder {
  id: string
  title?: string
  type?: string
  description?: string
  remindAt?: string
  status?: string
  petName?: string
}

const props = defineProps<{ reminder: Reminder; flashId?: string }>()
const emit = defineEmits<{
  (e: 'edit', id: string): void
  (e: 'acknowledge', id: string): void
  (e: 'cancel', id: string): void
  (e: 'delete', id: string): void
}>()

function truncate(str: string, n: number) {
  return str && str.length > n ? str.slice(0, n) + '...' : str
}

const timeText = computed(() =>
  props.reminder.remindAt ? new Date(props.reminder.remindAt).toLocaleString('zh-CN') : ''
)

const statusMap: Record<string, { text: string; bg: string }> = {
  PENDING: { text: '待发送', bg: '#fdeab4' },
  SENT: { text: '已发送', bg: '#b9dbf8' },
  ACKNOWLEDGED: { text: '已确认', bg: '#c6e7cd' },
  CANCELLED: { text: '已取消', bg: '#e2e5e9' },
}

const statusText = computed(() => statusMap[props.reminder.status || '']?.text || props.reminder.status)
const badgeStyle = computed(() => ({
  background: statusMap[props.reminder.status || '']?.bg || 'transparent',
}))
</script>
