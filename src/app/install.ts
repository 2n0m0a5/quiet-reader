import { registerSW } from 'virtual:pwa-register'
import { toast } from '../ui/toast'

// Offline support and updates.
// When a new version of Quiet has been downloaded, offer to switch to it, but only on the
// library screen: never interrupt someone in the middle of a chapter.
export function setUpOffline(isReading: () => boolean) {
  let pending = false

  const offer = () => {
    if (!pending || isReading()) return
    toast('A new version of Quiet is ready.', 15_000, {
      label: 'Update',
      run: () => updateSW(true),
    })
  }

  const updateSW = registerSW({
    onNeedRefresh() {
      pending = true
      offer()
    },
    onOfflineReady() {
      toast('Quiet is ready to work offline.')
    },
  })

  return { offerUpdate: offer }
}

// Chrome on Android and desktop offers to install web apps. When it does, show our own
// "Install" button instead of relying on the browser menu.
interface InstallPromptEvent extends Event {
  prompt(): Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

export function setUpInstallButton(button: HTMLElement) {
  let deferred: InstallPromptEvent | null = null
  window.addEventListener('beforeinstallprompt', e => {
    e.preventDefault()
    deferred = e as InstallPromptEvent
    button.hidden = false
  })
  button.addEventListener('click', async () => {
    if (!deferred) return
    await deferred.prompt()
    await deferred.userChoice
    deferred = null
    button.hidden = true
  })
  window.addEventListener('appinstalled', () => {
    deferred = null
    button.hidden = true
  })
}
