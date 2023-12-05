// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT License.
package brill.server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import brill.server.exception.SecurityServiceException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Random;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password Services - hashes passwords for storing in the DB and also checks passwords.
 * 
 * The client uses the a pepper value from the applications.yaml file plus the password to
 * create a SHA-256 hash, which is sent to the server. The server never gets to see the 
 * cleartext password, only ever the SHA-256 hash.
 * 
 * Rather than store the SHA-256 hashes directly in the DB, they are further hashed on the server using
 * PBKDF2WithHmacSHA384, with a random number of iterations and a 16 byte salt. The username is
 * included in the hash, so that a hash with a known password can't be copied to another user.
 * 
 * The only feasable way for a hacker to reverse a hash back to a password is using a dictionary attack.
 * The hacker would need to know the pepper, salt, username, iterations count and hash plus the algorithms. 
 * 
 * The algorithms for the DB stored hashes are similar to bcrypt but slightly different, so as to ensure
 * that off the shelf bcrypt hacker tools can't be used. A hacker would need to know the differences and
 * moodify the hacker tool accordinly. The value used is a SHA-256 hash of the password, not the actual password.
 * 
 * The password is encrypted on the client and decrypted on the server using AES and a Shared Secret created 
 * using a ECDH key exchange. Although the communications between the client and server are secured using TLS,
 * there's still the possibilty of a password getting logged on either the client or server. Because the
 * ECDH keys are different for each session, the password in the logs is different each time and
 * a replay type attack prevented.
 * 
 */
@Service
public class PasswordService {

    private static int MIN_ITERATIONS = 1200;
    private static int MAX_ITERATIONS = 1800;
    private static int ITERATIONS_ADJUSTMENT = 6789; // Changing this will invalidate all existing passwords in the DB.    

    @Value("${passwords.pepper:}")
    String passwordsPepper;

    // When true, passwords can be manually entered as clear text in the database. This is for initially setting up a new user database.
    // On next login the user is forced to change their password. Once changed, the password will be stored
    // using a PBKDF2WithHmacSHA384 hash.
    @Value("${passwords.allowClearText:false}")
    Boolean allowClearText;

    /**
     * Static method for use by the JavaScript helper class Db.java to hash passwords. The JavaScript Helper classes can't 
     * make use of Spring Boot dependancy injection but can call static methods within an @Service class.
     */
    public static String hashPasswordForJavaScript(String username, String password) throws SecurityServiceException {
        return hash(username, password);
    }

    public String hashPassword(String username, String password) throws SecurityServiceException {
        if (password == null || password.length() == 0) {
            throw new SecurityServiceException("The new password is weak and easily guessable.");
        }
        return hash(username, password);    
    }

    public boolean isValidPassword(String username, String originalPassword, String storedPassword) throws SecurityServiceException {
        try {
            if (originalPassword == null || originalPassword.length() == 0) {
                // Password is less than 8 characters or it contains a common password.
                throw new SecurityServiceException("The password is too short or weak.");
            }
            if (storedPassword == null || storedPassword.length() == 0) {
                throw new SecurityServiceException("Stored password is null or zero length.");
            }
            if (storedPassword.length() < 30) {
                return isValidClearTextPassword(username, originalPassword, storedPassword);
            }
            String[] parts = storedPassword.split("\\.");
            byte[] salt = fromHex(parts[0]);
            int iterations = Integer.parseInt(parts[1]) - ITERATIONS_ADJUSTMENT;
            byte[] hash = fromHex(parts[2]);
            PBEKeySpec spec = new PBEKeySpec((username.toLowerCase() + originalPassword).toCharArray(), salt, iterations, hash.length * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA384");
            byte[] testHash = skf.generateSecret(spec).getEncoded();
            
            if (hash.length != testHash.length) {
                return false;
            }
            for (int i = 0; i < hash.length; i++) {
                if (hash[i] != testHash[i]) {
                    return false;

                }
            }
            return true;

        } catch(NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new SecurityServiceException("Error while validating password.", e);
        } catch (NumberFormatException e) {
            throw new SecurityServiceException("Unable to parse iteration count as it contain non-numeric characters.", e);
        }
    }

    /**
     * When the stored password length is less than 30, it's assumed that the password is stored as clear text. The password from the
     * client has already had the pepper added and hashed using SHA-256. So we need to do the same to the clear text password
     * before it can be compared with the password from the client.
     * 
     * @param username
     * @param originalPassword
     * @param clearTextPassword
     * @return
     * @throws SecurityServiceException
     */
    private boolean isValidClearTextPassword(String username, String originalPassword, String clearTextPassword) throws SecurityServiceException {
        try {
            if (!allowClearText) {
                // The users password is an initial setup password that is stored as clear text (not hashed) but the
                // passwords.allowClearText configuration value in application.yaml is not defined or set to false.
                throw new SecurityServiceException("Password reset required. Please ask the system administrator to reset your password or to change the system config.");
            }
            String pwdPlusPepper = clearTextPassword + passwordsPepper;
            MessageDigest md = MessageDigest.getInstance( "SHA-256");
            md.update(pwdPlusPepper.getBytes());
            byte[] hash = md.digest();
            String hashHex = toHex(hash);
            return hashHex.equals(originalPassword);
        } catch(NoSuchAlgorithmException e) {
            throw new SecurityServiceException("Error while checking password.", e);
        } 
    }

    /**
     * Checks a decrypted re-connection password to see if it's valid.
     * 
     * When a session is re-connected, the client supplies an encrypted password in the reconnection request. 
     * This password is a SHA-256 of the previous session id plus username.
     * 
     * @param username Username.
     * @param sessionId Previous session id.
     * @param password Decrypted re-connection password to check.
     * @return True if the password is valid.
     * @throws SecurityServiceException
     */
    public boolean isValidReconnectPassword(String username, String sessionId, String password) throws SecurityServiceException {
        try {
            // Create a SHA-256 of the previous session id + username
            String original = sessionId + username;
            MessageDigest md = MessageDigest.getInstance( "SHA-256");
            md.update(original.getBytes());
            byte[] originalHash = md.digest();
            String originalHashHex = toHex(originalHash);
            return originalHashHex.equals(password);
        } catch(NoSuchAlgorithmException e) {
            throw new SecurityServiceException("Error while validating re-connection password.", e);
        } 
    }

    /**
     * Hashes a username and password using PBKDF2WithHmacSHA384 with a 16 byte salt and random number of iterations.
     * 
     * @param username
     * @param password
     * @return <salt>.<iteration count>.<hash>
     * @throws SecurityServiceException
     */
    private static String hash(String username, String password) throws SecurityServiceException {
        try {
            int iterations = new Random().nextInt(MAX_ITERATIONS - MIN_ITERATIONS + 1) + MIN_ITERATIONS;
            char[] chars = (username.toLowerCase() + password).toCharArray();
            byte[] salt = getSalt();
            PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, 384);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA384");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return (toHex(salt) + "." + (iterations + ITERATIONS_ADJUSTMENT) + "." + toHex(hash));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException nsae) {
            throw new SecurityServiceException("Password hash failed.");
        }
    }

    private static byte[] getSalt() throws NoSuchAlgorithmException {
        SecureRandom random = new SecureRandom();
        byte bytes[] = new byte[16];
        random.nextBytes(bytes);
        return bytes;
    }

    private static String toHex(byte[] array) throws NoSuchAlgorithmException {
        BigInteger bi = new BigInteger(1, array);
        String hex = bi.toString(16);
        int paddingLength = (array.length * 2) - hex.length();
        if (paddingLength > 0) {
            return String.format("%0" + paddingLength + "d", 0) + hex;
        } else {
            return hex;
        }
    }

    private static byte[] fromHex(String hex) throws NoSuchAlgorithmException {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }
}
