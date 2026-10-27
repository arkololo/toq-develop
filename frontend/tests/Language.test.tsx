import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { LanguageButton, LanguageProvider } from '../src/app/Language'
import { useLanguage } from '../src/app/LanguageContext'

function Sample() {
  const { t } = useLanguage()
  return <><LanguageButton /><p>{t('Bonjour', 'Hello')}</p></>
}

describe('language switch', () => {
  it('switches visible copy, document language and saved preference', () => {
    window.localStorage.removeItem('toq-language')
    render(<LanguageProvider><Sample /></LanguageProvider>)
    expect(screen.getByText('Bonjour')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Switch site to English' }))
    expect(screen.getByText('Hello')).toBeInTheDocument()
    expect(document.documentElement.lang).toBe('en')
    expect(window.localStorage.getItem('toq-language')).toBe('en')
  })
})
