import { createContext, useContext } from 'react'

export type Language = 'fr' | 'en'
export const LanguageContext = createContext<{ language: Language; setLanguage: (language: Language) => void }>({ language: 'fr', setLanguage: () => {} })

export function useLanguage() {
  const { language, setLanguage } = useContext(LanguageContext)
  return { language, setLanguage, t: (fr: string, en: string) => language === 'fr' ? fr : en }
}
