import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from '@/context/AuthContext'
import { ProtectedRoute } from '@/components/ProtectedRoute'
import { AdminRoute } from '@/components/AdminRoute'
import { LoginPage } from '@/pages/LoginPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { ForgotPasswordPage } from '@/pages/ForgotPasswordPage'
import { ResetPasswordPage } from '@/pages/ResetPasswordPage'
import { VerifyEmailPage } from '@/pages/VerifyEmailPage'
import { PendingPage } from '@/pages/PendingPage'
import { AdminPendingPage } from '@/pages/AdminPendingPage'
import { AdminLinksPage } from '@/pages/AdminLinksPage'
import { ContentBrowserPage } from '@/pages/ContentBrowserPage'
import { DashboardPage } from '@/pages/DashboardPage'

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
        <Routes>
          <Route path="/login" element={<LoginRoute />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/verify-email" element={<VerifyEmailPage />} />
          <Route path="/pending" element={<PendingPage />} />
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
            path="/"
            element={
              <ProtectedRoute>
                <DashboardPage />
              </ProtectedRoute>
            }
          />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
