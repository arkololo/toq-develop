import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { GoalGauges } from '../src/app/DashboardPanels'

describe('Jauges des objectifs', () => {
  it('distingue une cible en cours, atteinte et dépassée', () => {
    render(<GoalGauges goal={{ validFrom: '2026-09-01', validTo: null, kcal: 2000, proteinG: 100, carbohydrateG: 200, fatG: 70 }}
      day={{ date: '2026-09-29', kcal: 1500, proteinG: 100, carbohydrateG: 240, fatG: 40, complete: true, mealCount: 2 }} />)
    expect(screen.getByRole('progressbar', { name: 'Calories planifiées' })).toHaveClass('under')
    expect(screen.getByRole('progressbar', { name: 'Protéines planifiées' })).toHaveClass('reached')
    expect(screen.getByRole('progressbar', { name: 'Glucides planifiées' })).toHaveClass('over')
    expect(screen.getByText(/120 % · objectif dépassé/)).toBeInTheDocument()
  })
})
