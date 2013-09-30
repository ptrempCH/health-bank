package ch.ethz.inf.systems.ptremp.healthbank.REST.user;

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
 * Servlet implementation class Space
 * In this servlet we implement the functionality to get information about certain spaces via a GET
 * request and to store and edit individual spaces via the POST request.
 * 
 * @author Patrick Tremp
 */
@WebServlet(
		description = "Get and set information on user spaces", 
		urlPatterns = { 
				"/Space", 
				"/space"
		})
public class Space extends HttpServlet {

	private static final long serialVersionUID = -8550082360551645345L;
	
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
     * Main Constructor
     * @see HttpServlet#HttpServlet()
     */
    public Space() {
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
	 * This GET request allows the caller in three different ways to get information about one or multiple spaces belonging to the 
	 * user with the given session key and credentials. One can get information via a space name, an object id or by retrieving all
	 * spaces of the given user at once.
	 * 
	 * For a successful call the following parameters need to be present in the URL:
	 * Via Name:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - name: The name of the space to search information from. 
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Via id:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - id: The id of the space to search information from. 
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 *  Return all spaces of the given user:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * If the call was successful, the information will be in the 'values' attribute of the resulting JSON string 
	 * 
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	@SuppressWarnings("unchecked")
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		String values = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		
		// check parameters user specific parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		String id = request.getParameter("id");
		String name = request.getParameter("name");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Please provide the parameters 'session' and 'credentials' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			if(id!=null && id.length()>0){id = URLDecoder.decode(id, "UTF-8");}
			
			// check DB connection
			if(!connector.isConnected()){
				manager.reconnect();
			}
			
			// check if user is logged in and get the spaces 
			try {
				if(!manager.isUserLoggedIn(session, credentials)){
					errorMessage = "\"Space doGet: You need to be logged in to use this service\"";
					wasError = true;
					isLoggedIn = false;
				} else {
					BasicDBList list = new BasicDBList();
					DBCursor res = null;
					if(name!=null && name.length()>0){ // search via name
						list.add(manager.getUserID(credentials));
						BasicDBObject q1 = new BasicDBObject("userID", new BasicDBObject("$in", list));
						BasicDBObject q2 = new BasicDBObject("name", name);
						list.add(q1);
						list.add(q2);
						res = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("$and", list));
					} else if(id!=null && id.length()>0){ // search via id
						list.add(new ObjectId(id));
						res = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
					} else { // return all the spaces of the given user
						list.add(manager.getUserID(credentials));
						res = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("userID", new BasicDBObject("$in", list)));
						if(res==null){ // maybe due to some issues, the user does not have the basic spaces registered. So register them for him and try again.
							manager.createCoreSpaces(manager.getUserID(credentials));
							res = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("userID", new BasicDBObject("$in", list)));
						}
					}
					if(res==null){
						errorMessage = "\"Space doGet: There was an error, we did not find any spaces. Did you provide the correct sessionKey and credentials?\"";
						wasError = true;
					}
					if(!wasError){
						JSONParser parser = new JSONParser();
						JSONArray resArray = new JSONArray();
						while(res.hasNext()){
							DBObject obj = res.next();
							JSONObject resObj = (JSONObject) parser.parse(obj.toString());
							resArray.add(resObj);
						}
						JSONObject result = new JSONObject();
						result.put("spaces", resArray);
						values = result.toJSONString();
					}
				}
			} catch (IllegalArgumentException e) {
				errorMessage = "\"Space doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (IllegalQueryException e) {
				errorMessage = "\"Space doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"Space doGet: There was a connection error to the database. Please try again later.\"";
				wasError = true;
			} catch (ParseException e) {
				errorMessage = "\"Space doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			}
		}
		
		// Finally give the user feedback
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError){
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"values\" : "+values+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Returned spaces for the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"values\" : "+values+" }");
				if(MongoDBConnector.DEBUG){System.out.println("Returned spaces for the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} 
	}

	/**
	 * The POST request allows the caller to add new spaces, edit them and add or remove circles to/from it.
	 * There are three different possibilities for this call. One to add new spaces, one to edit them and one
	 * for the manipulation of circles in the space.
	 * 
	 * For a successful call, the following parameters need to be present in the URL:
	 * Adding a new space:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - name: The name of the space to add
	 * - descr: The description of the space
	 * - visualization: The associated visualization for this space
	 * - hidden: (optional)Defines if the space is visible for the user or hidden. defaults to 'false'
	 * 
	 * Editing an existing space:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - id: The id of the space to edit
	 * - name: (optional, but either name, descr, url, visualization or hidden must be set) The name of the space to edit
	 * - descr: (optional, but either name, descr, url, visualization or hidden must be set) The description of the space
	 * - visualization: (optional, but either name, descr, url, visualization or hidden must be set) The associated visualization for this space
	 * - hidden: (optional, but either name, descr, url, visualization or hidden must be set) Defines if the space is visible for the user or hidden. 
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Changing the circles associated with a space
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - id: The id of the space to edit
	 * - circles: A string containing the circles ID's separated by a space
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * 
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 * 
	 * TODO:
	 * - implement deletion of a space
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		String name = "", id="";
		
		// check parameters user specific parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Circle doPost: Please provide the parameters 'session' and 'credentials' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			
			// check record specific parameters
			name = request.getParameter("name");
			String descr = request.getParameter("descr");
			String visualization = request.getParameter("visualization");
			String hidden = request.getParameter("hidden");
			String circle = request.getParameter("circle");
			id = request.getParameter("id");
			
			// check if user is logged in and save the new value
			try {
				if(!manager.isUserLoggedIn(session, credentials)){
					errorMessage = "\"Space doPost: You need to be logged in to use this service\"";
					wasError = true;
					isLoggedIn = false;
				} else {
					name = (name!=null && name.length()>0)?URLDecoder.decode(name, "UTF-8"):null;
					descr = (descr!=null && descr.length()>0)?URLDecoder.decode(descr, "UTF-8"):null;
					visualization = (visualization!=null && visualization.length()>0)?URLDecoder.decode(visualization, "UTF-8"):null;
					hidden = (hidden!=null && hidden.length()>0)? URLDecoder.decode(hidden, "UTF-8"): null;
					id = (id!=null && id.length()>0)?URLDecoder.decode(id, "UTF-8"): null;
					circle = (circle!=null && circle.length()>0)?URLDecoder.decode(circle, "UTF-8"): null;
					
					// check DB connection
					if(!connector.isConnected()){
						manager.reconnect();
					}

					HashMap<Object, Object> data = new HashMap<Object, Object>();
					BasicDBList list = new BasicDBList();
					if(id==null) { // add new space
						if(name==null || name.length()<2 || descr==null || descr.length()<1){
							errorMessage = "\"Space doPost: Please provide the parameters 'name' and 'descr'with the request.\"";
							wasError = true;
						}
						if(!wasError){
							System.out.println("checking if space already exists");
							// check if exists already
							list.add(manager.getUserID(credentials));
							BasicDBObject q1 = new BasicDBObject("userID", new BasicDBObject("$in", list));
							BasicDBObject q2 = new BasicDBObject("name", name);
							list = new BasicDBList();
							list.add(q1);
							list.add(q2);
							DBCursor res = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("$and", list));
							System.out.println(MongoDBConnector.SPACES_COLLECTION_NAME);
							if(res!=null && res.hasNext()){
								errorMessage = "\"Space doPost: A space with this name already exists.\"";
								wasError = true;								
							} else {
								data.put("name", name);
								data.put("descr", descr);
								data.put("visualization", visualization);
								hidden = (hidden==null)?"false":hidden;
								data.put("hidden", hidden);
								DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
								data.put("timedate", dateFormat.format(new Date()));
								data.put("userID", manager.getUserID(credentials));
								
								connector.insert(MongoDBConnector.SPACES_COLLECTION_NAME, data);
							}
						}
					} else {
						if(id.length()<2){
							errorMessage = "\"Space doPost: Please provide a correct id argument.\"";
							wasError = true;
						}
						if(!wasError){
							list.add(new ObjectId(id));
							if(circle!=null){ // add/remove circle
								circle = URLDecoder.decode(circle, "UTF-8");
								int i=0;
								for(String s: circle.split(" ")){
									if(s.length()>0){
										data.put("circle"+i, s);
										i++;
									}
								}
								connector.update(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$set", new BasicDBObject("circles", data))); 
								
								// apply to all records assigned to this space
								DBCursor res = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("spaceID", new BasicDBObject("$in", list)));
								if(res!=null && res.hasNext()){
									String recordID;
									DBObject obj, tmp;
									DBCursor rec;
									BasicDBList newResults;
									while(res.hasNext()){
										obj = res.next();
										if(obj!=null && obj.containsField("recordID")){
											recordID = (String) obj.get("recordID");
											if(recordID != null && ObjectId.isValid(recordID)){
												rec = (DBCursor) connector.query(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("_id", new ObjectId(recordID)));
												if(rec!=null && rec.hasNext()){
													tmp = rec.next();
													if(tmp.containsField("circles")){ // read existing circles and add possible new ones
														newResults = new BasicDBList();
														BasicDBList curCir = (BasicDBList) tmp.get("circles");
														boolean found = false;
														String curCirID;
														for(int l=0;l<curCir.size();l++){
															curCirID = ((BasicDBObject) curCir.get(l)).getString("circle");
															if(curCirID!=null && ObjectId.isValid(curCirID)){
																newResults.add(new BasicDBObject("circle", curCirID));
															}
														}
														for(String s: circle.split(" ")){
															found = false;
															for(int l=0;l<curCir.size();l++){
																if(s.equals(((BasicDBObject) curCir.get(l)).getString("circle"))){
																	found=true;
																	break;
																}
															}
															if(!found){
																newResults.add(new BasicDBObject("circle", s));
															}
														}
														connector.update(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("_id", new ObjectId(recordID)), new BasicDBObject("$set", new BasicDBObject("circles", curCir)));
														
													} else { // Entry has no record assignment, so we need to assign the ones set for the space
														connector.update(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("_id", new ObjectId(recordID)), new BasicDBObject("$set", new BasicDBObject("circles", data)));
													}
												} else { continue; }
											} else { continue; }
										}
									}
									
								}
								
							} else if(name!=null || descr!=null || visualization!=null || hidden!=null){ // update space
								if(name!=null && name.length()>0){data.put("name", name);}
								if(descr!=null && descr.length()>0){data.put("descr", descr);}
								if(visualization!=null && visualization.length()>0){data.put("visualization", visualization);}
								if(hidden!=null && hidden.length()>0){data.put("hidden", hidden); System.out.println(hidden);}
								connector.update(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$set", data));
							}
						}
					}
				}
			} catch (NotConnectedException e) {
				errorMessage = "\"Space doPost: We lost connection to the DB. Please try again later. Sorry for that.\"";
				wasError = true;
			} catch (IllegalQueryException e) {
				errorMessage = "\"Space doPost: There was an internal error. Please contact an administrator and provide him/her with this message: "+e.getMessage()+"\"";
				wasError = true;
			}
		}
		
		// Finally give the user feedback
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError) {
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Space stored successfully\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Stored space with name "+name+" to the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Space stored successfully\" }");
				if(MongoDBConnector.DEBUG){System.out.println("Stored space with name "+name+" to the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} 
	}

}
