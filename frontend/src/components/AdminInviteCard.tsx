import { useState, useEffect } from 'react'
import { CopyLinkField } from '@/components/CopyLinkField'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { listAdminInvites, createAdminInvite, revokeInviteLink, type InviteLink } from '@/lib/api'
import { formatDate } from '@/lib/format'

export function AdminInviteCard() {
  const [links, setLinks] = useState<InviteLink[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const fetchLinks = async () => {
    try {
      const data = await listAdminInvites()
      setLinks(data)
    } catch {
      setError('Eroare la încărcarea linkurilor de admin.')
    }
  }

  useEffect(() => {
    void fetchLinks()
  }, [])

  async function handleGenerate() {
    setError(null)
    setLoading(true)
    try {
      await createAdminInvite()
      await fetchLinks()
    } catch {
      setError('Nu am putut genera linkul. Reîncearcă.')
    } finally {
      setLoading(false)
    }
  }

  async function handleRevoke(id: number) {
    try {
      await revokeInviteLink(id)
      await fetchLinks()
    } catch {
      setError('Nu am putut șterge linkul.')
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xl">Invitații pentru profesori</CardTitle>
        <CardDescription>
          Aceste linkuri invită un <strong>profesor</strong>: după aprobare vede și schimbă doar clasele și testele lui.
          Rolul de administrator îl dai tu, la aprobare.
          Din motive de securitate, linkul are <strong>o singură utilizare</strong>, expiră în <strong>24 de ore</strong>, iar contul creat va necesita <strong>aprobare manuală</strong>.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <div className="flex flex-wrap items-end gap-4">
          <Button disabled={loading} onClick={handleGenerate}>
            {loading ? 'Se generează…' : 'Generează link sigur'}
          </Button>
        </div>

        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}

        {links.length > 0 && (
          <div className="space-y-4 pt-4 border-t border-border">
            <Label>Linkuri active:</Label>
            {links.map((link) => (
              <div key={link.id} className="space-y-2 p-3 bg-muted/50 rounded-lg">
                <CopyLinkField
                  id={`admin-invite-${link.id}`}
                  label=""
                  url={link.url}
                />
                <div className="flex items-center justify-between text-sm text-muted-foreground">
                  <div className="flex flex-col">
                    <span>Folosit: {link.uses} din {link.maxUses}</span>
                    <span className="text-xs">Expiră: {formatDate(link.expiresAt)}</span>
                  </div>
                  <Button
                    variant="destructive"
                    size="sm"
                    onClick={() => void handleRevoke(link.id)}
                  >
                    Revocă
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}