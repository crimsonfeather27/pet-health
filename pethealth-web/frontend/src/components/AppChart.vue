<template>
  <v-chart
    ref="chartRef"
    :option="option"
    :autoresize="true"
    class="chart-host"
  />
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  DataZoomComponent,
} from 'echarts/components'
import VChart from 'vue-echarts'

use([
  CanvasRenderer,
  LineChart,
  BarChart,
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  DataZoomComponent,
])

const props = defineProps<{ option: any }>()
const chartRef = ref<InstanceType<typeof VChart>>()

// 暴露 resize / getInstance 供父组件调用
defineExpose({
  resize: () => chartRef.value?.resize(),
})
</script>

<style scoped>
.chart-host {
  width: 100%;
  height: 320px;
}
</style>
