package niagara.logical.predicates;

public abstract class Constant implements Atom {
	public abstract String getValue();

	public boolean equals(Object other) {
		if (other == null || !(other instanceof Constant))
			return false;
		return ((Constant) other).getValue().equals(getValue());
	}

	public boolean isConstant() {
		return true;
	}

	public boolean isVariable() {
		return false;
	}

	public boolean isPath() {
		return false;
	}

	public int hashCode() {
		return getValue().hashCode();
	}
}
