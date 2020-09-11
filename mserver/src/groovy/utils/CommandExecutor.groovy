package utils
import ru.neoflex.meta.model.Database

class CommandExecutor {
    def vars = new HashMap();
    def db = new Database("meta");

    static execute(commands) {
        (new CommandExecutor()).run(commands)
    }

    def dereference(value) {
        if (value instanceof Map) {
            return [:].putAll((value as Map).collect {k, v -> new MapEntry(k, dereference(v))})
        }
        else if (value instanceof List) {
            return value.collect {dereference(it)}
        }
        else if (value instanceof String) {
            if (value.startsWith("ref:")) {
                return vars[value.substring(4)]
            }
        }
        return value
    }

    def flush(it) {
        db.session.flush();
    }

    def delete(it) {
        db.deleteWhere(it.entity, dereference(it.props))
    }

    def saveOrUpdate(it) {
        def entity = dereference(it.props)
        db.saveOrUpdate(it.entity, entity)
        if (it.ref != null) {
            vars[it.ref] = entity
        }
    }

    def run(commands) {
        commands.each {
            if (it.op != null) {
                this."${it.op}"(it)
            }
            else {
                saveOrUpdate(it)
            }
        }
    }
}

