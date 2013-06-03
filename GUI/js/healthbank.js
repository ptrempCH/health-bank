/**
* Global Variables
*/
var API_URL = "http://localhost:8080/HealthBank/";
var WEB_URL = "http://localhost/GUI/www/"

/**
* Checks if the user is logged in and if the session/credentials are stored locally
* Redirects to the login page, if not logged in. 
**/
function checkLogin() {
	if(typeof(Storage)!=="undefined")
	{
		var mySession = localStorage.getItem("hb_session");
		var myCredentials = localStorage.getItem("hb_cred");
		if(mySession==undefined || myCredentials==undefined || mySession=="" || myCredentials==""){ 
			window.location = WEB_URL+"login.html"; 
			return;
		}
		
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
	if(typeof(Storage)!=="undefined")
	{
		var mySession = localStorage.getItem("hb_session");
		var myCredentials = localStorage.getItem("hb_cred");
		if(mySession==undefined || myCredentials==undefined || mySession=="" || myCredentials==""){ 
			window.location = WEB_URL+"login.html"; 
			return;
		}
		
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
* Sets the animation for the search bar.
**/
function setSearchAnimation() {
	$("#search").focus(function(){
		setTimeout(function(){$("#search").select();},500);
		$("#search").css("width", "190px");
		$("#searchIconSpan").css("margin-left", "0px");
		$("#logoutButton").css("margin-right", "0px");
		$("#searchIcon").attr('src',"../images/search-icon-selected.png");
	});
	$('#search').blur(function() {
		$("#search").css("width", "120px");
		$("#searchIconSpan").css("margin-left", "70px");
		$("#logoutButton").css("margin-right", "-70px");
		$("#searchIcon").attr('src',"../images/search-icon.png");
	});
	$("#searchIcon").click(function() {
		$("#search").focus();
	});
}

/**
* Sets the animation for the search bar in Apps
**/
function setSearchAnimationApp() {
	$("#search").focus(function(){
		setTimeout(function(){$("#search").select();},500);
		$("#search").css("width", "190px");
		$("#searchIconSpan").css("margin-left", "0px");
		$("#logoutButton").css("margin-right", "0px");
		$("#searchIcon").attr('src',"../../images/search-icon-selected.png");
	});
	$('#search').blur(function() {
		$("#search").css("width", "120px");
		$("#searchIconSpan").css("margin-left", "70px");
		$("#logoutButton").css("margin-right", "-70px");
		$("#searchIcon").attr('src',"../../images/search-icon.png");
	});
	$("#searchIcon").click(function() {
		$("#search").focus();
	});
}

/**
* Gets the data of the user such as username, name etc. 
* Redirects to the login page, if not logged in. 
**/
function getUserData(callback) {
	callback = callback || {};
	if(typeof(Storage)!=="undefined")
	{
		var mySession = localStorage.getItem("hb_session");
		var myCredentials = localStorage.getItem("hb_cred");
		if(mySession==undefined || myCredentials==undefined || mySession=="" || myCredentials==""){ 
			window.location = WEB_URL+"login.html"; 
			return;
		}
		
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

function updateUserData(userData, callback) {
	callback = callback || {};
	if(typeof(Storage)!=="undefined")
	{
		var mySession = localStorage.getItem("hb_session");
		var myCredentials = localStorage.getItem("hb_cred");
		if(mySession==undefined || myCredentials==undefined || mySession=="" || myCredentials==""){ 
			window.location = WEB_URL+"login.html"; 
			return;
		}
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
				birthday : encodeURIComponent(userData.birthday)
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

function updatePassword(pw, callback) {
	callback = callback || {};
	if(typeof(Storage)!=="undefined")
	{
		var mySession = localStorage.getItem("hb_session");
		var myCredentials = localStorage.getItem("hb_cred");
		if(mySession==undefined || myCredentials==undefined || mySession=="" || myCredentials==""){ 
			window.location = WEB_URL+"login.html"; 
			return;
		}
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

function updateUserIcon(formData, callback) {
	callback = callback || {};
	var oXHR = new XMLHttpRequest();
	oXHR.addEventListener('load', callback.onSuccess, false);
	oXHR.addEventListener('error', callback.onError, false);
	oXHR.open('POST', API_URL+"Profile");
	oXHR.send(formData);
}

/**
* Gets the records of the user 
* Redirects to the login page, if not logged in. 
**/
function loadRecords(callback) {
	callback = callback || {};
	if(typeof(Storage)!=="undefined")
	{
		var mySession = localStorage.getItem("hb_session");
		var myCredentials = localStorage.getItem("hb_cred");
		if(mySession==undefined || myCredentials==undefined || mySession=="" || myCredentials==""){ 
			window.location = WEB_URL+"login.html"; 
			return;
		}
		
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
* Gets the records of the user 
* Redirects to the login page, if not logged in. 
**/
function loadCircles(callback) {
	callback = callback || {};
	if(typeof(Storage)!=="undefined")
	{
		var mySession = localStorage.getItem("hb_session");
		var myCredentials = localStorage.getItem("hb_cred");
		if(mySession==undefined || myCredentials==undefined || mySession=="" || myCredentials==""){ 
			window.location = WEB_URL+"login.html"; 
			return;
		}
		
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
* Add a new record to the user's chronicle 
* Redirects to the login page, if not logged in. 
**/
function addRecord(name, descr, values, callback) {
	callback = callback || {};
	if(typeof(Storage)!=="undefined")
	{
		var mySession = localStorage.getItem("hb_session");
		var myCredentials = localStorage.getItem("hb_cred");
		if(mySession==undefined || myCredentials==undefined || mySession=="" || myCredentials==""){ 
			window.location = WEB_URL+"login.html"; 
			return;
		}

		if(values==undefined || values.length<1){
			if(callback.onError){
				console.log("calling onError");
		        callback.onError("values entry missing");
		    }
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
* TODO!
* Gets the latest news entries. 
* Redirects to the login page, if not logged in. 
**/
function loadNews(callback) {
	/** Not implemented yet */
}

/**
* Searches for users according to a query 
* Redirects to the login page, if not logged in. 
**/
function queryUsers(query, callback) {
	callback = callback || {};
	if(typeof(Storage)!=="undefined")
	{
		var mySession = localStorage.getItem("hb_session");
		var myCredentials = localStorage.getItem("hb_cred");
		if(mySession==undefined || myCredentials==undefined || mySession=="" || myCredentials==""){ 
			window.location = WEB_URL+"login.html"; 
			return;
		}
		
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
* Searches for entries according to a query 
* Redirects to the login page, if not logged in. 
**/
function queryEntries(query, callback) {
	callback = callback || {};
	if(typeof(Storage)!=="undefined")
	{
		var mySession = localStorage.getItem("hb_session");
		var myCredentials = localStorage.getItem("hb_cred");
		if(mySession==undefined || myCredentials==undefined || mySession=="" || myCredentials==""){ 
			window.location = WEB_URL+"login.html"; 
			return;
		}
		
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

function loadImage(iconName, userId, callback){
	callback = callback || {};
	if(typeof(Storage)!=="undefined")
	{
		var mySession = localStorage.getItem("hb_session");
		var myCredentials = localStorage.getItem("hb_cred");
		if(mySession==undefined || myCredentials==undefined || mySession=="" || myCredentials==""){ 
			window.location = WEB_URL+"login.html"; 
			return;
		}
		
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

function getURLParameter(name) {
    return decodeURIComponent((new RegExp('[?|&]' + name + '=' + '([^&;]+?)(&|#|;|$)').exec(location.search)||[,""])[1].replace(/\+/g, '%20'))||null;
}

function addItemToCircle(circleId, itemId, callback) {
	callback = callback || {};
	if(typeof(Storage)!=="undefined")
	{
		var mySession = localStorage.getItem("hb_session");
		var myCredentials = localStorage.getItem("hb_cred");
		if(mySession==undefined || myCredentials==undefined || mySession=="" || myCredentials==""){ 
			window.location = WEB_URL+"login.html"; 
			return;
		}
		
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