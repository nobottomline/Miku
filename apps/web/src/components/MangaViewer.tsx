import { useState, useEffect, useCallback } from 'react'
import { api } from '../api'

interface Props {
  sourceId: string
  sourceName: string
  onSelectManga: (url: string) => void
}

export function MangaViewer({ sourceId, sourceName, onSelectManga }: Props) {
  const [tab, setTab] = useState<'popular' | 'latest' | 'search'>('popular')
  const [mangas, setMangas] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(1)
  const [hasNext, setHasNext] = useState(false)
  const [query, setQuery] = useState('')
  const [searchInput, setSearchInput] = useState('')

  const fetchData = useCallback(async (p: number, reset = false) => {
    setLoading(true)
    try {
      let result
      if (tab === 'popular') result = await api.getPopular(sourceId, p)
      else if (tab === 'latest') result = await api.getLatest(sourceId, p)
      else result = await api.search(sourceId, query, p)

      setMangas(prev => reset ? result.mangas : [...prev, ...result.mangas])
      setHasNext(result.hasNextPage)
      setPage(p)
    } catch (err: any) {
      console.error(err)
    } finally {
      setLoading(false)
    }
  }, [sourceId, tab, query])

  useEffect(() => {
    if (tab !== 'search' || query) {
      fetchData(1, true)
    }
  }, [tab, query, sourceId])

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    if (searchInput.trim()) {
      setTab('search')
      setQuery(searchInput.trim())
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">{sourceName}</h1>

      {/* Tabs + Search */}
      <div className="flex flex-col sm:flex-row gap-3 mb-6">
        <div className="flex gap-1 bg-surface-alt rounded-lg p-1 border border-border">
          <TabBtn active={tab === 'popular'} onClick={() => { setTab('popular'); setMangas([]) }}>Popular</TabBtn>
          <TabBtn active={tab === 'latest'} onClick={() => { setTab('latest'); setMangas([]) }}>Latest</TabBtn>
        </div>

        <form onSubmit={handleSearch} className="flex-1 flex gap-2">
          <input
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            placeholder="Search manga..."
            className="flex-1 px-4 py-2 rounded-lg bg-surface-alt border border-border text-sm focus:outline-none focus:border-primary transition-colors placeholder:text-text-muted"
          />
          <button type="submit" className="px-4 py-2 rounded-lg bg-primary hover:bg-primary-dark text-white text-sm transition-colors">
            Search
          </button>
        </form>
      </div>

      {/* Manga Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
        {mangas.map((manga: any, i: number) => (
          <button
            key={`${manga.url}-${i}`}
            onClick={() => onSelectManga(manga.url)}
            className="group text-left"
          >
            <div className="aspect-[3/4] rounded-xl overflow-hidden bg-surface-alt border border-border group-hover:border-primary/50 transition-all mb-2 relative">
              {manga.thumbnailUrl ? (
                <img
                  src={api.imageUrl(manga.thumbnailUrl, sourceId)}
                  alt=""
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                  loading="lazy"
                />
              ) : (
                <div className="w-full h-full flex items-center justify-center text-text-muted text-xs">No image</div>
              )}
              <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/80 to-transparent p-2 pt-8">
                <div className="text-xs text-white font-medium line-clamp-2">{manga.title}</div>
              </div>
            </div>
          </button>
        ))}
      </div>

      {/* Loading / Load More */}
      {loading && <div className="text-center py-10 text-text-muted">Loading...</div>}
      {!loading && hasNext && (
        <div className="text-center py-6">
          <button
            onClick={() => fetchData(page + 1)}
            className="px-6 py-2.5 rounded-lg bg-surface-alt hover:bg-surface-hover border border-border transition-colors text-sm"
          >
            Load More
          </button>
        </div>
      )}
      {!loading && mangas.length === 0 && (
        <div className="text-center py-20 text-text-muted">No manga found</div>
      )}
    </div>
  )
}

function TabBtn({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      className={`px-4 py-1.5 text-sm rounded-md transition-colors ${
        active ? 'bg-primary text-white' : 'text-text-muted hover:text-text'
      }`}
    >
      {children}
    </button>
  )
}
