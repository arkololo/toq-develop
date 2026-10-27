import mascotGrid from '../assets/toqi-charte.png'
import { useLanguage } from './LanguageContext'

export type Goal = { validFrom: string; validTo: string | null; kcal: number | null; proteinG: number | null; carbohydrateG: number | null; fatG: number | null }
export type DayNutrition = { date: string; kcal: number; proteinG: number; carbohydrateG: number; fatG: number; complete: boolean; mealCount: number }
export type PlannedMeal = { id: string; recipeTitle: string; date: string; slot: string; portions: number }

const metrics = [
  { key: 'kcal', title: 'Calories', unit: 'kcal', color: 'green' },
  { key: 'proteinG', title: 'Protéines', unit: 'g', color: 'mint' },
  { key: 'carbohydrateG', title: 'Glucides', unit: 'g', color: 'peach' },
  { key: 'fatG', title: 'Lipides', unit: 'g', color: 'gold' },
] as const
const ratio = (value: number, target: number | null) => target && target > 0 ? Math.min(100, Math.round(value / target * 100)) : 0
const progress = (value: number, target: number | null) => target && target > 0 ? Math.round(value / target * 100) : 0
const gaugeState = (value: number, target: number | null) => !target || target <= 0 ? 'unset' : value > target ? 'over' : value >= target ? 'reached' : 'under'
const format = (value: number) => new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 }).format(value)
const iconStyle = (index: number) => ({ backgroundImage: `url(${mascotGrid})`, backgroundPosition: `${index * 33.333}% 0%` })

export function GoalGauges({ goal, day }: { goal: Goal; day?: DayNutrition }) {
  const { t } = useLanguage()
  return <div className="goal-gauges">{metrics.map(metric => {
    const target = goal[metric.key]
    const current = day?.[metric.key] ?? 0
    const percent = ratio(current, target)
    return <div className={`goal-gauge ${metric.color}`} key={metric.key}><span>{t(metric.title, metric.key === 'proteinG' ? 'Protein' : metric.key === 'carbohydrateG' ? 'Carbs' : metric.key === 'fatG' ? 'Fat' : 'Calories')}</span><strong>{target == null ? t('À définir', 'Not set') : `${format(target)} ${metric.unit} / ${t('jour', 'day')}`}</strong><div className={`gauge-track ${gaugeState(current, target)}`} role="progressbar" aria-label={`${metric.title} planifiées`} aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}><span style={{ width: `${percent}%` }} /></div><small>{day?.mealCount ? `${format(current)} ${metric.unit} · ${progress(current, target)} % · ${gaugeState(current, target) === 'over' ? t('objectif dépassé', 'over target') : gaugeState(current, target) === 'reached' ? t('objectif atteint', 'target reached') : t('objectif en cours', 'in progress')}` : t('Aucun repas planifié', 'No meals planned')}</small></div>
  })}</div>
}

export function HomeDashboard({ name, goal, days, meals, onPage }: { name: string; goal: Goal; days: DayNutrition[]; meals: PlannedMeal[]; onPage: (page: 'think' | 'planning' | 'recipes') => void }) {
  const { t, language } = useLanguage()
  const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Europe/Paris' })
  const day = days.find(item => item.date === today)
  const next = meals.filter(meal => meal.date >= today).sort((a, b) => a.date.localeCompare(b.date))[0]
  const cards = [
    { title: 'Nutrition', subtitle: t('Mes objectifs et macros', 'My goals and macros'), page: 'think' as const },
    { title: 'Planning', subtitle: t('Planifier ma semaine', 'Plan my week'), page: 'planning' as const },
    { title: t('Recettes', 'Recipes'), subtitle: t('Mes recettes', 'My recipes'), page: 'recipes' as const },
  ]
  return <div className="home-dashboard"><header className="home-welcome"><div><p className="eyebrow">{t('Votre espace TOQ', 'Your TOQ space')}</p><h1>{t('Bonjour', 'Hello')} {name} <span aria-hidden="true">👋</span></h1><p>{t('Prêt pour une bonne journée ?', 'Ready for a great day?')}</p></div><div className="home-welcome-mascot" style={iconStyle(2)} role="img" aria-label="TOQi vous accueille" /></header>
    <div className="home-feature-grid">{cards.map((card, index) => <button className={`home-feature-card feature-${index}`} key={card.title} onClick={() => onPage(card.page)}><span className="home-feature-art" style={iconStyle(index)} aria-hidden="true" /><strong>{card.title}</strong><span>{card.subtitle}</span><b aria-hidden="true">→</b></button>)}</div>
    <div className="home-bottom"><section className="home-summary"><h2>{t('Aujourd’hui', 'Today')}</h2><p className="section-note">{t('Apports prévus pour les repas de la journée.', 'Planned nutrition for today’s meals.')}</p><strong className="home-calories">{format(day?.kcal ?? 0)} <span>/ {goal.kcal == null ? '—' : format(goal.kcal)} kcal</span></strong><div className={`gauge-track ${gaugeState(day?.kcal ?? 0, goal.kcal)}`} role="progressbar" aria-label={t('Calories prévues', 'Planned calories')} aria-valuenow={ratio(day?.kcal ?? 0, goal.kcal)} aria-valuemin={0} aria-valuemax={100}><span style={{ width: `${ratio(day?.kcal ?? 0, goal.kcal)}%` }} /></div><div className="home-macro-row">{metrics.slice(1).map(metric => <div key={metric.key}><span className={`macro-dot ${metric.color}`} /><strong>{format(day?.[metric.key] ?? 0)} g</strong><small>{t(metric.title, metric.key === 'proteinG' ? 'Protein' : metric.key === 'carbohydrateG' ? 'Carbs' : 'Fat')}</small></div>)}</div>{day && !day.complete && <p className="section-note">{t('Calcul partiel : certains ingrédients n’ont pas de données nutritionnelles.', 'Partial calculation: nutrition data is missing for some ingredients.')}</p>}</section>
      <section className="home-next"><h2>{t('Prochain repas', 'Next meal')}</h2>{next ? <><div className="next-meal-art" style={iconStyle(3)} aria-hidden="true" /><strong>{next.recipeTitle}</strong><p>{new Date(next.date + 'T12:00:00').toLocaleDateString(language === 'fr' ? 'fr-FR' : 'en-US', { weekday: 'long', day: 'numeric', month: 'long' })} · {next.portions} portion{next.portions > 1 ? 's' : ''}</p><button className="text-button" onClick={() => onPage('planning')}>{t('Voir mon planning →', 'View my plan →')}</button></> : <><p className="section-note">{t('Aucun repas planifié pour les prochains jours.', 'No meals planned for the coming days.')}</p><button className="text-button" onClick={() => onPage('planning')}>{t('Planifier un repas →', 'Plan a meal →')}</button></>}</section></div>
  </div>
}

export function ThinkSummary({ goal, days }: { goal: Goal; days: DayNutrition[] }) {
  const { t, language } = useLanguage()
  const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Europe/Paris' })
  const current = days.find(day => day.date === today)
  return <><section className="think-intro"><div><h2>{t('Mes objectifs', 'My goals')}</h2><p>{t('Visualisez vos cibles et les apports prévus par votre planning.', 'See your targets and the nutrition planned in your meals.')}</p></div><div className="think-mascot" style={iconStyle(3)} aria-hidden="true" /><p>{t('Chaque repas planifié vous aide à garder le cap.', 'Every planned meal helps you stay on track.')}</p></section><GoalGauges goal={goal} day={current} /><section className="weekly-chart"><div><h2>{t('Suivi de la semaine', 'Weekly progress')}</h2><p className="section-note">{t('Pourcentage des objectifs quotidiens couvert par les repas planifiés.', 'Share of daily goals covered by planned meals.')}</p></div><div className="chart-days">{days.map(day => <div className="chart-day" key={day.date}><div className="chart-bars">{metrics.map(metric => <span className={metric.color} key={metric.key} style={{ height: `${ratio(day[metric.key], goal[metric.key])}%` }} title={`${metric.title} : ${format(day[metric.key])} ${metric.unit}`} />)}</div><strong>{new Date(day.date + 'T12:00:00').toLocaleDateString(language === 'fr' ? 'fr-FR' : 'en-US', { weekday: 'short' })}</strong></div>)}</div><div className="chart-legend">{metrics.map(metric => <span key={metric.key}><i className={metric.color} />{t(metric.title, metric.key === 'proteinG' ? 'Protein' : metric.key === 'carbohydrateG' ? 'Carbs' : metric.key === 'fatG' ? 'Fat' : 'Calories')}</span>)}</div></section></>
}
