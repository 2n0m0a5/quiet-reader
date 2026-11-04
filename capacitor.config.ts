import type { CapacitorConfig } from '@capacitor/cli'

// Settings for the Android (and later iOS) app, which wraps the web app in dist/.
const config: CapacitorConfig = {
  appId: 'app.quietreader',
  appName: 'Quiet',
  webDir: 'dist',
  backgroundColor: '#e8e6e1',
}

export default config
