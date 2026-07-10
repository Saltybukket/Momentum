package at.fitnessplatform.data

import at.fitnessplatform.core.model.DomainEvent
import at.fitnessplatform.domain.DomainEventDispatcher
import at.fitnessplatform.domain.DomainEventHandler
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalEventDispatcher @Inject constructor() : DomainEventDispatcher {
    private val handlers = ConcurrentHashMap<Class<*>, MutableList<DomainEventHandler<*>>>()
    private val processedEventIds = ConcurrentHashMap.newKeySet<String>()

    override fun <T : DomainEvent> register(type: Class<T>, handler: DomainEventHandler<T>) {
        handlers.computeIfAbsent(type) { mutableListOf() }.add(handler)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun publish(event: DomainEvent) {
        if (!processedEventIds.add(event.eventId)) return
        handlers[event.javaClass].orEmpty().forEach { handler ->
            (handler as DomainEventHandler<DomainEvent>).handle(event)
        }
    }
}
