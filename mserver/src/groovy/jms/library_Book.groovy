package jms
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.JSONHelper

def message = this.binding.variables.message
def dbname = "teneo"
def db = new Database(dbname)
def type = message._type_
def entity = JSONHelper.fromJSON(dbname, type, message)
db.merge(type, entity)
