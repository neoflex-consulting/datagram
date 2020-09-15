package MetaServer.utils

import ru.neoflex.meta.utils.MetaResource

class GTemplate {
    def static TEMPLATES_ROOT = '/cim/MetaServer/pim/scripts/psm/groovy/templates/'
    def engine = new groovy.text.GStringTemplateEngine()

    class DelegatedWriter extends Writer {
        @Delegate Writer out
        int offset = 0
        @Override
        void write(char[] cbuf, int off, int len) throws IOException {
            for (int i = off; i < off + len; ++i) {
                if (cbuf[i] == '\n'.charAt(0)) offset = 0
                else if (cbuf[i] == '\t'.charAt(0)) offset += 4
                else offset += 1
            }
            out.write(cbuf, off, len)
        }
    }
    def getTemplateURL(String path) {
        if (path && path.length() > 0 && path.charAt(0) == '/'.charAt(0)) {
            return MetaResource.getURL(path)
        }
        return MetaResource.getURL(TEMPLATES_ROOT + path)
    }
    def run(String path, Map binding) {
        def url = getTemplateURL(path)
        def reader = new InputStreamReader(url.openStream(), "UTF-8")
        return run(reader, binding)
    }
    def run(Reader reader, Map binding) {
        def writer = new StringWriter()
        def delegatedWriter = new DelegatedWriter(out: writer)
        run(reader, binding, delegatedWriter)
        return writer.toString()
    }
    def run(Reader reader, Map binding, DelegatedWriter delegatedWriter) {
        def origText = reader.getText()
        def replText
        while(true) {
            replText = origText.replaceAll(/(?m)^\s*<%[+]\s*(.*)\s*%>\s*\n/){all, String includePath->
                GroovyShell sh = new GroovyShell(new Binding(binding))
                getTemplateURL(sh.evaluate(includePath).toString()).getText("UTF-8")
            }
            if (origText == replText) break
            origText = replText
        }
        def template = {f, Object... args->
            def old = delegatedWriter.out
            def offset = delegatedWriter.offset
            try {
                delegatedWriter.out = new StringWriter()
                delegatedWriter.offset = 0
                f.call(*args)
                def lines = delegatedWriter.out.toString().readLines()
                def result = lines.join('\n' + ' '*offset)
                return result
            }
            finally {
                delegatedWriter.out = old
                delegatedWriter.offset = offset
            }
        }
        engine.createTemplate(replText).make([template: template] + binding).writeTo(delegatedWriter)
    }

    static Object run(Map entity, Map params) {
        def path = params.remove("path")
        def template = new GTemplate()
        return template.run(path, params)
    }
    static Object test(Map entity, Map params) {
        def template = new GTemplate()
        def s = """
<%
def printUser = {user->
%>{
    "user":
    {
        "name": "<%=user%>"
    }
}<%}
def listUsers = {list->
%>{
    "reviewers": 
    [   
        <%list.eachWithIndex {it, i ->%><%=template.call(printUser, it)%><%if (i < list.size() - 1) {%>,
        <%}%><%}%>
    ]
}<%}%>
<%=template.call(listUsers, users + ["d"])%>
<%=template.call(listUsers, users + ["e"])%>
"""
        return template.run(new StringReader(s), [users: ["a", "b", "c"]])
    }
}
