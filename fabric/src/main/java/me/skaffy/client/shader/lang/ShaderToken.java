package me.skaffy.client.shader.lang;

import me.skaffy.client.gui.sfy.Pos;

record ShaderToken(Kind kind, String text, Object value, int line, int column) {
	enum Kind {
		IDENT,
		KEYWORD,
		INT,
		FLOAT,
		STRING,
		COLOR3,
		COLOR4,
		OP,
		EOF
	}

	boolean is(String keywordOrOp) {
		return (kind == Kind.KEYWORD || kind == Kind.OP) && text.equals(keywordOrOp);
	}

	boolean isIdent() {
		return kind == Kind.IDENT;
	}

	Pos pos() {
		return new Pos(line, column);
	}

	@Override
	public String toString() {
		return kind == Kind.EOF ? "end of file" : "'" + text + "'";
	}
}
