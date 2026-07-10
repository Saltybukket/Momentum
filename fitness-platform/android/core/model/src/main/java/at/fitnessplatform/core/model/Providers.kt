package at.fitnessplatform.core.model

interface Clock { fun nowEpochMs(): Long }
interface UuidProvider { fun newUuid(): String }

class SystemClock : Clock { override fun nowEpochMs(): Long = System.currentTimeMillis() }
class JavaUuidProvider : UuidProvider { override fun newUuid(): String = java.util.UUID.randomUUID().toString() }
