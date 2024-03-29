package niagara.logical;

import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Hashtable;
import java.util.Vector;

import niagara.data_manager.DataManager;
import niagara.optimizer.colombia.Op;
import niagara.physical.PhysicalOperator;
import niagara.query_engine.PhysicalOperatorQueue;
import niagara.query_engine.SchedulablePlan;
import niagara.query_engine.TupleSchema;
import niagara.utils.PEException;
import niagara.utils.ShutdownException;
import niagara.utils.SinkTupleStream;
import niagara.xmlql_parser.Schema;
import niagara.xmlql_parser.varTbl;

/**
 * This class is used to represent a node in the logical operator tree. Has data
 * members that helps in book-keeping when generation the plan.
 * 
 */

@SuppressWarnings( { "serial", "unchecked" })
public class LogNode implements SchedulablePlan, java.io.Serializable {
	protected LogicalOperator operator; // operator

	protected Schema tupleDes; // describes the tuple
	// captures the parent-child relationship
	// among the different elements scanned so far
	protected varTbl varList; // list of var used in the subtree rooted at
	// this node. Maps variables to their schemaAttributes

	protected LogNode[] inputs; // array of inputs or logNode

	protected boolean isHead;

	/**
	 * Constructor without any parameter
	 */
	public LogNode() {
		operator = null;
		inputs = new LogNode[1];
	}

	/**
	 * Constructor
	 * 
	 * @param the
	 *            unary operator
	 * @param the
	 *            only input to this operator
	 */
	public LogNode(LogicalOperator _op, LogNode in) {
		inputs = new LogNode[1];
		inputs[0] = in;
		operator = _op;
	}

	/**
	 * Constructor
	 * 
	 * @param the
	 *            operator
	 */
	public LogNode(LogicalOperator _op) {
		operator = _op;
		inputs = new LogNode[] {};
		tupleDes = null;
		varList = null;
	}

	/**
	 * Constructor
	 * 
	 * @param the
	 *            binary operator
	 * @param the
	 *            left subtree
	 * @param the
	 *            right subtree
	 */
	public LogNode(LogicalOperator _op, LogNode leftin, LogNode rightin) {
		inputs = new LogNode[2];
		inputs[0] = leftin;
		inputs[1] = rightin;
		operator = _op;
	}

	public LogNode(LogicalOperator operator, LogNode[] inputs) {
		this.operator = operator;
		this.inputs = inputs;
	}

	/**
	 * @return the operator
	 */
	public LogicalOperator getOperator() {
		return operator;
	}

	public void setOperator(LogicalOperator operator) {
		this.operator = operator;
	}

	public LogNode[] getInputs() {
		return inputs;
	}

	/**
	 * This function returns the number of inputs to this logical node
	 * 
	 * @return The number of inputs to this logical node
	 */

	public int getArity() {
		return inputs.length;
	}

	/**
	 * @return the left subtree
	 */
	public LogNode leftInput() {
		return input(0);
	}

	/**
	 * @return the right subtree
	 */
	public LogNode rightInput() {
		return input(1);
	}

	public LogNode input(int i) {
		// OK. Trigger trick comes in
		if (inputs == null)
			return null;
		if (i >= inputs.length)
			return null; // should throw sth?
		return inputs[i];
		// if(trig==null) return null; // should not happen
		// inputs[i] = trig.findLogNode(inputsId[i]);
		// return inputs[i];
	}

	/**
	 * @return the first subtree or the only subtree in case of unary operators
	 */
	public LogNode input() {
		return input(0);
	}

	public void setInputs(LogNode[] inputs) {
		this.inputs = inputs;
	}

	/**
	 * @param the
	 *            Schema of the tuples at this node
	 */
	public void setSchema(Schema _td) {
		tupleDes = _td;
	}

	/**
	 * @param variable
	 *            table with the variables encountered so far
	 */
	public void setVarTbl(varTbl _vt) {
		varList = _vt;
	}

	/**
	 * @return true if the given set of variables is contained in the variables
	 *         encountered in the subtree rooted at this node, false otherwise
	 */
	public boolean contains(Vector variables) {
		if (varList == null)
			return false;
		return varList.contains(variables);
	}

	/**
	 * @return the variable table
	 */
	public varTbl getVarTbl() {
		return varList;
	}

	/**
	 * @return the Schema created from the elements encountered so far
	 */
	public Schema getSchema() {
		return tupleDes;
	}

	/**
	 * used for creating a postscript representation of this logical plan tree
	 * using the 'dot' command. called recursively on the child nodes.
	 * 
	 * @return String representation for the 'dot' command
	 */
	public String makeDot() {
		String dot = "";
		String thisNode = "\"" + operator.toString() + "\"";
		dot += operator.hashCode() + " [label=" + thisNode + "];\n";
		if (inputs != null)
			for (int i = 0; i < inputs.length; i++) {
				dot += operator.hashCode() + "->"
						+ inputs[i].getOperator().hashCode() + ";\n";
				dot += inputs[i].makeDot();
			}
		return dot;
	}

	/**
	 * saves the String representation of this tree for the dot command into a
	 * file that can be used to generate a postscript file with the graph.
	 * 
	 * @param the
	 *            String
	 * @param the
	 *            output
	 */
	public static void writeDot(String dot, Writer writer) {
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(writer);
			pw.println("digraph QueryPlan {");
			pw.println(dot);
			pw.println("}");
			pw.close();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (pw != null)
					pw.close();
			} catch (Exception e) {
			}
		}
	}

	/**
	 * prints this node to the standard output
	 */
	public void dump() {
		dump(new Hashtable());
	}

	protected void dump(Hashtable nodesDumped) {
		if (nodesDumped.containsKey(this))
			return;
		nodesDumped.put(this, this);

		operator.dump();

		if (inputs != null)
			for (int i = 0; i < inputs.length; i++)
				input(i).dump(nodesDumped);
	}

	public boolean isSchedulable() {
		return true;
	}

	public String getName() {
		// return an artificial name for this operator instance
		return operator.getName() + "#" + operator.hashCode();
	}

	public boolean isAccumulateOp() {
		return (operator instanceof Accumulate);
	}

	public String getAccumFileName() {
		if (!isAccumulateOp()) {
			throw new PEException(
					"Can't get AccumFile name from non-accumulate operator");
		}
		return ((Accumulate) operator).getAccumFileName();
	}

	public boolean isHead() {
		return isHead;
	}

	public void setIsHead() {
		isHead = true;
	}

	/** Instantiate the selected physical operator algorithm */
	public PhysicalOperator getPhysicalOperator() {
		Class physicalOperatorClass = operator.getSelectedAlgo();
		// If there is no selected algo, error
		if (physicalOperatorClass == null) {
			throw new PEException("No algorithm selected");
		}

		PhysicalOperator physicalOperator;
		try {
			// Get the zero-argument constructor
			Constructor constructor = physicalOperatorClass
					.getConstructor(new Class[] {});

			// Create a new physical operator object
			physicalOperator = (PhysicalOperator) constructor
					.newInstance(new Object[] {});
		} catch (NoSuchMethodException nsme) {
			throw new PEException(
					"Could not find a zero-argument constructor for: "
							+ physicalOperatorClass);
		} catch (InstantiationException e) {
			throw new PEException("Error in Instantiating Physical Operator"
					+ e.getMessage());
		} catch (IllegalAccessException e) {
			throw new PEException("Error in Instantiating Physical Operator"
					+ e.getMessage());
		} catch (InvocationTargetException e) {
			throw new PEException("Error in Instantiating Physical Operator"
					+ e.getMessage());
		}

		physicalOperator.initFrom(operator);

		TupleSchema[] ts = new TupleSchema[getArity()];
		for (int i = 0; i < getArity(); i++)
			ts[i] = new TupleSchema();
		physicalOperator.constructMinimalTupleSchema(ts);
		return physicalOperator;
	}

	public SchedulablePlan getInput(int i) {
		return input(i);
	}

	public boolean isSource() {
		return operator.isSourceOp();
	}

	public void setInputs(SchedulablePlan[] inputs) {
		setInputs((LogNode[]) inputs);
	}

	public int getNumberOfOutputs() {
		return operator.getNumberOfOutputs();
	}

	public void processSource(SinkTupleStream sinkStream, DataManager dm,
			PhysicalOperatorQueue opQueue) throws ShutdownException {
		if (!isSource())
			throw new PEException("Not a source op");
		LogicalOperator sourceOp = operator;

		if (sourceOp instanceof DTDScan) {
			DTDScan dop = (DTDScan) sourceOp;
			// Ask the data manager to start filling the output stream with
			// the parsed XML documents
			boolean scan = dm.getDocuments(dop.getDocs(), null, sinkStream);
			if (!scan)
				System.err.println("dtdScan FAILURE! "
						+ dop.getDocs().elementAt(0));
		} else
			throw new PEException("Unknown source op");
	}

	public void setOperator(Op operator) {
		this.operator = (LogicalOperator) operator;
	}

	public boolean isSendImmediate() {
		return false;
	}

	public void setSendImmediate() {
		assert false : "Should not ask this of a log node";
	}

	public void setPlanID(String planID) {
		throw new PEException("We do not support prepared plans for XMLQL");
	}

	public String getPlanID() {
		throw new PEException("We do not support prepared plans for XMLQL");
	}
}
