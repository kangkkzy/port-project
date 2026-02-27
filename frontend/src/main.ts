import { createApp } from 'vue'
import { createPinia } from 'pinia'

// 引入 Element Plus (UI组件库)
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

// 引入 Vue Konva (画图工具)
import VueKonva from 'vue-konva'

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus) // 注册 Element Plus
app.use(VueKonva)    // 注册画图工具

app.mount('#app')