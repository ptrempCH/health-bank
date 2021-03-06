package ch.ethz.inf.systems.ptremp.healthbank.test;

import java.net.UnknownHostException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import ch.ethz.inf.systems.ptremp.healthbank.db.MongoDBConnector;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.IllegalQueryException;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.NotConnectedException;

import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DBObject;

/**
 * This class was build with the intention to test the connection to the database server. 
 * @author Patrick Tremp
 *
 */
public class ConsoleOutputTest {

	/**
	 * In the main method we try to get a connection to the MongoDB, then insert a new entry into the collection
	 * "things". Next we query the "things" collection to retrieve the stored element(s) and display them in the
	 * console. Finally we update one entry in the DB and display the collection again by querying it again. 
	 * @param args This method does not need any external arguments
	 */
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		try {
			// TEST for Text Query
			MongoDBConnector connector = MongoDBConnector.getInstance();
			connector.connect();
			String query = "terrible headache";
			
			final DBObject textSearchCommand = new BasicDBObject();
	        textSearchCommand.put("text", MongoDBConnector.RECORDS_COLLECTION_NAME);
	        textSearchCommand.put("search", query);
	        final CommandResult commandResult = connector.command(textSearchCommand);
	        
	        JSONParser parser = new JSONParser();
	        JSONObject resObj = (JSONObject) parser.parse(commandResult.toString());
			JSONArray finalResult = new JSONArray();
			if(resObj!=null){
				JSONArray resArray = new JSONArray();
				resArray = (JSONArray) resObj.get("results");
				JSONObject result;
				for(int i=0;i<resArray.size();i++){
					result = (JSONObject) resArray.get(i);
					finalResult.add(result.get("obj"));
					System.out.println(i+"-> Score: "+result.get("score")+"; obj: "+result.get("obj").toString());
				}
			}
			System.out.println();
			System.out.println();
			System.out.println(finalResult.toString());
			
			// END TEST for Text Query
			
			
			// Test for DB access
			
			/*System.out.println("Hi, I'm creating a connector now.");
			System.out.println();
			MongoDBConnector connector = MongoDBConnector.getInstance();
			System.out.println("Now I try to connect to localhost on port 27017");
			System.out.println();
			connector.connect();
			System.out.println("I'll insert the following entry in database 'healthbank': { firstname : \"Patrick\", lastname : \"Tremp\", age : 25 }");
			System.out.println();
			HashMap<Object, Object> entry = new HashMap<Object, Object>();
			entry.put("firstname", "Patrick");
			entry.put("lastname", "Tremp");
			entry.put("age", 22);
			connector.insert("things", entry);
			System.out.println("Now lets query the db and output all entires with age==25.");
			BasicDBList list = new BasicDBList();
			list.add(25);
			DBCursor res = (DBCursor) connector.query("things", new BasicDBObject("age", new BasicDBObject("$in", list)));
			if(res != null){
				while(res.hasNext()){
					System.out.println(res.next());
				}
			}
			System.out.println();
			System.out.println("Now we update an entry with firstname==Patrick to firstname==Hans and add nationality==Switzerland");
			list = new BasicDBList();
			list.add("Patrick");
			connector.update("things", new BasicDBObject("firstname", new BasicDBObject("$in", list)), 
					new BasicDBObject("$set", new BasicDBObject("firstname", "Hans2").append("nationality", "Switzerland")));
			System.out.println();
			System.out.println("Let's query again for all values:");
			System.out.println();
			res = (DBCursor) connector.query("things", null);
			if(res != null){
				while(res.hasNext()){
					System.out.println(res.next());
				}
			}
			System.out.println();
			System.out.println("Finally I close the connection again...");
			connector.disconnect();
			System.out.println();
			System.out.println("Thanks for using this. Bye");
			CoreManager manager = new CoreManager();
			System.out.println(manager.randomString(32));*/
			
			// End for test db access
		} catch (NotConnectedException e) {
			System.out.println(e.getMessage());
		} catch (UnknownHostException e) {
			System.out.println(e.getMessage());
		} catch (IllegalArgumentException e){
			System.out.println(e.getMessage());
		} catch (IllegalQueryException e) {
			System.out.println(e.getMessage());
		} catch (ParseException e) {
			System.out.println(e.getMessage());
		}
	}

}
