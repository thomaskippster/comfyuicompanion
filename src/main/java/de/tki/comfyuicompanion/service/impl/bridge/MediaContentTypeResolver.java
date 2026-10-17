package de.tki.comfyuicompanion.service.impl.bridge;

/**
 * Resolves a media content type for proxied template previews when the upstream server does not
 * provide a meaningful one.
 */
public final class MediaContentTypeResolver {

    /**
     * Returns the upstream content type if meaningful, otherwise derives one from the URL extension.
     *
     * @param upstreamContentType content type reported upstream (may be {@code null})
     * @param url                 the fetched URL used for extension sniffing
     * @return a non-null content type, defaulting to {@code application/octet-stream}
     */
    public String resolve(String upstreamContentType, String url) {
        if (upstreamContentType != null && !upstreamContentType.trim().isEmpty()
                && !upstreamContentType.equalsIgnoreCase("application/octet-stream")) {
            return upstreamContentType;
        }
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains(".png")) {
            return "image/png";
        } else if (lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg")) {
            return "image/jpeg";
        } else if (lowerUrl.contains(".webp")) {
            return "image/webp";
        } else if (lowerUrl.contains(".gif")) {
            return "image/gif";
        } else if (lowerUrl.contains(".mp4")) {
            return "video/mp4";
        } else if (lowerUrl.contains(".webm")) {
            return "video/webm";
        } else if (lowerUrl.contains(".mov")) {
            return "video/quicktime";
        }
        return "application/octet-stream";
    }
}
