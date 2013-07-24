package ch.ethz.inf.systems.ptremp.healthbank.REST.records;

import java.io.IOException;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

import ch.ethz.inf.systems.ptremp.healthbank.db.MongoDBConnector;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.IllegalQueryException;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.NotConnectedException;
import ch.ethz.inf.systems.ptremp.healthbank.logic.CoreManager;

/**
* Servlet implementation class Record
* In this servlet we implement the functionality to create, edit and delete record entries.
* A record is an entry of a user containing health information. Records can be shared with
* other users via the association to circles.
* 
* @author Patrick Tremp
*/
@WebServlet(
		description = "Create, Edit, Read and Delete a record in the system", 
		urlPatterns = { 
				"/Record", 
				"/record", 
				"/rec"
		})
public class Record extends HttpServlet {
       
	private static final long serialVersionUID = 3311407018317758139L;

	/**
	 *  Instance of the {@link MongoDBConnector}, which is responsible for the connection to the DB
	 */
	private MongoDBConnector connector; 
	
	/**
	 * Instance of the {@link CoreManager}, which is responsible for some core functionalities used
	 * in several different servlet.
	 */
	private CoreManager manager;

	/**
	 * Main constructor
     * @see HttpServlet#HttpServlet()
     */
    public Record() {
        super();
        if(connector == null ) { 
        	connector = MongoDBConnector.getInstance();
        }
		if(manager == null) {
			manager = new CoreManager();
		}
    }

	/**
	 * This method will initialize the {@link MongoDBConnector} and {@link CoreManager} it they have not yet
	 * been initialized via constructor. 
	 * 
	 * @see Servlet#init(ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
		if(connector == null ) { 
        	connector = MongoDBConnector.getInstance();
        }
		if(manager == null) {
			manager = new CoreManager();
		}
	}

	/**
	 * The GET request allows the caller to get information about existing records. There are three ways of calling this 
	 * method. Either with or without an id and by another users id. With an id will result in getting the information about a 
	 * single record. Without will return all the records of the given user. By providing an other users id, one can get the
	 * records of the other user, if the other has the current in a circle.
	 * 
	 * For a successful call the following parameters need to be present in the URL:
	 * Return all records of current user:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - spaceId: (optional) The id of a space. If this is set, only the records of the current user which are in this particular space will be returned
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 * 
	 * TODO: 
	 * 	- It would be useful to just return 20-50 items at a time because of network issues.
	 */
	@SuppressWarnings("unchecked")
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		String values = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		int nrRes=0;
		
		// check parameters user specific parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		String spaceId = request.getParameter("spaceId");
		//String id = request.getParameter("id");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Please provide the parameters 'pw' and 'username' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			if(spaceId!=null && spaceId.length()>0){spaceId = URLDecoder.decode(spaceId, "UTF-8");}
			
			// check DB connection
			if(!connector.isConnected()){
				manager.reconnect();
			}
			
			// check if user is logged in and get the records 
			try {
				if(!manager.isUserLoggedIn(session, credentials)){
					errorMessage = "\"Record doGet: You need to be logged in to use this service\"";
					wasError = true;
					isLoggedIn = false;
				} else {
					DBCursor res;
					BasicDBList list = new BasicDBList();
					String userId = manager.getUserID(credentials);
					JSONParser parser = new JSONParser();
					JSONArray resArray = new JSONArray();
					if(spaceId!=null && spaceId.length()>0){
						res = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("spaceID", spaceId));
						if(res==null || !res.hasNext()){
							errorMessage = "\"Record doGet: We could not find any record assigned to the space with id: "+spaceId+"\"";
							wasError = true;
						} else {
							while (res.hasNext()) {
								DBObject obj = (DBObject) res.next();
								String recId = (String) obj.get("recordID");
								list = new BasicDBList();
								list.add(new ObjectId(recId));
								DBCursor rec = (DBCursor) connector.query(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
								if(rec!=null && rec.hasNext()){
									DBObject recEntry = (DBObject) rec.next();
									if(((String) recEntry.get("userID")).equals(userId)){
										DBCursor spa = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("recordID", recId));
										JSONArray spas = new JSONArray();
										if(spa!=null && spa.hasNext()){
											while(spa.hasNext()){
												DBObject spaObj = (DBObject) spa.next();
												String s = (String) spaObj.get("spaceID");
												if(s!=null && s.length()>0){ spas.add(s); }
											}
										}
										JSONObject resObj = (JSONObject) parser.parse(obj.toString());
										resObj.put("spaces", spas);
										resArray.add(resObj);
									}
								} 
							}
							
						}
					} else {
						list.add(userId);
						res = (DBCursor) connector.query(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("userID", new BasicDBObject("$in", list)));
						if(res==null || !res.hasNext()){
							errorMessage = "\"Record doGet: There was an error. Did you provide the correct sessionKey and credentials?\"";
							wasError = true;
						} else {
							while(res.hasNext()){
								DBObject obj = res.next();
								ObjectId recId = (ObjectId) obj.get("_id");
								DBCursor spa = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("recordID", recId.toString()));
								JSONArray spas = new JSONArray();
								if(spa!=null && spa.hasNext()){
									while(spa.hasNext()){
										DBObject spaObj = (DBObject) spa.next();
										String s = (String) spaObj.get("spaceID");
										if(s!=null && s.length()>0){ spas.add(s); }
									}
								}
								JSONObject resObj = (JSONObject) parser.parse(obj.toString());
								resObj.put("spaces", spas);
								resArray.add(resObj);
							}
						}
					}
					JSONObject result = new JSONObject();
					result.put("records", resArray);
					values = result.toJSONString();
				}
			} catch (IllegalArgumentException e) {
				errorMessage = "\"Record doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (IllegalQueryException e) {
				errorMessage = "\"Record doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"Record doGet: There was a connection error to the database. Please try again later.\"";
				wasError = true;
			} catch (ParseException e) {
				errorMessage = "\"Record doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			}
		}
		
		// Finally give the user feedback
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError){
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"size\" : \""+nrRes+"\", \"values\" : "+values+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Returned "+nrRes+" records to the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"size\" : \""+nrRes+"\", \"values\" : "+values+" }");
				if(MongoDBConnector.DEBUG){System.out.println("Returned "+nrRes+" records to the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} 
	}

	/**
	 * The POST request allows the caller to create new records, edit them and remove them again.
	 * There are at the moment two different ways of calling this method. One for insertion and one for editing. 
	 * Deletion will follow soon.
	 * 
	 * For a successful call the following parameters need to be present in the URL:
	 * Insert: 
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - name: The name of the record
	 * - descr: A description for the record
	 * - values: A JSON string that contains additional values for the record
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Editing:
	 *  - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - id: The id of the record to edit
	 * - circle: (either circle or space or both possible) A string containing the Id's of the associated circles separated by a space
	 * - space: (either circle or space or both possible) A string containing the Id's of the associated spaces separated by a space
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * 
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 * 
	 * TODO:
	 * 	- Implement deletion of entries
	 * 	- Implement editing of entries besides circles and spaces
	 * 	- File upload
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		String name = "";
		String id = "";
		
		// check parameters user specific parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Record doPost: Please provide the parameters 'pw' and 'username' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			
			// check record specific parameters
			try {
				// check if user is logged in and save the new value
				if(!manager.isUserLoggedIn(session, credentials)){
					errorMessage = "\"Record doPost: You need to be logged in to use this service\"";
					wasError = true;
					isLoggedIn = false;
				} else {
					id = request.getParameter("id");
					if(id!=null && id.length()>0){ // update circles
						id = URLDecoder.decode(id, "UTF-8");
						String circle = request.getParameter("circle");
						String space = request.getParameter("space");
						BasicDBList objectList = new BasicDBList();
						if(circle!=null){
							circle = URLDecoder.decode(circle, "UTF-8");
							for(String s: circle.split(" ")){
								objectList.add(new BasicDBObject("circle", s));
							}
							BasicDBList list = new BasicDBList();
							list.add(new ObjectId(id));
							connector.update(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$set", new BasicDBObject("circles", objectList)));
						} else  if(space!=null){
							space = URLDecoder.decode(space, "UTF-8");
							HashMap<Object, Object> data = new HashMap<Object, Object>();
							BasicDBList and = new BasicDBList();
							and.add(id);
							DBCursor res = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("recordID", new BasicDBObject("$in", and)));
							if(res==null || !res.hasNext()){ // we need to insert all of the new space entries
								for(String s: space.split(" ")){
									data = new HashMap<Object, Object>();
									data.put("recordID", id);
									data.put("spaceID", s);
									DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
									data.put("timedate", dateFormat.format(new Date()));
									connector.insert(MongoDBConnector.SPACES_COLLECTION_NAME, data);
								}
							} else { // add or remove the ones that do not fit anymore
								boolean found = false;
								while(res.hasNext()){
									found = false;
									DBObject obj = res.next();
									String spaId = (String) obj.get("spaceID");
									for(String s: space.split(" ")){
										if(s.equals("")){ continue; }
										if(spaId.equals(s)){
											space = space.replace(s, "");
											found=true; 
											break; }
									}
									if(!found){ // the user removed this space
										and = new BasicDBList();
										and.add(new BasicDBObject("recordID", id));
										and.add(new BasicDBObject("spaceID", spaId));
										DBObject queryObject = new BasicDBObject("$and", and);
										connector.delete(MongoDBConnector.SPACES_COLLECTION_NAME, queryObject);
									}
								}
								for(String s: space.split(" ")){ // now insert new ones
									if(s.equals("") || s.length()<2){ continue; }
									else {
										data = new HashMap<Object, Object>();
										data.put("recordID", id);
										data.put("spaceID", s);
										DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
										data.put("timedate", dateFormat.format(new Date()));
										connector.insert(MongoDBConnector.SPACES_COLLECTION_NAME, data);
									}
								}
							}
							
							/* old version
							for(String s: space.split(" ")){
								and = new BasicDBList();
								and.add(new BasicDBObject("recordID", id));
								and.add(new BasicDBObject("spaceID", s));
								DBObject queryObject = new BasicDBObject("$and", and);
								DBCursor res = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, queryObject);
								if(res==null || !res.hasNext()){
									HashMap<Object, Object> data = new HashMap<Object, Object>();
									data.put("recordID", id);
									data.put("spaceID", s);
									DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
									data.put("timedate", dateFormat.format(new Date()));
									connector.insert(MongoDBConnector.SPACES_COLLECTION_NAME, data);
								} else {
									// else do nothing, since it is already present
								}
								//objectList.add(new BasicDBObject("space", s));  // old old version
							}
							/* old old version
							BasicDBList list = new BasicDBList();
							list.add(new ObjectId(id));
							connector.update(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$set", new BasicDBObject("spaces", objectList)));
							*/
						} else {
							errorMessage = "\"Record doPost: If you provide the attribute 'id' you need either to provide the attribute 'circle' or 'space' as well!\"";
							wasError = true;
						}
					} else { // insert
						name = request.getParameter("name");
						String descr = request.getParameter("descr");
						String values = request.getParameter("values");
						if(name==null || name.length()<2 || values==null || values.length()<5 || descr==null || descr.length()<1){
							errorMessage = "\"Record doPost: Please provide the parameters 'name', 'descr' and 'values' with the request.\"";
							wasError = true;
						}
						if(!wasError){
							name = URLDecoder.decode(name, "UTF-8");
							descr = URLDecoder.decode(descr, "UTF-8");
							values = URLDecoder.decode(values, "UTF-8");
							
							// check DB connection
							if(!connector.isConnected()){
								manager.reconnect();
							}
							
							HashMap<Object, Object> data = new HashMap<Object, Object>();
							data.put("name", name);
							data.put("descr", descr);
							DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
							data.put("timedate", dateFormat.format(new Date()));
							
							JSONParser jsonParser = new JSONParser();
							data.put("userID", manager.getUserID(credentials));
							JSONObject json = (JSONObject) jsonParser.parse(values);
							for(Object key : json.keySet()){
								Object value = json.get(key);
								data.put(key, value);
							}
							
							// TODO  File upload
							
							connector.insert(MongoDBConnector.RECORDS_COLLECTION_NAME, data);
						}
					}
				}
			} catch (ParseException e) {
				errorMessage = "\"Record doPost: There was an error while parsing your JSON in parameter values. Did you provide correct JSON? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"Record doPost: We lost connection to the DB. Please try again later. Sorry for that.\"";
				wasError = true;
			} catch (IllegalQueryException e) {
				errorMessage = "\"Record doPost: There was an internal error. Please contact an administrator and provide him/her with this message: "+e.getMessage()+"\"";
				wasError = true;
			}
		}
		
		// Finally give the user feedback
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError) {
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Entry stored successfully\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Stored record with name "+name+" to the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Entry stored successfully\" }");
				if(MongoDBConnector.DEBUG){System.out.println("Stored record with name "+name+" to the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} 
	}

}
