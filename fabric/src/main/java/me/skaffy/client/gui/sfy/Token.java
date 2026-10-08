package me.skaffy.client.gui.sfy;

record Token(Kind kind, String text, Object value, int line, int column) {
	enum Kind {
		IDENT,
		KEYWORD,
		INT,
		LONG,
		DOUBLE,
		CHAR,
		STRING,
		COLOR,
		PERCENT,
		DURATION,
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
