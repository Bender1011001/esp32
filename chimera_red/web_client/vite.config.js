import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
    base: './',
    plugins: [
        react(),
        VitePWA({
            registerType: 'autoUpdate',
            // includeAssets: ['vite.svg'], // File missing, causing build error
            manifest: {
                name: 'Chimera Red Options',
                short_name: 'Chimera',
                description: 'ESP32-S3 + S24 Red Teaming Suite',
                theme_color: '#050505',
                background_color: '#050505',
                display: 'standalone',
                // Icons removed to pass build without assets
            }
        })
    ],
})
