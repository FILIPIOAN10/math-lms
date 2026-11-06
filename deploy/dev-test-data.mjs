// LOCAL TEST DATA, never run it against a real server. Six students hand in one test through the real API, so the
// per-option counts, the suspect-answer warning, common mistakes and numeric answers have something to show.
// Needs the accounts elev1..elev6@mathlms.local (the guide creates them). A new test each run.
// Run from math-lms/:  node deploy/dev-test-data.mjs
const API = process.env.API ?? 'http://localhost:8080'
const PASSWORD = process.env.TEST_PASSWORD ?? 'Admin123!' // the dev seed password, deploy/e2e-seed.sql

class Client {
  jar = new Map()

  async send(method, path, body) {
    const headers = { 'Content-Type': 'application/json', Cookie: [...this.jar].map(([k, v]) => `${k}=${v}`).join('; ') }
    if (this.jar.has('XSRF-TOKEN')) headers['X-XSRF-TOKEN'] = decodeURIComponent(this.jar.get('XSRF-TOKEN'))
    const res = await fetch(API + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
    for (const raw of res.headers.getSetCookie()) {
      const [pair] = raw.split(';')
      const at = pair.indexOf('=')
      this.jar.set(pair.slice(0, at), pair.slice(at + 1))
    }
    const text = await res.text()
    if (res.status >= 400) throw new Error(`${method} ${path} -> ${res.status} ${text.slice(0, 200)}`)
    return text ? JSON.parse(text) : null
  }

  async login(email) {
    await this.send('GET', '/api/auth/me').catch(() => null)
    await this.send('POST', '/api/auth/login', { email, password: PASSWORD })
    await this.send('GET', '/api/auth/me')
    return this
  }
}

const admin = await new Client().login('admin@mathlms.local')
const title = `Fracții — alarmă ${new Date().toLocaleTimeString('ro-RO')}`
const quiz = await admin.send('POST', '/api/admin/quizzes', { title, description: 'Date de test', practiceAllowed: true })
const fractions = await admin.send('POST', `/api/admin/quizzes/${quiz.id}/items`, {
  type: 'SINGLE_CHOICE', position: 0, statement: 'Cât face $\\frac{1}{2}+\\frac{1}{4}$?', points: 5, solution: 'Aduci la numitorul 4.',
  options: [
    { position: 0, text: '$\\frac{2}{6}$', correct: true }, // marked right on purpose: the warning must catch it
    { position: 1, text: '$\\frac{3}{4}$', correct: false },
    { position: 2, text: '$\\frac{1}{8}$', correct: false, mistake: 'Ai înmulțit fracțiile în loc să le aduni.' },
  ],
})
const easy = await admin.send('POST', `/api/admin/quizzes/${quiz.id}/items`, {
  type: 'SINGLE_CHOICE', position: 1, statement: 'Cât face $2 + 2$?', points: 5,
  options: [{ position: 0, text: '4', correct: true }, { position: 1, text: '5', correct: false }],
})
const numeric = await admin.send('POST', `/api/admin/quizzes/${quiz.id}/items`, {
  type: 'NUMERIC', position: 2, statement: 'Cât face $0,5 + 0,25$?', points: 5, solution: 'Aduni zecimile.', numericAnswer: '3/4',
})
await admin.send('POST', `/api/admin/quizzes/${quiz.id}/publish`, {})

const [a, b, c] = fractions.options
const [right, wrong] = easy.options
const papers = [
  ['elev1', b, right, '0,75'], // the three strongest agree on b), not on the marked a)
  ['elev2', b, right, '3/4'],
  ['elev3', b, right, '6/8'],
  ['elev4', a, wrong, '1'],
  ['elev5', a, wrong, '1'],
  ['elev6', c, wrong, 'nu stiu'],
]
for (const [name, first, second, number] of papers) {
  const student = await new Client().login(`${name}@mathlms.local`)
  const { attemptId } = await student.send('POST', `/api/quiz/quizzes/${quiz.id}/attempts`, {})
  await student.send('PUT', `/api/quiz/attempts/${attemptId}/responses/${fractions.id}`, { optionId: first.id })
  await student.send('PUT', `/api/quiz/attempts/${attemptId}/responses/${easy.id}`, { optionId: second.id })
  await student.send('PUT', `/api/quiz/attempts/${attemptId}/responses/${numeric.id}`, { value: number })
  const result = await student.send('POST', `/api/quiz/attempts/${attemptId}/submit`, {})
  console.log(`${name}: ${result.finalScore} p`)
}
console.log(`Gata: „${title}”. Statistici: http://localhost:5173/admin/quizzes/${quiz.id}/stats`)
