import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { authenticatedFetch } from '../auth/keycloak'
import { ProtectedImage } from './ProtectedImage'
import { RecipeProposalPreview } from './AssistantWidget'
import { useLanguage } from './LanguageContext'

type Ingredient = { name: string; quantity: number; unit: string; kcal100g?: number | null; proteinG100g?: number | null; carbohydrateG100g?: number | null; fatG100g?: number | null }
type Recipe = { id: string; title: string; description: string | null; baseServings: number; ingredients: Ingredient[]; steps: string[]; version: number; hasImage: boolean; kcalPerServing: number | null; proteinGPerServing: number | null; carbohydrateGPerServing: number | null; fatGPerServing: number | null; sourceTemplateId: string | null }
type Nutrition = { complete: boolean; missingIngredients: string[]; totals: { kcalPerServing: number; proteinGPerServing: number; carbohydrateGPerServing: number; fatGPerServing: number } }
type IngredientRow = { name: string; quantity: string; unit: string }
type Form = { title: string; description: string; baseServings: number; ingredients: IngredientRow[]; steps: string; kcalPerServing: string; proteinGPerServing: string; carbohydrateGPerServing: string; fatGPerServing: string; sourceTemplateId: string | null; sourceImageUrl: string | null }
type TemplateSummary = { id: string; title: string; imageUrl: string | null }
type Template = TemplateSummary & { originalTitle: string; category: string; area: string; ingredients: { name: string; measure: string }[]; steps: string[]; translated: boolean }
type TranslationProposal = { id: string; type: 'UPDATE_RECIPE'; summary: string; preview: Pick<Recipe, 'title' | 'baseServings' | 'ingredients' | 'steps'> }
function validTemplate(value: unknown): value is Template {
  if (!value || typeof value !== 'object') return false
  const item = value as Partial<Template>
  return typeof item.id === 'string' && typeof item.title === 'string' &&
    Array.isArray(item.ingredients) && item.ingredients.every(row => typeof row.name === 'string' && typeof row.measure === 'string') &&
    Array.isArray(item.steps) && item.steps.every(step => typeof step === 'string')
}
const emptyIngredient = (): IngredientRow => ({ name: '', quantity: '', unit: 'g' })
const blank = (): Form => ({ title: '', description: '', baseServings: 2, ingredients: [emptyIngredient()], steps: '', kcalPerServing: '', proteinGPerServing: '', carbohydrateGPerServing: '', fatGPerServing: '', sourceTemplateId: null, sourceImageUrl: null })
const macroFields = [
  ['kcalPerServing', 'Calories (kcal)'], ['proteinGPerServing', 'Protéines (g)'],
  ['carbohydrateGPerServing', 'Glucides (g)'], ['fatGPerServing', 'Lipides (g)'],
] as const
const toForm = (recipe: Recipe): Form => ({ title: recipe.title, description: recipe.description ?? '', baseServings: recipe.baseServings,
  ingredients: recipe.ingredients.map(item => ({ name: item.name, quantity: String(item.quantity), unit: item.unit })), steps: recipe.steps.join('\n'),
  kcalPerServing: recipe.kcalPerServing == null ? '' : String(recipe.kcalPerServing), proteinGPerServing: recipe.proteinGPerServing == null ? '' : String(recipe.proteinGPerServing),
  carbohydrateGPerServing: recipe.carbohydrateGPerServing == null ? '' : String(recipe.carbohydrateGPerServing), fatGPerServing: recipe.fatGPerServing == null ? '' : String(recipe.fatGPerServing),
  sourceTemplateId: recipe.sourceTemplateId, sourceImageUrl: null })

function readQuantity(measure: string): IngredientRow {
  const normalized = measure.replace(',', '.').trim()
  const mixed = normalized.match(/^(\d+)\s+(\d+)\/(\d+)\s*(.*)$/u)
  const fraction = normalized.match(/^(\d+)\/(\d+)\s*(.*)$/u)
  const decimal = normalized.match(/^(\d+(?:\.\d+)?)\s*(.*)$/u)
  const quantity = mixed ? Number(mixed[1]) + Number(mixed[2]) / Number(mixed[3])
    : fraction ? Number(fraction[1]) / Number(fraction[2]) : decimal ? Number(decimal[1]) : 1
  const unit = (mixed?.[4] || fraction?.[3] || decimal?.[2] || 'portion').trim()
  return { name: '', quantity: String(quantity > 0 ? quantity : 1), unit: (unit || 'portion').slice(0, 20) }
}

export function RecipePage() {
  const { t, language } = useLanguage()
  const translations = useRef(new Map<string, Template>())
  const originals = useRef(new Map<string, Template>())
  const [tab, setTab] = useState<'mine' | 'templates'>('mine')
  const [recipes, setRecipes] = useState<Recipe[]>([])
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<Recipe | null>(null)
  const [editor, setEditor] = useState(false)
  const [form, setForm] = useState<Form>(blank)
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [message, setMessage] = useState('')
  const [nutrition, setNutrition] = useState<Nutrition | null>(null)
  const [templates, setTemplates] = useState<TemplateSummary[]>([])
  const [templateQuery, setTemplateQuery] = useState('')
  const [selectedTemplate, setSelectedTemplate] = useState<Template | null>(null)
  const [templateLoading, setTemplateLoading] = useState(false)
  const [translationBusy, setTranslationBusy] = useState(false)
  const [translationProposal, setTranslationProposal] = useState<TranslationProposal | null>(null)
  useEffect(() => {
    setSelectedTemplate(current => current ? (language === 'en' ? originals.current.get(current.id) : translations.current.get(current.id)) ?? current : null)
  }, [language])

  async function reload() {
    const response = await authenticatedFetch('/api/v1/recipes?size=50')
    if (!response.ok) throw new Error('Impossible de charger vos recettes.')
    setRecipes(await response.json() as Recipe[])
  }
  useEffect(() => { void Promise.resolve().then(reload).catch((error: Error) => setMessage(error.message)) }, [])
  useEffect(() => {
    if (!selected || editor) return
    authenticatedFetch(`/api/v1/nutrition/recipes/${selected.id}`).then(async response => { if (response.ok) setNutrition(await response.json() as Nutrition) }).catch(() => setNutrition(null))
  }, [selected, editor])

  async function loadTemplates(term = '') {
    setTemplateLoading(true)
    try {
      const response = await authenticatedFetch(`/api/v1/recipes/templates?query=${encodeURIComponent(term)}`)
      if (!response.ok) throw new Error(t('Le catalogue de plats est momentanément indisponible.', 'The recipe catalog is temporarily unavailable.'))
      setTemplates(await response.json() as TemplateSummary[])
    } catch (error) { setMessage((error as Error).message) } finally { setTemplateLoading(false) }
  }
  async function openTemplate(id: string) {
    try {
      const response = await authenticatedFetch(`/api/v1/recipes/templates/${id}`)
      if (!response.ok) throw new Error(t('Ce plat ne peut pas être chargé.', 'This dish could not be loaded.'))
      const original: unknown = await response.json()
      if (!validTemplate(original)) throw new Error(t('La recette reçue est incomplète.', 'The recipe received is incomplete.'))
      originals.current.set(id, original)
      setSelectedTemplate(language === 'fr' ? translations.current.get(id) ?? original : original)
    } catch (error) { setMessage((error as Error).message) }
  }
  async function translateTemplate(template: Template) {
    setTranslationBusy(true); setMessage('')
    try {
      const response = await authenticatedFetch(`/api/v1/assistant/templates/${template.id}/translate`, { method: 'POST' })
      if (!response.ok) throw new Error(t('Traduction indisponible. Vérifiez que TOQi est activé, puis réessayez.', 'Translation unavailable. Check that TOQi is enabled, then try again.'))
      const translated: unknown = await response.json()
      if (!validTemplate(translated) || translated.id !== template.id)
        throw new Error(t('La traduction reçue est incomplète. La recette originale reste disponible.', 'The translation is incomplete. The original recipe remains available.'))
      translations.current.set(template.id, translated)
      setSelectedTemplate(translated)
      setMessage(t('Traduction française proposée par TOQi. Vérifiez les quantités, les allergènes et les étapes avant de cuisiner.', 'TOQi proposed a French translation. Check quantities, allergens and steps before cooking.'))
    } catch (error) { setMessage((error as Error).message) } finally { setTranslationBusy(false) }
  }
  async function proposeRecipeTranslation(recipe: Recipe) {
    setTranslationBusy(true); setTranslationProposal(null); setMessage('')
    try {
      const response = await authenticatedFetch(`/api/v1/assistant/recipes/${recipe.id}/translate`, { method: 'POST' })
      if (!response.ok) throw new Error(t('Traduction indisponible. Vérifiez que TOQi est activé, puis réessayez.', 'Translation unavailable. Check that TOQi is enabled, then try again.'))
      setTranslationProposal(await response.json() as TranslationProposal)
    } catch (error) { setMessage((error as Error).message) } finally { setTranslationBusy(false) }
  }
  async function confirmRecipeTranslation(proposal: TranslationProposal) {
    setTranslationBusy(true); setMessage('')
    try {
      const response = await authenticatedFetch(`/api/v1/assistant/actions/${proposal.id}/confirm`, { method: 'POST' })
      if (!response.ok) throw new Error(t('La recette a changé ou la proposition a expiré. Redemandez une traduction.', 'The recipe changed or the proposal expired. Request a new translation.'))
      const result = await response.json() as { resourceId: string }
      const detail = await authenticatedFetch(`/api/v1/recipes/${result.resourceId}`)
      if (detail.ok) setSelected(await detail.json() as Recipe)
      setTranslationProposal(null); await reload()
      setMessage(t('Recette traduite et enregistrée. Vérifiez le résultat avant de cuisiner.', 'Recipe translated and saved. Check it before cooking.'))
    } catch (error) { setMessage((error as Error).message) } finally { setTranslationBusy(false) }
  }
  function createFromTemplate(template: Template) {
    setSelected(null); setNutrition(null); setImageFile(null)
    setForm({ ...blank(), title: template.title.slice(0, 120), description: `${t('Recette inspirée de', 'Recipe inspired by')} TheMealDB · ${template.originalTitle}`.slice(0, 2000),
      ingredients: template.ingredients.map(item => ({ ...readQuantity(item.measure), name: item.name.slice(0, 120) })),
      steps: template.steps.map(step => step.slice(0, 2000)).join('\n'), sourceTemplateId: template.id, sourceImageUrl: template.imageUrl })
    setTab('mine'); setSelectedTemplate(null); setEditor(true)
    setMessage(template.translated ? t('Modèle traduit chargé. Vérifiez les ingrédients, les unités et les allergènes avant d’enregistrer.', 'Translated template loaded. Check ingredients, units and allergens before saving.') : t('Modèle chargé. La recette source est en anglais : vérifiez les ingrédients, les unités et les allergènes avant d’enregistrer.', 'Template loaded in English. Check ingredients, units and allergens before saving.'))
  }
  function edit(recipe: Recipe | null) {
    setSelected(recipe); setNutrition(null); setImageFile(null); setTranslationProposal(null); setForm(recipe ? toForm(recipe) : blank()); setEditor(true); setMessage('')
  }
  function updateIngredient(index: number, field: keyof IngredientRow, value: string) {
    setForm(current => ({ ...current, ingredients: current.ingredients.map((item, position) => position === index ? { ...item, [field]: value } : item) }))
  }
  async function importFile(file: File | undefined) {
    if (!file) return
    try {
      const data = JSON.parse(await file.text()) as Partial<Recipe>
      if (typeof data.title !== 'string' || !Array.isArray(data.ingredients) || !Array.isArray(data.steps) || !data.ingredients.length || !data.steps.length) throw new Error('Format invalide')
      const imported: Recipe = { id: '', title: data.title, description: data.description ?? null, baseServings: Number(data.baseServings ?? 1),
        ingredients: data.ingredients, steps: data.steps, version: 0, hasImage: false, kcalPerServing: data.kcalPerServing ?? null,
        proteinGPerServing: data.proteinGPerServing ?? null, carbohydrateGPerServing: data.carbohydrateGPerServing ?? null,
        fatGPerServing: data.fatGPerServing ?? null, sourceTemplateId: null }
      if (!Number.isInteger(imported.baseServings) || imported.baseServings < 1) throw new Error('Portions invalides')
      setSelected(null); setNutrition(null); setImageFile(null); setForm(toForm(imported)); setEditor(true)
      setMessage(t('Recette importée. Vérifiez les données puis enregistrez-la.', 'Recipe imported. Check the details, then save it.'))
    } catch { setMessage('Ce fichier JSON ne contient pas une recette TOQ valide.') }
  }
  async function save(event: FormEvent) {
    event.preventDefault()
    const ingredients = form.ingredients.map(item => ({ name: item.name.trim(), quantity: Number(item.quantity.replace(',', '.')), unit: item.unit.trim() }))
    const steps = form.steps.split('\n').map(step => step.trim()).filter(Boolean)
    const values = macroFields.map(([key]) => form[key].trim())
    if (!ingredients.length || ingredients.some(item => !item.name || !Number.isFinite(item.quantity) || item.quantity <= 0 || !item.unit) || !steps.length) { setMessage(t('Vérifiez les ingrédients et les étapes.', 'Check ingredients and steps.')); return }
    if (values.some(Boolean) && (!values.every(Boolean) || values.some(value => !Number.isFinite(Number(value.replace(',', '.'))) || Number(value.replace(',', '.')) < 0))) {
      setMessage('Renseignez les quatre valeurs nutritionnelles par portion, ou laissez les quatre champs vides.'); return
    }
    const macros = Object.fromEntries(macroFields.map(([key]) => [key, form[key] === '' ? null : Number(form[key].replace(',', '.'))]))
    try {
      const response = await authenticatedFetch(selected ? `/api/v1/recipes/${selected.id}` : '/api/v1/recipes', {
        method: selected ? 'PUT' : 'POST', body: JSON.stringify({ title: form.title, description: form.description || null, baseServings: form.baseServings, ingredients, steps, ...macros, sourceTemplateId: form.sourceTemplateId }),
      })
      if (!response.ok) throw new Error(t('La recette n’a pas pu être enregistrée.', 'The recipe could not be saved.'))
      const saved = await response.json() as Recipe
      let imageSaved = true
      if (form.sourceTemplateId && form.sourceImageUrl && !saved.hasImage) {
        imageSaved = (await authenticatedFetch(`/api/v1/recipes/${saved.id}/image/template/${form.sourceTemplateId}`, { method: 'PUT' })).ok
      } else if (imageFile) {
        const body = new FormData(); body.append('file', imageFile)
        imageSaved = (await authenticatedFetch(`/api/v1/recipes/${saved.id}/image`, { method: 'PUT', body })).ok
      }
      setSelected({ ...saved, hasImage: saved.hasImage || (imageSaved && !!(imageFile || form.sourceImageUrl)) })
      setNutrition(null); setEditor(false); setImageFile(null); await reload()
      setMessage(imageSaved ? t('Recette enregistrée.', 'Recipe saved.') : t('Recette enregistrée, mais la photo n’a pas pu être récupérée. Vous pouvez en choisir une en modifiant la recette.', 'Recipe saved, but the photo could not be retrieved. You can add one by editing the recipe.'))
    } catch (error) { setMessage((error as Error).message) }
  }
  async function archive(recipe: Recipe) {
    try { const response = await authenticatedFetch(`/api/v1/recipes/${recipe.id}`, { method: 'DELETE' }); if (!response.ok) throw new Error(t('La recette n’a pas pu être supprimée.', 'The recipe could not be deleted.')); setSelected(null); setNutrition(null); await reload(); setMessage(t('Recette supprimée.', 'Recipe deleted.')) }
    catch (error) { setMessage((error as Error).message) }
  }
  async function removeImage(recipe: Recipe) {
    const response = await authenticatedFetch(`/api/v1/recipes/${recipe.id}/image`, { method: 'DELETE' })
    if (response.ok) { setSelected({ ...recipe, hasImage: false }); await reload() }
    else setMessage('Impossible de supprimer cette photo.')
  }
  const visible = recipes.filter(recipe => recipe.title.toLocaleLowerCase('fr').includes(query.toLocaleLowerCase('fr')))
  const showUpload = !form.sourceImageUrl && !(selected?.sourceTemplateId && selected.hasImage)

  return <div className="app-page">
    <header className="app-page-header"><p className="eyebrow">Cook</p><h1>{t('Mes recettes', 'My recipes')}</h1><p>{t('Créez vos plats et découvrez des idées à cuisiner.', 'Create your dishes and discover new ideas.')}</p></header>
    {message && <p className="form-message" role="status">{message}</p>}
    <div className="page-tabs"><button className={tab === 'mine' ? 'selected' : ''} onClick={() => { setTab('mine'); setSelectedTemplate(null) }}>{t('Mes recettes', 'My recipes')}</button><button className={tab === 'templates' ? 'selected' : ''} onClick={() => { setTab('templates'); setEditor(false); setSelected(null); if (!templates.length) void loadTemplates() }}>{t('Idées de plats en ligne', 'Online recipe ideas')}</button></div>
    {tab === 'mine' && (editor ? <section className="workspace-card recipe-editor">
      <button className="text-button" onClick={() => setEditor(false)}>{t('← Retour aux recettes', '← Back to recipes')}</button><h2>{selected ? t('Modifier la recette', 'Edit recipe') : t('Créer une recette', 'Create recipe')}</h2>
      <form className="form-grid" onSubmit={save}>
        <label>{t('Nom du plat', 'Dish name')}<input value={form.title} onChange={event => setForm({ ...form, title: event.target.value })} maxLength={120} required /></label>
        <label>{t('Portions de référence', 'Base servings')}<input type="number" min="1" max="30" value={form.baseServings} onChange={event => setForm({ ...form, baseServings: Number(event.target.value) })} required /></label>
        <label className="wide-field">Description<textarea value={form.description} onChange={event => setForm({ ...form, description: event.target.value })} maxLength={2000} /></label>
        <fieldset className="wide-field ingredient-fieldset"><legend>{t('Ingrédients', 'Ingredients')}</legend><p className="section-note">{t('Une ligne par aliment. Les quantités servent aussi à générer la liste de courses.', 'One line per ingredient. Quantities are also used for the shopping list.')}</p>
          {form.ingredients.map((ingredient, index) => <div className="ingredient-input-row" key={index}>
            <label>{t('Aliment', 'Ingredient')} {index + 1}<input value={ingredient.name} onChange={event => updateIngredient(index, 'name', event.target.value)} maxLength={120} placeholder={t('Ex. tomate', 'E.g. tomato')} required /></label>
            <label>{t('Quantité', 'Quantity')}<input type="number" min="0.001" step="any" value={ingredient.quantity} onChange={event => updateIngredient(index, 'quantity', event.target.value)} placeholder="2" required /></label>
            <label>{t('Unité', 'Unit')}<input value={ingredient.unit} onChange={event => updateIngredient(index, 'unit', event.target.value)} maxLength={20} list="recipe-units" placeholder={t('g, pièce…', 'g, piece…')} required /></label>
            <button type="button" aria-label={`${t('Retirer l’aliment', 'Remove ingredient')} ${index + 1}`} onClick={() => setForm(current => ({ ...current, ingredients: current.ingredients.filter((_, position) => position !== index) }))} disabled={form.ingredients.length === 1}>×</button>
          </div>)}
          <datalist id="recipe-units"><option value="g" /><option value="kg" /><option value="ml" /><option value="l" /><option value="pièce" /><option value="cuillère à soupe" /><option value="cuillère à café" /><option value="tasse" /></datalist>
          <button className="button-secondary" type="button" onClick={() => setForm(current => ({ ...current, ingredients: [...current.ingredients, emptyIngredient()] }))}>{t('+ Ajouter un ingrédient', '+ Add ingredient')}</button>
        </fieldset>
        <fieldset className="wide-field recipe-macros"><legend>{t('Valeurs nutritionnelles par portion', 'Nutrition per serving')}</legend><p className="section-note">{t('Facultatif : renseignez les quatre valeurs ensemble. Elles s’appliquent au plat entier par portion, pas à chaque ingrédient.', 'Optional: enter all four values. They apply to the whole dish per serving, not to each ingredient.')}</p>
          <div>{macroFields.map(([key, label]) => <label key={key}>{t(label, key === 'kcalPerServing' ? 'Calories (kcal)' : key === 'proteinGPerServing' ? 'Protein (g)' : key === 'carbohydrateGPerServing' ? 'Carbs (g)' : 'Fat (g)')}<input type="number" min="0" step="any" value={form[key]} onChange={event => setForm(current => ({ ...current, [key]: event.target.value }))} placeholder="—" /></label>)}</div>
        </fieldset>
        <label className="wide-field">{t('Étapes — une ligne par étape', 'Steps — one per line')}<textarea value={form.steps} onChange={event => setForm({ ...form, steps: event.target.value })} placeholder={t('Laver les tomates.\nCuire le riz.', 'Wash the tomatoes.\nCook the rice.')} required /></label>
        {form.sourceImageUrl && <div className="wide-field source-photo"><img src={form.sourceImageUrl} alt={`${t('Photo du modèle', 'Template photo')} ${form.title}`} /><p>{t('Photo originale fournie par TheMealDB ; elle sera copiée dans votre recette.', 'Original photo from TheMealDB; it will be copied into your recipe.')}</p></div>}
        {showUpload && <label className="wide-field">{t('Photo du plat (facultative, PNG ou JPEG, 3 Mo max.)', 'Dish photo (optional, PNG or JPEG, max 3 MB)')}<input type="file" accept="image/png,image/jpeg" onChange={event => setImageFile(event.target.files?.[0] ?? null)} />{imageFile && <small>{imageFile.name}</small>}</label>}
        <button className="button-primary" type="submit">{t('Enregistrer la recette', 'Save recipe')}</button>
      </form>
    </section> : selected ? <section className="workspace-card"><button className="text-button" onClick={() => setSelected(null)}>← Toutes mes recettes</button>
      <div className="page-title"><div><p className="eyebrow">{t('Fiche recette', 'Recipe')}</p><h2>{selected.title}</h2><p className="section-note">{selected.baseServings} portion{selected.baseServings > 1 ? 's' : ''} · {selected.description}</p></div><div className="recipe-actions"><button className="button-secondary" onClick={() => void proposeRecipeTranslation(selected)} disabled={translationBusy}>🌐 {t('Traduire en français', 'Translate to French')}</button><button className="button-secondary" onClick={() => edit(selected)}>{t('Modifier', 'Edit')}</button></div></div>
      {translationProposal && <div className="assistant-proposal" role="status"><strong>{t('Traduction proposée', 'Proposed translation')}</strong><span>{translationProposal.summary}. {t('Les quantités et valeurs nutritionnelles seront conservées.', 'Quantities and nutrition values are preserved.')}</span><RecipeProposalPreview recipes={[translationProposal.preview]} /><button type="button" className="button-primary" disabled={translationBusy} onClick={() => confirmRecipeTranslation(translationProposal)}>{t('Confirmer et enregistrer', 'Approve and save')}</button><button type="button" className="text-button" onClick={() => setTranslationProposal(null)}>{t('Annuler', 'Cancel')}</button></div>}
      {selected.hasImage && <ProtectedImage key={selected.id} url={`/api/v1/recipes/${selected.id}/image`} alt={`Photo de ${selected.title}`} className="recipe-hero-image" />}
      {selected.hasImage && <button className="text-button" onClick={() => removeImage(selected)}>{t('Supprimer la photo', 'Remove photo')}</button>}
      {nutrition && (nutrition.complete || nutrition.missingIngredients.length < selected.ingredients.length) && <div className="nutrition-strip"><strong>{nutrition.totals.kcalPerServing} kcal / {t('portion', 'serving')}</strong><span>{nutrition.totals.proteinGPerServing} g {t('protéines', 'protein')}</span><span>{nutrition.totals.carbohydrateGPerServing} g {t('glucides', 'carbs')}</span><span>{nutrition.totals.fatGPerServing} g {t('lipides', 'fat')}</span>{!nutrition.complete && <small>{t('Calcul partiel :', 'Partial calculation:')} {nutrition.missingIngredients.join(', ')}</small>}</div>}
      {(!nutrition || (!nutrition.complete && nutrition.missingIngredients.length === selected.ingredients.length)) && <p className="section-note">{t('Aucune valeur nutritionnelle renseignée pour ce plat.', 'No nutrition values are available for this dish.')}</p>}
      <div className="recipe-detail-grid"><div className="detail-panel"><h3>{t('Ingrédients', 'Ingredients')}</h3>{selected.ingredients.map((item, index) => <p key={index}><span>{item.name}</span><strong>{item.quantity} {item.unit}</strong></p>)}</div><div className="detail-panel"><h3>{t('Étapes', 'Steps')}</h3><ol>{selected.steps.map((step, index) => <li key={index}>{step}</li>)}</ol></div></div>
      <button className="text-button danger" onClick={() => archive(selected)}>{t('Supprimer cette recette', 'Delete this recipe')}</button>
    </section> : <section className="workspace-card"><div className="page-title"><div><h2>{t('Mes plats', 'My dishes')}</h2><p className="section-note">{t('Retrouvez et préparez vos recettes.', 'Find and prepare your recipes.')}</p></div><div className="recipe-actions"><label className="import-file">{t('Importer une recette JSON', 'Import JSON recipe')}<input type="file" accept=".json,application/json" onChange={event => { void importFile(event.target.files?.[0]); event.target.value = '' }} /></label><button className="button-primary" onClick={() => edit(null)}>{t('+ Ajouter un plat', '+ Add dish')}</button></div></div>
      <label className="search-field">{t('Rechercher', 'Search')}<input value={query} onChange={event => setQuery(event.target.value)} placeholder={t('Nom d’une recette', 'Recipe name')} /></label>
      <div className="recipe-grid">{visible.map(recipe => <button className="recipe-tile recipe-button" key={recipe.id} onClick={() => { setNutrition(null); setSelected(recipe) }}>
        {recipe.hasImage && <ProtectedImage url={`/api/v1/recipes/${recipe.id}/image`} alt="" className="recipe-card-image" />}
        <span className="recipe-tile-body"><strong>{recipe.title}</strong><small>{recipe.baseServings} {t('portions', 'servings')} · {recipe.ingredients.length} {t('ingrédients', 'ingredients')}</small><span>{recipe.description || t('Voir la recette', 'View recipe')}</span></span>
      </button>)}{visible.length === 0 && <p className="recipe-empty">{recipes.length ? t('Aucune recette ne correspond à votre recherche.', 'No recipes match your search.') : t('Ajoutez votre première recette pour commencer.', 'Add your first recipe to get started.')}</p>}</div>
    </section>)}
    {tab === 'templates' && (selectedTemplate ? <section className="workspace-card template-detail"><button className="text-button" onClick={() => setSelectedTemplate(null)}>{t('← Tous les modèles', '← All templates')}</button>
      {selectedTemplate.imageUrl && <img src={selectedTemplate.imageUrl} alt={selectedTemplate.title} />}
      <p className="eyebrow">TheMealDB · {selectedTemplate.category} · {selectedTemplate.area}</p><h2>{selectedTemplate.title}</h2>
      {selectedTemplate.originalTitle !== selectedTemplate.title && <p className="section-note">Titre original : {selectedTemplate.originalTitle}</p>}
      <p className="form-message">{language === 'en' ? 'This TheMealDB recipe is already in English.' : selectedTemplate.translated ? 'Traduction française proposée par TOQi : vérifiez les quantités et les allergènes.' : 'Recette originale en anglais. Vous pouvez demander sa traduction à TOQi.'}</p>
      {language === 'fr' && !selectedTemplate.translated && <button className="button-secondary" type="button" disabled={translationBusy} onClick={() => void translateTemplate(selectedTemplate)}>{translationBusy ? 'Traduction…' : '🌐 Traduire cette recette en français'}</button>}
      {language === 'en' && <button className="button-secondary" type="button" onClick={() => { setSelectedTemplate(originals.current.get(selectedTemplate.id) ?? selectedTemplate); setMessage('Recipe shown in English. No text changes were made.') }}>🌐 Show recipe in English</button>}
      <div className="recipe-detail-grid"><div className="detail-panel"><h3>{t('Ingrédients', 'Ingredients')}</h3>{selectedTemplate.ingredients.map((item, index) => <p key={index}><span>{item.name}</span><strong>{item.measure}</strong></p>)}</div><div className="detail-panel"><h3>{t('Préparation', 'Preparation')}</h3><ol>{selectedTemplate.steps.map((step, index) => <li key={index}>{step}</li>)}</ol></div></div>
      <button className="button-primary" onClick={() => createFromTemplate(selectedTemplate)}>{t('Créer ma version de ce plat', 'Create my version of this dish')}</button><p className="section-note">{t('Vérifiez les unités et les éventuels allergènes avant de cuisiner.', 'Check units and allergens before cooking.')}</p>
    </section> : <section className="workspace-card"><div className="page-title"><div><h2>{t('Idées de plats', 'Dish ideas')}</h2><p className="section-note">{t('Recherchez en français. Les mots recherchés sont convertis en anglais pour TheMealDB ; les recettes source restent dans leur langue d’origine.', 'Search in French. Your search is converted to English for TheMealDB; open a recipe to see a French translation.')}</p></div></div>
      <form className="template-search" onSubmit={(event: FormEvent) => { event.preventDefault(); void loadTemplates(templateQuery) }}><input value={templateQuery} onChange={event => setTemplateQuery(event.target.value)} maxLength={80} placeholder={t('Ex. poulet, tarte aux pommes', 'E.g. chicken, apple pie')} aria-label={t('Rechercher un plat en ligne', 'Search online dishes')} /><button className="button-primary" type="submit">{t('Rechercher', 'Search')}</button></form>
      {templateLoading && <p className="section-note">Chargement des plats…</p>}
      <div className="recipe-grid">{templates.map(template => <button className="recipe-tile recipe-button" key={template.id} onClick={() => openTemplate(template.id)}>{template.imageUrl && <img className="recipe-card-image" src={template.imageUrl} alt="" />}<span className="recipe-tile-body"><strong>{template.title}</strong><span>{t('Voir les ingrédients et les étapes →', 'View ingredients and steps →')}</span></span></button>)}{!templateLoading && templates.length === 0 && <p className="section-note">{t('Aucun plat trouvé. Essayez un autre terme.', 'No dishes found. Try another search.')}</p>}</div>
      <p className="template-credit">{t('Recettes et photos :', 'Recipes and photos:')} <a href="https://www.themealdb.com/" target="_blank" rel="noreferrer">TheMealDB</a>. {t('Recherche française par mots-clés.', 'French keyword search.')}</p>
    </section>)}
  </div>
}
