<%+ "/cim/MetaServer/pim/scripts/psm/groovy/templates/test/lib.gtpl" %>
<%
def entity = getEntityByName(type, name)
%>
<%=entity.eClass().name%>
<%=template.call(printEntity, entity)%>