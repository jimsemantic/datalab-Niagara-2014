package niagara.physical;


import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import niagara.logical.FillFrame;
import niagara.optimizer.colombia.Cost;
import niagara.optimizer.colombia.ICatalog;
import niagara.optimizer.colombia.LogicalOp;
import niagara.optimizer.colombia.LogicalProperty;
import niagara.optimizer.colombia.Op;
import niagara.query_engine.TupleSchema;
import niagara.utils.BaseAttr;
import niagara.utils.FrameDetectorForFillFrame;
import niagara.utils.FrameDetectorTupleForFillFrame;
import niagara.utils.IntegerAttr;
import niagara.utils.Log;
import niagara.utils.Punctuation;
import niagara.utils.ShutdownException;
import niagara.utils.StringAttr;
import niagara.utils.TSAttr;
import niagara.utils.Tuple;


/**
 * This is the <code>PhysicalFillFrameOperator</code> that extends the
 * <code>PhysicalOperator</code> with the implementation of fill frame operator
 * 
 * @version 1.0
 * @author moorthy
 * 
 */
public class PhysicalFillFrame extends PhysicalOperator
{
	
	// No blocking source streams
	private static final boolean[]          blockingSourceStreams          = { false, false };
	private final boolean                   OVERRIDE_DEBUG                 = true;
	  
	private String fs_groupByAttributeName;        
	private String fs_frameStartAttributeName;     
	private String fs_frameEndAttributeName;       
	private String fs_frameIdAttributeName;        
	private String ff_groupByAttributeName;        
	private String ff_timeAttributeName;
	
	private boolean inputTupleSchemaLocationsKnown = false;
	
	//attributes of frame stream
	private int fs_groupByAttributeInputSchemaPosition;
	private int fs_frameIdAttributeInputSchemaPosition;
	private int fs_frameStartAttributeInputSchemaPosition;
	private int fs_frameEndAttributeInputSchemaPosition;
	
	//attributes of fill frame stream
	private int ff_groupByAttributeInputSchemaPosition;	
	private int ff_timeAttributeInputSchemaPosition;
	
	private int tupleArrival = 0;
	//to process frameId for frame stream - timestamp/scan
	//AtomicEvaluator ae;
	
	// Data template for creating punctuation
	Tuple tupleDataSample = null;
	private Document doc;
	
	private HashMap<Integer, FrameDetectorForFillFrame> frameStreamHashMap = new HashMap<Integer, FrameDetectorForFillFrame>();

	
	//buffer of fillframe stream tuples
	//private ArrayList<Tuple> fillFrameTupleBuffer = new ArrayList<Tuple>();	
	//private HashMap<Long,Tuple> fillFrameTupleBuffer = new HashMap<Long, Tuple>();
	private HashMap<Integer, LinkedList<Tuple>> fillFrameTupleBuffer = new HashMap<Integer, LinkedList<Tuple>>();
	//private HashMap<Integer, TreeMap<Long,Tuple>> fillFrameTupleBuffer = new HashMap<Integer, TreeMap<Long,Tuple>>();
	
	private double min_punctFrames_fftuplebuffer_totallength;
	private double max_punctFrames_fftuplebuffer_totallength;
	private double sum_punctFrames_fftuplebuffer_totallength;
	private double count_punctFrames_whenlistempty;
	private double count_punctFrames_whenlistnotempty;
	
	private double min_punctFrames_fftuplebuffer_lengthwalked;
	private double max_punctFrames_fftuplebuffer_lengthwalked;
	private double sum_punctFrames_fftuplebuffer_lengthwalked;
	
	private double sum_percent_punctFrames_fftuplebuffer_itemstossed;
	//punctFillframe in FrameDetectorForFillFrame
	
	private double min_frame_fftuplebuffer_totallength;
	private double max_frame_fftuplebuffer_totallength;
	private double sum_frame_fftuplebuffer_totallength;
	private double count_frames_whenlistempty;
	private double count_frames_whenlistnotempty;
	
	private double min_frame_fftuplebuffer_lengthwalked;
	private double max_frame_fftuplebuffer_lengthwalked;
	private double sum_frame_fftuplebuffer_lengthwalked;
	
	private double sum_percent_frame_fftuplebuffer_itemstossed;
	
	/*private double min_fillframe_framelist_totallength;
	private double max_fillframe_framelist_totallength;
	private double sum_fillframe_framelist_totallength;
	private double count_fillframe;
	
	private double min_fillframe_framelist_lengthwalked;
	private double max_fillframe_framelist_lengthwalked;
	private double sum_fillframe_framelist_lengthwalked;
	
	private double sum_percent_fillframe_fftuplebuffer_itemstossed;*/
	
	
	
	public PhysicalFillFrame() {
		this.setBlockingSourceStreams(PhysicalFillFrame.blockingSourceStreams);
		this.logging = false;
		this.log = new Log("fillframe");
	}
	
	public void constructTupleSchema(TupleSchema[] inputSchemas) {
		super.constructTupleSchema(inputSchemas);
		
		if (inputTupleSchemaLocationsKnown == false)
	    {
	      lookupTupleSchemaLocations();
	    }
	}
	
	private int getAttributePosition(TupleSchema schema, String attributeName) { 
	    return schema.getPosition(attributeName);
	  }
	
	private void lookupTupleSchemaLocations() {
		fs_groupByAttributeInputSchemaPosition = getAttributePosition(inputTupleSchemas[1], fs_groupByAttributeName);    
		fs_frameIdAttributeInputSchemaPosition = getAttributePosition(inputTupleSchemas[1], fs_frameIdAttributeName);
		fs_frameStartAttributeInputSchemaPosition = getAttributePosition(inputTupleSchemas[1], fs_frameStartAttributeName);
		fs_frameEndAttributeInputSchemaPosition = getAttributePosition(inputTupleSchemas[1], fs_frameEndAttributeName);
		ff_groupByAttributeInputSchemaPosition = getAttributePosition(inputTupleSchemas[0], ff_groupByAttributeName);
		ff_timeAttributeInputSchemaPosition = getAttributePosition(inputTupleSchemas[0], ff_timeAttributeName);
	    inputTupleSchemaLocationsKnown = true;
	  }
	
	private String getValue(Tuple tuple, int attributeLoc) {
	    String value;
	    value = ((BaseAttr) tuple.getAttribute(attributeLoc)).attrVal().toString();
	    return value;
	  }
	
	@Override
    protected void processTuple(final Tuple tupleElement, final int streamId)
      throws ShutdownException, InterruptedException	  {
		
		if(1 == streamId){ //framestream
			// Extract information from tuple    
		    int fs_groupByAttributeValue = Integer.parseInt(getValue(tupleElement, fs_groupByAttributeInputSchemaPosition));
		    int fs_frameIdAttributeValue = Integer.parseInt(getValue(tupleElement, fs_frameIdAttributeInputSchemaPosition));
		    long fs_frameStartAttributeValue = Long.parseLong(getValue(tupleElement, fs_frameStartAttributeInputSchemaPosition));
		    long fs_frameEndAttributeValue = Long.parseLong(getValue(tupleElement, fs_frameEndAttributeInputSchemaPosition));
		    //System.out.println("Frame:"+fs_frameIdAttributeValue+" "+fs_frameStartAttributeValue+" "+fs_frameEndAttributeValue);
		    FrameDetectorTupleForFillFrame fdTuple = new FrameDetectorTupleForFillFrame(fs_frameIdAttributeValue, fs_frameStartAttributeValue, fs_frameEndAttributeValue, fs_groupByAttributeValue);
		    
			
		    //Integer fs_groupby = new Integer(fs_groupByAttributeValue);
		    if(fillFrameTupleBuffer.containsKey(fs_groupByAttributeValue)){
		    	
		    	count_frames_whenlistnotempty++;
		    	
		    	int countItemsWalked = 0;
		    	int countItemsTossed = 0;
		    	
		    	LinkedList<Tuple> fillframeTupleList = fillFrameTupleBuffer.get(fs_groupByAttributeValue);
		    	if(fillframeTupleList.size()<min_frame_fftuplebuffer_totallength){
		    		min_frame_fftuplebuffer_totallength = fillframeTupleList.size();
		    	}
		    	if(fillframeTupleList.size()>max_frame_fftuplebuffer_totallength){
		    		max_frame_fftuplebuffer_totallength = fillframeTupleList.size();
		    	}
		    	sum_frame_fftuplebuffer_totallength += fillframeTupleList.size();
		    	//System.out.print("Frame:\nffbuffersize-"+fillframeTupleList.size());
		    	
		    	//countElemInFillFrameTupleBuffer = fillframeTupleList.size();
		    	if(null!=fillframeTupleList){
			    	Iterator<Tuple> ffTupleListIter = fillframeTupleList.iterator();
			    	while(ffTupleListIter.hasNext()){
			    		Tuple ffTuple = ffTupleListIter.next();
			    		int ff_groupByAttributeValue = Integer.parseInt(getValue(ffTuple, ff_groupByAttributeInputSchemaPosition));
						long ff_frameTimeAttributeValue = Long.parseLong(getValue(ffTuple, ff_timeAttributeInputSchemaPosition));
						
						//assumes filltuples comes in order
						/*if(ff_frameTimeAttributeValue>fs_frameEndAttributeValue){
							break;
						}*/
						countItemsWalked++;
			    		if(fdTuple.isTupleInFrame(ff_groupByAttributeValue, ff_frameTimeAttributeValue)){
			    			ffTuple.appendAttribute(new IntegerAttr(fs_frameIdAttributeValue));
			    			//System.out.print("-fm");
							putTuple(ffTuple, 0);
							ffTupleListIter.remove();
							countItemsTossed++;
			    		}//end of if
			    	}//end of while 
			    	
			    	if(countItemsWalked<min_frame_fftuplebuffer_lengthwalked){
			    		min_frame_fftuplebuffer_lengthwalked = countItemsWalked;
			    	}
			    	if(countItemsWalked>max_frame_fftuplebuffer_lengthwalked){
			    		max_frame_fftuplebuffer_lengthwalked = countItemsWalked;
			    	}
			    	sum_frame_fftuplebuffer_lengthwalked += countItemsWalked;
			    	
			    	if(0!=countItemsWalked){
			    		sum_percent_frame_fftuplebuffer_itemstossed += (countItemsTossed*100)/countItemsWalked;
			    	}
			    	
			    	//System.out.println("\ncountItemsWalked-"+countItemsWalked+"\ncountItemsTossed-"+countItemsTossed);
			    	//this.log.Update(""+(++tupleArrival),""+fs_frameEndAttributeValue+","+countElemInFillFrameTupleBuffer+"|"+countTuplesRemovedInFillFrameTupleBuffer+",fs");
			    	
		    	}//end of if
		    
		    }//end of if
		    else {
		    	count_frames_whenlistempty++;;
		    }
		    		    	
		    
		    
		    
			// See if we already have a frame for this groupby attribute
		    if (this.frameStreamHashMap.containsKey(fs_groupByAttributeValue))
		    {
		       FrameDetectorForFillFrame fDetectorX = this.frameStreamHashMap.get(fs_groupByAttributeValue);
		       fDetectorX.insertTupleIntoFrame(fdTuple);
		    }
		    else
		    {
		    	FrameDetectorForFillFrame new_fBuilderX = new FrameDetectorForFillFrame(fs_groupByAttributeValue);
		        this.frameStreamHashMap.put(fs_groupByAttributeValue, new_fBuilderX);
		        new_fBuilderX.insertTupleIntoFrame(fdTuple);
		    }  
		    //System.out.println("end of processTuple for frameStream"+" "+fs_frameStartAttributeValue);
		    
		} else { //fillframestream
			
			// If we haven't already picked up a template tuple,
			// copy this one.
			if (tupleDataSample == null)
				tupleDataSample = tupleElement;
			
			// Extract information from tuple    
		    int ff_groupByAttributeValue = Integer.parseInt(getValue(tupleElement, ff_groupByAttributeInputSchemaPosition));
		    long ff_timeAttributeValue = Long.parseLong(getValue(tupleElement, ff_timeAttributeInputSchemaPosition));
		    
		    //System.out.println("Filling tuple:"+ff_timeAttributeValue);
		    boolean ff_frameIdentifiedFlag = false;
		    //check if tuple belongs to frame already in frameStreamHashMap, then purge else put in buffer
		    if (this.frameStreamHashMap.containsKey(ff_groupByAttributeValue))
		    {
		    	FrameDetectorForFillFrame frameDetectorByGroupby = frameStreamHashMap.get(ff_groupByAttributeValue); 
		    	if(!frameDetectorByGroupby.getFrameTuples().isEmpty()){
			    	int frameid = frameDetectorByGroupby.searchFrame(frameDetectorByGroupby.getFrameTuples(), ff_timeAttributeValue);
			    	if(-1!=frameid){
			    		//System.out.print(":ff");
				    	tupleElement.appendAttribute(new IntegerAttr(frameid));
						//putTuple(tupleElement, streamId);
		    			putTuple(tupleElement, 0);
						ff_frameIdentifiedFlag = true;
			    	}
		    	}
		    	
		    } 
		    
		  //if fill frame tuple belongs to groupby attr not in hashmap or tuple not in start and end time boundary
		    if(!ff_frameIdentifiedFlag) { 
		    	//System.out.print(":fnf");
		    	//Integer ff_groupby = new Integer(ff_groupByAttributeValue);
		    	LinkedList<Tuple> fillframeTupleList;
		    	if(fillFrameTupleBuffer.containsKey(ff_groupByAttributeValue)){
		    		fillframeTupleList = fillFrameTupleBuffer.get(ff_groupByAttributeValue);
		    		fillframeTupleList.add(tupleElement);
		    	} else {
		    		fillframeTupleList = new LinkedList<Tuple>();
		    		fillframeTupleList.add(tupleElement);
		    		fillFrameTupleBuffer.put(ff_groupByAttributeValue,fillframeTupleList);
		    	}
		    	//this.log.Update(""+(++tupleArrival),""+ff_timeAttributeValue+","+fillframeTupleList.size()+",ff");
		    	
		    }
		   // System.out.println("end of processTuple for fillframeStream"+" "+ff_timeAttributeValue);
		}
	  
	}
	
	protected void processPunctuation(Punctuation inputTuple, int streamId)
			  throws ShutdownException, InterruptedException  {
		//Vector<FrameDetectorTupleForFillFrame> framesToOutputX = new Vector<FrameDetectorTupleForFillFrame>();
	    
		//process punctuation only on fill frame stream
		if(0==streamId){
			
			String pGroupBy = getValue(inputTuple, ff_groupByAttributeInputSchemaPosition);
			long ff_frameStartAttributeValue = Long.parseLong(getValue(inputTuple, ff_timeAttributeInputSchemaPosition));
			//long fs_frameEndAttributeValue = Long.parseLong(getValue(inputTuple, fs_frameEndAttributeInputSchemaPosition));
			
			/*if(null != fillFrameTupleBuffer && null != fillFrameTupleBuffer.get("1129")){
				this.log.Update(""+(++tupleArrival),""+ff_frameStartAttributeValue+","+fillFrameTupleBuffer.get("1129").size()+",ff");
			} else {
				this.log.Update(""+(++tupleArrival),""+ff_frameStartAttributeValue+",0,ff");
			}*/
			//System.out.println("Punct on Filling:"+ff_frameStartAttributeValue);
			
			if(!pGroupBy.equals("*"))
		    {
		    	int groupByAsInt = Integer.parseInt(pGroupBy);
		    	long punctuatedFrameId = frameStreamHashMap.get(groupByAsInt).processPunctuationOnTimeAttribute(ff_frameStartAttributeValue);
		    	// Pass on punctuation.
		    	if(-1 != punctuatedFrameId){
		    		putTuple(createPunctuation(inputTuple, punctuatedFrameId), 0);
		    	}
		    }
		    else
		    {
		    	for (Map.Entry<Integer, FrameDetectorForFillFrame> entry: frameStreamHashMap.entrySet()) 
		        {
		    		long punctuatedFrameId = entry.getValue().processPunctuationOnTimeAttribute(ff_frameStartAttributeValue);
		    		// Pass on punctuation.
			    	if(-1 != punctuatedFrameId){
			    		putTuple(createPunctuation(inputTuple, punctuatedFrameId), 0);
			    	}
		        }
		    	
		    	
		    }
			
		    
		} else { //process punctuation only on frame stream
			
			long fs_frameEndAttributeValue = Long.parseLong(getValue(inputTuple, fs_frameStartAttributeInputSchemaPosition));
			String pGroupBy = getValue(inputTuple, fs_groupByAttributeInputSchemaPosition);
			//System.out.println("\nend of processPunctuation for frameStream"+" "+fs_frameStartAttributeValue);
			
			//System.out.println("Punct on Frame:"+fs_frameEndAttributeValue);
			// Case 1: Time and GroupBy Attribute (routerid)
		    if(!pGroupBy.equals("*"))
		    {
		    	int groupByAsInt = Integer.parseInt(pGroupBy);
		    	if(fillFrameTupleBuffer.containsKey(groupByAsInt)){
		    		LinkedList<Tuple> fillframeTupleList = fillFrameTupleBuffer.get(groupByAsInt);
		    		if(null!=fillframeTupleList){
				    	Iterator<Tuple> ffTupleListIter = fillframeTupleList.iterator();
				    	while(ffTupleListIter.hasNext()){
				    		Tuple ffTuple = ffTupleListIter.next();
							long ff_frameTimeAttributeValue = Long.parseLong(getValue(ffTuple, ff_timeAttributeInputSchemaPosition));
				    		if(ff_frameTimeAttributeValue <= fs_frameEndAttributeValue){
								ffTupleListIter.remove();
				    		}//end of if
				    		//assumes filltuple comes in order
				    		/*else {
				    			break;
				    		}*/
				    	}//end of while 
				    	
				    	//this.log.Update(""+(++tupleArrival),""+fs_frameEndAttributeValue+","+countElemInFillFrameTupleBuffer+"|"+countTuplesRemovedInFillFrameTupleBuffer+",fs");
				    	
			    	}//end of if
		    	}
		    }
		    else
		    {
		    	for (Map.Entry<Integer, LinkedList<Tuple>> entry: fillFrameTupleBuffer.entrySet()) 
		        {
		    		LinkedList<Tuple> fillframeTupleList = entry.getValue();
		    		
		    		if(fillframeTupleList.size()<min_frame_fftuplebuffer_totallength){
			    		min_punctFrames_fftuplebuffer_totallength = fillframeTupleList.size();
			    	}
			    	if(fillframeTupleList.size()>max_frame_fftuplebuffer_totallength){
			    		max_punctFrames_fftuplebuffer_totallength = fillframeTupleList.size();
			    	}
			    	sum_punctFrames_fftuplebuffer_totallength += fillframeTupleList.size();
			    	//System.out.print("Punct on frame:\nffbuffersize-"+fillframeTupleList.size());
		    		
			    	int countItemsWalked = 0;
			    	int countItemsTossed = 0;
		    		if(null!=fillframeTupleList){
		    			count_punctFrames_whenlistnotempty++;
		    			
				    	Iterator<Tuple> ffTupleListIter = fillframeTupleList.iterator();
				    	while(ffTupleListIter.hasNext()){
				    		countItemsWalked++;
				    		Tuple ffTuple = ffTupleListIter.next();
							long ff_frameTimeAttributeValue = Long.parseLong(getValue(ffTuple, ff_timeAttributeInputSchemaPosition));
				    		if(ff_frameTimeAttributeValue <= fs_frameEndAttributeValue){
								ffTupleListIter.remove();
								countItemsTossed++;
				    		}//end of if
				    		//assumes filltuple comes in order
				    		/*else {
				    			break;
				    		}*/
				    	}//end of while 
				    	
				    	//this.log.Update(""+(++tupleArrival),""+fs_frameEndAttributeValue+","+countElemInFillFrameTupleBuffer+"|"+countTuplesRemovedInFillFrameTupleBuffer+",fs");
				    	
			    	}//end of if 
		    		else {
		    			count_punctFrames_whenlistempty++;;
		    		}
		    		
		    		if(countItemsWalked<min_punctFrames_fftuplebuffer_lengthwalked){
			    		min_punctFrames_fftuplebuffer_lengthwalked = countItemsWalked;
			    	}
			    	if(countItemsWalked>max_punctFrames_fftuplebuffer_lengthwalked){
			    		max_punctFrames_fftuplebuffer_lengthwalked = countItemsWalked;
			    	}
			    	sum_punctFrames_fftuplebuffer_lengthwalked += countItemsWalked;
			    	//System.out.println("\ncountItemsWalked-"+countItemsWalked+"\ncountItemsTossed-"+countItemsTossed);
			    	
			    	if(0!=countItemsWalked){
			    		sum_percent_punctFrames_fftuplebuffer_itemstossed += (countItemsTossed*100)/countItemsWalked;
			    	}
		        }
		    	
		    	
		    	
		    }
			//purge tuples from fillframebuffer with ts <= frameEndTime
			
			/*if(null != fillFrameTupleBuffer && null != fillFrameTupleBuffer.get("1129")){
				this.log.Update(""+(++tupleArrival),""+fs_frameStartAttributeValue+","+fillFrameTupleBuffer.get("1129").size()+",fs");
			} else {
				this.log.Update(""+(++tupleArrival),""+fs_frameStartAttributeValue+",0,fs");
			}*/
			
			
		}
	}
	
	/**
	 * This function generates a punctuation based on the timer value using the
	 * template generated by setupDataTemplate
	 */
	private Punctuation createPunctuation(Tuple inputTuple, long value) {
		// This input came from the timer. Generate punctuation
		// based on the timer value, where the time value is
		// (,last), indicating that all values from the beginning
		// to `last' have been seen. Note this assumes the
		// attribute is strictly non-decreasing.
		// Element eChild;
		// Text tPattern;
		short nodeType;

		// Create a new punctuation element
		Punctuation spe = new Punctuation(false);
		for (int iAttr = 0; iAttr < tupleDataSample.size(); iAttr++) {
			// Node ndSample = tupleDataSample.getAttribute(iAttr);
			Object ndSample = tupleDataSample.getAttribute(iAttr);

			if (ndSample instanceof BaseAttr) {
				spe.appendAttribute(((BaseAttr) ndSample).copy());
			} else if (ndSample instanceof Node) {
				nodeType = ((Node) ndSample).getNodeType();
				if (nodeType == Node.ATTRIBUTE_NODE) {
					Attr attr = doc.createAttribute(((Node) ndSample)
							.getNodeName());
					attr.appendChild(doc.createTextNode("*"));
					spe.appendAttribute(attr);
				} else {
					String stName = ((Node) ndSample).getNodeName();
					if (stName.compareTo("#document") == 0)
						stName = new String("document");
					Element ePunct = doc.createElement(stName);
					ePunct.appendChild(doc.createTextNode("*"));
					spe.appendAttribute(ePunct);
				}
			}
		}
		// tPattern = doc.createTextNode("(," +Long.toString(value) + ")");
		// hack
		/*
		 * tPattern = doc.createTextNode(Long.toString(value));
		 * 
		 * eChild = doc.createElement("wid_from_"+widName);
		 * eChild.appendChild(tPattern); spe.appendAttribute(eChild);
		 * 
		 * tPattern = doc.createTextNode("*");
		 * 
		 * eChild = doc.createElement("wid_to_"+widName);
		 * eChild.appendChild(tPattern); spe.appendAttribute(eChild);
		 */
		spe.appendAttribute(new StringAttr(value));

		return spe;
	}
	
	protected Tuple appendFrameId(Tuple inputTuple, int frameId, int steamId)
			throws ShutdownException, InterruptedException {

		inputTuple.appendAttribute(frameId);
		return inputTuple;
	}
	
	@Override
	public boolean isStateful() {
		 return true;
	}

	@Override
	protected void opInitFrom(LogicalOp op) {
		fs_groupByAttributeName = ((FillFrame) op).getFs_groupByAttributeName();
		fs_frameStartAttributeName = ((FillFrame) op).getFs_frameStartAttributeName();
		fs_frameEndAttributeName = ((FillFrame) op).getFs_frameEndAttributeName();
		fs_frameIdAttributeName = ((FillFrame) op).getFs_frameIdAttributeName();
		ff_groupByAttributeName = ((FillFrame) op).getFf_groupByAttributeName();
		ff_timeAttributeName = ((FillFrame) op).getFf_timeAttributeName();
	}

	@Override
	public Cost findLocalCost(ICatalog catalog, LogicalProperty[] InputLogProp) {
		// XXX vpapad: really naive. Only considers the hashing cost
	    final float inpCard = InputLogProp[0].getCardinality();
	    final float outputCard = this.logProp.getCardinality();

	    double cost = inpCard * catalog.getDouble("tuple_reading_cost");
	    cost += inpCard * catalog.getDouble("tuple_hashing_cost");
	    cost += outputCard * catalog.getDouble("tuple_construction_cost");
	    return new Cost(cost);
	}

	@Override
	public Op opCopy() {
		final PhysicalFillFrame frame = new PhysicalFillFrame();
		frame.fs_groupByAttributeName = this.fs_groupByAttributeName;
		frame.fs_frameStartAttributeName = this.fs_frameStartAttributeName;
		frame.fs_frameEndAttributeName = this.fs_frameEndAttributeName;
		frame.fs_frameIdAttributeName = this.fs_frameIdAttributeName;
		frame.ff_groupByAttributeName = this.ff_groupByAttributeName;
		frame.ff_timeAttributeName = this.ff_timeAttributeName;
	    return frame;
	}

	@Override
	public boolean equals(Object other) {
		if ((other == null) || !(other instanceof PhysicalFillFrame))
	    {
	      return false;
	    }
	    if (other.getClass() != PhysicalFillFrame.class)
	    {
	      return other.equals(this);
	    }
	    final PhysicalFillFrame f = (PhysicalFillFrame) other;
	    return this.fs_groupByAttributeName.equals(f.fs_groupByAttributeName)
	    		&& this.fs_frameStartAttributeName.equals(f.fs_frameStartAttributeName)
	    		&& this.fs_frameEndAttributeName.equals(f.fs_frameEndAttributeName)
	    		&& this.fs_frameIdAttributeName.equals(f.fs_frameIdAttributeName)
	    		&& this.ff_groupByAttributeName.equals(f.ff_groupByAttributeName)
	    		&& this.ff_timeAttributeName.equals(f.ff_timeAttributeName);
	}

	@Override
	public int hashCode() {
		return getArity();
	}
  
	//when stream is closed, then flush any frames still open in memory
	  public void streamClosed(int streamId) throws ShutdownException {
		  
		  //print all logging information
		  System.out.println("Frames-------------------------------------------------------------------------------------");
		  System.out.println("count of frames- 1)when fftuplebuffer is empty:"+count_frames_whenlistempty+" and 2)when fftuplebuffer is not emtpy:"+count_frames_whenlistnotempty);
		  
		  System.out.println("\nmin fftuplebuffer total length over all frames:"+min_frame_fftuplebuffer_totallength);
		  System.out.println("max fftuplebuffer total length over all frames:"+max_frame_fftuplebuffer_totallength);
		  System.out.println("sum fftuplebuffer total length over all frames:"+sum_frame_fftuplebuffer_totallength);
		  System.out.println("avg fftuplebuffer total length over all frames:"+(sum_frame_fftuplebuffer_totallength/(count_frames_whenlistempty+count_frames_whenlistnotempty)));
		  
		  System.out.println("\nmin fftuplebuffer length walked over all frames:"+min_frame_fftuplebuffer_lengthwalked);
		  System.out.println("max fftuplebuffer length walked over all frames:"+max_frame_fftuplebuffer_lengthwalked);
		  System.out.println("sum fftuplebuffer length walked over all frames:"+sum_frame_fftuplebuffer_lengthwalked);
		  System.out.println("avg fftuplebuffer length walked over all frames:"+(sum_frame_fftuplebuffer_lengthwalked/(count_frames_whenlistempty+count_frames_whenlistnotempty)));
		  
		  System.out.println("\navg percentage items tossed over all frames:"+(sum_percent_frame_fftuplebuffer_itemstossed/(count_frames_whenlistempty+count_frames_whenlistnotempty)));
		  
		  //print all logging information
		  System.out.println("Punctuation on Frame-------------------------------------------------------------------------------------");
		  System.out.println("count of frame punctuations- 1)when fftuplebuffer is empty:"+count_punctFrames_whenlistempty+" and 2)when fftuplebuffer is not emtpy:"+count_punctFrames_whenlistnotempty);
		  
		  System.out.println("\nmin fftuplebuffer total length over all frame punctuations:"+min_punctFrames_fftuplebuffer_totallength);
		  System.out.println("max fftuplebuffer total length over all frame punctuations:"+max_punctFrames_fftuplebuffer_totallength);
		  System.out.println("sum fftuplebuffer total length over all frame punctuations:"+sum_punctFrames_fftuplebuffer_totallength);
		  System.out.println("avg fftuplebuffer total length over all frame punctuations:"+(sum_punctFrames_fftuplebuffer_totallength/(count_punctFrames_whenlistempty+count_punctFrames_whenlistnotempty)));
		  
		  System.out.println("\nmin fftuplebuffer length walked over all frame punctuations:"+min_punctFrames_fftuplebuffer_lengthwalked);
		  System.out.println("max fftuplebuffer length walked over all frame punctuations:"+max_punctFrames_fftuplebuffer_lengthwalked);
		  System.out.println("sum fftuplebuffer length walked over all frame punctuations:"+sum_punctFrames_fftuplebuffer_lengthwalked);
		  System.out.println("avg fftuplebuffer length walked over all frame punctuations:"+(sum_punctFrames_fftuplebuffer_lengthwalked/(count_punctFrames_whenlistempty+count_punctFrames_whenlistnotempty)));
		  
		  System.out.println("\navg percentage items tossed over all frame punctuations:"+(sum_percent_punctFrames_fftuplebuffer_itemstossed/(count_punctFrames_whenlistempty+count_punctFrames_whenlistnotempty)));
	  }
  
}
