export const useSiteConfig = () => {
  const config = useState<Record<string, string>>('siteConfig', () => ({}))

  async function fetch() {
    const { get } = useApi()
    try {
      const res = await get<any>('/site-config/public')
      if (res.code === 200) config.value = res.data || {}
    } catch {}
  }

  return { config, fetch }
}
