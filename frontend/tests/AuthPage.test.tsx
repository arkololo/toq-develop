import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthPage } from '../src/app/AuthPage'
import { LanguageProvider } from '../src/app/Language'
import { keycloak } from '../src/auth/keycloak'

vi.mock('../src/auth/keycloak', () => ({ keycloak: { login: vi.fn(), register: vi.fn() } }))

describe('TOQ authentication pages', () => {
  beforeEach(() => { vi.clearAllMocks(); window.localStorage.setItem('toq-language', 'fr') })

  it('starts the Keycloak login flow with the selected locale', () => {
    render(<LanguageProvider><AuthPage initialMode="login" /></LanguageProvider>)
    fireEvent.click(screen.getByRole('button', { name: /Me connecter/ }))
    expect(keycloak.login).toHaveBeenCalledWith({ locale: 'fr' })
  })

  it('starts registration at Keycloak, without a TOQ password field', () => {
    render(<LanguageProvider><AuthPage initialMode="register" /></LanguageProvider>)
    fireEvent.click(screen.getByRole('button', { name: /Créer mon compte/ }))
    expect(keycloak.register).toHaveBeenCalledWith({ locale: 'fr' })
    expect(screen.queryByLabelText(/mot de passe/i)).not.toBeInTheDocument()
  })
})
