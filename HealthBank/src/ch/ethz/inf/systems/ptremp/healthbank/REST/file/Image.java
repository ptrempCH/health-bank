package ch.ethz.inf.systems.ptremp.healthbank.REST.file;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.URLDecoder;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.io.IOUtils;
import org.bson.types.ObjectId;


import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;

import ch.ethz.inf.systems.ptremp.healthbank.db.MongoDBConnector;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.IllegalQueryException;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.NotConnectedException;
import ch.ethz.inf.systems.ptremp.healthbank.logic.CoreManager;

/**
 * Servlet implementation class Image
 * In this servlet we implement the functionality to query for images stored in the DB
 * 
 * @author Patrick Tremp
 */
@WebServlet(
		description = "Get images from the database", 
		urlPatterns = { 
				"/Image", 
				"/image"
		})
public class Image extends HttpServlet {

	// Constants ----------------------------------------------------------------------------------

	private static final long serialVersionUID = -2419172287586119980L;
	
	/**
	 * Defines the default buffer size used throughout all the buffers used.
	 */
    private static final int DEFAULT_BUFFER_SIZE = 10240; // 10KB.

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
    public Image() {
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
	 * The GET request allows the caller to retrieve an image from the database
	 * 
	 * For a successful call the following parameters need to be present in the URL:
	 * Via filename:
	 * - type: This values is used to decide whether the caller wants to receive a user icon (value for type="user") or application icon (value for type="application")
	 * - name: The name of the image to retrieve. If an image with this name can not be found, a 404 Error is returned.
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * 
	 * Via id:
	 * - type: This values is used to decide whether the caller wants to receive a user icon (value for type="user") or application icon (value for type="application")
	 * - id: The id of the news entry to be queried
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * 
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 * 
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.addHeader("Access-Control-Allow-Origin", "*");
		
		// check parameters user specific parameters
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			response.sendError(HttpServletResponse.SC_BAD_REQUEST); // 400
        	return;
		}
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
				response.sendError(HttpServletResponse.SC_FORBIDDEN); // 403
	        	return;
			} else {
				// Get Name from request.
		        String fileName = request.getParameter("name");
		        String collection = request.getParameter("type");
				String id = request.getParameter("id");
				
				// url decode them
				if(fileName!=null && fileName.length()>0){fileName = URLDecoder.decode(fileName, "UTF-8");}
				if(collection!=null && collection.length()>0){collection = URLDecoder.decode(collection, "UTF-8");}
				if(id!=null && id.length()>0){id = URLDecoder.decode(id, "UTF-8");}

		        // Check if Name is supplied to the request.
		        if (((fileName == null || fileName.length()<1) && (id == null || id.length()<1)) || collection==null) {
		            response.sendError(HttpServletResponse.SC_NOT_FOUND); // 404.
		            return;
		        }
		        if(collection.equals("application")){
		        	collection = MongoDBConnector.APPLICATION_COLLECTION_NAME;
		        } else if(collection.equals("user")){
		        	collection = MongoDBConnector.USER_COLLECTION_NAME;
		        } else {
		        	response.sendError(HttpServletResponse.SC_BAD_REQUEST); // 400
		        	return;
		        }

		        // Lookup File by FileName in database.
		        GridFS gfsPhoto = new GridFS( connector.getRootDatabase(), collection );
		        GridFSDBFile imageForOutput = null;
		        if(fileName!=null && fileName.length()>0){
					imageForOutput = gfsPhoto.findOne(fileName);
		        } else if(id!=null && id.length()>0 && ObjectId.isValid(id)) {
		        	imageForOutput = gfsPhoto.findOne(new ObjectId(id));
		        }
				
				if(imageForOutput==null){
					response.sendError(HttpServletResponse.SC_NOT_FOUND); // 404.
		            return;
				}

		        // Init servlet response.
		        response.reset();
				response.addHeader("Access-Control-Allow-Origin", "*");
		        response.setBufferSize(DEFAULT_BUFFER_SIZE);
		        response.setContentType(imageForOutput.getContentType());
		        response.setHeader("Content-Length", String.valueOf(imageForOutput.getLength()));
		        response.setHeader("Content-Disposition", "attachment; filename=\"" + imageForOutput.getFilename() + "\"");

		        // Prepare stream.
		        BufferedOutputStream output = null;

		        try {
		            // Open stream.
					output = new BufferedOutputStream(response.getOutputStream(), DEFAULT_BUFFER_SIZE);
					byte[] image = IOUtils.toByteArray(imageForOutput.getInputStream());
					image = Base64.encodeBase64(image);
					response.setHeader("Content-Length", String.valueOf(image.length));
		            
		    		output.write(image);
		            
		            
		        } finally {
		        	try {
		                output.close();
		            } catch (IOException e) {
		                e.printStackTrace();
		            }

		        }
			}
		} catch (IllegalArgumentException e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500
        	return;
		} catch (IllegalQueryException e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500
        	return;
		} catch (NotConnectedException e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500
        	return;
		}
	}


}
