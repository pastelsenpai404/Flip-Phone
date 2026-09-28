package dev.codex.flippost;

import android.content.Context;
import android.security.KeyPairGeneratorSpec;
import android.util.Base64;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.util.Calendar;
import javax.crypto.Cipher;
import javax.security.auth.x500.X500Principal;

/** Android 5.1 compatible private-key storage; never fall back to plaintext. */
final class Secrets {
    private static final String ALIAS="FlipPostMailPassword";
    private static KeyStore keys(Context context,boolean create)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(create&&!store.containsAlias(ALIAS)){
            Calendar start=Calendar.getInstance(),end=Calendar.getInstance();end.add(Calendar.YEAR,20);
            KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA","AndroidKeyStore");
            generator.initialize(new KeyPairGeneratorSpec.Builder(context).setAlias(ALIAS).setKeySize(2048)
                .setSubject(new X500Principal("CN=Flip Post Mail")).setSerialNumber(BigInteger.ONE)
                .setStartDate(start.getTime()).setEndDate(end.getTime()).build());generator.generateKeyPair();
        }
        return store;
    }
    static String encrypt(Context context,String password)throws Exception{
        if(password.getBytes("UTF-8").length>200)throw new Exception("Password is too long");
        Cipher cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE,keys(context,true).getCertificate(ALIAS).getPublicKey());
        return Base64.encodeToString(cipher.doFinal(password.getBytes("UTF-8")),Base64.NO_WRAP);
    }
    static String decrypt(Context context,String saved)throws Exception{
        if(saved.length()==0)throw new Exception("Mail account is not configured");
        Cipher cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE,keys(context,false).getKey(ALIAS,null));
        return new String(cipher.doFinal(Base64.decode(saved,Base64.NO_WRAP)),"UTF-8");
    }
    static void forget(Context context)throws Exception{KeyStore store=keys(context,false);if(store.containsAlias(ALIAS))store.deleteEntry(ALIAS);}
}
