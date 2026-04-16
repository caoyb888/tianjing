import { ref, reactive } from 'vue'

export function useTableSearch<T extends Record<string, unknown>>(
  initialQuery: T,
  fetchFn: () => Promise<void>
) {
  const loading = ref(false)
  const query = reactive({ ...initialQuery, page: 1, size: 20 }) as T & { page: number; size: number }

  async function search() {
    query.page = 1
    loading.value = true
    try { await fetchFn() } finally { loading.value = false }
  }

  function reset() {
    Object.assign(query, initialQuery, { page: 1 })
    search()
  }

  async function load() {
    loading.value = true
    try { await fetchFn() } finally { loading.value = false }
  }

  return { loading, query, search, reset, load }
}
