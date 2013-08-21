package ch.ethz.inf.systems.ptremp.healthbank.REST.application;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.servlet.Servlet;
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
import org.apache.commons.io.IOUtils;
import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import ch.ethz.inf.systems.ptremp.healthbank.db.MongoDBConnector;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.IllegalQueryException;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.NotConnectedException;
import ch.ethz.inf.systems.ptremp.healthbank.logic.CoreManager;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSInputFile;

/**
* Servlet implementation class Application
* In this servlet we implement the functionality to register, edit and query applications and visualizations
* 
* @author Patrick Tremp
*/
@WebServlet(
		description = "This servlet deals with application. Registering them on the server and querying on them.", 
		urlPatterns = { 
				"/Application", 
				"/application", 
				"/app"
		})
public class Application extends HttpServlet {

	private static final long serialVersionUID = -1301352855066833140L;

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
     * Default Constructor
     * @see HttpServlet#HttpServlet()
     */
    public Application() {
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
	 * With this GET request the caller can query all the applications in the system if it is a user
	 * and all it uploaded personally if it is an institute user.
	 * to the HealthBank system so far. For other queries to the application collection, please use the {@link AppQuery} servlet.
	 * 
	 * For a successful call the following parameters need to be present in the URL:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	@SuppressWarnings("unchecked")
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		String result = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		
		// check parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Application doGet: Please provide the parameters 'credentials' and 'session' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			
			// check DB connection
			if(connector==null || !connector.isConnected()){
				manager.reconnect();
			}
			
			// check if user is logged in
			try {
				if(manager.isUserLoggedIn(session, credentials)){
					BasicDBList list = new BasicDBList();
					list.add(new ObjectId(manager.getUserID(credentials)));
					DBCursor myUser = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
					list = new BasicDBList();
					list.add(manager.getUserID(credentials));
					if(myUser!=null && myUser.hasNext()){
						DBObject userObj = myUser.next();
						String type = (String) userObj.get("type");
						if(type.equals("institute")){
							DBCursor res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, new BasicDBObject("companyID", new BasicDBObject("$in", list)));
							if(res!=null && res.hasNext()){
								JSONParser parser = new JSONParser();
								JSONArray resArray = new JSONArray();
								while (res.hasNext()) {
									DBObject obj = (DBObject) res.next();
									JSONObject resObj = (JSONObject) parser.parse(obj.toString());
									resArray.add(resObj);
								}

								JSONObject resultArray = new JSONObject();
								resultArray.put("applications", resArray);
								result = resultArray.toJSONString();
							} else {
								errorMessage = "\"Application doGet: We could not find any application. Have you already provided one?\"";
								wasError = true;
							}
						} else { // user
							DBCursor res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, null);
							if(res!=null && res.hasNext()){
								JSONParser parser = new JSONParser();
								JSONArray resArray = new JSONArray();
								while (res.hasNext()) {
									DBObject obj = (DBObject) res.next();
									JSONObject resObj = (JSONObject) parser.parse(obj.toString());
									String curOnline = (String) resObj.get("online");
									String isFor = (String) resObj.get("isFor");
									resObj.remove("secret");
									if(curOnline!=null && curOnline.equals("online") && (isFor==null || !isFor.equals("institutes"))){
										resArray.add(resObj);
									}
								}

								JSONObject resultArray = new JSONObject();
								resultArray.put("applications", resArray);
								result = resultArray.toJSONString();
							} else {
								errorMessage = "\"Application doGet: We could not find any application. It seems like there are none in the system yet...\"";
								wasError = true;
							}
						}
					}
					
				} else {
					errorMessage = "\"AppQuery: Either your session timed out or you forgot to send me the session and credentials.\"";
					wasError = true;
					isLoggedIn = false;
				}
			} catch (IllegalQueryException e) {
				errorMessage = "\"Application doGet: There was an error. Did you provide the correct username and password? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"Application doGet: We lost connection to the DB. Please try again later. Sorry for that.\"";
				wasError = true;
			} catch (ParseException e) {
				errorMessage = "\"Application doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			}
		}
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError) {
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Here are the results.\", \"values\" : "+result+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Gave application query answer to user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Here are the results.\", \"values\" : "+result+" }");
				if(MongoDBConnector.DEBUG){System.out.println("Gave application query answer to user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} 
	}

	/**
	 * With this POST request the caller (provided it is a institute user), may add new applications or visualizations to the HealthBank system.
	 * The more it shall be possible to edit existing applications/visualizations. For these two operations the caller shall use multipart content from
	 * a form.
	 * The more it shall be possible to update the app secret via a 'normal' POST request, without multipart content.
	 * 
	 * For a successful call the following parameters need to be present in the form:
	 * Add new item:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - icon: The icon for the new application/visualization (an actual image, png, jpeg, gif, pjpeg, svg and tiff are supported )
	 * - file: The HTML file for the application/visualization (an .html file)
	 * - css: (optional) A CSS file to use for the application/visualization (a .css file)
	 * - js: (optional) A JavaScript file to use for the application/visualization ( a .js file)
	 * - name: The name of the application/visualization
	 * - descr: A description for the application/visualization
	 * - type: The type of the new application/visualization. Can be either 'app' for an application or 'viz' for a visualization
	 * - version: A string indication the version number of the application
	 * - online: Can be either 'true' or 'false'. Depending on this value we show the application to our users or it is only visible to the author of the app
	 * - index: (only for applications) In order to make searching on the tremendous amount of data more effective, the author of an application has to give us at 
	 * 			least one and up to five keywords that are actually used for the stored records. This will be used by our index mechanism to make access easier.
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Edit existing item:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - id: The if of the application/visualization to edit
	 * - icon: The icon for the new application/visualization (an actual image, png, jpeg, gif, pjpeg, svg and tiff are supported )
	 * - file: The HTML file for the application/visualization (an .html file)
	 * - css: (optional) A CSS file to use for the application/visualization (a .css file)
	 * - js: (optional) A JavaScript file to use for the application/visualization ( a .js file)
	 * - name: The name of the application/visualization
	 * - descr: A description for the application/visualization
	 * - whatsNew: A message on what has changed between the old and new version
	 * - version: A string indication the version number of the application
	 * - online: Can be either 'true' or 'false'. Depending on this value we show the application to our users or it is only visible to the author of the app
	 * - index: (only for applications) In order to make searching on the tremendous amount of data more effective, the author of an application has to give us at 
	 * 			least one and up to five keywords that are actually used for the stored records. This will be used by our index mechanism to make access easier.
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Get a new app secret:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - id: The if of the application/visualization to edit
	 * - secret: The existing secret for the application
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		String session = "", credentials = "", name = "", descr = "", whatIsNew = "", version = "", type = "", online = "", icon = "", htmlFileName = "", cssFileName = "", jsFileName = "", id = "", isFor = "";
		ArrayList<String> indexKeywords = new ArrayList<String>();
        File htmlFile = null, cssFile = null, jsFile = null;
        InputStream htmlFileIS = null, cssFileIS = null, jsFileIS = null;
        OutputStream output;
		
		// check DB connection
		if(!connector.isConnected()){
			manager.reconnect();
		}
	
		HashMap<Object, Object> data = new HashMap<Object, Object>();
		// START MULTIPART
		if (ServletFileUpload.isMultipartContent(request)) {
			System.out.println("Application doPost: We received a multipart request");
			try {
                //Create GridFS object
				GridFS fs = new GridFS( connector.getRootDatabase(), MongoDBConnector.APPLICATION_COLLECTION_NAME);
				List<FileItem> items = new ServletFileUpload(new DiskFileItemFactory()).parseRequest(request);
		        for (FileItem item : items) {
		            if (!item.isFormField()) {
		                // Process form file field (input type="file")
		            	InputStream filecontent = item.getInputStream();
		                String filename = item.getName();
		                filename = URLDecoder.decode(filename, "UTF-8");
		                switch (item.getFieldName()) {
							case "icon":
								if(item.getContentType().equals("image/gif") || item.getContentType().equals("image/jpeg") || item.getContentType().equals("image/pjpeg") || item.getContentType().equals("image/png") || item.getContentType().equals("image/svg+xml") || item.getContentType().equals("image/tiff")){
									//Save image into database
					                GridFSInputFile in = fs.createFile( filecontent );
					                in.setFilename(filename);
					                in.setContentType(item.getContentType());
					                in.save();
					                icon = filename;
								}
								break;
							case "file":
								if(item.getContentType().equals("text/html")){
									htmlFileName = filename;
									htmlFileIS = item.getInputStream();
								}
								break;
							case "css":
								if(item.getContentType().equals("text/css")){
									cssFileName = filename;
									cssFileIS = item.getInputStream();
								}
								break;
							case "js":
								if(item.getContentType().equals("text/javascript") || item.getContentType().equals("application/javascript")){
									jsFileName = filename;
									jsFileIS = item.getInputStream();
								}
								break;
	
							default:
								break;
						}
		            } else {
		            	switch (item.getFieldName()) {
		            	case "id":
		            		id = item.getString();
		            		break;
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
						case "whatIsNew":
							whatIsNew = item.getString();
							break;
						case "version":
							version = item.getString();
							break;
						case "type":
							type = item.getString();
							break;
						case "isFor":
							isFor = item.getString();
						case "online":
							online = item.getString();
							break;
						case "index":
						case "index1":
						case "index2":
						case "index3":
						case "index4":
						case "index5":
							indexKeywords.add(item.getString());
							break;
						default:
							break;
						}
		            }
		        }
		        
		        credentials = URLDecoder.decode(credentials, "UTF-8");
				credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
				session = URLDecoder.decode(session, "UTF-8");
		        BasicDBList list = new BasicDBList();
				list.add(credentials.substring(0, credentials.lastIndexOf(':')));
		        
				if(!manager.isUserLoggedIn(session, credentials)){
					errorMessage = "\"Application doPost: You need to be logged in to use this service\"";
					wasError = true;
					isLoggedIn = false;
				} else {
			        // OK it seems like we could save the files successfully.
					DBCursor res = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("username", new BasicDBObject("$in", list)));
					if(res==null || !res.hasNext()){
						errorMessage = "\"Application doPost: No user found with these credentials. Did you provide the correct sessionKey and credentials?\"";
						wasError = true;
					}
					if(!wasError){
						DBObject user = res.next();
						if(user.get("type").equals("institute")){
							if(id!=null && id.length()>0){
								list = new BasicDBList();
								list.add(new ObjectId(id));
								res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
								if(res==null || !res.hasNext()){
									errorMessage = "\"Application doPost: No application found with this id. Did you provide the correct id?\"";
									wasError = true;
								}
							}
							if(!wasError){
								if(descr!=null && descr.length()>0){data.put("descr", descr);}
								if(icon!=null && icon.length()>0){data.put("icon", icon);}
								BasicDBList indexList = new BasicDBList();
								for(String s : indexKeywords){ 
									if(s.length()>0){indexList.add(new BasicDBObject("keyword", s)); }
								}
								if(type.equals("app")){data.put("index", indexList);}
								if(name!=null && name.length()>0){data.put("name", name);}
								if(online!=null && online.length()>0){data.put("online", online);}
								if(isFor!=null && isFor.length()>0){data.put("isFor", isFor);}
								DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
								data.put("timedate", dateFormat.format(new Date()));
								if(htmlFileName!=null && htmlFileName.length()>0){data.put("url", htmlFileName);}
								if(cssFileName!=null && cssFileName.length()>0){data.put("cssFile", cssFileName);}
								if(jsFileName!=null && jsFileName.length()>0){data.put("jsFile", jsFileName);}
								if(version!=null && version.length()>0){data.put("version", version);}
	
								if(id!=null && id.length()>0) { // edit
									if(whatIsNew!=null && whatIsNew.length()>0){data.put("whatIsNew", whatIsNew);}
									connector.update(MongoDBConnector.APPLICATION_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$set", data));
								} else { // new app
									data.put("secret", manager.randomString(32));
									data.put("companyID", manager.getUserID(credentials));
									data.put("companyName", user.get("companyname"));
									data.put("nrOfInstalls", 0);
									data.put("type", type);
									id = connector.insert(MongoDBConnector.APPLICATION_COLLECTION_NAME, data).toString();
								}	
								// now add the files
								if(htmlFileName!=null && htmlFileName.length()>0){
									htmlFile = new File(MongoDBConnector.APPHTML+id+"/"+(name.replaceAll("\\s+", ""))+"/"+htmlFileName);
									htmlFile.getParentFile().mkdirs();
									output = new FileOutputStream(htmlFile);
									try {
									    IOUtils.copy(htmlFileIS, output);
									    System.out.println("Stored file "+htmlFileName);
									} finally {
									    IOUtils.closeQuietly(output);
									    IOUtils.closeQuietly(htmlFileIS);
									}
				                }
								if(cssFileName!=null && cssFileName.length()>0){
									cssFile = new File(MongoDBConnector.APPHTML+id+"/"+(name.replaceAll("\\s+", ""))+"/css/"+cssFileName);
									cssFile.getParentFile().mkdirs();
									output = new FileOutputStream(cssFile);
									try {
									    IOUtils.copy(cssFileIS, output);
									    System.out.println("Stored file "+cssFileName);
									} finally {
									    IOUtils.closeQuietly(output);
									    IOUtils.closeQuietly(cssFileIS);
									}
				                }
								if(jsFileName!=null && jsFileName.length()>0){
									jsFile = new File(MongoDBConnector.APPHTML+id+"/"+(name.replaceAll("\\s+", ""))+"/js/"+jsFileName);
									jsFile.getParentFile().mkdirs();
									output = new FileOutputStream(jsFile);
									try {
									    IOUtils.copy(jsFileIS, output);
									    System.out.println("Stored file "+jsFileName);
									} finally {
									    IOUtils.closeQuietly(output);
									    IOUtils.closeQuietly(jsFileIS);
									}
				                }
							}
						} else {
							errorMessage = "\"You need to be logged in as an institute in order to save an app.\"";
							wasError = true;
						}
					}
				}
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
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Entry stored successfully\" }");
				if(MongoDBConnector.DEBUG){System.out.println("Updated application "+name+" of user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} // FINISH MULTIPART
		else { // update secret part
			String callback = request.getParameter("callback");
			credentials = request.getParameter("credentials");
			session = request.getParameter("session");
			String secret = request.getParameter("secret");
			id = request.getParameter("id");
			
			if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
				errorMessage = "\"Application: Please provide the parameters 'credentials' and 'session' with the request.\"";
				wasError = true;
			} else if(secret==null || secret.length()<2 || id==null || id.length()<2 || !ObjectId.isValid(id)){
				errorMessage = "\"Application: Please use multipart request to do anything else then to update your app secret.\"";
				wasError = true;
			}
			if(!wasError){
				credentials = URLDecoder.decode(credentials, "UTF-8");
				session = URLDecoder.decode(session, "UTF-8");
				credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
				secret = URLDecoder.decode(secret, "UTF-8");
				id = URLDecoder.decode(id, "UTF-8");
				
				// check DB connection
				if(connector==null || !connector.isConnected()){
					manager.reconnect();
				}
				
				// check if user is logged in
				try {
					if(manager.isUserLoggedIn(session, credentials)){
						BasicDBList list = new BasicDBList();
						list.add(new ObjectId(id));
						DBCursor res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
						if(res!=null && res.hasNext()){
							BasicDBObject app = (BasicDBObject) res.next();
							if(app!=null){
								String companyID = (String) app.get("companyID"); // first check that the current user is the owner of this app
								if(companyID != null && companyID.equals(manager.getUserID(credentials))){
									String savedSecret = (String) app.get("secret");
									if(savedSecret!=null && savedSecret.equals(secret)){
										connector.update(MongoDBConnector.APPLICATION_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$set", new BasicDBObject("secret", manager.randomString(32))));
									}else{
										errorMessage = "\"Application: The provided secret does not match the current app secret.\"";
										wasError = true;
									}
								} else{
									errorMessage = "\"Application: You are not the author of the application with the provided id.\"";
									wasError = true;
								}
							} else{
								errorMessage = "\"Application: The provided id is not a valid application id.\"";
								wasError = true;
							}
						} else{
							errorMessage = "\"Application: The provided id is not a valid application id.\"";
							wasError = true;
						}
						
					} else {
						errorMessage = "\"Application: Either your session timed out or you forgot to send me the session and credentials.\"";
						wasError = true;
						isLoggedIn = false;
					}
				} catch (IllegalQueryException e) {
					errorMessage = "\"Application: There was an error. Did you provide the correct parameters? Error: "+e.getMessage()+"\"";
					wasError = true;
				} catch (NotConnectedException e) {
					errorMessage = "\"Application: We lost connection to the DB. Please try again later. Sorry for that.\"";
					wasError = true;
				} 
			}
			
			if(callback!=null && callback.length()>0){
				callback = URLDecoder.decode(callback, "UTF-8");
				if(!wasError) {
					response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Changed secret successfully.\" }");
					if(MongoDBConnector.DEBUG){System.out.println("Changed secret of app with appId: "+id+"!");}
				}
				else {
					response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" }");
					if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
				}
			} else {
				if(!wasError) {
					response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Changed secret successfully.\" }");
					if(MongoDBConnector.DEBUG){System.out.println("Changed secret of app with appId: "+id+"!");}
				}
				else {
					response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" }");
					if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
				}
			} 
		}
		
	}

}
