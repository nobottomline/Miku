import { useState, useEffect } from 'react'
import { api } from '../api'

interface Props {
  onSelectSource: (id: string, name: string) => void
}

export function SourceBrowser({ onSelectSource }: Props) {
  const [tab, setTab] = useState<'sources' | 'extensions'>('sources')
  const [sources, setSources] = useState<any[]>([])
  const [availableExts, setAvailableExts] = useState<any[]>([])
  const [languages, setLanguages] = useState<string[]>([])
  const [selectedLang, setSelectedLang] = useState<string>('')
  const [extSearch, setExtSearch] = useState('')
  const [loading, setLoading] = useState(true)
  const [installing, setInstalling] = useState<Set<string>>(new Set())

  useEffect(() => { loadSources() }, [])

  const loadSources = async () => {
    setLoading(true)
    try {
      const [s, l] = await Promise.all([api.getSources(), api.getLanguages()])
      setSources(s)
      setLanguages(Array.isArray(l) ? l.sort() : [])
    } catch (err) { console.error(err) }
    finally { setLoading(false) }
  }

  const loadExtensions = async () => {
    setLoading(true)
    try {
      const exts = await api.getAvailableExtensions()
      setAvailableExts(exts)
    } catch (err) { console.error(err) }
    finally { setLoading(false) }
  }

  useEffect(() => {
    if (tab === 'extensions' && availableExts.length === 0) loadExtensions()
  }, [tab])

  const handleInstall = async (pkg: string) => {
    setInstalling(prev => new Set(prev).add(pkg))
    try {
      await api.installExtension(pkg)
      await Promise.all([loadSources(), loadExtensions()])
    } catch (err: any) {
      alert(`Install failed: ${err.message}`)
    } finally {
      setInstalling(prev => { const next = new Set(prev); next.delete(pkg); return next })
    }
  }

  const handleUninstall = async (pkg: string) => {
    try {
      await api.uninstallExtension(pkg)
      await Promise.all([loadSources(), loadExtensions()])
    } catch (err: any) { alert(`Uninstall failed: ${err.message}`) }
  }

  const filteredSources = selectedLang ? sources.filter(s => s.lang === selectedLang) : sources
  const filteredExts = extSearch
    ? availableExts.filter(e => e.name.toLowerCase().includes(extSearch.toLowerCase()) || e.pkg.toLowerCase().includes(extSearch.toLowerCase()))
    : availableExts

  if (loading && tab === 'sources' && sources.length === 0) {
    return <div className="text-center py-20 text-text-muted">Loading...</div>
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div className="flex gap-1 bg-surface-alt rounded-lg p-1 border border-border">
          <TabBtn active={tab === 'sources'} onClick={() => setTab('sources')}>Sources ({sources.length})</TabBtn>
          <TabBtn active={tab === 'extensions'} onClick={() => setTab('extensions')}>Extensions</TabBtn>
        </div>
      </div>

      {tab === 'sources' ? (
        <>
          {languages.length > 0 && (
            <div className="flex flex-wrap gap-2 mb-6">
              <button onClick={() => setSelectedLang('')}
                className={`px-3 py-1.5 text-sm rounded-lg border transition-colors ${!selectedLang ? 'bg-primary text-white border-primary' : 'border-border hover:bg-surface-hover'}`}>
                All ({sources.length})
              </button>
              {languages.map(lang => (
                <button key={lang} onClick={() => setSelectedLang(lang)}
                  className={`px-3 py-1.5 text-sm rounded-lg border transition-colors ${selectedLang === lang ? 'bg-primary text-white border-primary' : 'border-border hover:bg-surface-hover'}`}>
                  {lang.toUpperCase()} ({sources.filter(s => s.lang === lang).length})
                </button>
              ))}
            </div>
          )}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {filteredSources.map(source => (
              <button key={source.id} onClick={() => onSelectSource(source.id, source.name)}
                className="flex items-center gap-3 p-4 rounded-xl bg-surface-alt hover:bg-surface-hover border border-border transition-all hover:border-primary/50 text-left group">
                <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center text-primary font-bold text-sm flex-shrink-0">
                  {source.name.charAt(0)}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="font-medium text-sm truncate group-hover:text-primary transition-colors">{source.name}</div>
                  <div className="text-xs text-text-muted flex items-center gap-2">
                    <span>{source.lang?.toUpperCase()}</span>
                    {source.supportsLatest && <span className="text-success">● Latest</span>}
                  </div>
                </div>
              </button>
            ))}
          </div>
          {filteredSources.length === 0 && (
            <div className="text-center py-20 text-text-muted">
              {sources.length === 0 ? 'No extensions installed. Go to the Extensions tab to browse and install.' : 'No sources match the filter.'}
            </div>
          )}
        </>
      ) : (
        <>
          <div className="mb-6">
            <input value={extSearch} onChange={e => setExtSearch(e.target.value)} placeholder="Search extensions..."
              className="w-full px-4 py-2.5 rounded-lg bg-surface-alt border border-border text-sm focus:outline-none focus:border-primary transition-colors placeholder:text-text-muted" />
          </div>
          {loading ? (
            <div className="text-center py-20 text-text-muted">Loading available extensions from keiyoushi repo...</div>
          ) : (
            <div className="grid gap-2">
              {filteredExts.map(ext => (
                <div key={ext.pkg} className="flex items-center justify-between p-4 rounded-xl bg-surface-alt border border-border">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-sm">{ext.name}</span>
                      <span className="text-xs px-1.5 py-0.5 rounded bg-surface text-text-muted">{ext.lang.toUpperCase()}</span>
                      {ext.isNsfw && <span className="text-xs px-1.5 py-0.5 rounded bg-error/20 text-error">NSFW</span>}
                      {ext.hasUpdate && <span className="text-xs px-1.5 py-0.5 rounded bg-warning/20 text-warning">Update</span>}
                    </div>
                    <div className="text-xs text-text-muted mt-1">v{ext.versionName} · {ext.sources?.length || 0} source(s)</div>
                  </div>
                  <div className="flex-shrink-0 ml-3">
                    {ext.installed ? (
                      <div className="flex gap-2">
                        {ext.hasUpdate && (
                          <button onClick={() => handleInstall(ext.pkg)} disabled={installing.has(ext.pkg)}
                            className="px-3 py-1.5 text-xs rounded-lg bg-warning/20 text-warning hover:bg-warning/30 transition-colors disabled:opacity-50">Update</button>
                        )}
                        <button onClick={() => handleUninstall(ext.pkg)}
                          className="px-3 py-1.5 text-xs rounded-lg bg-error/20 text-error hover:bg-error/30 transition-colors">Uninstall</button>
                      </div>
                    ) : (
                      <button onClick={() => handleInstall(ext.pkg)} disabled={installing.has(ext.pkg)}
                        className="px-4 py-1.5 text-xs rounded-lg bg-primary hover:bg-primary-dark text-white transition-colors disabled:opacity-50">
                        {installing.has(ext.pkg) ? 'Installing...' : 'Install'}
                      </button>
                    )}
                  </div>
                </div>
              ))}
              {filteredExts.length === 0 && (
                <div className="text-center py-20 text-text-muted">{extSearch ? 'No extensions match your search.' : 'No extensions available.'}</div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  )
}

function TabBtn({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button onClick={onClick}
      className={`px-4 py-1.5 text-sm rounded-md transition-colors ${active ? 'bg-primary text-white' : 'text-text-muted hover:text-text'}`}>
      {children}
    </button>
  )
}
