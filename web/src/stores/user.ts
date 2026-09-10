import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const nickname = ref<string>(localStorage.getItem('nickname') || '')

  function setLogin(t: string, name: string) {
    token.value = t
    nickname.value = name
    localStorage.setItem('token', t)
    localStorage.setItem('nickname', name)
  }

  function logout() {
    token.value = ''
    nickname.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('nickname')
  }

  return { token, nickname, setLogin, logout }
})
