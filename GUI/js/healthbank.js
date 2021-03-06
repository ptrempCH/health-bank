/*!
 * healthbank JavaScript Library v0.1.0
 *
 * Includes jQuery, jQuery-UI, underscore
 *
 * Copyright 2013, Patrick Tremp, ETH Zurich
 * This is not yet released under a licese so all rights reserved.
 *
 * Last Change: 2013-09-21 at 11pm
 */



/**
* Global Variables
*/
var API_URL = "http://localhost:8080/HealthBank/";  // The URL for the API. Change to where your server is running
var WEB_URL = "http://localhost/GUI/www/"; 			// The URL to the root of the webpage. Change to where you put the web files
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
	} else {
		window.location = WEB_URL+"login.html";
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
	initializeActionBox();
	queryHaveNewMessage({ onSuccess: function(data){
		if(data.values.messages.length>0 && data.values.messages[0].hasNew=="true"){
			$("#mailIcon").attr("src", WEB_URL+"../images/newmail.png");
			$("#mailIcon").addClass("mailIconFlash");
			$('#mailIcon').trigger('hover'); 
			$('#mailIcon').prop("title", "You have a new message!");
		}
	}});
	initializeNavigation();
}

/**
* Initialize the action box at the top right of the page
*/
function initializeActionBox () {
	$.get(WEB_URL+'dialogs/page-actionbox.html').success(
		function(data){
			$('.page-actionbox').append(data); 
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
			$("#mailIcon").click(function(){
				window.location = WEB_URL+"messageInbox.html"; 
			});
			s = window.location.pathname;
			if(s.indexOf("space")>0 || s.indexOf("Space")>0 || s.indexOf("News")>0){
				$.each($(".page-actionbox img"), function(i, item){
					$(this).attr("src", "../"+$(this).attr("src"));
				});
			}
			setSearchAnimation();
		}
	);
}

/**
* Initialize the navigation at the left of the page
*/
function initializeNavigation () {
	if(user==undefined){
		setTimeout(function () {
			initializeNavigation();
		});
		return false;
	} 
	$.get(WEB_URL+'dialogs/navigation.html').success(
		function(data){
			$('#nav-container').append(data); 
			if(user.type=="institute" || user.type=="admin"){
				$(".appDevCont").show();
			} else {
				$(".appDevCont").hide();
			}
			s = window.location.pathname;
			if(s.indexOf("app")>0 || s.indexOf("App")>0 || s.indexOf("market")>0 || s.indexOf("devGuide")>0){
				$("#apps_subnavigation").show();
				$("#nav_apps").addClass("selectedLi");
				if(user.type=="institute" || user.type=="admin"){
					$("#nav_community").css("margin-top", "210px");
				} else {
					$("#nav_community").css("margin-top", "90px");
				}
				if(s.indexOf("market")>0){
					$("#apps_subsubnavigation").show();
					$("#nav_myApps").css("margin-top", "90px");
					if(user.type=="institute" || user.type=="admin"){
						$("#nav_community").css("margin-top", "300px");
					} else {
						$("#nav_community").css("margin-top", "180px");
					}
					if(window.location.href.indexOf("viz")>0){
						$("#nav_market_v").addClass("selectedLi");
					} else {
						$("#nav_market_a").addClass("selectedLi");
					}
				} else {
					$("#apps_subsubnavigation").hide();
				}
			} else {
				$("#apps_subnavigation").hide();
			}
			if(s.indexOf("space")>0 || s.indexOf("Space")>0 || s.indexOf("News")>0){
				$.each($("#navivation a"), function(i, item){
					$(this).attr("href", "../"+$(this).attr("href"));
				});
			}
			$("#nav_"+s.substring(s.lastIndexOf("/")+1, s.lastIndexOf("."))).addClass("selectedLi")
		}
	);
}

/**
* Sets the click handler for the hide buttons in the new spaces popup that appears
* when the user clicks on the '+' icon on any of the spaces pages. This needs to be
* set each time, one of the spaces 'hidden' status changes, since the DOM element changes
* with it.
*/
function setHideButtonClickHandlers () {
	$("#currentSpaces .hideSpaceButton").click(function () {
		$(this).html("Show");
		$(this).parent().attr("changed", "true");
		$(this).parent().appendTo("#otherSpaces");
		setHideButtonClickHandlers();
	});
	$("#otherSpaces .hideSpaceButton").click(function () {
		$(this).html("Hide");
		$(this).parent().attr("changed", "true");
		$(this).parent().appendTo("#currentSpaces");
		setHideButtonClickHandlers();
	});
}

/**
* This sets the tabs in the spaces pages according to the user preferences
*/
function setSpacesTabs () {
	if(user==undefined){
		setTimeout(function(){
			setSpacesTabs();
		}, 100);
		return false;
	}
	if(user.spaceTabOrder!=undefined && user.spaceTabOrder.length>0){
		$.each(user.spaceTabOrder.split(" "), function(i, item){
			$.each(spaces, function(j, spa){
				if(spa._id.$oid == item){
					//if(spa.hidden=="false"){ $("#userDefTabs").append('<div id="'+spa._id.$oid+'" class="tab" title="'+spa.descr+'"><a href="'+spa.url+'">'+spa.name+'</a></div>'); }
					if(spa.hidden=="false"){ $("#userDefTabs").append('<div id="tab_'+spa._id.$oid+'" class="tab" title="'+spa.descr+'"><a href="spaceView.html?id='+spa._id.$oid+'">'+spa.name+'</a></div>'); }
					return false;
				}
			});
		});
	} else { 
		if($("#userDefTabs").length>0){
			$.each(spaces, function (i, item) {
				//if(item.hidden=="false"){ $("#userDefTabs").append('<div id="'+item._id.$oid+'" class="tab" title="'+item.descr+'"><a href="'+item.url+'">'+item.name+'</a></div>'); }
				if(item.hidden=="false"){ $("#userDefTabs").append('<div id="tab_'+item._id.$oid+'" class="tab" title="'+item.descr+'"><a href="spaceView.html?id='+item._id.$oid+'">'+item.name+'</a></div>'); }
			});
		}
	}
}

/**
* This sets the visualization iFrame on the spaces pages that define a visualization
*/
function setVizFrame (space_id) {
	if(spaces==undefined){
		setTimeout(function(){
			setVizFrame(space_id);
		}, 100);
		return false;
	}
	$.each(spaces, function(i, item){
		if(item._id.$oid==space_id){
			if(item.visualization!=undefined){
				getApplicationDetail(item.visualization, "viz", {
					onSuccess: function(data){
						vizData = data.values.visualizations[0];
						if(vizData.url!=undefined && vizData.url.length>0){
							$("#vizFrame").attr("src", "../../apps/"+vizData._id.$oid+"/"+(vizData.name.replace(/\s+/g, ''))+"/"+vizData.url);
						} else{ 
							$("#vizFrame").html("There was an error loading the visualization. We could not find the HTML file for the visualization on the server. Please try again later.");
							$("#vizFrame").css("color", "red");
							return false;
						}
					}, onError: function(data){
						$("#vizFrame").html("There was an error loading the visualization. Please try again later.");
						$("#vizFrame").css("color", "red");
					}
				});
				$("#vizFrame").show();
			} else {
				$("#vizFrame").hide();
			}
			return false;
		}
	});
}

/**
* Initializing spaces pages with loading of the circles, spaces and records (only of the current logged in user),
* loading HTML snippets for popups and setting the click listener for the newTabIcon.
*
* Parameters:
* - backurl: the url of the space, we are initializing. This is propagated over several steps to loadLatestRecords() where it is needed for clicks on detail views of records.
* - spacesID: The id of the current space. This is propagated over seceral steps to loadLatestRecords()
* - nrOfItems: The amount of records that shall be displayed in the list. Used by loadLatestRecords().
*/
function initializeSpaces(backurl, spacesID, nrOfItems) {
	loadCircles({onSuccess: function(data){loadedCircles(data);}, onError: function(){console.log("Error while loading circles...");}});
	loadSpaces(
		{onSuccess: function(data){
			loadedSpaces(data);
			setSpacesTabs();
			setVizFrame(spacesID);
		}, onError: function(){
			console.log("Error while loading spaces...");
		}
	});
	loadRecords(spacesID, {onSuccess: function(data){loadLatestRecords(data.values.records, backurl, spacesID, nrOfItems);}, onError: function(){loadRecordsError();}});
	$.get(WEB_URL+'dialogs/circles-form.html').success(function(data){$('body').append(data); loadedExtHTMLForSpaces(backurl, spacesID, nrOfItems);});
	$.get(WEB_URL+'dialogs/spaces-form.html').success(function(data){$('body').append(data); loadedExtHTMLForSpaces(backurl, spacesID, nrOfItems);});
	$.get(WEB_URL+'dialogs/dialog-overlay.html').success(function(data){$('body').append(data); loadedExtHTMLForSpaces(backurl, spacesID, nrOfItems);});
	$.get(WEB_URL+'dialogs/spacePreferences-form.html').success(function(data){$('body').append(data); loadedExtHTMLForSpaces(backurl, spacesID, nrOfItems);});

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
		$("#otherSpaces").contents().remove();
		$("#currentSpaces").append("<li>All Entries</li>");
		if(user!=undefined && user.spaceTabOrder!=undefined){
			$.each(user.spaceTabOrder.split(" "), function(i, item){
				$.each(spaces, function(j, spa){
					if(spa._id.$oid == item){
						if(spa.name=="Medical" || spa.name=="Wellness"){
							if(spa.hidden=="false"){ 
								$("#currentSpaces").append("<li id=\""+spa._id.$oid+"\" title=\""+spa.descr+"\" >"+spa.name+"<button class='button hideSpaceButton'>Hide</button></li>");
							} else {
								$("#otherSpaces").append("<li id=\""+spa._id.$oid+"\" title=\""+spa.descr+"\">"+spa.name+"<button class='button hideSpaceButton'>Show</button></li>");
							}
						} else {
							if(spa.hidden=="false"){ 
								$("#currentSpaces").append("<li id=\""+spa._id.$oid+"\" title=\""+spa.descr+"\" >"+spa.name+"<button class='button hideSpaceButton'>Hide</button><button class='button editSpaceButton'>Edit</button></li>");
							} else {
								$("#otherSpaces").append("<li id=\""+spa._id.$oid+"\" title=\""+spa.descr+"\">"+spa.name+"<button class='button hideSpaceButton'>Show</button><button class='button editSpaceButton'>Edit</button></li>");
							}
						}
						
						return false;
					}
				});
			});
		}
		$.each(spaces, function(i, item){
			found = false;
			if(user!=undefined && user.spaceTabOrder!=undefined){
				$.each(user.spaceTabOrder.split(" "), function(j, spaID){
					if(spaID==item._id.$oid){found=true; return false;}
				});
			}
			if(found==false){
				if(item.name=="Medical" || item.name=="Wellness"){
					if(item.hidden=="true"){
						$("#otherSpaces").append("<li id=\""+item._id.$oid+"\" title=\""+item.descr+"\">"+item.name+"<button class='button hideSpaceButton'>Show</button></li>");
					} else {
						$("#currentSpaces").append("<li id=\""+item._id.$oid+"\" title=\""+item.descr+"\" >"+item.name+"<button class='button hideSpaceButton'>Hide</button></li>");
					}
				} else {
					if(item.hidden=="true"){
						$("#otherSpaces").append("<li id=\""+item._id.$oid+"\" title=\""+item.descr+"\">"+item.name+"<button class='button hideSpaceButton'>Show</button><button class='button editSpaceButton'>Edit</button></li>");
					} else {
						$("#currentSpaces").append("<li id=\""+item._id.$oid+"\" title=\""+item.descr+"\">"+item.name+"<button class='button hideSpaceButton'>Hide</button><button class='button editSpaceButton'>Edit</button></li>");
					}
				}
			}
		});
		$("#currentSpaces").sortable();

		$("#createNewSpaceButton").click(function() {
			window.location = WEB_URL+"spaces/addSpace.html"; 
			return false;
		});
		$(".editSpaceButton").click(function() {
			window.location = WEB_URL+"spaces/addSpace.html?id="+$(this).parent().attr("id");
		});
		$("#saveSpacesTab").click(function(){
			/*
			// 1. Get all items that changed and update the according space entries
			// 2. Get order of shown spaces and store this order in user entry
			// 3. Reload page
			*/

			// 1.
			$.each($("#currentSpaces li"), function(i, item){
				if($(this).attr("id")==undefined){return true;}
				if($(this).attr("changed")=="true"){
					editSpaces($(this).attr("id"), "", "", "false", "", {});
				}
			});
			$.each($("#otherSpaces li"), function(i, item){
				if($(this).attr("id")==undefined){return true;}
				if($(this).attr("changed")=="true"){
					editSpaces($(this).attr("id"), "", "", "true", "", {});
				}
			});

			// 2.
			s = "";
			$.each($("#currentSpaces li"), function(i, item){
			   if($(this).attr("id")!=undefined){
			   		s += $(this).attr("id")+" ";
			   }
			});
			user.spaceTabOrder = s;
			updateUserData(user, {
				onSuccess: function(data){
					// 3.
					window.location.reload(true);
				}, 
				onError: function(data){
					alert("Could not save the ordering of your taps. There was an interal error. Sorry about that.")
				}
			});

			return false;
		});
		setHideButtonClickHandlers();
		return false;
	});
	$("#createRecButton").click(function(){
		window.location = WEB_URL+"myApps.html"; 
		return false;
	});
	$("#filterMyEntry").change(function(){
		if(this.checked) {
			if(!$("#filterEveryone").is(':checked') && !$("#filterIndCircle").is(':checked') && !$("#filterIndPeople").is(':checked')){
				$("#filterTop").html("My Entries<img src=\"../../images/navigate_down.png\" height=\"20px\"/>");
			}
			found = false;
			$.each(records, function(i, item){
				if(item.userID==user._id.$oid){ found = true; return false; }
			});
			if(!found){
				updateFilterPeopleList(user._id.$oid, true, spacesID, backurl); 
			}
		}
		else { 
			updateFilterSelection(spacesID, backurl); 
			updateFilterPeopleList(user._id.$oid, false, spacesID, backurl); 
		}
	});
	$("#filterEveryone").change(function(){
		$.each(spaces, function(i, item){
			if(spacesID==item.name){spacesID=item._id.$oid; return false;}
		});
		if(this.checked) {
			$("#filterTop").html("No Filter<img src=\"../../images/navigate_down.png\" height=\"20px\"/>");
			$("#filterIndCircle").each(function(){ this.checked = false; $("#filterIndCircle").change(); });
			$("#filterIndPeople").each(function(){ this.checked = false; $("#filterIndPeople").change(); });
			$("#filterMyEntry").each(function(){ this.checked = true; $("#filterMyEntry").change(); });
			$("#filterIndPeopleList input").each(function(){ 
				this.checked = false; 
				updateFilterPeopleList(this.id.substring(7), true, spacesID, backurl);
			});
			$("#filterIndCircleList input").each(function(){ this.checked = false; });
			wasEveryone = true;
		}
		else { 
			updateFilterSelection(spacesID, backurl); 
			if(wasEveryone){
				updateFilterPeopleList("", false, spacesID, backurl); 
				wasEveryone=false;
			}
		}
	});
	$("#filterIndCircle").change(function(){
		if(this.checked) {
			$("#filterTop").html("Individual Circles<img src=\"../../images/navigate_down.png\" height=\"20px\"/>");
			$("#filterEveryone").each(function(){ this.checked = false; $("#filterEveryone").change(); });
			$("#filterIndPeople").each(function(){ this.checked = false; $("#filterIndPeople").change(); });
			$("#filterIndPeopleList input").each(function(){ this.checked = false; });
		}
		else { 
			updateFilterSelection(spacesID, backurl); 
			$("#filterIndCircleList input").each(function(){ this.checked = false; });
			updateFilterCircleList("", false, spacesID, backurl);
		}
	});
	$("#filterIndPeople").change(function(){
		if(this.checked) {
			$("#filterTop").html("Individual People<img src=\"../../images/navigate_down.png\" height=\"20px\"/>");
			$("#filterIndCircle").each(function(){ this.checked = false; $("#filterIndCircle").change(); });
			$("#filterEveryone").each(function(){ this.checked = false; $("#filterEveryone").change(); });
			$("#filterIndCircleList input").each(function(){ this.checked = false; });
		}
		else { 
			updateFilterSelection(spacesID, backurl); 
			$("#filterIndPeopleList input").each(function(){ this.checked = false; });
			updateFilterPeopleList("", false, spacesID, backurl);
		}
	});
	$("#filterDropdown li").hover(
			function(){
    			$(this).children('ul').hide();
    			$(this).children('ul').slideDown(100);
			},
			function () {
    			$('ul', this).slideUp(50);            
		});
	$("#filterMyEntry").each(function(){ this.checked = true; });
	initializeDropDown(spacesID, backurl);
}

/**
* Initializes the filter dropdown menu for the spaces pages. 
* attribute space is for the current selected space. The backurl for the url of the space which gets called, when returning from the detailview
*/
function initializeDropDown(space, backurl) {
	if(circles==undefined || spaces==undefined || user==undefined){
		setTimeout(function(){
			initializeDropDown(space, backurl);
		}, 100);
		return false;
	} else {
		$("#filterIndCircleList").html("");
		$("#filterIndPeopleList").html("");
		$.each(circles, function(i, item){
			$("#filterIndCircleList").append("<li><span><label><input id=\"filter_"+item._id.$oid+"\"type=\"checkbox\"/>&nbsp;"+item.name.substring(0,12)+"</label></span></li>");
		});
		$.each(user.haveMeInCircle, function(i, item){
			loadUserById(
				item.userId, 
				{
					onSuccess: function(data, id) {
						var curUser = data.values.users[0];
						if(curUser.firstname && curUser.lastname){
							$("#filterIndPeopleList").append("<li><span><label><input id=\"filter_"+id+"\"type=\"checkbox\"/>&nbsp;"+curUser.firstname.substring(0,1)+". "+curUser.lastname+"</label></span></li>");
						} else if(curUser.companyname){
							$("#filterIndPeopleList").append("<li><span><label><input id=\"filter_"+id+"\"type=\"checkbox\"/>&nbsp;"+curUser.companyname+"</label></span></li>");
						}
					}, onError: function(data){
						$(".detail-name").html("<center>No information found!</center>");
					}
				}
			);
		});

		var mySpaceId = "";
		$.each(spaces, function (i, item) {
			if(item._id.$oid==space){
				mySpaceId = item._id.$oid;
			}
		});

		$("#filterIndCircleList input").change(function(){
			if(this.checked){
				$("#filterIndCircle").each(function(){ this.checked = true; });
				$("#filterIndCircle").change();
				updateFilterCircleList(this.id.substring(7), true, mySpaceId, backurl);
			} else {
				updateFilterCircleList(this.id.substring(7), false, mySpaceId, backurl);
			}
		});

		wasEveryone = false;
	}
	
	setTimeout(function(){
		$("#filterIndPeopleList input").change(function(){
			if(this.checked){
				$("#filterIndPeople").each(function(){ this.checked = true; });
				$("#filterIndPeople").change();
				updateFilterPeopleList(this.id.substring(7), true, mySpaceId, backurl);
			} else {
				updateFilterPeopleList(this.id.substring(7), false, mySpaceId, backurl);
			}
		});
		param = getURLParameter("userId");
		if(param!=undefined){
			$("#filter_"+param).click();
		}
	}, 600);
}

/**
* This function and the following are to react to user input on the filters list
*/
var wasEveryone;
function updateFilterSelection(spacesName, backurl){
	if(!$("#filterEveryone").is(':checked')&& !$("#filterMyEntry").is(':checked') && !$("#filterIndCircle").is(':checked') && !$("#filterIndPeople").is(':checked')){
		$("#filterTop").html("No Filter<img src=\"../../images/navigate_down.png\" height=\"20px\"/>");
		$("#filterMyEntry").each(function(){ this.checked = true; });
		$("#filterEveryone").each(function(){ this.checked = true; });
		$.each(spaces, function(i, item){
			if(spacesName==item.name){spacesName=item._id.$oid; return false;}
		});
		$("#filterIndPeopleList input").each(function(){ 
			this.checked = false; 
			updateFilterPeopleList(this.id.substring(7), true, spacesName, backurl);
		});
		wasEveryone = true;
	} else if(!$("#filterEveryone").is(':checked')&& $("#filterMyEntry").is(':checked') && !$("#filterIndCircle").is(':checked') && !$("#filterIndPeople").is(':checked')){
		$("#filterTop").html("My Entries<img src=\"../../images/navigate_down.png\" height=\"20px\"/>");
	} else if($("#filterEveryone").is(':checked')&& !$("#filterMyEntry").is(':checked') && !$("#filterIndCircle").is(':checked') && !$("#filterIndPeople").is(':checked')){
		$("#filterTop").html("No Filter<img src=\"../../images/navigate_down.png\" height=\"20px\"/>");
		$("#filterMyEntry").each(function(){ this.checked = true; });
		$("#filterEveryone").each(function(){ this.checked = true; });
	}

}

function updateFilterCircleList(id, checked, space, backurl){
	if(checked){ // we need to add the records
		setTimeout(function(){
			$.each(spaces, function(i, item){
				if(space==item.name){space=item._id.$oid; return false;}
				else if(space==item._id.$oid){return false;}
			});
			queryEntriesByCircleId(
				id, space,
				{
					onSuccess: function(data, id) {
						newRecords = data.values.records;
						$.each(newRecords, function(i,item){
							item.indC = id;
						});
						records = records.concat(newRecords);
						loadLatestRecords(records, backurl, space, 0);
					}, onError: function(data){
						//alert("No more data was loaded. Maybe you are not allowed to see any of this user.")
					}
				}
			);
		}, 300);
	} else { // we need to remove the records
		if(id==undefined || id.length<1){
			for (var i = records.length-1; i >= 0; i--) {
			    if (records[i].indC != undefined) {
			        records.splice(i, 1);
			    }
			}
		} else {
			for (var i = records.length-1; i >= 0; i--) {
			    if (records[i].indC == id) {
			        records.splice(i, 1);
			    }
			}
		}
		loadLatestRecords(records, backurl, space, 0);
	}
}

function updateFilterPeopleList(id, checked, space, backurl){
	if(checked){ // we need to add the records
		setTimeout(function(){
			$.each(spaces, function(i, item){
				if(space==item.name){space=item._id.$oid; return false;}
				else if(space==item._id.$oid){return false;}
			});
			if(id==user._id.$oid){
				loadRecords(
					space, 
					{
						onSuccess: function(data){
							records = records.concat(data.values.records);
							loadLatestRecords(records, backurl, space, 0);
						}, onError: function(){
							loadRecordsError();
						}
					}
				);
			} else {
				queryEntriesByUserId(
					id, space,
					{
						onSuccess: function(data, id) {
							newRecords = data.values.records;
							$.each(newRecords, function(i,item){
								item.indP = id;
							});
							records = records.concat(newRecords);
							loadLatestRecords(records, backurl, space, 0);
						}, onError: function(data){
							//alert("No more data was loaded. Maybe you are not allowed to see any of this user.")
						}
					}
				);
			}
		}, 300);
	} else { // we need to remove the records
		if(id==undefined || id.length<1){
			for (var i = records.length-1; i >= 0; i--) {
			    if (records[i].indP != undefined) {
			        records.splice(i, 1);
			    }
			}
		} else {
			for (var i = records.length-1; i >= 0; i--) {
			    if (records[i].userID == id) {
			        records.splice(i, 1);
			    }
			}
		}
		loadLatestRecords(records, backurl, space, 0);
	}
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
		$("#search").css("width", "185px");
		$("#searchIconSpan").css("margin-left", "0px");
		$("#logoutButton").css("margin-right", "0px");
		$("#searchIcon").attr('src',WEB_URL+"../images/search-icon-selected.png");
	});
	$('#search').blur(function() {
		$("#search").css("width", "119px");
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
* Gets the data of the user such as username, name etc. stored as a record for defining its circles
* Redirects to the login page, if not logged in. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
**/
function getProfileRecord(callback){
	if(user==undefined || user.profileRecordId==undefined){return false;}
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"RecordQuery",
			type: 'get',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), id : encodeURIComponent(user.profileRecordId) },
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
* - userData: All the userData to work with. Only existing fields will be transmitted. Possible fields are gender, code, street, city, country, emailP,
* 				emailW, phoneP, phoneM, phoneW, nationality, spouse, insurance, birthday, height, weight, showHelp and allowResearch
*/
function updateUserData(userData, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		if(userData.firstname==undefined){userData.firstname="";}
		if(userData.lastname==undefined){userData.lastname="";}
		if(userData.companyname==undefined){userData.companyname="";}
		if(userData.descr==undefined){userData.descr="";}
		if(userData.gender==undefined){userData.gender="";}
		if(userData.code==undefined){userData.code="";}
		if(userData.street==undefined){userData.street="";}
		if(userData.city==undefined){userData.city="";}
		if(userData.country==undefined){userData.country="";}
		if(userData.emailP==undefined){userData.emailP="";}
		if(userData.emailW==undefined){userData.emailW="";}
		if(userData.phoneP==undefined){userData.phoneP="";}
		if(userData.phoneM==undefined){userData.phoneM="";}
		if(userData.phoneW==undefined){userData.phoneW="";}
		if(userData.nationality==undefined){userData.nationality="";}
		if(userData.spouse==undefined){userData.spouse="";}
		if(userData.insurance==undefined){userData.insurance="";}
		if(userData.birthday==undefined){userData.birthday="";}
		if(userData.height==undefined){userData.height="";}
		if(userData.weight==undefined){userData.weight="";}
		if(userData.allowResearch==undefined){userData.allowResearch="n";}
		if(userData.spaceTabOrder==undefined){userData.spaceTabOrder="";}
		if(userData.showHelp==undefined){userData.showHelp="";}
		
		$.ajax({
			url: API_URL+"Profile",
			type: 'post',
			dataType: 'json',
			data: { 
				session : encodeURIComponent(mySession), 
				credentials : encodeURIComponent(myCredentials), 
				firstname : encodeURIComponent(userData.firstname),
				lastname : encodeURIComponent(userData.lastname),
				companyname : encodeURIComponent(userData.companyname),
				descr : encodeURIComponent(userData.descr),
				gender : encodeURIComponent(userData.gender),
				street : encodeURIComponent(userData.street),
				code : encodeURIComponent(userData.code),
				city : encodeURIComponent(userData.city),
				country : encodeURIComponent(userData.country),
				emailP : encodeURIComponent(userData.emailP),
				emailW : encodeURIComponent(userData.emailW),
				phoneP : encodeURIComponent(userData.phoneP),
				phoneM : encodeURIComponent(userData.phoneM),
				phoneW : encodeURIComponent(userData.phoneW),
				nationality : encodeURIComponent(userData.nationality),
				spouse : encodeURIComponent(userData.spouse),
				insurance : encodeURIComponent(userData.insurance),
				birthday : encodeURIComponent(userData.birthday),
				height : encodeURIComponent(userData.height),
				weight : encodeURIComponent(userData.weight),
				allowResearch : encodeURIComponent(userData.allowResearch),
				spaceTabOrder: encodeURIComponent(userData.spaceTabOrder),
				showHelp: encodeURIComponent(userData.showHelp)
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
	user = data.values.user[0];
	if(user.userIcon!=undefined){
		$("#userInfo").html(decodeURIComponent(user.username));
		loadImage(user.userIcon, "", "user", {onSuccess: function(data){loadUserIcon(data);}});
	} else {
		$("#userInfo").html("Logged in as: "+decodeURIComponent(user.username));
	}
	if(user.showHelp != undefined && user.showHelp.length<7){
		showHelp(user.showHelp);
	}
	if (typeof loadUserInfoIndividual == 'function') { 
		loadUserInfoIndividual(user); 
	}
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
function loadRecords(space_id, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		if(space_id==undefined || space_id.length<1){
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
		} else {
			$.ajax({
				url: API_URL+"Record",
				type: 'get',
				dataType: 'json',
				data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), spaceId : encodeURIComponent(space_id) },
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
function addCircle(name, descr, color, callback){
	callback = callback || {};
	if(name==undefined || name.length<2 || descr==undefined || descr.length<2 || color==undefined || color.length<2){
		return false;
	}
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Circle",
			type: 'post',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), name : encodeURIComponent(name), descr : encodeURIComponent(descr), color: encodeURIComponent(color) },
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
function editCircle(id, name, descr, color, callback){
	callback = callback || {};
	if(name==undefined || name.length<2 || descr==undefined || descr.length<2 || id==undefined || id.length<2 || color==undefined || color.length<2){
		return false;
	}
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Circle",
			type: 'post',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), id : encodeURIComponent(id), name : encodeURIComponent(name), descr : encodeURIComponent(descr), color: encodeURIComponent(color) },
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
* Adds a new space to the user
* Redirects to the login page, if not logged in. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - name: The name of the space to create
* - descr: A description of the space
* - viz: The id of a visualization the user wants display on this space (so far only one visualization is supported)
* - hidden: true or false. If this value is set to true, there will not be a tab for this space on the screen, but the space still exists.
**/
function addSpace(name, descr, hidden, viz, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Space",
			type: 'post',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), name : encodeURIComponent(name), descr : encodeURIComponent(descr), hidden : encodeURIComponent(hidden), visualization : encodeURIComponent(viz) },
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
* Edits a space entry of the user
* Redirects to the login page, if not logged in. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - id: The id of the space to edit
* - name: The name of the space to create
* - descr: A description of the space
* - vizualisation: The id of a visualization the user wants display on this space (so far only one vizualization is supported)
* - hidden: true or false. If this value is set to true, there will not be a tab for this space on the screen, but the space still exists.
**/
function editSpaces(id, name, descr, hidden, viz, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"Space",
			type: 'post',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), name : encodeURIComponent(name), descr : encodeURIComponent(descr), hidden : encodeURIComponent(hidden), visualization : encodeURIComponent(viz), id : encodeURIComponent(id) },
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
* - appID: The id of the application that likes to add this record
* - name: The name of the new record
* - descr: The description of the new record
* - values: Additional values to be saved with this record in JSON data format. Use jsonlint.org to check for correctness  of your JSON
**/
function addRecord(appID, name, descr, values, callback) {
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
				appID : encodeURIComponent(appID),
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
* Add a new record to the another user's chronicle 
* Redirects to the login page, if not logged in. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - appID: The id of the application that likes to add this record
* - name: The name of the new record
* - descr: The description of the new record
* - userID: The id of the other user to add the query to
* - values: Additional values to be saved with this record in JSON data format. Use jsonlint.org to check for correctness  of your JSON
**/
function addPatientRecord(appID, name, descr, userID, values, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		if(values==undefined || values.length<1 || userID==undefined || userID.length<1){
			if(callback.onError){
				console.log("calling onError");
		        callback.onError("values or userID entry missing");
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
				appID : encodeURIComponent(appID),
				name : encodeURIComponent(name), 
				descr : encodeURIComponent(descr),
				values : encodeURIComponent(values),
				userID: encodeURIComponent(userID) 
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
* Add a new record to the user's chronicle including a file
* Redirects to the login page, if not logged in. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - formData: The form data that contains all the information about the new app or visualization to add.
*/
function addRecordWithFile(formData, callback) {
	callback = callback || {};
	var oXHR = new XMLHttpRequest();
	if(callback.onSuccess!=undefined){oXHR.addEventListener('load', callback.onSuccess, false);}
	if(callback.onError!=undefined){oXHR.addEventListener('error', callback.onError, false);}
	oXHR.open('POST', API_URL+"Record");
	oXHR.send(formData);
}

/**
* Add and edit a new news entry 
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
function addEditNews(isEdit, id, title, prev, content, callback) {
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
* Searches for all users which are in any of the current users circles 
* Redirects to the login page, if not logged in. 
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
**/
function queryUsersInCircles(callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"userquery",
			type: 'get',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), circle: encodeURIComponent("true") },
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
				        callback.onSuccess(data, id);
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
* Gets all the records of a certain user given by the according user ID. Only records we are allowed to see are returned 
* Redirects to the login page, if not logged in. 
* 
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - userId: The id of the user to search records from
* - spaceId: The id of a space, if set only records assigned to this space are returned
**/
function queryEntriesByUserId(userId, spaceId, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		if(spaceId== undefined || spaceId.length<1){
			$.ajax({
				url: API_URL+"recordquery",
				type: 'get',
				dataType: 'json',
				data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), userId: encodeURIComponent(userId) },
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
					        callback.onSuccess(data, userId);
					    }
					}
				}
			});
		} else {
			$.ajax({
				url: API_URL+"recordquery",
				type: 'get',
				dataType: 'json',
				data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), userId: encodeURIComponent(userId), spaceId : encodeURIComponent(spaceId) },
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
					        callback.onSuccess(data, userId);
					    }
					}
				}
			});
		}
	}
}

/**
* Gets all the records of users in a certain circle given by the according circle ID. Only records we are allowed to see are returned  
* Redirects to the login page, if not logged in. 
* 
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - circleId: The id of the circe to search records from the users in it
* - spaceId: The id of a space, if set only records assigned to this space are returned
**/
function queryEntriesByCircleId(circleId, spaceId, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		if(spaceId== undefined || spaceId.length<1){
			$.ajax({
				url: API_URL+"recordquery",
				type: 'get',
				dataType: 'json',
				data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), circleId: encodeURIComponent(circleId) },
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
					        callback.onSuccess(data, circleId);
					    }
					}
				}
			});
		} else {
			$.ajax({
				url: API_URL+"recordquery",
				type: 'get',
				dataType: 'json',
				data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), circleId: encodeURIComponent(circleId), spaceId : encodeURIComponent(spaceId) },
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
					        callback.onSuccess(data, circleId);
					    }
					}
				}
			});
		}
	}
}


/**
* Helper function to load an user or application icon from the server
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - icon: The name or id of the icon to load from the server
* - type: The type of the image you would like to receive. Use 'application' for an app icon and 'user' for a user icon
* - userId: the user id that belongs to the user we are loading the image from
*/
function loadImage(icon, userId, type, callback){
	callback = callback || {};
	if(isStorageDefined())
	{
		if(icon.$oid!=undefined){
			$.ajax({
				url: API_URL+"Image",
				type: 'get',
				data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), type: encodeURIComponent(type), id : encodeURIComponent(icon.$oid)},
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
				},
				error: function (request, status, error) {
			        alert(JSON.parse(request.responseText));
			    }
			});
		} else {
			$.ajax({
				url: API_URL+"Image",
				type: 'get',
				data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), type: encodeURIComponent(type), name : (icon!=undefined)?encodeURIComponent(icon):encodeURIComponent("defaultUserIcon")},
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
				},
				error: function (request, status, error) {
			        alert(JSON.parse(request.responseText));
			    }
			});
		}
	}
}

/**
* Helper function to load a file from the server
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - id: The id of the file to load
*/
function loadFile(id, callback){
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"File",
			type: 'get',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), id : encodeURIComponent(id)},
			success: function(data){
				if(callback.onSuccess){
			        callback.onSuccess(data);
			    }
			},
			error: function (request, status, error) {
		        alert(JSON.parse(request.responseText));
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
* - spaces_id: The id of the current space. This is propagated over seceral steps to loadLatestRecords()
* - nrOfItems: The amount of records that shall be displayed in the list. Used by loadLatestRecords().
* - recordId: The id of the record we want to save the spaces to 
*/
function saveSpacesOfRecord(recordId, backlink, space_id, nrOfItems){
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
					loadRecords(space_id, {onSuccess: function(data){loadLatestRecords(data.values.records, backlink, space_id, nrOfItems);}, onError: function(){loadRecordsError();}});
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
* - spaces_id: The id of the current space. This is propagated over seceral steps to loadLatestRecords()
* - nrOfItems: The amount of records that shall be displayed in the list. Used by loadLatestRecords().
* - recordId: The id of the record we want to save the circles to 
*/
function saveCirclesOfRecord(recordId, backlink, space_id, nrOfItems){
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
				loadRecords(space_id, {onSuccess: function(data){loadLatestRecords(data.values.records, backlink, space_id, nrOfItems);}, onError: function(){loadRecordsError();}});
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
* This function sorts the records entries according to its timedate value. After the sorting, the first entry in the 
* records array is the latest record of all in the list and the last element the oldest one.
*/
function sortRecords () {
	records = records.sort(function(a, b) {
        return (moment(a.timedate).isBefore(b.timedate)) ? 1 : ((moment(a.timedate).isAfter(b.timedate))? -1 : 0);
    });
}

/**
* This function loads the latest records of the current logged in user and initialices all kind of click listener
*
* Parameters:
* - backurl: the url of the space, we are initializing. 
* - spaces_id: The id of the current space. 
* - nrOfItems: The amount of records that shall be displayed in the list.
* - recordId: The id of the record we want to save the spaces to 
*/
function loadLatestRecords(data, backlink, space_id, nrOfItems) {
	if(spaces==undefined){
		setTimeout(function(){loadLatestRecords(data, backlink, space_id, nrOfItems);}, 100);
		return false;
	}
	$("#recordentries").html("");
	if(data==undefined || data.length==0){
		$("#recordentries").append("<center>No entries yet!</center>");
		return false;
	}
	records = data;
	sortRecords();

	if(space_id==undefined || space_id.length<1){
		$.each(records, function(i, item) {
			if(item.app==undefined || item.app!="profile"){
				if(item.indP!=undefined && item.indP.length>0){
					$("#recordentries").append('<li class="indP"><p class="'+item.indP+'">Other User:</p> <h3 class="recordlist-itemheader">'+decodeURIComponent(item.name)+'</h3><i>Created at: '
						+item.timedate+'</i><button value="'+item._id.$oid+'" title="Assign this record to a space" class=\"list-button spaceButton\">Spaces</button></li>');
					queryUserById(item.indP, {
						onSuccess: function(data, id){
							if(data.values.users[0].firstname!=undefined){
								$("."+id).html("User: <i>"+data.values.users[0].firstname + " " + data.values.users[0].lastname+"</i>");
							} else {
								$("."+id).html("User: <i>"+data.values.users[0].companyname + "</i>");
							}
						}
					});
				} else if(item.indC!=undefined && item.indC.length>0) {
					indC = item.indC;
					color = "";
					$.each(circles, function (j, cir) {
						if(cir._id.$oid==indC){color = cir.color;}
					});
					if(color.length>0){
						$("#recordentries").append('<li><p class="'+item.userID+'" style="background-color:'+color+' !important">Other User:</p> <h3 class="recordlist-itemheader">'+decodeURIComponent(item.name)+'</h3><i>Created at: '
							+item.timedate+'</i><button value="'+item._id.$oid+'" title="Assign this record to a space" class=\"list-button spaceButton\">Spaces</button></li>');
					} else {
						$("#recordentries").append('<li><p class="'+item.userID+'">Other User:</p> <h3 class="recordlist-itemheader">'+decodeURIComponent(item.name)+'</h3><i>Created at: '
							+item.timedate+'</i><button value="'+item._id.$oid+'" title="Assign this record to a space" class=\"list-button spaceButton\">Spaces</button></li>');
					}
					queryUserById(item.userID, {
						onSuccess: function(data, id){
							if(data.values.users[0].firstname!=undefined){
								$("."+item.userID).html("User: <i>"+data.values.users[0].firstname + " " + data.values.users[0].lastname+"</i>");
							} else {
								$("."+item.userID).html("User: <i>"+data.values.users[0].companyname + "</i>");
							}
						}
					});
				} else { 
					$("#recordentries").append('<li><h3 class="recordlist-itemheader">'+decodeURIComponent(item.name)+'</h3><i>Created at: '+item.timedate+'</i><button value="'+item._id.$oid
						+'" title="Assign this record to a space" class=\"list-button spaceButton\">Spaces</button><button title="Assign this record to a circle" value="'+item._id.$oid+'" class=\"list-button circleButton\">Circles</button></li>');
				}
			    if(nrOfItems>0 && i==(nrOfItems-1)){
			    	return false;
			    }
			}
		});
	} else {
		var spaceID = "";
		$.each(spaces, function(i, item){
			if(item.name==space_id){ spaceID = item._id.$oid;}
			else if(item._id.$oid == space_id) { spaceID = space_id;}
		});
		$.each(records, function(i, item) {
			if(item.app==undefined || item.app!="profile"){
				var found = false;
				if(item.spaces!=undefined){
					$.each(item.spaces, function(j, c){
						if(c == spaceID){ found = true;}
					});
					if(found){
						if(item.indP!=undefined && item.indP.length>0){
							$("#recordentries").append('<li class="indP"><p class="'+item.indP+'">Other User:</p> <h3 class="recordlist-itemheader">'+decodeURIComponent(item.name)+'</h3><i>Created at: '
								+item.timedate+'</i><button value="'+item._id.$oid+'" class=\"list-button spaceButton\">Spaces</button></li>');
							queryUserById(item.indP, {
								onSuccess: function(data, id){
									if(data.values.users[0].firstname!=undefined){
										$("."+id).html("User: <i>"+data.values.users[0].firstname + " " + data.values.users[0].lastname+"</i>");
									} else {
										$("."+id).html("User: <i>"+data.values.users[0].companyname + "</i>");
									}
								}
							});
						} else if(item.indC!=undefined && item.indC.length>0) {
							indC = item.indC;
							color = "";
							$.each(circles, function (j, cir) {
								if(cir._id.$oid==indC){color = cir.color;}
							});
							if(color.length>0){
								$("#recordentries").append('<li><p class="'+item.userID+'" style="background-color:'+color+' !important">Other User:</p> <h3 class="recordlist-itemheader">'+decodeURIComponent(item.name)+'</h3><i>Created at: '
									+item.timedate+'</i><button value="'+item._id.$oid+'" class=\"list-button spaceButton\">Spaces</button></li>');
							} else {
								$("#recordentries").append('<li><p class="'+item.userID+'">Other User:</p> <h3 class="recordlist-itemheader">'+decodeURIComponent(item.name)+'</h3><i>Created at: '
									+item.timedate+'</i><button value="'+item._id.$oid+'" class=\"list-button spaceButton\">Spaces</button></li>');
							}
							queryUserById(item.userID, {
								onSuccess: function(data, id){
									if(data.values.users[0].firstname!=undefined){
										$("."+item.userID).html("User: <i>"+data.values.users[0].firstname + " " + data.values.users[0].lastname+"</i>");
									} else {
										$("."+item.userID).html("User: <i>"+data.values.users[0].companyname + "</i>");
									}
								}
							});
						} else {
							$("#recordentries").append('<li><h3 class="recordlist-itemheader">'+decodeURIComponent(item.name)+'</h3><i>Created at: '+item.timedate+'</i><button value="'+item._id.$oid
								+'" class=\"list-button spaceButton\">Spaces</button><button value="'+item._id.$oid+'" class=\"list-button circleButton\">Circles</button></li>');
						}
					    if(nrOfItems>0 && i==(nrOfItems-1)){
					    	return false;
					    }
					}
				}
			}
		});			
		if($("#recordentries").html()==""){
			$("#recordentries").html("No entries yet. Please assign records to this space first in order to see them here.");
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
						$("#"+c.circle).prop('checked', true);
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
				        callback.onSuccess(data, id);
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

/**
* This function sends a new message to a user
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - recipientID: The id of the recipient of the message
* - message: The message body
* - subject: The subject or header of the message
*/
function sendMessage(recipientID, subject, message, callback){
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"message",
			type: 'post',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), recipient: encodeURIComponent(recipientID), subject: encodeURIComponent(subject), message: encodeURIComponent(message) },
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
* This function queries all the recieved messages from the server
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
*/
function getReceivedMessages(callback){
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"message",
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
* This function queries a single message from the server given an id
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - id: The id of the message to query
*/
function getMessageById(id, callback){
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"message",
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
* This function queries the server to check if the current user has new messages
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
*/
function queryHaveNewMessage(callback){
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"message",
			type: 'get',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), hasNew: encodeURIComponent("true") },
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
* This function sends the form data to add a new application or visualization to the server.
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - formData: The form data that contains all the information about the new app or visualization to add.
*/
function addNewApplication(formData, callback) {
	callback = callback || {};
	var oXHR = new XMLHttpRequest();
	if(callback.onSuccess!=undefined){oXHR.addEventListener('load', callback.onSuccess, false);}
	if(callback.onError!=undefined){oXHR.addEventListener('error', callback.onError, false);}
	oXHR.open('POST', API_URL+"application");
	oXHR.send(formData);
}

/**
* This function sends the form data to edit an existing application or visualization to the server.
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - formData: The form data that contains all the information about the application or visualization to edit.
*/
function editExistingApplication(formData, callback) {
	callback = callback || {};
	var oXHR = new XMLHttpRequest();
	if(callback.onSuccess!=undefined){oXHR.addEventListener('load', callback.onSuccess, false);}
	if(callback.onError!=undefined){oXHR.addEventListener('error', callback.onError, false);}
	oXHR.open('POST', API_URL+"application");
	oXHR.send(formData);
}

/**
* This function queries for details about a single application or visualization
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - id: The id of the application or visualization to query details about
* - type: The type you want to query. Either 'app' for applications or 'viz' for visualizations
*/
function getApplicationDetail(id, type, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"appquery",
			type: 'get',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), id: encodeURIComponent(id), type: encodeURIComponent(type) },
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
* This function queries for applications and visualizations according to a query word
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - query: The query to search applications for
* - type: The type you want to query. Either 'app' for applications or 'viz' for visualizations
*/
function getApplicationByQuery(query, type, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"appquery",
			type: 'get',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), query: encodeURIComponent(query), type: encodeURIComponent(type) },
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
* This function queries for all the applications or visualizations the current user has installed already
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - type: The type you want to query. Either 'app' for applications or 'viz' for visualizations
*/
function getInstalledApplications(type, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"appquery",
			type: 'get',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), type: encodeURIComponent(type) },
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
* @DEPRECATED, DO NOT USE ANYMORE
* This function queries for all the applications or visualizations the current user has created and added to the system already
* If the current user is a standard user, this will return all the applicatio and visualizations that are present on the system so far.
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
*/
/*function getUploadedOrInstalledApplications(callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"application",
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
}*/

/**
* This will return all the applications and visualizations that are present on the system so far.
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
*/
function getAllApplications(callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"application",
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
* This function queries for all the applications or visualizations the current user has created and added to the system already provided it is an institute user
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
*/
function getUploadedApplications(callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"application",
			type: 'get',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), uploaded : encodeURIComponent("true") },
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
* This function allows to install and uninstall applications and visualizations.
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - id: The id of the applications or visualizations to install or uninstall
*/
function inAndUninstallApplication(id, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"appinstall",
			type: 'post',
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
* This function allows to assign retrieve information about the app install such as assigned spaces and circles.
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - id: The id of the applications or visualizations to get install information
*/
function getAppInstallInformation(id, callback) {
	callback = callback || {};
	if(id==undefined || id.length==0){
		return false;
	}
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"appinstall",
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
* This function allows to assign spaces to an application so that in future every record entry, which is added by this app, is assigned to this space.
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - id: The id of the applications or visualizations to update
* - spaces: A list of selected space ID's separated by a whitespace
*/
function updateSpacesForApp(id, spacesList, callback) {
	callback = callback || {};
	if(spacesList==undefined || id==undefined || id.length==0){
		return false;
	}
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"appinstall",
			type: 'post',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), id: encodeURIComponent(id), spaces: encodeURIComponent(spacesList) },
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
* This function allows to assign circles to an application so that in future every record entry, which is added by this app, is assigned to this circle.
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - id: The id of the applications or visualizations to update
* - circles: A list of selected circle ID's separated by a whitespace
*/
function updateCirclesForApp(id, circlesList, callback) {
	callback = callback || {};
	if(circlesList==undefined || id==undefined || id.length==0){
		return false;
	}
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"appinstall",
			type: 'post',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), id: encodeURIComponent(id), circles: encodeURIComponent(circlesList) },
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
* This function allows to get a new app secret provided the logged in user is the owner of the app.
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - id: The id of the applications or visualizations to update the secret
* - secret: The current secret of the application/visualization
*/
function refreshAppSecret(id, secret, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		$.ajax({
			url: API_URL+"application",
			type: 'post',
			dataType: 'json',
			data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), id: encodeURIComponent(id), secret: encodeURIComponent(secret) },
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
* This function allows to call the query engine for institute users. 
* There are three ways of calling this function. For searching users, provide at least one of the parameters minAge, maxAge, minWeight, maxWeight, minHeight,
* maxHeight, country or keywords. For searching records, set the keywords parameter and for text search set the parameter query. With the type parameter
* we can distinguish the type of call. Allowed are 'user', 'record' and 'text'. Just set the other parameters to null/undefined or empty string.
* Only users and records of users who allowed research access are returned.
*
* Parameters:
* - callback: If desired the caller can provide functions onSuccess and onError via this callback, which will be called after the AJAX request returns
* - type: The type of the call. There are the three possibilities 'user', 'record', 'text'
* - minAge: (optional for user search) The minimum age of the users to search for
* - maxAge: (optional for user search) The maximum age of the users to search for
* - minWeight: (optional for user search) The minimum weight of the users to search for
* - maxWeight: (optional for user search) The maximum weight of the users to search for
* - minHeight: (optional for user search) The minimum height of the users to search for
* - maxHeight: (optional for user search) The maximum height of the users to search for
* - country: (optional for user search) The country the user to search for is from
* - keywords: (optional for users search, needed for record search) Keywords the records have to talk about
* - query: (for text search only) The query to search for
*/
function queryQueryEngine(type, minAge, maxAge, minHeight, maxHeight, minWeight, maxWeight, country, keywords, query, callback) {
	callback = callback || {};
	if(isStorageDefined())
	{
		if(type=="user"){
			$.ajax({
				url: API_URL+"queryengine",
				type: 'get',
				dataType: 'json',
				data: { 
					session : encodeURIComponent(mySession), 
					credentials : encodeURIComponent(myCredentials), 
					type: encodeURIComponent(type), 
					minAge: encodeURIComponent(minAge), 
					maxAge: encodeURIComponent(maxAge), 
					minWeight: encodeURIComponent(minWeight), 
					maxWeight: encodeURIComponent(maxWeight), 
					minHeight: encodeURIComponent(minHeight), 
					maxHeight: encodeURIComponent(maxHeight), 
					country: encodeURIComponent(country), 
					keywords: encodeURIComponent(keywords)
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
		} else if(type=="record"){
			$.ajax({
				url: API_URL+"queryengine",
				type: 'get',
				dataType: 'json',
				data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), keywords: encodeURIComponent(keywords), type: encodeURIComponent(type) },
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
		} else if(type=="text"){
			$.ajax({
				url: API_URL+"queryengine",
				type: 'get',
				dataType: 'json',
				data: { session : encodeURIComponent(mySession), credentials : encodeURIComponent(myCredentials), query: encodeURIComponent(query), type: encodeURIComponent(type) },
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
			if(callback.onError){
				console.log("calling onError: Wrong type argument");
		        callback.onError();
		    }
			return false;
		}
	}
}

/**
* This function will show help pop up dialogs when the user visists a page for the first time.
*
* Parameters:
* - index: a string of numbers representing what type of help messages the user has already seen. e.g. '123'
*/
function showHelp(index){
	pathname = $(location).attr('pathname');
	dialog = "", number = "";
	if(pathname.indexOf("home")>0){
		if(index.indexOf("1")==-1){
			dialog = 'dialogs/helpdialog-home.html';
			number = "1";
		}
	} else if(pathname.indexOf("spaces")>0){
		if(index.indexOf("2")==-1){
			dialog = 'dialogs/helpdialog-spaces.html';
			number = "2";
		}
	} else if(pathname.indexOf("circles")>0){
		if(index.indexOf("3")==-1){
			dialog = 'dialogs/helpdialog-circles.html';
			number = "3";
		}		
	} else if(pathname.indexOf("apps")>0){
		if(index.indexOf("4")==-1){
			dialog = 'dialogs/helpdialog-market.html';
			number = "4";
		}
	} else if(pathname.indexOf("messageInbox")>0){
		if(index.indexOf("5")==-1){
			dialog = 'dialogs/helpdialog-message.html';
			number = "5";
		}
	} else if(pathname.indexOf("profile")>0){
		if(index.indexOf("6")==-1){
			dialog = 'dialogs/helpdialog-profile.html';
			number = "6";
		}
	}
	if(dialog!=""){
		$.get(WEB_URL+dialog).success(
			function(data){
				$('body').append(data);
				$( "#dialog-help" ).dialog({
			      modal: true,
			      autoOpen: true,
			      width: 800, 
			      height: 600,
			      buttons: {
			        Ok: function() {
			          $( this ).dialog( "close" );
			        }
			      }
			    });
			}
		);
	}
	if(number!=""){
		index += number;
		user.showHelp = index;
		updateUserData(user, {});
	}
}


/**
* List of Countries for Profile etc.
*/
var availableTags = [
  	"Afghanistan",
	"Albania",
	"Algeria",
	"Andorra",
	"Angola",
	"Antigua &amp; Deps",
	"Argentina",
	"Armenia",
	"Australia",
	"Austria",
	"Azerbaijan",
	"Bahamas",
	"Bahrain",
	"Bangladesh",
	"Barbados",
	"Belarus",
	"Belgium",
	"Belize",
	"Benin",
	"Bhutan",
	"Bolivia",
	"Bosnia Herzegovina",
	"Botswana",
	"Brazil",
	"Brunei",
	"Bulgaria",
	"Burkina",
	"Burundi",
	"Cambodia",
	"Cameroon",
	"Canada",
	"Cape Verde",
	"Central African Rep",
	"Chad",
	"Chile",
	"China",
	"Colombia",
	"Comoros",
	"Congo",
	"Congo {Democratic Rep}",
	"Costa Rica",
	"Croatia",
	"Cuba",
	"Cyprus",
	"Czech Republic",
	"Denmark",
	"Djibouti",
	"Dominica",
	"Dominican Republic",
	"Timor Leste",
	"Ecuador",
	"Egypt",
	"El Salvador",
	"Equatorial Guinea",
	"Eritrea",
	"Estonia",
	"Ethiopia",
	"Fiji",
	"Finland",
	"France",
	"Gabon",
	"Gambia",
	"Georgia",
	"Germany",
	"Ghana",
	"Greece",
	"Grenada",
	"Guatemala",
	"Guinea",
	"Guinea-Bissau",
	"Guyana",
	"Haiti",
	"Honduras",
	"Hungary",
	"Iceland",
	"India",
	"Indonesia",
	"Iran",
	"Iraq",
	"Ireland {Republic}",
	"Israel",
	"Italy",
	"Ivory Coast",
	"Jamaica",
	"Japan",
	"Jordan",
	"Kazakhstan",
	"Kenya",
	"Kiribati",
	"Korea North",
	"Korea South",
	"Kuwait",
	"Kyrgyzstan",
	"Laos",
	"Latvia",
	"Lebanon",
	"Lesotho",
	"Liberia",
	"Libya",
	"Liechtenstein",
	"Lithuania",
	"Luxembourg",
	"Macedonia",
	"Madagascar",
	"Malawi",
	"Malaysia",
	"Maldives",
	"Mali",
	"Malta",
	"Marshall Islands",
	"Mauritania",
	"Mauritius",
	"Mexico",
	"Micronesia",
	"Moldova",
	"Monaco",
	"Mongolia",
	"Montenegro",
	"Morocco",
	"Mozambique",
	"Myanmar, {Burma}",
	"Namibia",
	"Nauru",
	"Nepal",
	"Netherlands",
	"New Zealand",
	"Nicaragua",
	"Niger",
	"Nigeria",
	"Norway",
	"Oman",
	"Pakistan",
	"Palau",
	"Panama",
	"Papua New Guinea",
	"Paraguay",
	"Peru",
	"Philippines",
	"Poland",
	"Portugal",
	"Qatar",
	"Romania",
	"Russian Federation",
	"Rwanda",
	"St Kitts &amp; Nevis",
	"St Lucia",
	"Saint Vincent &amp; the Grenadines",
	"Samoa",
	"San Marino",
	"Sao Tome &amp; Principe",
	"Saudi Arabia",
	"Senegal",
	"Serbia",
	"Seychelles",
	"Sierra Leone",
	"Singapore",
	"Slovakia",
	"Slovenia",
	"Solomon Islands",
	"Somalia",
	"South Africa",
	"Spain",
	"Sri Lanka",
	"Sudan",
	"Suriname",
	"Swaziland",
	"Sweden",
	"Switzerland",
	"Syria",
	"Taiwan",
	"Tajikistan",
	"Tanzania",
	"Thailand",
	"Togo",
	"Tonga",
	"Trinidad &amp; Tobago",
	"Tunisia",
	"Turkey",
	"Turkmenistan",
	"Tuvalu",
	"Uganda",
	"Ukraine",
	"United Arab Emirates",
	"United Kingdom",
	"United States of America",
	"Uruguay",
	"Uzbekistan",
	"Vanuatu",
	"Vatican City",
	"Venezuela",
	"Vietnam",
	"Yemen",
	"Zambia",
	"Zimbabwe",
];


/**
* _____________________________________________________________________________________________________________________________________________________________
*/

/**
 * Addons to jQuery by other Authors
 */

 // Array Remove - By John Resig (MIT Licensed)
Array.prototype.remove = function(from, to) {
  var rest = this.slice((to || from) + 1 || this.length);
  this.length = from < 0 ? this.length + from : from;
  return this.push.apply(this, rest);
};


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
