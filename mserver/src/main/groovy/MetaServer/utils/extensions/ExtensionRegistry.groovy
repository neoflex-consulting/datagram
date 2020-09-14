package MetaServer.utils.extensions


class ExtensionRegistry {
    public static ExtensionRegistry instance = new ExtensionRegistry()

    Map<String, ExtensionFactory> registry = [:]

    void register(ExtensionFactory factory) {
        registry[factory.className()] = factory
    }

    Extension get(Map entity) {
        def factory = registry.get(entity._type_)
        if (factory == null) {
            return null
        }
        def ext =  factory.createExtension()
        ext.entity = entity
        return ext
    }

    ExtensionRegistry() {
        register(new ExtensionFactory() {
            @Override
            String className() {
                return "etl.Transformation"
            }

            @Override
            Extension createExtension() {
                return new EtlTransformationExt()
            }
        })
    }

}


