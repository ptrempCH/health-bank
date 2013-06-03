package ch.ethz.inf.systems.ptremp.healthbank.REST.user;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
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
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
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
 * Servlet implementation class Profile
 */
@WebServlet(
		description = "Get and Set information about a user", 
		urlPatterns = { 
				"/Profile", 
				"/profile", 
				"/User", 
				"/user"
		})
public class Profile extends HttpServlet {

	private static final long serialVersionUID = -2817543681075077536L;
	private MongoDBConnector connector; 
	private CoreManager manager;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public Profile() {
        super();
        if(connector == null ) { 
        	connector = MongoDBConnector.getInstance();
        }
		if(manager == null) {
			manager = new CoreManager();
		}
    }

	/**
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
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		String userData = "\"\":\"\"";
		boolean wasError = false;
		boolean isLoggedIn = true;
		
		// check parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Please provide the parameters 'credentials' and 'session' with the request.\"";
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
					list.add(credentials.substring(0, credentials.lastIndexOf(':')));
					DBCursor res = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("username", new BasicDBObject("$in", list)));
					if(res==null || res.size()==0){
						errorMessage = "\"No user found with these credentials. Did you provide the correct sessionKey and credentials?\"";
						wasError = true;
					}
					if(!wasError){
						JSONParser parser = new JSONParser();
						DBObject obj = res.next();
						JSONObject resObj = (JSONObject) parser.parse(obj.toString());
						userData = "\"username\" : \""+resObj.get("username")+"\", \"firstname\" : \""+resObj.get("firstname")+"\", \"lastname\" : \""+resObj.get("lastname")+"\"";
						Object s = resObj.get("street");
						if(s!=null){ userData += ", \"street\" : \""+s.toString()+"\"";}
						s = resObj.get("code");
						if(s!=null){ userData += ", \"code\" : \""+s.toString()+"\"";}
						s = resObj.get("city");
						if(s!=null){ userData += ", \"city\" : \""+s.toString()+"\"";}
						s = resObj.get("country");
						if(s!=null){ userData += ", \"country\" : \""+s+"\"";}
						s = resObj.get("phoneP");
						if(s!=null){ userData += ", \"privPhone\" : \""+s.toString()+"\"";}
						s = resObj.get("phoneM");
						if(s!=null){ userData += ", \"mobPhone\" : \""+s.toString()+"\"";}
						s = resObj.get("phoneW");
						if(s!=null){ userData += ", \"workPhone\" : \""+s.toString()+"\"";}
						s = resObj.get("emailP");
						if(s!=null){ userData += ", \"privMail\" : \""+s.toString()+"\"";}
						s = resObj.get("emailW");
						if(s!=null){ userData += ", \"workMail\" : \""+s.toString()+"\"";}
						s = resObj.get("nationality");
						if(s!=null){ userData += ", \"nationality\" : \""+s.toString()+"\"";}
						s = resObj.get("birthday");
						if(s!=null){ userData += ", \"birthday\" : \""+s.toString()+"\"";}
						s = resObj.get("spouse");
						if(s!=null){ userData += ", \"spouse\" : \""+s.toString()+"\"";}
						s = resObj.get("insurance");
						if(s!=null){ userData += ", \"insurance\" : \""+s.toString()+"\"";}
						s = resObj.get("gender");
						if(s!=null){ userData += ", \"gender\" : \""+s.toString()+"\"";}
						s = resObj.get("userIcon");
						if(s!=null){ userData += ", \"userIcon\" : \""+s.toString()+"\"";}
						s = resObj.get("type");
						if(s!=null){ userData += ", \"type\" : \""+s.toString()+"\"";}
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
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Here are the user's data.\", "+userData+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Gave profile info to user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Here are the user's data.\", "+userData+" }");
				if(MongoDBConnector.DEBUG){System.out.println("Gave profile info to user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} 
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		String session = "", credentials = "";
		
		// check DB connection
		if(!connector.isConnected()){
			manager.reconnect();
		}

		HashMap<Object, Object> data = new HashMap<Object, Object>();
		// START USERICON
		if (ServletFileUpload.isMultipartContent(request)) {
			try {
				String userIcon = "";
                //Create GridFS object
				GridFS fs = new GridFS( connector.getRootDatabase(), MongoDBConnector.USER_COLLECTION_NAME);;
				List<FileItem> items = new ServletFileUpload(new DiskFileItemFactory()).parseRequest(request);
		        for (FileItem item : items) {
		            if (!item.isFormField()) {
		                // Process form file field (input type="file").
		                InputStream filecontent = item.getInputStream();
		                String filename = item.getName();
		                filename = URLDecoder.decode(filename, "UTF-8");

		                //Save image into database
		                GridFSInputFile in = fs.createFile( filecontent );
		                in.setFilename(filename);
		                in.setContentType(item.getContentType());
		                in.save();
		                userIcon = filename;
		            } else {
		            	if(item.getFieldName().equals("session")){
		            		session = item.getString();
		            	} else if(item.getFieldName().equals("credentials")){
		            		credentials = item.getString();
		            	}
		            }
		        }
		        
		        credentials = URLDecoder.decode(credentials, "UTF-8");
				credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
				session = URLDecoder.decode(session, "UTF-8");
		        BasicDBList list = new BasicDBList();
				list.add(credentials.substring(0, credentials.lastIndexOf(':')));
		        
				if(!manager.isUserLoggedIn(session, credentials)){
					errorMessage = "\"Profile doPost: You need to be logged in to use this service\"";
					wasError = true;
					isLoggedIn = false;
				} else {
			        // OK it seems like we could save the new image successfully. Hence delete the old one to reduce space consumption
					DBCursor res = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("username", new BasicDBObject("$in", list)));
					if(res==null || res.size()==0){
						errorMessage = "\"No user found with these credentials. Did you provide the correct sessionKey and credentials?\"";
						wasError = true;
					}
					if(!wasError){
						JSONParser parser = new JSONParser();
						DBObject obj = res.next();
						JSONObject resObj = (JSONObject) parser.parse(obj.toString());
						if(resObj.keySet().contains("userIcon")){
							String s = resObj.get("userIcon").toString();
							if(s!=null){ 
								fs.remove(s);
							}
						}
					}
					data.put("userIcon", userIcon);
					if(!wasError){
						connector.update(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("username", new BasicDBObject("$in", list)), new BasicDBObject("$set", data));
					}
				}
			} catch (FileUploadException | ParseException e) {
		        errorMessage = "\"Profile doPost: Cannot parse multipart request...\"";
				wasError = true;
		    } catch (IllegalQueryException e) {
		    	errorMessage = "\"Profile doPost: There was an illegal query to the DB...\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"Profile doPost: Not connected to the DB...\"";
				wasError = true;
			}
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Entry stored successfully\" }");
				if(MongoDBConnector.DEBUG){System.out.println("Updated user icon of user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} // FINISH USERICON
		else {
			String firstname = "", lastname="";
			
			// check parameters user specific parameters
			String callback = request.getParameter("callback");
			credentials = request.getParameter("credentials");
			session = request.getParameter("session");
			if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
				errorMessage = "\"Profile doPost: Please provide the parameters 'pw' and 'username' with the request.\"";
				wasError = true;
			}
			if(!wasError){
				credentials = URLDecoder.decode(credentials, "UTF-8");
				session = URLDecoder.decode(session, "UTF-8");
				credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
				
				// check profile specific parameters
				String gender = request.getParameter("gender");
				firstname = request.getParameter("firstname");
				lastname = request.getParameter("lastname");
				String street = request.getParameter("street");
				String code = request.getParameter("code");
				String city = request.getParameter("city");
				String country = request.getParameter("country");
				String phoneP = request.getParameter("privPhone");
				String phoneM = request.getParameter("mobPhone");
				String phoneW = request.getParameter("workPhone");
				String emailP = request.getParameter("privMail");
				String emailW = request.getParameter("workMail");
				String nationality = request.getParameter("nationality");
				String spouse = request.getParameter("spouse");
				String insurance = request.getParameter("insurance");
				String password = request.getParameter("password");
				String birthday = request.getParameter("birthday");
				gender = (gender!=null)?URLDecoder.decode(gender, "UTF-8"):null;
				firstname = (firstname!=null)?URLDecoder.decode(firstname, "UTF-8"):null;
				lastname = (lastname!=null)?URLDecoder.decode(lastname, "UTF-8"):null;
				street = (street!=null)?URLDecoder.decode(street, "UTF-8"):null;
				code = (code!=null)?URLDecoder.decode(code, "UTF-8"):null;
				city = (city!=null)?URLDecoder.decode(city, "UTF-8"):null;
				country = (country!=null)?URLDecoder.decode(country, "UTF-8"):null;
				phoneP = (phoneP!=null)?URLDecoder.decode(phoneP, "UTF-8"):null;
				phoneM = (phoneM!=null)?URLDecoder.decode(phoneM, "UTF-8"):null;
				phoneW = (phoneW!=null)?URLDecoder.decode(phoneW, "UTF-8"):null;
				emailP = (emailP!=null)?URLDecoder.decode(emailP, "UTF-8"):null;
				emailW = (emailW!=null)?URLDecoder.decode(emailW, "UTF-8"):null;
				nationality = (nationality!=null)?URLDecoder.decode(nationality, "UTF-8"):null;
				spouse = (spouse!=null)?URLDecoder.decode(spouse, "UTF-8"):null;
				insurance = (insurance!=null)?URLDecoder.decode(insurance, "UTF-8"):null;
				birthday = (birthday!=null)?URLDecoder.decode(birthday, "UTF-8"):null;
				
				String md5 = "";
				if(password!=null){
					password = URLDecoder.decode(password, "UTF-8");
					password = StringUtils.newStringUtf8(Base64.decodeBase64(password));
					password = password.substring(password.indexOf(":")+1);
					md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(password);
				}
				
				
				// check if user is logged in and save the new value
				try {
					if(!manager.isUserLoggedIn(session, credentials)){
						errorMessage = "\"Profile doPost: You need to be logged in to use this service\"";
						wasError = true;
						isLoggedIn = false;
					} else {
		                BasicDBList list = new BasicDBList();
						list.add(credentials.substring(0, credentials.lastIndexOf(':')));
						
						if(gender!=null){data.put("gender", gender);}
						if(firstname!=null){data.put("firstname", firstname);}
						if(lastname!=null){data.put("lastname", lastname);}
						if(street!=null){data.put("street", street);}
						if(code!=null){data.put("code", code);}
						if(city!=null){data.put("city", city);}
						if(country!=null){data.put("country", country);}
						if(phoneP!=null){data.put("phoneP", phoneP);}
						if(phoneM!=null){data.put("phoneM", phoneM);}
						if(phoneW!=null){data.put("phoneW", phoneW);}
						if(emailP!=null){data.put("emailP", emailP);}
						if(emailW!=null){data.put("emailW", emailW);}
						if(nationality!=null){data.put("nationality", nationality);}
						if(spouse!=null){data.put("spouse", spouse);}
						if(insurance!=null){data.put("insurance", insurance);}
						if(password!=null){data.put("password", md5);}
						if(birthday!=null){data.put("birthday", birthday);}
						
						connector.update(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("username", new BasicDBObject("$in", list)), new BasicDBObject("$set", data));
					}
				} catch (NotConnectedException e) {
					errorMessage = "\"Profile doPost: We lost connection to the DB. Please try again later. Sorry for that.\"";
					wasError = true;
				} catch (IllegalQueryException e) {
					errorMessage = "\"Profile doPost: There was an internal error. Please contact an administrator and provide him/her with this message: "+e.getMessage()+"\"";
					wasError = true;
				} catch (IllegalArgumentException e){
					errorMessage = "\"Profile doPost: There was an internal error. Please contact an administrator and provide him/her with this message: "+e.getMessage()+"\"";
					wasError = true;
				}
			}
			
		
			// Finally give the user feedback
			if(callback!=null && callback.length()>0){
				callback = URLDecoder.decode(callback, "UTF-8");
				if(!wasError) {
					response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Entry stored successfully\" } );");
					if(MongoDBConnector.DEBUG){System.out.println("Updated user info of user with sessionKey "+session);}
				}
				else {
					response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" } );");
					if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
				}
			} else {
				if(!wasError) {
					response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Entry stored successfully\" }");
					if(MongoDBConnector.DEBUG){System.out.println("Updated user info of user with sessionKey "+session);}
				}
				else {
					response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" }");
					if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
				}
			} 
		}
	}

	/**
	 * @see HttpServlet#doDelete(HttpServletRequest, HttpServletResponse)
	 */
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		
		// check parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Please provide the parameters 'credentials' and 'session' with the request.\"";
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
					list.add(credentials.substring(0, credentials.lastIndexOf(':')));
					if(!connector.delete(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("username", new BasicDBObject("$in", list)))){
						errorMessage = "\"There was an error deleting the user account. Did you provide the correct sessionKey and credentials?\"";
						wasError = true;
					}
					isLoggedIn = false; // user was deleted, hence we are no longer logged in... 					
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
			}
		}
				
		
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError) {
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"User got deleted successfully.\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Deleted user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Profile doDelete: There was an error: "+errorMessage+"!");}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"User got deleted successfully.\" }");
				if(MongoDBConnector.DEBUG){System.out.println("Deleted user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("Profile doDelete: There was an error: "+errorMessage+"!");}
			}
		} 
	}

}
