<%+ "/cim/MetaServer/pim/scripts/psm/groovy/templates/test/lib.gtpl" %>
Application name: <%=getApplicationContext().applicationName%>
Display name    : <%=getApplicationContext().displayName%>
Startup Date    : <%=new java.sql.Timestamp(getApplicationContext().getStartupDate())%>
User Dir        : <%=getProperty("user.dir")%>
{
    <%;%><%=template.call(printList, "rt.Oozie")%>
    <%;%><%=template.call(printList, "rt.LivyServer")%>
}