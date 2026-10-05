package org.eclipse.cargotracker.domain.shared;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpecificationTest {

    // Concrete implementation for testing
    static class AlwaysTrueSpec extends AbstractSpecification<String> {
        @Override
        public boolean isSatisfiedBy(String s) {
            return true;
        }
    }

    static class AlwaysFalseSpec extends AbstractSpecification<String> {
        @Override
        public boolean isSatisfiedBy(String s) {
            return false;
        }
    }

    static class ContainsHelloSpec extends AbstractSpecification<String> {
        @Override
        public boolean isSatisfiedBy(String s) {
            return s != null && s.contains("hello");
        }
    }

    @Test
    void andSpecification_bothTrue_returnsTrue() {
        Specification<String> spec = new AlwaysTrueSpec().and(new AlwaysTrueSpec());
        assertTrue(spec.isSatisfiedBy("test"));
    }

    @Test
    void andSpecification_firstFalse_returnsFalse() {
        Specification<String> spec = new AlwaysFalseSpec().and(new AlwaysTrueSpec());
        assertFalse(spec.isSatisfiedBy("test"));
    }

    @Test
    void andSpecification_secondFalse_returnsFalse() {
        Specification<String> spec = new AlwaysTrueSpec().and(new AlwaysFalseSpec());
        assertFalse(spec.isSatisfiedBy("test"));
    }

    @Test
    void andSpecification_bothFalse_returnsFalse() {
        Specification<String> spec = new AlwaysFalseSpec().and(new AlwaysFalseSpec());
        assertFalse(spec.isSatisfiedBy("test"));
    }

    @Test
    void orSpecification_bothTrue_returnsTrue() {
        Specification<String> spec = new AlwaysTrueSpec().or(new AlwaysTrueSpec());
        assertTrue(spec.isSatisfiedBy("test"));
    }

    @Test
    void orSpecification_firstTrue_returnsTrue() {
        Specification<String> spec = new AlwaysTrueSpec().or(new AlwaysFalseSpec());
        assertTrue(spec.isSatisfiedBy("test"));
    }

    @Test
    void orSpecification_secondTrue_returnsTrue() {
        Specification<String> spec = new AlwaysFalseSpec().or(new AlwaysTrueSpec());
        assertTrue(spec.isSatisfiedBy("test"));
    }

    @Test
    void orSpecification_bothFalse_returnsFalse() {
        Specification<String> spec = new AlwaysFalseSpec().or(new AlwaysFalseSpec());
        assertFalse(spec.isSatisfiedBy("test"));
    }

    @Test
    void notSpecification_trueSpec_returnsFalse() {
        Specification<String> spec = new AlwaysTrueSpec().not(new AlwaysTrueSpec());
        assertFalse(spec.isSatisfiedBy("test"));
    }

    @Test
    void notSpecification_falseSpec_returnsTrue() {
        Specification<String> spec = new AlwaysTrueSpec().not(new AlwaysFalseSpec());
        assertTrue(spec.isSatisfiedBy("test"));
    }

    @Test
    void andSpecification_constructor_createsSpec() {
        AndSpecification<String> spec = new AndSpecification<>(
                new AlwaysTrueSpec(), new AlwaysTrueSpec());
        assertNotNull(spec);
        assertTrue(spec.isSatisfiedBy("test"));
    }

    @Test
    void orSpecification_constructor_createsSpec() {
        OrSpecification<String> spec = new OrSpecification<>(
                new AlwaysTrueSpec(), new AlwaysFalseSpec());
        assertNotNull(spec);
        assertTrue(spec.isSatisfiedBy("test"));
    }

    @Test
    void notSpecification_constructor_createsSpec() {
        NotSpecification<String> spec = new NotSpecification<>(new AlwaysTrueSpec());
        assertNotNull(spec);
        assertFalse(spec.isSatisfiedBy("test"));
    }

    @Test
    void containsHelloSpec_withHello_returnsTrue() {
        ContainsHelloSpec spec = new ContainsHelloSpec();
        assertTrue(spec.isSatisfiedBy("hello world"));
    }

    @Test
    void containsHelloSpec_withoutHello_returnsFalse() {
        ContainsHelloSpec spec = new ContainsHelloSpec();
        assertFalse(spec.isSatisfiedBy("world"));
    }

    @Test
    void containsHelloSpec_withNull_returnsFalse() {
        ContainsHelloSpec spec = new ContainsHelloSpec();
        assertFalse(spec.isSatisfiedBy(null));
    }

    @Test
    void compositeSpec_andThenOr_worksCorrectly() {
        ContainsHelloSpec helloSpec = new ContainsHelloSpec();
        AlwaysTrueSpec trueSpec = new AlwaysTrueSpec();
        AlwaysFalseSpec falseSpec = new AlwaysFalseSpec();

        // (hello AND false) OR true = true
        Specification<String> spec = helloSpec.and(falseSpec).or(trueSpec);
        assertTrue(spec.isSatisfiedBy("hello world"));
    }
}
