package com.sanmoo.eventsourcing.creditaccount.quality;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

@SuppressWarnings("UnusedVariable") // ArchUnit reads @ArchTest fields reflectively
@AnalyzeClasses(packages = "com.sanmoo.eventsourcing.creditaccount")
public class DesignRulesFitnessFunctions {

    @ArchTest
    private static final ArchRule no_field_injection = noFields()
            .should().beAnnotatedWith(
                    org.springframework.beans.factory.annotation.Autowired.class
            )
            .because("constructor injection is preferred over @Autowired field injection");

    @ArchTest
    private static final ArchRule controllers_must_not_access_ports = classes()
            .that().resideInAPackage("..adapter.in.rest..")
            .and().haveSimpleNameNotContaining("RestConfiguration")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..",
                    "com.sanmoo.eventsourcing.creditaccount.adapter.in.rest..",
                    "com.sanmoo.eventsourcing.creditaccount.application.command..",
                    "com.sanmoo.eventsourcing.creditaccount.application.result..",
                    "com.sanmoo.eventsourcing.creditaccount.application.service..",
                    "com.sanmoo.eventsourcing.creditaccount.application.error..",
                    "com.sanmoo.eventsourcing.creditaccount.domain.model..",
                    "com.sanmoo.eventsourcing.creditaccount.domain.event..",
                    "com.sanmoo.eventsourcing.creditaccount.domain.error..",
                    "org.springframework..",
                    "tools.jackson..",
                    "com.fasterxml.jackson.annotation..",
                    "jakarta.."
            )
            .because("controllers should depend on the application layer, not directly on ports");

    @ArchTest
    private static final ArchRule controllers_must_not_depend_on_ports = noClasses()
            .that().resideInAPackage("..adapter.in.rest..")
            .and().haveSimpleNameNotContaining("RestConfiguration")
            .should().dependOnClassesThat()
            .resideInAPackage("..application.port..")
            .because("controllers must never directly reference application ports");

    @ArchTest
    private static final ArchRule no_public_fields_in_non_record_classes = fields()
            .that().areDeclaredInClassesThat().areNotRecords()
            .and().areDeclaredInClassesThat().areNotEnums()
            .should().bePrivate()
            .because("encapsulation: non-record classes should not expose fields directly");
}
