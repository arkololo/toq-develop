import { Component } from 'react'
import type { ReactNode } from 'react'
import logo from '../assets/toq-logo.png'

export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }
  static getDerivedStateFromError() { return { failed: true } }
  render() {
    if (!this.state.failed) return this.props.children
    const english = window.localStorage.getItem('toq-language') === 'en'
    return <main className="app-error-fallback"><img src={logo} alt="TOQ" /><h1>{english ? 'This page could not be displayed.' : 'Cette page ne s’affiche pas correctement.'}</h1><p>{english ? 'Refresh the page to try again. If the problem persists, return to the home page.' : 'Actualisez la page pour réessayer. Si le problème persiste, revenez à l’accueil.'}</p><div><button className="button-primary" type="button" onClick={() => window.location.reload()}>{english ? 'Try again' : 'Réessayer'}</button><a className="button-secondary" href="/">{english ? 'Home' : 'Accueil'}</a></div></main>
  }
}
