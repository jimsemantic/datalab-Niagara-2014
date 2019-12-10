/*  
   NEXMark Generator -- Niagara Extension to XMark Data Generator

   Acknowledgements:
   The NEXMark Generator was developed using the xmlgen generator 
   from the XMark Benchmark project as a basis. The NEXMark
   generator generates streams of auction elements (bids, items
   for auctions, persons) as opposed to the auction files
   generated by xmlgen.  xmlgen was developed by Florian Waas.
   See http://www.xml-benchmark.org for information.

   Copyright (c) Dept. of  Computer Science & Engineering,
   OGI School of Science & Engineering, OHSU. All Rights Reserved.

   Permission to use, copy, modify, and distribute this software and
   its documentation is hereby granted, provided that both the
   copyright notice and this permission notice appear in all copies
   of the software, derivative works or modified versions, and any
   portions thereof, and that both notices appear in supporting
   documentation.

   THE AUTHORS AND THE DEPT. OF COMPUTER SCIENCE & ENGINEERING 
   AT OHSU ALLOW USE OF THIS SOFTWARE IN ITS "AS IS" CONDITION, 
   AND THEY DISCLAIM ANY LIABILITY OF ANY KIND FOR ANY DAMAGES 
   WHATSOEVER RESULTING FROM THE USE OF THIS SOFTWARE.

   This software was developed with support from NSF ITR award
   IIS0086002 and from DARPA through NAVY/SPAWAR 
   Contract No. N66001-99-1-8098.

*/

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

class XMLAuctionStreamGenerator {
    
    
    String tab = "";
    String tab2 = "";
    String tab3 = "";
    String nl = "";

    public static final String yesno[] = { "yes", "no"};
    public static final String auction_type[] = {"Regular", "Featured"};
    public static final String XML_DECL = "\n<?xml version=\"1.0\"?>\n";
    public static final String STREAM_OPEN = 
        "<niagara:stream xmlns:niagara =\"http://www.cse.ogi.edu/dot/niagara\">";
    public static final String STREAM_CLOSE =
        "</niagara:stream>";

    // OPTIONS
    public static int ITEMS_PER_PERSON = 10;
    public static int BIDS_PER_ITEM = 10;
    
    public static final boolean USE_SEED = false;
    public static final int SEED = 1837640;
    
    private static int MAXINCREMENT_MILLISEC = 1000;
    private static int WARP = 10;
    private static int DELAY = 24000;    
   
    // BIGDOCS AND BIDSONLY OPTIONS
    // setting NUM_WRITERS > 1 creates that number of files and
    // the generated data is split among those files
    private static final int NUM_WRITERS = 1;
    // setting ONEDOC = true creates one large file as opposed
    // to several small files
    public static final boolean ONEDOC = false;
    public static final boolean WRITE_PERSONS = true;
    public static final boolean WRITE_ITEMS = true;
    
    public boolean LIMIT_ATTRIBUTES = false;
    
    // will generate bids, items and persons in a ratio of 10 bids/item 5 items/person
    private Random rnd;
    private SimpleCalendar cal = new SimpleCalendar();

    private Persons persons = new Persons(); // for managing person ids
    private OpenAuctions openAuctions; // for managing open auctions
    private PersonGen p = new PersonGen();  // used for generating values for person    

    private MyBuffer myBuf;
    private BufferedWriter[] writers;
    private boolean usePrettyPrint;
    private boolean usePunct;
    // used for counting number of times generator is called, num persons, etc
    private int cntGenCalls; 
    private int cntPersons;
    private int cntItems;
    private int cntBids;
    private int cntPunct;
    private int resultSize; // in bytes
    private int charsWritten;
    


    /**
     * @param resultSize Size in chars for the result
     */
    public XMLAuctionStreamGenerator(int resultChars,
                                     boolean prettyprint,
				     boolean punct) {
	 
        resultSize = resultChars;
        usePrettyPrint = prettyprint;
	usePunct = punct;
        openAuctions = new OpenAuctions(cal);
	if(USE_SEED) {
	   rnd = new Random(SEED);
	} else {
           rnd = new Random(103984);
	}

        if(usePrettyPrint) {
            tab = "\t";
            tab2 = "\t\t";
            tab3 = "\t\t\t";
            nl = "\n";
        }

        cntGenCalls = 0;
        cntPersons = 0;
        cntItems = 0;
        cntBids = 0;
	cntPunct = 0;
    }

    public void generateStream(BufferedWriter _dontuse) 
     throws IOException {
        myBuf = new MyBuffer();
        //this.writer = _dontuse;
	//_dontuse.close();

	String sizeStr;
	if(resultSize >= 1048576) {
	   sizeStr = resultSize/1048576 + "MB";
        } else {
	   sizeStr = resultSize/1024 + "KB";
        }

	String[] fn = new String[NUM_WRITERS];
	for(int i = 0; i<NUM_WRITERS; i++) {
           fn[i] = "fh" + sizeStr + "-" + i + ".xml";
	}

        writers = new BufferedWriter[NUM_WRITERS];
	for(int i = 0; i<NUM_WRITERS; i++) {
           writers[i]= new BufferedWriter(new OutputStreamWriter(
	              new FileOutputStream(fn[i])));
        }
        charsWritten = 0;

        if(LIMIT_ATTRIBUTES)
            System.out.println("WARNING: LIMITING ATTRIBUTES");
        if(!WRITE_PERSONS)
            System.out.println("WARNING: NOT WRITING PERSONS");
        if(!WRITE_ITEMS)
            System.out.println("WARNING: NOT WRITING ITEMS");

        // stream declaration
	if(!ONEDOC) {
           myBuf.append(STREAM_OPEN);
           myBuf.append(nl);
        } else {
           myBuf.append("<site>");
           myBuf.append(nl);
        }
        charsWritten += myBuf.length();
	for(int i = 0; i<NUM_WRITERS; i++) {
           writers[i].write(myBuf.array(), 0, myBuf.length());
        }
        
	// first do startup - generate 50 people and 50 open 
	// auctions that can be bid on
	int wIdx=0; 
        if(resultSize > 1000000) {
            // put 50 persons in a document
            for(int i = 0;i<5; i++) {
	        wIdx = i%NUM_WRITERS;
                initMyBuf();
                generatePerson(myBuf, 10);
                if(WRITE_PERSONS)
		   writeMyBuf(writers[wIdx]);
            } 
        
            for(int i = 0; i<5; i++) {
	        wIdx = (wIdx+1)%NUM_WRITERS;
                initMyBuf();
                generateOpenAuction(myBuf, 10);
		if(WRITE_ITEMS)
                   writeMyBuf(writers[wIdx]);
            }
        } else {
            initMyBuf();
            generatePerson(myBuf, 3);
       	    if(WRITE_PERSONS)
               writeMyBuf(writers[0]);

            initMyBuf();
            generateOpenAuction(myBuf, 3);
       	    if(WRITE_ITEMS)
               writeMyBuf(writers[0]); 
        }

        // now go into a loop generating bids and persons and so on
        // want on average 11 items/person and 10 bids/item
        while(charsWritten < resultSize) {
        //while(cntGenCalls < 37089) {
	    wIdx = cntGenCalls%NUM_WRITERS; // writer index
            cntGenCalls++;

            // generating a person approximately 10th time will
            // give is 10 items/person since we generate on average
            // one bid per loop
            initMyBuf();
            if(rnd.nextInt(10) == 0) { 
                generatePerson(myBuf, 1);
            } 
       	    if(WRITE_PERSONS)
	       writeMyBuf(writers[wIdx]);

            // want on average 1 item and 10 bids
            int numItems = rnd.nextInt(3); // should average 1
            initMyBuf();
            generateOpenAuction(myBuf, numItems);
       	    if(WRITE_ITEMS)
               writeMyBuf(writers[wIdx]);
	    
            int numBids = rnd.nextInt(21); // should average 10
            initMyBuf();
            generateBid(myBuf, numBids);
            writeMyBuf(writers[wIdx]);

            // insert a punctuation here, if desired
	    if(usePunct) {
	        if(cntGenCalls%3 == 0) {
		    cntPunct++;
                    myBuf.clear();
                    myBuf.append("<PUNCT_timestamp>");
                    myBuf.append(cal.getTime());
                    myBuf.append("</PUNCT_timestamp>\n");
		    for(int i = 0; i<NUM_WRITERS; i++) {
		       assert NUM_WRITERS == 1;
                       writers[i].write(myBuf.array(), 0,
		                             myBuf.length());
                    }
                }
            }
        }

        myBuf.clear();
	if(!ONEDOC) {
	   myBuf.append(STREAM_CLOSE);
           myBuf.append(nl);
	 } else {   
           myBuf.append("</site>");
           myBuf.append(nl);
         }
	
	for(int i = 0; i<NUM_WRITERS; i++) {
           writers[i].write(myBuf.array(), 0, myBuf.length());
	   writers[i].close();
	}
    }

    private void initMyBuf() throws IOException {
        myBuf.clear();
	if(!ONEDOC) {
           myBuf.append("<site>");
           myBuf.append(nl);
        }
    }
    
    private void writeMyBuf(BufferedWriter bw) throws IOException {
        if(!ONEDOC) {
           myBuf.append("</site>");
           myBuf.append(nl);
        }
        bw.write(myBuf.array(), 0, myBuf.length());
        charsWritten += myBuf.length();
        return;
    } 
    
    private void generateBid(MyBuffer myb, int numBids) throws IOException { 
        cntBids += numBids;

    	long ts=0, temp;
    	boolean start=true;
    	
        myb.append("<open_auctions>");
	
        myb.append(nl);
	
        for (int i=0; i<numBids;i++) {
            int itemId = openAuctions.getExistingId(); 
            myb.append(tab);
            myb.append("<open_auction id=\"");
            myb.append(itemId);
            myb.append("\">");
            myb.append(nl);
	 
            myb.append(tab2);
            myb.append("<bidder>");
            myb.append(nl);
	    
            myb.append(tab3);
            myb.append("<time>");
            myb.append(cal.getTime());
	    cal.incrementTime();
            // Alternate time stamps
            //myb.append(System.currentTimeMillis() - rnd.nextInt(MAXINCREMENT_MILLISEC));     
            //ts = System.currentTimeMillis() * WARP + DELAY;
            //myb.append(ts);	    	
            myb.append("</time>");
            myb.append(nl);
	    
            myb.append(tab3);
            myb.append("<person_ref person=\"");
            myb.append(persons.getExistingId()); 
            myb.append("\"></person_ref>");
            myb.append(nl);
	    
            myb.append(tab3);
            myb.append("<bid>");
            myb.append(openAuctions.increasePrice(itemId));
            myb.append(".00</bid>");
            myb.append(nl);
	    
            myb.append(tab2);
            myb.append("</bidder>");
            myb.append(nl);
	    
            myb.append(tab);
            myb.append("</open_auction>");
            myb.append(nl);
        }
        myb.append("</open_auctions>");
        myb.append(nl);	
    }
    
    // uugh, a bad thing here is that a person can be selling items that are in
    // different regions, ugly, but to keep it consistent requires maintaining
    // too much data and also I don't think this will affect results
    private void generateOpenAuction(MyBuffer myb, int numItems) 
        throws IOException {
        cntItems += numItems;

        myb.append("<open_auctions>");
        myb.append(nl);
	
        // open auction contains:
        // initial, reserve?, bidder*, current, privacy?, itemref, seller, annotation, 
        // quantity, type, interval    
	
        for (int i=0; i<numItems; i++) {
            // at this point we are not generating items, we are generating
            // only open auctions, id for open_auction is same as id of item
            // up for auction
	    
            myb.append(tab);
            myb.append("<open_auction id=\"");
	    // gets id and creates new open_auction instance
            int auctionId = openAuctions.getNewId();
            myb.append(auctionId);
            myb.append("\">");
            myb.append(nl);

            // no initial - does not fit our scenario

            if(!LIMIT_ATTRIBUTES) {
                // reserve 
                if(rnd.nextBoolean()) {
                    myb.append(tab2);
                    myb.append("<reserve>");
                    myb.append((int)Math.round((openAuctions.getCurrPrice(auctionId))*(1.2+(rnd.nextDouble()+1))));
                    myb.append("</reserve>");
                    myb.append(nl);
                }
                // no bidders
		
                // no current - do with accumlator
		
                // privacy 
                if(rnd.nextBoolean()) {
                    myb.append(tab2);
                    myb.append("<privacy>");
                    myb.append(yesno[rnd.nextInt(2)]);
                    myb.append("</privacy>");
                    myb.append(nl);
                }
            }
	    
            // itemref
            myb.append(tab2);
            myb.append("<itemref item=\"");
            // assume itemId and openAuctionId are same - only one auction per item allowed
            myb.append(auctionId);
            myb.append("\"></itemref>");
            myb.append(nl);
	    
            // seller
            myb.append(tab2);
            myb.append("<seller person=\"");
            myb.append(persons.getExistingId());
            myb.append("\"></seller>");
            myb.append(nl);
	    
            // skip annotation - too hard to generate - need to just get this done KT

            // KT - add category id XMark items can be in 1-10 categories
            // we allow an item to be in one category
            myb.append(tab2);
            myb.append("<category>");
            int catid = rnd.nextInt(303);
            myb.append(catid);
            myb.append("</category>");
            myb.append(nl);

            if(!LIMIT_ATTRIBUTES) {
                // quantity
                myb.append(tab2);
                myb.append("<quantity>");
                int quantity = 1+rnd.nextInt(10);
                myb.append(quantity);
                myb.append("</quantity>");
                myb.append(nl);
		
                // type
                myb.append(tab2);
                myb.append("<type>");
                myb.append(auction_type[rnd.nextInt(2)]);
                if(quantity>1 && rnd.nextBoolean())
                    myb.append(", Dutch"); // 
                myb.append("</type>");
                myb.append(nl);
		
                // interval
                myb.append(tab2);
                myb.append("<interval>");
                myb.append("<start>");
                myb.append(openAuctions.getStartTime(auctionId));
                myb.append("</start>");
                myb.append("<end>");
                myb.append(openAuctions.getEndTime(auctionId));
                //myb.append(System.currentTimeMillis() * WARP + DELAY + rnd.nextInt(MAXINCREMENT_MILLISEC));
                myb.append("</end>");
                myb.append("</interval>");
                myb.append(nl);
            }
            myb.append(tab);
            myb.append("</open_auction>");
            myb.append(nl);
        }
        myb.append("</open_auctions>");
        myb.append(nl);
    }
    
    // append region AFRICA, ASIA, AUSTRALIA, EUROPE, NAMERICA, SAMERICA
    //Item contains:
    // location, quantity, name, payment, description, shipping, 
    //      incategory+, mailbox)>
    // weird, item doesn't contain a reference to the seller, 
    // open_auction contains a reference to the item and a 
    // reference to the seller
    
  
    private void generatePerson(MyBuffer myb, int numPersons) 
        throws IOException {
        
        cntPersons += numPersons;

        myb.append("<people>");      
        myb.append(nl);

        for (int i=0; i<numPersons; i++) {
            p.generateValues(openAuctions); // person object is reusable now
	    
            myb.append(tab);
            myb.append("<person id=\"");
            myb.append(persons.getNewId()); 
            myb.append("\">");
            myb.append(nl);
	    
            myb.append(tab2);
            myb.append("<name>");
            myb.append(p.m_stName);
            myb.append("</name>");
            myb.append(nl);

            myb.append(tab2);
            myb.append("<emailaddress>");
            myb.append(p.m_stEmail);
            myb.append("</emailaddress>");
            myb.append(nl);

            if(!LIMIT_ATTRIBUTES) {
                if (p.has_phone) {
                    myb.append(tab2);
                    myb.append("<phone>");
                    myb.append(p.m_stPhone);
                    myb.append("</phone>");
                    myb.append(nl);
                }
                if (p.has_address) {
                    myb.append(tab2);
                    myb.append("<address>");
                    myb.append(nl);
		    
                    myb.append(tab3);
                    myb.append("<street>");
                    myb.append(p.m_address.m_stStreet);
                    myb.append("</street>");
                    myb.append(nl);
		    
                    myb.append(tab3);
                    myb.append("<city>");
                    myb.append(p.m_address.m_stCity);
                    myb.append("</city>");
                    myb.append(nl);
		    
                    myb.append(tab3);
                    myb.append("<country>");
                    myb.append(p.m_address.m_stCountry);
                    myb.append("</country>");
                    myb.append(nl);
		    
                    myb.append(tab3);
                    myb.append("<province>");
                    myb.append(p.m_address.m_stProvince);
                    myb.append("</province>");
                    myb.append(nl);
		    
                    myb.append(tab3);
                    myb.append("<zipcode>");
                    myb.append(p.m_address.m_stZipcode);
                    myb.append("</zipcode>");
                    myb.append(nl);
		    
                    myb.append(tab2);
                    myb.append("</address>");
                    myb.append(nl);
                }
                if (p.has_homepage) {
                    myb.append(tab2);
                    myb.append("<homepage>");
                    myb.append(p.m_stHomepage);
                    myb.append("</homepage>");
                    myb.append(nl);
                }
                if (p.has_creditcard) {
                    myb.append(tab2);
                    myb.append("<creditcard>");
                    myb.append(p.m_stCreditcard);
                    myb.append("</creditcard>");
                    myb.append(nl);
                }
		
                if (p.has_profile) {
                    myb.append(tab2);
                    myb.append("<profile income=\"");
                    myb.append(p.m_profile.m_stIncome);
                    myb.append("\">");
                    myb.append(nl);
		    
                    for (int j=0; j < p.m_profile.m_vctInterest.size(); j++) {
                        myb.append(tab3);
                        myb.append("<interest category=\"");
                        myb.append((String)p.m_profile.m_vctInterest.get(j));
                        myb.append("\"/>");
                        myb.append(nl);
                    }
                    if (p.m_profile.has_education) {
                        myb.append(tab3);
                        myb.append("<education>");
                        myb.append(p.m_profile.m_stEducation);
                        myb.append("</education>");
                        myb.append(nl);
                    }
                    if (p.m_profile.has_gender) {
                        myb.append(tab3);
                        myb.append("<gender>");
                        myb.append(p.m_profile.m_stGender);
                        myb.append("</gender>");
                        myb.append(nl);
                    }
		    
                    myb.append(tab3);
                    myb.append("<business>");
                    myb.append(p.m_profile.m_stBusiness);
                    myb.append("</business>");
                    myb.append(nl);
		    
                    if (p.m_profile.has_age) {
                        myb.append(tab3);
                        myb.append("<age>");
                        myb.append(p.m_profile.m_stAge);
                        myb.append("</age>");
                        myb.append(nl);
                    }
                    myb.append(tab2);
                    myb.append("</profile>");
                    myb.append(nl);
                }
                if (p.has_watches) {
                    myb.append(tab2);
                    myb.append("<watches>");
                    myb.append(nl);
                    for (int j=0; j<p.m_vctWatches.size(); j++) {
                        myb.append(tab3);
                        myb.append("<watch>");
                        myb.append((String)p.m_vctWatches.get(j));
                        myb.append("</watch>");
                        myb.append(nl);
                    }
                    myb.append(tab2);
                    myb.append("</watches>");
                    myb.append(nl);
                }
            }
	    
            myb.append(tab);
            myb.append("</person>");
            myb.append(nl);
        }
        myb.append("</people>");
        myb.append(nl);
    }

    public void printStats() {
        System.err.println("Gen Calls " + cntGenCalls);
        System.err.println("Persons: " + cntPersons);
        System.err.println("Items: " + cntItems);
        System.err.println("Bids: " + cntBids);    
        System.err.println("Puncts: " + cntPunct);
	System.err.println("Bytes: " + charsWritten);
    }
}
