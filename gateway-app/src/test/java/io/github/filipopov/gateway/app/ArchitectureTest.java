package io.github.filipopov.gateway.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.springframework.web.bind.annotation.RestController;

/**
 * Executable architecture: the hexagon's dependency rules, checked on every build.
 *
 * <pre>
 *   api ──► application ──► domain
 *             ▲
 *   adapters ─┘          app wires everything
 * </pre>
 */
@AnalyzeClasses(packages = "io.github.filipopov.gateway", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "io.github.filipopov.gateway.domain..";
    private static final String APPLICATION = "io.github.filipopov.gateway.application..";
    private static final String ADAPTERS = "io.github.filipopov.gateway.adapters..";
    private static final String API = "io.github.filipopov.gateway.api..";
    private static final String APP = "io.github.filipopov.gateway.app..";

    @ArchTest
    static final ArchRule layers_only_depend_inwards = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy(DOMAIN)
            .layer("Application").definedBy(APPLICATION)
            .layer("Adapters").definedBy(ADAPTERS)
            .layer("Api").definedBy(API)
            .layer("App").definedBy(APP)
            .whereLayer("App").mayNotBeAccessedByAnyLayer()
            .whereLayer("Api").mayOnlyBeAccessedByLayers("App")
            .whereLayer("Adapters").mayOnlyBeAccessedByLayers("App")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Api", "Adapters", "App")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Api", "Adapters", "App");

    @ArchTest
    static final ArchRule domain_is_plain_java = classes().that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", DOMAIN)
            .because("the domain must not know about Spring, Jackson, HTTP or any adapter");

    @ArchTest
    static final ArchRule application_is_framework_free = classes().that().resideInAPackage(APPLICATION)
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", DOMAIN, APPLICATION)
            .because("use cases are wired by the app module, so they need no framework annotations");

    @ArchTest
    static final ArchRule api_talks_to_use_cases_only = noClasses().that().resideInAPackage(API)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.github.filipopov.gateway.application.port.out..",
                    "io.github.filipopov.gateway.application.service..")
            .because("controllers call inbound ports; implementations and outbound ports are not their business");

    @ArchTest
    static final ArchRule v1_does_not_depend_on_v2 = noClasses().that().resideInAPackage("..api.v1..")
            .should().dependOnClassesThat().resideInAPackage("..api.v2..")
            .because("v1 must stay frozen while v2 evolves");

    @ArchTest
    static final ArchRule v2_does_not_depend_on_v1 = noClasses().that().resideInAPackage("..api.v2..")
            .should().dependOnClassesThat().resideInAPackage("..api.v1..")
            .because("sharing DTOs would let a v2 change silently break v1 clients");

    @ArchTest
    static final ArchRule providers_do_not_know_each_other = noClasses().that().resideInAPackage("..adapters.ledgerline..")
            .should().dependOnClassesThat().resideInAPackage("..adapters.coreflow..");

    @ArchTest
    static final ArchRule providers_do_not_know_each_other_reverse = noClasses().that().resideInAPackage("..adapters.coreflow..")
            .should().dependOnClassesThat().resideInAPackage("..adapters.ledgerline..");

    @ArchTest
    static final ArchRule controllers_live_in_api = classes().that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage(API);

    @ArchTest
    static final ArchRule no_field_injection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule no_standard_streams = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule no_java_util_logging = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
