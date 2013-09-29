var bmrSet;

function initButtons(){
	$("#cancelButton").click(function(){
		window.top.location = parent.WEB_URL+"myApps.html";
		return false;
	});
	$("#submitButton").click(function(){
		if($("#mealtype").val()=="sports"){
			addCalEntry("s");
		} else {
			addBMREntry();
		}
		return false;
	});
	$("#timelineButton").click(function(){
		window.top.location = parent.WEB_URL+"spaces/spaces.html";
		return false;
	});
	$(".page-content .calApp-addnew-box .calApp-form div label").autoWidth();
	$("#mealtype").change(function(e){
		if(this.value=="sports"){
			$("#cal").parent().show();
			$("#descr").parent().show();
			$("#gender").parent().hide();
			$("#age").parent().hide();
			$("#weight").parent().hide();
			$("#activity").parent().hide();
			$("#tall").parent().hide();
		} else {
			if(bmrSet){
				$("#calApp-feedback").html("You can add your BMR only once a day!");
				$("#calApp-feedback").css("color", "red");
				$("#mealtype").val("sports");
				return false;
			} 
			if($("#gender").parent().is(":hidden")){
				$("#cal").parent().hide();
				$("#descr").parent().hide();
				if(parent.user.gender==undefined || parent.user.gender.length==0){$("#gender").parent().show();}
				if(parent.user.birthday==undefined || parent.user.birthday.length==0){$("#age").parent().show();}
				$("#weight").parent().show();
				$("#activity").parent().show();
				$("#tall").parent().show();
			}
		}
		$(".page-content .calApp-addnew-box .calApp-form div label").autoWidth();
	});
}

function setValues(){
	if(parent.user!=undefined){
		if(parent.user.birthday!=undefined) {
			age = moment(parent.user.birthday, "DD-MM-YYYY").month(0).from(moment().month(0))
			$("#age").val(parseInt(age.substring(0, age.indexOf(" "))));
		}
		if(parent.user.weight!=undefined) {
			$("#weight").val(parent.user.weight);
		}
		if(parent.user.height!=undefined){
			$("#tall").val(parent.user.height);
		}
	}
}

function checkBMR() {
	parent.loadRecords("", {onSuccess: function(data){checkBMRSucc(data);}})
}

function checkBMRSucc(data){
	bmrSet = false;
	if(data.values.records!=undefined && data.values.records.size!=0){
		$.each(data.values.records, function(i, item) {
			if(item.app=="caloriesApp" && item.mealtype=="sports"){
				var date = new Date(item.timedate);
				if(moment().isSame(date, 'day')){
					if(item.name.indexOf("BMR") !== -1){bmrSet = true;}
					return false;
				}
			}
		});
	}

	$("#calApp-addnew-box .label").css("width", "150px");
	if(bmrSet){
		$("#mealtype").val("sports");
		$("#gender").parent().hide();
		$("#age").parent().hide();
		$("#weight").parent().hide();
		$("#activity").parent().hide();
		$("#tall").parent().hide();
	} else {
		$("#cal").parent().hide();
		$("#descr").parent().hide();
	}
	if(parent.user.gender=="mr"){
		$("#gender").val("m");
		$("#gender").parent().hide();
	} else if(parent.user.gender=="mrs"){
		$("#gender").val("f");
		$("#gender").parent().hide();
	}
	if(parent.user.birthday!=undefined){
		$("#age").parent().hide();
	}
	$(".page-content .calApp-addnew-box .calApp-form div label").autoWidth();
	$("#calApp-feedback").html("You can set your weight and height to be used here in your <a href=\"\" id=\"profileButton\">profile</a>!");
	$("#profileButton").click(function(){
		window.top.location = parent.WEB_URL+"profile.html";
	});
}
function addCalEntry(type){
	var mealtype = $("#mealtype").val();
	var descr = $("#descr").val();
	var amount = $("#amount").val();
	var cal = $("#cal").val();
	var comment = $("#comment").val();

	if(mealtype==undefined || descr==undefined || descr.length<1){
		$("#calApp-feedback").html("Please fill out all the form elements with a (*)!");
		$("#calApp-feedback").css("color", "red");
		return;
	}
	if(mealtype != "sports" && (amount==undefined || amount.length<1)){
		$("#calApp-feedback").html("Please fill out all the form elements with a (*)!");
		$("#calApp-feedback").css("color", "red");
		return;
	}
	if((cal!=undefined && isNaN(parseInt(cal))) || (amount!=undefined && isNaN(parseInt(amount)))){
		$("#calApp-feedback").html("Please add numbers to the amount and calaries fields!");
		$("#calApp-feedback").css("color", "red");
		return;
	}
	
	var values = "{ \"app\":\"caloriesApp\", \"mealtype\":\""+encodeURIComponent(mealtype)+"\"";
	if(mealtype!="sports"){
		values = values + ", \"amount\":\""+encodeURIComponent(amount)+"\"";
		if(cal!=undefined && cal.length>0){
			cal = parseInt(cal)*parseInt(amount);
			values = values + ", \"cal\":\""+encodeURIComponent(cal)+"\"";
		}
	} else{
		if(cal!=undefined && cal.length>0){
			values = values + ", \"cal\":\""+encodeURIComponent(cal)+"\"";
		}
	}
	if(comment!=undefined && comment.length>0) {
		values = values + ", \"comment\":\""+encodeURIComponent(comment)+"\"";
	}
	values = values + " }";

	parent.addRecord(parent.id, encodeURIComponent(descr), "Calories App Entry", values, {onSuccess: function(data){addCalEntrySucc(data, type);}, onError: function(message){addCalEntryErr(message);}});
}

function addCalEntrySucc(data, type) {
	$("#calApp-feedback").html("Added entry successfull. Click the Timeline button to see the value in the Spaces view.");
	$("#calApp-feedback").css("color", "green");
	checkBMR();
}
function addCalEntryErr(message) {
	$("#calApp-feedback").html(message);
	$("#calApp-feedback").css("color", "red");
}

function addBMREntry() {
	var age = $("#age").val();
	var weight = $("#weight").val();
	var tall = $("#tall").val();
	var activity = $("#activity").val();
	var gender = $("#gender").val();
	if(gender==undefined || activity==undefined || age==undefined || isNaN(age) || weight==undefined || isNaN(weight) || tall==undefined || isNaN(tall) ){
		$("#calApp-bmrfeedback").html("Please fill out all the form elements with a (*)!");
		$("#calApp-bmrfeedback").css("color", "red");
		return;
	}
	// calculate bmr accoring to revised harris-benedict equation -> http://en.wikipedia.org/wiki/Harris-Benedict_equation
	var bmr;
	if(gender="m"){
		bmr = 88.362 + (13.397 * weight) + (4.799 * tall) - (5.677 * age);
	} else {
		bmr = 447.593 + (9.247 * weight) + (3.098 * tall) - (4.330 * age);
	}
	switch(activity){
		case "little":
			bmr = bmr * 1.2;
			break;
		case "light":
			bmr = bmr * 1.375;
			break;
		case "moderate":
			bmr = bmr * 1.55;
			break;
		case "heavy":
			bmr = bmr * 1.725;
			break;
		case "veryHeavy":
			bmr = bmr * 1.9;
			break;
		default:
			break;
	}
	bmr = Math.round(bmr);
	var values = "{ \"app\":\"caloriesApp\", \"mealtype\":\""+encodeURIComponent("sports")+"\", ";
	values += "\"age\":\""+encodeURIComponent(age)+"\", \"weight\":\""+encodeURIComponent(weight)+"\", \"height\":\""+encodeURIComponent(tall)+"\", \"cal\":\""+encodeURIComponent(bmr)+"\"}";

	parent.addRecord(parent.id, "Daily BMR", "Calories App Entry", values, {onSuccess: function(data){addCalEntrySucc(data, "s");}, onError: function(message){addCalEntryErr(message);}});
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