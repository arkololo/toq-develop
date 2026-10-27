import logo from '../assets/toq-logo.png'
import mascot from '../assets/toqi-welcome.png'
import { keycloak } from '../auth/keycloak'
import { LanguageButton } from './Language'
import { useLanguage } from './LanguageContext'

export function AuthPage({ initialMode }: { initialMode: 'login' | 'register' }) {
  const { t, language } = useLanguage()
  const registering = initialMode === 'register'
  return <main className="auth-gate">
    <header className="auth-gate-header page-width">
      <a href="/" aria-label="TOQ — accueil"><img src={logo} alt="TOQ" /></a>
      <LanguageButton className="button-secondary" />
    </header>
    <section className="auth-gate-content page-width">
      <div className="auth-gate-copy">
        <p className="eyebrow">{t('Vos repas, l’esprit léger.', 'Your meals, made simple.')}</p>
        <h1>{registering ? t('Bienvenue à la table TOQ.', 'Welcome to the TOQ table.') : t('On se retrouve à table ?', 'Ready to join the table?')}</h1>
        <p>{registering ? t('Créez votre compte pour garder vos recettes, organiser la semaine et partager vos idées.', 'Create an account to save recipes, plan your week and share ideas.') : t('Retrouvez vos recettes, votre planning et toutes vos bonnes idées.', 'Find your recipes, meal plan and all your best ideas.')}</p>
        <img src={mascot} alt="TOQi" />
      </div>
      <div className="auth-gate-card">
        <span className="eyebrow">{registering ? t('Créer un compte', 'Create an account') : t('Connexion', 'Sign in')}</span>
        <h2>{registering ? t('Commençons ensemble.', "Let's get started.") : t('Heureux de vous revoir.', 'Welcome back.')}</h2>
        <p>{t('Vos identifiants sont saisis sur la page sécurisée de Keycloak. TOQ ne reçoit jamais votre mot de passe.', 'Enter your credentials on the secure Keycloak page. TOQ never receives your password.')}</p>
        <button type="button" className="button-primary" onClick={() => void (registering ? keycloak.register({ locale: language }) : keycloak.login({ locale: language }))}>{registering ? t('Créer mon compte', 'Create my account') : t('Me connecter', 'Sign in')} <span aria-hidden="true">↗</span></button>
        <p className="auth-gate-switch">{registering ? t('Déjà un compte ?', 'Already have an account?') : t('Pas encore de compte ?', 'New to TOQ?')} <a href={registering ? '/connexion' : '/inscription'}>{registering ? t('Se connecter', 'Sign in') : t('Créer un compte', 'Create an account')}</a></p>
      </div>
    </section>
  </main>
}
