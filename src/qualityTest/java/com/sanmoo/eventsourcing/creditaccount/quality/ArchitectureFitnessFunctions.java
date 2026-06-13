package com.sanmoo.eventsourcing.creditaccount.quality;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@SuppressWarnings("UnusedVariable") // ArchUnit reads @ArchTest fields reflectively
@AnalyzeClasses(packages = "com.sanmoo.eventsourcing.creditaccount")
public class ArchitectureFitnessFunctions {

    @ArchTest
    private static final ArchRule domain_must_not_depend_on_external_frameworks = classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "com.sanmoo.eventsourcing.creditaccount.domain..");

    @ArchTest
    private static final ArchRule core_must_not_depend_on_adapters = classes()
            .that().resideInAPackage("..core..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..",
                    "com.sanmoo.eventsourcing.creditaccount.domain..",
                    "com.sanmoo.eventsourcing.creditaccount.core..",
                    "org.springframework.stereotype..",
                    "tools.jackson..",  // required for idempotency serialization; consider extracting serialization port
                    "org.springframework.stereotype..",  // use cases are Spring-discovered application services
                    "org.springframework.transaction.annotation.."  // @Transactional for idempotent execution
            )
            .because("core must not depend on inbound or outbound adapters");

    @ArchTest
    private static final ArchRule inbound_adapters_must_not_depend_on_outbound = classes()
            .that().resideInAPackage("..adapter.in..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..",
                    "com.sanmoo.eventsourcing.creditaccount.adapter.in..",
                    "com.sanmoo.eventsourcing.creditaccount.core..",
                    "com.sanmoo.eventsourcing.creditaccount.domain..",
                    "org.springframework..",
                    "tools.jackson..",
                    "com.fasterxml.jackson.annotation..",
                    "jakarta.."
            );

    @ArchTest
    private static final ArchRule outbound_adapters_must_not_depend_on_inbound = classes()
            .that().resideInAPackage("..adapter.out..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..",
                    "com.sanmoo.eventsourcing.creditaccount.adapter.out..",
                    "com.sanmoo.eventsourcing.creditaccount.core..",
                    "com.sanmoo.eventsourcing.creditaccount.domain..",
                    "org.springframework..",
                    "tools.jackson..",
                    "com.github.f4b6a3.uuid.."
            );

    @ArchTest
    private static final ArchRule ports_must_be_interfaces_or_records = classes()
            .that().resideInAPackage("..core.port..")
            .and().areNotRecords()
            .should().beInterfaces()
            .because("core ports should be interfaces; data records are allowed as records");

    @ArchTest
    private static final ArchRule domain_events_must_be_records = classes()
            .that().resideInAPackage("..domain.event..")
            .and().areNotInterfaces()
            .should().beRecords()
            .because("domain events are immutable facts and should be records");

    @ArchTest
    private static final ArchRule domain_model_classes_must_be_records = classes()
            .that().resideInAPackage("..domain.model..")
            .and().areNotEnums()
            .should().beRecords()
            .because("domain value objects are immutable and should be records");

    @ArchTest
    private static final ArchRule core_must_not_depend_on_spring_infrastructure_outside_allow_list = noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework.transaction.support..",
                    "org.springframework.transaction",
                    "org.springframework.jdbc..",
                    "org.springframework.web..",
                    "org.springframework.boot.."
            )
            .because("core must depend on framework integrations only through ports; the only Spring packages allowed in core are org.springframework.stereotype.. and org.springframework.transaction.annotation..");
}
