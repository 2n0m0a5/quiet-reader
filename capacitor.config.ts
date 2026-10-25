import type { CapacitorConfig } from '@capacitor/cli'

// Settings for the Android (and later iOS) app, which wraps the web app in dist/.
const config: CapacitorConfig = {
  appId: 'app.quietreader',
  appName: 'Quiet',
  webDir: 'dist',
  backgroundColor: '#f7f5f0',
}

export default config
