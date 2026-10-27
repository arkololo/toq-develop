import { render, screen } from '@testing-library/react'
import axe from 'axe-core'
import { describe, expect, it } from 'vitest'
import { App } from '../src/app/App'

describe('Présentation de TOQ', () => {
  it('offre une navigation complète vers des sections existantes', () => {
    render(<App />)

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Vos repas, l’esprit léger.')
    expect(screen.getByRole('main')).toHaveAttribute('id', 'contenu')

    for (const link of screen.getAllByRole('link')) {
      const href = link.getAttribute('href')
      if (href?.startsWith('#')) expect(document.getElementById(href.slice(1))).not.toBeNull()
      else expect(['/connexion', '/inscription']).toContain(href)
    }
    expect(screen.getByRole('link', { name: 'Aller au contenu' })).toHaveAttribute('href', '#contenu')
  })

  it('respecte les règles sémantiques d’accessibilité WCAG A et AA', async () => {
    const { container } = render(<App />)
    const results = await axe.run(container, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21aa'] },
      // Le contraste et la géométrie nécessitent un navigateur réel, pas jsdom.
      rules: { 'color-contrast': { enabled: false } },
    })

    expect(results.violations).toEqual([])
  })
})
