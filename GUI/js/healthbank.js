/*!
 * healthbank JavaScript Library v0.1.0
 *
 * Includes jQuery, jQuery-UI, underscore
 *
 * Copyright 2013, Patrick Tremp, ETH Zurich
 * This is not yet released under a licese so all rights reserved.
 *
 * Last Change: 2013-06-26 at 5pm
 */


/**
* Global Variables
*/
var API_URL = "http://localhost:8080/HealthBank/";
var WEB_URL = "http://localhost/GUI/www/";
var mySession, myCredentials;
var records, circles, spaces, user;

/**
* Checks if the user is logged in and if the session/credentials are stored locally
* Redirects to the login page, if not logged in. 
**/
function checkLogin() {
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Login",
			type: 'get',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials) },
			success: function(data){
				if(data.result=="failed"){
					if(data.status=="loggedOut"){
						window.location = WEB_URL+"login.html"; 
					}
				} else { /* don't do anything, since we are still logged in. */	}
			}
		});
	}
}

/**
* Logouts the user and clear the local storage
* Redirects to the index page, when successfull. Redirects to login page, when there was an error. 
**/
function logout() {
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Logout",
			type: 'post',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials) },
			success: function(data){
				if(data.result=="failed"){
					window.location = WEB_URL+"login.html"; 
				} else {
					if(typeof(Storage)!=="undefined")
					{
						localStorage.setItem("hb_session", "");
						localStorage.setItem("hb_cred", "");
					}
					window.location = WEB_URL+"../index.html";
				}
			}
		});
	}
}

/**
* Initialize the websites with checking for login, defining the search bar at the top right, loading user information,
* and setting up click and key listener for the default parts
*/
function initialize() {
	checkLogin();
	setSearchAnimation();
	getUserData({onSuccess: function(data){loadUserInfo(data);}});
	$("#logoutButton").click(function(){
		logout();
		return false;
	});
	$("#userInfo").click(function(){
		window.location = WEB_URL+"profile.html";
		return false;
	});
	$("#search").keyup(function(event){
	    if(event.keyCode == 13){
	    	if($("#search").val()!=undefined && $("#search").val().length>0){
	    		if(typeof(Storage)!=="undefined") {
					localStorage.setItem("hb_query", $("#search").val());
				}
	        	window.location = WEB_URL + "queryResult.html?";
	       	}
	    }
	});
}

/**
* Initializing spaces pages with loading of the circles, spaces and records (only of the current logged in user),
* loading HTML snippets for popups and setting the click listener for the newTabIcon.
*
* Parameters:
* - backurl: the url of the space, we are initializing. This is propagated over several steps to loadLatestRecords() where it is needed for clicks on detail views of records.
* - spacesName: The name of the current space. This is propagated over seceral steps to loadLatestRecords()
* - nrOfItems: The amount of records that shall be displayed in the list. Used by loadLatestRecords().
*/
function initializeSpaces(backurl, spacesName, nrOfItems) {
	loadCircles({onSuccess: function(data){loadedCircles(data);}, onError: function(){console.log("Error while loading circles...");}});
	loadSpaces({onSuccess: function(data){loadedSpaces(data);}, onError: function(){console.log("Error while loading spaces...");}});
	loadRecords({onSuccess: function(data){loadLatestRecords(data, backurl, spacesName, nrOfItems);}, onError: function(){loadRecordsError();}});
	$.get(WEB_URL+'dialogs/circles-form.html').success(function(data){$('body').append(data); loadedExtHTMLForSpaces(backurl, spacesName, nrOfItems);});
	$.get(WEB_URL+'dialogs/spaces-form.html').success(function(data){$('body').append(data); loadedExtHTMLForSpaces(backurl, spacesName, nrOfItems);});
	$.get(WEB_URL+'dialogs/dialog-overlay.html').success(function(data){$('body').append(data); loadedExtHTMLForSpaces(backurl, spacesName, nrOfItems);});
	$.get(WEB_URL+'dialogs/newSpace-form.html').success(function(data){$('body').append(data); loadedExtHTMLForSpaces(backurl, spacesName, nrOfItems);});

	$("#newTabIcon").click(function(){
		$('#newSpace-form').height("300px");
		$('#newSpace-form').width("600px");
		var maskHeight = $(document).height();  
		var maskWidth = $(window).width();
		var dialogTop =  (maskHeight/2) - ($('#newSpace-form').height()/2);  
		var dialogLeft = (maskWidth/2) - ($('#newSpace-form').width()/2);
		var recordId = $(this).val();
		$('#dialog-overlay').css({height:maskHeight, width:maskWidth}).show();
		$('#newSpace-form').css({top:dialogTop, left:dialogLeft}).show();
		$("#currentSpaces").contents().remove();
		$("#currentSpaces").append("<li>All Entries</li>");
		$.each(spaces, function(i, item){
			var spaceName = item.name.replace(" ", "");
			if(spaceName=="Medical" || spaceName=="Wellness"){
				$("#currentSpaces").append("<li id=\""+item._id.$oid+"\" name=\""+spaceName+"\" value=\""+spaceName+"\">"+item.name+"<button class='button hideSpaceButton'>Hide</button></li></br>");
			} else {
				$("#currentSpaces").append("<li id=\""+item._id.$oid+"\" name=\""+spaceName+"\" value=\""+spaceName+"\">"+item.name+"<button class='button hideSpaceButton'>Hide</button><button class='button hideSpaceButton'>Remove</button></li></br>");
			}
		});
		$("#currentSpaces").sortable();
		return false;
	});
}

/**
* This function belongs to the initializeSpaces function and is called whenever a new external HTML component was loaded successfully.  
* We wait until this function was called four times (by using helping variable spacesIter) and then set all needed click handlers.
*
* Parameters:
* - backurl: the url of the space, we are initializing. This is propagated over several steps to loadLatestRecords() where it is needed for clicks on detail views of records.
* - spacesName: The name of the current space. This is propagated over seceral steps to loadLatestRecords()
* - nrOfItems: The amount of records that shall be displayed in the list. Used by loadLatestRecords().
*/
var spacesIter = 0;
function loadedExtHTMLForSpaces(backurl, spacesName, nrOfItems) {
	spacesIter++;
	if(spacesIter==4){
		$( "#closeCircles, #dialog-overlay, #closeSpaces, #closeSpacesTab" ).click(function() {
			$("#circles-form, #spaces-form, #dialog-overlay, #newSpace-form").hide();
			return false;
		});
		$( "#saveCircles" ).click(function() {
			saveCirclesOfRecord($("#recordID").val(), backurl, spacesName, nrOfItems);
			return false;
		});
		$( "#saveSpaces" ).click(function() {
			saveSpacesOfRecord($("#SrecordID").val(), backurl, spacesName, nrOfItems);
			return false;
		});
		$("#saveSpacesTab").click(function() {
			$( "#notYetImplementedDialog" ).dialog( "open" );
		});
	}
}


/**
* Gets called, when the not yet implemented dialog was loaded from an external HTML file. This will initialize the dialog
*/
function loadedNotYetImplemented() {
	$( "#notYetImplementedDialog" ).dialog({
		autoOpen: false,
		show: {
			effect: "slide",
			duration: 500
		},
		hide: {
			effect: "scale",
			duration: 500
		}
	});
}


/**
* Sets the animation for the search bar.
**/
function setSearchAnimation() {
	$("#search").focus(function(){
		setTimeout(function(){$("#search").select();},500);
		$("#search").css("width", "190px");
		$("#searchIconSpan").css("margin-left", "0px");
		$("#logoutButton").css("margin-right", "0px");
		$("#searchIcon").attr('src',WEB_URL+"../images/search-icon-selected.png");
	});
	$('#search').blur(function() {
		$("#search").css("width", "120px");
		$("#searchIconSpan").css("margin-left", "70px");
		$("#logoutButton").css("margin-right", "-70px");
		$("#searchIcon").attr('src',WEB_URL+"../images/search-icon.png");
	});
	$("#searchIcon").click(function() {
		$("#search").focus();
	});
}

/**
* Gets the data of the user such as username, name etc. 
* Redirects to the login page, if not logged in. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
**/
function getUserData(callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Profile",
			type: 'get',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials) },
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					}
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}

/**
* This function gathers all the information the user has provided in the profile view and sends it 
* to the server via AJAX. This is needed to update any information about the user. This function is 
* not only used in the edit profile dialog but can be used by anyone to update userData. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - userData: All the userData to work with. Only existing fields will be transmitted. Possible fields are gender, code, street, city, country, privMail,
* 				workMail, privPhone, mobPhone, workPhone, nationality, spouse, insurance, birthday, height, weight and allowResearch
*/
function updateUserData(userData, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		if(userData.gender==undefined){userData.gender="";}
		if(userData.code==undefined){userData.code="";}
		if(userData.street==undefined){userData.street="";}
		if(userData.city==undefined){userData.city="";}
		if(userData.country==undefined){userData.country="";}
		if(userData.privMail==undefined){userData.privMail="";}
		if(userData.workMail==undefined){userData.workMail="";}
		if(userData.privPhone==undefined){userData.privPhone="";}
		if(userData.mobPhone==undefined){userData.mobPhone="";}
		if(userData.workPhone==undefined){userData.workPhone="";}
		if(userData.nationality==undefined){userData.nationality="";}
		if(userData.spouse==undefined){userData.spouse="";}
		if(userData.insurance==undefined){userData.insurance="";}
		if(userData.birthday==undefined){userData.birthday="";}
		if(userData.height==undefined){userData.height="";}
		if(userData.weight==undefined){userData.weight="";}
		if(userData.allowResearch==undefined){userData.allowResearch="n";}
		
		$.ajax({
			url: API_URL+"Profile",
			type: 'post',
			dataType: 'json',
			data: { 
				session : encodeURIComponent(mySession), 
				credentials : encodeURIComponent(myCredentials), 
				surname : encodeURIComponent(userData.surname),
				lastname : encodeURIComponent(userData.lastname),
				gender : encodeURIComponent(userData.gender),
				street : encodeURIComponent(userData.street),
				code : encodeURIComponent(userData.code),
				city : encodeURIComponent(userData.city),
				country : encodeURIComponent(userData.country),
				privMail : encodeURIComponent(userData.privMail),
				workMail : encodeURIComponent(userData.workMail),
				privPhone : encodeURIComponent(userData.privPhone),
				mobPhone : encodeURIComponent(userData.mobPhone),
				workPhone : encodeURIComponent(userData.workPhone),
				nationality : encodeURIComponent(userData.nationality),
				spouse : encodeURIComponent(userData.spouse),
				insurance : encodeURIComponent(userData.insurance),
				birthday : encodeURIComponent(userData.birthday),
				height : encodeURIComponent(userData.height),
				weight : encodeURIComponent(userData.weight),
				allowResearch : encodeURIComponent(userData.allowResearch)
			},
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					} else{
						if(callback.onError){
					        callback.onError(data);
					    }
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}

/**
* This function allows updating of the user password. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - pw: The new password of the user. 
*/
function updatePassword(pw, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		var username = $.base64.decode(myCredentials);
		username = username.substring(0,username.indexOf(":"));
		var credentials = $.base64.encode(username+":"+pw);
		
		$.ajax({
			url: API_URL+"Profile",
			type: 'post',
			dataType: 'json',
			data: { 
				session : encodeURIComponent(mySession), 
				credentials : encodeURIComponent(myCredentials), 
				password : encodeURIComponent(credentials)
			},
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					} else{
						if(callback.onError){
					        callback.onError(data);
					    }
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}

/**
* This function will be called after the user data has been retrieved from the server.
* It is responsible of dealing with the userIcon and checking for different user types.
*/
function loadUserInfo(data){
	if(data==undefined){ 
		/* Do nothing for the moment */ 
		console.log("data undefined in loadUserInfo()");
		return;
	}
	data = data.values.user[0];
	if(data.userIcon!=undefined){
		$("#userInfo").html(decodeURIComponent(data.username));
		loadImage(data.userIcon, "", {onSuccess: function(data){loadUserIcon(data);}});
	} else {
		$("#userInfo").html("Logged in as: "+decodeURIComponent(data.username));
	}
	if(data.type=="institute"){
		$("#navApps").hide();
	}
	if (typeof loadUserInfoIndividual == 'function') { 
		loadUserInfoIndividual(data); 
	}
	user = data;
}

/**
* This function will be called after loadImage returned from the AJAX call. It will set the user icon to the correct
* place in the top right of the screen
*/
function loadUserIcon(data){
	$("#userInfo").html("<img src=\"data:image/png;base64,"+data+"\" height=\"25px\"/>"+$("#userInfo").html());
	$(".detail-icon").html("<img src=\"data:image/png;base64,"+data+"\" width=\"50px\"/>");
	$(".detail-icon").css("display", "block");
}

/**
* This function is a helper to update the user icon in the profile view. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - formData: The form data that contains all the information about the new user icon
*/
function updateUserIcon(formData, callback) {
	callback = callback || {};
	var oXHR = new XMLHttpRequest();
	oXHR.addEventListener('load', callback.onSuccess, false);
	oXHR.addEventListener('error', callback.onError, false);
	oXHR.open('POST', API_URL+"Profile");
	oXHR.send(formData);
}

/**
* This function will update the associated circles of a record
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - recordId: The id of the record to update the circles
* - circles: A string with all the circle ids seperated by a space
*/
function updateRecordCircles(recordId, circles, callback){
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Record",
			type: 'post',
			dataType: 'json',
			data: { 
				session : encodeURIComponent(mySession), 
				credentials : encodeURIComponent(myCredentials), 
				id : encodeURIComponent(recordId),
				circle : encodeURIComponent(circles)
			},
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					} else{
						if(callback.onError){
					        callback.onError(data);
					    }
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}, 
			error: function(data){
				console.log(data);
			}
		});
	}
}

/**
* This function will update the associated spaces of a record
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - recordId: The id of the record to update the spaces
* - circles: A string with all the spaces ids seperated by a space
*/
function updateRecordSpaces(spaceId, spaces, callback){
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Record",
			type: 'post',
			dataType: 'json',
			data: { 
				session : encodeURIComponent(mySession), 
				credentials : encodeURIComponent(myCredentials), 
				id : encodeURIComponent(spaceId),
				space : encodeURIComponent(spaces)
			},
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					} else{
						if(callback.onError){
					        callback.onError(data);
					    }
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}, 
			error: function(data){
				console.log(data);
			}
		});
	}
}

/**
* Gets the records of the user 
* Redirects to the login page, if not logged in. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
**/
function loadRecords(callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Record",
			type: 'get',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials) },
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					}
					if(callback.onError){
						console.log("calling onError");
				        callback.onError();
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}

/**
* Gets the circles of the user 
* Redirects to the login page, if not logged in. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
**/
function loadCircles(callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Circle",
			type: 'get',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials) },
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					}
					if(callback.onError){
						console.log("calling onError");
				        callback.onError();
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}

/**
* Add a new circle to the DB
* Redirects to login, if not logged in.

* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - name: The name of the new circle
* - descr: The description of the new circle
**/
function addCircle(name, descr, callback){
	callback = callback || {};
	if(name==undefined || name.length<2 || descr==undefined || descr.length<2){
		return false;
	}
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Circle",
			type: 'post',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), name : encodeURIComponent(name), descr : encodeURIComponent(descr) },
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					}
					if(callback.onError){
						console.log("calling onError");
				        callback.onError();
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}

/**
* Edit an existing circle in the DB
* Redirects to login, if not logged in.
* 
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - id: The id of the circle to edit
* - name: The new name of the circle
* - descr: The new description of the circle
**/
function editCircle(id, name, descr, callback){
	callback = callback || {};
	if(name==undefined || name.length<2 || descr==undefined || descr.length<2 || id==undefined || id.length<2){
		return false;
	}
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Circle",
			type: 'post',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), id : encodeURIComponent(id), name : encodeURIComponent(name), descr : encodeURIComponent(descr) },
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					}
					if(callback.onError){
						console.log("calling onError");
				        callback.onError();
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}

/**
* Delete an existing circle from the DB
* Redirects to login, if not logged in.
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - id: the id of the circle to delete
**/
function deleteCircle(id, callback){
	callback = callback || {};
	if(id==undefined || id.length<2){
		return false;
	}
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Circle",
			type: 'post',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), id : encodeURIComponent(id), del : encodeURIComponent("true") },
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					}
					if(callback.onError){
						console.log("calling onError");
				        callback.onError();
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}

/**
* Gets the spaces of the user 
* Redirects to the login page, if not logged in. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
**/
function loadSpaces(callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Space",
			type: 'get',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials) },
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					}
					if(callback.onError){
						console.log("calling onError "+data);
				        callback.onError();
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}


/**
* Add a new record to the user's chronicle 
* Redirects to the login page, if not logged in. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - name: The name of the new record
* - descr: The description of the new record
* - values: Additional values to be saved with this record in JSON data format. Use jsonlint.org to check for correctness  of your JSON
**/
function addRecord(name, descr, values, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		if(values==undefined || values.length<1){
			if(callback.onError){
				console.log("calling onError");
		        callback.onError("values entry missing");
		    }
		    return false;
		}
		
		$.ajax({
			url: API_URL+"Record",
			type: 'post',
			dataType: 'json',
			data: { 
				session : encodeURIComponent(mySession), 
				credentials : encodeURIComponent(myCredentials), 
				name : encodeURIComponent(name), 
				descr : encodeURIComponent(descr),
				values : encodeURIComponent(values) 
			},
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					}
					if(callback.onError){
						console.log("calling onError");
				        callback.onError(data.error);
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}

/**
* Add a new news entry 
* Redirects to the login page, if not logged in. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - isEdit: Boolean to decied if we are editing an existing news or if this is a new entry
* - id: The id of the news entry to edit. Empty if we are providing a new entry.
* - title: The title of the news
* - prev: A small preview for the news entry
* - content: The content of the news entry
**/
function addNews(isEdit, id, title, prev, content, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		if(content==undefined || content.length<1 || title==undefined || title.length<1 || prev==undefined || prev.length<1){
			if(callback.onError){
				console.log("calling onError");
		        callback.onError("Please specify title, preview and content for a news entry.");
		    }
		    return false;
		}
		
		if(isEdit){
			$.ajax({
				url: API_URL+"News",
				type: 'post',
				dataType: 'json',
				data: { 
					session : encodeURIComponent(mySession), 
					credentials : encodeURIComponent(myCredentials), 
					title : encodeURIComponent(title), 
					prev : encodeURIComponent(prev),
					content : encodeURIComponent(content),
					id : encodeURIComponent(id)
				},
				success: function(data){
					if(data.result=="failed"){
						if(data.loggedOut=="true"){
							window.location = WEB_URL+"login.html"; 
						}
						if(callback.onError){
							console.log("calling onError");
					        callback.onError(data.error);
					    }
					} else { 
						if(callback.onSuccess){
					        callback.onSuccess(data);
					    }
					}
				}
			});
		} else {
			$.ajax({
				url: API_URL+"News",
				type: 'post',
				dataType: 'json',
				data: { 
					session : encodeURIComponent(mySession), 
					credentials : encodeURIComponent(myCredentials), 
					title : encodeURIComponent(title), 
					prev : encodeURIComponent(prev),
					content : encodeURIComponent(content) 
				},
				success: function(data){
					if(data.result=="failed"){
						if(data.loggedOut=="true"){
							window.location = WEB_URL+"login.html"; 
						}
						if(callback.onError){
							console.log("calling onError");
					        callback.onError(data.error);
					    }
					} else { 
						if(callback.onSuccess){
					        callback.onSuccess(data);
					    }
					}
				}
			});
		}
	}
}

/**
* Gets the latest news entries. 
* Redirects to the login page, if not logged in. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
**/
function loadNews(callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"News",
			type: 'get',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials)},
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					}
					if(callback.onError){
						console.log("calling onError");
				        callback.onError();
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}

/**
* Gets a specific news entries. 
* Redirects to the login page, if not logged in. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
*- id: The id of the news entry to load
**/
function loadNewsEntry(id, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		if(id==undefined){
			if(callback.onError){ callback.onError();}
			return false;
		}
		
		$.ajax({
			url: API_URL+"News",
			type: 'get',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), id : encodeURIComponent(id) },
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					}
					if(callback.onError){
						console.log("calling onError");
				        callback.onError();
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}

/**
* Searches for users according to a query 
* Redirects to the login page, if not logged in. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - query: The query to search a user 
**/
function queryUsers(query, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"userquery",
			type: 'get',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), query: encodeURIComponent(query) },
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					}
					if(callback.onError){
						console.log("calling onError");
				        callback.onError();
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}

/**
* Searches for users according to an id 
* Redirects to the login page, if not logged in. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - id: The id of the user to query
**/
function queryUserById(id, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"userquery",
			type: 'get',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), id: encodeURIComponent(id) },
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					}
					if(callback.onError){
						console.log("calling onError");
				        callback.onError();
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}

/**
* Searches for entries according to a query 
* Redirects to the login page, if not logged in. 
* 
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - query: The query to search a record
**/
function queryEntries(query, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"recordquery",
			type: 'get',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), query: encodeURIComponent(query) },
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					}
					if(callback.onError){
						console.log("calling onError");
				        callback.onError();
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}

/**
* Alligns all elements you called this for according to the longest of these elements.
* E.g. $("div.myfields div label").autoWidth(); will align all labels to the longest one 
*/
jQuery.fn.autoWidth = function(options) 
{ 
  var settings = { 
        limitWidth   : false 
  } 

  if(options) { 
        jQuery.extend(settings, options); 
    }; 

    var maxWidth = 0; 

  this.each(function(){ 
        if ($(this).width() > maxWidth){ 
          if(settings.limitWidth && maxWidth >= settings.limitWidth) { 
            maxWidth = settings.limitWidth; 
          } else { 
            maxWidth = $(this).width(); 
          } 
        } 
  });   

  this.width(maxWidth); 
}

/**
* Helper function to load an user icon from the server
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - iconName: The name of the icon to load from the server
* - userId: the user id that belongs to the user we are loading the image from
*/
function loadImage(iconName, userId, callback){
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Image",
			type: 'get',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), name : (iconName!=undefined)?encodeURIComponent(iconName):encodeURIComponent("defaultUserIcon")},
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					}
					if(callback.onError){
						console.log("calling onError");
				        callback.onError(data);
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data, userId);
				    }
				}
			}
		});
	}
}

/**
* Helper function to retrieve a parameter from the URL that was called to load this page.
*
* Parameter: 
* - name: The name of the parameter to query.
*/
function getURLParameter(name) {
    return decodeURIComponent((new RegExp('[?|&]' + name + '=' + '([^&;]+?)(&|#|;|$)').exec(location.search)||[,""])[1].replace(/\+/g, '%20'))||null;
}

/**
* This function will add a user to a certain circle
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - circleId: The id of the circle we want to add a user to
* - itemId: the user id that belongs to the user we want to add to the circle
*/
function addUserToCircle(circleId, itemId, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Circle",
			type: 'post',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), id : encodeURIComponent(circleId), userId : encodeURIComponent(itemId)},
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					}
					if(callback.onError){
						console.log("calling onError");
				        callback.onError(data);
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}

/**
* This function will remove a user from a certain circle
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - circleId: The id of the circle we want to remove a user from
* - itemId: the user id that belongs to the user we want to remove from the circle
*/
function removeUserFromCircle(circleId, itemId, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Circle",
			type: 'post',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), id : encodeURIComponent(circleId), userId : encodeURIComponent(itemId), del : encodeURIComponent("true")},
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					}
					if(callback.onError){
						console.log("calling onError");
				        callback.onError(data);
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}

/**
* Helper function to check if on the current computer the HTML 5 local storage is accessible
*/
function isStorageDefined(){
	if(typeof(Storage)!=="undefined")
	{
		mySession = localStorage.getItem("hb_session");
		myCredentials = localStorage.getItem("hb_cred");
		if(mySession==undefined || myCredentials==undefined || mySession=="" || myCredentials==""){ 
			window.location = WEB_URL+"login.html"; 
			return;
		} else {
			return true;
		}
	} else {
		return false;
	}
}

/**
* Called when the loadCircle function returns its AJAX request. Will store circles in circles variable.
*/
function loadedCircles(data){
	if(data.values.circles.size==0){
		return false;
	}
	circles = data.values.circles;
}

/**
* Called when the loadCircle function returns its AJAX request. Will store circles in circles variable.
*/
function loadedSpaces(data){
	if(data.values.spaces.size==0){
		return false;
	}
	spaces = data.values.spaces;
}

/**
* This function checks which spaces have been selected in the dialog and saves the selection to the server
*
* Parameters:
* - backurl: the url of the space, we are initializing. This is propagated over several steps to loadLatestRecords() where it is needed for clicks on detail views of records.
* - spacesName: The name of the current space. This is propagated over seceral steps to loadLatestRecords()
* - nrOfItems: The amount of records that shall be displayed in the list. Used by loadLatestRecords().
* - recordId: The id of the record we want to save the spaces to 
*/
function saveSpacesOfRecord(recordId, backlink, space_name, nrOfItems){
	var selectedSpaces = [];
	var iter = 0;
	var s = "";
	if(spaces!=undefined){
		$.each(spaces, function(i, item){
			if($("#"+item._id.$oid).is(":checked")){
				selectedSpaces[iter] = item._id.$oid;
				iter++;
			}
		});
		$.each(selectedSpaces, function(i, item){
			s += item+" ";
		});
		s = s.substring(0,s.length-1);
		updateRecordSpaces(
			recordId, 
			s, 
			{
				onSuccess: function(data){
					$("#circles-form, #dialog-overlay, #spaces-form").hide();
					loadRecords({onSuccess: function(data){loadLatestRecords(data, backlink, space_name, nrOfItems);}, onError: function(){loadRecordsError();}});
				}, 
				onError: function(){
					d=document.createElement('div');
					$(d).html("<p style=\"color:red\">There was an error assigning your spaces. We are terribly sorry</p>")
						.dialog({
							autoOpen: false,
							show: {
								effect: "slide",
								duration: 200
							},
							hide: {
								effect: "scale",
								duration: 200
							}
						});
					$(d).dialog("open");
				}
			}
		);
	} else {
		d=document.createElement('div');
		$(d).css("z-index", "1000");
		$(d).html("<p style=\"color:red\">There was an error assigning your spaces. We are terribly sorry</p>")
			.dialog({
				autoOpen: false,
				show: {
					effect: "slide",
					duration: 200
				},
				hide: {
					effect: "scale",
					duration: 200
				}
			});
		$(d).dialog("open");
	}
}

/**
* This function checks which circles have been selected in the dialog and saves the selection to the server
*
* Parameters:
* - backurl: the url of the space, we are initializing. This is propagated over several steps to loadLatestRecords() where it is needed for clicks on detail views of records.
* - spacesName: The name of the current space. This is propagated over seceral steps to loadLatestRecords()
* - nrOfItems: The amount of records that shall be displayed in the list. Used by loadLatestRecords().
* - recordId: The id of the record we want to save the circles to 
*/
function saveCirclesOfRecord(recordId, backlink, space_name, nrOfItems){
	var selectedCircles = [];
	var iter = 0;
	var s = "";
	$.each(circles, function(i, item){
		if($("#"+item._id.$oid).is(":checked")){
			selectedCircles[iter] = item._id.$oid;
			iter++;
		}
	});
	$.each(selectedCircles, function(i, item){
		s += item+" ";
	});
	s = s.substring(0,s.length-1);
	updateRecordCircles(
		recordId, 
		s, 
		{
			onSuccess: function(data){
				$("#circles-form, #dialog-overlay").hide();
				loadRecords({onSuccess: function(data){loadLatestRecords(data, backlink, space_name, nrOfItems);}, onError: function(){loadRecordsError();}});
			}, 
			onError: function(){
				d=document.createElement('div');
				$(d).html("<p style=\"color:red\">There was an error assigning your circles. We are terribly sorry</p>")
					.dialog({
						autoOpen: false,
						show: {
							effect: "slide",
							duration: 200
						},
						hide: {
							effect: "scale",
							duration: 200
						}
					});
				$(d).dialog("open");
			}
		}
	);
}

/**
* This function loads the latest records of the current logged in user and initialices all kind of click listener
*
* Parameters:
* - backurl: the url of the space, we are initializing. 
* - spacesName: The name of the current space. 
* - nrOfItems: The amount of records that shall be displayed in the list.
* - recordId: The id of the record we want to save the spaces to 
*/
function loadLatestRecords(data, backlink, space_name, nrOfItems) {
	$("#recordentries").html("");
	if(data.values.records==undefined || data.values.records.length==0){
		$("#recordentries").append("<center>No entries yet!</center>");
		return false;
	}
	records = data.values.records;
	records.reverse();

	if(space_name==undefined || space_name.length<1){
		$.each(records, function(i, item) {
		    $("#recordentries").append('<li><h3 class="recordlist-itemheader">'+decodeURIComponent(item.name)+'</h3><i>Created at: '+item.timedate+'</i><button value="'+item._id.$oid+'" class=\"list-button spaceButton\">Spaces</button><button value="'+item._id.$oid+'" class=\"list-button circleButton\">Circles</button></li>');
		    if(nrOfItems>0 && i==(nrOfItems-1)){
		    	return false;
		    }
		});
	} else {
		var spaceID = "";
		$.each(spaces, function(i, item){
			if(item.name==space_name){ spaceID = item._id.$oid;}
		});
		$.each(records, function(i, item) {
			var found = false;
			if(item.spaces!=undefined){
				$.each(item.spaces, function(j, c){
					if(c == spaceID){ found = true;}
				});
				if(found){
			    	$("#recordentries").append('<li><h3 class="recordlist-itemheader">'+decodeURIComponent(item.name)+'</h3><i>Created at: '+item.timedate+'</i><button value="'+item._id.$oid+'" class=\"list-button spaceButton\">Spaces</button><button value="'+item._id.$oid+'" class=\"list-button circleButton\">Circles</button></li>');
				}
			}
		});			
		if($("#recordentries").html()==""){
			$("#recordentries").html("No entries yet. Please assign records to the "+space_name+" space first in order to see them here.");
		}
	}

	$(".recordlist-itemheader").click(function(){ 
		if(typeof(Storage)!=="undefined") {
			localStorage.setItem("hb_detailId", $($(this).parent()).find("button")[0].value);
			localStorage.setItem("hb_backlink", backlink);
		}
		window.location = WEB_URL+"detailRecord.html"; 

	});
    $(".circleButton").click(function(){
		$('#circles-form').height("320px");
		var maskHeight = $(document).height();  
		var maskWidth = $(window).width();
		var dialogTop =  (maskHeight/2) - ($('#circles-form').height()/2);  
		var dialogLeft = (maskWidth/2) - ($('#circles-form').width()/2);
		var recordId = $(this).val();
		$('#dialog-overlay').css({height:maskHeight, width:maskWidth}).show();
		$('#circles-form').css({top:dialogTop, left:dialogLeft}).show();
		$(".circles-form label").autoWidth();
		$("#recordID").val(recordId)
		$("#circle").contents().remove();
		$.each(circles, function(i, item){
			var circleName = item.name.replace(" ", "");
			$("#circle").append("<input type=\"checkbox\" id=\""+item._id.$oid+"\" name=\""+circleName+"\" value=\""+circleName+"\">"+item.name+"</input></br>");
		});
		$.each(records, function(i, item) {
			if(item._id.$oid==recordId){
				$("#record-name").html(item.name);
				if(item.circles!=undefined){
					$.each(item.circles, function(j,c){
						$("#"+c).prop('checked', true);
					});
				}
				return false;
			}
		});
		return false;
    });
    $(".spaceButton").click(function(){
    	$('#spaces-form').height("320px");
		var maskHeight = $(document).height();  
		var maskWidth = $(window).width();
		var dialogTop =  (maskHeight/2) - ($('#spaces-form').height()/2);  
		var dialogLeft = (maskWidth/2) - ($('#spaces-form').width()/2);
		var recordId = $(this).val();
		$('#dialog-overlay').css({height:maskHeight, width:maskWidth}).show();
		$('#spaces-form').css({top:dialogTop, left:dialogLeft}).show();
		$(".spaces-form label").autoWidth();
		$("#SrecordID").val(recordId);
		$("#space").contents().remove();
		$.each(spaces, function(i, item){
			var spaceName = item.name.replace(" ", "");
			$("#space").append("<input type=\"checkbox\" id=\""+item._id.$oid+"\" name=\""+spaceName+"\" value=\""+spaceName+"\">"+item.name+"</input></br>");
		});
		$.each(records, function(i, item) {
			if(item._id.$oid==recordId){
				$("#recordS-name").html(item.name);
				if(item.spaces!=undefined){
					$.each(item.spaces, function(j,s){
						$("#"+s).prop('checked', true);
					});
				}
				return false;
			}
		});
		return false;
    });
}
/**
* Gets called when the AJAX request for loading the records of the current user fails
*/
function loadRecordsError() {
	$("#recordentries").html("<center style=\"color: red\">Either you have no entries yet or there was an error!</center>");
	console.log("onError");
}

/**
* Called when the AJAX request to load the latest news returns successfully. Will display the news on the screen in a list.
* 
* Parameters:
* - data: The news data from the server
* - nrOfItems: how many news items shall be displayed in the list
*/
function loadLatestNews(data, nrOfItems) { 
	if(user==undefined){
		setTimeout(function(){loadLatestNews(data);}, 100);
	}
	$("#newsentries").html("");
	if(data.size==0){
		$("#newsentries").append("<center>No news yet!</center>");
	}

	news = data.values.news;
	news.reverse();
	$.each(news, function(i, item) {
	    $("#newsentries").append('<li><div id="'+item._id.$oid+'"><img class="newsicon" src="'+WEB_URL+'../images/news-icon.jpg"/><div class="newscontentbox"><h4 class="newsheader">'+item.title+'</h4><p class="newscontent">'+item.prev+'</p></div></div>'+((user!=undefined && user.type=="admin")?'<button value="'+item._id.$oid+'" class=\"list-button newsEditButton\">Edit</button>':'')+'</li>');
	    if(nrOfItems!=0 && i==(nrOfItems-1)){
	    	return false;
	    }
	});
	$("#news-section #newsentries li div").click(function(){ 
		window.location = WEB_URL+"news/detailNews.html?id="+$(this).attr("id"); 
	});
	$("#news-section #newsentries li .list-button").click(function(){
		window.location = WEB_URL+"news/addNews.html?id="+$(this).attr("value"); 
	});
}

/**
* Called when the AJAX request to load the latest news returns with an error. 
*/
function loadNewsError(){
	$("#newsentries").html("<center style=\"color: red\">Either there are no news in the system yet or there was an error!</center>");
}

/**
* Helper function that will adjust the size of a text area when there was some action by the user on it.
*
* Parameters:
* - o: The text area to adjust
*/
function textAreaAdjust(o) {
    o.style.height = "1px";
    o.style.height = (25+o.scrollHeight)+"px";
}

/**
* This function loads user information about a user given its id
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - id: The id of the user to load infos from
*/
function loadUserById(id, callback){
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"userquery",
			type: 'get',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), id: encodeURIComponent(id) },
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					}
					if(callback.onError){
						console.log("calling onError");
				        callback.onError();
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}

/**
* This function will update the circles associated to a certain space
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - spaceId: The id of the space we want to update
* - circles: A string with the circle ids seperated by a string
*/
function updateCirclesOfSpace(spaceId, circles, callback){
	if(spaceId==undefined || spaceId.length<2){
		return false;
	}
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Space",
			type: 'post',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), id: encodeURIComponent(spaceId), circle: encodeURIComponent(circles) },
			success: function(data){
				if(data.result=="failed"){
					if(data.loggedOut=="true"){
						window.location = WEB_URL+"login.html"; 
					}
					if(callback.onError){
						console.log("calling onError");
				        callback.onError();
				    }
				} else { 
					if(callback.onSuccess){
				        callback.onSuccess(data);
				    }
				}
			}
		});
	}
}