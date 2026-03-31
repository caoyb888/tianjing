import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { SceneConfig } from '@/types'
import { sceneApi } from '@/api/scene'

export const useSceneStore = defineStore('scene', () => {
  const scenes = ref<SceneConfig[]>([])
  const total = ref(0)
  const loading = ref(false)
  const currentScene = ref<SceneConfig | null>(null)

  async function fetchScenes(params?: { page?: number; size?: number; factory?: string; status?: string }) {
    loading.value = true
    try {
      const res = await sceneApi.list(params)
      scenes.value = res.data.data.items
      total.value = res.data.data.total
    } finally {
      loading.value = false
    }
  }

  async function fetchScene(sceneId: string) {
    const res = await sceneApi.get(sceneId)
    currentScene.value = res.data.data
    return res.data.data
  }

  async function enableScene(sceneId: string) {
    await sceneApi.enable(sceneId)
    const scene = scenes.value.find((s) => s.sceneId === sceneId)
    if (scene) scene.status = 'active' as any
  }

  async function disableScene(sceneId: string) {
    await sceneApi.disable(sceneId)
    const scene = scenes.value.find((s) => s.sceneId === sceneId)
    if (scene) scene.status = 'inactive' as any
  }

  return {
    scenes,
    total,
    loading,
    currentScene,
    fetchScenes,
    fetchScene,
    enableScene,
    disableScene,
  }
})
