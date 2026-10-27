import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { authenticatedFetch } from '../auth/keycloak'
import mascotGrid from '../assets/toqi-charte.png'
import { useLanguage } from './LanguageContext'

type Recipe = { id: string; title: string }
type Meal = { id: string; recipeTitle: string; date: string; slot: string; portions: number }
type ShoppingItem = { id: string; label: string; quantity: number; unit: string; checked: boolean }
type Shopping = { id: string | null; stale: boolean; items: ShoppingItem[] }
const slots = [ ['BREAKFAST', 'Petit-déjeuner'], ['LUNCH', 'Déjeuner'], ['DINNER', 'Dîner'], ['SNACK', 'Collation'] ] as const
const frenchDayNames = ['Lundi', 'Mardi', 'Mercredi', 'Jeudi', 'Vendredi', 'Samedi', 'Dimanche']
const categories = [
  ['all', 'Toutes les catégories'], ['produce', 'Fruits & légumes'], ['protein', 'Viandes & poissons'],
  ['dairy', 'Produits laitiers'], ['pantry', 'Épicerie & féculents'], ['spices', 'Épices & condiments'],
] as const
function categoryFor(label: string) {
  const value = label.toLocaleLowerCase('fr')
  if (/tomate|carotte|courgette|aubergine|oignon|ail|épinard|salade|laitue|avocat|pomme|banane|citron|poivron|brocoli|cucumber|pepper|spinach|zucchini|potato/.test(value)) return 'produce'
  if (/poulet|boeuf|bœuf|porc|saumon|thon|crevette|poisson|chicken|beef|pork|salmon|fish/.test(value)) return 'protein'
  if (/lait|yaourt|yogourt|fromage|crème|beurre|œuf|oeuf|milk|cheese|cream|butter|egg/.test(value)) return 'dairy'
  if (/sel|poivre|paprika|curry|cumin|herbe|basilic|persil|coriandre|épice|salt|spice/.test(value)) return 'spices'
  return 'pantry'
}
function asLocalDate(date: Date) { return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-') }
function weekMonday(raw: string) { const date = new Date(raw + 'T12:00:00'); date.setDate(date.getDate() - (date.getDay() || 7) + 1); return asLocalDate(date) }
function dayInWeek(monday: string, offset: number) { const date = new Date(monday + 'T12:00:00'); date.setDate(date.getDate() + offset); return asLocalDate(date) }

export function PlanningPage() {
  const { t, language } = useLanguage()
  const dayNames = language === 'fr' ? frenchDayNames : ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']
  const slotName = (key: string, french: string) => language === 'fr' ? french : ({ BREAKFAST: 'Breakfast', LUNCH: 'Lunch', DINNER: 'Dinner', SNACK: 'Snack' } as Record<string, string>)[key]
  const categoryName = (key: string, french: string) => language === 'fr' ? french : ({ all: 'All categories', produce: 'Fruit & vegetables', protein: 'Meat & fish', dairy: 'Dairy', pantry: 'Pantry & grains', spices: 'Spices & condiments' } as Record<string, string>)[key]
  const [week, setWeek] = useState(() => weekMonday(asLocalDate(new Date())))
  const [tab, setTab] = useState<'week' | 'shopping'>('week')
  const [recipes, setRecipes] = useState<Recipe[]>([])
  const [meals, setMeals] = useState<Meal[]>([])
  const [shopping, setShopping] = useState<Shopping>({ id: null, stale: true, items: [] })
  const [recipeId, setRecipeId] = useState('')
  const [mealDate, setMealDate] = useState(week)
  const [slot, setSlot] = useState('DINNER')
  const [portions, setPortions] = useState(1)
  const [message, setMessage] = useState('')
  const [category, setCategory] = useState<string>('all')
  const load = useCallback(async () => {
    const [recipeResponse, planResponse, shoppingResponse] = await Promise.all([
      authenticatedFetch('/api/v1/recipes?size=50'),
      authenticatedFetch(`/api/v1/plans/${week}`),
      authenticatedFetch(`/api/v1/plans/${week}/shopping-list`),
    ])
    if (!recipeResponse.ok || !planResponse.ok || !shoppingResponse.ok) throw new Error(language === 'fr' ? 'Impossible de charger le planning.' : 'Unable to load your plan.')
    setRecipes(await recipeResponse.json() as Recipe[])
    setMeals(await planResponse.json() as Meal[])
    setShopping(await shoppingResponse.json() as Shopping)
  }, [week, language])
  useEffect(() => { void Promise.resolve().then(load).catch((error: Error) => setMessage(error.message)) }, [load])

  async function add(event: FormEvent) {
    event.preventDefault()
    setMessage('')
    try {
      const response = await authenticatedFetch(`/api/v1/plans/${week}/meals`, { method: 'POST', body: JSON.stringify({ recipeId, date: mealDate, slot, portions }) })
      if (!response.ok) throw new Error(t('Le repas n’a pas pu être ajouté. Vérifiez la recette et la date.', 'Could not add the meal. Check the recipe and date.'))
      await load(); setMessage(t('Repas ajouté à la semaine.', 'Meal added to the week.'))
    } catch (error) { setMessage((error as Error).message) }
  }
  async function remove(id: string) {
    try {
      const response = await authenticatedFetch(`/api/v1/plans/${week}/meals/${id}`, { method: 'DELETE' })
      if (!response.ok) throw new Error(t('Impossible de retirer ce repas.', 'Unable to remove this meal.'))
      await load()
    } catch (error) { setMessage((error as Error).message) }
  }
  async function generate() {
    try {
      const response = await authenticatedFetch(`/api/v1/plans/${week}/shopping-list`, { method: 'POST' })
      if (!response.ok) throw new Error(t('Impossible de générer la liste de courses.', 'Unable to generate the shopping list.'))
      setShopping(await response.json() as Shopping); setMessage(t('Liste de courses actualisée.', 'Shopping list refreshed.'))
    } catch (error) { setMessage((error as Error).message) }
  }
  async function toggle(item: ShoppingItem) {
    if (!shopping.id) return
    try {
      const response = await authenticatedFetch(`/api/v1/shopping-lists/${shopping.id}/items/${item.id}`, { method: 'PATCH', body: JSON.stringify({ checked: !item.checked }) })
      if (!response.ok) throw new Error(t('Impossible de modifier cet article.', 'Unable to update this item.'))
      setShopping(current => ({ ...current, items: current.items.map(value => value.id === item.id ? { ...value, checked: !value.checked } : value) }))
    } catch (error) { setMessage((error as Error).message) }
  }

  return <div className="app-page">
    <header className="app-page-header"><p className="eyebrow">Order</p><h1>{t('Organisez votre semaine', 'Plan your week')}</h1><p>{t('Planifiez vos repas, simplifiez vos courses.', 'Plan meals and simplify shopping.')}</p></header>
    <div className="page-tabs"><button className={tab === 'week' ? 'selected' : ''} onClick={() => setTab('week')}>{t('Planning semaine', 'Weekly plan')}</button><button className={tab === 'shopping' ? 'selected' : ''} onClick={() => setTab('shopping')}>{t('Liste de courses', 'Shopping list')}</button></div>
    {message && <p className="form-message" role="status">{message}</p>}
    {tab === 'week' && <section className="workspace-card">
      <div className="page-title"><div><h2>{t('Semaine du', 'Week of')} {new Date(week + 'T12:00:00').toLocaleDateString(language === 'fr' ? 'fr-FR' : 'en-US', { day: 'numeric', month: 'long' })}</h2><p className="section-note">{t('Choisissez un jour, un créneau et une recette.', 'Choose a day, meal type and recipe.')}</p></div><div className="week-switch"><button aria-label={t('Semaine précédente', 'Previous week')} onClick={() => { const next = dayInWeek(week, -7); setWeek(next); setMealDate(next) }}>‹</button><button aria-label={t('Semaine suivante', 'Next week')} onClick={() => { const next = dayInWeek(week, 7); setWeek(next); setMealDate(next) }}>›</button></div></div>
      <div className="week-grid"><div className="week-grid-head">{t('Repas', 'Meals')}</div>{dayNames.map((day, index) => <div className="week-grid-head" key={day}>{day}<small>{dayInWeek(week, index).slice(8)}</small></div>)}{slots.map(([key, label]) => <div className="week-row" key={key}><div className="week-row-label">{slotName(key, label)}</div>{dayNames.map((day, index) => { const date = dayInWeek(week, index); return <div className="week-cell" key={day}>{meals.filter(meal => meal.date === date && meal.slot === key).map(meal => <div className="meal-pill" key={meal.id}><span>{meal.recipeTitle}</span><small>{meal.portions} portion{meal.portions > 1 ? 's' : ''}</small><button type="button" aria-label={`${t('Retirer', 'Remove')} ${meal.recipeTitle}`} onClick={() => remove(meal.id)}>×</button></div>)}</div> })}</div>)}</div>
      <form className="planning-controls" onSubmit={add}><label>{t('Jour', 'Day')}<input type="date" min={week} max={dayInWeek(week, 6)} value={mealDate} onChange={event => setMealDate(event.target.value)} required /></label><label>{t('Créneau', 'Meal type')}<select value={slot} onChange={event => setSlot(event.target.value)}>{slots.map(([value, label]) => <option value={value} key={value}>{slotName(value, label)}</option>)}</select></label><label>{t('Recette', 'Recipe')}<select value={recipeId} onChange={event => setRecipeId(event.target.value)} required><option value="">{t('Choisir une recette', 'Choose a recipe')}</option>{recipes.map(recipe => <option value={recipe.id} key={recipe.id}>{recipe.title}</option>)}</select></label><label>Portions<input type="number" min="1" max="30" value={portions} onChange={event => setPortions(Number(event.target.value))} required /></label><button className="button-primary" type="submit">{t('Ajouter un repas', 'Add meal')}</button></form>
    </section>}
    {tab === 'shopping' && <section className="workspace-card shopping-page"><div className="page-title"><div><p className="eyebrow">Check</p><h2>{t('Liste de courses', 'Shopping list')}</h2><p className="section-note">{t('Tout ce dont vous avez besoin, regroupé par catégorie.', 'Everything you need, grouped by category.')}</p></div><div className="shopping-actions"><button className="button-primary" onClick={generate}>{shopping.id ? t('Actualiser la liste', 'Refresh list') : t('Générer ma liste', 'Generate list')}</button><button className="button-secondary" onClick={() => window.print()} disabled={shopping.items.length === 0}>{t('Imprimer', 'Print')}</button></div></div>{shopping.stale && shopping.id && <p className="form-message">{t('Le planning a changé. Actualisez la liste pour voir les bonnes quantités.', 'The plan changed. Refresh the list for correct quantities.')}</p>}<div className="shopping-layout"><nav className="shopping-categories" aria-label={t('Catégories de courses', 'Shopping categories')}>{categories.map(([id, label]) => <button className={category === id ? 'selected' : ''} key={id} onClick={() => setCategory(id)}>{categoryName(id, label)}<span>{id === 'all' ? shopping.items.length : shopping.items.filter(item => categoryFor(item.label) === id).length}</span></button>)}</nav><div className="shopping-list">{categories.slice(1).filter(([id]) => category === 'all' || category === id).map(([id, label]) => { const items = shopping.items.filter(item => categoryFor(item.label) === id); return items.length ? <section className="shopping-group" key={id}><h3>{categoryName(id, label)}</h3>{items.map(item => <label className={item.checked ? 'shopping-row checked' : 'shopping-row'} key={item.id}><input type="checkbox" checked={item.checked} onChange={() => toggle(item)} /><span>{item.label}</span><strong>{item.quantity} {item.unit}</strong></label>)}</section> : null })}{shopping.items.length === 0 && <p className="section-note">{t('Ajoutez des repas au planning, puis générez votre liste.', 'Add meals to the plan, then generate your list.')}</p>}</div><aside className="shopping-mascot"><p>{t('J’ai regroupé les ingrédients de tes repas. Ça te fait gagner du temps !', 'I grouped the ingredients for your meals. That saves time!')}</p><div style={{ backgroundImage: `url(${mascotGrid})` }} role="img" aria-label="TOQi avec un panier de courses" /></aside></div></section>}
  </div>
}
