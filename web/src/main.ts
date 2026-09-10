import { createApp } from 'vue'
import { createPinia } from 'pinia'
import TDesign, { MessagePlugin } from 'tdesign-vue-next'
import 'tdesign-vue-next/es/style/index.css'
import './style.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

// 全局错误兜底：避免单个请求/渲染异常导致整页白屏
app.config.errorHandler = (err, _instance, info) => {
  console.error('[全局错误]', err, info)
  try {
    MessagePlugin.error('页面出现未预期错误，请刷新重试')
  } catch {
    /* 兜底不可用时不二次报错 */
  }
}

app.use(createPinia()).use(router).use(TDesign).mount('#app')
