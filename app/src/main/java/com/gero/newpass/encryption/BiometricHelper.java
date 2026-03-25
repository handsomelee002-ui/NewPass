package com.gero.newpass.encryption;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;

import javax.crypto.Cipher;

public class BiometricHelper {
    private static final String KEY_NAME = "newpass_biometric_rsa_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    // RSA/ECB/PKCS1Padding is supported by AndroidKeyStore
    private static final String TRANSFORMATION = KeyProperties.KEY_ALGORITHM_RSA + "/" +
            KeyProperties.BLOCK_MODE_ECB + "/" +
            KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1;

    public static void createBiometricKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_NAME)) {
            keyStore.deleteEntry(KEY_NAME);
        }

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE);

        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                KEY_NAME,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setUserAuthenticationRequired(true);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            builder.setInvalidatedByBiometricEnrollment(true);
        }

        keyPairGenerator.initialize(builder.build());
        keyPairGenerator.generateKeyPair();
    }

    public static Cipher getEncryptionCipher() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        PublicKey publicKey = keyStore.getCertificate(KEY_NAME).getPublicKey();

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher;
    }

    public static Cipher getDecryptionCipher() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(KEY_NAME, null);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher;
    }
}
