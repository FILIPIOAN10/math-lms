import { useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

interface Props {
  id: string
  label: string
  url: string
  inputTestId?: string
  copyTestId?: string
}

/**
 * A read-only link with a copy button. When the browser refuses the clipboard (permissions, embedded browsers) the
 * link is selected instead, so the teacher only has to press Ctrl+C.
 */
export function CopyLinkField({ id, label, url, inputTestId, copyTestId }: Props) {
  const [copied, setCopied] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const input = useRef<HTMLInputElement>(null)

  async function handleCopy() {
    setError(null)
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
    } catch {
      input.current?.focus()
      input.current?.select()
      setError('Browserul nu permite copierea automată — linkul e selectat, apasă Ctrl+C.')
    }
  }

  return (
    <div className="space-y-1">
      <Label htmlFor={id}>{label}</Label>
      <div className="flex gap-2">
        <Input
          id={id}
          ref={input}
          data-testid={inputTestId}
          readOnly
          value={url}
          onFocus={(e) => e.currentTarget.select()}
        />
        <Button variant="outline" data-testid={copyTestId} onClick={handleCopy}>
          {copied ? 'Copiat ✓' : 'Copiază'}
        </Button>
      </div>
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  )
}
