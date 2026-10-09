<template>
  <div class="toast-container">
    <div
      v-for="t in toasts"
      :key="t.id"
      :class="['toast', `toast-${t.type}`]"
    >
      {{ t.msg }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { useToast } from '@/composables/useToast'
const { toasts } = useToast()
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 80px;
  right: 1.5rem;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  pointer-events: none;
}
</style>
