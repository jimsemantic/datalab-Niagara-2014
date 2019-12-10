package niagara.logical;

import niagara.connection_server.Catalog;
import niagara.connection_server.InvalidPlanException;
import niagara.optimizer.colombia.ICatalog;
import niagara.optimizer.colombia.LogicalProperty;
import niagara.optimizer.colombia.Op;

import org.w3c.dom.Element;

/**
 * @author rfernand
 * @version 1.0
 * 
 * The <code>Instrument3</code> logical operator drives feedback punctuation generation.
 *
 */
public class Instrument3 extends UnaryOperator {

	private long interval;
	private Boolean logging;
	private Boolean propagate;
	private Boolean passpunct;

	private String fAttrs;
	private String comparators;
	private String values;
	
	
	public Instrument3() {
		interval = 0;
		logging = false;
		propagate = false;
		passpunct = true;
	}

	public long getInterval(){
		return interval;
	}
	
	public Boolean getLogging() {
		return logging;
	}
	
	public Boolean getPropagate(){
		return propagate;
	}
	
	public Boolean getPrintPunct()
	{
		return passpunct;
	}
	
	public String getFAttrs()
	{
		return fAttrs;
	}

	public String getComparators()
	{
		return comparators;
	}

	public String getValues()
	{
		return values;
	}

	
	
	public void loadFromXML(Element e, LogicalProperty[] inputProperties, Catalog catalog) throws InvalidPlanException {
		interval = Long.parseLong(e.getAttribute("interval"));
		String l = e.getAttribute("log").toString();
		String p = e.getAttribute("propagate").toString();
		String pp = e.getAttribute("printpunct").toString();
		
		if(l.equals("yes"))
			logging = true;
		else logging = false;
		
		if(p.equals("yes"))
			propagate = true;
		else propagate = false;

		if(pp.equals("yes"))
			passpunct = true;
		else passpunct = false;

		
/*		String fAttrStr = e.getAttribute("fattrs");
		if (fAttrStr.length() == 0)
			throw new InvalidPlanException("Bad value for 'fattrs' for : "
					+ id);

		String[] punctAttrs = fAttrStr.split("[\t| ]+");
		if (punctAttrs.length != 2)
			throw new InvalidPlanException("Bad value for 'fattrs' for : "
					+ id);
*/
		fAttrs = e.getAttribute("fattrs").toString();
		values = e.getAttribute("values").toString();
		comparators = e.getAttribute("comparators").toString();
		
	}


	@Override
	public boolean equals(Object other) {

		if(other == null || !(other instanceof Instrument3))
			return false;
		if(((Instrument3)other).logging != this.logging)
			return false;
		if(((Instrument3)other).propagate != this.propagate)
			return false;
		if(((Instrument3)other).passpunct != this.passpunct)
			return false;

		if(((Instrument3)other).fAttrs != this.fAttrs)
			return false;
		if(((Instrument3)other).values != this.values)
			return false;
		if(((Instrument3)other).comparators != this.comparators)
			return false;

		
		if(other.getClass() != Instrument3.class)
			return other.equals(this);

		Instrument3 ot = (Instrument3)other;

		if(ot.interval != this.interval) 
		return false;

		return true;

	}

	
	public void dump() {
		System.out.println("Expensive :");
		System.out.println("Interval: " + String.valueOf(interval));
		System.out.println("Log: " + logging);
		System.out.println("Propagate: " + propagate);
	}

	public String toString() {
		return " expensive " ;
	}

	@Override
	public int hashCode() {
		return String.valueOf(interval).hashCode() ^ values.hashCode() ^ comparators.hashCode() ^ fAttrs.hashCode();
	}

	@Override
	public Op opCopy() {
		Instrument3 op = new Instrument3();
		op.interval = interval;
		op.propagate = propagate;
		op.passpunct = passpunct;
		op.logging = this.logging;
		op.fAttrs = fAttrs;
		op.comparators = comparators;
		op.values = values;
		return op;	
	}

	@Override
	public LogicalProperty findLogProp(ICatalog catalog, LogicalProperty[] input) {
		return input[0].copy();
	}
}
