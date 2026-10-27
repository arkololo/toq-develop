import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LanguageProvider } from '../src/app/Language'
import { RecipePage } from '../src/app/RecipePage'
import { authenticatedFetch } from '../src/auth/keycloak'

vi.mock('../src/auth/keycloak', () => ({ authenticatedFetch: vi.fn() }))
const original = { id: '42', title: 'Pasta salad', originalTitle: 'Pasta salad', category: 'Vegetarian', area: 'Italian', imageUrl: null, ingredients: [{ name: 'Tomato', measure: '2 pieces' }], steps: ['Cut tomatoes.'], translated: false }

describe('TheMealDB translation', () => {
  beforeEach(() => {
    vi.mocked(authenticatedFetch).mockImplementation(async path => {
      if (path === '/api/v1/recipes?size=50') return Response.json([])
      if (path === '/api/v1/recipes/templates?query=') return Response.json([{ id: '42', title: 'Pasta salad', imageUrl: null }])
      if (path === '/api/v1/recipes/templates/42') return Response.json(original)
      if (path === '/api/v1/assistant/templates/42/translate') return Response.json({ id: '42', title: 'Salade de pâtes' })
      return Response.json([])
    })
  })

  it('keeps the original recipe visible when the translation response is incomplete', async () => {
    window.localStorage.setItem('toq-language', 'fr')
    render(<LanguageProvider><RecipePage /></LanguageProvider>)
    fireEvent.click(screen.getByRole('button', { name: 'Idées de plats en ligne' }))
    fireEvent.click(await screen.findByRole('button', { name: /Pasta salad/ }))
    fireEvent.click(await screen.findByRole('button', { name: /Traduire cette recette/ }))
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('traduction reçue est incomplète'))
    expect(screen.getByRole('heading', { name: 'Pasta salad' })).toBeInTheDocument()
    expect(screen.getByText('Cut tomatoes.')).toBeInTheDocument()
  })

  it('displays a valid French translation without losing the recipe detail', async () => {
    window.localStorage.setItem('toq-language', 'fr')
    vi.mocked(authenticatedFetch).mockImplementation(async path => {
      if (path === '/api/v1/recipes?size=50') return Response.json([])
      if (path === '/api/v1/recipes/templates?query=') return Response.json([{ id: '42', title: 'Pasta salad', imageUrl: null }])
      if (path === '/api/v1/recipes/templates/42') return Response.json(original)
      if (path === '/api/v1/assistant/templates/42/translate') return Response.json({ ...original, title: 'Salade de pâtes', ingredients: [{ name: 'Tomate', measure: '2 pièces' }], steps: ['Couper les tomates.'], translated: true })
      return Response.json([])
    })
    render(<LanguageProvider><RecipePage /></LanguageProvider>)
    fireEvent.click(screen.getByRole('button', { name: 'Idées de plats en ligne' }))
    fireEvent.click(await screen.findByRole('button', { name: /Pasta salad/ }))
    fireEvent.click(await screen.findByRole('button', { name: /Traduire cette recette/ }))
    expect(await screen.findByRole('heading', { name: 'Salade de pâtes' })).toBeInTheDocument()
    expect(screen.getByText('Couper les tomates.')).toBeInTheDocument()
  })

  it('offers an English display without calling Gemini or changing the source', async () => {
    window.localStorage.setItem('toq-language', 'en')
    render(<LanguageProvider><RecipePage /></LanguageProvider>)
    fireEvent.click(screen.getByRole('button', { name: 'Online recipe ideas' }))
    fireEvent.click(await screen.findByRole('button', { name: /Pasta salad/ }))
    fireEvent.click(await screen.findByRole('button', { name: /Show recipe in English/ }))
    expect(screen.getByRole('heading', { name: 'Pasta salad' })).toBeInTheDocument()
    expect(vi.mocked(authenticatedFetch).mock.calls.some(([path]) => path === '/api/v1/assistant/templates/42/translate')).toBe(false)
  })
})
