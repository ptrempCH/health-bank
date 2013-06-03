package ch.ethz.inf.systems.ptremp.healthbank.exceptions;

import com.mongodb.BasicDBObject;

/**
 * Called when a query usually defined as a {@link BasicDBObject} was malformed and did result in a mongoDB exception.
 * 
 * @author Patrick Tremp
 */
public class IllegalQueryException extends Exception {

	private static final long serialVersionUID = -2356485848766009430L;
	
	public IllegalQueryException(String message){
		super(message);
	}
	
	public IllegalQueryException() {
		super("Your provided query produced an error. Please rewrite it.");
	}

}
