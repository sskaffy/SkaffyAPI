package me.skaffy.client.gui.sfy;

import java.util.List;
import java.util.Set;

final class Ast {
	private Ast() {
	}

	record Unit(String packageName, Pos packagePos, List<Import> imports, ClassDecl type) {
	}

	record Import(String name, boolean wildcard, Pos pos) {
	}

	record Modifiers(Set<String> keywords, List<Annotation> annotations, Pos pos) {
		boolean has(String keyword) {
			return keywords.contains(keyword);
		}

		boolean annotated(String name) {
			return annotations.stream().anyMatch(annotation -> annotation.name().equals(name));
		}
	}

	record Annotation(String name, Pos pos) {
	}

	record TypeRef(String name, List<TypeRef> args, boolean varargs, Pos pos) {
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder(name);

			if (!args.isEmpty()) {
				builder.append('<');

				for (int i = 0; i < args.size(); i++) {
					if (i > 0) {
						builder.append(", ");
					}

					builder.append(args.get(i));
				}

				builder.append('>');
			}

			return builder.append(varargs ? "..." : "").toString();
		}
	}

	sealed interface Member permits Field, Method, Constructor, RecordDecl, EnumDecl {
		Pos pos();
	}

	record ClassDecl(String name, Modifiers modifiers, List<Member> members, Pos pos) {
	}

	record Field(Modifiers modifiers, TypeRef type, String name, Expr init, Pos pos) implements Member {
	}

	record Method(Modifiers modifiers, TypeRef returnType, String name, List<Param> params, Block body, Pos pos) implements Member {
	}

	record Constructor(Modifiers modifiers, String name, List<Param> params, Block body, boolean compact, Pos pos) implements Member {
	}

	record RecordDecl(Modifiers modifiers, String name, List<Param> components, List<Member> members, Pos pos) implements Member {
	}

	record EnumDecl(Modifiers modifiers, String name, List<EnumConstant> constants, List<Member> members, Pos pos) implements Member {
	}

	record EnumConstant(String name, List<Expr> args, Pos pos) {
	}

	record Param(TypeRef type, String name, boolean isFinal, Pos pos) {
	}

	sealed interface Stmt permits Block, LocalVar, ExprStmt, If, While, DoWhile, For, ForEach, Return, Break, Continue, Throw, Try, Switch, Yield, Empty {
		Pos pos();
	}

	record Block(List<Stmt> statements, Pos pos) implements Stmt {
	}

	record LocalVar(boolean isFinal, TypeRef type, List<Declarator> declarators, Pos pos) implements Stmt {
	}

	record Declarator(String name, Expr init, Pos pos) {
	}

	record ExprStmt(Expr expr, Pos pos) implements Stmt {
	}

	record If(Expr condition, Stmt then, Stmt otherwise, Pos pos) implements Stmt {
	}

	record While(Expr condition, Stmt body, Pos pos) implements Stmt {
	}

	record DoWhile(Stmt body, Expr condition, Pos pos) implements Stmt {
	}

	record For(List<Stmt> init, Expr condition, List<Expr> updates, Stmt body, Pos pos) implements Stmt {
	}

	record ForEach(boolean isFinal, TypeRef type, String name, Expr iterable, Stmt body, Pos pos) implements Stmt {
	}

	record Return(Expr value, Pos pos) implements Stmt {
	}

	record Break(Pos pos) implements Stmt {
	}

	record Continue(Pos pos) implements Stmt {
	}

	record Throw(Expr value, Pos pos) implements Stmt {
	}

	record Try(Block body, List<Catch> catches, Block finallyBlock, Pos pos) implements Stmt {
	}

	record Catch(TypeRef type, String name, Block body, Pos pos) {
	}

	record Switch(Expr selector, List<Case> cases, boolean arrows, Pos pos) implements Stmt {
	}

	record Case(List<Expr> labels, boolean isDefault, List<Stmt> body, Expr arrowValue, Pos pos) {
	}

	record Yield(Expr value, Pos pos) implements Stmt {
	}

	record Empty(Pos pos) implements Stmt {
	}

	sealed interface Expr permits Literal, Name, Select, Call, New, Unary, Binary, Assign, IncDec, Conditional, Cast, InstanceOf, Lambda, SwitchExpr, This {
		Pos pos();
	}

	record Literal(Object value, Pos pos) implements Expr {
	}

	record ColorLit(int argb) {
	}

	record PercentLit(double percent) {
	}

	record DurationLit(long millis) {
	}

	record Name(String name, Pos pos) implements Expr {
	}

	record Select(Expr target, String name, Pos pos) implements Expr {
	}

	record Call(Expr target, String name, List<Expr> args, Pos pos) implements Expr {
	}

	record New(TypeRef type, List<Expr> args, Pos pos) implements Expr {
	}

	record Unary(String op, Expr operand, Pos pos) implements Expr {
	}

	record Binary(String op, Expr left, Expr right, Pos pos) implements Expr {
	}

	record Assign(String op, Expr target, Expr value, Pos pos) implements Expr {
	}

	record IncDec(boolean prefix, boolean increment, Expr target, Pos pos) implements Expr {
	}

	record Conditional(Expr condition, Expr then, Expr otherwise, Pos pos) implements Expr {
	}

	record Cast(TypeRef type, Expr operand, Pos pos) implements Expr {
	}

	record InstanceOf(Expr operand, TypeRef type, String binding, Pos pos) implements Expr {
	}

	record Lambda(List<Param> params, Object body, Pos pos) implements Expr {
	}

	record SwitchExpr(Expr selector, List<Case> cases, boolean arrows, Pos pos) implements Expr {
	}

	record This(Pos pos) implements Expr {
	}
}
