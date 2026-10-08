package me.skaffy.client.gui.sfy;

@FunctionalInterface
public interface Invoker {
	Object call(Object self, Object[] args) throws Exception;
}
