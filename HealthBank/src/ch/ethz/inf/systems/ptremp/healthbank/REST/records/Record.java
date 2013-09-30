package ch.ethz.inf.systems.ptremp.healthbank.REST.records;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSInputFile;

import ch.ethz.inf.systems.ptremp.healthbank.REST.user.authentication.Token;
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
	 * 	- Implement functionality to just return 20-50 items at a time and loading more lazily
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
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Record  doGet: Please provide the parameters 'credentials' and 'session' with the request.\"";
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
										JSONObject resObj = (JSONObject) parser.parse(recEntry.toString());
										resObj.put("spaces", spas);
										resArray.add(resObj);
										nrRes++;
									}
								} 
							}
							
						}
					} else {
						list.add(userId);
						res = (DBCursor) connector.query(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("userID", new BasicDBObject("$in", list)));
						if(res==null || !res.hasNext()){
							errorMessage = "\"Record doGet: You do not have any records yet! Add some first.\"";
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
								nrRes++;
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
	 * - appID: The id of the app that inserts the record
	 * - values: A JSON string that contains additional values for the record
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Insert for other user: 
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - userID: The id of the user other then the current one to which this record shall be added
	 * - name: The name of the record
	 * - descr: A description for the record
	 * - appID: The id of the app that inserts the record
	 * - values: A JSON string that contains additional values for the record
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Insert with multipart:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - name: The name of the record
	 * - descr: A description for the record
	 * - appID: The id of the app that inserts the record
	 * - values: A JSON string that contains additional values for the record
	 * - file: The file to upload
	 * 
	 *  Insert with multipart for another user:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - name: The name of the record
	 * - descr: A description for the record
	 * - appID: The id of the app that inserts the record
	 * - values: A JSON string that contains additional values for the record
	 * - file: The file to upload
	 * - userID: The id of the user other then the current one to which this record shall be added
	 * 
	 * Insert from app for user that is not currently logged in. 
	 * - userID: The id of the user the record shall be assigned to
	 * - appID: The id of the application that likes to store this record
	 * - token: The token received from the call to {@link Token} 
	 * - name: The name of the record
	 * - descr: A description for the record
	 * - values: A JSON string that contains additional values for the record
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
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		String name = "", id = "", userID = "", descr = "", values = "", callback = "", credentials = "", session = "", appID = "";
		
		// START MULTIPART
		if (ServletFileUpload.isMultipartContent(request)) {
			System.out.println("Record doPost: We received a multipart request");
			try {
				GridFS fs = new GridFS( connector.getRootDatabase(), MongoDBConnector.RECORDS_COLLECTION_NAME);
				GridFSInputFile in  = null;
				List<FileItem> items = new ServletFileUpload(new DiskFileItemFactory()).parseRequest(request);
		        for (FileItem item : items) {
		            if (!item.isFormField()) {
		                // Process form file field (input type="file")
		            	InputStream filecontent = item.getInputStream();
		                String filename = item.getName();
		                filename = URLDecoder.decode(filename, "UTF-8");
						//Save image into database
		                in = fs.createFile( filecontent );
		                in.setFilename(filename);
		                in.setContentType(item.getContentType());
		                in.setId(ObjectId.get());
		                //in.save();
		            } else {
		            	switch (item.getFieldName()) {
						case "session":
							session = item.getString();
							break;
						case "credentials":
							credentials = item.getString();
							break;
						case "name":
							name = item.getString();
							break;
						case "descr":
							descr = item.getString();
							break;
						case "values":
							values = item.getString();
							break;
						case "appID":
							appID = item.getString();
							break;
						case "userID":
							userID = item.getString();
							break;
						default:
							break;
						}
		            }
		        }

		        BasicDBList list = new BasicDBList();
		        if(credentials==null || credentials.length()<1 || session==null || session.length()<1){
		        	errorMessage = "\"Record doPost: You need to provide the attributes credential and sesssion.\"";
					wasError = true;
		        } else {
			        credentials = URLDecoder.decode(credentials, "UTF-8");
					credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
					session = URLDecoder.decode(session, "UTF-8");
					if(credentials.contains(":")){list.add(credentials.substring(0, credentials.lastIndexOf(':')));}
		        
					if(!manager.isUserLoggedIn(session, credentials)){
						errorMessage = "\"Record doPost: You need to be logged in to use this service\"";
						wasError = true;
						isLoggedIn = false;
					} else {
						DBCursor res = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("username", new BasicDBObject("$in", list)));
						if(res==null || !res.hasNext()){
							errorMessage = "\"No user found with these credentials. Did you provide the correct sessionKey and credentials?\"";
							wasError = true;
						}
						if(!wasError){
							DBObject obj = res.next();
							ObjectId myUserID = (ObjectId) obj.get("_id");
							
							System.out.println(name+", "+values+", "+descr);
							if(appID==null || appID.length()<2 || name==null || name.length()<2 || values==null || values.length()<5 || descr==null || descr.length()<1){
								errorMessage = "\"Record doPost: Please provide the parameters 'appID', 'name', 'descr' and 'values' with the request.\"";
								wasError = true;
							}
							if(!wasError){
								name = URLDecoder.decode(name, "UTF-8");
								descr = URLDecoder.decode(descr, "UTF-8");
								values = URLDecoder.decode(values, "UTF-8");
								appID = URLDecoder.decode(appID, "UTF-8");
								if(userID!=null){userID = URLDecoder.decode(userID, "UTF-8");}
								
								// check DB connection
								if(!connector.isConnected()){
									manager.reconnect();
								}
								
								HashMap<Object, Object> data = new HashMap<Object, Object>();
								data.put("appID", appID);
								data.put("name", name);
								data.put("descr", descr);
								DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
								data.put("timedate", dateFormat.format(new Date()));
								
								if(userID!=null && userID.length()>0){ // add to another user
									// check if the desired user has you in a circle
									ArrayList<String> circlesIAmInOfAUser = manager.getCirclesIAmInOfAUser(manager.getUserID(credentials), userID);
									if(circlesIAmInOfAUser!=null && circlesIAmInOfAUser.size()>0){
										data.put("userID", userID);
									} else {
										errorMessage = "\"Record doPost: You are not allowed to add entries to this users health record.\"";
										wasError = true;
									}
								} else { // add to current user
									data.put("userID", manager.getUserID(credentials));
								}
								
								if(in!=null){
									if(myUserID!=null){in.put("userID", myUserID);}
									in.save();
									data.put("fileID", in.getId());
									data.put("fileName", in.getFilename());
								}
								
								
								JSONParser jsonParser = new JSONParser();
								JSONObject json = (JSONObject) jsonParser.parse(values);
								for(Object key : json.keySet()){
									Object value = json.get(key);
									data.put(key, value);
								}
								
								
								// check if for this application the user has defined spaces or circles
								list = new BasicDBList();
								list.add(manager.getUserID(credentials));
								BasicDBObject q1 = new BasicDBObject("userID", new BasicDBObject("$in", list));
								BasicDBObject q2 = new BasicDBObject("appID", appID);
								BasicDBList myList = new BasicDBList();
								myList.add(q1);
								myList.add(q2);
								BasicDBObject and = new BasicDBObject("$and", myList);
								res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, and);
								BasicDBList mySpaces = null;
								if(res!=null && res.hasNext()){
									obj = (BasicDBObject) res.next();
									BasicDBList myCircles = null;
									if(obj.containsField("spaces")){ mySpaces = (BasicDBList) obj.get("spaces"); }
									if(obj.containsField("circles")){ myCircles = (BasicDBList) obj.get("circles"); }
									if(myCircles!=null){
										data.put("circles", myCircles);
										System.out.println("added circles to new record");
									}
								}
								
								ObjectId newId = (ObjectId) connector.insert(MongoDBConnector.RECORDS_COLLECTION_NAME, data);
								if(mySpaces!=null && !mySpaces.isEmpty()){
									System.out.println("mySpaces size: "+mySpaces.size());
									for(int i=0; i<mySpaces.size();i++){
										data = new HashMap<Object, Object>();
										data.put("recordID", newId.toString());
										data.put("spaceID", ((BasicDBObject)mySpaces.get(i)).getString("space"));
										data.put("timedate", dateFormat.format(new Date()));
										connector.insert(MongoDBConnector.SPACES_COLLECTION_NAME, data);
										System.out.println("added space to new record with id:"+newId);
									}
								}
							}
						}
					}
		        }
				
			} catch (ParseException e) {
				errorMessage = "\"Record doPost: There was an error while parsing your JSON in parameter values. Did you provide correct JSON? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (FileUploadException e) {
		        errorMessage = "\"Application doPost: Cannot parse multipart request...\"";
				wasError = true;
		    } catch (IllegalQueryException e) {
		    	errorMessage = "\"Application doPost: There was an illegal query to the DB...\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"Application doPost: Not connected to the DB...\"";
				wasError = true;
			}
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Record stored successfully\" }");
				if(MongoDBConnector.DEBUG){System.out.println("Stored record successfully for user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} // FINISH MULTIPART
		else {
			appID = request.getParameter("appID");
			if(appID!=null){appID = URLDecoder.decode(appID, "UTF-8");}
			userID = request.getParameter("userID");
			String token = request.getParameter("token");
			if(appID!=null && appID.length()>0 && token!=null && token.length()>0 && userID!=null && userID.length()>0){ // call from external server using push 
				if(token==null || token.length()<2 || userID==null || userID.length()<2){
					errorMessage = "\"Record doPost: Please provide the parameters 'userID', 'appID' and 'token' with the request.\"";
					wasError = true;
				} else {
					token = URLDecoder.decode(token, "UTF-8");
					userID = URLDecoder.decode(userID, "UTF-8");
					try{
						BasicDBObject q1 = new BasicDBObject("userID", userID);
						BasicDBObject q2 = new BasicDBObject("appID", appID);
						ArrayList<BasicDBObject> myList = new ArrayList<BasicDBObject>();
						myList.add(q1);
						myList.add(q2);
						BasicDBObject and = new BasicDBObject("$and", myList);
						DBCursor res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, and);
						if(res==null || !res.hasNext()){
							wasError=true;
							errorMessage = "Record doPost: User does not have this app installed!";
						} else {
							BasicDBObject install = (BasicDBObject) res.next();
							if(install!=null){
								String savedToken = install.getString("token");
								if(savedToken.equals(token)){
									String expiresToken = install.getString("token_expires");
									DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
									Date d = dateFormat.parse(expiresToken);
									if(d.before(new Date())){
										wasError=true;
										errorMessage = "Record doPost: Token is expired. Please get another one!";
									} else {
										name = request.getParameter("name");
										descr = request.getParameter("descr");
										values = request.getParameter("values");
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
											data.put("timedate", dateFormat.format(new Date()));
											JSONParser jsonParser = new JSONParser();
											JSONObject json = (JSONObject) jsonParser.parse(values);
											for(Object key : json.keySet()){
												Object value = json.get(key);
												data.put(key, value);
											}
											data.put("appID", appID);
											data.put("userID", userID);
											
											// check if for this application the user has defined spaces or circles
											BasicDBList list = new BasicDBList();
											list.add(manager.getUserID(credentials));
											q1 = new BasicDBObject("userID", new BasicDBObject("$in", list));
											q2 = new BasicDBObject("appID", appID);
											BasicDBList myList2 = new BasicDBList();
											myList2.add(q1);
											myList2.add(q2);
											and = new BasicDBObject("$and", myList2);
											res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, and);
											BasicDBList mySpaces = null;
											if(res!=null && res.hasNext()){
												BasicDBObject obj = (BasicDBObject) res.next();
												BasicDBList myCircles = null;
												if(obj.containsField("spaces")){ mySpaces = (BasicDBList) obj.get("spaces"); }
												if(obj.containsField("circles")){ myCircles = (BasicDBList) obj.get("circles"); }
												if(myCircles!=null){
													data.put("circles", myCircles);
													System.out.println("added circles to new record");
												}
											}
											
											ObjectId newId = (ObjectId) connector.insert(MongoDBConnector.RECORDS_COLLECTION_NAME, data);
											if(mySpaces!=null && !mySpaces.isEmpty()){
												System.out.println("mySpaces size: "+mySpaces.size());
												for(int i=0; i<mySpaces.size();i++){
													data = new HashMap<Object, Object>();
													data.put("recordID", newId.toString());
													data.put("spaceID", ((BasicDBObject)mySpaces.get(i)).getString("space"));
													data.put("timedate", dateFormat.format(new Date()));
													connector.insert(MongoDBConnector.SPACES_COLLECTION_NAME, data);
													System.out.println("added space to new record with id:"+newId);
												}
											}
											
											
											
											connector.update(MongoDBConnector.APPLICATION_COLLECTION_NAME, and, new BasicDBObject("$set", new BasicDBObject("token", "")));
										}
									}
								} else {
									wasError=true;
									errorMessage = "Record doPost: Token is not valid. Use Token utility first to get a user specific access token!";
								}
							} else {
								wasError=true;
								errorMessage = "Record doPost: User does not have this app installed!";
							}
						}
					} catch (NotConnectedException e) {
						errorMessage = "\"Record doPost: We lost connection to the DB. Please try again later. Sorry for that.\"";
						wasError = true;
					} catch (IllegalQueryException e) {
						errorMessage = "\"Record doPost: There was an internal error. Please contact an administrator and provide him/her with this message: "+e.getMessage()+"\"";
						wasError = true;
					} catch (java.text.ParseException e) {
						errorMessage = "\"Record doPost: There was an internal parse error. Please contact an administrator and provide him/her with this message: "+e.getMessage()+"\"";
						wasError = true;
					} catch (ParseException e) {
						errorMessage = "\"Record doPost: There was an error while parsing your JSON in parameter values. Did you provide correct JSON? Error: "+e.getMessage()+"\"";
						wasError = true;
					}
					
					if(!wasError) {
						response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Entry stored successfully\" }");
						if(MongoDBConnector.DEBUG){System.out.println("Stored record with name "+name+" to the user with userID "+userID);}
					}
					else {
						response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" }");
						if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
					}
				}
				
			} else {
				// check parameters user specific parameters
				callback = request.getParameter("callback");
				credentials = request.getParameter("credentials");
				session = request.getParameter("session");
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
								} else {
									errorMessage = "\"Record doPost: If you provide the attribute 'id' you need either to provide the attribute 'circle' or 'space' as well!\"";
									wasError = true;
								}
							} else { // insert
								userID = request.getParameter("userID");
								name = request.getParameter("name");
								descr = request.getParameter("descr");
								values = request.getParameter("values");
								if(appID==null || appID.length()<2 || name==null || name.length()<2 || values==null || values.length()<5 || descr==null || descr.length()<1){
									errorMessage = "\"Record doPost: Please provide the parameters 'appID', 'name', 'descr' and 'values' with the request.\"";
									wasError = true;
								}
								if(!wasError){
									if(userID!=null){userID = URLDecoder.decode(userID, "UTF-8");}
									name = URLDecoder.decode(name, "UTF-8");
									descr = URLDecoder.decode(descr, "UTF-8");
									values = URLDecoder.decode(values, "UTF-8");
									
									// check DB connection
									if(!connector.isConnected()){
										manager.reconnect();
									}
									
									HashMap<Object, Object> data = new HashMap<Object, Object>();
									data.put("appID", appID);
									data.put("name", name);
									data.put("descr", descr);
									DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
									data.put("timedate", dateFormat.format(new Date()));
		
									if(userID!=null && userID.length()>0){ // add to another user
										// check if the desired user has you in a circle
										System.out.println(userID);
										ArrayList<String> circlesIAmInOfAUser = manager.getCirclesIAmInOfAUser(manager.getUserID(credentials), userID);
										if(circlesIAmInOfAUser!=null && circlesIAmInOfAUser.size()>0){
											data.put("userID", userID);
										} else {
											errorMessage = "\"Record doPost: You are not allowed to add entries to this users health record.\"";
											wasError = true;
										}
									} else { // add to current user
										data.put("userID", manager.getUserID(credentials));
									}
									
									JSONParser jsonParser = new JSONParser();
									JSONObject json = (JSONObject) jsonParser.parse(values);
									for(Object key : json.keySet()){
										Object value = json.get(key);
										data.put(key, value);
									}
									
									// check if for this application the user has defined spaces or circles
									BasicDBList list = new BasicDBList();
									list.add(manager.getUserID(credentials));
									BasicDBObject q1 = new BasicDBObject("userID", new BasicDBObject("$in", list));
									BasicDBObject q2 = new BasicDBObject("appID", appID);
									BasicDBList myList = new BasicDBList();
									myList.add(q1);
									myList.add(q2);
									BasicDBObject and = new BasicDBObject("$and", myList);
									DBCursor res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, and);
									BasicDBList mySpaces = null;
									if(res!=null && res.hasNext()){
										BasicDBObject obj = (BasicDBObject) res.next();
										BasicDBList myCircles = null;
										if(obj.containsField("spaces")){ mySpaces = (BasicDBList) obj.get("spaces"); }
										if(obj.containsField("circles")){ myCircles = (BasicDBList) obj.get("circles"); }
										if(myCircles!=null){
											data.put("circles", myCircles);
											System.out.println("added circles to new record");
										}
									}
									
									ObjectId newId = (ObjectId) connector.insert(MongoDBConnector.RECORDS_COLLECTION_NAME, data);
									if(mySpaces!=null && !mySpaces.isEmpty()){
										System.out.println("mySpaces size: "+mySpaces.size());
										for(int i=0; i<mySpaces.size();i++){
											data = new HashMap<Object, Object>();
											data.put("recordID", newId.toString());
											data.put("spaceID", ((BasicDBObject)mySpaces.get(i)).getString("space"));
											data.put("timedate", dateFormat.format(new Date()));
											connector.insert(MongoDBConnector.SPACES_COLLECTION_NAME, data);
											System.out.println("added space to new record with id:"+newId);
										}
									}
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
					} catch (IllegalArgumentException e) {
						errorMessage = "\"Record doPost: There was an internal error. Please contact an administrator and provide him/her with this message: "+e.getMessage()+"\"";
						wasError = true;
					}
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
	
	/**
	 * Not used yet. Needs discussion with stakeholders
	 */
	@Override
	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		String name = "", id = "", descr = "", values = "", callback = "", credentials = "", session = "";
		
		// START MULTIPART
		if (ServletFileUpload.isMultipartContent(request)) {
			System.out.println("Record doPut: We received a multipart request");
			try {
				GridFS fs = new GridFS( connector.getRootDatabase(), MongoDBConnector.RECORDS_COLLECTION_NAME);
				GridFSInputFile in  = null;
				List<FileItem> items = new ServletFileUpload(new DiskFileItemFactory()).parseRequest(request);
		        for (FileItem item : items) {
		            if (!item.isFormField()) {
		                // Process form file field (input type="file")
		            	InputStream filecontent = item.getInputStream();
		                String filename = item.getName();
		                filename = URLDecoder.decode(filename, "UTF-8");
						//Save image into database
		                in = fs.createFile( filecontent );
		                in.setFilename(filename);
		                in.setContentType(item.getContentType());
		                in.setId(ObjectId.get());
		            } else {
		            	switch (item.getFieldName()) {
						case "session":
							session = item.getString();
							break;
						case "credentials":
							credentials = item.getString();
							break;
						case "name":
							name = item.getString();
							break;
						case "descr":
							descr = item.getString();
							break;
						case "id":
							id = item.getString();
							break;
						case "values":
							values = item.getString();
							break;
						default:
							break;
						}
		            }
		        }

		        if(credentials==null || credentials.length()<1 || session==null || session.length()<1 || id==null || id.length()<1){
		        	errorMessage = "\"Record doPut: You need to provide the attributes credential, id and sesssion.\"";
					wasError = true;
		        } else {
			        credentials = URLDecoder.decode(credentials, "UTF-8");
					credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
					session = URLDecoder.decode(session, "UTF-8");
					id = URLDecoder.decode(id, "UTF-8");
					
					// check DB connection
					if(!connector.isConnected()){
						manager.reconnect();
					}
		        
					if(!manager.isUserLoggedIn(session, credentials)){
						errorMessage = "\"Record doPutt: You need to be logged in to use this service\"";
						wasError = true;
						isLoggedIn = false;
					} else {
						BasicDBList list = new BasicDBList();
						list.add(new ObjectId(id));
						DBCursor res = (DBCursor) connector.query(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
						if(res==null || !res.hasNext()){
							errorMessage = "\"Record doPut: No record found with this id. Did you provide the correct sessionKey and credentials?\"";
							wasError = true;
						}
						if(!wasError){
							DBObject record = res.next();
							BasicDBObject tmpUserID = (BasicDBObject) record.get("userID");
							if(manager.getUserID(credentials).equals(tmpUserID.toString())){
							
								HashMap<Object, Object> data = new HashMap<Object, Object>();
								
								if(name!=null){
									name = URLDecoder.decode(name, "UTF-8");
									data.put("name", name);
								}
								if(descr!=null){
									descr = URLDecoder.decode(descr, "UTF-8");
									data.put("descr", descr);
								}
								if(values!=null){ 
									values = URLDecoder.decode(values, "UTF-8"); 

									JSONParser jsonParser = new JSONParser();
									JSONObject json = (JSONObject) jsonParser.parse(values);
									for(Object key : json.keySet()){
										Object value = json.get(key);
										data.put(key, value);
									}
								}
								DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
								data.put("timedate", dateFormat.format(new Date()));
								
								if(in!=null){
									in.put("userID", manager.getUserID(credentials));
									in.save();
									data.put("fileID", in.getId());
									data.put("fileName", in.getFilename());
								}
								
								connector.update(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$set", data));
							} else {
								errorMessage = "\"Record doPut: The record with this id does not fit to the current user. You are not allowed to update any records of other users.\"";
								wasError = true;
							}
						}
					}
		        }
				
			} catch (ParseException e) {
				errorMessage = "\"Record doPut: There was an error while parsing your JSON in parameter values. Did you provide correct JSON? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (FileUploadException e) {
		        errorMessage = "\"Application doPut: Cannot parse multipart request...\"";
				wasError = true;
		    } catch (IllegalQueryException e) {
		    	errorMessage = "\"Application doPut: There was an illegal query to the DB...\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"Application doPut: Not connected to the DB...\"";
				wasError = true;
			}
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Record updated successfully\" }");
				if(MongoDBConnector.DEBUG){System.out.println("Updated record successfully for user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} // FINISH MULTIPART
		else {			
			// check parameters user specific parameters
			callback = request.getParameter("callback");
			credentials = request.getParameter("credentials");
			session = request.getParameter("session");
			id = request.getParameter("id");
			if(credentials==null || credentials.length()<2 || session==null || session.length()<4 || id==null || id.length()<1){
				errorMessage = "\"Record doPut: Please provide the parameters 'credentials', 'id' and 'session' with the request.\"";
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
						errorMessage = "\"Record doPut: You need to be logged in to use this service\"";
						wasError = true;
						isLoggedIn = false;
					} else {
						// check DB connection
						if(!connector.isConnected()){
							manager.reconnect();
						}
						BasicDBList list = new BasicDBList();
						list.add(new ObjectId(id));
						DBCursor res = (DBCursor) connector.query(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
						if(res==null || !res.hasNext()){
							errorMessage = "\"Record doPut: No record found with this id. Did you provide the correct sessionKey and credentials?\"";
							wasError = true;
						}
						if(!wasError){
							DBObject record = res.next();
							BasicDBObject tmpUserID = (BasicDBObject) record.get("userID");
							if(manager.getUserID(credentials).equals(tmpUserID.toString())){
								name = request.getParameter("name");
								descr = request.getParameter("descr");
								values = request.getParameter("values");

								HashMap<Object, Object> data = new HashMap<Object, Object>();
								if(name!=null){
									name = URLDecoder.decode(name, "UTF-8");
									data.put("name", name);
								}
								if(descr!=null){
									descr = URLDecoder.decode(descr, "UTF-8");
									data.put("descr", descr);
								}
								if(values!=null){ 
									values = URLDecoder.decode(values, "UTF-8"); 
									JSONParser jsonParser = new JSONParser();
									JSONObject json = (JSONObject) jsonParser.parse(values);
									for(Object key : json.keySet()){
										Object value = json.get(key);
										data.put(key, value);
									}
								}
								
								DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
								data.put("timedate", dateFormat.format(new Date()));
								
								connector.update(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)),  new BasicDBObject("$set", data));						
							} else {
								errorMessage = "\"Record doPut: The record with this id does not fit to the current user. You are not allowed to update any records of other users.\"";
								wasError = true;
							}
						}
						
					}
				} catch (ParseException e) {
					errorMessage = "\"Record doPut: There was an error while parsing your JSON in parameter values. Did you provide correct JSON? Error: "+e.getMessage()+"\"";
					wasError = true;
				} catch (NotConnectedException e) {
					errorMessage = "\"Record doPut: We lost connection to the DB. Please try again later. Sorry for that.\"";
					wasError = true;
				} catch (IllegalQueryException e) {
					errorMessage = "\"Record doPut: There was an internal error. Please contact an administrator and provide him/her with this message: "+e.getMessage()+"\"";
					wasError = true;
				}
			}
		
			// Finally give the user feedback
			if(callback!=null && callback.length()>0){
				callback = URLDecoder.decode(callback, "UTF-8");
				if(!wasError) {
					response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Entry updated successfully\" } );");
					if(MongoDBConnector.DEBUG){System.out.println("Updated record with name "+name+" to the user with sessionKey "+session);}
				}
				else {
					response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" } );");
					if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
				}
			} else {
				if(!wasError) {
					response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Entry updated successfully\" }");
					if(MongoDBConnector.DEBUG){System.out.println("Updated record with name "+name+" to the user with sessionKey "+session);}
				}
				else {
					response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" }");
					if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
				}
			} 
		}
	}
	
	/**
	 * Not used yet. Needs discussion with stakeholders
	 */
	@Override
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		
		// check parameters user specific parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		String id = request.getParameter("id");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4 || id==null || id.length()<4){
			errorMessage = "\"Record doDelete: Please provide the parameters 'credentials', 'session' and 'id' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			id = URLDecoder.decode(id, "UTF-8");
			
			// check DB connection
			if(!connector.isConnected()){
				manager.reconnect();
			}
			
			// check if user is logged in and get the records 
			try {
				if(!manager.isUserLoggedIn(session, credentials)){
					errorMessage = "\"Record doDelete: You need to be logged in to use this service\"";
					wasError = true;
					isLoggedIn = false;
				} else {
					// first check if the id is a record of the current user
					DBCursor res;
					BasicDBList list = new BasicDBList();
					list.add(new ObjectId(id));
					res = (DBCursor) connector.query(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
					if(res==null || !res.hasNext()){
						errorMessage = "\"Record doDelete: We could not find any record with the provided id: "+id+"\"";
						wasError = true;
					} else {
						DBObject record = res.next();
						BasicDBObject userID = (BasicDBObject) record.get("userID");
						if(manager.getUserID(credentials).equals(userID.toString())){
							// Then Delete possible file 
							Object recFile = record.get("fileID");
							if(recFile!=null){
								ObjectId fileID = new ObjectId();
								if(recFile instanceof ObjectId){
									fileID = (ObjectId) recFile;
								} else if(recFile instanceof String){
									fileID = new ObjectId((String) recFile);
								}
								GridFS fs = new GridFS( connector.getRootDatabase(), MongoDBConnector.RECORDS_COLLECTION_NAME);
								fs.remove(fileID);
							}
							// finally delete record
							connector.delete(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
						} else {
							errorMessage = "\"Record doDelete: The provided id: "+id+" is not a valid record id for this user\"";
							wasError = true;
						}
					}							
				}
			} catch (IllegalArgumentException e) {
				errorMessage = "\"Record doDelete: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (IllegalQueryException e) {
				errorMessage = "\"Record doDelete: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"Record doDelete: There was a connection error to the database. Please try again later.\"";
				wasError = true;
			} 
		}
		
		// Finally give the user feedback
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError){
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\" : \"Successfully deleted record with id: "+id+"\"} );");
				if(MongoDBConnector.DEBUG){System.out.println("Deleted record with id "+id+" from the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\" : \"Successfully deleted record with id: "+id+"\"} );");
				if(MongoDBConnector.DEBUG){System.out.println("Deleted record with id "+id+" from the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} 
	}

}
