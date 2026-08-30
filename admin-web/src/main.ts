import { createApp } from 'vue'
import { createPinia } from 'pinia'
import WinDesignNext from 'win-design-next'
import 'win-design-next/dist/index.css'
import App from './App.vue'
import router from './router'
import { vPermission } from './directives/permission'
import './styles/index.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(WinDesignNext)
app.directive('permission', vPermission)
app.mount('#app')
