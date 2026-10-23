package com.algallad.store.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

object ImageUtils {
    // ---- Free-tier sizing (Firestore payload caps; unit-tested in ImageLimitsTest) ----
    // Thumb: Base64 WebP on the product doc, max 240px side, <= 15 KB (<= 22000 Base64 chars).
    const val THUMB_MAX_SIDE = 240
    const val THUMB_MAX_BYTES = 15 * 1024
    const val THUMB_MAX_BASE64 = 22000

    // Full: Base64 WebP in products/{id}/images/{index}, max 720px side, <= ~120 KB (<= 170000 chars).
    const val FULL_MAX_SIDE = 720
    const val FULL_MAX_BYTES = 120 * 1024
    const val FULL_MAX_BASE64 = 170000

    const val MAX_IMAGES = 4

    /** Start quality for the adaptive WebP loop. */
    private const val QUALITY_START = 95
    private const val QUALITY_MIN = 30
    private const val QUALITY_STEP = 5

    // After the quality loop, if still > maxBytes, shrink the side by 15% (x0.85) down to a floor.
    private const val SCALE_FACTOR = 0.85f
    const val MIN_SIDE_FLOOR = 32

    /** Diagnostic log tag (Round 6, Task A). Logs stages/mimes/dimensions only — never
     *  URIs, file names, or anything derived from user content (no PII). Debug-only: the
     *  verbose (d) channel is additionally stripped in release by proguard-rules.pro, and
     *  gated on BuildConfig.DEBUG here so no diagnostic logging ships in a release build. */
    private const val TAG = "AlGalladPhoto"
    private fun plogd(msg: String) {
        if (com.algallad.store.BuildConfig.DEBUG) android.util.Log.d(TAG, msg)
    }
    private fun ploge(msg: String) { android.util.Log.e(TAG, msg) }

    /** Which pipeline stage produced a failure (diagnostics + typed errors). */
    enum class Stage { OPEN, BOUNDS, DECODE, ENCODE, BUDGET }

    /**
     * Pure planner for the byte-limit shrink loop (unit-tested). Given the sizes produced by
     * re-encoding at successively shrunk sides, return the index to use, or null if even the
     * floor-sized image is still too big. `sizesByStep` is size produced at each shrink step
     * (step 0 = original side). Pure so it can be proven without Android bitmaps.
     */
    fun planScaleDown(sizesByStep: List<Int>, maxBytes: Int): Int? {
        for ((step, size) in sizesByStep.withIndex()) {
            if (size <= maxBytes) return step
        }
        return null
    }

    /** Next side length after one shrink, floored at MIN_SIDE_FLOOR. */
    fun nextSide(side: Int): Int = maxOf(MIN_SIDE_FLOOR, (side * SCALE_FACTOR).toInt())

    /**
     * Pure (unit-tested): power-of-two inSampleSize so the decoded longest side still
     * fits the target [maxSide] (same semantics as the pre-Round-6 inline loop: the
     * decode lands at or just under maxSide; an exact clamp happens after decode).
     * Invalid inputs never produce a sample > 1 (safe default: decode as-is).
     */
    fun sampleSizeFor(width: Int, height: Int, maxSide: Int): Int {
        if (width <= 0 || height <= 0 || maxSide <= 0) return 1
        var sample = 1
        val longest = maxOf(width, height)
        while (longest / sample > maxSide) sample *= 2
        return sample
    }

    /**
     * Pure (unit-tested): EXIF orientation tag -> clockwise rotation degrees.
     * Mirrored orientations (TRANSPOSE/TRANSVERSE/FLIP_*) intentionally map to 0 —
     * same behaviour as Round 5 and earlier; rotation-only correction is what phone
     * cameras actually emit. ImageDecoder (API 28+) handles mirrors itself.
     */
    fun exifDegrees(orientation: Int): Int = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    /**
     * Pure (unit-tested): classification of a failed DECODE stage. Bounds ok but no
     * pixels -> DECODE_ERROR (we know the container but cannot produce pixels);
     * bounds itself failed -> UNREADABLE (unsupported/corrupt format, e.g. an AVIF
     * the platform cannot decode at all).
     */
    fun classifyDecodeFailure(boundsWidth: Int, boundsHeight: Int): ImageFailure =
        if (boundsWidth > 0 && boundsHeight > 0) ImageFailure.DECODE_ERROR else ImageFailure.UNREADABLE


    /** Android-side: API 30+ lossy WebP, API 28-29 lossless WebP, earlier → JPEG. */
    private fun webpFormat(): Bitmap.CompressFormat = when {
        android.os.Build.VERSION.SDK_INT >= 30 -> Bitmap.CompressFormat.WEBP_LOSSY
        android.os.Build.VERSION.SDK_INT >= 28 -> Bitmap.CompressFormat.WEBP
        else -> Bitmap.CompressFormat.JPEG
    }

    /** Why a compression failed. Surfaced to the UI as a localized message; never contains PII. */
    enum class ImageFailure {
        /** No stream, or bounds decode failed (unsupported format / corrupt). */
        UNREADABLE,
        /** Decoded bitmap was null, empty, or failed output validation. */
        DECODE_ERROR,
        /** Output could not be squeezed into maxBytes even at the size floor. */
        OVER_BUDGET,
        /** Cancelled / interrupted (e.g. scope cancelled). Never shown as an error. */
        CANCELLED,
        /** Anything else (I/O, OOM, provider quirks). */
        UNKNOWN,
    }

    /** Typed photo failure; [cause] is logged, never shown (may name providers). */
    class PhotoException(
        val failure: ImageFailure,
        cause: Throwable? = null,
        val stage: Stage? = null,
    ) : Exception("photo: $failure@${stage ?: "?"}", cause)

    /**
     * Round 6 (Task A) — robust pipeline. The picked content URI is copied to an
     * app-private temp file with a SINGLE provider-stream read, then every other
     * step (bounds, decode, EXIF, fallbacks) runs against the file — so provider
     * quirks (Xiaomi/MIUI cloud HEIC, one-shot streams, EXIF stream refusal) and
     * stream re-use can no longer break decoding. The temp file is always deleted.
     *
     * Decoders, in order: API 28+ ImageDecoder (HEIC/HEIF/WebP/AVIF where the
     * platform supports them, applies EXIF orientation itself, samples via
     * setTargetSampleSize so full-size bitmaps are never held) with a BitmapFactory
     * fallback; API 26-27 BitmapFactory (bounds-first inSampleSize) + ExifInterface.
     *
     * The byte-budget encode loop (quality -> side-shrink -> decode-validate) and
     * every constant are unchanged from Round 5. Runs on the caller's thread:
     * call from Dispatchers.IO / Default.
     */
    fun compressWebPDetailed(ctx: Context, uri: Uri, maxSide: Int, maxBytes: Int): Result<String> {
        val copied = copyToCache(ctx, uri)
        val file = copied.getOrElse {
            return Result.failure(it as? PhotoException ?: PhotoException(ImageFailure.UNREADABLE, it, Stage.OPEN))
        }
        return try {
            compressFromFile(file, maxSide, maxBytes)
        } finally {
            runCatching { file.delete() }
        }
    }

    /**
     * Full + thumb from ONE copy of the provider stream (Round 6): the picker's
     * content URI is read exactly once, both sizes are produced from the private
     * temp file, which is deleted afterwards. [openFailed] is true when the copy
     * itself failed (OPEN stage) — no decode was attempted.
     */
    class PickResult(val full: Result<String>, val thumb: Result<String>, val openFailed: Boolean)

    fun pickAndCompress(ctx: Context, uri: Uri): PickResult {
        val copied = copyToCache(ctx, uri)
        val file = copied.getOrNull()
        if (file == null) {
            val ex = copied.exceptionOrNull() as? PhotoException
                ?: PhotoException(ImageFailure.UNREADABLE, null, Stage.OPEN)
            return PickResult(Result.failure(ex), Result.failure(ex), openFailed = true)
        }
        return try {
            val full = compressFromFile(file, FULL_MAX_SIDE, FULL_MAX_BYTES)
            val thumb = if (full.isSuccess) compressFromFile(file, THUMB_MAX_SIDE, THUMB_MAX_BYTES) else full
            PickResult(full, thumb, openFailed = false)
        } finally {
            runCatching { file.delete() }
        }
    }

    /** Single read of the provider stream into cacheDir/pick (always deleted by callers). */
    private fun copyToCache(ctx: Context, uri: Uri): Result<File> {
        val t0 = System.currentTimeMillis()
        if (uri == Uri.EMPTY) return Result.failure(PhotoException(ImageFailure.UNREADABLE, null, Stage.OPEN))
        // Mime type is safe to log (provider metadata, not user content).
        val mime = runCatching { ctx.contentResolver.getType(uri) }.getOrNull() ?: "unknown"
        var tmp: File? = null
        return try {
            val input: InputStream? = ctx.contentResolver.openInputStream(uri)
            if (input == null) {
                ploge("stage=OPEN ok=false reason=nullStream mime=$mime scheme=${uri.scheme} elapsedMs=${System.currentTimeMillis() - t0}")
                return Result.failure(PhotoException(ImageFailure.UNREADABLE, null, Stage.OPEN))
            }
            val dir = File(ctx.cacheDir, "pick").apply { mkdirs() }
            tmp = File.createTempFile("pick-", ".img", dir)
            val copied = input.use { ins -> tmp.outputStream().use { out -> ins.copyTo(out) } }
            plogd("stage=OPEN ok=true mime=$mime bytes=$copied elapsedMs=${System.currentTimeMillis() - t0}")
            Result.success(tmp)
        } catch (e: Exception) {
            runCatching { tmp?.delete() }
            ploge("stage=OPEN ok=false reason=${e.javaClass.simpleName} mime=$mime scheme=${uri.scheme} elapsedMs=${System.currentTimeMillis() - t0}")
            Result.failure(PhotoException(ImageFailure.UNREADABLE, e, Stage.OPEN))
        }
    }

    /** Decode pipeline + encode budget loop against a local file (see compressWebPDetailed docs). */
    private fun compressFromFile(file: File, maxSide: Int, maxBytes: Int): Result<String> {
        val t0 = System.currentTimeMillis()
        try {
            // 1) Bounds-only decode of the FILE (never hits provider quirks, never loads pixels).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val bW = bounds.outWidth
            val bH = bounds.outHeight
            val bMime = bounds.outMimeType ?: "unknown"
            plogd("stage=BOUNDS w=$bW h=$bH mime=$bMime")

            // 2) Decode with sampling so full-size bitmaps are never held.
            val decoded: Bitmap? = if (Build.VERSION.SDK_INT >= 28) {
                decodeImageDecoder(file, maxSide) ?: decodeFactory(file, bW, bH, maxSide)
            } else {
                decodeFactory(file, bW, bH, maxSide)
            }
            val bmp = decoded
            if (bmp == null) {
                val failure = classifyDecodeFailure(bW, bH)
                ploge("stage=DECODE ok=false failure=$failure boundsMime=$bMime elapsedMs=${System.currentTimeMillis() - t0}")
                return Result.failure(PhotoException(failure, null, Stage.DECODE))
            }
            if (bmp.width <= 0 || bmp.height <= 0) {
                runCatching { bmp.recycle() }
                ploge("stage=DECODE ok=false failure=DECODE_ERROR reason=emptyBitmap elapsedMs=${System.currentTimeMillis() - t0}")
                return Result.failure(PhotoException(ImageFailure.DECODE_ERROR, null, Stage.DECODE))
            }
            plogd("stage=DECODE ok=true w=${bmp.width} h=${bmp.height} elapsedMs=${System.currentTimeMillis() - t0}")

            // 3) Clamp to the exact target side (sample sizes are powers of two, so the
            //    decode can still land up to ~2x over the target). Recycles the source.
            val scaled = scaleDownToSide(bmp, maxSide)
            if (scaled == null) {
                ploge("stage=DECODE ok=false failure=DECODE_ERROR reason=scale elapsedMs=${System.currentTimeMillis() - t0}")
                return Result.failure(PhotoException(ImageFailure.DECODE_ERROR, null, Stage.DECODE))
            }
            fun recycleAll(b: Bitmap) {
                runCatching { scaled.recycle() }
                if (b !== scaled) runCatching { b.recycle() }
            }

            // 4) Adaptive quality loop until <= maxBytes (and >= QUALITY_MIN).
            var quality = QUALITY_START
            var bytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(webpFormat(), quality, out)
                bytes = out.toByteArray()
                quality -= QUALITY_STEP
            } while (bytes.size > maxBytes && quality >= QUALITY_MIN)
            if (bytes.isEmpty()) {
                recycleAll(scaled)
                return Result.failure(PhotoException(ImageFailure.DECODE_ERROR, null, Stage.ENCODE))
            }

            // 5) Still too big after quality floor → shrink the side (x0.85) and retry down to the floor.
            var current: Bitmap = scaled
            var side = maxOf(current.width, current.height)
            while (bytes.size > maxBytes) {
                val smaller = nextSide(side)
                if (smaller >= side) {
                    recycleAll(current) // already at/under floor and still too big → give up
                    ploge("stage=BUDGET ok=false bytes=${bytes.size} max=$maxBytes elapsedMs=${System.currentTimeMillis() - t0}")
                    return Result.failure(PhotoException(ImageFailure.OVER_BUDGET, null, Stage.BUDGET))
                }
                side = smaller
                val scale = side.toFloat() / maxOf(current.width, current.height)
                val m = Matrix().apply { postScale(scale, scale) }
                val prev = current
                try {
                    current = Bitmap.createBitmap(current, 0, 0, current.width, current.height, m, true)
                } catch (e: Exception) {
                    if (prev !== scaled) runCatching { prev.recycle() }
                    recycleAll(scaled)
                    return Result.failure(PhotoException(ImageFailure.OVER_BUDGET, e, Stage.BUDGET))
                }
                if (prev !== scaled) runCatching { prev.recycle() }
                quality = QUALITY_START
                do {
                    val out = ByteArrayOutputStream()
                    current.compress(webpFormat(), quality, out)
                    bytes = out.toByteArray()
                    quality -= QUALITY_STEP
                } while (bytes.size > maxBytes && quality >= QUALITY_MIN)
                if (bytes.isEmpty()) {
                    recycleAll(current)
                    return Result.failure(PhotoException(ImageFailure.DECODE_ERROR, null, Stage.ENCODE))
                }
            }

            // 6) Validate the output really decodes as an image (reject garbage data).
            val finalSide = maxOf(current.width, current.height)
            if (BitmapFactory.decodeByteArray(bytes, 0, bytes.size) == null) {
                recycleAll(current)
                return Result.failure(PhotoException(ImageFailure.DECODE_ERROR, null, Stage.ENCODE))
            }
            recycleAll(current)
            plogd("stage=ENCODE ok=true side=$finalSide bytes=${bytes.size} elapsedMs=${System.currentTimeMillis() - t0}")
            return Result.success(Base64.encodeToString(bytes, Base64.NO_WRAP))
        } catch (e: OutOfMemoryError) {
            return Result.failure(PhotoException(ImageFailure.OVER_BUDGET, RuntimeException("oom"), Stage.BUDGET))
        } catch (e: Exception) {
            return Result.failure(PhotoException(ImageFailure.UNKNOWN, e, Stage.ENCODE))
        }
    }

    /** API 28+: HEIC/HEIF/WebP/AVIF where supported, EXIF applied by the platform,
     *  sampled via setTargetSampleSize so a 12MP photo never materializes at full size. */
    @RequiresApi(28)
    private fun decodeImageDecoder(file: File, maxSide: Int): Bitmap? = runCatching {
        val source = ImageDecoder.createSource(file)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            decoder.setTargetSampleSize(sampleSizeFor(info.size.width, info.size.height, maxSide))
        }
    }.getOrNull()

    /** API 26-27 baseline (also the API 28+ fallback): bounds-first sampling + EXIF rotation
     *  read from the FILE (a local file never refuses a second open, unlike provider streams). */
    private fun decodeFactory(file: File, outW: Int, outH: Int, maxSide: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(outW, outH, maxSide) }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        if (bmp.width <= 0 || bmp.height <= 0) {
            runCatching { bmp.recycle() }
            return null
        }
        val orientation = runCatching {
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val rotation = exifDegrees(orientation)
        if (rotation == 0) return bmp
        val rotated = runCatching {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }.getOrNull()
        if (rotated != null && rotated !== bmp) runCatching { bmp.recycle() }
        // Best effort: an unrotated but decodable image beats failing the pick (documented).
        return rotated ?: bmp
    }

    /** Clamp to exact target side; recycles [bmp] when a new bitmap is produced. */
    private fun scaleDownToSide(bmp: Bitmap, maxSide: Int): Bitmap? {
        val longest = maxOf(bmp.width, bmp.height)
        if (longest <= maxSide) return bmp
        val scale = maxSide.toFloat() / longest
        val out = runCatching {
            val m = Matrix().apply { postScale(scale, scale) }
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }.getOrNull()
        if (out == null) {
            runCatching { bmp.recycle() }
            return null
        }
        if (out !== bmp) runCatching { bmp.recycle() }
        return out
    }

    /** Returns a Base64 WebP, or null if the file is not a decodable image. Kept for callers. */
    fun compressWebP(ctx: Context, uri: Uri, maxSide: Int, maxBytes: Int): String? =
        compressWebPDetailed(ctx, uri, maxSide, maxBytes).getOrNull()

    /** Thumbnail: Base64 WebP <= 240px side and <= 15 KB. */
    fun thumbBase64(ctx: Context, uri: Uri): String? =
        compressWebP(ctx, uri, THUMB_MAX_SIDE, THUMB_MAX_BYTES)

    /** Full image: Base64 WebP <= 720px side and <= ~120 KB. */
    fun fullBase64(ctx: Context, uri: Uri): String? =
        compressWebP(ctx, uri, FULL_MAX_SIDE, FULL_MAX_BYTES)
}
