package com.noypi.docguardxray.utils;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public final class HashUtils {
    private HashUtils() {}

    public static String sha256Hex(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}

