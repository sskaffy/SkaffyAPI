package me.skaffy.client.shader.lang;

import java.util.List;
import java.util.Set;

import me.skaffy.client.gui.sfy.Pos;

final class ShaderAst {
	private ShaderAst() {
	}

	record Unit(String packageName, Pos packagePos, List<Import> imports, ClassDecl type) {
	}

	record Import(String name, boolean wildcard, Pos pos) {
	}

	record ClassDecl(String name, List<Annotation> annotations, List<Member> members, Pos pos) {
	}

	record Annotation(String name, List<AnnotationArg> args, Pos pos) {
		AnnotationArg arg(String key) {
			for (AnnotationArg arg : args) {
				if (key.equals(arg.key())) {
					return arg;
				}
			}

			return null;
		}
	}

	record AnnotationArg(String key, Expr value, Pos pos) {
	}

	record TypeRef(String name, boolean array, Pos pos) {
		@Override
		public String toString() {
			return array ? name + "[]" : name;
		}
	}

	sealed interface Member permits Field, Method, RecordDecl {
		Pos pos();
	}

	record Field(List<Annotation> annotations, Set<String> modifiers, TypeRef type, String name, Expr init, Pos pos) implements Member {
	}

	record Method(List<Annotation> annotations, Set<String> modifiers, TypeRef returnType, String name, List<Param> params, Block body, Pos pos) implements Member {
	}

	record RecordDecl(Set<String> modifiers, String name, List<Param> components, Pos pos) implements Member {
	}

	record Param(TypeRef type, String name, Pos pos) {
	}

	sealed interface Stmt permits Block, LocalVar, ExprStmt, If, While, DoWhile, For, ForEach, Return, Break, Continue, Discard, Switch, Empty {
		Pos pos();
	}

	record Block(List<Stmt> statements, Pos pos) implements Stmt {
	}

	record LocalVar(boolean isConst, boolean isFinal, TypeRef type, List<Declarator> declarators, Pos pos) implements Stmt {
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

	record ForEach(TypeRef type, String name, Expr iterable, Stmt body, Pos pos) implements Stmt {
	}

	record Return(Expr value, Pos pos) implements Stmt {
	}

	record Break(Pos pos) implements Stmt {
	}

	record Continue(Pos pos) implements Stmt {
	}

	record Discard(Pos pos) implements Stmt {
	}

	record Switch(Expr selector, List<Case> cases, Pos pos) implements Stmt {
	}

	record Case(List<Expr> labels, boolean isDefault, boolean arrow, List<Stmt> body, Pos pos) {
	}

	record Empty(Pos pos) implements Stmt {
	}

	sealed interface Expr permits Literal, Name, Select, Index, Call, NewRecord, NewArray, ArrayInit, Unary, Binary, Assign, IncDec, Conditional, Cast {
		Pos pos();
	}

	enum LiteralKind {
		INT,
		FLOAT,
		BOOL,
		COLOR3,
		COLOR4,
		STRING
	}

	record Literal(LiteralKind kind, Object value, String text, Pos pos) implements Expr {
	}

	record Name(String name, Pos pos) implements Expr {
	}

	record Select(Expr target, String name, Pos pos) implements Expr {
	}

	record Index(Expr target, Expr index, Pos pos) implements Expr {
	}

	record Call(Expr target, String name, List<Expr> args, Pos pos) implements Expr {
	}

	record NewRecord(String name, List<Expr> args, Pos pos) implements Expr {
	}

	record NewArray(String element, Expr size, List<Expr> values, Pos pos) implements Expr {
	}

	record ArrayInit(List<Expr> values, Pos pos) implements Expr {
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

	record Cast(String type, Expr operand, Pos pos) implements Expr {
	}
}
