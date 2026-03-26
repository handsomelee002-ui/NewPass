package com.gero.newpass.utilities;

import android.graphics.Color;
import java.util.regex.Pattern;

public class PasswordStrengthHelper {

    public enum Strength {
        WEAK(Color.parseColor("#F44336"), "Weak"),     // Red
        FAIR(Color.parseColor("#FFC107"), "Fair"),     // Amber
        GOOD(Color.parseColor("#2196F3"), "Good"),     // Blue
        STRONG(Color.parseColor("#4CAF50"), "Strong"); // Green

        public final int color;
        public final String label;

        Strength(int color, String label) {
            this.color = color;
            this.label = label;
        }
    }

    /**
     * Calculates the Shannon entropy of the password and maps it to a Strength enum.
     * < 40 bits = WEAK
     * 40 - 59 bits = FAIR
     * 60 - 79 bits = GOOD
     * >= 80 bits = STRONG
     */
    public static Strength calculateStrength(String password) {
        if (password == null || password.isEmpty()) {
            return Strength.WEAK;
        }

        int length = password.length();
        boolean hasLower = Pattern.compile("[a-z]").matcher(password).find();
        boolean hasUpper = Pattern.compile("[A-Z]").matcher(password).find();
        boolean hasDigit = Pattern.compile("[0-9]").matcher(password).find();
        boolean hasSpecial = Pattern.compile("[^a-zA-Z0-9]").matcher(password).find();

        int poolSize = 0;
        if (hasLower) poolSize += 26;
        if (hasUpper) poolSize += 26;
        if (hasDigit) poolSize += 10;
        if (hasSpecial) poolSize += 32;

        if (poolSize == 0) return Strength.WEAK;

        // Severe penalty for identical repeating characters (e.g., "11111", "aaaaa")
        if (password.matches("^(.)\\1*$")) {
            return Strength.WEAK;
        }

        double entropy = length * (Math.log(poolSize) / Math.log(2));

        // Penalty limits based on character uniformity regardless of length
        if (poolSize <= 10 && entropy >= 60) {
            entropy = 59; // Cap at FAIR for single pools
        } else if (poolSize <= 36 && entropy >= 80) {
            entropy = 79; // Cap at GOOD for max 2 pools
        }

        if (entropy < 40) {
            return Strength.WEAK;
        } else if (entropy < 60) {
            return Strength.FAIR;
        } else if (entropy < 80) {
            return Strength.GOOD;
        } else {
            return Strength.STRONG;
        }
    }
}
