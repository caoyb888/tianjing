import { ref } from 'vue'

interface PaginationOptions {
  defaultPage?: number
  defaultSize?: number
}

export function usePagination(options: PaginationOptions = {}) {
  const page = ref(options.defaultPage ?? 1)
  const size = ref(options.defaultSize ?? 20)
  const total = ref(0)

  function resetPage() {
    page.value = 1
  }

  return { page, size, total, resetPage }
}
