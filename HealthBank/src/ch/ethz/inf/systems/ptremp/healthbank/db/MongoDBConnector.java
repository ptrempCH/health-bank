package ch.ethz.inf.systems.ptremp.healthbank.db;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashMap;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.IllegalQueryException;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.NotConnectedException;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.MongoClient;
import com.mongodb.MongoException;
import com.mongodb.WriteResult;

import javax.xml.parsers.*;
import org.xml.sax.*;
import org.bson.types.ObjectId;
import org.w3c.dom.*;

public class MongoDBConnector implements DBConnector {
	
/**
 * Singleton for this class
 */
	private static MongoDBConnector instance = null;
	
	/**
	 * DO NOT ACCESS THIS CONSTRUCTOR DIRECTLY!
	 * Please use {@link #getInstance()} in order to get an instance of this class. Never use this constructor directly!
	 */
	protected MongoDBConnector(){
		this.connected = false;
		File f = new File("C:\\Temp\\HB_Configuration.xml");
		if(!f.exists()){
			f = new File("/opt/HB_Configuration.xml");
			if(!f.exists()){
				throw new RuntimeException("Could not read configuration file!!");
			}
		}
        if(!readXML(f.getAbsolutePath())){
        	throw new RuntimeException("Could not parse configuration file!!");
        }
	}

	/**
	 * Get an instance of this class. This class uses the singleton pattern. You should always use this method statically
	 * in order to get an instance of this class. 
	 * @return The instance of the MongoDBConnector class
	 */
	public static MongoDBConnector getInstance() {
		if(instance == null){
			System.out.println("Connector is creating a new instance");
			instance = new MongoDBConnector();
		}
		return instance;
	}
/** END Singleton implementation **/
	
	
/** 
 * Private fields for DB connection 
**/
	
	private MongoClient mongoClient;
	private DB rootDatabase;
	private boolean connected;
	
	
/**
 * Public fields
 */
	
	/** 
	 * Names and address to use to store data in the DB 
	**/
	
	/** The host address of the DB server. **/
	public static String HOST;
	/** The port of the DB server. **/
	public static int PORT;
	/** The name of the DB to use. **/
	public static String DB_NAME;
	/** The name of the users collection in the DB to use. **/
	public static String USER_COLLECTION_NAME;
	/** The name of the applications collection in the DB to use. **/
	public static String APPLICATION_COLLECTION_NAME;
	/** The name of the records collection in the DB to use. **/
	public static String RECORDS_COLLECTION_NAME;
	/** The name of the spaces collection in the DB to use. **/
	public static String SPACES_COLLECTION_NAME;
	/** The name of the circles collection in the DB to use.  **/
	public static String CIRCLES_COLLECTION_NAME;
	/** The name of the messages collection in the DB to use. **/
	public static String MESSAGES_COLLECTION_NAME;
	/** The name of the news collection in the DB to use. **/
	public static String NEWS_COLLECTION_NAME;
	
	
	/**
	 * Decide if debugging messages shall be printed to the console
	 */
	public static boolean DEBUG;
	
	/**
	 * Folder paths
	 */
	public static String IPATH; // Images
	public static String APPHTML; // HTML files for applications
	

/**
 * Public methods
 */
	
	
	/**
	 * Check if we are connected to the DB server.
	 * @return True if we are connected, false otherwise
	 */
	public boolean isConnected() {
		return connected;
	}
	

	/**
	 * Get the root database we are working with. Usually you should not need this directly. 
	 * All methods are declared in a way, that they handle the root database.
	 * @return The {@link DB} we work with.
	 */
	public DB getRootDatabase() {
		return rootDatabase;
	}

	/**
	 * Connect to the database. Use constructor first
	 * @throws UnknownHostException 
	 */
	@Override
	public void connect() throws UnknownHostException, MongoException {
		try {
			mongoClient = new MongoClient( HOST, PORT );
			rootDatabase = mongoClient.getDB( DB_NAME );
			this.connected = true;
			if(DEBUG){System.out.println("Connector called connect() successfully. this.connected is now: "+this.connected);}
		} catch (UnknownHostException e) {
			throw new UnknownHostException("Could not connect. Please provide the correct URL and port and make sure the DB server is running");
		} catch (MongoException e) {
			throw new MongoException("Could not connect to the DB. Is the DB running?");
		}
	}
	
	/**
	 * Disconnect from the database
	 */
	@Override
	public void disconnect() {
		mongoClient.close();
		mongoClient = null;
		rootDatabase = null;
		this.connected = false;
	}

	/**
	 * Create a new Database. Since mongoDB does this on its own, you should never call this method!
	 * @param name The name of the {@link DB} to create
	 * @throws NotImplementedException This method is not used for mongoDB, hence it will throw an exception if called.
	 */
	@Override
	public boolean createDB(String name) {
		// This is done automatically by mongoDB, hence ignore this.
		throw new NotImplementedException();
	}

	/**
	 * Delete a Database. This will remove all data on disk of this DB. Use with caution. 
	 * @param name The name of the {@link DB} to delete
	 * @return True if the database was deleted successfully. False otherwise
	 * @throws NotConnectedException 
	 * @throws NotImplementedException This method is not used for mongoDB, hence it will throw an exception if called.
	 */
	@Override
	public boolean deleteDB(String name) throws NotConnectedException {
		if(!connected || rootDatabase==null){ 
			try{
				this.connect();
			} catch (Exception e){
				throw new NotConnectedException("We are not connected to a database, try to connect first."); 
			}
		}
		try {
			if(rootDatabase.getName().equals(name))
				rootDatabase.dropDatabase();
			else {
				if(!mongoClient.getDatabaseNames().contains(name))
					return false;
				mongoClient.getDB(name).dropDatabase();
			}
			return true;
		} catch (MongoException e) {
			return false;
		}
	}

	/**
	 * Insert data into a mongoDB collection
	 * @param collection The name of the collection to store the data in
	 * @param data The data to store in the database.
	 * @return Object An instance of {@link ObjectId} with the id of the last inserted object
	 * @throws NotConnectedException 
	 * @throws IllegalArgumentException
	 */
	@Override
	public Object insert(String collection, HashMap<Object, Object> data) throws NotConnectedException {
		if(data == null || data.size() < 1){ throw new IllegalArgumentException("Please provide some data to store to the insert method."); }
		if(!connected || rootDatabase==null){ 
			try{
				this.connect();
			} catch (Exception e){
				throw new NotConnectedException("We are not connected to a database, try to connect first."); 
			}
		}
		rootDatabase.requestStart();
		try{
			DBCollection coll = rootDatabase.getCollection(collection);
			BasicDBObject doc = new BasicDBObject(data);
			coll.insert(doc);
			return (ObjectId)doc.get("_id");
		} finally{
			rootDatabase.requestDone();
		}
	}
	
	
	/**
	 * Update a field in a collection with new data. This could include updating existing attributes of a field or adding new attributes to a field.
	 * @param collection The name of the collection to update the data in
	 * @param queryObject A {@link BasicDBObject} to query from. Have a look at <a href="http://docs.mongodb.org/ecosystem/tutorial/getting-started-with-java-driver/
	 * 			#getting-a-set-of-documents-with-a-query">this link</a> for more information
	 * @param update The {@link BasicDBObject} to store in the respective field.
	 * @throws IllegalArgumentException
	 * @throws IllegalQueryException
	 * @throws NotConnectedException 
	 */
	@Override
	public void update(String collection, Object queryObject, Object update) throws IllegalQueryException, IllegalArgumentException, NotConnectedException {
		if(!connected || rootDatabase==null){ 
			try{
				this.connect();
			} catch (Exception e){
				throw new NotConnectedException("We are not connected to a database, try to connect first."); 
			}
		}
		rootDatabase.requestStart();
		try {
			if(queryObject==null){
				throw new IllegalArgumentException("Please provide a valid BasicDBObject as queryObject");
			} else if(update == null) {
				throw new IllegalArgumentException("Please provide a valid BasicDBObject as update attribute.");
			}else {
				DBCollection coll = rootDatabase.getCollection(collection);
				BasicDBObject query;
				BasicDBObject updateDoc;
				if(queryObject instanceof BasicDBObject){
					query = (BasicDBObject) queryObject;
				} else {
					throw new IllegalArgumentException("Please provide a valid BasicDBObject as queryObject");
				}
				if(update instanceof BasicDBObject){
					updateDoc = (BasicDBObject) update;
				} else {
					throw new IllegalArgumentException("Please provide a valid BasicDBObject as update attribute");
				}
				coll.update(query, updateDoc);
			}
		} catch (MongoException e) {
			throw new IllegalQueryException("Your provided query did throw an exception: "+e.getMessage());
		} finally {
			rootDatabase.requestDone();
		}
	}

	/**
	 * Query the database
	 * @param 
	 * 		collection The name of the collection to search for
	 * @param 
	 * 		queryObject A {@link BasicDBObject} to query from. Have a look at <a href="http://docs.mongodb.org/ecosystem/tutorial/getting-started-with-java-driver/
	 * 			#getting-a-set-of-documents-with-a-query">this link</a> for more information
	 * @return The {@link DBCursor} for the result as an Object
	 * @throws IllegalQueryException
	 * @throws NotConnectedException 
	 */
	@Override
	public Object query(String collection, Object queryObject) throws IllegalQueryException, NotConnectedException {
		if(!connected || rootDatabase==null){ 
			try{
				this.connect();
			} catch (Exception e){
				throw new NotConnectedException("We are not connected to a database, try to connect first."); 
			}
		}
		rootDatabase.requestStart();
		try{
			DBCollection coll = rootDatabase.getCollection(collection);
			DBCursor cursor;
			if(queryObject==null){
				cursor = coll.find().sort(new BasicDBObject("timedate", 1)); 
			} else {
				BasicDBObject query;
				if(queryObject instanceof BasicDBObject){
					query = (BasicDBObject) queryObject;
				} else {
					throw new IllegalArgumentException("Please provide a valid BasicDBObject as queryObject");
				}
				cursor = coll.find(query).sort(new BasicDBObject("timedate", 1));
			}
			if(!cursor.hasNext()){ return null;} // maybe we should skip this line for performance reasons
			return cursor;
		} catch (MongoException e) {
			throw new IllegalQueryException("Your provided query did throw an exception: "+e.getMessage());
		}
		finally {
			rootDatabase.requestDone();
		}
	}
	
	/**
	 * Delete an entry in a collection.
	 * @param collection The name of the collection to delete the entry in
	 * @param query The {@link BasicDBObject} that represents a query to look for the entry to delete.
	 * @return True if deletion was successful, false otherwise.
	 * @throws IllegalQueryException
	 * @throws NotConnectedException 
	 */
	@Override
	public boolean delete(String collection, Object queryObject) throws IllegalQueryException, NotConnectedException {
		if(!connected || rootDatabase==null){ 
			try{
				this.connect();
			} catch (Exception e){
				throw new NotConnectedException("We are not connected to a database, try to connect first."); 
			}
		}
		rootDatabase.requestStart();
		try{
			DBCollection coll = rootDatabase.getCollection(collection);
			BasicDBObject query;
			if(queryObject instanceof BasicDBObject){
				query = (BasicDBObject) queryObject;
			} else {
				throw new IllegalArgumentException("Please provide a valid BasicDBObject as queryObject");
			}
			WriteResult wr = coll.remove(query);
			if(wr.getN()==0){ return false;} // we did not delete something
			return true;
		} catch (MongoException e) {
			throw new IllegalQueryException("Your provided query did throw an exception: "+e.getMessage());
		}
		finally {
			rootDatabase.requestDone();
		}
	}

	/**
	 * Create a new table in the current database. Since mongoDB does this on its own on insert, you should never call this method!
	 * @param name The name of the table to create
	 * @throws NotImplementedException This method is not used for mongoDB, hence it will throw an exception if called.
	 */
	@Override
	public boolean createTable(String table) {
		throw new NotImplementedException();
	}

	/**
	 * Delete a collection in the current {@link DB}.
	 * @param collection The name of the {@link DBCollection} to delete
	 * @return True if the deletion was successful, false otherwise
	 * @throws NotConnectedException
	 * @throws IllegalQueryException 
	 */
	@Override
	public boolean deleteTable(String collection) throws NotConnectedException, IllegalQueryException {
		if(!connected || rootDatabase==null){ 
			try{
				this.connect();
			} catch (Exception e){
				throw new NotConnectedException("We are not connected to a database, try to connect first."); 
			}
		}
		rootDatabase.requestStart();
		try{
			DBCollection coll = rootDatabase.getCollection(collection);
			coll.drop();
			return true;
		} catch (MongoException e) {
			throw new IllegalQueryException("Your provided query did throw an exception: "+e.getMessage());
		}
		finally {
			rootDatabase.requestDone();
		}
	}
	
	/**
	 * Private methods
	 */

	/**
	 * Read the configuration XML file and store the values needed to connect to and set up the database to the public fields.
	 * This method was copy pasted from:
	 * http://stackoverflow.com/questions/7373567/java-how-to-read-and-write-xml-files/7373596#7373596
	 * and finally adapted to our needs. Thanks go to the author.
	 * @param xml A String containing XML data
	 * @return True if parsing went well, false otherwise.
	 */
	private boolean readXML(String xml) {
        Document dom;
        // Make an  instance of the DocumentBuilderFactory
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            // use the factory to take an instance of the document builder
            DocumentBuilder db = dbf.newDocumentBuilder();
            // parse using the builder to get the DOM mapping of the    
            // XML file
            dom = db.parse(xml);

            Element doc = dom.getDocumentElement();

            HOST = getTextValue(HOST, doc, "HOST");
            if (HOST == null || HOST.isEmpty()) {
                    HOST = "localhost";
            }
            String port = "";
            port = getTextValue(port, doc, "PORT");
            if (port != null) {
                if (!port.isEmpty())
                    PORT = Integer.parseInt(port);
                else
                	PORT = 27017;
            }
            DB_NAME = getTextValue(DB_NAME, doc, "DB_NAME");
            if (DB_NAME == null || DB_NAME.isEmpty()) {
            	DB_NAME = "healthbank";
            }
            USER_COLLECTION_NAME = getTextValue(USER_COLLECTION_NAME, doc, "USER_COLLECTION_NAME");
            if (USER_COLLECTION_NAME == null || USER_COLLECTION_NAME.isEmpty()) {
            	USER_COLLECTION_NAME = "users";
            }
            APPLICATION_COLLECTION_NAME = getTextValue(APPLICATION_COLLECTION_NAME, doc, "APPLICATION_COLLECTION_NAME");
            if (APPLICATION_COLLECTION_NAME == null || APPLICATION_COLLECTION_NAME.isEmpty()) {
            	APPLICATION_COLLECTION_NAME = "applications";
            }
            RECORDS_COLLECTION_NAME = getTextValue(RECORDS_COLLECTION_NAME, doc, "RECORDS_COLLECTION_NAME");
            if (RECORDS_COLLECTION_NAME == null || RECORDS_COLLECTION_NAME.isEmpty()) {
            	RECORDS_COLLECTION_NAME = "records";
            }
            SPACES_COLLECTION_NAME = getTextValue(SPACES_COLLECTION_NAME, doc, "FOLDERS_COLLECTION_NAME");
            if (SPACES_COLLECTION_NAME == null || SPACES_COLLECTION_NAME.isEmpty()) {
            	SPACES_COLLECTION_NAME = "spaces";
            }
            CIRCLES_COLLECTION_NAME = getTextValue(CIRCLES_COLLECTION_NAME, doc, "CIRCLES_COLLECTION_NAME");
            if (CIRCLES_COLLECTION_NAME == null || CIRCLES_COLLECTION_NAME.isEmpty()) {
            	CIRCLES_COLLECTION_NAME = "circles";
            }
            MESSAGES_COLLECTION_NAME = getTextValue(MESSAGES_COLLECTION_NAME, doc, "MESSAGES_COLLECTION_NAME");
            if (MESSAGES_COLLECTION_NAME == null || MESSAGES_COLLECTION_NAME.isEmpty()) {
            	MESSAGES_COLLECTION_NAME = "messages";
            }
            NEWS_COLLECTION_NAME = getTextValue(NEWS_COLLECTION_NAME, doc, "NEWS_COLLECTION_NAME");
            if (NEWS_COLLECTION_NAME == null || NEWS_COLLECTION_NAME.isEmpty()) {
            	NEWS_COLLECTION_NAME = "news";
            }
            IPATH = getTextValue(IPATH, doc, "IPATH");
            if (IPATH == null || IPATH.isEmpty()) {
            	IPATH = "C:\\Temp\\images\\";
            }
            APPHTML = getTextValue(APPHTML, doc, "APPHTML");
            if (APPHTML == null || APPHTML.isEmpty()) {
            	APPHTML = "C:\\xampp\\htdocs\\GUI\\www\\apps\\";
            }
            
            String debug = "";
            debug = getTextValue(debug, doc, "DEBUGGING");
            if (debug == null || debug.isEmpty()) {
            	DEBUG = false;
            } else {
            	DEBUG = true;
            }
            return true;

        } catch (ParserConfigurationException pce) {
            System.out.println(pce.getMessage());
        } catch (SAXException se) {
            System.out.println(se.getMessage());
        } catch (IOException ioe) {
            System.err.println(ioe.getMessage());
        }

        return false;
    }
	
	private String getTextValue(String def, Element doc, String tag) {
	    String value = def;
	    NodeList nl;
	    nl = doc.getElementsByTagName(tag);
	    if (nl.getLength() > 0 && nl.item(0).hasChildNodes()) {
	        value = nl.item(0).getFirstChild().getNodeValue();
	    }
	    return value;
	}

}
