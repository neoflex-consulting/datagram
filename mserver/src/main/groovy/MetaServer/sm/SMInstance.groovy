package MetaServer.sm

import MetaServer.utils.DBConnection
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.utils.Context
import org.springframework.security.core.context.SecurityContextHolder

import ru.neoflex.meta.model.Database

class SMInstance {
    private final static Log logger = LogFactory.getLog(SMInstance.class)
    def public static goTo(Map entity, Map params = null){
        def db = Database.new
        def smInstance = db.get(params._type_, Long.valueOf(params.e_id))
        def currentState = db.get(smInstance.currentState)
        def transitionName = params.get("transition")
        
        def transition = currentState.transitions.find({it->it.name == transitionName})
        def targetState = transition.toState;

        if(currentState != null) {
            if(currentState.e_id == targetState.e_id) {
                return [result: false, "message": "Already in this state!"]
            }
        }
        
        def transitionInstance = db.instantiate("sm.SMTransitionInstance")
        transitionInstance.transition = transition
        transitionInstance.smInstance = smInstance
         
        smInstance.transitionInstances.add(transitionInstance)        
        smInstance.currentState = targetState
        db.save(transitionInstance)
        db.save(smInstance)
        
        return [result: "Finished!"]
    }
    
    def public static getAvailableTransitions(Map entity, Map params = null) {
        def smInstance = Database.new.get(params._type_, Long.valueOf(params.e_id))
        def state = Database.new.get(smInstance.currentState)
        def transitions = state
            .transitions
            .findAll({it -> it.userRoles == null || it.userRoles.size() ==  0 || Context.current.getContextSvc().hasAuthority(*it.userRoles)})
            .findAll({it -> it.guardCondition == null || Eval.me("entity", it, it.guardCondition)})
            .collect({it.name})
        return [result: "Finished!", transitions: transitions]
    }
    
    def public static actit(Map entity){
        return [result: "Finished!"]
    }

}