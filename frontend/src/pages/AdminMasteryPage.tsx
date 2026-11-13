import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Card, CardContent } from '@/components/ui/card'
import { ErrorState } from '@/components/ErrorState'
import { MasteryLegend } from '@/components/MasteryLegend'
import { EnMasteryGrid } from '@/components/EnMasteryGrid'
import { ClassAverageCell } from '@/components/ClassAverageCell'
import { PageHeader } from '@/components/PageHeader'
import { HomeSection } from '@/components/home/HomeBits'
import {
  createCatchUpAssignment, downloadClassGrades, getClassEnMastery, getClassMastery, getClassReview, listAssignments, listClasses, listQuizzes,
  type AssignmentSummary, type ClassMastery, type EnClassMastery, type QuizSummary, type StudentReviewStatus,
} from '@/lib/api'
import { Button } from '@/components/ui/button'
import { CatchUpDialog } from '@/components/CatchUpDialog'
import { weakStudentIds } from '@/lib/catchUp'
import { formatDate, roCount } from '@/lib/format'
import { errorMessage } from '@/lib/errors'
import { saveBlob } from '@/lib/download'
import { classAverage, MASTERY_LABELS, MASTERY_TONES, masteryLevel } from '@/lib/mastery'
import { LoadingCards } from '@/components/LoadingCards'

/** The class average of one chapter: the mean percent of the students who have data there. */
function average(grid: ClassMastery, column: number) {
  return classAverage(grid.students.map((s) => s.cells[column]?.percent))
}

/**
 * The teacher's mastery map for one class: students down, the class's chapters across (grouped by book), each cell
 * coloured by level and labelled with the percent - so "Ana is weak at fractions" is visible at a glance. The bottom
 * row is the class average per chapter. Scrolls sideways on a narrow screen, with the names pinned.
 */
export function AdminMasteryPage() {
  const { classId } = useParams<{ classId: string }>()
  const id = Number(classId)
  const [grid, setGrid] = useState<ClassMastery | null>(null)
  const [className, setClassName] = useState<string | null>(null)
  const [review, setReview] = useState<StudentReviewStatus[] | null>(null)
  const [enGrid, setEnGrid] = useState<EnClassMastery | null>(null)
  const [error, setError] = useState<string | null>(null)
  // Catch-up homework: the chapter column whose dialog is open, the quizzes it offers (loaded on first open), the result.
  const [catchUpColumn, setCatchUpColumn] = useState<number | null>(null)
  const [quizzes, setQuizzes] = useState<QuizSummary[] | null>(null)
  const [givenQuizIds, setGivenQuizIds] = useState<number[]>([])
  const [catchUpError, setCatchUpError] = useState<string | null>(null)
  const [given, setGiven] = useState<AssignmentSummary | null>(null)
  // "Descarcă notele (CSV)": a one-off action, so its state stays here.
  const [downloading, setDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState<string | null>(null)

  async function downloadGrades() {
    setDownloading(true)
    setDownloadError(null)
    try {
      saveBlob(await downloadClassGrades(id), `note-${className ?? `clasa-${id}`}.csv`)
    } catch (e) {
      setDownloadError(errorMessage(e))
    } finally {
      setDownloading(false)
    }
  }

  function openCatchUp(column: number) {
    setGiven(null)
    setCatchUpError(null)
    // Fresh each time: a homework given a moment ago must already count as given.
    Promise.all([quizzes ?? listQuizzes(), listAssignments()])
      .then(([list, assignments]) => {
        setQuizzes(list)
        setGivenQuizIds(assignments.filter((a) => a.schoolClassId === id).map((a) => a.quizId))
        setCatchUpColumn(column)
      })
      .catch((e) => setCatchUpError(errorMessage(e)))
  }

  useEffect(() => {
    let cancelled = false
    getClassMastery(id)
      .then((g) => !cancelled && setGrid(g))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    listClasses()
      .then((list) => !cancelled && setClassName(list.find((c) => c.id === id)?.name ?? null))
      .catch(() => undefined)
    getClassReview(id)
      .then((r) => !cancelled && setReview(r))
      .catch(() => !cancelled && setReview([]))
    getClassEnMastery(id)
      .then((g) => !cancelled && setEnGrid(g))
      .catch(() => undefined) // the class chapters above still work without it
    return () => {
      cancelled = true
    }
  }, [id])

  const title = className ? `Pe capitole — ${className}` : 'Pe capitole'
  const books: { title: string; span: number }[] = []
  for (const chapter of grid?.chapters ?? []) {
    const last = books[books.length - 1]
    if (last && last.title === chapter.bookTitle) last.span++
    else books.push({ title: chapter.bookTitle, span: 1 })
  }
  const hasData = grid?.students.some((s) => s.cells.some((c) => c.percent !== null)) ?? false

  return (
    <div className="p-4">
      <div className="mx-auto max-w-6xl space-y-4">
        <PageHeader
          title={title}
          description="Nivelul fiecărui elev pe fiecare capitol: ultimul răspuns la fiecare întrebare legată de capitol, din teste și din exersare."
          breadcrumbs={[{ label: 'Clase', to: '/admin/content' }, { label: className ?? 'Clasă' }, { label: 'Pe capitole' }]}
          actions={
            <Button type="button" variant="outline" size="sm" onClick={() => void downloadGrades()} disabled={downloading}>
              {downloading ? 'Se descarcă...' : 'Descarcă notele (CSV)'}
            </Button>
          }
        />
        {downloadError && <p role="alert" className="text-sm text-destructive">{downloadError}</p>}
        {error && <ErrorState message={error} />}
        {!grid && !error && <LoadingCards />}

        {grid && grid.chapters.length === 0 && (
          <p className="text-sm text-muted-foreground">Clasa nu are încă capitole. Adaugă cărți și capitole din Conținut.</p>
        )}
        {grid && grid.chapters.length > 0 && grid.students.length === 0 && (
          <p className="text-sm text-muted-foreground">Niciun elev înscris în clasă.</p>
        )}
        {grid && grid.chapters.length > 0 && grid.students.length > 0 && (
          <>
            {!hasData && (
              <p className="text-sm text-muted-foreground">
                Nimic de afișat încă. Leagă testele de capitole (la „Editează detaliile” sau la fiecare întrebare) — doar
                răspunsurile la întrebările cu capitol intră pe hartă.
              </p>
            )}
            <Card>
              <CardContent className="overflow-x-auto p-0">
                <table aria-label="Nivel pe capitole" className="w-full border-collapse text-sm">
                  <thead>
                    <tr>
                      <th rowSpan={2} scope="col" className="sticky left-0 z-10 bg-card px-3 py-2 text-left font-medium">Elev</th>
                      {books.map((book, i) => (
                        <th key={`${book.title}-${i}`} colSpan={book.span} scope="colgroup"
                            className="border-l px-3 pt-2 text-center text-xs font-medium text-muted-foreground">
                          {book.title}
                        </th>
                      ))}
                    </tr>
                    <tr>
                      {grid.chapters.map((chapter) => (
                        <th key={chapter.id} scope="col" className="border-l px-3 pb-2 text-center font-medium whitespace-nowrap">
                          {chapter.title}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {grid.students.map((student) => (
                      <tr key={student.studentId} className="border-t">
                        <th scope="row" className="sticky left-0 z-10 bg-card px-3 py-2 text-left font-medium whitespace-nowrap">
                          <Link to={`/admin/students/${student.studentId}`} className="hover:underline">{student.fullName}</Link>
                        </th>
                        {student.cells.map((cell, i) => {
                          const level = masteryLevel(cell.percent)
                          const chapter = grid.chapters[i]
                          const label = cell.percent === null
                            ? `${student.fullName}, ${chapter.title}: ${MASTERY_LABELS.none}`
                            : `${student.fullName}, ${chapter.title}: ${cell.percent}%, ${MASTERY_LABELS[level]}`
                          return (
                            <td key={cell.chapterId} aria-label={label} title={label}
                                className={`border-l px-3 py-2 text-center font-medium tabular-nums ${MASTERY_TONES[level]}`}>
                              {cell.percent === null ? '—' : `${cell.percent}%`}
                            </td>
                          )
                        })}
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr className="border-t-2">
                      <th scope="row" className="sticky left-0 z-10 bg-card px-3 py-2 text-left font-medium">Media clasei</th>
                      {grid.chapters.map((chapter, i) => (
                        <ClassAverageCell key={chapter.id} average={average(grid, i)} />
                      ))}
                    </tr>
                    <tr className="border-t">
                      <th scope="row" className="sticky left-0 z-10 bg-card px-3 py-2 text-left font-medium">Recuperare</th>
                      {grid.chapters.map((chapter, i) => {
                        const weak = weakStudentIds(grid, i).length
                        return (
                          <td key={chapter.id} className="border-l px-2 py-1 text-center">
                            <Button type="button" size="sm" variant={weak > 0 ? 'outline' : 'ghost'}
                                    aria-label={`Temă de recuperare la ${chapter.title}`}
                                    onClick={() => openCatchUp(i)}>
                              {weak > 0 ? `Temă (${weak})` : 'Temă'}
                            </Button>
                          </td>
                        )
                      })}
                    </tr>
                  </tfoot>
                </table>
              </CardContent>
            </Card>
            <MasteryLegend />
            <p className="text-xs text-muted-foreground">
              „Temă (n)” dă o temă doar celor n elevi sub 50% la capitol; îi poți bifa sau debifa înainte.
            </p>
            {catchUpError && <p className="text-sm text-destructive">{catchUpError}</p>}
            {given && (
              <p role="status" className="text-sm">
                Tema „{given.quizTitle}” a fost dată la {roCount(given.forStudents, 'elev', 'elevi')},
                termen {formatDate(given.dueAt)}.{' '}
                <Link to={`/admin/assignments?open=${given.id}`} className="underline">Vezi tema</Link>
              </p>
            )}
            {catchUpColumn !== null && quizzes && (
              <CatchUpDialog
                open
                onClose={() => setCatchUpColumn(null)}
                grid={grid}
                column={catchUpColumn}
                classId={id}
                quizzes={quizzes}
                givenQuizIds={givenQuizIds}
                onCreated={setGiven}
                createCatchUpAssignment={createCatchUpAssignment}
              />
            )}
          </>
        )}

        {enGrid && enGrid.chapters.length > 0 && (
          <HomeSection title="Pe capitolele EN">
            <EnMasteryGrid grid={enGrid} />
          </HomeSection>
        )}

        {review && review.length > 0 && (
          <HomeSection title="Recapitulare zilnică">
            <Card>
              <CardContent className="overflow-x-auto p-0">
                <table aria-label="Recapitulare zilnică pe elevi" className="w-full border-collapse text-sm">
                  <thead>
                    <tr className="text-left text-xs text-muted-foreground">
                      <th scope="col" className="px-3 py-2 font-medium">Elev</th>
                      <th scope="col" className="px-3 py-2 font-medium">Azi</th>
                      <th scope="col" className="px-3 py-2 font-medium">Seria</th>
                      <th scope="col" className="px-3 py-2 font-medium">Stăpânite</th>
                      <th scope="col" className="px-3 py-2 font-medium">Ultima recapitulare</th>
                    </tr>
                  </thead>
                  <tbody>
                    {review.map((r) => {
                      const due = r.doneToday + r.remainingToday
                      return (
                        <tr key={r.studentId} className="border-t">
                          <th scope="row" className="px-3 py-2 text-left font-medium whitespace-nowrap">
                            <Link to={`/admin/students/${r.studentId}`} className="hover:underline">{r.fullName}</Link>
                          </th>
                          <td className={`px-3 py-2 tabular-nums ${r.remainingToday === 0 && r.doneToday > 0 ? 'text-emerald-700 dark:text-emerald-300' : ''}`}>
                            {due === 0 ? '—' : `${r.doneToday} / ${due}`}
                          </td>
                          <td className="px-3 py-2">{r.streak === 0 ? '—' : roCount(r.streak, 'zi', 'zile')}</td>
                          <td className="px-3 py-2 tabular-nums">{r.mastered} din {r.totalCards}</td>
                          <td className="px-3 py-2 text-muted-foreground">
                            {r.lastReviewOn === null ? 'niciodată' : r.lastReviewOn.split('-').reverse().join('.')}
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </CardContent>
            </Card>
            <p className="text-xs text-muted-foreground">
              „Azi” = recapitulate / de recapitulat azi (maxim 10 pe zi). Seria = zile la rând cu cel puțin o recapitulare.
            </p>
          </HomeSection>
        )}
      </div>
    </div>
  )
}
