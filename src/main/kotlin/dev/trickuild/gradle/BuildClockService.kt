package dev.trickuild.gradle

import java.time.Instant
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/** A fresh service instance for each invocation, also when configuration cache is reused. */
abstract class BuildClockService : BuildService<BuildServiceParameters.None> {
    val instant: Instant by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Instant.now() }
}
