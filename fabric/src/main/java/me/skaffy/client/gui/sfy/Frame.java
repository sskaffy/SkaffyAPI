package me.skaffy.client.gui.sfy;

final class Frame {
	static final int NORMAL = 0;
	static final int BREAK = 1;
	static final int CONTINUE = 2;
	static final int RETURN = 3;
	static final int YIELD = 4;

	final Object[] locals;
	final Object self;
	Object[] captured;
	Object result;

	Frame(int size, Object self) {
		this.locals = new Object[size];
		this.self = self;
	}

	@FunctionalInterface
	interface Ev {
		Object ev(Frame frame);
	}

	@FunctionalInterface
	interface Ex {
		int ex(Frame frame);
	}
}
