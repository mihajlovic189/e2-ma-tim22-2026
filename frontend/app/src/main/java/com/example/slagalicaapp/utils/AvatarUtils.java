package com.example.slagalicaapp.utils;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Avatars are stored as small base64-encoded JPEG data URIs directly on the user's
 * Firestore document, instead of the raw local content:// URI the image picker returns.
 * A content:// URI only ever resolves on the device that originally picked it, so storing
 * it as-is means other users' devices can never display that avatar — encoding the actual
 * image bytes makes it visible everywhere the Firestore doc is read.
 */
public final class AvatarUtils {

    private static final String DATA_URI_PREFIX = "data:image/jpeg;base64,";
    private static final int TARGET_SIZE = 160;
    private static final int JPEG_QUALITY = 80;

    private AvatarUtils() {}

    /** Reads the picked image, downsizes/crops it to a square and returns a data: URI ready to store in Firestore, or null on failure. */
    public static String encodeFromUri(ContentResolver resolver, Uri uri) {
        try (InputStream is = resolver.openInputStream(uri)) {
            if (is == null) return null;
            Bitmap original = BitmapFactory.decodeStream(is);
            if (original == null) return null;

            Bitmap square = cropToSquare(original);
            Bitmap scaled = Bitmap.createScaledBitmap(square, TARGET_SIZE, TARGET_SIZE, true);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos);
            return DATA_URI_PREFIX + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap cropToSquare(Bitmap bitmap) {
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        int x = (bitmap.getWidth() - size) / 2;
        int y = (bitmap.getHeight() - size) / 2;
        return Bitmap.createBitmap(bitmap, x, y, size, size);
    }

    /** Renders a stored avatar value (data: URI, legacy local content:// URI, or empty) into an ImageView. */
    public static void apply(ImageView imageView, String avatarUri, int fallbackDrawableRes) {
        if (avatarUri == null || avatarUri.trim().isEmpty()) {
            imageView.setImageResource(fallbackDrawableRes);
            return;
        }

        if (avatarUri.startsWith("data:image")) {
            int comma = avatarUri.indexOf(',');
            if (comma >= 0) {
                try {
                    byte[] bytes = Base64.decode(avatarUri.substring(comma + 1), Base64.NO_WRAP);
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bmp != null) {
                        imageView.setImageBitmap(bmp);
                        return;
                    }
                } catch (Exception ignored) {}
            }
            imageView.setImageResource(fallbackDrawableRes);
            return;
        }

        // Legacy local content:// URI from before avatars were embedded — only ever
        // resolves on the device that picked it.
        try {
            imageView.setImageURI(Uri.parse(avatarUri));
        } catch (Exception e) {
            imageView.setImageResource(fallbackDrawableRes);
        }
    }
}
