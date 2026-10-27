import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AssistantWidget } from '../src/app/AssistantWidget'
import { authenticatedFetch } from '../src/auth/keycloak'

vi.mock('../src/auth/keycloak', () => ({ authenticatedFetch: vi.fn() }))

describe('Assistant TOQi', () => {
  it('affiche une proposition sans écrire avant confirmation', async () => {
    const applied = vi.fn()
    vi.mocked(authenticatedFetch).mockImplementation(async (path, options) => {
      if (path === '/api/v1/assistant/status') return Response.json({ enabled: true })
      if (path === '/api/v1/assistant/chat') return Response.json({ reply: 'Je peux créer cette recette.', proposal: { id: 'proposal-1', type: 'CREATE_RECIPE', summary: 'Créer la recette « Soupe »' } })
      if (path === '/api/v1/assistant/actions/proposal-1/confirm' && options?.method === 'POST') return Response.json({ message: 'Recette créée.' })
      return new Response(null, { status: 404 })
    })
    render(<AssistantWidget onActionApplied={applied} />)
    fireEvent.click(screen.getByRole('button', { name: 'Ouvrir TOQi' }))
    await screen.findByText(/Vos messages et le contexte utile/)
    fireEvent.change(screen.getByRole('textbox', { name: 'Votre message à TOQi' }), { target: { value: 'Crée une soupe' } })
    fireEvent.click(screen.getByRole('button', { name: 'Envoyer à TOQi' }))
    await screen.findByText('Créer la recette « Soupe »')
    expect(applied).not.toHaveBeenCalled()
    expect(vi.mocked(authenticatedFetch).mock.calls.some(([path]) => path.includes('/confirm'))).toBe(false)
    fireEvent.click(screen.getByRole('button', { name: 'Confirmer cette action' }))
    await waitFor(() => expect(applied).toHaveBeenCalledWith('CREATE_RECIPE'))
    expect(screen.getByText('Recette créée.')).toBeInTheDocument()
  })
  it('montre les recettes d’un lot avant de les ajouter', async () => {
    vi.mocked(authenticatedFetch).mockClear()
    vi.mocked(authenticatedFetch).mockImplementation(async (path) => {
      if (path === '/api/v1/assistant/status') return Response.json({ enabled: true })
      if (path === '/api/v1/assistant/chat') return Response.json({ reply: 'Deux recettes proposées.', proposal: {
        id: 'batch-1', type: 'CREATE_RECIPES', summary: 'Ajouter deux recettes', preview: { recipes: [
          { title: 'Porridge', baseServings: 1, ingredients: [{ name: 'Avoine', quantity: 50, unit: 'g' }], steps: ['Cuire l’avoine.'] },
          { title: 'Salade', baseServings: 2, ingredients: [{ name: 'Tomate', quantity: 2, unit: 'pièce' }], steps: ['Couper les tomates.'] },
        ] },
      } })
      return new Response(null, { status: 404 })
    })
    render(<AssistantWidget onActionApplied={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: 'Ouvrir TOQi' }))
    await screen.findByText(/Vos messages et le contexte utile/)
    fireEvent.change(screen.getByRole('textbox', { name: 'Votre message à TOQi' }), { target: { value: 'Deux recettes' } })
    fireEvent.click(screen.getByRole('button', { name: 'Envoyer à TOQi' }))
    await screen.findByText('Porridge')
    expect(screen.getByText('Salade')).toBeInTheDocument()
    expect(vi.mocked(authenticatedFetch).mock.calls.some(([path]) => path.includes('/confirm'))).toBe(false)
  })
})
