package ru.neoflex.meta.controllers;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * Created by orlov on 27.03.2015.
 */

@Controller
public class IndexController {
    @RequestMapping(value="/**/index.html/**", produces = "text/html")
    public ResponseEntity<byte[]> index(HttpServletRequest request) {
        String path = request.getRequestURI();
        final String pattern = "index.html";
        int index = path.indexOf(pattern);
        ClassPathResource indexResource = new ClassPathResource(path.substring(0, index + pattern.length()));
        try {
            InputStream inputStream = indexResource.getInputStream();
            try {
                BufferedInputStream bis = new BufferedInputStream(inputStream);
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                int result = bis.read();
                while(result != -1) {
                    buf.write((byte) result);
                    result = bis.read();
                }
                return new ResponseEntity<byte[]>(buf.toByteArray(), HttpStatus.OK);
            }
            finally {
                inputStream.close();
            }
        }
        catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
}
