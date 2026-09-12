import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
  { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
  { path: '/assistant', name: 'assistant', component: () => import('@/views/AssistantView.vue') },
  { path: '/positions', name: 'positions', component: () => import('@/views/PositionManageView.vue') },
  { path: '/positions/new/:id?', name: 'position-wizard', component: () => import('@/views/PositionWizardView.vue') },
  { path: '/resumes', name: 'resumes', component: () => import('@/views/ResumeView.vue') },
  { path: '/reviews', name: 'reviews', component: () => import('@/views/ReviewView.vue') },
  { path: '/jobs', name: 'jobs', component: () => import('@/views/JobFinderView.vue') },
  { path: '/knowledge', name: 'knowledge', component: () => import('@/views/KnowledgeView.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (!token && to.name !== 'login') {
    return { name: 'login' }
  }
  return true
})

export default router
