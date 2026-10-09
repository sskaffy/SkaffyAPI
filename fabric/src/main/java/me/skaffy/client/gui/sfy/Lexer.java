package me.skaffy.client.gui.sfy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import me.skaffy.client.gui.sfy.Token.Kind;

final class Lexer {
	static final Set<String> KEYWORDS = Set.of(
			"package", "import", "class", "record", "enum", "public", "private", "protected", "static", "final", "var",
			"void", "int", "long", "double", "boolean", "char", "if", "else", "for", "while", "do", "switch", "case",
			"default", "break", "continue", "return", "new", "null", "true", "false", "this", "try", "catch", "finally",
			"throw", "instanceof", "float", "byte", "short", "interface", "extends", "implements", "abstract", "super",
			"synchronized", "volatile", "transient", "native", "strictfp", "goto", "const", "throws", "assert");
	private static final String[] OPERATORS = {
			">>>=", "<<=", ">>=", ">>>", "...", "->", "::", "++", "--", "&&", "||", "==", "!=", "<=", ">=", "+=", "-=", "*=",
			"/=", "%=", "&=", "|=", "^=", "<<", "(", ")", "{", "}", "[", "]", ";", ",", ".", "@", "=", ">", "<", "!",
			"~", "?", ":", "+", "-", "*", "/", "&", "|", "^", "%"};

	private final String file;
	private final String source;
	private int index;
	private int line = 1;
	private int lineStart;

	private Lexer(String file, String source) {
		this.file = file;
		this.source = source;
	}

	static List<Token> tokenize(String file, String source) throws CompileException {
		return new Lexer(file, source).run();
	}

	private List<Token> run() throws CompileException {
		List<Token> tokens = new ArrayList<>();

		while (true) {
			skipSpaceAndComments();

			if (index >= source.length()) {
				tokens.add(new Token(Kind.EOF, "", null, line, column()));
				return tokens;
			}

			tokens.add(next());
		}
	}

	private int column() {
		return index - lineStart + 1;
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

	private char peek(int offset) {
		int at = index + offset;
		return at < source.length() ? source.charAt(at) : '\0';
	}

	private Token next() throws CompileException {
		int startLine = line;
		int startColumn = column();
		char c = source.charAt(index);

		if (Character.isJavaIdentifierStart(c) && c != '$') {
			int start = index;

			while (index < source.length() && Character.isJavaIdentifierPart(source.charAt(index)) && source.charAt(index) != '$') {
				index++;
			}

			String word = source.substring(start, index);
			return new Token(KEYWORDS.contains(word) ? Kind.KEYWORD : Kind.IDENT, word, null, startLine, startColumn);
		}

		if (Character.isDigit(c) || c == '.' && Character.isDigit(peek(1))) {
			return number(startLine, startColumn);
		}

		if (c == '#') {
			return color(startLine, startColumn);
		}

		if (c == '"') {
			if (peek(1) == '"' && peek(2) == '"') {
				return textBlock(startLine, startColumn);
			}

			return string(startLine, startColumn);
		}

		if (c == '\'') {
			return character(startLine, startColumn);
		}

		for (String operator : OPERATORS) {
			if (source.startsWith(operator, index)) {
				index += operator.length();
				return new Token(Kind.OP, operator, null, startLine, startColumn);
			}
		}

		throw error(startLine, startColumn, "Unexpected character '" + c + "'");
	}

	private Token number(int startLine, int startColumn) throws CompileException {
		int start = index;
		boolean decimal = false;
		String digits;

		if (source.charAt(index) == '0' && (peek(1) == 'x' || peek(1) == 'X' || peek(1) == 'b' || peek(1) == 'B')) {
			int radix = peek(1) == 'x' || peek(1) == 'X' ? 16 : 2;
			index += 2;
			int digitsStart = index;

			while (index < source.length() && (Character.digit(source.charAt(index), radix) >= 0 || source.charAt(index) == '_')) {
				index++;
			}

			digits = source.substring(digitsStart, index).replace("_", "");

			if (digits.isEmpty()) {
				throw error(startLine, startColumn, "Number has no digits");
			}

			boolean isLong = index < source.length() && (source.charAt(index) == 'L' || source.charAt(index) == 'l');

			if (isLong) {
				index++;
			}

			checkNoLetterAfter(startLine, startColumn);

			try {
				long value = Long.parseUnsignedLong(digits, radix);

				if (!isLong && Long.compareUnsigned(value, 0xFFFFFFFFL) > 0) {
					throw error(startLine, startColumn, "Number is too big for an int, add L to make it a long");
				}

				return isLong ? new Token(Kind.LONG, source.substring(start, index), value, startLine, startColumn)
						: new Token(Kind.INT, source.substring(start, index), (int) value, startLine, startColumn);
			} catch (NumberFormatException e) {
				throw error(startLine, startColumn, "Number is too big");
			}
		}

		while (index < source.length() && (Character.isDigit(source.charAt(index)) || source.charAt(index) == '_')) {
			index++;
		}

		if (index < source.length() && source.charAt(index) == '.' && Character.isDigit(peek(1))) {
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

		digits = source.substring(start, index).replace("_", "");
		String text;

		if (index < source.length() && source.charAt(index) == '%') {
			index++;
			return new Token(Kind.PERCENT, source.substring(start, index), Double.parseDouble(digits), startLine, startColumn);
		}

		if (source.startsWith("ms", index) && !identifierPart(peek(2))) {
			index += 2;
			return new Token(Kind.DURATION, source.substring(start, index), Math.round(Double.parseDouble(digits)), startLine, startColumn);
		}

		if (index < source.length() && source.charAt(index) == 's' && !identifierPart(peek(1))) {
			index++;
			return new Token(Kind.DURATION, source.substring(start, index), Math.round(Double.parseDouble(digits) * 1000), startLine, startColumn);
		}

		if (index < source.length() && (source.charAt(index) == 'f' || source.charAt(index) == 'F') && !identifierPart(peek(1))) {
			throw error(startLine, startColumn, "There is no float in .sfy, use a double: " + digits);
		}

		if (index < source.length() && (source.charAt(index) == 'd' || source.charAt(index) == 'D') && !identifierPart(peek(1))) {
			index++;
			decimal = true;
		}

		if (!decimal && index < source.length() && (source.charAt(index) == 'L' || source.charAt(index) == 'l') && !identifierPart(peek(1))) {
			index++;
			text = source.substring(start, index);

			try {
				return new Token(Kind.LONG, text, Long.parseLong(digits), startLine, startColumn);
			} catch (NumberFormatException e) {
				throw error(startLine, startColumn, "Number is too big for a long");
			}
		}

		checkNoLetterAfter(startLine, startColumn);
		text = source.substring(start, index);

		if (decimal) {
			return new Token(Kind.DOUBLE, text, Double.parseDouble(digits), startLine, startColumn);
		}

		try {
			long value = Long.parseLong(digits);

			if (value > 2147483648L) {
				throw error(startLine, startColumn, "Number is too big for an int, add L to make it a long");
			}

			return new Token(Kind.INT, text, value == 2147483648L ? (Object) value : (Object) (int) value, startLine, startColumn);
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

			throw error(startLine, startColumn, "Unknown number suffix '" + source.substring(index, end) + "' (known: %, ms, s, L)");
		}
	}

	private static boolean identifierPart(char c) {
		return Character.isJavaIdentifierPart(c) && c != '\0';
	}

	private Token color(int startLine, int startColumn) throws CompileException {
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
		return new Token(Kind.COLOR, source.substring(start, index), argb, startLine, startColumn);
	}

	private Token string(int startLine, int startColumn) throws CompileException {
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
				return new Token(Kind.STRING, source.substring(start, index), value.toString(), startLine, startColumn);
			}

			if (c == '\\') {
				value.append(escape());
			} else {
				value.append(c);
				index++;
			}
		}
	}

	private Token textBlock(int startLine, int startColumn) throws CompileException {
		int start = index;
		index += 3;

		while (index < source.length() && source.charAt(index) != '\n') {
			if (!Character.isWhitespace(source.charAt(index))) {
				throw error(startLine, startColumn, "A text block starts with \"\"\" and a new line");
			}

			index++;
		}

		if (index >= source.length()) {
			throw error(startLine, startColumn, "Text block is never closed with \"\"\"");
		}

		index++;
		line++;
		lineStart = index;
		List<String> lines = new ArrayList<>();
		StringBuilder current = new StringBuilder();

		while (true) {
			if (index >= source.length()) {
				throw error(startLine, startColumn, "Text block is never closed with \"\"\"");
			}

			char c = source.charAt(index);

			if (c == '"' && peek(1) == '"' && peek(2) == '"') {
				index += 3;
				lines.add(current.toString());
				break;
			}

			if (c == '\n') {
				lines.add(current.toString());
				current.setLength(0);
				index++;
				line++;
				lineStart = index;
			} else if (c == '\\') {
				current.append(escape());
			} else {
				current.append(c);
				index++;
			}
		}

		int indent = Integer.MAX_VALUE;

		for (int i = 0; i < lines.size(); i++) {
			String text = lines.get(i);
			boolean last = i == lines.size() - 1;

			if (text.isBlank() && !last) {
				continue;
			}

			int spaces = 0;

			while (spaces < text.length() && Character.isWhitespace(text.charAt(spaces))) {
				spaces++;
			}

			indent = Math.min(indent, spaces);
		}

		StringBuilder value = new StringBuilder();

		for (int i = 0; i < lines.size(); i++) {
			String text = lines.get(i);
			boolean last = i == lines.size() - 1;

			if (last && text.isBlank()) {
				break;
			}

			value.append(text.length() >= indent ? text.substring(indent).stripTrailing() : text.strip());

			if (!last) {
				value.append('\n');
			}
		}

		return new Token(Kind.STRING, source.substring(start, index), value.toString(), startLine, startColumn);
	}

	private Token character(int startLine, int startColumn) throws CompileException {
		int start = index;
		index++;

		if (index >= source.length() || source.charAt(index) == '\n' || source.charAt(index) == '\'') {
			throw error(startLine, startColumn, "Empty character, write 'a'");
		}

		char value;

		if (source.charAt(index) == '\\') {
			String escaped = escape();

			if (escaped.length() != 1) {
				throw error(startLine, startColumn, "A char holds exactly one character");
			}

			value = escaped.charAt(0);
		} else {
			value = source.charAt(index++);
		}

		if (index >= source.length() || source.charAt(index) != '\'') {
			throw error(startLine, startColumn, "A char holds exactly one character, closed with '");
		}

		index++;
		return new Token(Kind.CHAR, source.substring(start, index), value, startLine, startColumn);
	}

	private String escape() throws CompileException {
		int startColumn = column();
		index++;

		if (index >= source.length()) {
			throw error(line, startColumn, "Unfinished escape");
		}

		char c = source.charAt(index++);
		return switch (c) {
			case 'n' -> "\n";
			case 't' -> "\t";
			case 'r' -> "\r";
			case 'b' -> "\b";
			case 'f' -> "\f";
			case 's' -> " ";
			case '0' -> "\0";
			case '\\' -> "\\";
			case '\'' -> "'";
			case '"' -> "\"";
			case 'u' -> {
				while (index < source.length() && source.charAt(index) == 'u') {
					index++;
				}

				if (index + 4 > source.length()) {
					throw error(line, startColumn, "\\u needs 4 hex digits");
				}

				try {
					char value = (char) Integer.parseInt(source.substring(index, index + 4), 16);
					index += 4;
					yield String.valueOf(value);
				} catch (NumberFormatException e) {
					throw error(line, startColumn, "\\u needs 4 hex digits");
				}
			}
			default -> throw error(line, startColumn, "Unknown escape \\" + c);
		};
	}

	private CompileException error(int line, int column, String message) {
		return new CompileException(List.of(new CompileError(file, new Pos(line, column), message)));
	}
}
