package me.shadura.escposprint.ssl;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import me.shadura.escposprint.EscPosPrintApp;
import me.shadura.escposprint.app.HostNotVerifiedActivity;

/**
 * Used with {@link HostNotVerifiedActivity} to trust certain hosts
 */
public class AndroidCupsHostnameVerifier implements HostnameVerifier {
    @Override
    public boolean verify(String hostname, SSLSession session) {
        return HostNotVerifiedActivity.isHostnameTrusted(EscPosPrintApp.getContext(), hostname);
    }
}
