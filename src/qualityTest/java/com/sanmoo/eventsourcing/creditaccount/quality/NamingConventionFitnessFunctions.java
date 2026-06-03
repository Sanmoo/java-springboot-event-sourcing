package com.sanmoo.eventsourcing.creditaccount.quality;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@SuppressWarnings("UnusedVariable") // ArchUnit reads @ArchTest fields reflectively
@AnalyzeClasses(packages = "com.sanmoo.eventsourcing.creditaccount")
public class NamingConventionFitnessFunctions {

    @ArchTest
    private static final ArchRule use_case_inputs_must_be_records = classes()
            .that().resideInAPackage("..core.usecase..")
            .and().haveSimpleNameEndingWith("Input")
            .should().beRecords()
            .because("use case inputs are immutable data carriers and should be records");

    @ArchTest
    private static final ArchRule use_case_outputs_must_be_records = classes()
            .that().resideInAPackage("..core.usecase..")
            .and().haveSimpleNameEndingWith("Output")
            .should().beRecords()
            .because("use case outputs are immutable data carriers and should be records");

    @ArchTest
    private static final ArchRule domain_exceptions_must_extend_DomainException = classes()
            .that().resideInAPackage("..domain.error..")
            .and().areAssignableTo(RuntimeException.class)
            .should().beAssignableTo(
                    com.sanmoo.eventsourcing.creditaccount.domain.error.DomainException.class
            )
            .because("all domain exceptions must extend the base DomainException");

    @ArchTest
    private static final ArchRule core_exceptions_must_extend_RuntimeException = classes()
            .that().resideInAPackage("..core.error..")
            .should().beAssignableTo(RuntimeException.class)
            .because("core errors should extend RuntimeException");

    @ArchTest
    private static final ArchRule root_package_should_only_contain_application_class = classes()
            .that().resideInAPackage("com.sanmoo.eventsourcing.creditaccount")
            .should().haveSimpleName("CreditAccountApplication")
            .because("only the Spring Boot application entry point should be in the root package");
}
