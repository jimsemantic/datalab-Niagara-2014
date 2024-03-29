package niagara.query_engine;

import java.util.Vector;

import niagara.xmlql_parser.opType;

/**
 * A tree of ContainTreeNode forms a containment tree. Each node could have only
 * one of four operators: AND, OR, IS, UNDEF (for string), DIRECT_CONTAIN,
 * CONTAIN and each node has two children. For CONTAIN and DIRECT_CONTAIN nodes,
 * the left child contains the right child.
 */
@SuppressWarnings("unchecked")
class ContainTreeNode {
	private int optype;
	private ContainTreeNode leftChild;
	private ContainTreeNode rightChild;
	private String term;
	private Vector schemaAncestors; // this is used to construct

	// containment relationships

	public ContainTreeNode(int optype, ContainTreeNode left,
			ContainTreeNode right) {

		this.optype = optype;
		leftChild = left;
		rightChild = right;
		term = null;
		schemaAncestors = new Vector();
	}

	public ContainTreeNode(String str, int ancestor) {
		optype = opType.UNDEF;
		leftChild = null;
		rightChild = null;
		term = str;
		schemaAncestors = new Vector();

		if (ancestor > 0)
			schemaAncestors.addElement(new Integer(ancestor));
	}

	public ContainTreeNode getLeftChild() {
		return leftChild;
	}

	public void setLeftChild(ContainTreeNode node) {
		leftChild = node;
	}

	public void setRightChild(ContainTreeNode node) {
		rightChild = node;
	}

	public ContainTreeNode getRightChild() {
		return rightChild;
	}

	public Vector getAncestors(Vector containUnits) {

		if (schemaAncestors.size() > 0) {
			int anc = ((Integer) schemaAncestors.elementAt(0)).intValue();
			while (anc > 0) {
				ContainUnit anc_unit = (ContainUnit) containUnits
						.elementAt(anc);
				if (anc_unit == null) {
					System.err
							.println("bug: can't get contain unit of ancestor");
					System.exit(1);
				}
				anc = anc_unit.getParent();
				if (anc > 0) {
					schemaAncestors.insertElementAt(new Integer(anc), 0);
				}
			}
		}

		return schemaAncestors;
	}

	public Vector getAncestors() {
		return schemaAncestors;
	}

	public void setAncestors(Vector vec) {

		schemaAncestors = null;
		schemaAncestors = vec;
	}

	public String toString() {

		String ret = "(";

		if (leftChild != null)
			ret += leftChild.toString();

		String opstr = getOpString();
		ret += " " + opstr + " ";

		if (opstr.equals("UNKNOWN") || optype == opType.UNDEF)
			return opstr;

		if (rightChild != null)
			ret += rightChild.toString();

		ret += ")";

		return ret;
	}

	public void dump(int level) {
		for (int i = 0; i < level; i++)
			System.out.print("  ");

		System.out.print(getOpString());
		System.out.println("(" + schemaAncestors + ")");

		if (leftChild != null) {
			leftChild.dump(level + 1);
		}
		if (rightChild != null) {
			rightChild.dump(level + 1);
		}
	}

	public String getOpString() {

		switch (optype) {

		case opType.LT:
			return "&lt;";
		case opType.GT:
			return "&gt;";
		case opType.EQ:
			return "=";
		case opType.LEQ:
			return "&lt;=";
		case opType.GEQ:
			return "&gt;=";
		case opType.AND:
			return "AND";
		case opType.OR:
			return "OR";
		case opType.IS:
			return "CONTAINS"; // // Hack by leonidas
		case opType.CONTAIN:
			return "CONTAINS";
		case opType.DIRECT_CONTAIN:
			return "CONTAINS";
		case opType.UNDEF:
			return term;
		default:
			return ("UNKNOWN");
		}
	}
}
