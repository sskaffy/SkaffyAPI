package me.skaffy.client.shader.lang;

import java.util.List;

public sealed interface ShType {
	ShType VOID = new VoidType();
	ShType FLOAT = new Scalar(Base.FLOAT);
	ShType INT = new Scalar(Base.INT);
	ShType BOOL = new Scalar(Base.BOOL);
	ShType VEC2 = new Vector(Base.FLOAT, 2);
	ShType VEC3 = new Vector(Base.FLOAT, 3);
	ShType VEC4 = new Vector(Base.FLOAT, 4);
	ShType IVEC2 = new Vector(Base.INT, 2);
	ShType IVEC3 = new Vector(Base.INT, 3);
	ShType IVEC4 = new Vector(Base.INT, 4);
	ShType BVEC2 = new Vector(Base.BOOL, 2);
	ShType BVEC3 = new Vector(Base.BOOL, 3);
	ShType BVEC4 = new Vector(Base.BOOL, 4);
	ShType MAT2 = new Matrix(2);
	ShType MAT3 = new Matrix(3);
	ShType MAT4 = new Matrix(4);
	ShType TEXTURE = new TextureType();

	enum Base {
		FLOAT,
		INT,
		BOOL
	}

	String name();

	String glsl();

	default int components() {
		return 0;
	}

	default Base base() {
		return null;
	}

	default boolean isNumeric() {
		return base() == Base.FLOAT || base() == Base.INT;
	}

	default boolean isBasic() {
		return base() != null;
	}

	static ShType scalar(Base base) {
		return switch (base) {
			case FLOAT -> FLOAT;
			case INT -> INT;
			case BOOL -> BOOL;
		};
	}

	static ShType vector(Base base, int size) {
		return size == 1 ? scalar(base) : new Vector(base, size);
	}

	static ShType withBase(ShType type, Base base) {
		return switch (type) {
			case Scalar ignored -> scalar(base);
			case Vector vector -> new Vector(base, vector.size());
			default -> type;
		};
	}

	static ShType builtin(String name) {
		return switch (name) {
			case "float" -> FLOAT;
			case "int" -> INT;
			case "bool" -> BOOL;
			case "vec2" -> VEC2;
			case "vec3" -> VEC3;
			case "vec4" -> VEC4;
			case "ivec2" -> IVEC2;
			case "ivec3" -> IVEC3;
			case "ivec4" -> IVEC4;
			case "bvec2" -> BVEC2;
			case "bvec3" -> BVEC3;
			case "bvec4" -> BVEC4;
			case "mat2" -> MAT2;
			case "mat3" -> MAT3;
			case "mat4" -> MAT4;
			case "Texture" -> TEXTURE;
			default -> null;
		};
	}

	record VoidType() implements ShType {
		@Override
		public String name() {
			return "void";
		}

		@Override
		public String glsl() {
			return "void";
		}
	}

	record Scalar(Base kind) implements ShType {
		@Override
		public String name() {
			return switch (kind) {
				case FLOAT -> "float";
				case INT -> "int";
				case BOOL -> "bool";
			};
		}

		@Override
		public String glsl() {
			return name();
		}

		@Override
		public int components() {
			return 1;
		}

		@Override
		public Base base() {
			return kind;
		}
	}

	record Vector(Base kind, int size) implements ShType {
		@Override
		public String name() {
			return switch (kind) {
				case FLOAT -> "vec";
				case INT -> "ivec";
				case BOOL -> "bvec";
			} + size;
		}

		@Override
		public String glsl() {
			return name();
		}

		@Override
		public int components() {
			return size;
		}

		@Override
		public Base base() {
			return kind;
		}
	}

	record Matrix(int size) implements ShType {
		@Override
		public String name() {
			return "mat" + size;
		}

		@Override
		public String glsl() {
			return name();
		}

		@Override
		public int components() {
			return size * size;
		}

		@Override
		public Base base() {
			return Base.FLOAT;
		}

		ShType column() {
			return new Vector(Base.FLOAT, size);
		}
	}

	record TextureType() implements ShType {
		@Override
		public String name() {
			return "Texture";
		}

		@Override
		public String glsl() {
			return "sampler2D";
		}
	}

	record StructType(StructDef def) implements ShType {
		@Override
		public String name() {
			return def.name();
		}

		@Override
		public String glsl() {
			return def.glslName();
		}
	}

	record ArrayType(ShType element, int length) implements ShType {
		@Override
		public String name() {
			return element.name() + "[" + length + "]";
		}

		@Override
		public String glsl() {
			return element.glsl() + "[" + length + "]";
		}
	}

	final class StructDef {
		private final String name;
		private final String glslName;
		private List<StructField> fields = List.of();
		private final boolean builtin;

		StructDef(String name, String glslName, boolean builtin) {
			this.name = name;
			this.glslName = glslName;
			this.builtin = builtin;
		}

		String name() {
			return name;
		}

		String glslName() {
			return glslName;
		}

		List<StructField> fields() {
			return fields;
		}

		void fields(List<StructField> fields) {
			this.fields = List.copyOf(fields);
		}

		boolean builtin() {
			return builtin;
		}

		StructField field(String fieldName) {
			for (StructField field : fields) {
				if (field.name().equals(fieldName)) {
					return field;
				}
			}

			return null;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	record StructField(String name, String glslName, ShType type, boolean writable) {
	}
}
