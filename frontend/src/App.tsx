import { lazy, Suspense } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from '@/context/AuthContext'
import { ProtectedRoute } from '@/components/ProtectedRoute'
import { AdminRoute } from '@/components/AdminRoute'
import { AppShell } from '@/components/AppShell'
import { RoleRoute } from '@/components/RoleRoute'
import { LoginPage } from '@/pages/LoginPage'
import { DashboardPage } from '@/pages/DashboardPage'

// Every other page is its own chunk, downloaded the first time it is opened:
// a student on a phone never downloads the admin pages.
const RegisterPage = lazy(() => import('@/pages/RegisterPage').then((m) => ({ default: m.RegisterPage })))
const ForgotPasswordPage = lazy(() => import('@/pages/ForgotPasswordPage').then((m) => ({ default: m.ForgotPasswordPage })))
const ResetPasswordPage = lazy(() => import('@/pages/ResetPasswordPage').then((m) => ({ default: m.ResetPasswordPage })))
const VerifyEmailPage = lazy(() => import('@/pages/VerifyEmailPage').then((m) => ({ default: m.VerifyEmailPage })))
const PendingPage = lazy(() => import('@/pages/PendingPage').then((m) => ({ default: m.PendingPage })))
const AdminPendingPage = lazy(() => import('@/pages/AdminPendingPage').then((m) => ({ default: m.AdminPendingPage })))
const AdminLinksPage = lazy(() => import('@/pages/AdminLinksPage').then((m) => ({ default: m.AdminLinksPage })))
const AdminContentPage = lazy(() => import('@/pages/AdminContentPage').then((m) => ({ default: m.AdminContentPage })))
const AdminQuizzesPage = lazy(() => import('@/pages/AdminQuizzesPage').then((m) => ({ default: m.AdminQuizzesPage })))
const AdminAssignmentsPage = lazy(() => import('@/pages/AdminAssignmentsPage').then((m) => ({ default: m.AdminAssignmentsPage })))
const ContentBrowserPage = lazy(() => import('@/pages/ContentBrowserPage').then((m) => ({ default: m.ContentBrowserPage })))
const StudentQuizListPage = lazy(() => import('@/pages/StudentQuizListPage').then((m) => ({ default: m.StudentQuizListPage })))
const TakeQuizPage = lazy(() => import('@/pages/TakeQuizPage').then((m) => ({ default: m.TakeQuizPage })))
const AttemptResultPage = lazy(() => import('@/pages/AttemptResultPage').then((m) => ({ default: m.AttemptResultPage })))
const AdminGradingPage = lazy(() => import('@/pages/AdminGradingPage').then((m) => ({ default: m.AdminGradingPage })))
const AdminQuizStatsPage = lazy(() => import('@/pages/AdminQuizStatsPage').then((m) => ({ default: m.AdminQuizStatsPage })))
const StudentProgressPage = lazy(() => import('@/pages/StudentProgressPage').then((m) => ({ default: m.StudentProgressPage })))
const ParentChildrenPage = lazy(() => import('@/pages/ParentChildrenPage').then((m) => ({ default: m.ParentChildrenPage })))
const ParentChildPage = lazy(() => import('@/pages/ParentChildPage').then((m) => ({ default: m.ParentChildPage })))
const ParentAttemptResultPage = lazy(() => import('@/pages/ParentAttemptResultPage').then((m) => ({ default: m.ParentAttemptResultPage })))

function LoginRoute() {
  const { user, loading } = useAuth()

  if (loading) {
    return null
  }
  if (user) {
    return <Navigate to="/" replace />
  }
  return <LoginPage />
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Suspense fallback={<p className="p-4 text-muted-foreground">Se încarcă...</p>}>
          <Routes>
          <Route path="/login" element={<LoginRoute />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/verify-email" element={<VerifyEmailPage />} />
          <Route path="/pending" element={<PendingPage />} />
          {/* Taking a test has no app header: its timer is pinned to the top and nothing should pull the student away. */}
          <Route
            path="/quizzes/:id/take"
            element={
              <RoleRoute role="STUDENT">
                <TakeQuizPage />
              </RoleRoute>
            }
          />
          <Route element={<AppShell />}>
            <Route
              path="/admin/pending"
              element={
                <AdminRoute>
                  <AdminPendingPage />
                </AdminRoute>
              }
            />
            <Route
              path="/admin/links"
              element={
                <AdminRoute>
                  <AdminLinksPage />
                </AdminRoute>
              }
            />
            <Route
              path="/content"
              element={
                <ProtectedRoute>
                  <ContentBrowserPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin/content"
              element={
                <AdminRoute>
                  <AdminContentPage />
                </AdminRoute>
              }
            />
            <Route
              path="/admin/quizzes"
              element={
                <AdminRoute>
                  <AdminQuizzesPage />
                </AdminRoute>
              }
            />
            <Route
              path="/admin/assignments"
              element={
                <AdminRoute>
                  <AdminAssignmentsPage />
                </AdminRoute>
              }
            />
            <Route
              path="/admin/quizzes/:quizId/stats"
              element={
                <AdminRoute>
                  <AdminQuizStatsPage />
                </AdminRoute>
              }
            />
            <Route
              path="/admin/grading"
              element={
                <AdminRoute>
                  <AdminGradingPage />
                </AdminRoute>
              }
            />
            <Route
              path="/quizzes"
              element={
                <RoleRoute role="STUDENT">
                  <StudentQuizListPage />
                </RoleRoute>
              }
            />
            <Route
              path="/progress"
              element={
                <RoleRoute role="STUDENT">
                  <StudentProgressPage />
                </RoleRoute>
              }
            />
            <Route
              path="/quizzes/attempts/:attemptId/result"
              element={
                <RoleRoute role="STUDENT">
                  <AttemptResultPage />
                </RoleRoute>
              }
            />
            <Route
              path="/parent"
              element={
                <RoleRoute role="PARENT">
                  <ParentChildrenPage />
                </RoleRoute>
              }
            />
            <Route
              path="/parent/children/:childId"
              element={
                <RoleRoute role="PARENT">
                  <ParentChildPage />
                </RoleRoute>
              }
            />
            <Route
              path="/parent/children/:childId/attempts/:attemptId"
              element={
                <RoleRoute role="PARENT">
                  <ParentAttemptResultPage />
                </RoleRoute>
              }
            />
            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <DashboardPage />
                </ProtectedRoute>
              }
            />
          </Route>
        </Routes>
        </Suspense>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
