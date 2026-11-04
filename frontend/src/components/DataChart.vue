<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { use, init } from 'echarts/core'
import type { EChartsType, EChartsCoreOption } from 'echarts/core'
import { LineChart, PieChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, AriaComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([
  LineChart,
  PieChart,
  BarChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  AriaComponent,
  CanvasRenderer
])
const props = defineProps<{ option: EChartsCoreOption; label: string; height?: number }>()
const host = ref<HTMLDivElement>()
let chart: EChartsType | undefined
let observer: ResizeObserver | undefined
function render() {
  chart?.setOption(
    {
      ...props.option,
      animation: !matchMedia('(prefers-reduced-motion: reduce)').matches,
      aria: { enabled: true, label: { description: props.label } }
    },
    { notMerge: true }
  )
}
onMounted(() => {
  if (!host.value) return
  chart = init(host.value)
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(host.value)
  render()
})
watch(() => props.option, render, { deep: true })
onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <div
    ref="host"
    class="chart"
    role="img"
    :aria-label="label"
    :style="{ height: `${height || 280}px` }"
  ></div>
</template>
