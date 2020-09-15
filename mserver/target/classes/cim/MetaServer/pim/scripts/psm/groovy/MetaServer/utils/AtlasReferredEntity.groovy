package MetaServer.utils

import ru.neoflex.meta.model.Database

class AtlasReferredEntity {
    AtlasEntity.TypeName typeName
    Map uniqueAttributes
    
    AtlasReferredEntity(typeName, uniqueAttributes) {
        this.typeName = typeName
        this.uniqueAttributes = uniqueAttributes
    }
    
    def static AtlasReferredEntity createReferredEntity(AtlasEntity.TypeName typeName, String qualifiedName) {
        new AtlasReferredEntity(typeName, ["qualifiedName" : qualifiedName])
    }
}

