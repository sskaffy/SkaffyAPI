package me.skaffy.client.shader.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import me.skaffy.client.gui.sfy.CompileError;
import me.skaffy.client.gui.sfy.CompileException;
import me.skaffy.client.gui.sfy.Pos;

final class ShaderLexer {
	static final Set<String> KEYWORDS = Set.of(
			"package", "import", "class", "record", "public", "private", "protected", "static", "final", "const", "var",
			"void", "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return",
			"discard", "new", "true", "false",
			"null", "this", "try", "catch", "finally", "throw", "throws", "instanceof", "enum", "interface", "extends",
			"implements", "abstract", "super", "synchronized", "volatile", "transient", "native", "strictfp", "goto",
			"assert", "long", "double", "char", "byte", "short", "boolean", "yield");
	private static final String[] OPERATORS = {
			"<<=", ">>=", "->", "++", "--", "&&", "||", "==", "!=", "<=", ">=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
			"<<", ">>", "(", ")", "{", "}", "[", "]", ";", ",", ".", "@", "=", ">", "<", "!", "~", "?", ":", "+", "-", "*", "/",
			"&", "|", "^", "%"};

	private final String file;
	private final String source;
	private int index;
	private int line = 1;
	private int lineStart;

	private ShaderLexer(String file, String source) {
		this.file = file;
		this.source = source;
	}

	static List<ShaderToken> tokenize(String file, String source) throws CompileException {
		return new ShaderLexer(file, source).run();
	}

	private List<ShaderToken> run() throws CompileException {
		List<ShaderToken> tokens = new ArrayList<>();

		while (true) {
			skipSpaceAndComments();

			if (index >= source.length()) {
				tokens.add(new ShaderToken(ShaderToken.Kind.EOF, "", null, line, column()));
				return tokens;
			}

			tokens.add(next());
		}
	}

	private int column() {
		return index - lineStart + 1;
	}

	private char peek(int offset) {
		int at = index + offset;
		return at < source.length() ? source.charAt(at) : '\0';
	}

	private void skipSpaceAndComments() throws CompileException {
		while (index < source.length()) {
			char c = source.charAt(index);

			if (c == '\n') {
				index++;
				line++;
				lineStart = index;
			} else if (Character.isWhitespace(c)) {
				index++;
			} else if (c == '/' && peek(1) == '/') {
				while (index < source.length() && source.charAt(index) != '\n') {
					index++;
				}
			} else if (c == '/' && peek(1) == '*') {
				int startLine = line;
				int startColumn = column();
				index += 2;

				while (true) {
					if (index >= source.length()) {
						throw error(startLine, startColumn, "Comment is never closed with */");
					}

					if (source.charAt(index) == '*' && peek(1) == '/') {
						index += 2;
						break;
					}

					if (source.charAt(index) == '\n') {
						line++;
						lineStart = index + 1;
					}

					index++;
				}
			} else {
				return;
			}
		}
	}

	private ShaderToken next() throws CompileException {
		int startLine = line;
		int startColumn = column();
		char c = source.charAt(index);

		if (Character.isJavaIdentifierStart(c) && c != '$') {
			int start = index;

			while (index < source.length() && Character.isJavaIdentifierPart(source.charAt(index)) && source.charAt(index) != '$') {
				index++;
			}

			String word = source.substring(start, index);
			return new ShaderToken(KEYWORDS.contains(word) ? ShaderToken.Kind.KEYWORD : ShaderToken.Kind.IDENT, word, null, startLine, startColumn);
		}

		if (Character.isDigit(c) || c == '.' && Character.isDigit(peek(1))) {
			return number(startLine, startColumn);
		}

		if (c == '#') {
			return color(startLine, startColumn);
		}

		if (c == '"') {
			return string(startLine, startColumn);
		}

		for (String operator : OPERATORS) {
			if (source.startsWith(operator, index)) {
				index += operator.length();
				return new ShaderToken(ShaderToken.Kind.OP, operator, null, startLine, startColumn);
			}
		}

		throw error(startLine, startColumn, "Unexpected character '" + c + "'");
	}

	private ShaderToken number(int startLine, int startColumn) throws CompileException {
		int start = index;

		if (source.charAt(index) == '0' && (peek(1) == 'x' || peek(1) == 'X')) {
			index += 2;
			int digitsStart = index;

			while (index < source.length() && (Character.digit(source.charAt(index), 16) >= 0 || source.charAt(index) == '_')) {
				index++;
			}

			String digits = source.substring(digitsStart, index).replace("_", "");

			if (digits.isEmpty()) {
				throw error(startLine, startColumn, "Number has no digits");
			}

			checkNoLetterAfter(startLine, startColumn);
			long value = Long.parseLong(digits, 16);

			if (value > 0xFFFFFFFFL) {
				throw error(startLine, startColumn, "Number is too big for an int");
			}

			return new ShaderToken(ShaderToken.Kind.INT, source.substring(start, index), (int) value, startLine, startColumn);
		}

		boolean decimal = false;

		while (index < source.length() && (Character.isDigit(source.charAt(index)) || source.charAt(index) == '_')) {
			index++;
		}

		if (index < source.length() && source.charAt(index) == '.' && !Character.isJavaIdentifierStart(peek(1))) {
			decimal = true;
			index++;

			while (index < source.length() && (Character.isDigit(source.charAt(index)) || source.charAt(index) == '_')) {
				index++;
			}
		}

		if (index < source.length() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')
				&& (Character.isDigit(peek(1)) || (peek(1) == '-' || peek(1) == '+') && Character.isDigit(peek(2)))) {
			decimal = true;
			index += 2;

			while (index < source.length() && Character.isDigit(source.charAt(index))) {
				index++;
			}
		}

		String digits = source.substring(start, index).replace("_", "");

		if (index < source.length() && (source.charAt(index) == 'f' || source.charAt(index) == 'F') && !identifierPart(peek(1))) {
			index++;
			decimal = true;
		} else if (index < source.length() && (source.charAt(index) == 'd' || source.charAt(index) == 'D') && !identifierPart(peek(1))) {
			throw error(startLine, startColumn, "Shaders have no double, write a float: " + digits);
		} else if (index < source.length() && (source.charAt(index) == 'L' || source.charAt(index) == 'l') && !identifierPart(peek(1))) {
			throw error(startLine, startColumn, "Shaders have no long, write an int: " + digits);
		}

		checkNoLetterAfter(startLine, startColumn);
		String text = source.substring(start, index);

		if (decimal) {
			double value = Double.parseDouble(digits);

			if (Double.isInfinite((float) value)) {
				throw error(startLine, startColumn, "Number is too big for a float");
			}

			return new ShaderToken(ShaderToken.Kind.FLOAT, text, value, startLine, startColumn);
		}

		try {
			long value = Long.parseLong(digits);

			if (value > 2147483648L) {
				throw error(startLine, startColumn, "Number is too big for an int");
			}

			return new ShaderToken(ShaderToken.Kind.INT, text, value == 2147483648L ? (Object) value : (Object) (int) value, startLine, startColumn);
		} catch (NumberFormatException e) {
			throw error(startLine, startColumn, "Number is too big");
		}
	}

	private void checkNoLetterAfter(int startLine, int startColumn) throws CompileException {
		if (index < source.length() && identifierPart(source.charAt(index))) {
			int end = index;

			while (end < source.length() && identifierPart(source.charAt(end))) {
				end++;
			}

			throw error(startLine, startColumn, "Unknown number suffix '" + source.substring(index, end) + "' (only f is allowed)");
		}
	}

	private static boolean identifierPart(char c) {
		return Character.isJavaIdentifierPart(c) && c != '\0';
	}

	private ShaderToken color(int startLine, int startColumn) throws CompileException {
		int start = index;
		index++;

		while (index < source.length() && Character.digit(source.charAt(index), 16) >= 0) {
			index++;
		}

		String hex = source.substring(start + 1, index);

		if (index < source.length() && identifierPart(source.charAt(index))) {
			throw error(startLine, startColumn, "Colors are #RRGGBB or #AARRGGBB");
		}

		int argb = switch (hex.length()) {
			case 6 -> 0xFF000000 | Integer.parseUnsignedInt(hex, 16);
			case 8 -> Integer.parseUnsignedInt(hex, 16);
			default -> throw error(startLine, startColumn, "Colors are #RRGGBB or #AARRGGBB, got #" + hex);
		};
		return new ShaderToken(hex.length() == 6 ? ShaderToken.Kind.COLOR3 : ShaderToken.Kind.COLOR4, source.substring(start, index), argb, startLine, startColumn);
	}

	private ShaderToken string(int startLine, int startColumn) throws CompileException {
		int start = index;
		index++;
		StringBuilder value = new StringBuilder();

		while (true) {
			if (index >= source.length() || source.charAt(index) == '\n') {
				throw error(startLine, startColumn, "Text is never closed with \"");
			}

			char c = source.charAt(index);

			if (c == '"') {
				index++;
				return new ShaderToken(ShaderToken.Kind.STRING, source.substring(start, index), value.toString(), startLine, startColumn);
			}

			if (c == '\\') {
				char escaped = peek(1);

				if (escaped != '"' && escaped != '\\') {
					throw error(line, column(), "Only \\\" and \\\\ work in shader text");
				}

				value.append(escaped);
				index += 2;
			} else {
				value.append(c);
				index++;
			}
		}
	}

	private CompileException error(int line, int column, String message) {
		return new CompileException(List.of(new CompileError(file, new Pos(line, column), message)));
	}
}
