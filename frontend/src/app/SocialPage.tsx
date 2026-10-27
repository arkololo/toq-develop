import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import publicationArt from '../assets/toqi-publications.png'
import { authenticatedFetch } from '../auth/keycloak'
import { ProtectedImage } from './ProtectedImage'
import { useLanguage } from './LanguageContext'

type Recipe = { id: string; title: string; hasImage: boolean }
type Publication = { id: string; authorName: string; recipeTitle: string; caption: string | null; createdAt: string; likes: number; comments: number; liked: boolean; favorite: boolean; mine: boolean; hasImage: boolean }
type Comment = { id: string; authorName: string; body: string }

export function SocialPage() {
  const { t, language } = useLanguage()
  const [feed, setFeed] = useState<Publication[]>([])
  const [tab, setTab] = useState<'all' | 'favorites'>('all')
  const [recipes, setRecipes] = useState<Recipe[]>([])
  const [page, setPage] = useState(0)
  const [recipeId, setRecipeId] = useState('')
  const [caption, setCaption] = useState('')
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [openComments, setOpenComments] = useState<string | null>(null)
  const [comments, setComments] = useState<Comment[]>([])
  const [commentText, setCommentText] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const load = useCallback(async (next = 0, view = tab) => {
    const path = view === 'favorites' ? 'publications/favorites' : 'publications'
    const response = await authenticatedFetch(`/api/v1/social/${path}?page=${next}&size=12`)
    if (!response.ok) throw new Error(language === 'fr' ? 'Impossible de charger les publications.' : 'Unable to load posts.')
    const items = await response.json() as Publication[]
    setFeed(old => next ? [...old, ...items] : items)
    setPage(next)
  }, [tab, language])
  useEffect(() => {
    authenticatedFetch('/api/v1/recipes?size=50').then(async response => { if (response.ok) setRecipes(await response.json() as Recipe[]) }).catch(() => {})
  }, [])
  useEffect(() => { void Promise.resolve().then(() => load(0, tab)).catch((error: Error) => setMessage(error.message)) }, [load, tab])
  async function publish(event: FormEvent) {
    event.preventDefault()
    if (!recipeId || busy) return
    setBusy(true)
    try {
      const response = await authenticatedFetch('/api/v1/social/publications', { method: 'POST', body: JSON.stringify({ recipeId, caption }) })
      if (!response.ok) throw new Error(t('La publication a échoué. Vérifiez que cette recette vous appartient.', 'Publishing failed. Check that this recipe belongs to you.'))
      const published = await response.json() as Publication
      let imageSaved = true
      if (imageFile) {
        const body = new FormData()
        body.append('file', imageFile)
        imageSaved = (await authenticatedFetch(`/api/v1/social/publications/${published.id}/image`, { method: 'PUT', body })).ok
      }
      setCaption(''); setImageFile(null)
      setMessage(imageSaved ? t('Recette publiée.', 'Recipe published.') : t('Publication créée, mais la photo a été refusée. Utilisez un PNG ou JPEG de moins de 3 Mo.', 'Post created, but the photo was rejected. Use a PNG or JPEG under 3 MB.'))
      if (tab === 'all') await load(0, 'all')
      else setTab('all')
    } catch (error) { setMessage((error as Error).message) } finally { setBusy(false) }
  }
  async function react(item: Publication, name: 'likes' | 'favorites', active: boolean) {
    const response = await authenticatedFetch(`/api/v1/social/publications/${item.id}/${name}`, { method: active ? 'DELETE' : 'POST' })
    if (response.ok) { const updated = await response.json() as Publication; setFeed(old => old.map(post => post.id === item.id ? updated : post).filter(post => tab !== 'favorites' || post.favorite)) }
    else setMessage(t('Votre réaction n’a pas pu être enregistrée.', 'Your reaction could not be saved.'))
  }
  async function toggleComments(id: string) {
    if (openComments === id) { setOpenComments(null); return }
    const response = await authenticatedFetch(`/api/v1/social/publications/${id}/comments`)
    if (response.ok) { setComments(await response.json() as Comment[]); setOpenComments(id); setCommentText('') }
  }
  async function addComment(event: FormEvent, id: string) {
    event.preventDefault()
    const response = await authenticatedFetch(`/api/v1/social/publications/${id}/comments`, { method: 'POST', body: JSON.stringify({ body: commentText }) })
    if (response.ok) { const item = await response.json() as Comment; setComments(old => [...old, item]); setCommentText(''); setFeed(old => old.map(post => post.id === id ? { ...post, comments: post.comments + 1 } : post)) }
    else setMessage(t('Le commentaire n’a pas pu être ajouté.', 'The comment could not be added.'))
  }
  async function remove(id: string) {
    const response = await authenticatedFetch(`/api/v1/social/publications/${id}`, { method: 'DELETE' })
    if (response.ok) setFeed(old => old.filter(item => item.id !== id))
    else setMessage(t('La publication n’a pas pu être supprimée.', 'The post could not be deleted.'))
  }
  return <div className="app-page">
    <header className="app-page-header"><p className="eyebrow">Share</p><h1>{t('La table TOQ', 'The TOQ table')}</h1><p>{t('Partagez vos recettes et découvrez celles de la communauté.', 'Share your recipes and discover the community’s dishes.')}</p></header>
    {message && <p className="form-message" role="status">{message}</p>}
    <div className="page-tabs"><button className={tab === 'all' ? 'selected' : ''} onClick={() => setTab('all')}>{t('Publications', 'Posts')}</button><button className={tab === 'favorites' ? 'selected' : ''} onClick={() => setTab('favorites')}>{t('Mes favoris', 'My favorites')}</button></div>
    <div className="social-layout">
      <section className="workspace-card"><h2>{t('Publier une recette', 'Share a recipe')}</h2><p className="section-note">{t('Choisissez l’un de vos plats pour le partager.', 'Choose one of your dishes to share.')}</p>
        <form className="social-form" onSubmit={publish}>
          <label>{t('Ma recette', 'My recipe')}<select value={recipeId} onChange={event => setRecipeId(event.target.value)} required><option value="">{t('Choisir un plat', 'Choose a dish')}</option>{recipes.map(recipe => <option key={recipe.id} value={recipe.id}>{recipe.title}</option>)}</select></label>
          <label>{t('Quelques mots', 'A few words')}<textarea value={caption} onChange={event => setCaption(event.target.value)} maxLength={1000} placeholder={t('Pourquoi aimez-vous ce plat ?', 'Why do you like this dish?')} /></label>
          {recipes.find(recipe => recipe.id === recipeId)?.hasImage && <div className="social-recipe-preview"><ProtectedImage url={`/api/v1/recipes/${recipeId}/image`} alt={t('Photo de la recette sélectionnée', 'Selected recipe photo')} className="social-recipe-photo" /><small>{t('La photo de la recette sera reprise automatiquement.', 'The recipe photo will be used automatically.')}</small></div>}
          <label>{t('Choisir une autre photo (facultatif, PNG ou JPEG, 3 Mo max.)', 'Choose another photo (optional, PNG or JPEG, max 3 MB)')}<input type="file" accept="image/png,image/jpeg" onChange={event => setImageFile(event.target.files?.[0] ?? null)} />{imageFile && <small>{imageFile.name}</small>}</label>
          <button className="button-primary" type="submit" disabled={busy || recipes.length === 0}>{t('Publier', 'Publish')}</button>
        </form>
        {recipes.length === 0 && <p className="section-note">{t('Créez d’abord une recette dans Cook.', 'Create a recipe in Cook first.')}</p>}
        <img className="social-art" src={publicationArt} alt={t('TOQi prépare, partage et savoure ses repas', 'TOQi prepares, shares and enjoys meals')} />
      </section>
      <section className="social-feed" aria-label="Publications">
        {feed.map((item, index) => <article className="social-post" key={item.id}>
          <div className="social-post-head"><span className="social-avatar">{item.authorName.slice(0, 1).toUpperCase()}</span><div><strong>{item.authorName}</strong><small>{new Date(item.createdAt).toLocaleDateString(language === 'fr' ? 'fr-FR' : 'en-US', { dateStyle: 'medium' })}</small></div></div>
          <ProtectedImage url={item.hasImage ? `/api/v1/social/publications/${item.id}/image` : null} alt={`${t('Plat partagé par', 'Dish shared by')} ${item.authorName}`} className="social-post-visual social-post-photo" fallback={<div className="social-post-visual" style={{ backgroundImage: `url(${publicationArt})`, backgroundPosition: `${(index % 3) * 50}% ${Math.floor(index % 9 / 3) * 50}%` }} aria-hidden="true" />} />
          <h2>{item.recipeTitle}</h2>{item.caption && <p>{item.caption}</p>}
          <div className="social-actions"><button onClick={() => react(item, 'likes', item.liked)} aria-pressed={item.liked}>{item.liked ? '♥' : '♡'} {item.likes}</button><button onClick={() => toggleComments(item.id)}>{t('Commentaires', 'Comments')} · {item.comments}</button><button onClick={() => react(item, 'favorites', item.favorite)} aria-pressed={item.favorite}>{item.favorite ? '★' : '☆'} {t('Favori', 'Favorite')}</button>{item.mine && <button onClick={() => remove(item.id)}>{t('Supprimer', 'Delete')}</button>}</div>
          {openComments === item.id && <div className="social-comments">{comments.map(comment => <p key={comment.id}><strong>{comment.authorName}</strong> {comment.body}</p>)}<form onSubmit={event => addComment(event, item.id)}><input value={commentText} onChange={event => setCommentText(event.target.value)} maxLength={1000} placeholder={t('Écrire un commentaire', 'Write a comment')} required /><button type="submit">{t('Envoyer', 'Send')}</button></form></div>}
        </article>)}
        {feed.length === 0 && <div className="workspace-card"><h2>{tab === 'favorites' ? t('Aucun favori pour l’instant', 'No favorites yet') : t('Aucune publication pour l’instant', 'No posts yet')}</h2><p>{tab === 'favorites' ? t('Touchez l’étoile d’une publication pour la retrouver ici.', 'Star a post to find it here.') : t('Partagez votre première recette avec la communauté.', 'Share your first recipe with the community.')}</p></div>}
        {feed.length >= (page + 1) * 12 && <button className="button-secondary" onClick={() => load(page + 1)}>{t('Voir plus', 'Show more')}</button>}
      </section>
    </div>
  </div>
}
