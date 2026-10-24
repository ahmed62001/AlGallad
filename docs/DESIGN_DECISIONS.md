# Design decisions

Short ADR-style entries for decisions visible in the source code. Each entry states the decision, the reason, and the trade-offs.

## 1. Prices stored as integer piastres, not floating-point

**Decision:** All monetary values (`priceMinor`, `oldPriceMinor`, `totalMinor`, `unitPriceMinor`) are stored as whole piastres (1 EGP = 100 piastres). Formatting helpers in `Money.kt` convert to/from EGP strings for display.

**Why:** Floating-point arithmetic introduces rounding errors (e.g. 0.1 + 0.2 ≠ 0.3). Integer piastres make cart totals, order totals, and price comparisons exact and safe for Firestore queries and security-rule comparisons.

**Trade-off:** Display code must divide by 100 and format decimals manually; Arabic-Indic digit input requires custom normalization in `poundsToMinor`.

## 2. Base64 WebP images embedded in Firestore, sized for the Spark free tier

**Decision:** Product thumbnails and full images are encoded as Base64 WebP strings stored directly on product documents (`thumb` field and `products/{id}/images/{index}` subcollection). Size caps are enforced client-side: thumb ≤ 240 px side / ≤ 15 KB / ≤ 22,000 Base64 chars; full image ≤ 720 px side / ~120 KB / ≤ 170,000 Base64 chars (`ImageUtils.kt`, `firebase/firestore.rules`).

**Why:** Firebase Spark (free) Firestore has a 1 MiB document limit and no Cloud Storage bundle large enough for product photos. Base64 WebP keeps every product within document limits while remaining viewable on the Spark plan.

**Trade-off:** Base64 increases size by ~33%; image quality is bounded by the byte budget; high-resolution photos are downscaled aggressively.

## 3. Favorites and theme are local-only (DataStore, not Firestore)

**Decision:** Theme mode (System/Light/Dark), font scale, and language are persisted with AndroidX DataStore (`ThemePrefs.kt`). No "favorites" collection exists in Firestore; cart state lives in the ViewModel and is persisted locally.

**Why:** Keeps the Firestore data model small and avoids per-user document writes for UI preferences. Reduces cost and latency on the free tier.

**Trade-off:** Preferences do not roam across devices; cart contents are not server-backed.

## 4. Admin check enforced on both client and server

**Decision:** The client hides admin screens unless `BuildConfig.ADMIN_EMAILS` matches the signed-in email (`Repo.kt`), and `firebase/firestore.rules` independently enforces `isAdmin()` for product/order mutations.

**Why:** Client-side checks alone are bypassable (rooted devices, modified clients). Firestore rules are the authoritative enforcement; the client check only prevents accidental exposure of admin UI to non-admin users.

**Trade-off:** The admin email must be kept in sync between `app/build.gradle.kts` and `firebase/firestore.rules`.

## 5. Guest browsing; sign-in required to order

**Decision:** Unauthenticated users can browse products and add items to the cart. Placing an order requires a verified email (`validOrderCreate` requires `request.auth.token.email_verified == true`).

**Why:** Lowers the barrier to browsing while ensuring every order is tied to a verifiable identity. Reduces spam orders.

**Trade-off:** Guest carts are not persisted to the server; clearing app data loses the cart.

## 6. Order prices re-verified before checkout

**Decision:** The customer-side checkout flow re-reads product prices from Firestore and blocks submission if prices changed since the cart rendered. Admins can re-verify historical orders with a price-check result (`PriceVerifier.kt`).

**Why:** Protects both seller and customer from stale-price disputes on the free tier, where server-side triggers are not available.

**Trade-off:** Adds one extra Firestore read per checkout.

## 7. No LICENSE file

**Decision:** The README states "Source published for portfolio viewing. All rights reserved." and no `LICENSE` file is added.

**Why:** The author retains all rights; the repository is a portfolio sample, not an open-source project.
