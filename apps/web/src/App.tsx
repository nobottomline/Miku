import { useState, useEffect } from 'react'
import { api } from './api'
import { SourceBrowser } from './components/SourceBrowser'
import { MangaViewer } from './components/MangaViewer'
import { Reader } from './components/Reader'
import { AuthModal } from './components/AuthModal'
import { StatusBar } from './components/StatusBar'

type View =
  | { type: 'sources' }
  | { type: 'browse'; sourceId: string; sourceName: string }
  | { type: 'manga'; sourceId: string; mangaUrl: string }
  | { type: 'reader'; sourceId: string; chapterUrl: string; chapterName: string }

export default function App() {
  const [view, setView] = useState<View>({ type: 'sources' })
  const [showAuth, setShowAuth] = useState(false)
  const [user, setUser] = useState<any>(null)
  const [health, setHealth] = useState<any>(null)

  useEffect(() => {
    api.health().then(setHealth).catch(() => {})
    if (api.getToken()) {
      api.me().then(setUser).catch(() => api.logout())
    }
  }, [])

  const handleLogout = () => {
    api.logout()
    setUser(null)
  }

  return (
    <div className="min-h-screen flex flex-col">
      {/* Header */}
      <header className="bg-surface-alt border-b border-border sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 h-14 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button onClick={() => setView({ type: 'sources' })} className="flex items-center gap-2 hover:opacity-80 transition-opacity">
              <span className="text-xl font-bold text-primary">Miku</span>
              <span className="text-text-muted text-sm">manga server</span>
            </button>

            {view.type !== 'sources' && (
              <div className="flex items-center gap-1 text-text-muted text-sm">
                <span>/</span>
                {view.type === 'browse' && <span>{(view as any).sourceName}</span>}
                {view.type === 'manga' && <span>Manga Details</span>}
                {view.type === 'reader' && <span>{(view as any).chapterName}</span>}
              </div>
            )}
          </div>

          <div className="flex items-center gap-3">
            {view.type !== 'sources' && (
              <button
                onClick={() => setView({ type: 'sources' })}
                className="px-3 py-1.5 text-sm rounded-lg bg-surface hover:bg-surface-hover border border-border transition-colors"
              >
                &larr; Sources
              </button>
            )}

            {user ? (
              <div className="flex items-center gap-2">
                <span className="text-sm text-text-muted">{user.username}</span>
                <span className="text-xs px-1.5 py-0.5 rounded bg-primary/20 text-primary">{user.role}</span>
                <button onClick={handleLogout} className="text-sm text-text-muted hover:text-error transition-colors">Logout</button>
              </div>
            ) : (
              <button onClick={() => setShowAuth(true)} className="px-3 py-1.5 text-sm rounded-lg bg-primary hover:bg-primary-dark transition-colors text-white">
                Sign In
              </button>
            )}
          </div>
        </div>
      </header>

      {/* Main */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 py-6">
        {view.type === 'sources' && (
          <SourceBrowser onSelectSource={(id, name) => setView({ type: 'browse', sourceId: id, sourceName: name })} />
        )}
        {view.type === 'browse' && (
          <MangaViewer
            sourceId={view.sourceId}
            sourceName={view.sourceName}
            onSelectManga={(url) => setView({ type: 'manga', sourceId: view.sourceId, mangaUrl: url })}
          />
        )}
        {view.type === 'manga' && (
          <MangaDetails
            sourceId={view.sourceId}
            mangaUrl={view.mangaUrl}
            onReadChapter={(url, name) => setView({ type: 'reader', sourceId: view.sourceId, chapterUrl: url, chapterName: name })}
            onBack={() => setView({ type: 'sources' })}
          />
        )}
        {view.type === 'reader' && (
          <Reader sourceId={view.sourceId} chapterUrl={view.chapterUrl} />
        )}
      </main>

      {/* Status Bar */}
      <StatusBar health={health} />

      {/* Auth Modal */}
      {showAuth && <AuthModal onClose={() => setShowAuth(false)} onSuccess={(u) => { setUser(u); setShowAuth(false) }} />}
    </div>
  )
}

function MangaDetails({ sourceId, mangaUrl, onReadChapter, onBack: _onBack }: {
  sourceId: string; mangaUrl: string
  onReadChapter: (url: string, name: string) => void; onBack: () => void
}) {
  const [manga, setManga] = useState<any>(null)
  const [chapters, setChapters] = useState<any[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    Promise.all([
      api.getMangaDetails(sourceId, mangaUrl),
      api.getChapters(sourceId, mangaUrl),
    ]).then(([m, c]) => {
      setManga(m)
      setChapters(c)
    }).catch(console.error).finally(() => setLoading(false))
  }, [sourceId, mangaUrl])

  if (loading) return <div className="text-center py-20 text-text-muted">Loading manga details...</div>
  if (!manga) return <div className="text-center py-20 text-error">Failed to load manga</div>

  return (
    <div>
      <div className="flex gap-6 mb-8">
        {manga.thumbnailUrl && (
          <img src={api.imageUrl(manga.thumbnailUrl, sourceId)} alt="" className="w-48 h-72 object-cover rounded-xl shadow-lg flex-shrink-0" />
        )}
        <div className="flex-1 min-w-0">
          <h1 className="text-2xl font-bold mb-2">{manga.title}</h1>
          <div className="flex flex-wrap gap-2 mb-3">
            {manga.author && <span className="text-sm text-text-muted">Author: {manga.author}</span>}
            {manga.artist && <span className="text-sm text-text-muted">&bull; Artist: {manga.artist}</span>}
          </div>
          <div className="flex flex-wrap gap-1.5 mb-4">
            <span className={`text-xs px-2 py-0.5 rounded-full ${
              manga.status === 'ONGOING' ? 'bg-success/20 text-success' :
              manga.status === 'COMPLETED' ? 'bg-primary/20 text-primary' :
              'bg-warning/20 text-warning'
            }`}>{manga.status}</span>
            {manga.genres?.map((g: string) => (
              <span key={g} className="text-xs px-2 py-0.5 rounded-full bg-surface-hover text-text-muted">{g}</span>
            ))}
          </div>
          {manga.description && (
            <p className="text-sm text-text-muted leading-relaxed line-clamp-6">{manga.description}</p>
          )}
        </div>
      </div>

      <h2 className="text-lg font-semibold mb-3">Chapters ({chapters.length})</h2>
      <div className="grid gap-1">
        {chapters.map((ch: any) => (
          <button
            key={ch.url}
            onClick={() => onReadChapter(ch.url, ch.name)}
            className="flex items-center justify-between px-4 py-2.5 rounded-lg bg-surface-alt hover:bg-surface-hover border border-border transition-colors text-left"
          >
            <span className="text-sm">{ch.name}</span>
            {ch.scanlator && <span className="text-xs text-text-muted">{ch.scanlator}</span>}
          </button>
        ))}
      </div>
    </div>
  )
}
