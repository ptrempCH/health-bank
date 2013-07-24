var entries, entriesShown, caloriesData, sportsData, days, bmrSet;

function loadTodayCalItems(){
	loadRecords("Calories", {onSuccess: function(data){loadTodayCalItemsSucc(data);}, onError: function(){loadCalItemsErr();}});
}
function loadTodaySportsItems(){
	loadRecords("Calories", {onSuccess: function(data){loadTodaySportsItemsSucc(data);}, onError: function(){loadCalItemsErr();}})
}
function loadAllCalItems(){
	loadRecords("Calories", {onSuccess: function(data){loadAllCalItemsSucc(data);}, onError: function(){loadAllCalItemsErr();}});
}
function loadTodayCalItemsSucc(data) {
	$("#recordentries").html("");
	if(data.values.records.size==0){
		$("#recordentries").append("<center>No entries yet!</center>");
	}
	var calories = 0, burned = 0;
	var age=0, tall=0, weight=0;
	bmrSet = false;
	data.values.records.reverse();
	entries = data.values.records;
	$.each(data.values.records, function(i, item) {
		if(item.app=="caloriesApp" && item.mealtype!="sports"){
			if(age==0 && item.age!=undefined){age = item.age;}
			if(tall==0 && item.height!=undefined){tall = item.height;}
			if(weight==0 && item.weight!=undefined){weight = item.weight;}
			var date = new Date(item.timedate);
			if(moment().isSame(date, 'day')){
				var s = '<li><h3 class="recordlist-itemheader">'+decodeURIComponent(item.descr)+'</h3><i>Created at: '+moment(date).format("HH:mm:ss")+'</i><p>Type: '+item.mealtype+': ';
				if(item.mealtype=="sports"){
					s = s+ "burned ";
				}else {
					s = s+ 'consumed '+item.amount+' items with';
				}
				if(item.cal!=undefined){
					if(item.mealtype!="sports"){
						calories = calories + parseInt(item.cal);
					} else {
						burned = burned + parseInt(item.cal);
					}
					s = s+' a total of '+item.cal + ' calories';
				} 
				s = s+'</p>';
				if(item.comment!=undefined){
					s = s+'<p>Comment: '+decodeURIComponent(item.comment)+'</p>';
				}
				s = s+'</li>';
				$("#recordentries").append(s);
				if(item.descr.indexOf("BMR") !== -1){bmrSet = true;}
			}
		}
	});
	if($("#recordentries").html()==""){
		$("#recordentries").append("<center>No entries yet!</center>");
	}
	$("#chronic-section-header").html("Your records for today  ("+calories+"cal consumed)")
	$("#age").val(age);
	$("#weight").val(weight);
	$("#tall").val(tall);
	$(".recordlist-itemheader").click(function(){ 
		if(typeof(Storage)!=="undefined") {
			localStorage.setItem("hb_detailId", entries[$(this).parent().index()]._id.$oid);
			localStorage.setItem("hb_backlink", "apps/caloriesApp.html");
		}
		window.location = WEB_URL+"detailRecord.html"; 
	});
}
function loadTodaySportsItemsSucc(data) {
	$("#recordentries").html("");
	if(data.values.records.size==0){
		$("#recordentries").append("<center>No entries yet!</center>");
	}
	var calories = 0, burned = 0;
	var age=0, tall=0, weight=0;
	bmrSet = false;
	data.values.records.reverse();
	entries = data.values.records;
	$.each(data.values.records, function(i, item) {
		if(item.app=="caloriesApp" && item.mealtype=="sports"){
			if(age==0 && item.age!=undefined){age = item.age;}
			if(tall==0 && item.height!=undefined){tall = item.height;}
			if(weight==0 && item.weight!=undefined){weight = item.weight;}
			var date = new Date(item.timedate);
			if(moment().isSame(date, 'day')){
				var s = '<li><h3 class="recordlist-itemheader">'+decodeURIComponent(item.descr)+'</h3><i>Created at: '+moment(date).format("HH:mm:ss")+'</i><p>Type: '+item.mealtype+': burned ';
				if(item.cal!=undefined){
					burned = burned + parseInt(item.cal);
					s = s+' a total of '+item.cal + ' calories';
				} 
				s = s+'</p>';
				if(item.comment!=undefined){
					s = s+'<p>Comment: '+decodeURIComponent(item.comment)+'</p>';
				}
				s = s+'</li>';
				$("#recordentries").append(s);
				if(item.descr.indexOf("BMR") !== -1){bmrSet = true;}
			}
		}
	});
	if($("#recordentries").html()==""){
		$("#recordentries").append("<center>No entries yet!</center>");
	}
	$("#chronic-section-header").html("Your records for today  ("+burned+"cal burned)")
	if(user!=undefined){
		if(user.birthday){
			$("#age").val(Math.floor(moment().diff(moment(user.birthday, "DD-MM-YYYY"), "days")/365));	
		}
		if(user.weight!=undefined){
			$("#weight").val(user.weight);
		}
		if(user.height!=undefined){
			$("#tall").val(user.height);
		}
	} else {
		$("#age").val(age);
		$("#weight").val(weight);
		$("#tall").val(tall);
	}
	$(".recordlist-itemheader").click(function(){ 
		if(typeof(Storage)!=="undefined") {
			localStorage.setItem("hb_detailId", entries[$(this).parent().index()]._id.$oid);
			localStorage.setItem("hb_backlink", "apps/sportsApp.html");
		}
		window.location = WEB_URL+"detailRecord.html"; 
	});
}
function loadAllCalItemsSucc(data) {
	$("#recordentries").html("");
	if(data.values.records.size==0){
		$("#recordentries").append("<center>No entries yet!</center>");
		$("#chart_div").css("display", "none");
	}
	$("#chart_div").css("display", "block");
	$("#chart_div").html(" Try to <a href=\"\" onclick=\"location.reload();\">reload</a> the page.");
	var calories = 0, sportsCal = 0, caloriesToday = 0, sportsToday = 0;
	caloriesData = [];
	sportsData = [];
	days = [];
	$.each(data.values.records, function(i, item) {
		if(item.app=="caloriesApp"){
			if(item.cal!=undefined){
				calories = calories + parseInt(item.cal);
				var date = moment(item.timedate).format("DD-MM-YYYY");
				var found = false;
				if(days.length==0){ days[0] = date; }
				else { 
					for(var i=0;i<days.length;i++){
						if(days[i] == date){found = true;}
					}
					if(!found){days[i] = date;}
				}
				found = false;
				if(item.mealtype=="sports"){
					sportsCal = sportsCal + parseInt(item.cal);
					if(sportsData.length==0){
						if(item.descr.indexOf("BMR") !== -1){
							sportsData[0] = [date, 0, parseInt(item.cal), parseInt(item.cal)];
						} else {
							sportsData[0] = [date, parseInt(item.cal), 0, parseInt(item.cal)];
						}
					} else {
						for(var i=0;i<sportsData.length;i++){
							if(sportsData[i][0]==date){
								if(item.descr.indexOf("BMR") !== -1){
									sportsData[i][2] = parseInt(item.cal);
									sportsData[i][3] = sportsData[i][3]+parseInt(item.cal);
								} else {
									sportsData[i][1] = sportsData[i][1]+parseInt(item.cal);
									sportsData[i][3] = sportsData[i][3]+parseInt(item.cal);
								}
								found = true;
								break;
							}
						}
						if(!found){
							if(item.descr.indexOf("BMR") !== -1){
								sportsData[i] = [date, 0, parseInt(item.cal), parseInt(item.cal)];
							} else {
								sportsData[i] = [date, parseInt(item.cal), 0, parseInt(item.cal)];
							}
						}
					}
				} else {
					if(caloriesData.length==0){
						if(item.mealtype=="breakfast"){
							caloriesData[0] = [date, parseInt(item.cal), 0, 0, 0];
						} else if(item.mealtype=="lunch"){
							caloriesData[0] = [date, 0, parseInt(item.cal), 0, 0];
						} else if(item.mealtype=="dinner"){
							caloriesData[0] = [date, 0, 0, parseInt(item.cal), 0];
						} else if(item.mealtype=="snack"){
							caloriesData[0] = [date, 0, 0, 0, parseInt(item.cal)];
						}
					} else {
						for(var i=0;i<caloriesData.length;i++){
							if(caloriesData[i][0]==date){
								if(item.mealtype=="breakfast"){
									caloriesData[i][1] = caloriesData[i][1]+parseInt(item.cal);
								} else if(item.mealtype=="lunch"){
									caloriesData[i][2] = caloriesData[i][2]+parseInt(item.cal);
								} else if(item.mealtype=="dinner"){
									caloriesData[i][3] = caloriesData[i][3]+parseInt(item.cal);
								} else if(item.mealtype=="snack"){
									caloriesData[i][4] = caloriesData[i][4]+parseInt(item.cal);
								}
								found = true;
								break;
							}
						}
						if(!found){
							if(item.mealtype=="breakfast"){
								caloriesData[i] = [date, parseInt(item.cal), 0, 0, 0];
							} else if(item.mealtype=="lunch"){
								caloriesData[i] = [date, 0, parseInt(item.cal), 0, 0];
							} else if(item.mealtype=="dinner"){
								caloriesData[i] = [date, 0, 0, parseInt(item.cal), 0];
							} else if(item.mealtype=="snack"){
								caloriesData[i] = [date, 0, 0, 0, parseInt(item.cal)];
							}
						}
					}
				} 
			}
		}
	});
	data.values.records.reverse();
	entries = data.values.records;
	var iter = 0;
	$.each(data.values.records, function(i, item) {
		if(item.app=="caloriesApp"){
			var date = new Date(item.timedate);
			if(moment().isSame(date, 'day')){
				if(item.mealtype=="sports"){
					sportsToday = sportsToday + parseInt(item.cal);
				} else {
					caloriesToday = caloriesToday + parseInt(item.cal);
				}
			}
			iter++;
			var s = '<li><h3 class="recordlist-itemheader">'+decodeURIComponent(item.descr)+'</h3><i>Created at: '+moment(item.timedate).format("HH:mm:ss")+'</i><p>Type: '+item.mealtype+': ';
			if(item.mealtype=="sports"){
				s = s+ "burned ";
			}else {
				s = s+ 'consumed '+item.amount+' items with';
			}
			if(item.cal!=undefined){
				s = s+' a total of '+item.cal + ' calories';
			}
			s = s+'</p>';
			if(item.comment!=undefined){
				s = s+'<p>Comment: '+decodeURIComponent(item.comment)+'</p>';
			}
			s = s+'<button value="'+item._id.$oid+'" class=\"list-button calListBut circleButton\">Circles</button><button value="'+item._id.$oid+'" class=\"list-button calListSpaceBut spaceButton\">Spaces</button></li>';
			$("#recordentries").append(s);
			if(iter==10){ return false;}
		}
	});
	entriesShown = 10;
	
	if($("#recordentries").html()==""){
		$("#recordentries").append("<center>No entries yet!</center>");
	}
	$("#totalConsumption").html(calories+"cal");
	$("#todayConsumption").html(caloriesToday+"cal");
	$("#totalBurned").html(sportsCal+"cal");
	$("#todayBurned").html(sportsToday+"cal");
	$("#totalRatio").html((Math.ceil(calories/sportsCal * 100) / 100)+"");
	$("#todayRatio").html((Math.ceil(caloriesToday/sportsToday * 100) / 100)+"");
	$("#totalDays").html(days.length+"d");
	$(".recordlist-itemheader").click(function(){ 
		if(typeof(Storage)!=="undefined") {
			localStorage.setItem("hb_detailId", $($(this).parent()).find("button")[0].value);
			localStorage.setItem("hb_backlink", "spaces/caloriesTimeline.html");
		}
		window.location = WEB_URL+"detailRecord.html"; 
	});
	$(".circleButton").click(function(){
		$( "#notYetImplementedDialog" ).dialog( "open" );
    });
    $(".spaceButton").click(function(){
    	$( "#notYetImplementedDialog" ).dialog( "open" );
    });
}
function loadCalItemsErr() {
	$("#recordentries").html("<center style=\"color: red\">Either you have no entries yet or there was an error!</center>");
}
function loadAllCalItemsErr() {
	$("#recordentries").html("<center style=\"color: red\">Either you have no entries yet or there was an error!</center>");
	$("#chart_div").css("display", "none");
}
function loadMoreEntries(){
	if(entries!=undefined && entries.length>0)
	var iter = 0;
	for(item=0;item<entries.length;item++) {
		if(entries[item].app=="caloriesApp"){
			iter++;
			if(iter>entriesShown){
				var s = '<li><h3 class="recordlist-itemheader ">'+decodeURIComponent(entries[item].descr)+'</h3><i>Created at: '+moment(entries[item].timedate).format("HH:mm:ss")+'</i><p>Type: '+entries[item].mealtype+': ';
				if(entries[item].mealtype=="sports"){
					s = s+ "burned ";
				}else {
					s = s+ 'consumed '+entries[item].amount+' items with';
				}
				if(entries[item].cal!=undefined){
					s = s+' a total of '+entries[item].cal + ' calories';
				}
				s = s+'</p>';
				if(entries[item].comment!=undefined){
					s = s+'<p>Comment: '+decodeURIComponent(entries[item].comment)+'</p>';
				}
				s = s+'<button value="'+entries[item]._id.$oid+'" class=\"list-button calListBut circleButton\">Circles</button><button value="'+entries[item]._id.$oid+'" class=\"list-button calListSpaceBut spaceButton\">Spaces</button></li>';
				$("#recordentries").append(s);
				if(iter==entriesShown+10){ break;}
			}
		}
	}
	$(".recordlist-itemheader").click(function(){ 
		if(typeof(Storage)!=="undefined") {
			localStorage.setItem("hb_detailId", $($(this).parent()).find("button")[0].value);
			localStorage.setItem("hb_backlink", "spaces/caloriesTimeline.html");
		}
		window.location = WEB_URL+"detailRecord.html"; 
	});
	$(".circleButton").click(function(){
		$( "#notYetImplementedDialog" ).dialog( "open" );
    });
    $(".spaceButton").click(function(){
    	$( "#notYetImplementedDialog" ).dialog( "open" );
    });
	entriesShown += (iter-entriesShown);
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
	}
	if(cal!=undefined && cal.length>0){
		cal = parseInt(cal)*parseInt(amount);
		values = values + ", \"cal\":\""+encodeURIComponent(cal)+"\"";
	}
	if(comment!=undefined && comment.length>0) {
		values = values + ", \"comment\":\""+encodeURIComponent(comment)+"\"";
	}
	values = values + " }";

	addRecord(encodeURIComponent(descr), "Calories App Entry", values, {onSuccess: function(data){addCalEntrySucc(data, type);}, onError: function(message){addCalEntryErr(message);}});
}

function addCalEntrySucc(data, type) {
	$("#calApp-feedback").html("Please fill out all the fields with a *.");
	$("#calApp-feedback").css("color", "black");
	$("#cancelButton").click();
	if(type=="s"){
		loadTodaySportsItems();
	} else {
		loadTodayCalItems();
	}
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

	addRecord("Daily BMR", "Calories App Entry", values, {onSuccess: function(data){addCalEntrySucc(data, "s");}, onError: function(message){addCalEntryErr(message);}});
}

function drawVisualization() {
	setTimeout(function(){
	    var data;
	    var chart;
	    var options;
	    if(caloriesData!=undefined){
	    	for(var i=0; i<caloriesData.length;i++){
	    		caloriesData[i][5] = (caloriesData[i][4]+caloriesData[i][3]+caloriesData[i][2]+caloriesData[i][1])/4;
	    	}
	    	if(caloriesData.length<2){
	    		data = google.visualization.arrayToDataTable([
			      ['Day', 'Breakfast', 'Lunch', 'Dinner', 'Snack', 'Avg'],
			      caloriesData[0]
			    ]);
	    	} else if(caloriesData.length==2){
	    		data = google.visualization.arrayToDataTable([
			      ['Day', 'Breakfast', 'Lunch', 'Dinner', 'Snack', 'Avg'],
			      caloriesData[0],
			      caloriesData[1]
			    ]);
	    	} else if(caloriesData.length==3){
	    		data = google.visualization.arrayToDataTable([
			      ['Day', 'Breakfast', 'Lunch', 'Dinner', 'Snack', 'Avg'],
			      caloriesData[0],
			      caloriesData[1],
			      caloriesData[2]
			    ]);
	    	} else if(caloriesData.length==4){
	    		data = google.visualization.arrayToDataTable([
			      ['Day', 'Breakfast', 'Lunch', 'Dinner', 'Snack', 'Avg'],
			      caloriesData[0],
			      caloriesData[1],
			      caloriesData[2],
			      caloriesData[3]
			    ]);
	    	} else if(caloriesData.length==5){
	    		data = google.visualization.arrayToDataTable([
			      ['Day', 'Breakfast', 'Lunch', 'Dinner', 'Snack', 'Avg'],
			      caloriesData[0],
			      caloriesData[1],
			      caloriesData[2],
			      caloriesData[3],
			      caloriesData[4]
			    ]);
	    	} else if(caloriesData.length==6){
				data = google.visualization.arrayToDataTable([
			      ['Day', 'Breakfast', 'Lunch', 'Dinner', 'Snack', 'Avg'],
			      caloriesData[0],
			      caloriesData[1],
			      caloriesData[2],
			      caloriesData[3],
			      caloriesData[4],
			      caloriesData[5]
			    ]);
	    	} else {
			    data = google.visualization.arrayToDataTable([
			      ['Day', 'Breakfast', 'Lunch', 'Dinner', 'Snack', 'Avg'],
			      caloriesData[0],
			      caloriesData[1],
			      caloriesData[2],
			      caloriesData[3],
			      caloriesData[4],
			      caloriesData[5],
			      caloriesData[6]
			    ]);
			}

		    options = {
		      title : 'Your Calories Consumption in the Last 7 Active Days',
		      width: 480,
		      height: 250,
		      vAxis: {title: "Calories"},
		      hAxis: {title: "Day"},
		      seriesType: "bars",
		      series: {4: {type: "line"}}
		    };

		    chart = new google.visualization.ComboChart(document.getElementById('chart_div'));
		    chart.draw(data, options);
		}
		if(sportsData!=undefined){
	    	if(sportsData.length<2){
	    		data = google.visualization.arrayToDataTable([
			      ['Day', 'Sport', 'BMR', 'Total'],
			      sportsData[0]
			    ]);
	    	} else if(sportsData.length==2){
	    		data = google.visualization.arrayToDataTable([
			      ['Day', 'Sport', 'BMR', 'Total'],
			      sportsData[0],
			      sportsData[1]
			    ]);
	    	} else if(sportsData.length==3){
	    		data = google.visualization.arrayToDataTable([
			      ['Day', 'Sport', 'BMR', 'Total'],
			      sportsData[0],
			      sportsData[1],
			      sportsData[2]
			    ]);
	    	} else if(sportsData.length==4){
	    		data = google.visualization.arrayToDataTable([
			      ['Day', 'Sport', 'BMR', 'Total'],
			      sportsData[0],
			      sportsData[1],
			      sportsData[2],
			      sportsData[3]
			    ]);
	    	} else if(sportsData.length==5){
	    		data = google.visualization.arrayToDataTable([
			      ['Day', 'Sport', 'BMR', 'Total'],
			      sportsData[0],
			      sportsData[1],
			      sportsData[2],
			      sportsData[3],
			      sportsData[4]
			    ]);
	    	} else if(sportsData.length==6){
				data = google.visualization.arrayToDataTable([
			      ['Day', 'Sport', 'BMR', 'Total'],
			      sportsData[0],
			      sportsData[1],
			      sportsData[2],
			      sportsData[3],
			      sportsData[4],
			      sportsData[5]
			    ]);
	    	} else {
			    data = google.visualization.arrayToDataTable([
			      ['Day', 'Sport', 'BMR', 'Total'],
			      sportsData[0],
			      sportsData[1],
			      sportsData[2],
			      sportsData[3],
			      sportsData[4],
			      sportsData[5],
			      sportsData[6]
			    ]);
			}

		    options = {
		      title : 'Your Burned Calories in the Last 7 Active Days',
		      width: 480,
		      height: 250,
		      vAxis: {title: "Calories"},
		      hAxis: {title: "Day"},
		      seriesType: "bars",
		      series: {2: {type: "line"}}
		    };

		    chart = new google.visualization.ComboChart(document.getElementById('chartSports_div'));
		    chart.draw(data, options);
		}
	}, 1000);
  }