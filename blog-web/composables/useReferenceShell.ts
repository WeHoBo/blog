import { isReferenceLayout } from '~/composables/useSkin'

export function useReferenceShell() {
  const { current } = useSkin()
  const reference = computed(() => isReferenceLayout(current.value.layout))
  const rail = computed(() => current.value.layout === 'platform' || current.value.layout === 'album')
  return { current, reference, rail }
}
