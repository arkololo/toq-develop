import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import mascot from '../assets/toqi-welcome.png'
import { authenticatedFetch } from '../auth/keycloak'
import { useLanguage } from './LanguageContext'

type RecipePreview = { title: string; description?: string; baseServings: number; ingredients: { name: string; quantity: number; unit: string }[]; steps: string[] }
type MealPreview = { date: string; slot: 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'SNACK'; recipeTitle: string; portions: number }
type Proposal = { id: string; type: 'CREATE_RECIPE' | 'CREATE_RECIPES' | 'UPDATE_RECIPE' | 'ADD_MEAL' | 'UPDATE_MEAL' | 'REMOVE_MEAL' | 'REPLACE_WEEK' | 'SET_GOAL'; summary: string; preview?: RecipePreview & { recipes?: RecipePreview[]; meals?: MealPreview[] } }
type Message = { role: 'user' | 'model'; text: string; proposal?: Proposal | null; done?: boolean }

export function RecipeProposalPreview({ recipes }: { recipes: RecipePreview[] }) {
  const { t } = useLanguage()
  return <ul className="assistant-recipe-preview">{recipes.map((recipe, index) => <li key={`${recipe.title}-${index}`}>
    <strong>{recipe.title}</strong><small>{recipe.baseServings} {t('portions', 'servings')} · {recipe.ingredients.map(item => `${item.quantity} ${item.unit} ${item.name}`).join(', ')}</small>
    <details><summary>{t('Voir les étapes', 'View steps')}</summary><ol>{recipe.steps.map((step, position) => <li key={position}>{step}</li>)}</ol></details>
  </li>)}</ul>
}

function WeekProposalPreview({ meals }: { meals: MealPreview[] }) {
  const { t, language } = useLanguage()
  const slot = { BREAKFAST: t('Petit déjeuner', 'Breakfast'), LUNCH: t('Déjeuner', 'Lunch'), DINNER: t('Dîner', 'Dinner'), SNACK: t('Collation', 'Snack') }
  return <details className="assistant-week-preview"><summary>{t('Voir les 7 jours et tous les repas', 'View all 7 days and meals')}</summary><ul>{meals.map((meal, index) => <li key={`${meal.date}-${meal.slot}-${index}`}><strong>{new Intl.DateTimeFormat(language === 'fr' ? 'fr-FR' : 'en-GB', { weekday: 'short', day: 'numeric', month: 'short' }).format(new Date(`${meal.date}T12:00:00`))}</strong><span>{slot[meal.slot]} · {meal.recipeTitle} · {meal.portions} {t('portion(s)', 'serving(s)')}</span></li>)}</ul></details>
}

export function AssistantWidget({ onActionApplied }: { onActionApplied: (type: Proposal['type']) => void }) {
  const { t, language } = useLanguage()
  const [open, setOpen] = useState(false)
  const [enabled, setEnabled] = useState<boolean | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!open || enabled !== null) return
    authenticatedFetch('/api/v1/assistant/status').then(async response => {
      if (!response.ok) throw new Error(language === 'fr' ? 'Assistant indisponible.' : 'Assistant unavailable.')
      setEnabled((await response.json() as { enabled: boolean }).enabled)
    }).catch(() => setError(language === 'fr' ? 'Assistant indisponible.' : 'Assistant unavailable.'))
  }, [open, enabled, language])

  async function send(event: FormEvent) {
    event.preventDefault()
    const message = draft.trim()
    if (!message || busy || !enabled) return
    const history = messages.slice(-8).map(item => ({ role: item.role, text: item.text }))
    setMessages(current => [...current, { role: 'user', text: message }])
    setDraft(''); setBusy(true); setError('')
    try {
      const response = await authenticatedFetch('/api/v1/assistant/chat', {
        method: 'POST', body: JSON.stringify({ message, history, language }),
      })
      if (!response.ok) throw new Error(response.status === 429 ? t('Limite de messages atteinte. Réessayez plus tard.', 'Message limit reached. Try again later.') : t('TOQi ne peut pas répondre pour le moment.', 'TOQi cannot reply right now.'))
      const result = await response.json() as { reply: string; proposal: Proposal | null }
      setMessages(current => [...current, { role: 'model', text: result.reply, proposal: result.proposal }])
    } catch (reason) { setError((reason as Error).message) } finally { setBusy(false) }
  }

  async function confirm(proposal: Proposal) {
    if (busy) return
    setBusy(true); setError('')
    try {
      const response = await authenticatedFetch(`/api/v1/assistant/actions/${proposal.id}/confirm`, { method: 'POST' })
      if (!response.ok) throw new Error(t('Action non appliquée. La proposition a peut-être expiré : redemandez-la à TOQi.', 'Action not applied. The proposal may have expired; ask TOQi again.'))
      const result = await response.json() as { message: string }
      setMessages(current => current.map(item => item.proposal?.id === proposal.id ? { ...item, done: true } : item).concat({ role: 'model', text: result.message }))
      onActionApplied(proposal.type)
    } catch (reason) { setError((reason as Error).message) } finally { setBusy(false) }
  }

  return <div className="assistant-widget">
    {open && <section className="assistant-panel" aria-label="Discussion avec TOQi">
      <header><img src={mascot} alt="" /><div><strong>TOQi</strong><span>{t('Votre assistant repas', 'Your meal assistant')}</span></div><button type="button" aria-label={t('Fermer TOQi', 'Close TOQi')} onClick={() => setOpen(false)}>×</button></header>
      <div className="assistant-messages" aria-live="polite">
        <p className="assistant-intro">{t('Bonjour ! Je peux créer plusieurs recettes, traduire ou modifier vos plats et préparer votre semaine de repas. Vous validez chaque proposition.', 'Hello! I can create several recipes, translate or edit dishes, and plan your full week of meals. You approve every change.')}</p>
        {enabled === false && <p className="assistant-notice">{t("Gemini n'est pas encore configuré sur cette installation. Ajoutez la clé dans le fichier local", 'Gemini is not configured. Add the key to the local')} <code>.env</code>{t(', puis redémarrez le User Service.', ' file, then restart User Service.')}</p>}
        {enabled && <p className="assistant-notice">{t('Vos messages et le contexte utile de votre compte sont envoyés à Gemini. Vérifiez les suggestions nutritionnelles et confirmez chaque modification avant son application.', 'Your messages and relevant account context are sent to Gemini. Check nutrition suggestions and approve each change before it is applied.')}</p>}
        {messages.map((item, index) => <div className={`assistant-message ${item.role}`} key={index}><p>{item.text}</p>{item.proposal && <div className="assistant-proposal"><strong>{t('Action proposée', 'Proposed action')}</strong><span>{item.proposal.summary}</span>{item.proposal.type === 'REPLACE_WEEK' && <span>{t('Attention : les repas actuellement prévus pour cette semaine seront remplacés après confirmation.', 'The existing meals for this week will be replaced after approval.')}</span>}{item.proposal.preview?.recipes && <RecipeProposalPreview recipes={item.proposal.preview.recipes} />}{item.proposal.preview?.title && !item.proposal.preview.recipes && <RecipeProposalPreview recipes={[item.proposal.preview]} />}{item.proposal.preview?.meals && <WeekProposalPreview meals={item.proposal.preview.meals} />}<button type="button" onClick={() => confirm(item.proposal!)} disabled={busy || item.done}>{item.done ? t('Appliquée', 'Applied') : t('Confirmer cette action', 'Approve this action')}</button></div>}</div>)}
        {busy && <p className="assistant-thinking">{t('TOQi réfléchit…', 'TOQi is thinking…')}</p>}
        {error && <p className="assistant-error" role="alert">{error}</p>}
      </div>
      <form onSubmit={send}><label className="sr-only" htmlFor="assistant-message">{t('Votre message à TOQi', 'Your message to TOQi')}</label><input id="assistant-message" value={draft} onChange={event => setDraft(event.target.value)} maxLength={1000} placeholder={t('Ex. Propose-moi un dîner végétarien', 'E.g. Suggest a vegetarian dinner')} disabled={!enabled || busy} /><button type="submit" disabled={!enabled || busy || !draft.trim()} aria-label={t('Envoyer à TOQi', 'Send to TOQi')}>➤</button></form>
    </section>}
    <button className="assistant-launcher" type="button" aria-label={open ? t('Fermer TOQi', 'Close TOQi') : t('Ouvrir TOQi', 'Open TOQi')} aria-expanded={open} onClick={() => setOpen(current => !current)}><img src={mascot} alt="" /><span>{t('Parler à TOQi', 'Talk to TOQi')}</span></button>
  </div>
}
