package me.skaffy.client.shader.lang;

import java.util.Set;
import java.util.function.Function;

import me.skaffy.client.gui.sfy.CompileException;

import org.jspecify.annotations.Nullable;

public final class ShaderCompiler {
	private ShaderCompiler() {
	}

	public record Result(@Nullable ShaderModule module, @Nullable CompileException error, Set<String> dependencies, Set<String> missing) {
	}

	public static Result compile(String className, String source, Function<String, String> sources) {
		String file = className.substring(className.lastIndexOf('.') + 1) + ".sfy";
		ShaderChecker.Classes classes = new ShaderChecker.Classes(sources);

		try {
			ShaderAst.Unit unit = ShaderParser.parseFile(file, source);
			ShaderChecker.Checked checked = ShaderChecker.check(file, className, unit, classes);
			return new Result(ShaderAssembler.assemble(checked, classes.dependencies), null, Set.copyOf(classes.dependencies), Set.of());
		} catch (CompileException e) {
			return new Result(null, e, Set.copyOf(classes.dependencies), Set.copyOf(classes.missing));
		}
	}

	public static String frameBlock() {
		return ShaderAssembler.frameBlock();
	}
}
