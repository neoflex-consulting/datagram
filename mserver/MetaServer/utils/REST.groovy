package MetaServer.utils

import groovyx.net.http.RESTClient
import ru.neoflex.meta.utils.KerberosRestClient

class REST {
    public static Object getSimpleHTTPClient(String url, Map config) {
        if (config.isKerberosEnabled == true) {
            return new KerberosRestClient(url, config.keyTabLocation, config.userPrincipal)
        } else {
            return new RESTClient( url )
        }
    }

    public static Object getSimpleHTTPClient(Map config) {
        return getSimpleHTTPClient(config.http, config)
    }

    public static Object getHTTPClient(String url, Map config) {
        def client = getSimpleHTTPClient(url, config)
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
    }

}
