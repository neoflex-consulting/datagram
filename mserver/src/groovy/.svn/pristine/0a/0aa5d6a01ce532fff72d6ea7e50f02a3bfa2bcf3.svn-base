<%
def getDatabase = {new ru.neoflex.meta.model.Database("teneo")}
def getCurrent = {ru.neoflex.meta.utils.Context.current}
def getEntity = {type, id -> getDatabase().get(type, id)}
def getContextSvc = {getCurrent().contextSvc}
def getApplicationContext = {getContextSvc().applicationContext}
def getResource = {path -> getApplicationContext().getResource(path)}
def getEnvironment = {getContextSvc().environment}
def getProperty = {name -> getEnvironment().getProperty(name)}
def getEntityByName = {type, name -> getCurrent().session.createQuery("from ${type} where name = :name").setParameter("name", name).uniqueResult()}
def getList = {type -> getCurrent().session.createQuery("from ${type}").list()}

def printEntity = {entity->
def eClass = ru.neoflex.meta.utils.ECoreUtils.findEClass(entity._type_)
def list = eClass.getEAllAttributes()
%>{
    "_type_": "<%=entity._type_%>",
    "e_id": <%=entity.e_id%>,
    <%list.eachWithIndex {it, i ->%>"<%=it.name%>": <%=entity."${it.name}" instanceof CharSequence ? '"' + entity."${it.name}" + '"': entity."${it.name}"%><%if (i < list.size() - 1) {%>,
    <%}%><%}%>
}<%}

def printList = {type->
def list = getList(type)
%>[
    <%list.eachWithIndex {it, i ->%><%=template.call(printEntity, it)%><%if (i < list.size() - 1) {%>,
    <%}%><%}%>
]
<%}

%>