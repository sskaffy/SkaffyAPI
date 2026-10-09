package me.skaffy.client.gui.sfy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.skaffy.client.gui.sfy.Ast.Annotation;
import me.skaffy.client.gui.sfy.Ast.Assign;
import me.skaffy.client.gui.sfy.Ast.Binary;
import me.skaffy.client.gui.sfy.Ast.Block;
import me.skaffy.client.gui.sfy.Ast.Break;
import me.skaffy.client.gui.sfy.Ast.Call;
import me.skaffy.client.gui.sfy.Ast.Case;
import me.skaffy.client.gui.sfy.Ast.Cast;
import me.skaffy.client.gui.sfy.Ast.Catch;
import me.skaffy.client.gui.sfy.Ast.ClassDecl;
import me.skaffy.client.gui.sfy.Ast.Conditional;
import me.skaffy.client.gui.sfy.Ast.Constructor;
import me.skaffy.client.gui.sfy.Ast.Continue;
import me.skaffy.client.gui.sfy.Ast.Declarator;
import me.skaffy.client.gui.sfy.Ast.DoWhile;
import me.skaffy.client.gui.sfy.Ast.Empty;
import me.skaffy.client.gui.sfy.Ast.EnumConstant;
import me.skaffy.client.gui.sfy.Ast.EnumDecl;
import me.skaffy.client.gui.sfy.Ast.Expr;
import me.skaffy.client.gui.sfy.Ast.ExprStmt;
import me.skaffy.client.gui.sfy.Ast.Field;
import me.skaffy.client.gui.sfy.Ast.For;
import me.skaffy.client.gui.sfy.Ast.ForEach;
import me.skaffy.client.gui.sfy.Ast.If;
import me.skaffy.client.gui.sfy.Ast.IncDec;
import me.skaffy.client.gui.sfy.Ast.InstanceOf;
import me.skaffy.client.gui.sfy.Ast.Lambda;
import me.skaffy.client.gui.sfy.Ast.Literal;
import me.skaffy.client.gui.sfy.Ast.LocalVar;
import me.skaffy.client.gui.sfy.Ast.Member;
import me.skaffy.client.gui.sfy.Ast.Method;
import me.skaffy.client.gui.sfy.Ast.Modifiers;
import me.skaffy.client.gui.sfy.Ast.Name;
import me.skaffy.client.gui.sfy.Ast.New;
import me.skaffy.client.gui.sfy.Ast.Param;
import me.skaffy.client.gui.sfy.Ast.RecordDecl;
import me.skaffy.client.gui.sfy.Ast.Return;
import me.skaffy.client.gui.sfy.Ast.Select;
import me.skaffy.client.gui.sfy.Ast.Stmt;
import me.skaffy.client.gui.sfy.Ast.Switch;
import me.skaffy.client.gui.sfy.Ast.SwitchExpr;
import me.skaffy.client.gui.sfy.Ast.This;
import me.skaffy.client.gui.sfy.Ast.Throw;
import me.skaffy.client.gui.sfy.Ast.Try;
import me.skaffy.client.gui.sfy.Ast.TypeRef;
import me.skaffy.client.gui.sfy.Ast.Unary;
import me.skaffy.client.gui.sfy.Ast.Unit;
import me.skaffy.client.gui.sfy.Ast.While;
import me.skaffy.client.gui.sfy.Ast.Yield;
import me.skaffy.client.gui.sfy.Token.Kind;

final class Parser {
	private static final Set<String> PRIMITIVES = Set.of("int", "long", "double", "boolean", "char");
	private static final Set<String> MODIFIERS = Set.of("public", "private", "protected", "static", "final");
	private static final Set<String> ASSIGN_OPS = Set.of("=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=");

	private final String file;
	private final List<Token> tokens;
	private int index;
	private int pendingGreater;

	private Parser(String file, List<Token> tokens) {
		this.file = file;
		this.tokens = tokens;
	}

	static Unit parseFile(String file, String source) throws CompileException {
		Parser parser = new Parser(file, Lexer.tokenize(file, source));
		return parser.unit();
	}

	static Method parseMethod(String file, String source, String className) throws CompileException {
		Parser parser = new Parser(file, Lexer.tokenize(file, source));
		Member member = parser.member(className, false);

		if (!(member instanceof Method method)) {
			throw parser.error(member.pos(), "Expected one method");
		}

		parser.expectEnd();
		return method;
	}

	private void expectEnd() throws CompileException {
		if (peek().kind() != Kind.EOF) {
			throw error(peek(), "Expected the end of the file, found " + peek());
		}
	}

	private Unit unit() throws CompileException {
		if (!peek().is("package")) {
			throw error(peek(), "A .sfy file starts with its package, for example: package shop;");
		}

		Pos packagePos = next().pos();
		StringBuilder name = new StringBuilder(identifier("package name"));

		while (accept(".")) {
			name.append('.').append(identifier("package name"));
		}

		expect(";");

		List<Ast.Import> imports = new ArrayList<>();

		while (peek().is("import")) {
			Pos importPos = next().pos();

			if (peek().is("static")) {
				throw error(peek(), "There are no static imports in .sfy");
			}

			StringBuilder imported = new StringBuilder(identifier("import"));
			boolean wildcard = false;

			while (accept(".")) {
				if (accept("*")) {
					wildcard = true;
					break;
				}

				imported.append('.').append(identifier("import"));
			}

			expect(";");
			imports.add(new Ast.Import(imported.toString(), wildcard, importPos));
		}

		Modifiers modifiers = modifiers();

		if (peek().is("record") || peek().is("enum") || peek().is("interface")) {
			throw error(peek(), "A .sfy file has one class; records and enums go inside it");
		}

		expect("class");
		Token nameToken = peek();
		String className = identifier("class name");

		if (peek().is("extends") || peek().is("implements")) {
			throw error(peek(), "Classes can't extend or implement anything in .sfy");
		}

		if (peek().is("<")) {
			throw error(peek(), "The class can't have type parameters");
		}

		List<Member> members = classBody(className, false);
		expectEnd();
		return new Unit(name.toString(), packagePos, imports, new ClassDecl(className, modifiers, members, nameToken.pos()));
	}

	private List<Member> classBody(String className, boolean inRecord) throws CompileException {
		expect("{");
		List<Member> members = new ArrayList<>();

		while (!accept("}")) {
			if (peek().kind() == Kind.EOF) {
				throw error(peek(), "Missing } at the end of " + className);
			}

			if (accept(";")) {
				continue;
			}

			members.add(member(className, inRecord));
		}

		return members;
	}

	private Member member(String className, boolean inRecord) throws CompileException {
		Modifiers modifiers = modifiers();
		Token start = peek();

		if (accept("record")) {
			String name = identifier("record name");
			rejectTypeParams();
			List<Param> components = params(false);

			if (peek().is("implements")) {
				throw error(peek(), "Records can't implement anything in .sfy");
			}

			return new RecordDecl(modifiers, name, components, classBody(name, true), start.pos());
		}

		if (accept("enum")) {
			String name = identifier("enum name");

			if (peek().is("implements")) {
				throw error(peek(), "Enums can't implement anything in .sfy");
			}

			return enumBody(modifiers, name, start.pos());
		}

		if (peek().is("class") || peek().is("interface")) {
			throw error(peek(), "Only records and enums can go inside the class for now");
		}

		if (peek().is("<")) {
			throw error(peek(), "Methods can't have type parameters in .sfy");
		}

		if (peek().isIdent() && peek().text().equals(className) && peek(1).is("(")) {
			next();
			List<Param> params = params(false);
			rejectThrows();
			return new Ast.Constructor(modifiers, className, params, block(), false, start.pos());
		}

		if (inRecord && peek().isIdent() && peek().text().equals(className) && peek(1).is("{")) {
			next();
			return new Ast.Constructor(modifiers, className, List.of(), block(), true, start.pos());
		}

		TypeRef type = null;

		if (!accept("void")) {
			type = type(false);
		}

		Token nameToken = peek();
		String name = identifier("name");

		if (peek().is("(")) {
			List<Param> params = params(false);

			if (peek().is("[")) {
				throw error(peek(), "Arrays don't exist in .sfy, use List");
			}

			rejectThrows();

			if (accept(";")) {
				throw error(nameToken, "Method " + name + " needs a body { }");
			}

			return new Method(modifiers, type, name, params, block(), nameToken.pos());
		}

		if (type == null) {
			throw error(nameToken, "Fields can't be void");
		}

		Expr init = null;

		if (accept("=")) {
			init = expression();
		}

		if (peek().is(",")) {
			throw error(peek(), "Declare one field per line in .sfy");
		}

		expect(";");
		return new Field(modifiers, type, name, init, nameToken.pos());
	}

	private Member enumBody(Modifiers modifiers, String name, Pos pos) throws CompileException {
		expect("{");
		List<EnumConstant> constants = new ArrayList<>();

		while (peek().isIdent()) {
			Token constant = next();
			List<Expr> args = peek().is("(") ? arguments() : List.of();

			if (peek().is("{")) {
				throw error(peek(), "Enum constants can't have their own bodies in .sfy");
			}

			constants.add(new EnumConstant(constant.text(), args, constant.pos()));

			if (!accept(",")) {
				break;
			}
		}

		List<Member> members = new ArrayList<>();

		if (accept(";")) {
			while (!accept("}")) {
				if (peek().kind() == Kind.EOF) {
					throw error(peek(), "Missing } at the end of " + name);
				}

				if (accept(";")) {
					continue;
				}

				members.add(member(name, false));
			}
		} else {
			expect("}");
		}

		return new EnumDecl(modifiers, name, constants, members, pos);
	}

	private void rejectTypeParams() throws CompileException {
		if (peek().is("<")) {
			throw error(peek(), "Records can't have type parameters in .sfy");
		}
	}

	private void rejectThrows() throws CompileException {
		if (peek().is("throws")) {
			throw error(peek(), "No 'throws' needed in .sfy, every exception can be thrown anywhere");
		}
	}

	private Modifiers modifiers() throws CompileException {
		Set<String> keywords = new LinkedHashSet<>();
		List<Annotation> annotations = new ArrayList<>();
		Pos pos = peek().pos();

		while (true) {
			Token token = peek();

			if (token.kind() == Kind.KEYWORD && MODIFIERS.contains(token.text())) {
				if (!keywords.add(token.text())) {
					throw error(token, "Repeated modifier " + token.text());
				}

				next();
			} else if (token.kind() == Kind.KEYWORD && Set.of("abstract", "synchronized", "volatile", "transient", "native", "strictfp").contains(token.text())) {
				throw error(token, "'" + token.text() + "' doesn't exist in .sfy");
			} else if (token.is("@")) {
				next();
				Token name = peek();
				annotations.add(new Annotation(identifier("annotation name"), name.pos()));

				if (peek().is("(")) {
					throw error(peek(), "Annotations don't take arguments in .sfy");
				}
			} else {
				return new Modifiers(keywords, annotations, pos);
			}
		}
	}

	private List<Param> params(boolean lambda) throws CompileException {
		expect("(");
		List<Param> params = new ArrayList<>();

		if (accept(")")) {
			return params;
		}

		do {
			boolean isFinal = accept("final");
			Token start = peek();
			TypeRef type = accept("var") ? null : type(true);
			String name = identifier("parameter name");

			if (peek().is("[")) {
				throw error(peek(), "Arrays don't exist in .sfy, use List");
			}

			params.add(new Param(type, name, isFinal, start.pos()));
		} while (accept(","));

		expect(")");
		return params;
	}

	private TypeRef type(boolean allowVarargs) throws CompileException {
		Token start = peek();

		if (start.kind() == Kind.KEYWORD && Set.of("float", "byte", "short").contains(start.text())) {
			throw error(start, "There is no " + start.text() + " in .sfy, use " + (start.text().equals("float") ? "double" : "int"));
		}

		String name;

		if (start.kind() == Kind.KEYWORD && PRIMITIVES.contains(start.text())) {
			name = next().text();
		} else if (start.isIdent()) {
			StringBuilder builder = new StringBuilder(next().text());

			while (peek().is(".") && peek(1).isIdent()) {
				next();
				builder.append('.').append(next().text());
			}

			name = builder.toString();
		} else {
			throw error(start, "Expected a type, found " + start);
		}

		List<TypeRef> args = new ArrayList<>();

		if (peek().is("<") && pendingGreater == 0) {
			next();

			if (!isCloseAngle()) {
				do {
					if (peek().is("?")) {
						throw error(peek(), "Wildcards (?) don't exist in .sfy");
					}

					args.add(type(false));
				} while (accept(","));
			}

			closeAngle();
		}

		if (peek().is("[") && peek(1).is("]")) {
			String element = switch (name) {
				case "int" -> "Integer";
				case "long" -> "Long";
				case "double" -> "Double";
				case "boolean" -> "Boolean";
				case "char" -> "Character";
				default -> name;
			};
			throw error(peek(), "Arrays don't exist in .sfy, use List<" + element + ">");
		}

		boolean varargs = false;

		if (allowVarargs && accept("...")) {
			varargs = true;
		}

		return new TypeRef(name, args, varargs, start.pos());
	}

	private boolean isCloseAngle() {
		return pendingGreater > 0 || peek().is(">") || peek().is(">>") || peek().is(">>>");
	}

	private void closeAngle() throws CompileException {
		if (pendingGreater > 0) {
			pendingGreater--;

			if (pendingGreater == 0) {
				next();
			}

			return;
		}

		Token token = peek();

		switch (token.text()) {
			case ">" -> next();
			case ">>" -> pendingGreater = 1;
			case ">>>" -> pendingGreater = 2;
			default -> throw error(token, "Expected > to close the type arguments, found " + token);
		}
	}

	private Block block() throws CompileException {
		Token start = expect("{");
		List<Stmt> statements = new ArrayList<>();

		while (!accept("}")) {
			if (peek().kind() == Kind.EOF) {
				throw error(start, "This { is never closed");
			}

			statements.add(statement());
		}

		return new Block(statements, start.pos());
	}

	private Stmt statement() throws CompileException {
		Token start = peek();

		if (start.is("{")) {
			return block();
		}

		if (accept(";")) {
			return new Empty(start.pos());
		}

		if (accept("if")) {
			expect("(");
			Expr condition = expression();
			expect(")");
			Stmt then = statement();
			Stmt otherwise = accept("else") ? statement() : null;
			return new If(condition, then, otherwise, start.pos());
		}

		if (accept("while")) {
			expect("(");
			Expr condition = expression();
			expect(")");
			return new While(condition, statement(), start.pos());
		}

		if (accept("do")) {
			Stmt body = statement();
			expect("while");
			expect("(");
			Expr condition = expression();
			expect(")");
			expect(";");
			return new DoWhile(body, condition, start.pos());
		}

		if (accept("for")) {
			return forStatement(start);
		}

		if (accept("return")) {
			Expr value = peek().is(";") ? null : expression();
			expect(";");
			return new Return(value, start.pos());
		}

		if (accept("break")) {
			if (peek().isIdent()) {
				throw error(peek(), "Labels don't exist in .sfy");
			}

			expect(";");
			return new Break(start.pos());
		}

		if (accept("continue")) {
			if (peek().isIdent()) {
				throw error(peek(), "Labels don't exist in .sfy");
			}

			expect(";");
			return new Continue(start.pos());
		}

		if (accept("throw")) {
			Expr value = expression();
			expect(";");
			return new Throw(value, start.pos());
		}

		if (accept("try")) {
			return tryStatement(start);
		}

		if (peek().is("switch")) {
			next();
			SwitchParts parts = switchParts();
			return new Switch(parts.selector, parts.cases, parts.arrows, start.pos());
		}

		if (start.isIdent() && start.text().equals("yield") && !ASSIGN_OPS.contains(peek(1).text()) && !peek(1).is(".") && !peek(1).is("++") && !peek(1).is("--")) {
			next();
			Expr value = expression();
			expect(";");
			return new Yield(value, start.pos());
		}

		if (peek().is("class") || peek().is("record") || peek().is("enum") || peek().is("interface")) {
			throw error(peek(), "Types can't be declared inside methods");
		}

		if (peek().is("assert")) {
			throw error(peek(), "'assert' doesn't exist in .sfy, use if and throw");
		}

		if (isLocalVarStart()) {
			LocalVar declaration = localVar();
			expect(";");
			return declaration;
		}

		Expr expr = expression();

		if (!(expr instanceof Assign || expr instanceof IncDec || expr instanceof Call || expr instanceof New)) {
			throw error(start, "This is not a statement (only assignments, ++, --, method calls and new can stand alone)");
		}

		expect(";");
		return new ExprStmt(expr, start.pos());
	}

	private Stmt forStatement(Token start) throws CompileException {
		expect("(");
		int save = index;
		int saveGreater = pendingGreater;
		boolean isFinal = accept("final");

		if (isLocalVarStart() || isFinal) {
			TypeRef type = accept("var") ? null : type(false);

			if (peek().isIdent() && peek(1).is(":")) {
				String name = next().text();
				next();
				Expr iterable = expression();
				expect(")");
				return new ForEach(isFinal, type, name, iterable, statement(), start.pos());
			}

			index = save;
			pendingGreater = saveGreater;
		}

		List<Stmt> init = new ArrayList<>();

		if (!peek().is(";")) {
			if (isLocalVarStart() || peek().is("final")) {
				init.add(localVar());
			} else {
				do {
					Token exprStart = peek();
					init.add(new ExprStmt(expression(), exprStart.pos()));
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
		return new For(init, condition, updates, statement(), start.pos());
	}

	private Stmt tryStatement(Token start) throws CompileException {
		if (peek().is("(")) {
			throw error(peek(), "try-with-resources doesn't exist in .sfy");
		}

		Block body = block();
		List<Catch> catches = new ArrayList<>();

		while (peek().is("catch")) {
			Token catchToken = next();
			expect("(");
			TypeRef type = type(false);

			if (peek().is("|")) {
				throw error(peek(), "Catch Exception, it catches everything");
			}

			String name = identifier("exception name");
			expect(")");
			catches.add(new Catch(type, name, block(), catchToken.pos()));
		}

		Block finallyBlock = accept("finally") ? block() : null;

		if (catches.isEmpty() && finallyBlock == null) {
			throw error(start, "try needs a catch or a finally");
		}

		return new Try(body, catches, finallyBlock, start.pos());
	}

	private record SwitchParts(Expr selector, List<Case> cases, boolean arrows) {
	}

	private SwitchParts switchParts() throws CompileException {
		expect("(");
		Expr selector = expression();
		expect(")");
		expect("{");
		List<Case> cases = new ArrayList<>();
		Boolean arrows = null;

		while (!accept("}")) {
			Token caseToken = peek();
			boolean isDefault;
			List<Expr> labels = new ArrayList<>();

			if (accept("default")) {
				isDefault = true;
			} else if (accept("case")) {
				isDefault = false;

				do {
					if (peek().is("null")) {
						throw error(peek(), "case null doesn't exist in .sfy");
					}

					labels.add(ternary());
				} while (accept(","));
			} else {
				throw error(caseToken, "Expected case or default, found " + caseToken);
			}

			boolean arrow;

			if (accept("->")) {
				arrow = true;
			} else {
				expect(":");
				arrow = false;
			}

			if (arrows == null) {
				arrows = arrow;
			} else if (arrows != arrow) {
				throw error(caseToken, "Don't mix case -> and case : in one switch");
			}

			if (arrow) {
				Token bodyStart = peek();

				if (bodyStart.is("{")) {
					cases.add(new Case(labels, isDefault, List.of(block()), null, caseToken.pos()));
				} else if (bodyStart.is("throw")) {
					cases.add(new Case(labels, isDefault, List.of(statement()), null, caseToken.pos()));
				} else {
					Expr value = expression();
					expect(";");
					cases.add(new Case(labels, isDefault, List.of(), value, caseToken.pos()));
				}
			} else {
				List<Stmt> body = new ArrayList<>();

				while (!peek().is("case") && !peek().is("default") && !peek().is("}")) {
					if (peek().kind() == Kind.EOF) {
						throw error(peek(), "Switch is never closed with }");
					}

					body.add(statement());
				}

				cases.add(new Case(labels, isDefault, body, null, caseToken.pos()));
			}
		}

		return new SwitchParts(selector, cases, arrows == null || arrows);
	}

	private boolean isLocalVarStart() {
		Token token = peek();

		if (token.is("final") || token.is("var") && peek(1).isIdent()) {
			return true;
		}

		if (token.kind() == Kind.KEYWORD && (PRIMITIVES.contains(token.text()) || Set.of("float", "byte", "short").contains(token.text()))) {
			return !peek(1).is(".");
		}

		if (!token.isIdent()) {
			return false;
		}

		int save = index;
		int saveGreater = pendingGreater;

		try {
			type(false);
			return peek().isIdent() && (peek(1).is("=") || peek(1).is(";") || peek(1).is(",") || peek(1).is(":"));
		} catch (CompileException e) {
			return false;
		} finally {
			index = save;
			pendingGreater = saveGreater;
		}
	}

	private LocalVar localVar() throws CompileException {
		Token start = peek();
		boolean isFinal = accept("final");
		TypeRef type = accept("var") ? null : type(false);
		List<Declarator> declarators = new ArrayList<>();

		do {
			Token name = peek();
			String variable = identifier("variable name");

			if (peek().is("[")) {
				throw error(peek(), "Arrays don't exist in .sfy, use List");
			}

			Expr init = accept("=") ? expression() : null;
			declarators.add(new Declarator(variable, init, name.pos()));
		} while (accept(","));

		return new LocalVar(isFinal, type, declarators, start.pos());
	}

	private Expr expression() throws CompileException {
		if (isLambdaStart()) {
			return lambda();
		}

		Expr target = ternary();
		Token op = peek();

		if (op.kind() == Kind.OP && ASSIGN_OPS.contains(op.text())) {
			next();

			if (!(target instanceof Name || target instanceof Select)) {
				throw error(op, "Only variables and fields can be assigned");
			}

			return new Assign(op.text(), target, expression(), op.pos());
		}

		return target;
	}

	private boolean isLambdaStart() {
		if (peek().isIdent() && peek(1).is("->")) {
			return true;
		}

		if (!peek().is("(")) {
			return false;
		}

		int depth = 0;

		for (int i = index; i < tokens.size(); i++) {
			Token token = tokens.get(i);

			if (token.is("(")) {
				depth++;
			} else if (token.is(")")) {
				depth--;

				if (depth == 0) {
					return i + 1 < tokens.size() && tokens.get(i + 1).is("->");
				}
			} else if (token.kind() == Kind.EOF || token.is(";") || token.is("{") || token.is("}")) {
				return false;
			}
		}

		return false;
	}

	private Expr lambda() throws CompileException {
		Token start = peek();
		List<Param> params;

		if (peek().isIdent()) {
			Token name = next();
			params = List.of(new Param(null, name.text(), false, name.pos()));
		} else {
			boolean untyped = peek(1).isIdent() && (peek(2).is(",") || peek(2).is(")"));

			if (untyped) {
				expect("(");
				params = new ArrayList<>();

				do {
					Token name = peek();
					params.add(new Param(null, identifier("parameter name"), false, name.pos()));
				} while (accept(","));

				expect(")");
			} else {
				params = params(true);
			}
		}

		expect("->");
		Object body = peek().is("{") ? block() : expression();
		return new Lambda(params, body, start.pos());
	}

	private Expr ternary() throws CompileException {
		Expr condition = binary(0);

		if (peek().is("?")) {
			Token question = next();
			Expr then = isLambdaStart() ? lambda() : ternary();
			expect(":");
			Expr otherwise = isLambdaStart() ? lambda() : ternary();
			return new Conditional(condition, then, otherwise, question.pos());
		}

		return condition;
	}

	private static final List<Set<String>> LEVELS = List.of(
			Set.of("||"),
			Set.of("&&"),
			Set.of("|"),
			Set.of("^"),
			Set.of("&"),
			Set.of("==", "!="),
			Set.of("<", ">", "<=", ">=", "instanceof"),
			Set.of("<<", ">>", ">>>"),
			Set.of("+", "-"),
			Set.of("*", "/", "%"));

	private Expr binary(int level) throws CompileException {
		if (level == LEVELS.size()) {
			return unary();
		}

		Expr left = binary(level + 1);

		while (true) {
			Token op = peek();

			if (!(op.kind() == Kind.OP || op.is("instanceof")) || !LEVELS.get(level).contains(op.text())) {
				return left;
			}

			next();

			if (op.text().equals("instanceof")) {
				if (peek().is("final")) {
					next();
				}

				TypeRef type = type(false);
				String binding = peek().isIdent() ? next().text() : null;
				left = new InstanceOf(left, type, binding, op.pos());
			} else {
				left = new Binary(op.text(), left, binary(level + 1), op.pos());
			}
		}
	}

	private Expr unary() throws CompileException {
		Token op = peek();

		if (op.is("-") && peek(1).kind() == Kind.INT && peek(1).value() instanceof Long) {
			next();
			Token literal = next();
			return postfix(new Literal(Integer.MIN_VALUE, literal.pos()));
		}

		if (op.is("+") || op.is("-") || op.is("!") || op.is("~")) {
			next();
			return new Unary(op.text(), unary(), op.pos());
		}

		if (op.is("++") || op.is("--")) {
			next();
			Expr target = unary();
			return new IncDec(true, op.text().equals("++"), target, op.pos());
		}

		if (op.is("(") && isCast()) {
			next();
			TypeRef type = type(false);
			expect(")");
			Expr operand = isLambdaStart() ? lambda() : unary();
			return new Cast(type, operand, op.pos());
		}

		return postfix(primary());
	}

	private boolean isCast() {
		int save = index;
		int saveGreater = pendingGreater;

		try {
			next();
			Token first = peek();
			boolean primitive = first.kind() == Kind.KEYWORD && PRIMITIVES.contains(first.text());

			if (!primitive && !first.isIdent()) {
				return false;
			}

			type(false);

			if (!peek().is(")")) {
				return false;
			}

			next();

			if (primitive) {
				return true;
			}

			Token after = peek();
			return after.isIdent() || after.is("(") || after.is("!") || after.is("~") || after.is("this") || after.is("new")
					|| after.is("true") || after.is("false") || after.is("null") || after.is("switch")
					|| after.kind() == Kind.INT || after.kind() == Kind.LONG || after.kind() == Kind.DOUBLE
					|| after.kind() == Kind.CHAR || after.kind() == Kind.STRING || after.kind() == Kind.COLOR
					|| after.kind() == Kind.PERCENT || after.kind() == Kind.DURATION;
		} catch (CompileException e) {
			return false;
		} finally {
			index = save;
			pendingGreater = saveGreater;
		}
	}

	private Expr postfix(Expr expr) throws CompileException {
		while (true) {
			Token token = peek();

			if (token.is(".")) {
				next();

				if (peek().is("<")) {
					throw error(peek(), "Explicit type arguments (x.<T>m()) don't exist in .sfy");
				}

				if (peek().is("new") || peek().is("this") || peek().is("class")) {
					throw error(peek(), "'." + peek().text() + "' doesn't exist in .sfy");
				}

				Token name = peek();
				String member = identifier("member name");

				if (peek().is("(")) {
					expr = new Call(expr, member, arguments(), name.pos());
				} else {
					expr = new Select(expr, member, name.pos());
				}
			} else if (token.is("++") || token.is("--")) {
				next();
				expr = new IncDec(false, token.text().equals("++"), expr, token.pos());
			} else if (token.is("::")) {
				throw error(token, "Method references (::) don't exist in .sfy, use a lambda: x -> ...");
			} else if (token.is("[")) {
				throw error(token, "Arrays don't exist in .sfy, use list.get(i)");
			} else {
				return expr;
			}
		}
	}

	private Expr primary() throws CompileException {
		Token token = next();

		switch (token.kind()) {
			case INT, LONG, DOUBLE, CHAR, STRING -> {
				if (token.value() instanceof Long value && token.kind() == Kind.INT) {
					throw error(token, "Number " + value + " is too big for an int");
				}

				return new Literal(token.value(), token.pos());
			}
			case COLOR -> {
				return new Literal(new Ast.ColorLit((Integer) token.value()), token.pos());
			}
			case PERCENT -> {
				return new Literal(new Ast.PercentLit((Double) token.value()), token.pos());
			}
			case DURATION -> {
				return new Literal(new Ast.DurationLit((Long) token.value()), token.pos());
			}
			case IDENT -> {
				if (peek().is("(")) {
					return new Call(null, token.text(), arguments(), token.pos());
				}

				return new Name(token.text(), token.pos());
			}
			case KEYWORD -> {
				switch (token.text()) {
					case "true" -> {
						return new Literal(Boolean.TRUE, token.pos());
					}
					case "false" -> {
						return new Literal(Boolean.FALSE, token.pos());
					}
					case "null" -> {
						return new Literal(null, token.pos());
					}
					case "this" -> {
						if (peek().is("(")) {
							throw error(token, "Calling another constructor with this(...) doesn't exist in .sfy");
						}

						return new This(token.pos());
					}
					case "new" -> {
						TypeRef type = type(false);

						if (peek().is("[")) {
							throw error(peek(), "Arrays don't exist in .sfy, use List");
						}

						List<Expr> args = arguments();

						if (peek().is("{")) {
							throw error(peek(), "Anonymous classes don't exist in .sfy, use a lambda");
						}

						return new New(type, args, token.pos());
					}
					case "switch" -> {
						SwitchParts parts = switchParts();
						return new SwitchExpr(parts.selector, parts.cases, parts.arrows, token.pos());
					}
					case "int", "long", "double", "boolean", "char" -> {
						if (peek().is(".")) {
							throw error(token, "'" + token.text() + "' has no members");
						}

						throw error(token, "Unexpected " + token);
					}
					case "super" -> throw error(token, "'super' doesn't exist in .sfy");
					default -> throw error(token, "Unexpected " + token);
				}
			}
			case OP -> {
				if (token.is("(")) {
					Expr inner = expression();
					expect(")");
					return inner;
				}

				throw error(token, "Unexpected " + token);
			}
			case EOF -> throw error(token, "The file ends in the middle of an expression");
		}

		throw error(token, "Unexpected " + token);
	}

	private List<Expr> arguments() throws CompileException {
		expect("(");
		List<Expr> args = new ArrayList<>();

		if (accept(")")) {
			return args;
		}

		do {
			args.add(expression());
		} while (accept(","));

		expect(")");
		return args;
	}

	private Token peek() {
		return tokens.get(Math.min(index, tokens.size() - 1));
	}

	private Token peek(int offset) {
		return tokens.get(Math.min(index + offset, tokens.size() - 1));
	}

	private Token next() {
		Token token = peek();

		if (index < tokens.size() - 1) {
			index++;
		}

		return token;
	}

	private boolean accept(String text) {
		if (pendingGreater > 0) {
			return false;
		}

		if (peek().is(text)) {
			next();
			return true;
		}

		return false;
	}

	private Token expect(String text) throws CompileException {
		Token token = peek();

		if (pendingGreater > 0 || !token.is(text)) {
			throw error(token, "Expected '" + text + "', found " + token);
		}

		return next();
	}

	private String identifier(String what) throws CompileException {
		Token token = peek();

		if (!token.isIdent()) {
			if (token.kind() == Kind.KEYWORD) {
				throw error(token, "'" + token.text() + "' is a keyword and can't be a " + what);
			}

			throw error(token, "Expected a " + what + ", found " + token);
		}

		return next().text();
	}

	private CompileException error(Token token, String message) {
		return error(token.pos(), message);
	}

	private CompileException error(Pos pos, String message) {
		return new CompileException(List.of(new CompileError(file, pos, message)));
	}
}
