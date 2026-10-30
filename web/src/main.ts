import { createApp } from 'vue'
import {
  ElAlert,
  ElButton,
  ElConfigProvider,
  ElDrawer,
  ElEmpty,
  ElInput,
  ElTag,
} from 'element-plus'
// 按需引样式：这条路比 `import 'element-plus/dist/index.css'` 多几行，
// 但在这个项目里带宽是第一预算（2 Mbps），全量 CSS 是 320 KB。
// 新增一个 Element 组件时，这里必须同时加一行组件与一行 style。
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/config-provider/style/css'
import 'element-plus/es/components/drawer/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/tag/style/css'
import App from './App.vue'
import './styles.css'

const app = createApp(App)

for (const component of [ElAlert, ElButton, ElConfigProvider, ElDrawer, ElEmpty, ElInput, ElTag]) {
  app.use(component)
}

app.mount('#app')
