package init

import MetaServer.etl.Project
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.svc.BaseSvc

def initDir = new File(BaseSvc.getDeployDir(), "cim/MetaServer/data/init")
Project.initRepo(initDir, true)
