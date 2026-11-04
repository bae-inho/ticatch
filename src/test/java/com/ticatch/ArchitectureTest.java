package com.ticatch;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;

/** 개발 규칙(docs/conventions.md 2절)의 계층·모듈 의존 규칙을 강제한다. */
@AnalyzeClasses(packages = "com.ticatch", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT_PACKAGE = "com.ticatch.";

    @ArchTest
    static final ArchRule 계층은_controller_service_repository_domain_방향으로만_의존한다 = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Controller").definedBy("..controller..")
            .layer("Service").definedBy("..service..")
            .layer("Repository").definedBy("..repository..")
            .layer("Domain").definedBy("..domain..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Service")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Controller", "Service", "Repository");

    @ArchTest
    static final ArchRule 엔티티와_리포지토리는_자기_모듈_안에서만_쓴다 = classes()
            .that().areAnnotatedWith(Entity.class)
            .or().resideInAPackage("..repository..")
            .should(beUsedOnlyInsideTheirModule());

    @ArchTest
    static final ArchRule 모듈_사이에_순환_의존이_없다 = slices()
            .matching("com.ticatch.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule global은_업무_모듈에_의존하지_않는다 = noClasses()
            .that().resideInAPackage("com.ticatch.global..")
            .should().dependOnClassesThat(
                    resideInAPackage("com.ticatch..").and(not(resideInAPackage("com.ticatch.global.."))));

    private static ArchCondition<JavaClass> beUsedOnlyInsideTheirModule() {
        return new ArchCondition<>("be used only inside their own module") {
            @Override
            public void check(JavaClass target, ConditionEvents events) {
                for (Dependency dependency : target.getDirectDependenciesToSelf()) {
                    if (!moduleOf(dependency.getOriginClass()).equals(moduleOf(target))) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    /** {@code com.ticatch.user.service.UserService} → {@code user}. */
    private static String moduleOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(ROOT_PACKAGE)) {
            return "";
        }
        String rest = packageName.substring(ROOT_PACKAGE.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
