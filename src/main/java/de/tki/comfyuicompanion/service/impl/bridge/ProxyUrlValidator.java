package de.tki.comfyuicompanion.service.impl.bridge;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Validates URLs that the preview proxy is allowed to fetch. Only http(s) URLs pointing at
 * loopback IP literals are accepted, which prevents SSRF and DNS-rebinding abuse.
 */
public final class ProxyUrlValidator {

    /**
     * Checks whether the given URL may be proxied.
     *
     * @param urlStr the URL to check
     * @return {@code true} if it is an http(s) URL with a loopback IP-literal host
     */
    public boolean isValidProxyUrl(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(urlStr);
        } catch (Exception e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return false;
        }
        // Only allow IP literals in the loopback range. We refuse DNS lookups
        // entirely so a domain cannot be used for DNS-rebinding SSRF.
        if (!isLoopbackLiteral(host)) {
            return false;
        }
        // Confirm the literal really is loopback (defense against weird inputs
        // that just happen to start with "127." but are not real IPv4 literals).
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private boolean isLoopbackLiteral(String host) {
        if (host == null) {
            return false;
        }
        if (host.equals("::1") || host.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        return host.startsWith("127.");
    }
}
