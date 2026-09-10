import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 30000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (resp) => resp.data,
  (error) => {
    const msg = error?.response?.data?.message || error.message || '请求失败'
    return Promise.reject(new Error(msg))
  },
)

/** 后端统一返回 Result<T>，这里取 data；失败已在拦截器抛错。 */
export async function unwrap<T>(promise: Promise<any>): Promise<T> {
  const res = await promise
  if (res && typeof res === 'object' && 'code' in res) {
    if (res.code !== 200) {
      throw new Error(res.message || '请求失败')
    }
    return res.data as T
  }
  return res as T
}

export default http
