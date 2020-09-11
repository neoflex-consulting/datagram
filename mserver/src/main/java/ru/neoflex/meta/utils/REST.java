package ru.neoflex.meta.utils;

import java.net.URISyntaxException;

import groovyx.net.http.RESTClient;

class REST {
    public static RESTClient getSimpleHTTPClient(String url, boolean isKerberosEnabled, String keyTabLocation, String userPrincipal) throws URISyntaxException {
        if (isKerberosEnabled == true) {
            return new KerberosRestClient(url, keyTabLocation, userPrincipal);
        } else {
            return new RESTClient(url);
        }
    }
    /*
    public static RESTClient getHTTPClient(String url, boolean isKerberosEnabled, String keyTabLocation, String userPrincipal) throws URISyntaxException {
    	return getSimpleHTTPClient(url, isKerberosEnabled, keyTabLocation, userPrincipal);
        client.handler.failure = { resp ->
            throw new RuntimeException(resp.statusLine.toString())
        }
        client.handler.success = { resp, reader ->
            [resp:resp, reader:reader]
        }
       return client 
    }
    public static Object getHTTPClient(Map config) {
        return getHTTPClient(config.http, config)
    }

    public static Object getHTTPClient(String url) {
        return getHTTPClient(url, [isKerberosEnabled: false])
    } */

}
