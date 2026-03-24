interface Props {
  health: any
}

export function StatusBar({ health }: Props) {
  return (
    <footer className="bg-surface-alt border-t border-border py-2 px-4">
      <div className="max-w-7xl mx-auto flex items-center justify-between text-xs text-text-muted">
        <span>Miku Server {health?.version || '...'}</span>
        <div className="flex items-center gap-4">
          <span>{health?.extensions || 0} extensions</span>
          <span>{health?.sources || 0} sources</span>
          <span className={health?.status === 'ok' ? 'text-success' : 'text-error'}>
            {health?.status === 'ok' ? '\u25CF Connected' : '\u25CF Disconnected'}
          </span>
        </div>
      </div>
    </footer>
  )
}
