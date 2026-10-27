import logo from '../assets/toq-logo.png'
import toqi from '../assets/toqi-welcome.png'
import { ProfilePage } from './Profile'
import { LanguageButton } from './Language'
import { useLanguage } from './LanguageContext'
import { AuthPage } from './AuthPage'

const features = [
  {
    id: 'recettes',
    number: '01',
    label: 'À votre goût',
    title: 'Vos recettes, au même endroit.',
    description: 'Retrouver vos plats préférés, leurs ingrédients et les étapes de préparation. Le point de départ d’une semaine qui vous ressemble.',
    icon: 'recipe',
  },
  {
    id: 'nutrition',
    number: '02',
    label: 'Des repères simples',
    title: 'Mieux connaître votre assiette.',
    description: 'Comprendre les apports des repas prévus et les mettre en regard de vos objectifs. Des informations utiles, à votre rythme.',
    icon: 'nutrition',
  },
  {
    id: 'organisation',
    number: '03',
    label: 'Une semaine plus légère',
    title: 'Des repas aux courses.',
    description: 'Organiser les repas de la semaine, ajuster les portions et rassembler les ingrédients dans une liste de courses.',
    icon: 'planner',
  },
] as const

function FeatureIcon({ name }: { name: (typeof features)[number]['icon'] }) {
  return (
    <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {name === 'recipe' && <><path d="M4 4h6a3 3 0 0 1 3 3v14a4 4 0 0 0-4-2H4z" /><path d="M13 7a3 3 0 0 1 3-3h4v15h-3a4 4 0 0 0-4 2M7 8h3M7 12h3M16 8h1M16 12h1" /></>}
      {name === 'nutrition' && <><path d="M12 3v9h9A9 9 0 1 1 12 3Z" /><path d="M16 3.9A9 9 0 0 1 20.1 8H16Z" /></>}
      {name === 'planner' && <><rect x="3" y="5" width="18" height="16" rx="3" /><path d="M7 3v4M17 3v4M3 10h18M8 15l3 3 5-5" /></>}
    </svg>
  )
}

export function App({ authenticated = false }: { authenticated?: boolean }) {
  const { t } = useLanguage()
  if (authenticated) return <ProfilePage />
  if (window.location.pathname === '/connexion' || window.location.pathname === '/inscription')
    return <AuthPage initialMode={window.location.pathname === '/inscription' ? 'register' : 'login'} />
  return (
    <>
      <a className="skip-link" href="#contenu">{t('Aller au contenu', 'Skip to content')}</a>
      <header className="site-header page-width">
        <a className="brand" href="#accueil" aria-label="TOQ — accueil">
          <img src={logo} width="1536" height="1024" alt="TOQ — Think, Organize, Quick" />
        </a>
        <nav className="main-nav" aria-label="Navigation principale">
          <a href="#decouvrir">{t('L’idée TOQ', 'The TOQ idea')}</a>
          <a href="#a-propos">{t('À propos', 'About')}</a>
        </nav>
        <LanguageButton className="button-secondary" />
        <a className="button-secondary" href="/connexion">{t('Se connecter', 'Sign in')}</a>
      </header>

      <main id="contenu" tabIndex={-1}>
        <section className="hero page-width" id="accueil" aria-labelledby="hero-title">
          <div className="hero-copy">
            <p className="eyebrow"><span aria-hidden="true">✦</span> {t('Un peu d’organisation. Beaucoup de plaisir.', 'A little planning. Lots of joy.')}</p>
            <h1 id="hero-title">{t('Vos repas, ', 'Your meals, ')}<br />{t('l’esprit ', 'made ')}<span>{t('léger.', 'simple.')}</span></h1>
            <p className="hero-description">{t('Des idées dans l’assiette, une semaine bien pensée et plus de place pour le plaisir de cuisiner.', 'Ideas for your plate, a well planned week, and more time to enjoy cooking.')}</p>
            <a className="button-primary" href="#decouvrir">{t('Découvrir TOQ', 'Discover TOQ')} <span aria-hidden="true">↗</span></a>
            <p className="hero-note"><a href="/inscription">{t('Créer un compte', 'Create an account')}</a> · {t('Connectez-vous pour organiser vos repas.', 'Sign in to plan your meals.')}</p>
          </div>
          <div className="hero-visual">
            <div className="visual-ring" aria-hidden="true" />
            <p className="visual-note">{t('Un petit coup de pouce,', 'A little help,')}<br /><strong>{t('chaque jour.', 'every day.')}</strong></p>
            <img className="mascot" src={toqi} alt="TOQi, la mascotte de TOQ, vous accueille avec le sourire." width="858" height="807" fetchPriority="high" />
            <div className="mascot-caption"><span aria-hidden="true">✦</span> {t('Faites connaissance avec', 'Meet')} <strong>TOQi</strong></div>
          </div>
        </section>

        <section className="features-section page-width" id="decouvrir" aria-labelledby="features-title">
          <div className="section-heading">
            <div>
              <p className="eyebrow">{t('Moins de « qu’est-ce qu’on mange ? »', 'Less wondering what to eat')}</p>
              <h2 id="features-title">{t('Une idée simple pour vos repas.', 'One simple idea for your meals.')}</h2>
            </div>
            <p>{t('TOQ vous accompagne de l’idée à l’assiette.', 'TOQ helps you from idea to plate.')}</p>
          </div>
          <div className="feature-grid">
            {features.map((feature) => (
              <article className={`feature-card feature-card--${feature.icon}`} id={feature.id} key={feature.id} aria-labelledby={`${feature.id}-title`}>
                <div className="feature-top"><span className="feature-icon"><FeatureIcon name={feature.icon} /></span><span className="feature-number" aria-hidden="true">{feature.number}</span></div>
                <p className="feature-label">{t(feature.label, feature.id === 'recettes' ? 'Made for you' : feature.id === 'nutrition' ? 'Simple insights' : 'An easier week')}</p>
                <h3 id={`${feature.id}-title`}>{t(feature.title, feature.id === 'recettes' ? 'All your recipes in one place.' : feature.id === 'nutrition' ? 'Know your plate better.' : 'From meals to shopping.')}</h3>
                <p className="feature-description">{t(feature.description, feature.id === 'recettes' ? 'Keep your favorite dishes, ingredients and cooking steps together.' : feature.id === 'nutrition' ? 'Understand your planned meals and how they fit your goals.' : 'Plan your weekly meals, adjust portions and make a shopping list.')}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="about-section page-width" id="a-propos" aria-labelledby="about-title">
          <div className="about-symbol" aria-hidden="true">✳</div>
          <div>
            <p className="eyebrow">{t('Notre conviction', 'Our belief')}</p>
            <h2 id="about-title">{t('Bien manger commence', 'Eating well starts')} <br />{t('par se simplifier la vie.', 'with making life easier.')}</h2>
          </div>
          <p>{t('TOQ relie vos envies, vos repères nutritionnels et l’organisation de vos repas. Un projet imaginé pour vous laisser du temps pour ce qui compte : cuisiner, savourer, partager un bon moment.', 'TOQ brings together your tastes, nutrition goals and meal plans, leaving more time for cooking, sharing and enjoying food.')}</p>
        </section>
      </main>

      <footer className="site-footer page-width">
        <p><strong>TOQ</strong><span> {t('Un quotidien à votre goût.', 'Everyday meals, your way.')}</span></p>
        <a href="#accueil">{t('Retour en haut', 'Back to top')} <span aria-hidden="true">↑</span></a>
      </footer>
    </>
  )
}
