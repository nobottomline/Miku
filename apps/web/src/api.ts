const API_BASE = '/api/v1'

interface ApiError {
  code: string
  message: string
}

class MikuApi {
  private token: string | null = null

  setToken(token: string | null) {
    this.token = token
    if (token) localStorage.setItem('miku_token', token)
    else localStorage.removeItem('miku_token')
  }

  getToken(): string | null {
    if (!this.token) this.token = localStorage.getItem('miku_token')
    return this.token
  }

  private async request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...((options.headers as Record<string, string>) || {}),
    }
    if (this.getToken()) {
      headers['Authorization'] = `Bearer ${this.getToken()}`
    }

    const res = await fetch(`${API_BASE}${path}`, { ...options, headers })

    if (!res.ok) {
      const err: ApiError = await res.json().catch(() => ({ code: 'unknown', message: res.statusText }))
      throw new Error(err.message || `HTTP ${res.status}`)
    }

    return res.json()
  }

  // Health
  async health() {
    const res = await fetch('/health')
    return res.json()
  }

  // Auth
  async register(username: string, email: string, password: string) {
    return this.request('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username, email, password }),
    })
  }

  async login(username: string, password: string) {
    const data = await this.request<{ accessToken: string; refreshToken: string; expiresIn: number }>(
      '/auth/login',
      { method: 'POST', body: JSON.stringify({ username, password }) }
    )
    this.setToken(data.accessToken)
    localStorage.setItem('miku_refresh', data.refreshToken)
    return data
  }

  async me() {
    return this.request<{ id: number; username: string; email: string; role: string }>('/auth/me')
  }

  logout() {
    this.setToken(null)
    localStorage.removeItem('miku_refresh')
  }

  // Sources
  async getSources(lang?: string) {
    const params = lang ? `?lang=${lang}` : ''
    return this.request<any[]>(`/sources${params}`)
  }

  async getLanguages() {
    return this.request<string[]>('/sources/languages')
  }

  async getPopular(sourceId: string, page = 1) {
    return this.request<any>(`/sources/${sourceId}/popular?page=${page}`)
  }

  async getLatest(sourceId: string, page = 1) {
    return this.request<any>(`/sources/${sourceId}/latest?page=${page}`)
  }

  async search(sourceId: string, query: string, page = 1) {
    return this.request<any>(`/sources/${sourceId}/search?q=${encodeURIComponent(query)}&page=${page}`)
  }

  // Manga
  async getMangaDetails(sourceId: string, url: string) {
    return this.request<any>(`/manga/${sourceId}/details?url=${encodeURIComponent(url)}`)
  }

  async getChapters(sourceId: string, url: string) {
    return this.request<any[]>(`/manga/${sourceId}/chapters?url=${encodeURIComponent(url)}`)
  }

  async getPages(sourceId: string, url: string) {
    return this.request<any[]>(`/manga/${sourceId}/pages?url=${encodeURIComponent(url)}`)
  }

  // Extensions
  async getInstalledExtensions() {
    return this.request<any[]>('/extensions/installed')
  }

  async getAvailableExtensions(lang?: string) {
    const params = lang ? `?lang=${lang}` : ''
    return this.request<any[]>(`/extensions/available${params}`)
  }

  async searchExtensions(query: string) {
    return this.request<any[]>(`/extensions/search?q=${encodeURIComponent(query)}`)
  }

  async installExtension(pkg: string) {
    return this.request<any>(`/extensions/install/${encodeURIComponent(pkg)}`, { method: 'POST' })
  }

  async uninstallExtension(pkg: string) {
    return this.request<any>(`/extensions/${encodeURIComponent(pkg)}`, { method: 'DELETE' })
  }

  async checkUpdates() {
    return this.request<any[]>('/extensions/updates')
  }

  // Image proxy
  imageUrl(url: string, sourceId?: string) {
    const params = new URLSearchParams({ url })
    if (sourceId) params.set('sourceId', sourceId.toString())
    return `${API_BASE}/image/proxy?${params}`
  }
}

export const api = new MikuApi()
