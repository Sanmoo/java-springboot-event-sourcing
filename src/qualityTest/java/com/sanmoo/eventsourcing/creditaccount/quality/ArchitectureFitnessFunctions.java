package com.sanmoo.eventsourcing.creditaccount.quality;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@SuppressWarnings("UnusedVariable") // ArchUnit reads @ArchTest fields reflectively
@AnalyzeClasses(packages = "com.sanmoo.eventsourcing.creditaccount")
public class ArchitectureFitnessFunctions {

    @ArchTest
    private static final ArchRule domain_must_not_depend_on_external_frameworks = classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "com.sanmoo.eventsourcing.creditaccount.domain..");

    @ArchTest
    private static final ArchRule application_must_not_depend_on_adapters = classes()
            .that().resideInAPackage("..application..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..",
                    "com.sanmoo.eventsourcing.creditaccount.domain..",
                    "com.sanmoo.eventsourcing.creditaccount.application..",
                    "tools.jackson.."  // required for idempotency serialization; consider extracting serialization port
            )
            .because("application must not depend on inbound or outbound adapters");

    @ArchTest
    private static final ArchRule inbound_adapters_must_not_depend_on_outbound = classes()
            .that().resideInAPackage("..adapter.in..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..",
                    "com.sanmoo.eventsourcing.creditaccount.adapter.in..",
                    "com.sanmoo.eventsourcing.creditaccount.application..",
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
                    "com.sanmoo.eventsourcing.creditaccount.application..",
                    "com.sanmoo.eventsourcing.creditaccount.domain..",
                    "org.springframework..",
                    "tools.jackson.."
            );

    @ArchTest
    private static final ArchRule ports_must_be_interfaces_or_records = classes()
            .that().resideInAPackage("..application.port..")
            .and().areNotRecords()
            .should().beInterfaces()
            .because("application ports should be interfaces; data records are allowed as records");

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
}
