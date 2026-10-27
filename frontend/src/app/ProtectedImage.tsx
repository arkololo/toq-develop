import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { authenticatedFetch } from '../auth/keycloak'

export function ProtectedImage({ url, alt, className, fallback }: { url?: string | null; alt: string; className?: string; fallback?: ReactNode }) {
  const [src, setSrc] = useState<string | null>(null)
  useEffect(() => {
    if (!url) return
    const controller = new AbortController()
    let objectUrl: string | null = null
    void authenticatedFetch(url, { signal: controller.signal }).then(async response => {
      if (!response.ok) return
      objectUrl = URL.createObjectURL(await response.blob())
      if (!controller.signal.aborted) setSrc(objectUrl)
    }).catch(() => {})
    return () => { controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [url])
  return src ? <img className={className} src={src} alt={alt} /> : <>{fallback}</>
}
