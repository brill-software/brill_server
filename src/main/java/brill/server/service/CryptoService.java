// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT License.
package brill.server.service;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.ECPointUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.springframework.stereotype.Service;
import brill.server.exception.CryptoServiceException;
import static brill.server.utils.HexUtils.hexToBytes;
import static brill.server.utils.HexUtils.bytesToHex;
import static java.lang.String.format;


/**
 * Cryptography Services
 * 
 * Provides methods for encryption and decryption using ECDH and AES. A set of secp256k1 public keys are excahnged between
 * the client and server. The keys are used to calculate a shared secret. The calculated shared secret is hashed using SHA-256
 * and used as the Shared Secret by both both parites to perform AES encryption/decryption.
 * 
 */

class KeyPairHex {
    public String publicKey;
    public String privateKey;
    
    public KeyPairHex(String publicKey, String privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }
}

@Service
public class CryptoService {

    public KeyPairHex generateKeyPair() throws CryptoServiceException{
        try {
            Security.addProvider(new BouncyCastleProvider());
            KeyPairGenerator kpgen = KeyPairGenerator.getInstance("ECDH", "BC");
            kpgen.initialize(new ECGenParameterSpec("secp256k1"), new SecureRandom());
            KeyPair kp = kpgen.generateKeyPair();
            String publicKey = bytesToHex(ecKeyBytesFromDERKey(kp.getPublic().getEncoded()));
            String privateKey =  bytesToHex(kp.getPrivate().getEncoded());
            return new KeyPairHex(publicKey, privateKey);
        } catch (NoSuchProviderException | NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new CryptoServiceException(format("Key pair generation exception: %s", e.getMessage()));
        } 
    }

    /**
     * Gets a shared secret using the Server Private Key and Client Public Key. The client
     * should arrive at the same shared secret using the Server Public Key and Client Private Key.
     *  
     * @param serverPrivateKeyHex Hex string containing the Server Public Key.
     * @param clientPublicKeyHex Hex string containing the Client Public Key.
     * @return 64 character hex string representing 32 bytes.
     */
    public String getSharedSecret(String serverPrivateKeyHex, String clientPublicKeyHex) throws CryptoServiceException {
        try {
            if (clientPublicKeyHex.length() != 130) {
                throw new CryptoServiceException("The Client Public Key must consist of 130 hex characters.");
            }

            // Get the private key
            PrivateKey serverPrivateKey = privateKeyFromEncoded(hexToBytes(serverPrivateKeyHex));
            
            // Get the Public Key
            PublicKey clientPublicKey = publicKeyFromEC(hexToBytes(clientPublicKeyHex));

            // Generate the shared secret.
            KeyAgreement ka = KeyAgreement.getInstance("ECDH", "BC");
            ka.init(serverPrivateKey);
            ka.doPhase(clientPublicKey, true);
            byte[] sharedSecret = ka.generateSecret();

            // Apply a SHA-256 hash.
            MessageDigest md = MessageDigest.getInstance( "SHA-256");
            md.update(sharedSecret);
            byte[] hashedSharedSecret = md.digest();

            String hashedSharedSecretHex = bytesToHex(hashedSharedSecret);
            return hashedSharedSecretHex;

        } catch (NoSuchProviderException | NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
            throw new CryptoServiceException(format("Shared secret exception: %s", e.getMessage()));
        } 
    }

    public String encrypt(String hex, String sharedSecretHex) throws CryptoServiceException {
        try {
            // Create an initialization vector (IV) with all zeros.
            byte[] iv = new byte[16]; 
            Arrays.fill(iv, (byte)0);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
           
            // Decrypt cypherTextHex
            SecretKeySpec secretKeySpec = new SecretKeySpec(hexToBytes(sharedSecretHex), "AES");
            Cipher aes = Cipher.getInstance("AES/CTR/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec);
            byte[] encryptedBytes = aes.doFinal(hexToBytes(hex));

            return bytesToHex(encryptedBytes);

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException e) {
            throw new CryptoServiceException(format("Decryption exception: %s", e.getMessage()));
        }
    }

    public String decrypt(String cypherTextHex, String sharedSecretHex) throws CryptoServiceException {
        try {
            // Create an initialization vector (IV) with all zeros.
            byte[] iv = new byte[16]; 
            Arrays.fill(iv, (byte)0);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
           
            // Decrypt cypherTextHex
            SecretKeySpec secretKeySpec = new SecretKeySpec(hexToBytes(sharedSecretHex), "AES");
            Cipher aes = Cipher.getInstance("AES/CTR/NoPadding");
            aes.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec);
            byte[] decryptedBytes = aes.doFinal(hexToBytes(cypherTextHex));

            return bytesToHex(decryptedBytes);

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException e) {
            throw new CryptoServiceException(format("Decryption exception: %s", e.getMessage()));
        }
    }

    private static byte[] ecKeyBytesFromDERKey(byte[] ourPk) {
        ASN1Sequence sequence = DERSequence.getInstance(ourPk);
        DERBitString subjectPublicKey = (DERBitString) sequence.getObjectAt(1);
        return subjectPublicKey.getBytes();
    }

    private PrivateKey privateKeyFromEncoded(byte[] encodedByteArray) throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory kf = KeyFactory.getInstance("EC", "BC");
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(encodedByteArray);
        PrivateKey privateKey = kf.generatePrivate(pkcs8EncodedKeySpec);
        return privateKey;
    }

    private static PublicKey publicKeyFromEC(byte[] ecKeyByteArray) throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory kf = KeyFactory.getInstance("EC", "BC");
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
        ECNamedCurveSpec params = new ECNamedCurveSpec("secp256k1", spec.getCurve(), spec.getG(), spec.getN());
        ECPoint publicPoint = ECPointUtil.decodePoint(params.getCurve(), ecKeyByteArray);
        ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(publicPoint, params);
        return kf.generatePublic(pubKeySpec);
    }
}