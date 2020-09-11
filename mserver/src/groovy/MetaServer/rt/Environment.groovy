package MetaServer.rt;
import ru.neoflex.meta.utils.Context;
/* protected region MetaServer.rtEnvironment.inport on begin */
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.context.expression.MapAccessor
import org.springframework.expression.ExpressionParser
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.SymmetricCipher;
/* protected region MetaServer.rtEnvironment.inport end */
class Environment {
    /* protected region MetaServer.rtEnvironment.statics on begin */
    private final static Log logger = LogFactory.getLog(Environment.class);

    private static Map OBJ(String klass, String name) {
        return Database.new.list(klass, [name: name]).first()
    }

    private static String DECRYPT(String encryptedString) {
        return SymmetricCipher.decrypt(encryptedString)
    }

    private static void rewriteEnvironment(Map environment) {
        for (parameter in environment.parameters) {
            if (parameter.attributePath != null) {
                logger.info(parameter.name)
                def rootList = Database.new.list(parameter.objectClass, [name: parameter.objectName])
                if (rootList.size() == 0) {
                    logger.warn("unknown object [${parameter.objectClass}/${parameter.objectName}]")
                    continue
                }
                if (rootList.size() > 1) {
                    logger.warn("non unique object [${parameter.objectClass}/${parameter.objectName}]")
                }
                def root = rootList.get(0)
                StandardEvaluationContext context = new StandardEvaluationContext(root)
                context.addPropertyAccessor(new MapAccessor())
                context.setVariable("context", Context.current)
                context.setVariable("env", Context.current.contextSvc.applicationContext.environment)
                context.registerFunction("OBJ", Environment.class.getDeclaredMethod("OBJ", [String.class, String.class] as Class[]))
                context.registerFunction("DECRYPT", Environment.class.getDeclaredMethod("DECRYPT", [String.class] as Class[]))
                ExpressionParser parser = new SpelExpressionParser()
                def parameterValue = parser.parseExpression(parameter.parameterValue).getValue(context)
                parser.parseExpression(parameter.attributePath).setValue(context, parameterValue)
                Database.new.save(root)
            }
        }
    }
    /* protected region MetaServer.rtEnvironment.statics end */

    public static Object rewriteParameters(Map entity, Map params = null) {
    /* protected region MetaServer.rtEnvironment.rewriteParameters on begin */
        def environment = Database.new.get(entity)
        rewriteEnvironment(environment)
        return [environment.name]
    /* protected region MetaServer.rtEnvironment.rewriteParameters end */
    }

    public static Object rewriteCurrent(Map entity, Map params = null) {
    /* protected region MetaServer.rtEnvironment.rewriteCurrent on begin */
        def custCode = Context.current.contextSvc.applicationContext.environment.getProperty("cust.code")
        logger.info(custCode)
        def result = []
        for (env in Database.new.list("rt.Environment", [name: custCode])) {
            result.add(env.name)
            rewriteEnvironment(env)
        }
        return result
    /* protected region MetaServer.rtEnvironment.rewriteCurrent end */
    }

    public static Object encryptString(Map entity, Map params = null) {
        /* protected region MetaServer.rtEnvironment.encryptString on begin */
        return [SymmetricCipher.encrypt(params.plainString)]
        /* protected region MetaServer.rtEnvironment.encryptString end */
    }

    public static Object decryptString(Map entity, Map params = null) {
        /* protected region MetaServer.rtEnvironment.decryptString on begin */
        return [SymmetricCipher.decrypt(params.encryptedString)]
        /* protected region MetaServer.rtEnvironment.decryptString end */
    }

}
