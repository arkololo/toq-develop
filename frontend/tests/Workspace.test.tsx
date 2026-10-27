import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ProfilePage } from '../src/app/Profile'
import { authenticatedFetch } from '../src/auth/keycloak'

vi.mock('../src/auth/keycloak', () => ({
  keycloak: { tokenParsed: { preferred_username: 'alice' }, logout: vi.fn() },
  authenticatedFetch: vi.fn(async (path: string) => {
    if (path === '/api/v1/users/me') return Response.json({ id: 'u1', displayName: 'Alice', version: 1 })
    if (path === '/api/v1/users/me/preferences') return Response.json({ dietaryPreferenceCodes: [], allergyCodes: [], excludedIngredientIds: [] })
    if (path === '/api/v1/users/me/nutrition-goals') return Response.json([])
    if (path.startsWith('/api/v1/plans/') && path.endsWith('/shopping-list')) return Response.json({ id: null, stale: true, items: [] })
    if (path.startsWith('/api/v1/social/publications')) return Response.json([])
    return Response.json([])
  }),
}))

describe('Espace connecté', () => {
  it('rend les pages nutrition, planning, recettes et partage accessibles depuis la barre latérale', async () => {
    render(<ProfilePage />)
    await waitFor(() => expect(screen.getByRole('navigation', { name: 'Navigation principale' })).toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: 'Nutrition' }))
    expect(screen.getByRole('heading', { name: 'Objectifs et nutrition' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Rechercher' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Planning' }))
    expect(screen.getByRole('heading', { name: 'Organisez votre semaine' })).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Liste de courses' })).toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: 'Recettes' }))
    expect(screen.getByRole('heading', { name: 'Mes recettes' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Ajouter un plat/ }))
    expect(screen.getByLabelText(/Photo du plat/)).toHaveAttribute('type', 'file')
    expect(screen.getByRole('textbox', { name: 'Aliment 1' })).toBeInTheDocument()
    expect(screen.getByRole('spinbutton', { name: 'Calories (kcal)' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Ajouter un ingrédient/ }))
    expect(screen.getByRole('textbox', { name: 'Aliment 2' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Partager' }))
    expect(screen.getByRole('heading', { name: 'La table TOQ' })).toBeInTheDocument()
    expect(screen.getByLabelText(/Choisir une autre photo/)).toHaveAttribute('type', 'file')
    expect(screen.getByRole('button', { name: 'Mes favoris' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Paramètres' }))
    fireEvent.click(screen.getByText(/Habitudes alimentaires/))
    expect(screen.getByRole('checkbox', { name: 'Végétarien' })).toBeInTheDocument()
  })

  it('impose un pseudo avant de montrer les pages au premier accès', async () => {
    vi.mocked(authenticatedFetch).mockResolvedValueOnce(new Response(null, { status: 404 }))
    render(<ProfilePage />)
    expect(await screen.findByRole('heading', { name: /Comment souhaitez-vous/ })).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Navigation principale' })).not.toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Pseudo obligatoire' })).toBeRequired()
  })
})
