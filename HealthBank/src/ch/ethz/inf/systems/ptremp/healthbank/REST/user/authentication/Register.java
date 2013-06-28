package ch.ethz.inf.systems.ptremp.healthbank.REST.user.authentication;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.HashMap;

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
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSInputFile;

import ch.ethz.inf.systems.ptremp.healthbank.db.MongoDBConnector;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.IllegalQueryException;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.NotConnectedException;
import ch.ethz.inf.systems.ptremp.healthbank.logic.CoreManager;

/**
 * Servlet implementation class Register
 * In this servlet we implement the functionality to register a new user via a POST request.
 * 
 * @author Patrick Tremp
 */
@WebServlet(
		description = "Register to the system as a user.", 
		urlPatterns = { 
				"/Register", 
				"/register",
				"/reg"
		})
public class Register extends HttpServlet {

	private static final long serialVersionUID = 2356378203795161460L;
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
    public Register() {
        super();
        connector = MongoDBConnector.getInstance();
		manager = new CoreManager();
    }

	/**
	 * The GET request is not defined for this servlet. It will send the caller a message, that the POST request has to be used instead
	 * 
	 * For a successful call the following parameters need to be present in the URL:
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		
		String callback = request.getParameter("callback");
		
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"error\": \"Please use a Post request for the purpose of registration.\" } );");
			if(MongoDBConnector.DEBUG){System.out.println("Someone accessed doGet of Logout");}
		} else {
			response.getOutputStream().println("{ \"result\": \"failed\", \"error\": \"Please use a Post request for the purpose of registration.\" }");
			if(MongoDBConnector.DEBUG){System.out.println("Someone accessed doGet of Logout");}
		} 
	}

	/**
	 * The POST request allows the caller to register a new user in the system. 
	 * 
	 * For a successful call the following parameters need to be present in the URL. At the moment there are two different
	 * possibilities for this POST call. One for 'normal' users and one for institutes. 
	 * Users:
	 * - pw: This is the chosen password for the user in a hashed form for security. (So far Base64, which is not that secure...)
	 * - username: This is the chosen user name. If the name already exists, an error message is returned to the caller
	 * - firstname: The first name of the user
	 * - lastname: The last name of the user
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Institutes:
	 * - pw: This is the chosen password for the user in a hashed form for security. (So far Base64, which is not that secure...)
	 * - username: This is the chosen user name. If the name already exists, an error message is returned to the caller
	 * - companyname: The name of the company
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 * 
	 * TODO: Once the companies / institute have been better defined. We need to redefine this method to correctly deal with it.
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		boolean wasError = false;
		
		// check parameters
		String callback = request.getParameter("callback");
		String pw = request.getParameter("pw");
		String username = request.getParameter("username");
		String firstname = request.getParameter("firstname");
		String lastname = request.getParameter("lastname");
		String companyname = request.getParameter("companyname");
		if(pw==null || pw.length()<6 || username==null || username.length()<4){
			errorMessage = "\"Please provide the parameters 'pw' and 'username' with the request. " +
					"A password has to be longer then 6 characters and a username longer then four.\"";
			wasError = true;
		}
		if(firstname==null || firstname.length()<2 || lastname==null || lastname.length()<2){
			if(companyname==null || companyname.length()<2){
				errorMessage = "\"The firstname and lastname or companyname have to be longer then 1 character.\"";
				wasError = true;
			}
		}
		if(companyname!=null && (firstname!=null || lastname!=null)){
			errorMessage = "\"You can only either define a company with a company name or a user with a first and last name.\"";
			wasError = true;
		}
		pw = URLDecoder.decode(pw, "UTF-8");
		username = URLDecoder.decode(username, "UTF-8");
		firstname = (firstname!=null)?URLDecoder.decode(firstname, "UTF-8"):null;
		lastname = (lastname!=null)?URLDecoder.decode(lastname, "UTF-8"):null;
		companyname = (companyname!=null)?URLDecoder.decode(companyname, "UTF-8"):null;
		String credentials = StringUtils.newStringUtf8(Base64.decodeBase64(pw));
		if(!credentials.contains(":") || !username.equals(credentials.substring(0, credentials.indexOf(":")))){
			errorMessage = "\"Username and password do not fit.\"";
			wasError = true;
		}
		if(!wasError){
			pw = credentials.substring(credentials.indexOf(":")+1);
			
			// check DB connection
			if(connector==null || !connector.isConnected()){
				manager.reconnect();
			}
			
			//check if a user with this name already exists
			DBCursor res = null;
			try {
				BasicDBList list = new BasicDBList();
				list.add(username);
				res = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("username", new BasicDBObject("$in", list)));
				if(res != null && res.count()>0){
					errorMessage = "\"A user or institute with this name already exists. Please chose another username.";
					wasError = true;
				}
				if(!wasError){
					// register new user in DB
					HashMap<Object, Object> data = new HashMap<Object, Object>();
					data.put("username", username);
					String md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(pw); // password is saved encrypted.
					data.put("password", md5);
					if(companyname==null){
						data.put("firstname", firstname);
						data.put("lastname", lastname);
						data.put("type", "user");
					} else {
						data.put("companyname", companyname);
						data.put("type", "institute");
					}
					
					/** USER ICON **/
					String newFileName = "defaultUserIcon";
					File imageFile = new File(MongoDBConnector.IPATH+"defaultUserIcon.png");
					GridFS gfsPhoto = new GridFS( connector.getRootDatabase(), MongoDBConnector.USER_COLLECTION_NAME);
					GridFSInputFile gfsFile = gfsPhoto.createFile(imageFile);
					gfsFile.setFilename(newFileName);
					gfsFile.save();
	                data.put("userIcon", newFileName);
	                /**END USER ICON **/
					
					ObjectId userid = (ObjectId) connector.insert(MongoDBConnector.USER_COLLECTION_NAME, data);
					// Finally add the default circles and spaces to the user
					manager.createCoreCircles(userid.toString());
					manager.createCoreSpaces(userid.toString());
				}
			} catch (IllegalQueryException e) {
				errorMessage = "\"There was an error with the provided query. Please call an administrator. "+e.getMessage();
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"There was an error. No connection to DB server. Please call an administrator. "+e.getMessage();
				wasError = true;
			}
		}
		
		// finally send back a response
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError){
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"message\": \"registered new user: "+username+". Please log in to continue.\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Registered new user: "+username);}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"error\": "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} else {
			if(!wasError){
				response.getOutputStream().println("{ \"result\": \"success\", \"message\": \"registered new user: "+username+". Please log in to continue.\" }");
				if(MongoDBConnector.DEBUG){System.out.println("Registered new user: "+username);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"error\": "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		}
	}

}
