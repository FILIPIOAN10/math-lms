import { Fragment, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { usePageTitle } from '@/lib/usePageTitle'

export interface Crumb {
  label: string
  /** A step with its own address. */
  to?: string
  /** A step inside the page (e.g. back from a chapter to its book), when there is no address for it. */
  onClick?: () => void
}

const crumbLink = 'hover:text-foreground hover:underline'

/**
 * The top of every page, the same everywhere: an optional trail back ("Quiz-uri / Simulare EN"), the page's one
 * <h1>, a line saying what the page is for, and the page's own actions on the right (they wrap under on a phone).
 * It also names the browser tab after the page.
 */
export function PageHeader({
  title,
  description,
  breadcrumbs,
  actions,
  documentTitle,
}: {
  title: string
  description?: ReactNode
  breadcrumbs?: Crumb[]
  actions?: ReactNode
  /** The tab title when it should differ from the heading. */
  documentTitle?: string
}) {
  usePageTitle(documentTitle ?? title)

  return (
    <div className="space-y-1">
      {breadcrumbs && breadcrumbs.length > 0 && (
        <nav aria-label="Unde ești" className="flex flex-wrap items-center gap-1 text-sm text-muted-foreground">
          {breadcrumbs.map((crumb, i) => {
            const last = i === breadcrumbs.length - 1
            return (
              <Fragment key={`${i}-${crumb.label}`}>
                {i > 0 && <span aria-hidden="true">/</span>}
                {last ? (
                  <span aria-current="page" className="text-foreground">{crumb.label}</span>
                ) : crumb.to ? (
                  <Link to={crumb.to} className={crumbLink}>{crumb.label}</Link>
                ) : crumb.onClick ? (
                  <button type="button" onClick={crumb.onClick} className={crumbLink}>{crumb.label}</button>
                ) : (
                  <span>{crumb.label}</span>
                )}
              </Fragment>
            )
          })}
        </nav>
      )}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 space-y-1">
          <h1 className="text-2xl font-semibold break-words">{title}</h1>
          {description && <p className="text-sm text-muted-foreground">{description}</p>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </div>
    </div>
  )
}
