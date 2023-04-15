package net.romangr.partybot

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ImportRuntimeHints


@ConfigurationPropertiesScan
@SpringBootApplication
@ImportRuntimeHints(LiquibaseRuntimeHints::class)
class VpnServiceApplication {
}

fun main(args: Array<String>) {
    runApplication<VpnServiceApplication>(*args)
}

data class ActionResult<V, S>(val value: V, val status: S)


class LiquibaseRuntimeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        hints.reflection().registerType(
            liquibase.change.core.AddUniqueConstraintChange::class.java,
            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
            MemberCategory.INVOKE_PUBLIC_METHODS
        )
        hints.reflection().registerType(
            java.util.ArrayList::class.java,
            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
            MemberCategory.INVOKE_PUBLIC_METHODS
        )
        hints.reflection().registerType(
            liquibase.datatype.core.BigIntType::class.java,
            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
            MemberCategory.INVOKE_PUBLIC_METHODS,
            MemberCategory.PUBLIC_FIELDS,
        )
    }
}