import { describe, expect, it } from 'vitest'
import { CALIBRATION_MIN, calibrationLine } from '@/lib/confidence'

describe('calibrationLine', () => {
  it('says nothing until there are enough sure answers', () => {
    expect(calibrationLine(null)).toBeNull()
    expect(calibrationLine(undefined)).toBeNull()
    expect(calibrationLine({ sure: CALIBRATION_MIN - 1, sureCorrect: CALIBRATION_MIN - 1 })).toBeNull()
  })

  it('says how many of the sure answers were right', () => {
    expect(calibrationLine({ sure: 10, sureCorrect: 8 })).toBe('Când ai zis „Știu sigur”, ai avut dreptate la 8 din 10.')
  })
})
