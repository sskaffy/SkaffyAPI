package me.skaffy.client.gui.sfy;

import java.util.List;

public final class SfyMethod {
	final MethodInfo info;

	SfyMethod(MethodInfo info) {
		this.info = info;
	}

	public String name() {
		return info.name;
	}

	public boolean isPublic() {
		return info.isPublic;
	}

	public boolean isGui() {
		return info.isGui;
	}

	public boolean isHud() {
		return info.isHud;
	}

	public boolean isStatic() {
		return info.isStatic;
	}

	public int paramCount() {
		return info.params.size();
	}

	public boolean returnsValue() {
		return info.returnType != Type.VOID;
	}

	public List<String> paramNames() {
		return info.paramNames;
	}

	public String signature() {
		return info.signature();
	}

	@Override
	public String toString() {
		return info.toString();
	}
}
