import { useState } from 'react'
import type { FormEvent } from 'react'
import { authenticatedFetch } from '../auth/keycloak'
import { useLanguage } from './LanguageContext'

type Product = { barcode: string; name: string; kcal100g: number | null; proteinG100g: number | null; carbohydrateG100g: number | null; fatG100g: number | null; allergens: string; labels: string; cached: boolean }
type CiqualFood = { code: string; name: string; kcal100g: number | null; proteinG100g: number | null; carbohydrateG100g: number | null; fatG100g: number | null }
const nutrients = [
  ['kcal100g', 'Énergie', 'kcal'], ['proteinG100g', 'Protéines', 'g'],
  ['carbohydrateG100g', 'Glucides', 'g'], ['fatG100g', 'Lipides', 'g'],
] as const

export function NutritionPage() {
  const { t } = useLanguage()
  const [barcode, setBarcode] = useState('')
  const [product, setProduct] = useState<Product | null>(null)
  const [ciqualQuery, setCiqualQuery] = useState('')
  const [candidates, setCandidates] = useState<CiqualFood[]>([])
  const [equivalent, setEquivalent] = useState<CiqualFood | null>(null)
  const [message, setMessage] = useState('')
  const [ciqualMessage, setCiqualMessage] = useState('')
  const [loading, setLoading] = useState(false)
  const [ciqualLoading, setCiqualLoading] = useState(false)

  async function searchProduct(event: FormEvent) {
    event.preventDefault()
    if (!/^\d{8,14}$/.test(barcode)) { setMessage(t('Saisissez un code-barres de 8 à 14 chiffres.', 'Enter a barcode of 8 to 14 digits.')); return }
    setLoading(true); setMessage(''); setProduct(null); setEquivalent(null); setCandidates([])
    try {
      const response = await authenticatedFetch(`/api/v1/nutrition/products/${barcode}`)
      if (response.status === 404) throw new Error(t('Aucun produit OFF trouvé. Vous pouvez chercher un aliment équivalent dans CIQUAL ci-dessous.', 'No OFF product found. You can search for a similar food in CIQUAL below.'))
      if (!response.ok) throw new Error(t('La recherche du produit est momentanément indisponible.', 'Product search is temporarily unavailable.'))
      setProduct(await response.json() as Product)
    } catch (error) { setMessage((error as Error).message) } finally { setLoading(false) }
  }
  async function searchCiqual(event: FormEvent) {
    event.preventDefault()
    if (ciqualQuery.trim().length < 2) { setCiqualMessage(t('Saisissez au moins deux caractères.', 'Enter at least two characters.')); return }
    setCiqualLoading(true); setCiqualMessage(''); setCandidates([])
    try {
      const response = await authenticatedFetch(`/api/v1/nutrition/ciqual?query=${encodeURIComponent(ciqualQuery.trim())}`)
      if (!response.ok) throw new Error(t('La recherche CIQUAL est momentanément indisponible.', 'CIQUAL search is temporarily unavailable.'))
      const results = await response.json() as CiqualFood[]
      setCandidates(results)
      if (!results.length) setCiqualMessage(t('Aucun aliment trouvé. Essayez un terme plus générique.', 'No food found. Try a more general term.'))
    } catch (error) { setCiqualMessage((error as Error).message) } finally { setCiqualLoading(false) }
  }
  const missing = product && nutrients.some(([key]) => product[key] == null)
  return <section className="workspace-card nutrition-page">
    <div className="page-title"><div><p className="eyebrow">Think · Explore</p><h2>{t('Comprendre les aliments', 'Understand foods')}</h2><p className="section-note">{t('Identifiez un produit avec Open Food Facts et complétez ses données manquantes avec un aliment équivalent CIQUAL.', 'Identify a product with Open Food Facts and fill missing values with a similar CIQUAL food.')}</p></div></div>
    <form className="nutrition-search" onSubmit={searchProduct}><label>{t('Code-barres du produit', 'Product barcode')}<input value={barcode} onChange={event => setBarcode(event.target.value.replace(/\D/g, ''))} maxLength={14} placeholder="Ex. 3274080005003" inputMode="numeric" /></label><button className="button-primary" type="submit" disabled={loading}>{loading ? t('Recherche…', 'Searching…') : t('Rechercher', 'Search')}</button></form>
    {message && <p className="form-message" role="status">{message}</p>}
    {product && <div className="nutrition-result"><div><span className="eyebrow">{t('Produit', 'Product')} · Open Food Facts</span><h3>{product.name}</h3><p>{t('Code-barres', 'Barcode')} {product.barcode}</p></div>
      <div className="nutrition-values">{nutrients.map(([key, label, unit]) => {
        const offValue = product[key]
        const estimated = offValue == null ? equivalent?.[key] ?? null : null
        return <div key={key}><strong>{offValue ?? estimated ?? t('Non renseigné', 'Unknown')}{offValue != null || estimated != null ? ` ${unit}` : ''}</strong><span>{t(label, key === 'kcal100g' ? 'Energy' : key === 'proteinG100g' ? 'Protein' : key === 'carbohydrateG100g' ? 'Carbs' : 'Fat')} / 100 g</span><small>{offValue != null ? t('Produit · OFF', 'Product · OFF') : estimated != null ? t('Estimation · CIQUAL', 'Estimate · CIQUAL') : t('Donnée absente', 'No data')}</small></div>
      })}</div>
      <div className="nutrition-facts"><p><strong>{t('Allergènes', 'Allergens')}</strong><span>{product.allergens || t('Non renseignés par OFF', 'Not listed by OFF')}</span></p><p><strong>Labels</strong><span>{product.labels || t('Non renseignés par OFF', 'Not listed by OFF')}</span></p></div>
      {missing && <p className="ciqual-warning">{t('Des valeurs OFF manquent. Choisissez ci-dessous un aliment générique équivalent : ses valeurs seront affichées comme estimation, sans modifier celles du produit.', 'Some OFF values are missing. Choose a similar generic food below: its values are shown as estimates without changing product data.')}</p>}
      {equivalent && <small>{t('Équivalent choisi :', 'Chosen equivalent:')} {equivalent.name} · {t('code CIQUAL', 'CIQUAL code')} {equivalent.code}. {t('Les allergènes ne sont jamais déduits de cet équivalent.', 'Allergens are never inferred from this equivalent.')}</small>}
    </div>}
    <div className="ciqual-browser"><div><p className="eyebrow">{t('Référence française', 'French reference')}</p><h3>{t('Rechercher dans CIQUAL 2025', 'Search CIQUAL 2025')}</h3><p className="section-note">{t('Recherchez un type d’aliment, par exemple « riz cuit » ou « carotte ». La correspondance avec un produit de marque doit être vérifiée par vous.', 'Search for a generic food such as cooked rice or carrots. Check whether it matches your branded product.')}</p></div>
      <form className="nutrition-search" onSubmit={searchCiqual}><label>{t('Aliment équivalent', 'Similar food')}<input value={ciqualQuery} onChange={event => setCiqualQuery(event.target.value)} maxLength={80} placeholder={t('Ex. riz blanc cuit', 'E.g. cooked white rice')} /></label><button className="button-secondary" type="submit" disabled={ciqualLoading}>{ciqualLoading ? t('Recherche…', 'Searching…') : t('Chercher dans CIQUAL', 'Search CIQUAL')}</button></form>
      {ciqualMessage && <p className="form-message" role="status">{ciqualMessage}</p>}
      {candidates.length > 0 && <div className="ciqual-results" role="listbox" aria-label="Résultats CIQUAL">{candidates.map(food => <button type="button" role="option" aria-selected={equivalent?.code === food.code} className={equivalent?.code === food.code ? 'selected' : ''} key={food.code} onClick={() => setEquivalent(food)}><strong>{food.name}</strong><small>{food.kcal100g ?? '—'} kcal · {food.proteinG100g ?? '—'} g protéines / 100 g</small><span>{equivalent?.code === food.code ? 'Sélectionné' : 'Choisir cet équivalent'}</span></button>)}</div>}
      {equivalent && <div className="nutrition-result ciqual-selection" aria-live="polite"><div><span className="eyebrow">{product ? t('Équivalent retenu · estimation CIQUAL', 'Selected equivalent · CIQUAL estimate') : t('Aliment générique · CIQUAL 2025', 'Generic food · CIQUAL 2025')}</span><h3>{equivalent.name}</h3><p>{t('Code CIQUAL', 'CIQUAL code')} {equivalent.code}</p></div><div className="nutrition-values">{nutrients.map(([key, label, unit]) => <div key={key}><strong>{equivalent[key] == null ? t('Non renseigné', 'Unknown') : `${equivalent[key]} ${unit}`}</strong><span>{t(label, key === 'kcal100g' ? 'Energy' : key === 'proteinG100g' ? 'Protein' : key === 'carbohydrateG100g' ? 'Carbs' : 'Fat')} / 100 g</span><small>{t('Valeur CIQUAL', 'CIQUAL value')}</small></div>)}</div>{product && <small>{t('Seules les valeurs absentes du produit OFF sont utilisées comme estimation.', 'Only missing OFF values are filled by estimates.')}</small>}</div>}
      <small>{t('Source : Anses. 2025. Table de composition nutritionnelle des aliments Ciqual. Valeurs pour 100 g ; les valeurs manquantes ne sont pas estimées.', 'Source: Anses. 2025. Ciqual food composition table. Values per 100 g; missing values are not estimated.')}</small>
    </div>
  </section>
}
