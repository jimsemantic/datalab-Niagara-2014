package niagara.physical;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.Vector;

import niagara.logical.WindowGroup;
import niagara.optimizer.colombia.Attribute;
import niagara.optimizer.colombia.Attrs;
import niagara.optimizer.colombia.Cost;
import niagara.optimizer.colombia.ICatalog;
import niagara.optimizer.colombia.LogicalOp;
import niagara.optimizer.colombia.LogicalProperty;
import niagara.optimizer.colombia.Op;
import niagara.utils.BaseAttr;
import niagara.utils.FeedbackPunctuation;
import niagara.utils.Guard;
import niagara.utils.IntegerAttr;
import niagara.utils.LongAttr;
import niagara.utils.Punctuation;
import niagara.utils.ShutdownException;
import niagara.utils.Tuple;
import niagara.xmlql_parser.skolem;

import org.w3c.dom.Document;

/**
 * This is the <code>PhysicalWindowGroup</code> that extends the
 * PhysicalGroupOperator with the implementation of the group operator.
 * 
 * @version 1.0
 * 
 */
@SuppressWarnings("unchecked")
public abstract class PhysicalWindowGroup extends PhysicalOperator {

	// Feedback Punctuation propagation
	protected boolean propagate;
	
	protected boolean exploit;
	
//	 Feedback
	protected Guard outputGuard = new Guard();
	protected int[] positions;
	protected String[] names;
	
	/* Guard */
	//String guardOutput = "*";
	//String fAttr;
	
	String fAttr = "";

	// These are private nested classes used within the operator
	// Copied from PhysicalGroup
	
	/**
	 * This is the class that stores the information in an entry of the hash
	 * table
	 */
	protected class HashEntry {
		// This is the object representing the final results
		private Object finalResult;

		// This is the object representing the partial results
		private Object partialResult;

		// This is the id of the currency of the partial results
		private int partialResultId;

		// This is a representative tuple of the hash entry
		Tuple representativeTuple;

		/**
		 * This is the constructor of a hash entry that initialize it with a
		 * representative tuple
		 * 
		 * @param currPartialResultId
		 *            The current id of partial results
		 * @param representativeTuple
		 *            A representative tuple for this hash entry
		 */
		public HashEntry(int currPartialResultId, Tuple representativeTuple) {

			// Initialize the results to nothing
			finalResult = null;
			partialResult = null;

			// Initialize the partial result id
			this.partialResultId = currPartialResultId;

			// Initialize the representative tuple
			// this.representativeTuple = representativeTuple;

			// to support window aggregates
			this.representativeTuple = (Tuple) representativeTuple.clone();

		}

		/**
		 * This function returns the final result associated with this entry
		 * 
		 * @return The final result
		 */

		public Object getFinalResult() {
			// Return the final result
			return finalResult;
		}

		/**
		 * This function sets the final result of this entry
		 * 
		 * @param finalResult
		 *            The final result to be set
		 */

		public void setFinalResult(Object finalResult) {
			this.finalResult = finalResult;
		}

		/**
		 * This function returns the partial result associated with this entry
		 * 
		 * @return The partial result
		 */
		public Object getPartialResult() {
			return partialResult;
		}

		/**
		 * This function sets the partial result of this entry
		 * 
		 * @param partialResult
		 *            The partial result to be set
		 */

		public void setPartialResult(Object partialResult) {
			this.partialResult = partialResult;
		}

		/**
		 * This function updates the partial result to make it consistent with
		 * the current partial result id
		 * 
		 * @param currPartialResultId
		 *            The current partial result id of the operator
		 */

		public void updatePartialResult(int currPartialResultId) {
			// If the stored partial id is less than the current partial id,
			// then
			// clear the partial result and update the stored partial id
			if (partialResultId < currPartialResultId) {
				// Clear the partial results
				partialResult = null;

				// Update the stored partial result id
				partialResultId = currPartialResultId;
			}
		}

		/**
		 * This function returns the representative tuple associated with this
		 * hash entry
		 * 
		 * @return The representative tuple associated with the hash entry
		 */
		public Tuple getRepresentativeTuple() {
			// Return the representative tuple
			return representativeTuple;
		}
	}

	// ///////////////////////////////////////////////////////////////////////
	// These are the private data members of the PhysicalGroupOperator //
	// class //
	// ///////////////////////////////////////////////////////////////////////

	// This is the array having information about blocking and non-blocking
	// streams
	//
	private static final boolean[] blockingSourceStreams = { true };

	// The list of attributes to group by
	protected Vector groupAttributeList;
	protected Hasher hasher;

	// This is the hash table for performing grouping efficiently
	protected Hashtable hashtable;

	// Store the values that make up a hash key
	//private String[] rgstPValues;
	//private String[] rgstTValues;

	// This is the current partial id of the operator used to discard previous
	// partial results
	protected int currPartialResultId;
	protected Document doc;
	protected int numGroupingAttributes;
	private int[] attributeIds;
	private HashEntry singleGroupResult;

	// streamIds, together with window, record the tuple element and Id
	// for a tuple in the current sliding window
	//
	protected ArrayList streamIds;
	protected Attribute windowAttr;
	protected String widName;
	SimpleAtomicEvaluator eaFrom, eaTo;
	
	int tupleOut = 0;
	int tupleDrop = 0;
	int count = 0;
	
	// Required for ECW
	protected long curWidFrom;

	
	int tuplePrintCount = 0;

	// private int widFromPos, widToPos;

	// /////////////////////////////////////////////////////////////////////////
	// These are the methods of the PhysicalWindowGroupOperator class //
	// /////////////////////////////////////////////////////////////////////////

	/**
	 * This is the constructor for the PhysicalWindowOperator class that
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
	public PhysicalWindowGroup() {
		setBlockingSourceStreams(blockingSourceStreams);
	}

	public final void opInitFrom(LogicalOp logicalOperator) {
		skolem grouping = ((niagara.logical.Group) logicalOperator)
				.getSkolemAttributes();
		groupAttributeList = grouping.getVarList();

		windowAttr = ((WindowGroup) logicalOperator).getWindowAttr();
		widName = ((WindowGroup) logicalOperator).getWid();
		propagate = ((WindowGroup) logicalOperator).getPropagate();
		logging = ((WindowGroup) logicalOperator).getLogging();
		exploit = ((WindowGroup) logicalOperator).getExploit();
		fAttr = ((WindowGroup) logicalOperator).getFAttr();
		
		// have subclass do initialization
		localInitFrom(logicalOperator);
	}

	abstract protected void localInitFrom(LogicalOp logicalOperator);

	// Copied from PhysicalGroup

	public final Op opCopy() {
		PhysicalWindowGroup op = localCopy();
		op.groupAttributeList = groupAttributeList;
		op.widName = widName;
		op.propagate = propagate;
		//op.guardOutput = guardOutput;
		op.fAttr = fAttr;
		op.logging = logging;
		op.log = log; 
		op.exploit = exploit;
		
		return op;
	}

	public final boolean equals(Object o) {
		if (o == null)
			return false;
		if (!this.getClass().isInstance(o))
			return false;
		if (o.getClass() != this.getClass())
			return o.equals(this);
		if (!groupAttributeList
				.equals(((PhysicalWindowGroup) o).groupAttributeList))
			return false;
		if (!widName.equals(((PhysicalWindowGroup) o).widName))
			return false;
		if (((PhysicalWindowGroup) o).propagate != propagate)
			return false;
//		if (!((PhysicalWindowGroup) o).guardOutput.equals(guardOutput))
//			return false;
		if (!((PhysicalWindowGroup) o).fAttr.equals(fAttr))
			return false;
		if (((PhysicalWindowGroup) o).exploit != exploit)
			return false;
		
		return localEquals(o);
	}

	/**
	 * This function initializes the data structures for an operator. This
	 * over-rides the corresponding function in the base class.
	 * 
	 * @return True if the operator is to continue and false otherwise
	 */
	protected final void opInitialize() {

		eaFrom = new SimpleAtomicEvaluator("wid_from_" + widName);
		eaFrom.resolveVariables(inputTupleSchemas[0], 0);
		eaTo = new SimpleAtomicEvaluator("wid_to_" + widName);
		eaTo.resolveVariables(inputTupleSchemas[0], 0);

		numGroupingAttributes = groupAttributeList.size();

		if (numGroupingAttributes > 0) {
			hasher = new Hasher(groupAttributeList);
			hasher.resolveVariables(inputTupleSchemas[0]);

			// rgstPValues = new String[groupAttributeList.size()];
			// rgstTValues = new String[groupAttributeList.size()];
			hashtable = new Hashtable();

			// get the attr indices
			Attribute attr;
			attributeIds = new int[numGroupingAttributes];
			for (int i = 0; i < numGroupingAttributes; i++) {
				attr = (Attribute) groupAttributeList.get(i);
				attributeIds[i] = inputTupleSchemas[0].getPosition(attr
						.getName());
			}
		} else {
			singleGroupResult = null;
		}

		currPartialResultId = 0;

		// Ask subclasses to initialize
		this.initializeForExecution();
	}

	// END

	protected void processPunctuation(Punctuation inputTuple, int streamId)
			throws ShutdownException, InterruptedException {

		//System.err.println(this.id + " process punct");
		
//		if (tuplePrintCount % 1 == 0) {
//			System.out.println("PhysicalWindowGroup::processPunctuation, hashtable.size():  " + hashtable.size());
//		}
//		tuplePrintCount++;

		HashEntry groupResult = null;
		if (numGroupingAttributes == 0)
			assert false : "not supported yet - yell at Kristin";

		String stPunctKey;
		try {
			stPunctKey = hasher.hashKey(inputTuple);
		} catch (java.lang.ArrayIndexOutOfBoundsException ex) {
			// Not a punctuation for the group attribute. Ignore it.
			return;
		}

		String[] punctValues = new String[numGroupingAttributes];
		hasher.getValuesFromKey(stPunctKey, punctValues);

		// Does the punctuation punctuate on every grouping attribute?
		boolean idealPunct = true;
		for (int i = 0; i < numGroupingAttributes; i++)
			if (punctValues[i].trim().compareTo("*") == 0) {
				idealPunct = false;
				break;
			}

		// Yes. The punctuation punctuates on every grouping attributes
		if (idealPunct) {
			groupResult = (HashEntry) hashtable.get(stPunctKey);

			if (groupResult != null)
				putResult(groupResult, false);
			/*
			 * else { int pos =
			 * inputTupleSchemas[0].getPosition("wid_from_"+inputName); String
			 * tmpPos =
			 * inputTuple.getAttribute(pos).getFirstChild().getNodeValue(); }
			 */// for debugging
			hashtable.remove(stPunctKey);
		}
		// No. Need to walk through the hashtable
		else {
			Set keys = hashtable.keySet();

			LinkedList removes = new LinkedList();
			LinkedList puts = new LinkedList();

			String groupKey;
			String[] groupKeyValues = new String[numGroupingAttributes];
			boolean match;
			for (Object key : keys) {
				groupKey = (String) key;
				hasher.getValuesFromKey(groupKey, groupKeyValues);
				match = true;
				for (int i = 0; i < numGroupingAttributes; i++) {
					// if groupKeyValues match punctVals, output the result
					if (groupKeyValues[i].compareTo(punctValues[i]) != 0
							&& punctValues[i].trim().compareTo("*") != 0) {
						match = false;
						break;
					}
				}
				if (match) {
					puts.add(hashtable.get(groupKey));
					removes.add(key);
				}
			}

			for (Object p : puts) {
				putResult((HashEntry) p, false);
			}

			for (Object r : removes) {
				hashtable.remove(r);
			}

		}
		producePunctuation(Long
				.valueOf(eaFrom.getAtomicValue(inputTuple, null)));
	}

	private void producePunctuation(long wid) throws InterruptedException,
			ShutdownException {
		Punctuation punct = new Punctuation(false);

		Attrs attributes = outputTupleSchema.getAttrs();

		/*
		 * for (int i = 0; i < attributes.size(); i++) {
		 * System.err.println(attributes.get(i).getName()); }
		 */

		for (int i = 0; i < attributes.size(); i++) {
			if (attributes.get(i).getName().compareTo(eaFrom.getName()) == 0) {
				punct.appendAttribute(new LongAttr(wid));
			} else {
				punct.appendAttribute(BaseAttr
						.createWildStar(BaseAttr.Type.String));
			}
		}

		putTuple(punct, 0);
		
		if(logging){
			punctsOut++; // Count the input punctuations for this operator
			log.Update("PunctsOut", String.valueOf(punctsOut));
		}
		//displayTupleAlways(punct, "PhysicalWindowGroup::producePunctuation end");

	}

	private void putResult(HashEntry hashEntry, boolean partial)
			throws InterruptedException, ShutdownException {

		// Update hash entry for partial results
		hashEntry.updatePartialResult(currPartialResultId);

		
		// Get the result object if at least partial or final
		// result is not null
		Object partialResult = hashEntry.getPartialResult();
		Object finalResult = hashEntry.getFinalResult();

		BaseAttr resultNode = null;

		if (partialResult != null || finalResult != null) {
			resultNode = this.constructResult(partialResult, finalResult);
		}

		// If there is a non- empty result, then create tuple and add to
		// result
		if (resultNode != null) 
		{
			Tuple tupleElement = createTuple(resultNode, hashEntry.getRepresentativeTuple(), partial);

//				int pos = outputTupleSchema.getPosition(fAttr);
//				IntegerAttr v = (IntegerAttr) tupleElement.getAttribute(pos);
//				String tupleGuard = v.toASCII();
				
				if (exploit) 
				{
					// get attribute positions from tuple to check against guards
					//int[] positions = new int[2];
					//String[] names = ; //{ "wid_from_bucket", "milepost" };

//					for (int i = 0; i < names.length; i++) {
	//					positions[i] = outputTupleSchema.getPosition(names[i]);
		//			}

					// check against guards
					Boolean guardMatch = false;
					for (FeedbackPunctuation fp : outputGuard.elements()) 
					{
						guardMatch = guardMatch	|| fp.match(positions, tupleElement.getTuple());
					}

					if (guardMatch) {
						return;
					}
				} 
				
				
				if (logging) {
					tupleOut++;
					log.Update("TupleOut", String.valueOf(tupleOut));
				}

			//	System.out.println(this.getName() + tupleOut);
				putTuple(tupleElement, 0);

				
		}
	}

	/**
	 * This function processes a tuple element read from a source stream when
	 * the operator is in a blocking state. This over-rides the corresponding
	 * function in the base class.
	 * 
	 * @param tupleElement
	 *            The tuple element read from a source stream
	 * @param streamId
	 *            The source stream from which the tuple was read
	 * 
	 * @exception ShutdownException
	 *                query shutdown by user or execution error
	 */

	// ****Look at a tuple once, and hash it to multiple hash entries
	// **** -Jenny
	protected final void blockingProcessTuple(Tuple tupleElement, int streamId)
			throws ShutdownException {
		Object ungroupedResult = this.constructUngroupedResult(tupleElement);
		if (ungroupedResult == null)
			return;

		int tupleSize = tupleElement.size();

		int from = Integer.parseInt(eaFrom.getAtomicValue(tupleElement, null));
		int to = Integer.parseInt(eaTo.getAtomicValue(tupleElement, null));
		
		// For ECW
		curWidFrom = from;
		
		HashEntry prevResult;

		String key = hasher.hashKey(tupleElement);
		String[] values = new String[numGroupingAttributes];
		hasher.getValuesFromKey(key, values);

		IntegerAttr wid;
		String hashKey;
		
		// amit: if this tuple matches an FP in the guard, don't add it to the hashtable. Rather if there is a 
		// matching hashtable entry also delete that entry
		//System.out.println(this.getName() + count++);
		
		
		// Probe hash table to see whether result for this hashcode
		// already exist
		for (int i = from; i <= to; i++) {
			values[numGroupingAttributes - 1] = String.valueOf(i);
			hashKey = hasher.hashKey(values);
			prevResult = (HashEntry) hashtable.get(hashKey);

			if (prevResult == null) {
				wid = new IntegerAttr(i);

				Tuple tmpTuple = (Tuple) tupleElement.clone();

				tmpTuple.setAttribute(tupleSize - 2, wid);
				// If it does not have the result, just create new one
				// with the current partial result id with the tupleElement
				// as the representative tuple
				prevResult = new HashEntry(currPartialResultId, tmpTuple);

				// Add the entry to hash table
				// hashtable.put(key, prevResult);
				hashtable.put(hashKey, prevResult);

			} else {
				// It did have the result - update partial results
				prevResult.updatePartialResult(currPartialResultId);
			}
			// Based on whether the tuple represents partial or final results
			// merge ungrouped result with previously grouped results
			if (tupleElement.isPartial()) {
				// Merge the partial result so far with current ungrouped result
				Object newPartialResult = this.mergeResults(prevResult
						.getPartialResult(), ungroupedResult);

				// Update the partial result
				prevResult.setPartialResult(newPartialResult);
			} else {
				// Merge the final result so far with current ungrouped result
				Object newFinalResult = this.mergeResults(prevResult
						.getFinalResult(), ungroupedResult);

				// Update the final result
				prevResult.setFinalResult(newFinalResult);
			}
			prevResult = null;

		}

		
		
	}

	/**
	 * This function returns the current output of the operator. This function
	 * is invoked only when the operator is blocking. This over-rides the
	 * corresponding function in the base class.
	 * 
	 * @param partial
	 *            If this function call is due to a request for a partial result
	 * 
	 */
	protected final void flushCurrentResults(boolean partial)
			throws InterruptedException, ShutdownException {

		if (numGroupingAttributes == 0) {
			if (singleGroupResult == null) {
				putEmptyResult(partial);
			} else {
				putResult(singleGroupResult, partial);
			}
			return;
		}

		// Get all the values in the hashtable and an iterator over the values
		Collection values = hashtable.values();
		Iterator iter = values.iterator();

		// If the iterator does not have any values, then call empty construct
		if (!iter.hasNext()) {
			putEmptyResult(partial);
		} else {
			// For each group, construct results
			while (iter.hasNext()) {
				// Get the next element in the hash table
				HashEntry hashEntry = (HashEntry) iter.next();
				putResult(hashEntry, partial);
			}
		}
	}

	private void putEmptyResult(boolean partial) throws InterruptedException,
			ShutdownException {
		BaseAttr emptyResult = constructEmptyResult();

		// If there is a non- empty result, then create tuple and add to
		// result
		if (emptyResult != null) {
			// Create tuple
			Tuple tupleElement = createTuple(emptyResult, null, // No
																// representative
																// tuple
					partial);

			// Add the tuple to the result
			putTuple(tupleElement, 0);
		}
	}

	/**
	 * This function removes the effects of the partial results in a given
	 * source stream. This over-rides the corresponding function in the base
	 * class.
	 * 
	 * @param streamId
	 *            The id of the source streams the partial result of which are
	 *            to be removed.
	 * 
	 */
	protected final void removeEffectsOfPartialResult(int streamId) {
		// Just increment the current partial id
		++currPartialResultId;
	}

	/**
	 * This function creates a group tuple given the grouped result
	 * 
	 * @param groupedResult
	 *            The grouped result
	 * @param representativeTuple
	 *            The representative tuple for the group
	 * @param partial
	 *            Whether the tuple is a partial or final result
	 * 
	 * @return A tuple with the grouped result
	 */
	// HERE
	protected Tuple createTuple(BaseAttr groupedResult,
			Tuple representativeTuple, boolean partial) {

		// Create a result tuple element tagged appropriately as
		// partial or final
		Tuple tupleElement = new Tuple(partial);

		// For each grouping attribute, add the corresponding element
		// to the result tuple from the representative tuple

		for (int grp = 0; grp < numGroupingAttributes; ++grp) {
			// Append the relevant attribute from the representative tuple
			// to the result
			if (representativeTuple != null)
				tupleElement.appendAttribute(representativeTuple
						.getAttribute(attributeIds[grp]));
			else
				tupleElement.appendAttribute(null);
		}

		// Add the grouped result as the attribute
		tupleElement.appendAttribute(groupedResult);

		// Return the result tuple
		return tupleElement;
	}

	public void setResultDocument(Document doc) {
		this.doc = doc;
	}

	// ///////////////////////////////////////////////////////////////////////
	// These functions are the hooks that are used to implement specific //
	// group operators //
	// ///////////////////////////////////////////////////////////////////////

	/**
	 * This function is called to initialize a grouping operator for execution
	 * by setting up relevant structures etc.
	 */
	protected abstract void initializeForExecution();

	/* do initialization - called from initFrom */
	// protected abstract void localInitFrom(LogicalOp logicalOperator);
	protected abstract PhysicalWindowGroup localCopy(); // called from copy()

	protected abstract boolean localEquals(Object o); // called from equals()

	/**
	 * This function constructs a ungrouped result from a tuple
	 * 
	 * @param tupleElement
	 *            The tuple to construct the ungrouped result from
	 * 
	 * @return The constructed object; If no object is constructed, returns null
	 */

	protected abstract Object constructUngroupedResult(Tuple tupleElement)
			throws ShutdownException;

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

	protected abstract Object mergeResults(Object groupedResult,
			Object ungroupedResult);

	/**
	 * This function returns an empty result in case there are no groups
	 * 
	 * @return The result when there are no groups. Returns null if no result is
	 *         to be constructed
	 */

	protected abstract BaseAttr constructEmptyResult();

	/**
	 * This function constructs a result from the grouped partial and final
	 * results of a group. Both partial result and final result cannot be null.
	 * 
	 * @param partialResult
	 *            The partial results of the group (this can be null)
	 * @param finalResult
	 *            The final results of the group (this can be null)
	 * 
	 * @return A results merging partial and final results; If no such result,
	 *         returns null
	 */

	protected abstract BaseAttr constructResult(Object partialResult,
			Object finalResult);

	public boolean isStateful() {
		return true;
	}

	public Cost findLocalCost(ICatalog catalog, LogicalProperty[] inputLogProp) {
		// XXX vpapad: really naive. Only considers the hashing cost
		float inpCard = inputLogProp[0].getCardinality();
		float outputCard = logProp.getCardinality();

		double cost = inpCard * catalog.getDouble("tuple_reading_cost");
		cost += inpCard * catalog.getDouble("tuple_hashing_cost");
		cost += outputCard * catalog.getDouble("tuple_construction_cost");
		return new Cost(cost);
	}

	public void getInstrumentationValues(
			ArrayList<String> instrumentationNames,
			ArrayList<Object> instrumentationValues) {
		if (instrumented) {
			instrumentationNames.add("open groups");
			if (hashtable == null)
				instrumentationValues.add(0);
			else
				instrumentationValues.add(hashtable.size());
			super.getInstrumentationValues(instrumentationNames,
					instrumentationValues);
		}
	}
}
