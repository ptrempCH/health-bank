package ch.ethz.inf.systems.ptremp.healthbank.REST.application;

import java.io.IOException;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.bson.types.ObjectId;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import ch.ethz.inf.systems.ptremp.healthbank.db.MongoDBConnector;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.IllegalQueryException;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.NotConnectedException;
import ch.ethz.inf.systems.ptremp.healthbank.logic.CoreManager;

/**
* Servlet implementation class AppInstall
* In this servlet we implement the functionality to install and uninstall applications and visualizations
* 
* @author Patrick Tremp
*/
@WebServlet(
		description = "Install and uninstall applications and visualizations. Only doPost is supported.", 
		urlPatterns = { 
				"/AppInstall", 
				"/appinstall", 
				"/appInstall"
		})
public class AppInstall extends HttpServlet {
	
	private static final long serialVersionUID = -1576909966241935541L;

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
     * Default constructor
     * @see HttpServlet#HttpServlet()
     */
    public AppInstall() {
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
	 * The POST request allows the caller to install and uninstall applications or visualizations for the currently logged in user. If the application 
	 * or visualization is already registered with the user, it will be uninstalled. Else we install and register it with the user. 
	 * So far we only remember which application/visualization the user actually wanted to install. There is no more functionality yet. Likewise when the 
	 * application/visualization gets uninstalled, we only delete the reference on the user site. All records that were added by the application/visualization 
	 * will remain in the database and continue to be visible for the user and the people allowed to see it via circles. 
	 * 
	 * For a successful call the following parameters need to be present in the URL:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - id: The id of the application or visualization the user wants to install or uninstall to or from his account.
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		String installed = "Installed";
		boolean wasError = false;
		boolean isLoggedIn = true;
		
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		String id = request.getParameter("id");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"AppInstall: Please provide the parameters 'credentials' and 'session' with the request.\"";
			wasError = true;
		}
		
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			if(id!=null && id.length()>0){id = URLDecoder.decode(id, "UTF-8");}
			
			// check DB connection
			if(connector==null || !connector.isConnected()){
				manager.reconnect();
			}
			
			// check if user is logged in
			try {
				if(manager.isUserLoggedIn(session, credentials)){
					HashMap<Object, Object> data = new HashMap<Object, Object>();
					BasicDBList list = new BasicDBList();
					DBCursor res;
					if(id!=null && id.length()>0){
						list.add(new ObjectId(id));
						res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
						if(res!=null && res.hasNext()){
							BasicDBObject obj = (BasicDBObject) res.next();
//							String type = (String) obj.get("type");
							int nrOfInstalls = -1;
							try{
								nrOfInstalls = obj.getInt("nrOfInstalls");
							} catch (ClassCastException e) {
							  // ignore since nrOfInstalls remains -1
								System.out.println("AppInstall: There was a ClassCastException: "+e.getMessage());
							}
						
							list = new BasicDBList();
//							list.add(new ObjectId(manager.getUserID(credentials)));
							list.add(manager.getUserID(credentials));
//							BasicDBObject queryObject = new BasicDBObject("_id", new BasicDBObject("$in", list));
//							res = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, queryObject);
							BasicDBObject q1 = new BasicDBObject("userID", new BasicDBObject("$in", list));
							BasicDBObject q2 = new BasicDBObject("appID", id);
							ArrayList<BasicDBObject> myList = new ArrayList<BasicDBObject>();
							myList.add(q1);
							myList.add(q2);
							BasicDBObject and = new BasicDBObject("$and", myList);
							res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, and);
							if(res!=null && res.hasNext()){
								System.out.println("AppInstall doPost: res is not null or res has next");
//								BasicDBObject user = (BasicDBObject) res.next();
//								BasicDBList usersApps = (BasicDBList) user.get("application");
//								boolean found = false;
//								if(usersApps!=null){
//									for(Object o : usersApps){
//										if(((BasicDBObject) o).toString().contains(id)){found=true;}
//									}
//								}
//								if(found){ // uninstall
//									if(type.contains("app")){
//										connector.update(MongoDBConnector.USER_COLLECTION_NAME, queryObject, new BasicDBObject("$pull", new BasicDBObject("application", new BasicDBObject("app", id))));
//									} else if(type.contains("viz")){
//										connector.update(MongoDBConnector.USER_COLLECTION_NAME, queryObject, new BasicDBObject("$pull", new BasicDBObject("application", new BasicDBObject("viz", id))));
//									}		
									installed = "Uninstalled";
									
									
									connector.delete(MongoDBConnector.APPLICATION_COLLECTION_NAME, and);
									if(nrOfInstalls>0){nrOfInstalls--;}
								} else {  // install

									System.out.println("AppInstall doPost: res is null or res has no next");
//									if(type.contains("app")){
//										connector.update(MongoDBConnector.USER_COLLECTION_NAME, queryObject, new BasicDBObject("$push", new BasicDBObject("application", new BasicDBObject("app", id))));
//									} else if(type.contains("viz")){
//										connector.update(MongoDBConnector.USER_COLLECTION_NAME, queryObject, new BasicDBObject("$push", new BasicDBObject("application", new BasicDBObject("viz", id))));
//									}
									
									data.put("userID", manager.getUserID(credentials));
									data.put("appID", id);
									DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
									data.put("timedate", dateFormat.format(new Date()));
									connector.insert(MongoDBConnector.APPLICATION_COLLECTION_NAME, data);
									
									if(nrOfInstalls!=-1){nrOfInstalls++;}
								}
								if(nrOfInstalls!=-1){
									list.add(new ObjectId(id));
									connector.update(MongoDBConnector.APPLICATION_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$set", new BasicDBObject("nrOfInstalls", nrOfInstalls)));
								}
//							}
						} else {
							errorMessage = "\"AppInstall: We are sorry, but there is no application with this id to be found in the database.\"";
							wasError = true;
						}
					} else {
						errorMessage = "\"AppInstall: Please provide the parameter 'id' to decide which application/visualization you want to install or deinstall.\"";
						wasError = true;
					}
				} else {
					errorMessage = "\"AppQuery: Either your session timed out or you forgot to send me the session and credentials.\"";
					wasError = true;
					isLoggedIn = false;
				}
			} catch (IllegalQueryException e) {
				errorMessage = "\"AppInstall: There was an error. Did you provide the correct username and password? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"AppInstall: We lost connection to the DB. Please try again later. Sorry for that.\"";
				wasError = true;
			} 
		}
		
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError) {
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Installation successfull.\" }");
				if(MongoDBConnector.DEBUG){System.out.println(installed+" application to user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Installation successfull.\" }");
				if(MongoDBConnector.DEBUG){System.out.println(installed+" application to user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} 
	}

}
