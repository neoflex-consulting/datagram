package MetaServer.utils.extensions

interface ExtensionFactory {
    abstract String className()
    abstract Extension createExtension()
}