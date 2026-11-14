package moe.n4tsu.dextop.xr

/** In-memory lookup of live [XrPoseProvider]s. Production registers nothing until later tasks. */
internal object XrPoseProviders {
    private val lock = Any()
    private val providers = LinkedHashMap<String, XrPoseProvider>()

    fun register(provider: XrPoseProvider) {
        synchronized(lock) { providers[provider.id] = provider }
    }

    fun unregister(id: String) {
        synchronized(lock) { providers.remove(id) }
    }

    fun byId(id: String): XrPoseProvider? = synchronized(lock) { providers[id] }

    fun all(): List<XrPoseProvider> = synchronized(lock) { providers.values.toList() }

    fun clearForTests() {
        synchronized(lock) { providers.clear() }
    }
}
