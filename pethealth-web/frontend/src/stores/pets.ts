// 宠物缓存 store —— 对应原 AppState.petCache
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiGet } from '@/api'

export interface Pet {
  id: string
  name: string
  species?: string
  breed?: string
  ageMonths?: number
  birthday?: string
  avatar?: string
  // 原后端宠物对象含多个内嵌列表，这里用 any 兜底
  [key: string]: any
}

export const usePetsStore = defineStore('pets', () => {
  const pets = ref<Pet[]>([])
  const loaded = ref(false)

  async function loadPets(force = false): Promise<Pet[]> {
    if (!force && loaded.value && pets.value.length > 0) return pets.value
    try {
      const list = await apiGet<Pet[]>('/api/pets')
      pets.value = Array.isArray(list) ? list : []
      loaded.value = true
    } catch (e) {
      pets.value = []
    }
    return pets.value
  }

  async function refreshPet(petId: string): Promise<Pet | null> {
    const updated = await apiGet<Pet>(`/api/pets/${petId}`)
    const idx = pets.value.findIndex((p) => p.id === petId)
    if (idx >= 0) pets.value[idx] = updated
    else pets.value.push(updated)
    return updated
  }

  function clear() {
    pets.value = []
    loaded.value = false
  }

  return { pets, loaded, loadPets, refreshPet, clear }
})
