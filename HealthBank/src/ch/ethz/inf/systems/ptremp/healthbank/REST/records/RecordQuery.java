package ch.ethz.inf.systems.ptremp.healthbank.REST.records;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.regex.Pattern;

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
 * Servlet implementation class RecordQuery
 * In this servlet we implement the functionality to query for records. It therefore only supports
 * GET requests.
 * 
 * @author Patrick Tremp
 */
@WebServlet(
		description = "Query for Records", 
		urlPatterns = { 
				"/RecordQuery", 
				"/recordquery", 
				"/rq"
		})
public class RecordQuery extends HttpServlet {

	private static final long serialVersionUID = 3599963803086380098L;
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
    public RecordQuery() {
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
	 * The GET request allows the caller to query for single or multiple records. There are multiple ways of calling this method. 
	 * Either one can query a specific record by its id or query one or multiple records by a query string. The more one can get
	 * all the records of a specific user by providing the userId parameter or even all records of people in a specific circle of
	 * the currently logged in user by setting the circleId parameter. 
	 * 
	 * For a successful call the following parameters need to be present in the URL:
	 * With query:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - query: A query string which should match records with similar strings in attributes name, descr, or app
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * With id:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - id: The id of a specific record
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * With userId:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - userId: The id of a user
	 * - spaceId: (optional) The id of a space. If this is set, only records of user userId which are in space spaceId will be returned
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server) 
	 * 
	 * With circleId
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - circleId: The id of a circle
	 * - spaceId: (optional) The id of a space. If this is set, only records of users in circle circleId which are in space spaceId will be returned
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 * 
	 */
	@SuppressWarnings("unchecked")
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		String result = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		boolean found = false;
		
		// check parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		String query = request.getParameter("query");
		String recordId = request.getParameter("id");
		String userId = request.getParameter("userId");
		String circleId = request.getParameter("circleId");
		String spaceId = request.getParameter("spaceId");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Please provide the parameters 'credentials' and 'session' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			if(query!=null && query.length()>0){query = URLDecoder.decode(query, "UTF-8");}
			if(recordId!=null && recordId.length()>0){recordId = URLDecoder.decode(recordId, "UTF-8");}
			if(userId!=null && userId.length()>0){userId = URLDecoder.decode(userId, "UTF-8");}
			if(circleId!=null && circleId.length()>0){circleId = URLDecoder.decode(circleId, "UTF-8");}
			if(spaceId!=null && spaceId.length()>0) {spaceId = URLDecoder.decode(spaceId, "UTF-8");}
			
			// check DB connection
			if(connector==null || !connector.isConnected()){
				manager.reconnect();
			}
			
			// check if user is logged in
			try {
				if(manager.isUserLoggedIn(session, credentials)){
					BasicDBList list = new BasicDBList();
					int foundItemsCount = 0;
					DBCursor res;
					JSONArray resArray = new JSONArray();
					if(recordId!=null && recordId.length()>0){
						list.add(new ObjectId(recordId));
						res = (DBCursor) connector.query(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
						if(res!=null && res.size()>0){
							JSONParser parser = new JSONParser();
							DBObject obj = res.next();
							String curId = obj.get("userID").toString();
							if(!curId.equals(manager.getUserID(credentials))){
								ArrayList<String> circles = manager.getCirclesIAmInOfAUser(manager.getUserID(credentials), userId);
								if(circles.size()>0){
									BasicDBList recordsCircles = (BasicDBList) obj.get("circles");
									if(recordsCircles!=null && !recordsCircles.isEmpty()){
										found = false;
										for (int i=0;i<recordsCircles.size();i++) { 
											for(int j=0;j<circles.size();j++){
												if(recordsCircles.get(i).toString().contains(circles.get(j))){ 
													JSONObject resObj = (JSONObject) parser.parse(obj.toString());
													resArray.add(resObj);
													foundItemsCount++;
													found = true;
													break;
												}
											}
											if(found){break;}
										} 
									} 
								}
							} else {
								resArray.add(parser.parse(obj.toString()));
							}
							
							if(!resArray.isEmpty()){
								JSONObject resObj = new JSONObject();
								resObj.put("records", resArray);
								result = resObj.toJSONString();	
							} else {
								errorMessage = "\"No entry found for this id. Or maybe you are not allowed to see this record\"";
								wasError = true;
							}
						}
					} else if(query!=null && query.length()>0){
						if(query.equals("*")){
							query = "\\w*";
						}
						Pattern regex = Pattern.compile("\\w*"+query+"\\w*");
						list.add(regex);
						regex = Pattern.compile("\\w*"+query.toLowerCase()+"\\w*");
						list.add(regex);
						regex = Pattern.compile("\\w*"+query.toUpperCase()+"\\w*");
						list.add(regex);
						regex = Pattern.compile("\\w*"+query.replace(query.charAt(0), query.substring(0, 1).toUpperCase().charAt(0))+"\\w*");
						list.add(regex);
						DBObject app = new BasicDBObject("app", new BasicDBObject("$in", list));
						DBObject name = new BasicDBObject("name", new BasicDBObject("$in", list));
						DBObject descr = new BasicDBObject("descr", new BasicDBObject("$in", list));
						BasicDBList or = new BasicDBList();
						or.add(app);
						or.add(name);
						or.add(descr);
						DBObject queryObject = new BasicDBObject("$or", or);
						res = (DBCursor) connector.query(MongoDBConnector.RECORDS_COLLECTION_NAME, queryObject);
						if(res!=null && res.size()>0){
							JSONParser parser = new JSONParser();
							while(res.hasNext()){
								DBObject obj = res.next();
								String curId = obj.get("userID").toString();
								if(!curId.equals(manager.getUserID(credentials))){
									ArrayList<String> circles = manager.getCirclesIAmInOfAUser(manager.getUserID(credentials), curId);
									if(circles.size()>0){
										BasicDBList recordsCircles = (BasicDBList) obj.get("circles");
										if(recordsCircles!=null && !recordsCircles.isEmpty()){
											found = false;
											for (int i=0;i<recordsCircles.size();i++) { 
												for(int j=0;j<circles.size();j++){
													if(recordsCircles.get(i).toString().contains(circles.get(j))){ 
														JSONObject resObj = (JSONObject) parser.parse(obj.toString());
														ObjectId recId = (ObjectId) obj.get("_id");
														DBCursor spa = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("recordID", recId.toString()));
														JSONArray spas = new JSONArray();
														if(spa!=null && spa.hasNext()){
															while(spa.hasNext()){
																DBObject spaObj = (DBObject) spa.next();
																String s = (String) spaObj.get("spaceID");
																if(s!=null && s.length()>0){
																	BasicDBList indSpaList = new BasicDBList();
																	indSpaList.add(new ObjectId(s));
																	DBCursor indSpa = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", indSpaList)));
																	if(indSpa!=null && indSpa.hasNext()){
																		if(((String)((DBObject) indSpa.next()).get("userID")).equals(manager.getUserID(credentials))){
																			spas.add(s);															
																		}
																	}
																}
															}
														}													
														resObj.put("spaces", spas);
														resArray.add(resObj);
														foundItemsCount++;
														found = true;
														break;
													}
												}
												if(found){break;}
											} 
										} 
									}
								} else {
									JSONObject resObj = (JSONObject) parser.parse(obj.toString());
									resArray.add(resObj);
									foundItemsCount++;
								}
							}
						} 
						if(foundItemsCount>0){
							JSONObject resObj = new JSONObject();
							resObj.put("records", resArray);
							result = resObj.toJSONString();						
						} else {
							errorMessage = "\"The database query did not return any result. Try to refrase the query!\"";
							wasError = true;
						}
					} else if(userId!=null && userId.length()>0){
						ArrayList<String> circles = manager.getCirclesIAmInOfAUser(manager.getUserID(credentials), userId);
						if(circles.size()>0){
							BasicDBList and = new BasicDBList();
							and.add(new BasicDBObject("userID", userId));
							BasicDBList profile = new BasicDBList();
							profile.add("profile");
							and.add(new BasicDBObject("app", new BasicDBObject("$not", new BasicDBObject("$in", profile))));
							if(spaceId!=null && spaceId.length()>0){
								System.out.println("RecordQuery: spaceId is set to: "+spaceId);
								BasicDBList recIDs = new BasicDBList();
								res = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("spaceID", spaceId));
								if(res!=null && res.hasNext()){
									while (res.hasNext()) {
										DBObject entry = (DBObject) res.next();
										System.out.println("RecordQuery: Found a space-record assignment "+entry.get("recordID"));
										String recId = (String) entry.get("recordID");
										if(recId!=null && recId.length()>0){
											recIDs.add(new ObjectId(recId));
											System.out.println("RecordQuery: Added "+recId+" to recIDs list");
										}
									}
								}
								and.add(new BasicDBObject("_id", new BasicDBObject("$in", recIDs)));
							}
							DBObject queryObject = new BasicDBObject("$and", and);
							res = (DBCursor) connector.query(MongoDBConnector.RECORDS_COLLECTION_NAME, queryObject);
							if(res!=null && res.hasNext()){
								JSONParser parser = new JSONParser();
								while(res.hasNext()){
									DBObject obj = res.next();
									BasicDBList recordsCircles = (BasicDBList) obj.get("circles");
									if(recordsCircles!=null && !recordsCircles.isEmpty()){
										found = false;
										for (int i=0;i<recordsCircles.size();i++) { 
											for(int j=0;j<circles.size();j++){
												if(recordsCircles.get(i).toString().contains(circles.get(j))){ 
													JSONObject resObj = (JSONObject) parser.parse(obj.toString());
													resObj.remove("circles");
													ObjectId recId = (ObjectId) obj.get("_id");
													DBCursor spa = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("recordID", recId.toString()));
													JSONArray spas = new JSONArray();
													if(spa!=null && spa.hasNext()){
														while(spa.hasNext()){
															DBObject spaObj = (DBObject) spa.next();
															String s = (String) spaObj.get("spaceID");
															if(s!=null && s.length()>0){
																BasicDBList indSpaList = new BasicDBList();
																indSpaList.add(new ObjectId(s));
																DBCursor indSpa = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", indSpaList)));
																if(indSpa!=null && indSpa.hasNext()){
																	if(((String)((DBObject) indSpa.next()).get("userID")).equals(manager.getUserID(credentials))){
																		spas.add(s);															
																	}
																}
															}
														}
													}													
													resObj.put("spaces", spas);
													resArray.add(resObj);
													foundItemsCount++;
													found = true;
													break;
												}
											}
											if(found){break;}
										} 
									} 
								}
							} 
							if(foundItemsCount>0){
								JSONObject resObj = new JSONObject();
								resObj.put("records", resArray);
								result = resObj.toJSONString();						
							} else {
								errorMessage = "\"The database request did not return any result. Did you provide a correct id? Or maybe the other user wont allow you to see something?\"";
								wasError = true;
							}
						}
					} else if(circleId!=null && circleId.length()>0){
						list.add(new ObjectId(circleId));
						res = (DBCursor) connector.query(MongoDBConnector.CIRCLES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
						if(res!=null && res.hasNext()){
							BasicDBList users = (BasicDBList) res.next().get("users");
							if(users!=null && !users.isEmpty()){
								for(int k=0;k<users.size();k++){
									ArrayList<String> circles = manager.getCirclesIAmInOfAUser(manager.getUserID(credentials), users.get(k).toString().substring(14, 38));
									if(circles.size()>0){
										BasicDBList and = new BasicDBList();
										and.add(new BasicDBObject("userID", users.get(k).toString().substring(14, 38)));
										BasicDBList profile = new BasicDBList();
										profile.add("profile");
										and.add(new BasicDBObject("app", new BasicDBObject("$not", new BasicDBObject("$in", profile))));
										if(spaceId!=null && spaceId.length()>0){
											BasicDBList recIDs = new BasicDBList();
											res = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("spaceID", spaceId));
											if(res!=null && res.hasNext()){
												while (res.hasNext()) {
													DBObject entry = (DBObject) res.next();
													String recId = (String) entry.get("recordID");
													if(recId!=null && recId.length()>0){
														recIDs.add(new ObjectId(recId));
													}
												}
											}
											and.add(new BasicDBObject("_id", new BasicDBObject("$in", recIDs)));
										}
										DBObject queryObject = new BasicDBObject("$and", and);
										res = (DBCursor) connector.query(MongoDBConnector.RECORDS_COLLECTION_NAME, queryObject);
										if(res!=null && res.hasNext()){
											JSONParser parser = new JSONParser();
											while(res.hasNext()){
												DBObject obj = res.next();
												BasicDBList recordsCircles = (BasicDBList) obj.get("circles");
												if(recordsCircles!=null && !recordsCircles.isEmpty()){
													found = false;
													for (int i=0;i<recordsCircles.size();i++) { 
														for(int j=0;j<circles.size();j++){
															if(recordsCircles.get(i).toString().contains(circles.get(j))){ 
																JSONObject resObj = (JSONObject) parser.parse(obj.toString());
																resObj.remove("circles");
																ObjectId recId = (ObjectId) obj.get("_id");
																DBCursor spa = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("recordID", recId.toString()));
																JSONArray spas = new JSONArray();
																if(spa!=null && spa.hasNext()){
																	while(spa.hasNext()){
																		DBObject spaObj = (DBObject) spa.next();
																		String s = (String) spaObj.get("spaceID");
																		if(s!=null && s.length()>0){
																			BasicDBList indSpaList = new BasicDBList();
																			indSpaList.add(new ObjectId(s));
																			DBCursor indSpa = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", indSpaList)));
																			if(indSpa!=null && indSpa.hasNext()){
																				if(((String)((DBObject) indSpa.next()).get("userID")).equals(manager.getUserID(credentials))){
																					spas.add(s);															
																				}
																			}
																		}
																	}
																}													
																resObj.put("spaces", spas);
																resArray.add(resObj);
																foundItemsCount++;
																found = true;
																break;
															}
														}
														if(found){break;}
													} 
												} 
											}
										} 
									}
								}
								if(foundItemsCount>0){
									JSONObject resObj = new JSONObject();
									resObj.put("records", resArray);
									result = resObj.toJSONString();						
								} else {
									errorMessage = "\"The database request did not return any result. Did you provide a correct id? Or maybe the other user wont allow you to see something?\"";
									wasError = true;
								}
							}
						}
						
					} else {
						errorMessage = "\"Wrong call! Please provide either a recordId, a userId, a circleId or a query string to the request.\"";
						wasError = true;
						isLoggedIn = false;
					}
				} else {
					errorMessage = "\"Either your session timed out or you forgot to send me the session and credentials.\"";
					wasError = true;
					isLoggedIn = false;
				}
			} catch (IllegalQueryException e) {
				errorMessage = "\"There was an error. Did you provide the correct username and password? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"We lost connection to the DB. Please try again later. Sorry for that.\"";
				wasError = true;
			} catch (ParseException e) {
				errorMessage = "\"There was an error. Did you provide the correct username and password? Error: "+e.getMessage()+"\"";
				wasError = true;
			}
		}
		
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError) {
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Here are the results.\", \"values\" : "+result+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Gave user query answer to user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Here are the results.\", \"values\" : "+result+" }");
				if(MongoDBConnector.DEBUG){System.out.println("Gave user query answer to user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} 
	}

}
