package ru.neoflex.meta.license;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

import org.bouncycastle.openssl.PEMReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.neoflex.meta.utils.JSONHelper;

//@Service
public class LicenseService {
    {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    private Resource privateKey;
    private Resource publicCrt;
    @Value("classpath:license.properties")
    private Resource license;
    
    @Autowired
    ResourceLoader resourceLoader;    
    
    private final String sign_format = "SHA256withRSA";
    
    private static ObjectMapper mapper = new ObjectMapper();
    
    private final Properties licenseProperties = new Properties();
    
    public Properties getLicenseProperties() {
        return licenseProperties;
    }
    
    public class NotValidSignatureException extends Exception {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;
        
    }
    
    public class LicenseSignatureNotFound extends Exception {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;
        
    }
    
    public Boolean verifyLicenseSignature() throws Exception {
        Resource licenseSign;
        try {
            licenseSign = resourceLoader.getResource("classpath:license.sha256");
        } catch(Exception e) {
            LicenseSignatureNotFound s = new LicenseSignatureNotFound();
            s.setStackTrace(e.getStackTrace());
            throw s;
        }
        return this.verifySignature(Files.readAllBytes(license.getFile().toPath()), Files.readAllBytes(licenseSign.getFile().toPath()), publicCrt.getFile().toPath().toString());
    }
    
    @PostConstruct
    public void initService() throws Exception {
        
        try {
            privateKey = resourceLoader.getResource("classpath:private_key.der");
        } catch(Exception e) {
            privateKey = null;
        }        

        try {
            publicCrt = resourceLoader.getResource("classpath:public.crt");
        } catch(Exception e) {
            publicCrt = null;
        }                
        
        try {
            //license = resourceLoader.getResource("classpath:license.properties");            
            String content = new String(Files.readAllBytes(license.getFile().toPath()));
            if(!verifyLicenseSignature()) {
                throw new NotValidSignatureException();
            }            
            licenseProperties.load(new StringReader(content));
        } catch(Exception e) {
            license = null;
            if(e.getClass().equals(NotValidSignatureException.class) || e.getClass().equals(LicenseSignatureNotFound.class)) {
                throw e;
            }
        }
    }   
    
    public String encryptTextByPublic(String msg, Cipher cipher) throws Exception {
        cipher.init(Cipher.ENCRYPT_MODE, this.getPublic());
        return Base64.getEncoder().encodeToString(cipher.doFinal(msg.getBytes("UTF-8")));
    }

    public String encryptTextByPrivate(String msg, Cipher cipher) throws Exception {
        cipher.init(Cipher.ENCRYPT_MODE, this.getPrivate());
        return Base64.getEncoder().encodeToString(cipher.doFinal(msg.getBytes("UTF-8")));
    }
    
    public String decryptTextWithPublic(String msg, Cipher cipher) throws Exception {
        cipher.init(Cipher.DECRYPT_MODE, this.getPublic());
        return new String(cipher.doFinal(Base64.getDecoder().decode(msg)), "UTF-8");
    }
    
    public String decryptTextWithPublicNoBase64(String msg, Cipher cipher) throws Exception {
        cipher.init(Cipher.DECRYPT_MODE, this.getPublic());
        return new String(cipher.doFinal(msg.getBytes()), "UTF-8");
    }

    public String decryptTextWithPrivate(String msg, Cipher cipher) throws Exception {
        cipher.init(Cipher.DECRYPT_MODE, this.getPrivate());
        return new String(cipher.doFinal(Base64.getDecoder().decode(msg)), "UTF-8");
    }
    
    public String decryptTextWithPrivate(String msg, Cipher cipher, String key) throws Exception {
        cipher.init(Cipher.DECRYPT_MODE, this.getPrivate(key));
        return new String(cipher.doFinal(Base64.getDecoder().decode(msg)), "UTF-8");
    }    
    
    public boolean verifySignature(byte[] data, byte[] signature, String keyFile) throws Exception {
        Signature sig = Signature.getInstance(sign_format);
        sig.initVerify(getPublic(keyFile));
        sig.update(data);
        
        return sig.verify(signature);
    }
    
    private PublicKey getPublic() throws Exception {
        PEMReader pemReader = new PEMReader(new FileReader(publicCrt.getFile()));
        X509Certificate cert = (X509Certificate)pemReader.readObject();        
        PublicKey key = cert.getPublicKey();   
        pemReader.close();
        return key;
    }    
    
    private PublicKey getPublic(String filename) throws Exception {
        PEMReader pemReader = new PEMReader(new FileReader(filename));
        X509Certificate cert = (X509Certificate)pemReader.readObject();        
        PublicKey key = cert.getPublicKey();   
        pemReader.close();
        return key;
    }    
    
    private PrivateKey getPrivate() throws Exception {
        byte[] keyBytes = Files.readAllBytes(privateKey.getFile().toPath());
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    private PrivateKey getPrivate(String filename) throws Exception {
        byte[] keyBytes = Files.readAllBytes(new File(filename).toPath());
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }    

    public boolean isLogRestriction(Integer userSessionCount, Integer allSessionCount) {
                
        if(this.licenseProperties == null) {
            return false;
        }

        if(this.licenseProperties.getProperty("oneUserRestriction") != null) {
            if(Integer.valueOf(this.licenseProperties.getProperty("oneUserRestriction")) < userSessionCount) {
                return true;
            }
        }
        
        if(this.licenseProperties.getProperty("allUserRestriction") != null) {
            if(Integer.valueOf(this.licenseProperties.getProperty("allUserRestriction")) < allSessionCount) {
                return true;
            }
        }
        
        return false;
    }
    
    public void saveSessionLogLine(String loggingSessionsFile, String user, Integer sessionCount, Integer allSessionCount) throws NoSuchAlgorithmException, NoSuchPaddingException, Exception {                
        SessionsLogEntry entry = new SessionsLogEntry();
        entry.date = JSONHelper.formatDate(new Date());
        entry.user = user;
        entry.sessionsCount = sessionCount;
        saveSignedLog(loggingSessionsFile, entry);
    }
    
    private LicenseService.SignedLogEntry mapStr(String str)  {
        try {
            return mapper.readValue(str, LicenseService.SignedLogEntry.class);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }        
    
    public Boolean isRestristionActive(List<String> loglines) throws ParseException {

        Date licenseStart = licenseProperties.get("start") == null ? null : (new SimpleDateFormat("yyyy-mm-dd")).parse((String) licenseProperties.get("start"));
        
        List<LicenseService.SignedLogEntry> entities = loglines.stream()
                .map(this::mapStr)
                .filter(entity -> {                    
                        return entity != null && !entity.message.user.equals("TIMER") && (licenseStart == null || entity.message.getParsedDate().after(licenseStart));
                        })
                .collect(Collectors.toList());
        
        if(entities.size() == 0) {
            return false;
        }
        
        Integer gracePeriod = licenseProperties.get("gracePeriod") == null ? null : Integer.valueOf(licenseProperties.get("gracePeriod").toString());                 
        
        if(gracePeriod == -1) {
            return false;
        }
        
        SessionsLogEntry minDate = entities
                .stream().map(e -> e.message)
                .min(Comparator.comparing(SessionsLogEntry::getParsedDate)).orElseGet(null);
        
        if(minDate != null && gracePeriod == null) {
            return true;
        }
        
        if(minDate != null && gracePeriod != null && gracePeriod != -1) {
            Calendar c = Calendar.getInstance();
            c.setTime(minDate.getParsedDate());
            c.add(Calendar.DAY_OF_MONTH, gracePeriod);
            return (new Date()).after(c.getTime());
        }        
        
        return false;
    }
    
    public List<String> readLog(Path file) {
        List<String> lines = null;

        try {
            if (Files.notExists(file)) {
                Files.createFile(file);
            }
            
            lines = Files.readAllLines(file);
        } catch(Exception e) {
            e.printStackTrace();
        }
        return lines;
    }
    
    private void writeLog(Path file, List<String> lines) {
        try {
            Files.write(file, lines, StandardOpenOption.WRITE);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    private List<String> addMessage(List<String> lines, SessionsLogEntry entry) throws NoSuchAlgorithmException, NoSuchPaddingException, Exception {
        
        String message = mapper.writeValueAsString(entry);
        
        String contentToSign = message;
        
        
        if(lines != null && lines.size() > 0) {
            LicenseService.SignedLogEntry lineEntry = mapStr(lines.get(lines.size() - 1));
            
            contentToSign = message + mapper.writeValueAsString(lineEntry.message);
        }
        
        String sign = this.encryptTextByPublic(contentToSign, Cipher.getInstance("RSA"));
        
        String newLine = "{\"message\":" + message + ", \"sign\":\"" + sign + "\"}";
                            
        lines.add(newLine);
        
        return lines;
    }
    
    private void saveSignedLog(String loggingSessionsFile, SessionsLogEntry entry) throws Exception {
                               
        Path p = Paths.get(loggingSessionsFile);
        
        List<String> lines = readLog(p);
        
        addMessage(lines, entry);
        
        writeLog(p, lines);

    }
    
    static class SessionsLogEntry {
        
        public String date;
        public String user;
        public Integer sessionsCount;
        
        @JsonIgnore
        public Date getParsedDate() {
            try {
                return JSONHelper.parseDate(date);
            } catch (ParseException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                return null;
            }
        }
        
        public SessionsLogEntry() {
            
        }
    }
       
    static class SignedLogEntry {
        
        public SessionsLogEntry message;
        public String sign;
        
        public SignedLogEntry() {
            
        }        
    }
    
    public static void main(String[] args) throws Exception {
        String fileName = args[0];
        String privateKey = args[1];
        
        Path p = Paths.get(fileName);
        List<String> lines = null;
        
        LicenseService service = new LicenseService();

        if (Files.exists(p)) {
            lines = Files.readAllLines(p);
            Cipher cipher = Cipher.getInstance("RSA");
            for(int i = 0; i < lines.size();i++) {
                String line = lines.get(i);
                LicenseService.SignedLogEntry lineEntry = mapper.readValue(line, LicenseService.SignedLogEntry.class);
                                
                String content = mapper.writeValueAsString(lineEntry.message);
                if(i > 0) {
                    LicenseService.SignedLogEntry previous = mapper.readValue(lines.get(i - 1), LicenseService.SignedLogEntry.class);
                    content = content + mapper.writeValueAsString(previous.message);
                }
                
                String decripted = service.decryptTextWithPrivate(lineEntry.sign, cipher, privateKey); 
                
                if(!decripted.equals(content) ) {
                    throw new RuntimeException("Line " + (i + 1) + " is corrupted!");
                }
            }
            System.out.println("Valid!");
        }
                        
    }
}
