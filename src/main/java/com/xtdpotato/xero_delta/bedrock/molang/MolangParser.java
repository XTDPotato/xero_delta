package com.xtdpotato.xero_delta.bedrock.molang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MolangParser {
    public MolangExpression parse(String source) {
        if (source == null || source.isBlank()) return MolangExpression.ZERO;
        Parser parser = new Parser(source.toLowerCase(Locale.ROOT));
        Node node = parser.expression();
        parser.requireEnd();
        return node::evaluate;
    }

    private interface Node {
        double evaluate(MolangContext context);
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) { this.source = source; }

        private Node expression() {
            Node value = term();
            while (true) {
                skipSpace();
                if (take('+')) {
                    Node left = value, right = term();
                    value = context -> left.evaluate(context) + right.evaluate(context);
                } else if (take('-')) {
                    Node left = value, right = term();
                    value = context -> left.evaluate(context) - right.evaluate(context);
                } else return value;
            }
        }

        private Node term() {
            Node value = unary();
            while (true) {
                skipSpace();
                if (take('*')) {
                    Node left = value, right = unary();
                    value = context -> left.evaluate(context) * right.evaluate(context);
                } else if (take('/')) {
                    Node left = value, right = unary();
                    value = context -> {
                        double denominator = right.evaluate(context);
                        return denominator == 0 ? 0 : left.evaluate(context) / denominator;
                    };
                } else return value;
            }
        }

        private Node unary() {
            skipSpace();
            if (take('-')) {
                Node node = unary();
                return context -> -node.evaluate(context);
            }
            return primary();
        }

        private Node primary() {
            skipSpace();
            if (take('(')) {
                Node node = expression();
                require(')');
                return node;
            }
            if (index < source.length() && (Character.isDigit(source.charAt(index)) || source.charAt(index) == '.')) {
                int start = index++;
                while (index < source.length() && (Character.isDigit(source.charAt(index))
                    || source.charAt(index) == '.')) index++;
                double number = Double.parseDouble(source.substring(start, index));
                return context -> number;
            }
            String name = identifier();
            skipSpace();
            if (!take('(')) return context -> context.query(name);
            List<Node> arguments = new ArrayList<>();
            skipSpace();
            if (!take(')')) {
                do arguments.add(expression()); while (take(','));
                require(')');
            }
            return function(name, arguments);
        }

        private Node function(String name, List<Node> arguments) {
            return switch (name) {
                case "math.sin" -> unaryFunction(arguments, value -> Math.sin(Math.toRadians(value)));
                case "math.cos" -> unaryFunction(arguments, value -> Math.cos(Math.toRadians(value)));
                case "math.clamp" -> {
                    requireArguments(name, arguments, 3);
                    yield context -> Math.max(arguments.get(1).evaluate(context), Math.min(
                        arguments.get(2).evaluate(context), arguments.get(0).evaluate(context)));
                }
                default -> context -> 0;
            };
        }

        private Node unaryFunction(List<Node> arguments, java.util.function.DoubleUnaryOperator function) {
            requireArguments("function", arguments, 1);
            return context -> function.applyAsDouble(arguments.getFirst().evaluate(context));
        }

        private void requireArguments(String name, List<Node> arguments, int count) {
            if (arguments.size() != count) throw error(name + " expects " + count + " arguments");
        }

        private String identifier() {
            skipSpace();
            int start = index;
            while (index < source.length()) {
                char value = source.charAt(index);
                if (!Character.isLetterOrDigit(value) && value != '_' && value != '.') break;
                index++;
            }
            if (start == index) throw error("Expected expression");
            return source.substring(start, index);
        }

        private boolean take(char expected) {
            skipSpace();
            if (index >= source.length() || source.charAt(index) != expected) return false;
            index++;
            return true;
        }

        private void require(char expected) {
            if (!take(expected)) throw error("Expected '" + expected + "'");
        }

        private void requireEnd() {
            skipSpace();
            if (index != source.length()) throw error("Unexpected input");
        }

        private void skipSpace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at " + index + " in " + source);
        }
    }
}
