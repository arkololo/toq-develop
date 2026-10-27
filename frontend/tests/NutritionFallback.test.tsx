import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { NutritionPage } from '../src/app/NutritionPage'

vi.mock('../src/auth/keycloak', () => ({
  authenticatedFetch: vi.fn(async (path: string) => {
    if (path.includes('/products/')) return Response.json({ barcode: '3017620422003', name: 'Produit test', kcal100g: 200, proteinG100g: null, carbohydrateG100g: null, fatG100g: 8, allergens: '', labels: '', cached: false })
    if (path.includes('/ciqual?')) return Response.json([{ code: '123', name: 'Pâte à tartiner, aliment moyen', kcal100g: 400, proteinG100g: 6, carbohydrateG100g: 45, fatG100g: 23 }])
    return Response.json([])
  }),
}))

describe('Repli CIQUAL', () => {
  it('garde les valeurs produit OFF et étiquette les autres comme estimation', async () => {
    render(<NutritionPage />)
    fireEvent.change(screen.getByRole('textbox', { name: 'Code-barres du produit' }), { target: { value: '3017620422003' } })
    fireEvent.click(screen.getByRole('button', { name: 'Rechercher' }))
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Produit test' })).toBeInTheDocument())
    fireEvent.change(screen.getByRole('textbox', { name: 'Aliment équivalent' }), { target: { value: 'pâte' } })
    fireEvent.click(screen.getByRole('button', { name: 'Chercher dans CIQUAL' }))
    fireEvent.click(await screen.findByRole('option', { name: /Pâte à tartiner, aliment moyen/ }))
    expect(screen.getAllByText('Produit · OFF')).toHaveLength(2)
    expect(screen.getAllByText('Estimation · CIQUAL')).toHaveLength(2)
    expect(screen.getByText('200 kcal')).toBeInTheDocument()
    expect(screen.getAllByText('6 g').length).toBeGreaterThan(0)
  })
})
