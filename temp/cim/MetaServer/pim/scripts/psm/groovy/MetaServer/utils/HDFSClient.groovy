package MetaServer.utils

import groovy.io.FileType

class HDFSClient {

    String url
    String user
    Object config

    HDFSClient(url, user, config) {
        this.url = url
        this.user = user
        this.config = config
    }

    def client = REST.getSimpleHTTPClient(url + '/', config)

    public Object deleteDir(String path) {
        def res = client.delete(
                path : getPath(path),
                requestContentType : groovyx.net.http.ContentType.ANY,
                contentType : groovyx.net.http.ContentType.JSON,
                query : ['user.name': user, 'op': "DELETE" , 'recursive': "true"]
        )

        return [result: res.getData().boolean, problems:[]]
    }

    public Object delete(String path) {
        def res = client.delete(
                path : getPath(path),
                requestContentType : groovyx.net.http.ContentType.ANY,
                contentType : groovyx.net.http.ContentType.JSON,
                query : ['user.name': user, 'op': "DELETE"]
        )


        return [result: res.getData().boolean, problems:[]]
    }

    public Object createDir(String path) {
        def res = client.put(
            path : getPath(path),
            requestContentType : groovyx.net.http.ContentType.ANY,
            contentType : groovyx.net.http.ContentType.JSON,
            query : ['user.name': user, 'op': "MKDIRS"]
        )

        return [result: res.getData().boolean, problems:[]]
    }

    public Object putDir(String path, File dir, List exclude) {
        createDir(path)
        def files = []
        def subDirs = []

        dir.eachFile (FileType.FILES) { file ->
          files << file
        }

        exclude.each{n->
            files.removeAll{it.name == n}
        }
        files.each {
            putFile("${path}/${it.name}", it)
        }

        dir.eachFile (FileType.DIRECTORIES) { directory ->
          subDirs << directory
        }
        subDirs.each {
            putDir("${path}/${it.name}", it, null)
        }
        return [result: true, problems:[]]

    }

    public Object putBytes(String path, byte[] bytes) {
        def put1 = client.put(
                path : getPath(path),
                requestContentType : groovyx.net.http.ContentType.ANY,
                contentType : groovyx.net.http.ContentType.ANY,
                query : ['user.name': user, 'op': "CREATE"]
        )

        def put2 = /*REST.getHTTPClient(put1.headers.location, config)*/client.put(
                uri: put1.headers.location,
                requestContentType : groovyx.net.http.ContentType.BINARY,
                contentType : groovyx.net.http.ContentType.ANY,
                body : bytes
        )
        return [result: true, problems:[]]
    }

    public Object putFile(String path, File file) {
        return putBytes(path, file.bytes)
    }

    public Object putString(String path, String content) {
        return putBytes(path, content.getBytes("UTF-8"))
    }

    public Object readFile(String path, Integer sampleSize = -1, String charset = "UTF-8") {
        if (charset == "" || charset == null) charset = "UTF-8"
        def response = client.get(
            path : getPath(path),
            requestContentType : groovyx.net.http.ContentType.ANY,
            contentType : groovyx.net.http.ContentType.BINARY,
            query : ['user.name': user, 'op': "OPEN"]
        )
        if (sampleSize == -1 || sampleSize == null) {
            return [result: response.getData().getText(charset), problems:[]]
        } else {
            def chars = new char[sampleSize]
            response.getData().newReader(charset).read(chars, 0, sampleSize)
            return [result: String.valueOf(chars).trim(), problems:[]]
        }
    }

    private String getPath(String path) {
        if (path.take(1) == '/') {
            return path.substring(1, path.length());
        } else return path
    }

}