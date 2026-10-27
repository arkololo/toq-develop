import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import logo from '../assets/toq-navbar.png'
import { authenticatedFetch, keycloak } from '../auth/keycloak'
import { HomeDashboard, ThinkSummary } from './DashboardPanels'
import type { DayNutrition, Goal, PlannedMeal } from './DashboardPanels'
import { NutritionPage } from './NutritionPage'
import { PlanningPage } from './PlanningPage'
import { RecipePage } from './RecipePage'
import { SocialPage } from './SocialPage'
import { AssistantWidget } from './AssistantWidget'
import { LanguageButton } from './Language'
import { useLanguage } from './LanguageContext'

type Page = 'home' | 'think' | 'planning' | 'recipes' | 'social' | 'profile' | 'settings'
type Profile = { id: string; displayName: string; version: number }
type Preferences = { dietaryPreferenceCodes: string[]; allergyCodes: string[]; excludedIngredientIds: string[] }
const today = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Europe/Paris' })
const emptyGoal: Goal = { validFrom: today(), validTo: null, kcal: null, proteinG: null, carbohydrateG: null, fatG: null }
const csv = (raw: string) => raw.split(',').map(value => value.trim().toUpperCase()).filter(Boolean)
const weekMonday = () => { const day = new Date(today() + 'T12:00:00'); day.setDate(day.getDate() - (day.getDay() || 7) + 1); return day.toLocaleDateString('sv-SE') }
const habits = [
  ['VEGETARIAN', 'Végétarien'], ['VEGAN', 'Végétalien'], ['PESCETARIAN', 'Pescétarien'],
  ['FLEXITARIAN', 'Flexitarien'], ['HALAL', 'Halal'], ['KOSHER', 'Casher'],
  ['LOW_CARB', 'Réduit en glucides'], ['GLUTEN_FREE', 'Sans gluten'], ['LACTOSE_FREE', 'Sans lactose'],
] as const

export function ProfilePage() {
  const { t, language } = useLanguage()
  const [page, setPage] = useState<Page>('home')
  const [profile, setProfile] = useState<Profile | null>(null)
  const [prefs, setPrefs] = useState<Preferences>({ dietaryPreferenceCodes: [], allergyCodes: [], excludedIngredientIds: [] })
  const [dietary, setDietary] = useState<string[]>([])
  const [allergies, setAllergies] = useState('')
  const [goal, setGoal] = useState<Goal>(emptyGoal)
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [displayName, setDisplayName] = useState('')
  const [days, setDays] = useState<DayNutrition[]>([])
  const [meals, setMeals] = useState<PlannedMeal[]>([])
  const [assistantRevision, setAssistantRevision] = useState(0)

  useEffect(() => {
    async function load() {
      const result = await authenticatedFetch('/api/v1/users/me')
      if (result.status === 404) return
      if (!result.ok) throw new Error('Le profil ne peut pas être chargé.')
      const current = await result.json() as Profile
      setProfile(current)
      setDisplayName(current.displayName)
      const [p, g] = await Promise.all([authenticatedFetch('/api/v1/users/me/preferences'), authenticatedFetch('/api/v1/users/me/nutrition-goals')])
      if (p.ok) {
        const value = await p.json() as Preferences
        setPrefs(value); setDietary(value.dietaryPreferenceCodes); setAllergies(value.allergyCodes.join(', '))
      }
      if (g.ok) { const values = await g.json() as Goal[]; if (values[0]) setGoal(values[0]) }
    }
    load().catch((error: Error) => { setMessage(error.message); setLoadError(true) }).finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    if (!profile) return
    const week = weekMonday()
    void Promise.all([authenticatedFetch(`/api/v1/plans/${week}/nutrition`), authenticatedFetch(`/api/v1/plans/${week}`)])
      .then(async ([nutritionResponse, mealsResponse]) => {
        if (nutritionResponse.ok) setDays(await nutritionResponse.json() as DayNutrition[])
        if (mealsResponse.ok) setMeals(await mealsResponse.json() as PlannedMeal[])
      }).catch(() => {})
  }, [profile, page])

  async function saveProfile(event: FormEvent) {
    event.preventDefault()
    try {
      const response = await authenticatedFetch('/api/v1/users/me', { method: 'PUT', body: JSON.stringify({ displayName }) })
      if (!response.ok) throw new Error(t('Impossible d’enregistrer le profil.', 'Unable to save your profile.'))
      setProfile(await response.json() as Profile); setPage('home'); setMessage(t('Nom affiché enregistré.', 'Display name saved.'))
    } catch (error) { setMessage((error as Error).message) }
  }
  async function savePreferences(event: FormEvent) {
    event.preventDefault()
    try {
      const response = await authenticatedFetch('/api/v1/users/me/preferences', { method: 'PUT', body: JSON.stringify({ ...prefs, dietaryPreferenceCodes: dietary, allergyCodes: csv(allergies) }) })
      if (!response.ok) throw new Error(t('Impossible d’enregistrer les préférences.', 'Unable to save preferences.'))
      const value = await response.json() as Preferences
      setPrefs(value); setDietary(value.dietaryPreferenceCodes); setAllergies(value.allergyCodes.join(', ')); setMessage(t('Préférences enregistrées.', 'Preferences saved.'))
    } catch (error) { setMessage((error as Error).message) }
  }
  async function saveGoal(event: FormEvent) {
    event.preventDefault()
    try {
      const response = await authenticatedFetch('/api/v1/users/me/nutrition-goals', { method: 'PUT', body: JSON.stringify(goal) })
      if (!response.ok) throw new Error(t('Impossible d’enregistrer l’objectif.', 'Unable to save the goal.'))
      setGoal(await response.json() as Goal); setMessage(t('Objectif enregistré.', 'Goal saved.'))
    } catch (error) { setMessage((error as Error).message) }
  }

  async function refreshAfterAssistant(type: 'CREATE_RECIPE' | 'CREATE_RECIPES' | 'UPDATE_RECIPE' | 'ADD_MEAL' | 'UPDATE_MEAL' | 'REMOVE_MEAL' | 'REPLACE_WEEK' | 'SET_GOAL') {
    if (type === 'SET_GOAL') {
      const response = await authenticatedFetch('/api/v1/users/me/nutrition-goals')
      if (response.ok) { const values = await response.json() as Goal[]; if (values[0]) setGoal(values[0]) }
      setPage('think')
    } else if (type === 'ADD_MEAL' || type === 'UPDATE_MEAL' || type === 'REMOVE_MEAL' || type === 'REPLACE_WEEK') {
      const week = weekMonday()
      const [nutritionResponse, mealsResponse] = await Promise.all([authenticatedFetch(`/api/v1/plans/${week}/nutrition`), authenticatedFetch(`/api/v1/plans/${week}`)])
      if (nutritionResponse.ok) setDays(await nutritionResponse.json() as DayNutrition[])
      if (mealsResponse.ok) setMeals(await mealsResponse.json() as PlannedMeal[])
      setPage('planning')
    } else setPage('recipes')
  }

  const username = String(keycloak.tokenParsed?.preferred_username ?? '')
  const name = profile?.displayName || username || 'vous'
  const nav = (id: Page, label: string, glyph: string) => <button type="button" className={`side-nav-item${page === id ? ' active' : ''}`} aria-current={page === id ? 'page' : undefined} onClick={() => { setPage(id); setMessage('') }}><span className="side-icon" aria-hidden="true">{glyph}</span>{label}</button>
  if (loading) return <main className="workspace-loading">{t('Chargement de votre espace…', 'Loading your space…')}</main>
  if (loadError) return <main className="workspace-loading"><div><p>{message}</p><button className="button-primary" onClick={() => window.location.reload()}>{t('Réessayer', 'Try again')}</button></div></main>
  if (!profile) return <main className="onboarding"><div className="onboarding-card"><LanguageButton className="button-secondary" /><img src={logo} alt="TOQ" /><p className="eyebrow">{t('Bienvenue chez TOQ', 'Welcome to TOQ')}</p><h1>{t('Comment souhaitez-vous qu’on vous appelle ?', 'What should we call you?')}</h1><p>{t('Choisissez un pseudo avant d’accéder à votre espace. Vous pourrez le modifier plus tard dans Profil.', 'Choose a nickname before entering your space. You can change it later in Profile.')}</p><form onSubmit={saveProfile}><label>{t('Pseudo obligatoire', 'Nickname required')}<input autoFocus value={displayName} onChange={event => setDisplayName(event.target.value)} minLength={2} maxLength={80} placeholder={t('Votre pseudo', 'Your nickname')} required /></label><button className="button-primary" type="submit">{t('Entrer dans TOQ', 'Enter TOQ')}</button></form>{message && <p className="form-message" role="status">{message}</p>}</div></main>
  return <div className="workspace">
    <aside className="sidebar">
      <div className="sidebar-logo-frame"><img src={logo} alt="TOQ" /></div>
      <nav aria-label={t('Navigation principale', 'Main navigation')}>{nav('home', t('Accueil', 'Home'), '⌂')}{nav('think', 'Nutrition', '◈')}{nav('planning', 'Planning', '▦')}{nav('recipes', t('Recettes', 'Recipes'), '▣')}{nav('social', t('Partager', 'Share'), '♡')}</nav>
      <div className="sidebar-bottom"><LanguageButton className="side-nav-item" />{nav('profile', t('Profil', 'Profile'), '♙')}{nav('settings', t('Paramètres', 'Settings'), '⚙')}<button type="button" className="side-nav-item" onClick={() => keycloak.logout({ redirectUri: window.location.origin })}><span className="side-icon" aria-hidden="true">↪</span>{t('Déconnexion', 'Sign out')}</button></div>
    </aside>
    <main className="workspace-main" id="contenu">
      {message && <p className="form-message" role="status">{message}</p>}
      {page === 'home' && <HomeDashboard name={name} goal={goal} days={days} meals={meals} onPage={setPage} />}
      {page === 'think' && <div className="app-page"><header className="app-page-header"><p className="eyebrow">Think</p><h1>{t('Objectifs et nutrition', 'Goals and nutrition')}</h1><p>{t('Analysez vos apports planifiés et gardez le cap.', 'Review planned nutrition and stay on track.')}</p></header><div className="page-tabs"><a href="#resume">{t('Vue d’ensemble', 'Overview')}</a><a href="#objectifs">{t('Modifier les objectifs', 'Edit goals')}</a><a href="#nutrition">{t('Explorer un produit', 'Explore a product')}</a></div><div id="resume"><ThinkSummary goal={goal} days={days} /></div><section className="workspace-card" id="objectifs"><h2>{t('Définir mes objectifs', 'Set my goals')}</h2><form className="form-grid" onSubmit={saveGoal}><label>{t('À partir du', 'From')}<input type="date" value={goal.validFrom} onChange={event => setGoal({ ...goal, validFrom: event.target.value })} required /></label><label>{t('Jusqu’au', 'Until')}<input type="date" value={goal.validTo ?? ''} onChange={event => setGoal({ ...goal, validTo: event.target.value || null })} /></label><label>{t('Calories par jour', 'Daily calories')}<input type="number" min="0" value={goal.kcal ?? ''} onChange={event => setGoal({ ...goal, kcal: event.target.value ? Number(event.target.value) : null })} /></label><label>{t('Protéines (g)', 'Protein (g)')}<input type="number" min="0" value={goal.proteinG ?? ''} onChange={event => setGoal({ ...goal, proteinG: event.target.value ? Number(event.target.value) : null })} /></label><label>{t('Glucides (g)', 'Carbs (g)')}<input type="number" min="0" value={goal.carbohydrateG ?? ''} onChange={event => setGoal({ ...goal, carbohydrateG: event.target.value ? Number(event.target.value) : null })} /></label><label>{t('Lipides (g)', 'Fat (g)')}<input type="number" min="0" value={goal.fatG ?? ''} onChange={event => setGoal({ ...goal, fatG: event.target.value ? Number(event.target.value) : null })} /></label><button type="submit" className="button-primary">{t('Enregistrer l’objectif', 'Save goal')}</button></form></section><div id="nutrition"><NutritionPage /></div></div>}
      {page === 'planning' && <PlanningPage key={`${assistantRevision}-${language}`} />}
      {page === 'recipes' && <RecipePage key={assistantRevision} />}
      {page === 'social' && <SocialPage />}
      {page === 'profile' && <section className="workspace-card"><p className="eyebrow">{t('Profil', 'Profile')}</p><h1>{t('Mon profil', 'My profile')}</h1><form className="form-grid" onSubmit={saveProfile}><label>{t('Pseudo affiché', 'Display name')}<input value={displayName} onChange={event => setDisplayName(event.target.value)} minLength={2} maxLength={80} required /></label><button type="submit" className="button-primary">{t('Enregistrer le profil', 'Save profile')}</button></form></section>}
      {page === 'settings' && <section className="workspace-card"><p className="eyebrow">{t('Paramètres', 'Settings')}</p><h1>{t('Préférences alimentaires', 'Dietary preferences')}</h1><p className="section-note">{t('Choisissez vos habitudes alimentaires. Elles resteront modifiables à tout moment.', 'Choose your dietary habits. You can change them any time.')}</p><form className="form-grid" onSubmit={savePreferences}><div className="wide-field"><details className="multi-select"><summary>{t('Habitudes alimentaires', 'Dietary habits')} <span>{dietary.length ? `${dietary.length} ${t('sélectionnée', 'selected')}${language === 'fr' && dietary.length > 1 ? 's' : ''}` : t('Choisir', 'Choose')}</span></summary><div>{[...habits, ...dietary.filter(code => !habits.some(option => option[0] === code)).map(code => [code, code] as const)].map(([code, label]) => <label key={code}><input type="checkbox" checked={dietary.includes(code)} onChange={event => setDietary(current => event.target.checked ? [...current, code] : current.filter(value => value !== code))} />{t(label, ({ VEGETARIAN: 'Vegetarian', VEGAN: 'Vegan', PESCETARIAN: 'Pescatarian', FLEXITARIAN: 'Flexitarian', HALAL: 'Halal', KOSHER: 'Kosher', LOW_CARB: 'Low carb', GLUTEN_FREE: 'Gluten free', LACTOSE_FREE: 'Lactose free' } as Record<string, string>)[code] || label)}</label>)}</div></details></div><label className="wide-field">{t('Allergies, séparées par des virgules', 'Allergies, separated by commas')}<input value={allergies} onChange={event => setAllergies(event.target.value)} placeholder="GLUTEN, PEANUT" /></label><button type="submit" className="button-primary">{t('Enregistrer les préférences', 'Save preferences')}</button></form></section>}
    </main>
    <AssistantWidget onActionApplied={type => { setAssistantRevision(value => value + 1); void refreshAfterAssistant(type) }} />
  </div>
}
