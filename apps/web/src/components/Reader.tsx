import { useState, useEffect } from 'react'
import { api } from '../api'

interface Props {
  sourceId: string
  chapterUrl: string
}

export function Reader({ sourceId, chapterUrl }: Props) {
  const [pages, setPages] = useState<any[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    api.getPages(sourceId, chapterUrl)
      .then(setPages)
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [sourceId, chapterUrl])

  if (loading) return <div className="text-center py-20 text-text-muted">Loading pages...</div>

  return (
    <div className="max-w-3xl mx-auto space-y-1">
      {pages.map((page: any) => (
        <div key={page.index} className="relative">
          <img
            src={api.imageUrl(page.imageUrl || page.url, sourceId)}
            alt={`Page ${page.index + 1}`}
            className="w-full"
            loading="lazy"
          />
        </div>
      ))}
      {pages.length === 0 && <div className="text-center py-20 text-text-muted">No pages found</div>}
    </div>
  )
}
