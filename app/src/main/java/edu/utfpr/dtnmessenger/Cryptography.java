package edu.utfpr.dtnmessenger;

import android.annotation.TargetApi;
import android.os.Build;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class Cryptography {
    private static final int DEFAULT_PUB_KEY_LENGTH = 216;
    private static final int DEFAULT_AES_KEY_LENGTH = 172;

    //--------------- Asymmetrical Cryptography ---------------

    public static int getDefaultPublicKeyLength() {
        return DEFAULT_PUB_KEY_LENGTH;
    }

    @TargetApi(Build.VERSION_CODES.O)
    public static String encryptRSA(PublicKey key, String message) throws Exception {
        Cipher rsa = Cipher.getInstance("RSA");
        rsa.init(Cipher.ENCRYPT_MODE, key);
        rsa.update(message.getBytes());
        String result = Base64.getEncoder().encodeToString(rsa.doFinal());
        return result;
    }

    @TargetApi(Build.VERSION_CODES.O)
    public static String decryptRSA(PrivateKey key, String cipherText) throws Exception {
        Cipher rsa;
        rsa = Cipher.getInstance("RSA");
        rsa.init(Cipher.DECRYPT_MODE, key);
        rsa.update(Base64.getDecoder().decode(cipherText));
        String result = new String(rsa.doFinal());
        return result;
    }

    public static String generateKeyPairString() {
        try {
            SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(1024, sr);
            KeyPair keyPair = keyGen.generateKeyPair();
            String publicKeyString = getPublicKeyString(keyPair.getPublic());
            String privateKeyString = getPrivateKeyString(keyPair.getPrivate());
            String keyPairString = publicKeyString + privateKeyString;
            return keyPairString;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static PublicKey getPublicKeyFromString(String pubKey) {
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(android.util.Base64.decode(pubKey, android.util.Base64.NO_WRAP)));
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static PrivateKey getPrivateKeyFromString(String privKey) {
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(android.util.Base64.decode(privKey, android.util.Base64.NO_WRAP)));
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static KeyPair getKeyPairFromKeyPairString(String keyPairString) {
        String publicKeyString = keyPairString.substring(0, DEFAULT_PUB_KEY_LENGTH);
        String privateKeyString = keyPairString.substring(DEFAULT_PUB_KEY_LENGTH);
        PublicKey publicKey = getPublicKeyFromString(publicKeyString);
        PrivateKey privateKey = getPrivateKeyFromString(privateKeyString);
        KeyPair keyPair = new KeyPair(publicKey, privateKey);
        return keyPair;
    }

    public static String getPublicKeyString(PublicKey pubKey) {
        return new String(android.util.Base64.encode(pubKey.getEncoded(), android.util.Base64.NO_WRAP));
    }

    public static String getPrivateKeyString(PrivateKey privKey) {
        return new String(android.util.Base64.encode(privKey.getEncoded(), android.util.Base64.NO_WRAP));
    }

    //--------------- Symmetrical Cryptography ---------------

    public static int getDefaultAESKeyLength() {
        return DEFAULT_AES_KEY_LENGTH;
    }

    //A UUID represents a 128-bit value (2 long).
    // To represent 128 bit into hex string there will be 128/4=32 char (each char is 4bit long).
    // In string format it also contains 4 (-) that's why the length is always 36 bytes.
    // The UUID is generated using a cryptographically strong pseudo random number generator.
    public static String getNewAESSecretKey() {
        return UUID.randomUUID().toString();
    }

    private static SecretKeySpec setKey(String myKey) {
        MessageDigest sha = null;
        try {
            byte[] key = myKey.getBytes(StandardCharsets.UTF_8);
            sha = MessageDigest.getInstance("SHA-1");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            return secretKey;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.O)
    public static String encryptAES(String strToEncrypt, String secret) {
        try {
            SecretKeySpec secretKey = setKey(secret);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder()
                    .encodeToString(cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            //Catch logic
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.O)
    public static String decryptAES(final String strToDecrypt, final String secret) {
        try {
            SecretKeySpec secretKey = setKey(secret);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder()
                    .decode(strToDecrypt)));
        } catch (Exception e) {
            //Catch logic
        }
        return null;
    }
}
