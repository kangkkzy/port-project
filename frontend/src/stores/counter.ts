// counter.ts
// 一个简单的 Pinia store 示例，用于管理计数器状态。
// 演示了 ref、computed 和 action 的基本用法。

import { ref, computed } from 'vue'
import { defineStore } from 'pinia'

export const useCounterStore = defineStore('counter', () => {
  // 状态：当前计数值
  const count = ref(0)

  // 计算属性：计数的两倍
  const doubleCount = computed(() => count.value * 2)

  // 动作：增加计数
  function increment() {
    count.value++
  }

  // 返回所有状态和方法，供组件使用
  return { count, doubleCount, increment }
})