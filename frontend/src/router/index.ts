import { createRouter, createWebHistory } from 'vue-router'
import SimulationDashboard from '../components/SimulationDashboard.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: SimulationDashboard
    }
  ]
})

export default router