package niagara.physical;

import niagara.logical.Nest;
import niagara.optimizer.colombia.Cost;
import niagara.optimizer.colombia.ICatalog;
import niagara.optimizer.colombia.LogicalOp;
import niagara.optimizer.colombia.LogicalProperty;
import niagara.query_engine.TupleSchema;
import niagara.utils.BaseAttr;
import niagara.utils.NodeVector;
import niagara.utils.ShutdownException;
import niagara.utils.Tuple;
import niagara.utils.XMLAttr;
import niagara.xmlql_parser.constructBaseNode;
import niagara.xmlql_parser.constructInternalNode;
import niagara.xmlql_parser.varTbl;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This is the <code>PhysicalNestOperator</code> that extends the
 * <code>PhysicalGroupOperator</code> with the implementation of nesting (a form
 * of grouping)
 * 
 * @version 1.0
 * 
 */

public class PhysicalNest extends PhysicalGroup {

	private static final boolean[] blockingSourceStreams = { true };

	// The result template for the nest operator
	private constructBaseNode resultTemplate;
	private int numGroupingAttributes;

	// temporary result list storage place
	private NodeVector resultList;

	/**
	 * This is the constructor for the PhysicalNestOperator class that
	 * initializes it with the appropriate logical operator, source streams,
	 * sink streams, and the responsiveness to control information.
	 * 
	 * @param logicalOperator
	 *            The logical operator that this operator implements
	 * @param sourceStreams
	 *            The Source Streams associated with the operator
	 * @param sinkStreams
	 *            The Sink Streams associated with the operator
	 * @param responsiveness
	 *            The responsiveness to control messages, in milli seconds
	 */
	public PhysicalNest() {
		setBlockingSourceStreams(blockingSourceStreams);
	}

	/**
	 * @see niagara.optimizer.colombia.PhysicalOp#initFrom(LogicalOp)
	 */
	public void localInitFrom(LogicalOp logicalOp) {
		resultTemplate = ((Nest) logicalOp).getResTemp();
		assert resultTemplate instanceof constructInternalNode : "KT I believe the system only supports nests that have a specified top node";
	}

	/**
	 * @see niagara.optimizer.colombia.Op#copy()
	 */
	protected PhysicalGroup localCopy() {
		PhysicalNest op = new PhysicalNest();
		// XXX vpapad: We treat resultTemplate as an immutable object
		op.resultTemplate = resultTemplate;
		return op;
	}

	protected boolean localEquals(Object other) {
		return resultTemplate.equals(((PhysicalNest) other).resultTemplate);
	}

	/**
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return groupAttributeList.hashCode() ^ resultTemplate.hashCode();
	}

	// ///////////////////////////////////////////////////////////////////////
	// These functions are the hooks that are used to implement specific //
	// nest operator (specializing the group operator) //
	// ///////////////////////////////////////////////////////////////////////

	/**
	 * This function is called to initialize a grouping operator for execution
	 * by setting up relevant structures etc. PhysicalGroupOperator calls this
	 * function...
	 */

	protected final void initializeForExecution() {
		numGroupingAttributes = groupAttributeList.size();
		resultList = new NodeVector();
	}

	/**
	 * This function constructs a ungrouped result from a tuple
	 * 
	 * @param tupleElement
	 *            The tuple to construct the ungrouped result from
	 * 
	 * @return The constructed object; If no object is constructed, returns null
	 */
	protected final BaseAttr constructUngroupedResult(Tuple tupleElement)
			throws ShutdownException {

		// Construct the result as per the template for the tuple
		resultList.quickReset();
		PhysicalConstruct.constructResult(tupleElement, resultTemplate,
				resultList, doc);

		// The list can have a size of only one, get that result
		// and return it
		if (resultList.size() == 0) // this means we got a null
			return null;
		assert resultList.size() == 1;
		return new XMLAttr(resultList.get(0));
	}

	/**
	 * This function merges a grouped result with an ungrouped result
	 * 
	 * @param groupedResult
	 *            The grouped result that is to be modified (this can be null)
	 * @param ungroupedResult
	 *            The ungrouped result that is to be grouped with groupedResult
	 *            (this can never be null)
	 * 
	 * @return The new grouped result
	 */

	protected final Object mergeResults(Object groupedResult,
			BaseAttr ungroupedResult) {
		// Set up the final result - if the groupedResult is null, then
		// create holder for final result, else just use groupedResult
		// ungrouped nodes was a node list, now it is the whole node
		// created by constructResult,
		// grouped result will be a node containing a root element
		// followed by a list of children
		// if grouped result is null, we first add stripped down root element

		NodeVector resultVec;

		assert ungroupedResult instanceof XMLAttr : "How to nest non-XML data? - Jenny";

		if (groupedResult == null) {
			resultVec = new NodeVector();
			// add root element to the result
			// KT don't think we need this clone...
			// resultVec.add((Node)ungroupedResult);
			resultVec.add(((XMLAttr) ungroupedResult).getNodeValue());
		} else {
			resultVec = (NodeVector) groupedResult;

			// The ungrouped result is a node list
			// NodeList ungroupedNodes =
			// ((Node)ungroupedResult).getChildNodes();
			NodeList ungroupedNodes = (((XMLAttr) ungroupedResult)
					.getNodeValue().getChildNodes());

			// Add all items in ungrouped result
			int numNodes = ungroupedNodes.getLength();

			for (int node = 0; node < numNodes; ++node) {
				resultVec.add(ungroupedNodes.item(node));
			}
		}

		// Return the grouped result
		return resultVec;
	}

	/**
	 * This function returns an empty result in case there are no groups
	 * 
	 * @return The result when there are no groups. Returns null if no result is
	 *         to be constructed
	 */

	protected final Node constructEmptyResult() {
		// If the number of grouping attributes is 0, then construct result,
		// else return null
		if (numGroupingAttributes == 0) {
			// Code used to create and return a Element with the root tag
			// if possible - KT - hope we don't get schema attribute for
			// root tag!!
			String rootTag = (String) ((constructInternalNode) resultTemplate)
					.getStartTag().getSdata().getValue();
			return doc.createElement(rootTag);
		} else {
			return null;
		}
	}

	/**
	 * This function constructs a result from the grouped partial and final
	 * results of a group. Both partial result and final result cannot be null
	 * 
	 * @param partialResult
	 *            The partial results of the group (this can be null)
	 * @param finalResult
	 *            The final results of the group (this can be null)
	 * 
	 * @return A results merging partial and final results; If no such result,
	 *         returns null
	 */

	protected final BaseAttr constructResult(Object partialResult,
			Object finalResult) {
		// first element in finalResult and partial result
		// should be the same
		Element resultElement;
		if (finalResult != null)
			resultElement = (Element) ((NodeVector) finalResult).get(0);
		else if (partialResult != null)
			resultElement = (Element) ((NodeVector) partialResult).get(0);
		else
			resultElement = (Element) constructEmptyResult();

		// If the partial result is not null, add all elements
		// of the partial result
		if (partialResult != null) {
			// Add nodes in vector to result
			addNodesToResult(resultElement, (NodeVector) partialResult);
		}

		// If the final result is not null, add all element of the final result
		if (finalResult != null) {
			// Add nodes in vector to result
			addNodesToResult(resultElement, (NodeVector) finalResult);
		}
		return new XMLAttr(resultElement);
	}

	/**
	 * This function add the elements of a node vector to the result element
	 * 
	 * @param resultElement
	 *            The result Element
	 * @param nodeVector
	 *            The vector of nodes to be added
	 */
	private void addNodesToResult(Element resultElement, NodeVector nodeVector) {

		// Loop over all elements and add a clone to the result element
		int numNodes = nodeVector.size();

		// ignore node at index 0, that is the root element...
		for (int node = 1; node < numNodes; ++node) {
			// TODO KT I'm not sure nest needs to clone!
			resultElement.appendChild(((Node) nodeVector.get(node))
					.cloneNode(true));
		}
	}

	/**
	 * @see niagara.optimizer.colombia.PhysicalOp#FindLocalCost(ICatalog,
	 *      LogicalProperty, LogicalProperty[])
	 */
	public final Cost findLocalCost(ICatalog catalog,
			LogicalProperty[] inputLogProp) {
		// KT - stolen from construct and PhysicalGroup
		// XXX vpapad: really naive. Only considers the hashing cost
		float inpCard = inputLogProp[0].getCardinality();
		float outputCard = logProp.getCardinality();

		double cost = inpCard * catalog.getDouble("tuple_reading_cost");
		cost += inpCard * catalog.getDouble("tuple_hashing_cost");
		cost += outputCard * catalog.getDouble("tuple_construction_cost");

		// XXX vpapad: Absolutely no connection to reality!
		// We consider only a fixed cost per output tuple
		cost += constructTupleCost(catalog) * getLogProp().getCardinality();
		return new Cost(cost);
	}

	/**
	 * @see niagara.query_engine.PhysicalOperator#constructTupleSchema(TupleSchema[])
	 */
	public void constructTupleSchema(TupleSchema[] inputSchemas) {
		super.constructTupleSchema(inputSchemas);
		resultTemplate.replaceVar(new varTbl(inputSchemas[0]));
	}
}
