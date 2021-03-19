package init

import MetaServer.etl.Project
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.svc.BaseSvc

def db = new Database("teneo")
def userInfoEntity = "auth.UserInfo"
def userInfos = db.list(userInfoEntity)
if (userInfos.size() == 0) {
    def userInfo = db.instantiate(userInfoEntity)
    userInfo.putAll([name: "admin", login: "admin", password: "admin"])
    db.save(userInfoEntity, userInfo)
}