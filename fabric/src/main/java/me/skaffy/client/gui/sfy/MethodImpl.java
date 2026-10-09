package me.skaffy.client.gui.sfy;

final class MethodImpl {
	final String name;
	final String file;
	final int paramCount;
	private volatile Code code;

	private record Code(int maxLocals, Frame.Ex body) {
	}

	MethodImpl(String name, String file, int paramCount) {
		this.name = name;
		this.file = file;
		this.paramCount = paramCount;
	}

	void set(int maxLocals, Frame.Ex body) {
		this.code = new Code(maxLocals, body);
	}

	Object invoke(Object self, Object[] args) {
		Code current = code;
		Frame frame = new Frame(current.maxLocals, self);
		System.arraycopy(args, 0, frame.locals, 0, args.length);
		current.body.ex(frame);
		return frame.result;
	}
}
