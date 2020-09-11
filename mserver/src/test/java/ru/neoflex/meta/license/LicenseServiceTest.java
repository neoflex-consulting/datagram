package ru.neoflex.meta.license;

import static org.junit.Assert.assertEquals;
import static org.powermock.api.mockito.PowerMockito.spy;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.core.io.ClassRelativeResourceLoader;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.neoflex.meta.license.LicenseService.NotValidSignatureException;
import ru.neoflex.meta.utils.JSONHelper;

@RunWith(PowerMockRunner.class)
@PrepareForTest({LicenseService.class})
public class LicenseServiceTest {
    
    LicenseService licenseService;
    List<String> lines = new ArrayList<String>();             

    @Before
    public void setUp() throws Exception {
        licenseService = spy(new LicenseService());
        licenseService.resourceLoader = new ClassRelativeResourceLoader(LicenseService.class);
        PowerMockito.doNothing().when(licenseService, "writeLog", ArgumentMatchers.any(), ArgumentMatchers.any());
        PowerMockito.doReturn("").when(licenseService, "encryptTextByPublic", ArgumentMatchers.any(), ArgumentMatchers.any());
        PowerMockito.doReturn(lines).when(licenseService, "readLog", ArgumentMatchers.any());
    }
    
    @Test
    public final void testRestrictionActive() throws Exception {                
        licenseService.getLicenseProperties().put("gracePeriod", 0);        
        lines.clear();        
        licenseService.saveSessionLogLine("", "user", 1, 1);        
        assertEquals(1, lines.size());        
        assertEquals(true, licenseService.isRestristionActive(lines));
    }
    
    @Test
    public final void testIgnoreRestriction() throws Exception {                
        licenseService.getLicenseProperties().put("gracePeriod", -1);        
        lines.clear();        
        licenseService.saveSessionLogLine("", "user", 1, 1);        
        assertEquals(1, lines.size());        
        assertEquals(false, licenseService.isRestristionActive(lines));
    }    

    @Test
    public final void testRestrictionNotActiveByGracePeriod() throws Exception {                 
        licenseService.getLicenseProperties().put("gracePeriod", 1);        
        lines.clear();        
        licenseService.saveSessionLogLine("", "user", 1, 1);        
        assertEquals(1, lines.size());        
        assertEquals(false, licenseService.isRestristionActive(lines));
    }
    
    @Test
    public final void testRestrictionActiveByGracePeriodExpiration() throws Exception {         
        
        licenseService.getLicenseProperties().put("gracePeriod", 2);

        LicenseService.SignedLogEntry logEntry = new LicenseService.SignedLogEntry();
        logEntry.message = new LicenseService.SessionsLogEntry();
        
        Calendar c = Calendar.getInstance();
        
        c.setTime(new Date());
        c.add(Calendar.DAY_OF_MONTH, -3);
        
        logEntry.message.date = JSONHelper.formatDate(c.getTime());
        logEntry.message.user = "user";
        
        lines.clear();
        String m = (new ObjectMapper()).writeValueAsString(logEntry);
        lines.add(m);
        
        assertEquals(1, lines.size());        
        assertEquals(true, licenseService.isRestristionActive(lines));
    }
    
    //@Test
    public final void testServiceInitialization() throws Exception {
        try {
            licenseService.initService();
        } catch (Throwable e) {
            
        }
        Mockito.verify(licenseService,  Mockito.times(1)).verifyLicenseSignature();
    }
    
    //@Test(expected=NotValidSignatureException.class)
    public final void testServiceInitializationException() throws Exception {
        licenseService.initService();
    }
    
}
