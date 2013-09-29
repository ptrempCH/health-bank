package ch.ethz.inf.systems.ptremp.healthbank.REST.query;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
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
import com.mongodb.CommandResult;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

import ch.ethz.inf.systems.ptremp.healthbank.db.MongoDBConnector;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.IllegalQueryException;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.NotConnectedException;
import ch.ethz.inf.systems.ptremp.healthbank.logic.CoreManager;

/**
* Servlet implementation class QueryEngine
* In this servlet we implement the functionality to query users and record entries.
* 
* @author Patrick Tremp
*/
@WebServlet(
		description = "Ask queries to find people and records for research purposes", 
		urlPatterns = { 
				"/QueryEngine", 
				"/queryengine", 
				"/qe"
		})
public class QueryEngine extends HttpServlet {
	private static final long serialVersionUID = 4518764967470554929L;

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
     * @see HttpServlet#HttpServlet()
     */
    public QueryEngine() {
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
	 * The GET request allows the caller to ask various kinds of queries for users as well as for record entries.
	 * This service is only usable by institute users and returns only data of users that actively allowed to inspect their data for research purposes.
	 * There are three possible ways on how to call this service. The type attribute allows to differ these cases. 
	 * 
	 * For a successful call the following parameters need to be present in the URL:
	 * Query for users (at least one optional field has to be set):
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - type: Has to be set to 'user'!
	 * - minAge: (optional)The minimum age the user shall have
	 * - maxAge: (optional)The maximum age the user shall have
	 * - minWeight: (optional)The minimum weight the user shall have
	 * - maxWeight: (optional)The maximum weight the user shall have
	 * - minHeight: (optional)The minimum height the user shall have
	 * - maxHeight: (optional)The maximum height the user shall have
	 * - country: (optional)The country where the user is from
	 * - keywords: (optional)A list of keywords to be found in the users records. keywords shall be separated by a space
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Query for records:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - type: Has to be set to 'record'!
	 * - keywords: A list of keywords to be found in the users records. keywords shall be separated by a space
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Query with a text search:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - type: Has to be set to 'text'!
	 * - query: The query to be asked
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * The information is stored in the 'values' attribute of the resulting JSON string.
	 * 
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
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
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"QueryEngine doGet: Please provide the parameters 'credentials' and 'session' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			
			// check DB connection
			if(!connector.isConnected()){
				manager.reconnect();
			}
			
			// check if user is logged in and get the records 
			try {
				if(!manager.isUserLoggedIn(session, credentials)){
					errorMessage = "\"QueryEngine doGet: You need to be logged in to use this service\"";
					wasError = true;
					isLoggedIn = false;
				} else {
					BasicDBList list = new BasicDBList();
					list.add(credentials.substring(0, credentials.lastIndexOf(':')));
					DBCursor res = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("username", new BasicDBObject("$in", list)));
					if(res==null || !res.hasNext()){
						errorMessage = "\"QueryEngine doGet: No user found with these credentials. Did you provide the correct sessionKey and credentials?\"";
						wasError = true;
					}
					if(!wasError){
						DBObject user = res.next();
						if(user.get("type").equals("institute") || user.get("type").equals("admin")){
							String type = request.getParameter("type");
							if(type==null || type.length()<2){
								errorMessage = "\"QueryEngine doGet: Please provide the parameter 'type' with value 'user', 'record' or 'text' depending if you want to query users, records, or a text search.\"";
								wasError = true;
							} else {
								switch (type) {
								case "user":
									values = queryUsers(request);
									break;
								case "record":
									values = queryRecords(request);
									break;
								case "text":
									values = queryText(request);
									break;
								default:
									errorMessage = "\"QueryEngine doGet: Please provide the parameter 'type' with value 'user', 'record' or 'mixed' depending if you want to query users, records, or a mix of both.\"";
									wasError = true;
									break;
								}
								if(values==null){
									errorMessage = "\"QueryEngine doGet: Either we could not find any results to your query or there was an internal error. Sorry for that.\"";
									wasError = true;
								}
							}
						} else {
							errorMessage = "\"QueryEngine doGet: You need to be logged in as an institute in order to save an app.\"";
							wasError = true;
						}
					}
				}
			} catch (IllegalArgumentException e) {
				errorMessage = "\"QueryEngine doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (IllegalQueryException e) {
				errorMessage = "\"QueryEngine doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"QueryEngine doGet: There was a connection error to the database. Please try again later.\"";
				wasError = true;
			} catch (ParseException e) {
				errorMessage = "\"QueryEngine doGet: There was an error. Did you provide the correct username and password? Error: "+e.getMessage()+"\"";
				wasError = true;
			}
		}
		
		if(values==null || values.length()==0){
			values = "\"\"";
		}
		// Finally give the user feedback
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError){
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"values\" : "+values+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Returned query result for the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"values\" : "+values+" }");
				if(MongoDBConnector.DEBUG){System.out.println("Returned query result for the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} 
	}

	/**
	 * Handles queries which shall use the MongoDB text search
	 * 
	 * @param request The request object from the call
	 * @return A string containing the JSON values to return
	 * @throws IllegalArgumentException
	 * @throws IllegalQueryException
	 * @throws NotConnectedException
	 * @throws UnsupportedEncodingException
	 * @throws ParseException
	 */
	@SuppressWarnings("unchecked")
	private String queryText(HttpServletRequest request) throws IllegalArgumentException, IllegalQueryException, NotConnectedException, UnsupportedEncodingException, ParseException{
		String query = null;
		JSONArray finalResult = new JSONArray();
		if((query = request.getParameter("query"))!=null){ 
			query = URLDecoder.decode(query, "UTF-8");
			
			final DBObject textSearchCommand = new BasicDBObject();
	        textSearchCommand.put("text", MongoDBConnector.RECORDS_COLLECTION_NAME);
	        textSearchCommand.put("search", query);
	        final CommandResult commandResult = connector.command(textSearchCommand);
			
	        JSONParser parser = new JSONParser();
	        JSONObject resObj = (JSONObject) parser.parse(commandResult.toString());
			if(resObj!=null){
				JSONArray resArray = new JSONArray();
				resArray = (JSONArray) resObj.get("results");
				JSONObject tmpResult = null;
				Object tmp = null;
				ObjectId tmpOID = null; 
				BasicDBList list = new BasicDBList();
				DBCursor tmpUser;
				// Parse result
				for(int i=0;i<resArray.size();i++){
					tmp = resArray.get(i);
					if(tmp!=null && (tmp instanceof JSONObject)){
						tmpResult = (JSONObject) tmp;
						if(tmpResult.containsKey("obj")){
							tmpResult = (JSONObject) tmpResult.get("obj");
							
							// check if user allows research
							tmp =  tmpResult.get("userID"); 
							if(tmp!=null){
								if(tmp instanceof ObjectId){ tmpOID = (ObjectId) tmp; }
								else if(tmp instanceof String){ tmpOID = new ObjectId(tmp.toString()); }
							}
							else { continue; }
							list = new BasicDBList();
							list.add(tmpOID);
							tmpUser = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
							if(tmpUser!=null && tmpUser.hasNext()){
								tmp = tmpUser.next().get("allowResearch");
								if(tmp!=null && (tmp instanceof String) && ((String)tmp).equals("y")){
									// add record to result set
									tmpResult.remove("circles");
									finalResult.add(tmpResult);									
								} else { continue; }
							} else { continue; }
						}
					} else {
						continue;
					}
				}
			}
		}
		return finalResult.toJSONString();
	}

	/**
	 * Handles query to search for records via keywords
	 * 
	 * @param request The request object from the call
	 * @return A string containing the JSON values to return
	 * @throws IllegalArgumentException
	 * @throws IllegalQueryException
	 * @throws NotConnectedException
	 * @throws UnsupportedEncodingException
	 * @throws ParseException
	 */
	@SuppressWarnings("unchecked")
	private String queryRecords(HttpServletRequest request) throws IllegalArgumentException, IllegalQueryException, NotConnectedException, UnsupportedEncodingException, ParseException{
		String keywords = null;
		BasicDBList or = new BasicDBList(), list = new BasicDBList();
		
		// first construct the query
		if((keywords = request.getParameter("keywords"))!=null){ 
			keywords = URLDecoder.decode(keywords, "UTF-8");
			for(String s : keywords.split(" ")){
				if(s.length()<1){continue;}
				if(s.equals("*")){
					s = "\\w*";
				}
				Pattern regex = Pattern.compile("\\w*"+s+"\\w*");
				list.add(regex);
				regex = Pattern.compile("\\w*"+s.toLowerCase()+"\\w*");
				list.add(regex);
				regex = Pattern.compile("\\w*"+s.toUpperCase()+"\\w*");
				list.add(regex);
				regex = Pattern.compile("\\w*"+s.replace(s.charAt(0), s.substring(0, 1).toUpperCase().charAt(0))+"\\w*");
				list.add(regex);
			}
			DBObject obj;
			ArrayList<String> keys = getKeyWords();
			if(keys!=null){
				for(String s : keys){
					obj = new BasicDBObject(s, new BasicDBObject("$in", list));
					or.add(obj);
				}
				DBCursor res = (DBCursor) connector.query(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("$or", or));
				if(res!=null && res.hasNext()){
					// if we have results, check if the corresponding user has allowed to share with research and if so, add the record to the result set
					JSONArray resArray = new JSONArray();
					JSONParser parser = new JSONParser();
					ObjectId tmpOID = null; 
					Object tmpO;
					DBCursor tmpUser;
					JSONObject resObj;
					while(res.hasNext()){
						obj = res.next();
						// search for the user
						tmpO =  obj.get("userID"); 
						if(tmpO!=null){
							if(tmpO instanceof ObjectId){ tmpOID = (ObjectId) tmpO; }
							else if(tmpO instanceof String){ tmpOID = new ObjectId(tmpO.toString()); }
						}
						else { continue; }
						System.out.println("tmpO is: "+tmpO.toString());
						list = new BasicDBList();
						list.add(tmpOID);
						tmpUser = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
						if(tmpUser!=null && tmpUser.hasNext()){
							System.out.println("found tmpUser");
							// check if allows research
							tmpO = tmpUser.next().get("allowResearch");
							if(tmpO!=null && (tmpO instanceof String)){
								if(((String)tmpO).equals("y")){
									System.out.println("tmpUser allows research");
									// add record to result set
									resObj = (JSONObject) parser.parse(obj.toString());
									if(resObj!=null){
										if(resObj.get("app").equals("profile")){continue;}
										resObj.remove("circles");
										resArray.add(resObj);
									}
								} else { continue;}
							} else { continue; }
						} else { continue; }
	
					}
					return resArray.toJSONString();
				} else {
					return null;
				}
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	/**
	 * Handles queries to search for users
	 * 
	 * @param request The request object from the call
	 * @return A string containing the JSON values to return
	 * @throws IllegalArgumentException
	 * @throws IllegalQueryException
	 * @throws NotConnectedException
	 * @throws UnsupportedEncodingException
	 * @throws ParseException
	 */
	@SuppressWarnings("unchecked")
	private String queryUsers(HttpServletRequest request) throws IllegalArgumentException, IllegalQueryException, NotConnectedException, UnsupportedEncodingException, ParseException{
		// first get all the parameters and search for users, that might fit the parameters without having a look at the records
		String result = "";
		String minAge, maxAge, minWeight, maxWeight, minHeight, maxHeight, country, keywords;
		BasicDBList and = new BasicDBList();
		if((minAge = request.getParameter("minAge"))!=null && minAge.length()>0){ minAge = URLDecoder.decode(minAge, "UTF-8"); }
		if((maxAge = request.getParameter("maxAge"))!=null && maxAge.length()>0){ maxAge = URLDecoder.decode(maxAge, "UTF-8"); }
		if((minWeight = request.getParameter("minWeight"))!=null && minWeight.length()>0){ 
			minWeight = URLDecoder.decode(minWeight, "UTF-8");
			and.add(new BasicDBObject("weight", new BasicDBObject("$gte", minWeight)));
		}
		if((maxWeight = request.getParameter("maxWeight"))!=null && maxWeight.length()>0){ 
			maxWeight = URLDecoder.decode(maxWeight, "UTF-8");
			and.add(new BasicDBObject("weight", new BasicDBObject("$lte", maxWeight)));
		}
		if((minHeight = request.getParameter("minHeight"))!=null && minHeight.length()>0){ 
			minHeight = URLDecoder.decode(minHeight, "UTF-8");
			and.add(new BasicDBObject("height", new BasicDBObject("$gte", minHeight)));
		}
		if((maxHeight = request.getParameter("maxHeight"))!=null && maxHeight.length()>0){ 
			maxHeight = URLDecoder.decode(maxHeight, "UTF-8");
			and.add(new BasicDBObject("height", new BasicDBObject("$lte", maxHeight)));
		}
		if((country = request.getParameter("country"))!=null && country.length()>0){ 
			country = URLDecoder.decode(country, "UTF-8");
			and.add(new BasicDBObject("country", country));
		}

		
		DBCursor res;
		if(and.isEmpty()){
			res = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, null);
		} else {
			res = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("$and", and));
		}
		if(res==null || !res.hasNext()){
			return result; // nothing found
		} else {
			// now check for every user, if he/she allowed research access
			BasicDBList or = new BasicDBList(), list = new BasicDBList();
			// if the query contains symptoms, prepare for all users
			if((keywords = request.getParameter("keywords"))!=null && keywords.length()>0){ 
				keywords = URLDecoder.decode(keywords, "UTF-8");
				for(String s : keywords.split(" ")){
					if(s.length()<1){continue;}
					if(s.equals("*")){
						s = "\\w*";
					}
					Pattern regex = Pattern.compile("\\w*"+s+"\\w*");
					list.add(regex);
					regex = Pattern.compile("\\w*"+s.toLowerCase()+"\\w*");
					list.add(regex);
					regex = Pattern.compile("\\w*"+s.toUpperCase()+"\\w*");
					list.add(regex);
					regex = Pattern.compile("\\w*"+s.replace(s.charAt(0), s.substring(0, 1).toUpperCase().charAt(0))+"\\w*");
					list.add(regex);
				}
				DBObject obj;
				ArrayList<String> keys = getKeyWords();
				if(keys!=null){
					for(String s : keys){
						obj = new BasicDBObject(s, new BasicDBObject("$in", list));
						or.add(obj);
					}
				}
			}
			JSONArray resArray = new JSONArray();
			while (res.hasNext()) {
				DBObject user = res.next();
				Object allowResearch = user.get("allowResearch");
				if(allowResearch!=null && (allowResearch instanceof String) && ((String)allowResearch).equals("y")){
					if((minAge!=null && minAge.length()>0) || (maxAge!=null && maxAge.length()>0)){
						if(user.containsField("birthday") && (user.get("birthday") instanceof String)){ 
							int age = getAge((String) user.get("birthday"));
							try{
								if((minAge!=null && minAge.length()>0) && Integer.parseInt(minAge)>age){ continue; } // user is too young
								if((maxAge!=null && maxAge.length()>0) && Integer.parseInt(maxAge)<age){ continue; } // user is too old
							} catch (NumberFormatException e){
								continue;
							}
						} else {
							continue;
						}
					}	
					// If the query contains symptoms, check if the found user has any records that match this
					boolean recordsFound = false;
					if(keywords!=null && keywords.length()>0){
						ObjectId id = (ObjectId) user.get("_id");
						and = new BasicDBList();
						and.add(new BasicDBObject("userID", id.toString()));
						and.add(new BasicDBObject("$or", or));
						DBObject queryObject = new BasicDBObject("$and", and);
						DBCursor tmpRes = (DBCursor) connector.query(MongoDBConnector.RECORDS_COLLECTION_NAME, queryObject);
						if(tmpRes!=null && tmpRes.hasNext()){
							recordsFound = true;
						}
					}
					
					// return only first, last and user name, as well as _id and icon. So the institute is able to contact the person
					if(keywords == null || keywords.length()==0 || ((keywords != null && keywords.length()>0) && recordsFound)){
						JSONParser parser = new JSONParser();
						JSONObject userObj = (JSONObject) parser.parse(user.toString());
						JSONObject resObj = new JSONObject();
						resObj.put("username", userObj.get("username"));
						resObj.put("firstname", userObj.get("firstname"));
						resObj.put("lastname", userObj.get("lastname"));
						resObj.put("icon", userObj.get("userIcon"));
						resObj.put("_id", userObj.get("_id"));
						
						resArray.add(resObj);
					}
				}
			}
			
			result = resArray.toJSONString();
		}
		

		
		
		return result;
	}

	/**
	 * Calculates the age of someone from s {@link String} value
	 * @param string A {@link String} with format dd-MM-yyyy representing a birthday
	 * @return The age of that person
	 */
	private int getAge(String string) {
		Date date1, date2;
        Calendar cal1 = new GregorianCalendar(), cal2 = new GregorianCalendar();
        int factor = 0, age = 0;
		try {
			date1 = new SimpleDateFormat("dd-MM-yyyy").parse(string);
			date2 = new Date();
	        cal1.setTime(date1);
	        cal2.setTime(date2);
	        if(cal2.get(Calendar.DAY_OF_YEAR) < cal1.get(Calendar.DAY_OF_YEAR)) {
	              factor = -1; 
	        }
	        age = cal2.get(Calendar.YEAR) - cal1.get(Calendar.YEAR) + factor;
			return age;
		} catch (java.text.ParseException e) {
			return -1;
		}
        
	}

	/**
	 * Gets the keywords of the applications saved in the application collection
	 * @return An {@link ArrayList} with the keys as values
	 * @throws IllegalQueryException
	 * @throws NotConnectedException
	 */
	private ArrayList<String> getKeyWords() throws IllegalQueryException, NotConnectedException {
		ArrayList<String> result = new ArrayList<String>();
		DBCursor res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, new BasicDBObject("type", "register"));
		if(res!=null && res.hasNext()){
			DBObject entry = res.next();
			Object list = entry.get("keywords");
			if(list!=null && (list instanceof BasicDBList)){
				BasicDBList keys = (BasicDBList) list;
				BasicDBObject key; 
				for(int i=0;i<keys.size();i++){
					if(keys.get(i) instanceof BasicDBObject){
						key = (BasicDBObject) keys.get(i);
						result.add(key.getString("key"));
					}
				}
				return result;
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

}
