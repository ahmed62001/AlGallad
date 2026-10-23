# AlGallad

**AlGallad** is a bilingual Arabic RTL / English Android store application for a trading company in Egypt. It supports cash on delivery (COD), customer accounts, product browsing, order creation in Cloud Firestore, and a PDF order invoice that the customer can send to the seller through WhatsApp.

> Source published for portfolio viewing. All rights reserved.

## Highlights

- Arabic RTL and English localization, runtime language switching, and light/dark/system themes.
- Firebase Authentication with email verification and customer profile management.
- Firestore-backed product catalog with categories, search tokens, pagination, cart persistence, availability checks, and fresh price verification before checkout.
- COD checkout, order status tracking, customer cancellation, and an admin workflow for products, orders, pricing checks, and store settings.
- Generated bilingual PDF order invoice shared through WhatsApp or the Android share sheet.
- Product image pipeline that decodes local images, applies EXIF rotation, resizes, encodes WebP, Base64-encodes the result, and enforces conservative Firestore payload budgets.
- Firestore Security Rules and composite index definitions are included as source configuration.

## Tech stack

- Kotlin and Jetpack Compose
- Material 3, Navigation Compose, AndroidX DataStore, and AppCompat
- Firebase Authentication, Cloud Firestore, Firebase App Check, and Play Integrity
- Firebase Spark (free) plan-oriented data sizing
- Base64 WebP image storage pipeline

## Architecture

The application uses a Compose UI organized by feature screens and a single `AppViewModel` for UI state and orchestration. `Repo` is the Firebase data boundary for authentication, products, profiles, orders, and store settings. Kotlin data models mirror the Firestore documents, while pure helpers cover validation, money formatting, search tokens, price verification, theme preferences, PDF generation, WhatsApp handoff, and image processing. Firestore Security Rules enforce authenticated customer ownership and a placeholder admin identity.

## Setup

This public repository deliberately contains **no secrets, API keys, signing files, Firebase project configuration, customer data, product data, or real contact details**.

1. Create your own Firebase project and enable Email/Password Authentication and Cloud Firestore.
2. Download that project’s Android configuration file and save it locally as `app/google-services.json`. Use `app/google-services.json.example` only as a structural reference; it contains placeholders.
3. Set your own admin email in `app/build.gradle.kts` and `firebase/firestore.rules`.
4. Configure your own seller WhatsApp number through the app’s admin store-settings flow.
5. Deploy your own Firestore rules and indexes if you use Firebase CLI tooling.
6. Build a debug APK:

   ```bash
   ./gradlew assembleDebug
   ```

For release distribution, configure signing only in your local environment. Do not commit signing files or credentials.

## Repository scope

This is a cleaned portfolio source release. The package name remains `com.algallad.store` for code continuity. The included configuration values are placeholders and must be replaced with resources from your own Firebase project before building.
