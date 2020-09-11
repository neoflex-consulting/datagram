package MetaServer.rt

import MetaServer.utils.REST
import ru.neoflex.meta.utils.SymmetricCipher

class Oozie {
    public static Object deployKey(Map entity, Map params = null) {
        def key = SymmetricCipher.getKey()
        if (key != null) {
            def keyFileName = "${entity.home}/${entity.user}/skey.bin"
            def http = REST.getHTTPClient(entity.webhdfs + "/", entity)
            def put1 = http.put(
                    path: keyFileName.substring(1),
                    query : ['user.name': entity.user, 'op': "CREATE", 'permission': '600']
            )
            def put2 = http.put(
                    uri: put1.resp.headers.location,
                    requestContentType : groovyx.net.http.ContentType.BINARY,
                    contentType : groovyx.net.http.ContentType.ANY,
                    body : key
            )
        }
        return [status: "OK"]
    }
}
