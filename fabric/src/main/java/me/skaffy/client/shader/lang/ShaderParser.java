package me.skaffy.client.shader.lang;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.skaffy.client.gui.sfy.CompileError;
import me.skaffy.client.gui.sfy.CompileException;
import me.skaffy.client.gui.sfy.Pos;
import me.skaffy.client.shader.lang.ShaderAst.Annotation;
import me.skaffy.client.shader.lang.ShaderAst.AnnotationArg;
import me.skaffy.client.shader.lang.ShaderAst.ArrayInit;
import me.skaffy.client.shader.lang.ShaderAst.Assign;
import me.skaffy.client.shader.lang.ShaderAst.Binary;
import me.skaffy.client.shader.lang.ShaderAst.Block;
import me.skaffy.client.shader.lang.ShaderAst.Break;
import me.skaffy.client.shader.lang.ShaderAst.Call;
import me.skaffy.client.shader.lang.ShaderAst.Case;
import me.skaffy.client.shader.lang.ShaderAst.Cast;
import me.skaffy.client.shader.lang.ShaderAst.ClassDecl;
import me.skaffy.client.shader.lang.ShaderAst.Conditional;
import me.skaffy.client.shader.lang.ShaderAst.Continue;
import me.skaffy.client.shader.lang.ShaderAst.Declarator;
import me.skaffy.client.shader.lang.ShaderAst.Discard;
import me.skaffy.client.shader.lang.ShaderAst.DoWhile;
import me.skaffy.client.shader.lang.ShaderAst.Empty;
import me.skaffy.client.shader.lang.ShaderAst.Expr;
import me.skaffy.client.shader.lang.ShaderAst.ExprStmt;
import me.skaffy.client.shader.lang.ShaderAst.Field;
import me.skaffy.client.shader.lang.ShaderAst.For;
import me.skaffy.client.shader.lang.ShaderAst.ForEach;
import me.skaffy.client.shader.lang.ShaderAst.If;
import me.skaffy.client.shader.lang.ShaderAst.Import;
import me.skaffy.client.shader.lang.ShaderAst.IncDec;
import me.skaffy.client.shader.lang.ShaderAst.Index;
import me.skaffy.client.shader.lang.ShaderAst.Literal;
import me.skaffy.client.shader.lang.ShaderAst.LiteralKind;
import me.skaffy.client.shader.lang.ShaderAst.LocalVar;
import me.skaffy.client.shader.lang.ShaderAst.Member;
import me.skaffy.client.shader.lang.ShaderAst.Method;
import me.skaffy.client.shader.lang.ShaderAst.Name;
import me.skaffy.client.shader.lang.ShaderAst.NewArray;
import me.skaffy.client.shader.lang.ShaderAst.NewRecord;
import me.skaffy.client.shader.lang.ShaderAst.Param;
import me.skaffy.client.shader.lang.ShaderAst.RecordDecl;
import me.skaffy.client.shader.lang.ShaderAst.Return;
import me.skaffy.client.shader.lang.ShaderAst.Select;
import me.skaffy.client.shader.lang.ShaderAst.Stmt;
import me.skaffy.client.shader.lang.ShaderAst.Switch;
import me.skaffy.client.shader.lang.ShaderAst.TypeRef;
import me.skaffy.client.shader.lang.ShaderAst.Unary;
import me.skaffy.client.shader.lang.ShaderAst.Unit;
import me.skaffy.client.shader.lang.ShaderAst.While;
import me.skaffy.client.shader.lang.ShaderToken.Kind;

final class ShaderParser {
	private static final Set<String> MODIFIERS = Set.of("public", "private", "protected", "static", "final", "const");
	private static final Set<String> ASSIGN_OPS = Set.of("=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=");
	static final Set<String> CAST_TYPES = Set.of("float", "int", "bool", "vec2", "vec3", "vec4", "ivec2", "ivec3", "ivec4",
			"bvec2", "bvec3", "bvec4", "mat2", "mat3", "mat4");
	private static final String[][] BINARY_LEVELS = {
			{"||"}, {"&&"}, {"|"}, {"^"}, {"&"}, {"==", "!="}, {"<", ">", "<=", ">="}, {"<<", ">>"}, {"+", "-"}, {"*", "/", "%"}};

	private final String file;
	private final List<ShaderToken> tokens;
	private int index;

	private ShaderParser(String file, List<ShaderToken> tokens) {
		this.file = file;
		this.tokens = tokens;
	}

	static Unit parseFile(String file, String source) throws CompileException {
		return new ShaderParser(file, ShaderLexer.tokenize(file, source)).unit();
	}


	private ShaderToken peek() {
		return tokens.get(index);
	}

	private ShaderToken peek(int offset) {
		return tokens.get(Math.min(index + offset, tokens.size() - 1));
	}

	private ShaderToken next() {
		ShaderToken token = tokens.get(index);

		if (token.kind() != Kind.EOF) {
			index++;
		}

		return token;
	}

	private boolean accept(String keywordOrOp) {
		if (peek().is(keywordOrOp)) {
			next();
			return true;
		}

		return false;
	}

	private ShaderToken expect(String keywordOrOp) throws CompileException {
		if (!peek().is(keywordOrOp)) {
			throw error(peek(), "Expected '" + keywordOrOp + "' but found " + peek());
		}

		return next();
	}

	private String identifier(String what) throws CompileException {
		ShaderToken token = peek();

		if (token.isIdent()) {
			return next().text();
		}

		if (token.kind() == Kind.KEYWORD) {
			throw error(token, "'" + token.text() + "' is a reserved word and can't be a " + what);
		}

		throw error(token, "Expected a " + what + " but found " + token);
	}

	private CompileException error(ShaderToken token, String message) {
		return error(token.pos(), message);
	}

	private CompileException error(Pos pos, String message) {
		return new CompileException(List.of(new CompileError(file, pos, message)));
	}


	private Unit unit() throws CompileException {
		if (!peek().is("package")) {
			throw error(peek(), "A .sfy file starts with its package, for example: package effects;");
		}

		Pos packagePos = next().pos();
		String packageName = qualifiedName("package name");
		expect(";");
		List<Import> imports = new ArrayList<>();

		while (peek().is("import")) {
			Pos pos = next().pos();

			if (peek().is("static")) {
				throw error(peek(), "There are no static imports");
			}

			StringBuilder name = new StringBuilder(identifier("import"));
			boolean wildcard = false;

			while (accept(".")) {
				if (accept("*")) {
					wildcard = true;
					break;
				}

				name.append('.').append(identifier("import"));
			}

			expect(";");
			imports.add(new Import(name.toString(), wildcard, pos));
		}

		List<Annotation> annotations = annotations();
		modifiers();

		if (peek().is("record") || peek().is("enum") || peek().is("interface")) {
			throw error(peek(), "A .sfy file has one class; records go inside it");
		}

		expect("class");
		ShaderToken nameToken = peek();
		String className = identifier("class name");

		if (peek().is("extends") || peek().is("implements")) {
			throw error(peek(), "Classes can't extend or implement anything in .sfy");
		}

		expect("{");
		List<Member> members = new ArrayList<>();

		while (!accept("}")) {
			if (peek().kind() == Kind.EOF) {
				throw error(peek(), "Missing } at the end of " + className);
			}

			if (accept(";")) {
				continue;
			}

			members.add(member());
		}

		if (peek().kind() != Kind.EOF) {
			throw error(peek(), "Expected the end of the file, found " + peek());
		}

		return new Unit(packageName, packagePos, imports, new ClassDecl(className, annotations, members, nameToken.pos()));
	}

	private String qualifiedName(String what) throws CompileException {
		StringBuilder name = new StringBuilder(identifier(what));

		while (accept(".")) {
			name.append('.').append(identifier(what));
		}

		return name.toString();
	}

	private List<Annotation> annotations() throws CompileException {
		List<Annotation> annotations = new ArrayList<>();

		while (peek().is("@")) {
			Pos pos = next().pos();
			String name = identifier("annotation name");
			List<AnnotationArg> args = new ArrayList<>();

			if (accept("(")) {
				if (!peek().is(")")) {
					do {
						ShaderToken start = peek();

						if (start.isIdent() && peek(1).is("=")) {
							next();
							next();
							args.add(new AnnotationArg(start.text(), annotationValue(), start.pos()));
						} else {
							if (!args.isEmpty() && args.getLast().key() != null) {
								throw error(start, "Write unnamed values first: @" + name + "(VALUE, name = ...)");
							}

							args.add(new AnnotationArg(null, annotationValue(), start.pos()));
						}
					} while (accept(","));
				}

				expect(")");
			}

			annotations.add(new Annotation(name, args, pos));
		}

		return annotations;
	}

	private Expr annotationValue() throws CompileException {
		if (peek().is("{")) {
			return arrayInit();
		}

		return expression();
	}

	private Set<String> modifiers() {
		Set<String> modifiers = new LinkedHashSet<>();

		while (peek().kind() == Kind.KEYWORD && MODIFIERS.contains(peek().text())) {
			modifiers.add(next().text());
		}

		return modifiers;
	}

	private Member member() throws CompileException {
		List<Annotation> annotations = annotations();
		Set<String> modifiers = modifiers();

		if (peek().is("@")) {
			annotations = new ArrayList<>(annotations);
			annotations.addAll(annotations());
		}

		ShaderToken start = peek();

		if (accept("record")) {
			String name = identifier("record name");
			List<Param> components = params();

			if (accept("{")) {
				if (!accept("}")) {
					throw error(peek(), "Records in shaders hold values only, no methods (write helper methods in the class)");
				}
			} else {
				expect(";");
			}

			return new RecordDecl(modifiers, name, components, start.pos());
		}

		if (peek().is("class") || peek().is("enum") || peek().is("interface")) {
			throw error(peek(), "Only records can go inside the class of a shader");
		}

		TypeRef returnType = accept("void") ? null : type("type");
		ShaderToken nameToken = peek();
		String name = identifier(returnType == null ? "method name" : "name");

		if (peek().is("(")) {
			List<Param> params = params();

			if (peek().is("throws")) {
				throw error(peek(), "Shaders have no exceptions");
			}

			return new Method(annotations, modifiers, returnType, name, params, block(), nameToken.pos());
		}

		if (returnType == null) {
			throw error(nameToken, "Only methods can be void");
		}

		if (peek().is("[")) {
			throw error(peek(), "Write arrays like Java: float[] " + name);
		}

		Expr init = accept("=") ? initializer() : null;

		if (peek().is(",")) {
			throw error(peek(), "Declare one field per line in shaders");
		}

		expect(";");
		return new Field(annotations, modifiers, returnType, name, init, nameToken.pos());
	}

	private TypeRef type(String what) throws CompileException {
		ShaderToken token = peek();

		if (token.is("boolean")) {
			throw error(token, "Shaders use bool, not boolean");
		}

		if (token.is("double") || token.is("long") || token.is("char") || token.is("byte") || token.is("short")) {
			throw error(token, "Shaders have no " + token.text() + "; use float, int or bool");
		}

		StringBuilder qualified = new StringBuilder(identifier(what));

		while (peek().is(".") && peek(1).isIdent()) {
			next();
			qualified.append('.').append(next().text());
		}

		String name = qualified.toString();
		boolean array = false;

		if (peek().is("[") && peek(1).is("]")) {
			next();
			next();
			array = true;

			if (peek().is("[")) {
				throw error(peek(), "Arrays of arrays don't exist in shaders");
			}
		}

		return new TypeRef(name, array, token.pos());
	}

	private List<Param> params() throws CompileException {
		expect("(");
		List<Param> params = new ArrayList<>();

		if (!accept(")")) {
			do {
				if (peek().is("final")) {
					next();
				}

				TypeRef type = type("parameter type");
				ShaderToken name = peek();
				params.add(new Param(type, identifier("parameter name"), name.pos()));
			} while (accept(","));

			expect(")");
		}

		return params;
	}


	private Block block() throws CompileException {
		Pos pos = expect("{").pos();
		List<Stmt> statements = new ArrayList<>();

		while (!accept("}")) {
			if (peek().kind() == Kind.EOF) {
				throw error(peek(), "Missing }");
			}

			statements.add(statement());
		}

		return new Block(statements, pos);
	}

	private Stmt statement() throws CompileException {
		ShaderToken token = peek();
		Pos pos = token.pos();

		if (token.is("{")) {
			return block();
		}

		if (accept(";")) {
			return new Empty(pos);
		}

		if (accept("if")) {
			expect("(");
			Expr condition = expression();
			expect(")");
			Stmt then = statement();
			Stmt otherwise = accept("else") ? statement() : null;
			return new If(condition, then, otherwise, pos);
		}

		if (accept("while")) {
			expect("(");
			Expr condition = expression();
			expect(")");
			return new While(condition, statement(), pos);
		}

		if (accept("do")) {
			Stmt body = statement();
			expect("while");
			expect("(");
			Expr condition = expression();
			expect(")");
			expect(";");
			return new DoWhile(body, condition, pos);
		}

		if (accept("for")) {
			return forStatement(pos);
		}

		if (accept("switch")) {
			return switchStatement(pos);
		}

		if (accept("return")) {
			Expr value = peek().is(";") ? null : expression();
			expect(";");
			return new Return(value, pos);
		}

		if (accept("break")) {
			expect(";");
			return new Break(pos);
		}

		if (accept("continue")) {
			expect(";");
			return new Continue(pos);
		}

		if (accept("discard")) {
			expect(";");
			return new Discard(pos);
		}

		if (token.is("throw") || token.is("try")) {
			throw error(token, "Shaders have no exceptions");
		}

		if (isLocalVarStart()) {
			LocalVar local = localVar();
			expect(";");
			return local;
		}

		Expr expr = expression();
		expect(";");
		return new ExprStmt(expr, pos);
	}

	private boolean isLocalVarStart() {
		ShaderToken token = peek();

		if (token.is("final") || token.is("const") || token.is("var") && peek(1).isIdent()) {
			return true;
		}

		if (token.is("boolean") || token.is("double") || token.is("long")) {
			return true;
		}

		if (!token.isIdent()) {
			return false;
		}

		int end = typeEnd(0);
		return peek(end + 1).isIdent() || peek(end + 1).is("[") && peek(end + 2).is("]");
	}

	private int typeEnd(int offset) {
		int end = offset;

		while (peek(end + 1).is(".") && peek(end + 2).isIdent()) {
			end += 2;
		}

		return end;
	}

	private LocalVar localVar() throws CompileException {
		Pos pos = peek().pos();
		boolean isConst = false;
		boolean isFinal = false;

		while (peek().is("final") || peek().is("const")) {
			if (next().is("const")) {
				isConst = true;
			} else {
				isFinal = true;
			}
		}

		TypeRef type = accept("var") ? null : type("type");
		List<Declarator> declarators = new ArrayList<>();

		do {
			ShaderToken name = peek();
			String declared = identifier("variable name");

			if (peek().is("[")) {
				throw error(peek(), "Write arrays like Java: float[] " + declared);
			}

			Expr init = accept("=") ? initializer() : null;
			declarators.add(new Declarator(declared, init, name.pos()));
		} while (accept(","));

		return new LocalVar(isConst, isFinal, type, declarators, pos);
	}

	private Expr initializer() throws CompileException {
		return peek().is("{") ? arrayInit() : expression();
	}

	private ArrayInit arrayInit() throws CompileException {
		Pos pos = expect("{").pos();
		List<Expr> values = new ArrayList<>();

		if (!accept("}")) {
			do {
				if (peek().is("}")) {
					break;
				}

				values.add(peek().is("{") ? arrayInit() : expression());
			} while (accept(","));

			expect("}");
		}

		return new ArrayInit(values, pos);
	}

	private Stmt forStatement(Pos pos) throws CompileException {
		expect("(");

		int end = peek().isIdent() ? typeEnd(0) : 0;

		if ((peek().isIdent() || peek().is("var")) && peek(end + 1).isIdent() && peek(end + 2).is(":")
				|| peek().isIdent() && peek(end + 1).is("[") && peek(end + 2).is("]") && peek(end + 3).isIdent() && peek(end + 4).is(":")) {
			TypeRef type = accept("var") ? null : type("type");
			String name = identifier("variable name");
			expect(":");
			Expr iterable = expression();
			expect(")");
			return new ForEach(type, name, iterable, statement(), pos);
		}

		List<Stmt> init = new ArrayList<>();

		if (!peek().is(";")) {
			if (isLocalVarStart()) {
				init.add(localVar());
			} else {
				do {
					Expr expr = expression();
					init.add(new ExprStmt(expr, expr.pos()));
				} while (accept(","));
			}
		}

		expect(";");
		Expr condition = peek().is(";") ? null : expression();
		expect(";");
		List<Expr> updates = new ArrayList<>();

		if (!peek().is(")")) {
			do {
				updates.add(expression());
			} while (accept(","));
		}

		expect(")");
		return new For(init, condition, updates, statement(), pos);
	}

	private Stmt switchStatement(Pos pos) throws CompileException {
		expect("(");
		Expr selector = expression();
		expect(")");
		expect("{");
		List<Case> cases = new ArrayList<>();
		Boolean arrows = null;

		while (!accept("}")) {
			ShaderToken start = peek();
			List<Expr> labels = new ArrayList<>();
			boolean isDefault;

			if (accept("default")) {
				isDefault = true;
			} else if (accept("case")) {
				isDefault = false;

				do {
					labels.add(ternary());
				} while (accept(","));
			} else {
				throw error(start, "Expected case or default but found " + start);
			}

			boolean arrow = peek().is("->");

			if (!arrow && !peek().is(":")) {
				throw error(peek(), "Expected : or -> after the case");
			}

			if (arrows != null && arrows != arrow) {
				throw error(peek(), "Use either case X: or case X -> in one switch, not both");
			}

			arrows = arrow;
			next();
			List<Stmt> body = new ArrayList<>();

			if (arrow) {
				body.add(statement());
			} else {
				while (!peek().is("case") && !peek().is("default") && !peek().is("}")) {
					if (peek().kind() == Kind.EOF) {
						throw error(peek(), "Missing } at the end of the switch");
					}

					body.add(statement());
				}
			}

			cases.add(new Case(labels, isDefault, arrow, body, start.pos()));
		}

		return new Switch(selector, cases, pos);
	}


	private Expr expression() throws CompileException {
		Expr target = ternary();

		if (peek().kind() == Kind.OP && ASSIGN_OPS.contains(peek().text())) {
			ShaderToken op = next();
			Expr value = expression();
			return new Assign(op.text(), target, value, op.pos());
		}

		return target;
	}

	private Expr ternary() throws CompileException {
		Expr condition = binary(0);

		if (peek().is("?")) {
			Pos pos = next().pos();
			Expr then = expression();
			expect(":");
			Expr otherwise = ternary();
			return new Conditional(condition, then, otherwise, pos);
		}

		return condition;
	}

	private Expr binary(int level) throws CompileException {
		if (level == BINARY_LEVELS.length) {
			return unary();
		}

		Expr left = binary(level + 1);

		while (true) {
			ShaderToken token = peek();

			if (token.kind() != Kind.OP || !contains(BINARY_LEVELS[level], token.text())) {
				return left;
			}

			next();
			left = new Binary(token.text(), left, binary(level + 1), token.pos());
		}
	}

	private static boolean contains(String[] ops, String op) {
		for (String candidate : ops) {
			if (candidate.equals(op)) {
				return true;
			}
		}

		return false;
	}

	private Expr unary() throws CompileException {
		ShaderToken token = peek();

		if (token.is("-") || token.is("+") || token.is("!") || token.is("~")) {
			next();
			Expr operand = unary();

			if (token.is("-") && operand instanceof Literal literal && literal.kind() == LiteralKind.INT && literal.value() instanceof Long) {
				return new Literal(LiteralKind.INT, Integer.MIN_VALUE, "-2147483648", token.pos());
			}

			return new Unary(token.text(), operand, token.pos());
		}

		if (token.is("++") || token.is("--")) {
			next();
			return new IncDec(true, token.is("++"), unary(), token.pos());
		}

		if (token.is("(") && peek(1).isIdent() && CAST_TYPES.contains(peek(1).text()) && peek(2).is(")") && startsOperand(peek(3))) {
			next();
			String type = next().text();
			next();
			return new Cast(type, unary(), token.pos());
		}

		return postfix(primary());
	}

	private static boolean startsOperand(ShaderToken token) {
		return switch (token.kind()) {
			case IDENT, INT, FLOAT, COLOR3, COLOR4 -> true;
			case KEYWORD -> token.is("true") || token.is("false") || token.is("new");
			case OP -> token.is("(") || token.is("!") || token.is("~");
			default -> false;
		};
	}

	private Expr postfix(Expr expr) throws CompileException {
		while (true) {
			ShaderToken token = peek();

			if (accept(".")) {
				ShaderToken name = peek();
				String member = identifier("field or method name");

				if (peek().is("(")) {
					expr = new Call(expr, member, args(), name.pos());
				} else {
					expr = new Select(expr, member, name.pos());
				}
			} else if (accept("[")) {
				Expr indexExpr = expression();
				expect("]");
				expr = new Index(expr, indexExpr, token.pos());
			} else if (token.is("++") || token.is("--")) {
				next();
				expr = new IncDec(false, token.is("++"), expr, token.pos());
			} else {
				return expr;
			}
		}
	}

	private List<Expr> args() throws CompileException {
		expect("(");
		List<Expr> args = new ArrayList<>();

		if (!accept(")")) {
			do {
				args.add(expression());
			} while (accept(","));

			expect(")");
		}

		return args;
	}

	private Expr primary() throws CompileException {
		ShaderToken token = peek();
		Pos pos = token.pos();

		switch (token.kind()) {
			case INT -> {
				next();
				return new Literal(LiteralKind.INT, token.value(), token.text(), pos);
			}
			case FLOAT -> {
				next();
				return new Literal(LiteralKind.FLOAT, token.value(), token.text(), pos);
			}
			case COLOR3 -> {
				next();
				return new Literal(LiteralKind.COLOR3, token.value(), token.text(), pos);
			}
			case COLOR4 -> {
				next();
				return new Literal(LiteralKind.COLOR4, token.value(), token.text(), pos);
			}
			case STRING -> {
				next();
				return new Literal(LiteralKind.STRING, token.value(), token.text(), pos);
			}
			case IDENT -> {
				next();

				if (peek().is("(")) {
					return new Call(null, token.text(), args(), pos);
				}

				return new Name(token.text(), pos);
			}
			default -> {
			}
		}

		if (accept("true") || accept("false")) {
			return new Literal(LiteralKind.BOOL, token.is("true"), token.text(), pos);
		}

		if (accept("(")) {
			Expr inner = expression();
			expect(")");
			return inner;
		}

		if (accept("new")) {
			StringBuilder qualified = new StringBuilder(identifier("type"));

			while (peek().is(".") && peek(1).isIdent()) {
				next();
				qualified.append('.').append(next().text());
			}

			String name = qualified.toString();

			if (accept("[")) {
				if (accept("]")) {
					if (!peek().is("{")) {
						throw error(peek(), "Write new " + name + "[size] or new " + name + "[]{values}");
					}

					return new NewArray(name, null, arrayInit().values(), pos);
				}

				Expr size = expression();
				expect("]");
				return new NewArray(name, size, null, pos);
			}

			return new NewRecord(name, args(), pos);
		}

		if (token.is("this")) {
			throw error(token, "Shaders have no 'this'; use field names directly");
		}

		if (token.is("null")) {
			throw error(token, "Shaders have no null");
		}

		throw error(token, "Expected a value but found " + token);
	}
}
