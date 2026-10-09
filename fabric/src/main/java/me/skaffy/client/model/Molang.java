package me.skaffy.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

public final class Molang {
	public interface Scope {
		float animTime();

		float lifeTime();

		float groundSpeed();

		float headXRotation();

		float headYRotation();

		float distanceMoved();

		default float variable(String name) {
			return 0;
		}
	}

	public interface Expression {
		float evaluate(Scope scope);
	}

	public static final Expression ZERO = scope -> 0;

	private Molang() {
	}

	public static Expression constant(float value) {
		return scope -> value;
	}

	public static Expression parse(String source) {
		String text = source.trim().toLowerCase(Locale.ROOT);

		if (text.startsWith("return ")) {
			text = text.substring(7);
		}

		while (text.endsWith(";")) {
			text = text.substring(0, text.length() - 1).trim();
		}

		if (text.isEmpty()) {
			return ZERO;
		}

		Parser parser = new Parser(text);
		Expression expression = parser.ternary();
		parser.skipSpaces();

		if (parser.position < text.length()) {
			throw new IllegalArgumentException("Unexpected '" + text.charAt(parser.position) + "' in " + source);
		}

		return expression;
	}

	private static final class Parser {
		private final String text;
		private int position;

		Parser(String text) {
			this.text = text;
		}

		Expression ternary() {
			Expression condition = or();

			if (eat("??")) {
				ternary();
				return condition;
			}

			if (eat("?")) {
				Expression yes = ternary();
				Expression no = eat(":") ? ternary() : ZERO;
				return scope -> condition.evaluate(scope) != 0 ? yes.evaluate(scope) : no.evaluate(scope);
			}

			return condition;
		}

		Expression or() {
			Expression left = and();

			while (eat("||")) {
				Expression l = left;
				Expression right = and();
				left = scope -> l.evaluate(scope) != 0 || right.evaluate(scope) != 0 ? 1 : 0;
			}

			return left;
		}

		Expression and() {
			Expression left = equality();

			while (eat("&&")) {
				Expression l = left;
				Expression right = equality();
				left = scope -> l.evaluate(scope) != 0 && right.evaluate(scope) != 0 ? 1 : 0;
			}

			return left;
		}

		Expression equality() {
			Expression left = comparison();

			while (true) {
				Expression l = left;

				if (eat("==")) {
					Expression right = comparison();
					left = scope -> l.evaluate(scope) == right.evaluate(scope) ? 1 : 0;
				} else if (eat("!=")) {
					Expression right = comparison();
					left = scope -> l.evaluate(scope) != right.evaluate(scope) ? 1 : 0;
				} else {
					return left;
				}
			}
		}

		Expression comparison() {
			Expression left = additive();

			while (true) {
				Expression l = left;

				if (eat("<=")) {
					Expression right = additive();
					left = scope -> l.evaluate(scope) <= right.evaluate(scope) ? 1 : 0;
				} else if (eat(">=")) {
					Expression right = additive();
					left = scope -> l.evaluate(scope) >= right.evaluate(scope) ? 1 : 0;
				} else if (eat("<")) {
					Expression right = additive();
					left = scope -> l.evaluate(scope) < right.evaluate(scope) ? 1 : 0;
				} else if (eat(">")) {
					Expression right = additive();
					left = scope -> l.evaluate(scope) > right.evaluate(scope) ? 1 : 0;
				} else {
					return left;
				}
			}
		}

		Expression additive() {
			Expression left = multiplicative();

			while (true) {
				Expression l = left;

				if (eat("+")) {
					Expression right = multiplicative();
					left = scope -> l.evaluate(scope) + right.evaluate(scope);
				} else if (peek('-') && !peekAt(1, '>')) {
					position++;
					Expression right = multiplicative();
					left = scope -> l.evaluate(scope) - right.evaluate(scope);
				} else {
					return left;
				}
			}
		}

		Expression multiplicative() {
			Expression left = unary();

			while (true) {
				Expression l = left;

				if (eat("*")) {
					Expression right = unary();
					left = scope -> l.evaluate(scope) * right.evaluate(scope);
				} else if (eat("/")) {
					Expression right = unary();
					left = scope -> {
						float divisor = right.evaluate(scope);
						return divisor == 0 ? 0 : l.evaluate(scope) / divisor;
					};
				} else {
					return left;
				}
			}
		}

		Expression unary() {
			if (eat("-")) {
				Expression operand = unary();
				return scope -> -operand.evaluate(scope);
			}

			if (eat("+")) {
				return unary();
			}

			if (peek('!') && !peekAt(1, '=')) {
				position++;
				Expression operand = unary();
				return scope -> operand.evaluate(scope) == 0 ? 1 : 0;
			}

			return primary();
		}

		Expression primary() {
			skipSpaces();

			if (eat("(")) {
				Expression inner = ternary();
				expect(")");
				return inner;
			}

			if (position < text.length() && (Character.isDigit(text.charAt(position)) || text.charAt(position) == '.')) {
				int start = position;

				while (position < text.length() && (Character.isDigit(text.charAt(position)) || text.charAt(position) == '.')) {
					position++;
				}

				float value = Float.parseFloat(text.substring(start, position));

				if (position < text.length() && text.charAt(position) == 'f') {
					position++;
				}

				return constant(value);
			}

			if (position < text.length() && (Character.isLetter(text.charAt(position)) || text.charAt(position) == '_')) {
				int start = position;

				while (position < text.length() && (Character.isLetterOrDigit(text.charAt(position)) || text.charAt(position) == '_' || text.charAt(position) == '.')) {
					position++;
				}

				String name = text.substring(start, position);
				List<Expression> arguments = new ArrayList<>();

				if (eat("(")) {
					if (!eat(")")) {
						do {
							arguments.add(ternary());
						} while (eat(","));

						expect(")");
					}
				}

				return name(name, arguments);
			}

			throw new IllegalArgumentException(position < text.length() ? "Unexpected '" + text.charAt(position) + "'" : "Expression ended early");
		}

		private Expression name(String name, List<Expression> args) {
			String shortName = name.startsWith("query.") ? "q." + name.substring(6) : name;

			if (name.startsWith("variable.") || name.startsWith("v.")) {
				String variable = name.substring(name.indexOf('.') + 1);
				return scope -> scope.variable(variable);
			}

			return switch (shortName) {
				case "true" -> constant(1);
				case "false" -> ZERO;
				case "math.pi" -> constant((float) Math.PI);
				case "q.anim_time" -> Scope::animTime;
				case "q.life_time" -> Scope::lifeTime;
				case "q.ground_speed" -> Scope::groundSpeed;
				case "q.head_x_rotation" -> Scope::headXRotation;
				case "q.head_y_rotation" -> Scope::headYRotation;
				case "q.modified_distance_moved" -> Scope::distanceMoved;
				case "math.sin" -> unaryMath(args, v -> Math.sin(Math.toRadians(v)));
				case "math.cos" -> unaryMath(args, v -> Math.cos(Math.toRadians(v)));
				case "math.asin" -> unaryMath(args, v -> Math.toDegrees(Math.asin(v)));
				case "math.acos" -> unaryMath(args, v -> Math.toDegrees(Math.acos(v)));
				case "math.atan" -> unaryMath(args, v -> Math.toDegrees(Math.atan(v)));
				case "math.abs" -> unaryMath(args, Math::abs);
				case "math.ceil" -> unaryMath(args, Math::ceil);
				case "math.floor" -> unaryMath(args, Math::floor);
				case "math.round" -> unaryMath(args, v -> Math.round(v));
				case "math.trunc" -> unaryMath(args, v -> v < 0 ? Math.ceil(v) : Math.floor(v));
				case "math.sqrt" -> unaryMath(args, Math::sqrt);
				case "math.exp" -> unaryMath(args, Math::exp);
				case "math.ln" -> unaryMath(args, Math::log);
				case "math.hermite_blend" -> unaryMath(args, v -> 3 * v * v - 2 * v * v * v);
				case "math.atan2" -> binaryMath(args, (y, x) -> Math.toDegrees(Math.atan2(y, x)));
				case "math.min" -> binaryMath(args, Math::min);
				case "math.max" -> binaryMath(args, Math::max);
				case "math.pow" -> binaryMath(args, Math::pow);
				case "math.mod" -> binaryMath(args, (a, b) -> b == 0 ? 0 : a % b);
				case "math.random" -> binaryMath(args, (low, high) -> low + ThreadLocalRandom.current().nextDouble() * (high - low));
				case "math.random_integer" -> binaryMath(args, (low, high) -> Math.floor(low + ThreadLocalRandom.current().nextDouble() * (high - low + 1)));
				case "math.clamp" -> {
					Expression value = arg(args, 0);
					Expression min = arg(args, 1);
					Expression max = arg(args, 2);
					yield scope -> Math.clamp(value.evaluate(scope), min.evaluate(scope), Math.max(min.evaluate(scope), max.evaluate(scope)));
				}
				case "math.lerp" -> {
					Expression a = arg(args, 0);
					Expression b = arg(args, 1);
					Expression t = arg(args, 2);
					yield scope -> a.evaluate(scope) + (b.evaluate(scope) - a.evaluate(scope)) * t.evaluate(scope);
				}
				case "math.lerprotate" -> {
					Expression a = arg(args, 0);
					Expression b = arg(args, 1);
					Expression t = arg(args, 2);
					yield scope -> {
						float from = a.evaluate(scope);
						float difference = (((b.evaluate(scope) - from) % 360) + 540) % 360 - 180;
						return from + difference * t.evaluate(scope);
					};
				}
				default -> ZERO;
			};
		}

		private static Expression arg(List<Expression> args, int index) {
			return index < args.size() ? args.get(index) : ZERO;
		}

		private static Expression unaryMath(List<Expression> args, DoubleUnaryOperator function) {
			Expression value = arg(args, 0);
			return scope -> (float) function.applyAsDouble(value.evaluate(scope));
		}

		private static Expression binaryMath(List<Expression> args, DoubleBinaryOperator function) {
			Expression a = arg(args, 0);
			Expression b = arg(args, 1);
			return scope -> (float) function.applyAsDouble(a.evaluate(scope), b.evaluate(scope));
		}

		boolean eat(String token) {
			skipSpaces();

			if (text.startsWith(token, position)) {
				position += token.length();
				return true;
			}

			return false;
		}

		boolean peek(char c) {
			skipSpaces();
			return position < text.length() && text.charAt(position) == c;
		}

		boolean peekAt(int offset, char c) {
			return position + offset < text.length() && text.charAt(position + offset) == c;
		}

		void expect(String token) {
			if (!eat(token)) {
				throw new IllegalArgumentException("Expected '" + token + "' at " + position);
			}
		}

		void skipSpaces() {
			while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
				position++;
			}
		}
	}
}
