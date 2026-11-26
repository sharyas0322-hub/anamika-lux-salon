# Anamika Lux Android wrapper

This Android app opens the existing production website in a native Android WebView. It does not create a second backend or database.

- Production URL: https://anamika-lux-salon.onrender.com/
- Package: com.anamikalux.app
- Location is requested at runtime when the website asks for browser geolocation.
- External telephone, SMS, email, map and WhatsApp links are handed to Android.
- Website authentication, bookings, staff/admin dashboards and masked-call API remain on the existing web backend.

Build locally with Android SDK and Gradle 8.9:
`gradle assembleDebug`

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

Note: Web push notifications and background location require separate native integration and are not claimed by this initial wrapper.
