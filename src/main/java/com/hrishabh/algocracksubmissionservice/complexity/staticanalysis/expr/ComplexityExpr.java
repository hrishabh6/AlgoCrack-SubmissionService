package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr;

import java.util.List;
import java.util.Objects;

/**
 * Symbolic asymptotic expression (plan §14.4). Not a numeric evaluator.
 */
public sealed interface ComplexityExpr permits
        ComplexityExpr.Constant,
        ComplexityExpr.Variable,
        ComplexityExpr.Log,
        ComplexityExpr.Sum,
        ComplexityExpr.Product,
        ComplexityExpr.Power,
        ComplexityExpr.Exponential,
        ComplexityExpr.Factorial,
        ComplexityExpr.Unknown {

    record Constant(int value) implements ComplexityExpr {
        public Constant {
            if (value < 0) {
                throw new IllegalArgumentException("constant must be non-negative");
            }
        }
    }

    record Variable(String name) implements ComplexityExpr {
        public Variable {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("blank variable");
            }
        }
    }

    record Log(ComplexityExpr argument) implements ComplexityExpr {
    }

    record Sum(List<ComplexityExpr> terms) implements ComplexityExpr {
        public Sum {
            terms = List.copyOf(terms);
        }
    }

    record Product(List<ComplexityExpr> factors) implements ComplexityExpr {
        public Product {
            factors = List.copyOf(factors);
            if (factors.isEmpty()) {
                throw new IllegalArgumentException("product requires factors");
            }
        }
    }

    record Power(String variable, int exponent) implements ComplexityExpr {
        public Power {
            if (exponent < 0) {
                throw new IllegalArgumentException("negative exponent");
            }
        }
    }

    record Exponential(int base, String variable) implements ComplexityExpr {
        public Exponential {
            if (base < 2) {
                throw new IllegalArgumentException("base must be >= 2");
            }
        }
    }

    record Factorial(String variable) implements ComplexityExpr {
    }

    record Unknown(String reason) implements ComplexityExpr {
        public Unknown {
            reason = reason == null ? "unknown" : reason;
        }

        public Unknown() {
            this("unknown");
        }
    }

    static ComplexityExpr var(String name) {
        return new Variable(name);
    }

    static ComplexityExpr one() {
        return new Constant(1);
    }

    static ComplexityExpr sum(ComplexityExpr... terms) {
        return new Sum(List.of(terms));
    }

    static ComplexityExpr product(ComplexityExpr... factors) {
        return new Product(List.of(factors));
    }

    static ComplexityExpr nLogN(String variable) {
        return new Product(List.of(new Variable(variable), new Log(new Variable(variable))));
    }
}
