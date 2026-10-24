# Security model

This document describes the security approach conceptually. It contains no real credentials, real project IDs, or real account information.

## Authentication

- Email/password authentication via Firebase Authentication.
- New accounts must verify their email before they can place an order (`firebase/firestore.rules` requires `request.auth.token.email_verified == true` for order creation).
- Password reset is handled by Firebase Authentication; the app never sees or stores passwords.
- Auth failure messages are generic (one message for wrong email or wrong password) to avoid email enumeration.

## Authorization

- **Customers** can read products and settings, read/write their own profile (`users/{uid}`), create orders (`orders/{id}`), and cancel their own new orders.
- **Admins** (email allowlist in Firestore rules) can create/update/delete products and product images, update any order status, read all orders, and update settings.
- All authorization is enforced by Firestore Security Rules, which are the authoritative server-side check. The client mirrors these checks only to avoid showing unauthorized UI.

## Client vs server responsibilities

| Responsibility | Client | Server (Firestore rules) |
|----------------|--------|-------------------------|
| Password validation | Yes (client-side rules) | N/A (Firebase Auth) |
| Email verification check | No | Yes (order creation) |
| Admin access | Yes (UI gating) | Yes (product/order/settings writes) |
| Order ownership | Yes (UI filtering) | Yes (read/update/delete rules) |
| Price immutability after order | Yes (re-verification) | Partial (rules restrict fields) |
| Document schema validation | Yes (Limits) | Yes (rules validate keys/types/sizes) |

## Known limits of the free (Spark) plan

- Firestore Security Rules are the primary server-side enforcement; no Cloud Functions triggers are used.
- No reCAPTCHA/App Check enforcement is configured in the rules (App Check is initialized in the app but rules do not require it).
- Rate limiting is handled by Firebase Authentication (too-many-requests errors are surfaced generically).
- Documents are size-limited; product images are therefore capped at conservative byte budgets.

## What is NOT in this repository

- No real `google-services.json` (only `app/google-services.json.example` with placeholders).
- No signing keys, keystores, or `keystore.properties`.
- No real emails, phone numbers, project IDs, API keys, or customer data.
- No access tokens, refresh tokens, or service-account credentials.
