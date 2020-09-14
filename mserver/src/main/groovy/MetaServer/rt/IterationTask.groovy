package MetaServer.rt

import RunTask 

/* protected region MetaServer.rtParametrizedTask.inport end */

class IterationTask {    
    def public static generate(Map entity, Map params = null){        
        return RunTask.generate(entity, params)        
    }
    
    def public static deployAllTransformations(Map entity, Map params = null){
        return RunTask.deployAllTransformations(entity, params)
    }
    
    def public static deployDagToAirflow(Map entity, Map params = null) {
        return RunTask.deployDagToAirflow(entity, params)
    }

}