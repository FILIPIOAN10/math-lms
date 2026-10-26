import { Button } from '@/components/ui/button'
import { MathContent } from '@/components/MathContent'
import type { AiConfidence, AiProposal } from '@/lib/api'

const CONFIDENCE: Record<AiConfidence, string> = {
  HIGH: 'încredere mare',
  MEDIUM: 'încredere medie',
  LOW: 'încredere mică - verifică atent',
}

/**
 * The AI's proposal for one answer, above the points: still being made, failed (with a way to ask again), or ready -
 * the total, how sure it is and why, with one click to take it all. Nothing here grades anything by itself.
 */
export function AiProposalPanel({ proposal, max, canRequest, readOnly, onRequest, onAccept }: {
  proposal: AiProposal | null
  max: number
  /** The answer has photos and a barem: the AI can be asked. */
  canRequest: boolean
  readOnly: boolean
  onRequest: () => void
  onAccept: () => void
}) {
  if (proposal === null) {
    return canRequest && !readOnly ? (
      <Button type="button" size="sm" variant="outline" onClick={onRequest}>Cere propunere AI</Button>
    ) : null
  }
  return (
    <div data-testid="ai-proposal" className="space-y-2 rounded-lg border border-sky-500/30 bg-sky-500/10 p-3 text-sm">
      {(proposal.status === 'QUEUED' || proposal.status === 'RUNNING') && (
        <p role="status" className="text-sky-900 dark:text-sky-100">
          AI-ul citește rezolvarea… propunerea apare aici în câteva minute.
        </p>
      )}
      {proposal.status === 'FAILED' && (
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-destructive">AI-ul nu a putut propune: {proposal.error}</p>
          {!readOnly && <Button type="button" size="xs" variant="outline" onClick={onRequest}>Cere din nou</Button>}
        </div>
      )}
      {proposal.status === 'DONE' && (
        <>
          <p className="font-medium text-sky-900 dark:text-sky-100">
            Propunere AI: {proposal.points} / {max} p
            {proposal.confidence && <span className="font-normal"> · {CONFIDENCE[proposal.confidence]}</span>}
          </p>
          {proposal.reason && <MathContent className="text-sky-900 dark:text-sky-100">{proposal.reason}</MathContent>}
          {!readOnly && (
            <div className="flex flex-wrap items-center gap-2">
              <Button type="button" size="sm" onClick={onAccept}>Acceptă propunerea AI</Button>
              <span className="text-xs text-muted-foreground">sau Enter pe o cerință ia doar punctajul ei; o cifră îl schimbă.</span>
            </div>
          )}
        </>
      )}
    </div>
  )
}
