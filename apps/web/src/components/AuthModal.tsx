import { useState } from 'react'
import { api } from '../api'

interface Props {
  onClose: () => void
  onSuccess: (user: any) => void
}

export function AuthModal({ onClose, onSuccess }: Props) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)

    try {
      if (mode === 'register') {
        await api.register(username, email, password)
      }
      await api.login(username, password)
      const user = await api.me()
      onSuccess(user)
    } catch (err: any) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4" onClick={onClose}>
      <div className="bg-surface-alt border border-border rounded-2xl p-6 w-full max-w-md" onClick={e => e.stopPropagation()}>
        <h2 className="text-xl font-bold mb-6">{mode === 'login' ? 'Sign In' : 'Create Account'}</h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          <input
            value={username}
            onChange={e => setUsername(e.target.value)}
            placeholder="Username"
            className="w-full px-4 py-2.5 rounded-lg bg-surface border border-border text-sm focus:outline-none focus:border-primary transition-colors"
            required
          />
          {mode === 'register' && (
            <input
              value={email}
              onChange={e => setEmail(e.target.value)}
              placeholder="Email"
              type="email"
              className="w-full px-4 py-2.5 rounded-lg bg-surface border border-border text-sm focus:outline-none focus:border-primary transition-colors"
              required
            />
          )}
          <input
            value={password}
            onChange={e => setPassword(e.target.value)}
            placeholder="Password"
            type="password"
            className="w-full px-4 py-2.5 rounded-lg bg-surface border border-border text-sm focus:outline-none focus:border-primary transition-colors"
            required
          />

          {error && <p className="text-error text-sm">{error}</p>}

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 rounded-lg bg-primary hover:bg-primary-dark text-white text-sm font-medium transition-colors disabled:opacity-50"
          >
            {loading ? 'Loading...' : mode === 'login' ? 'Sign In' : 'Create Account'}
          </button>
        </form>

        <p className="text-center text-sm text-text-muted mt-4">
          {mode === 'login' ? "Don't have an account? " : 'Already have an account? '}
          <button onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError('') }} className="text-primary hover:underline">
            {mode === 'login' ? 'Register' : 'Sign In'}
          </button>
        </p>
      </div>
    </div>
  )
}
