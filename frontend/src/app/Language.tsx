import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { LanguageContext, useLanguage } from './LanguageContext'
import type { Language } from './LanguageContext'

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [language, setLanguage] = useState<Language>(() => window.localStorage.getItem('toq-language') === 'en' ? 'en' : 'fr')
  useEffect(() => {
    document.documentElement.lang = language
    window.localStorage.setItem('toq-language', language)
  }, [language])
  return <LanguageContext.Provider value={{ language, setLanguage }}>{children}</LanguageContext.Provider>
}

export function LanguageButton({ className = '' }: { className?: string }) {
  const { language, setLanguage } = useLanguage()
  return <button type="button" className={className} aria-label={language === 'fr' ? 'Switch site to English' : 'Passer le site en français'}
    onClick={() => setLanguage(language === 'fr' ? 'en' : 'fr')}>🌐 {language === 'fr' ? 'EN' : 'FR'}</button>
}
