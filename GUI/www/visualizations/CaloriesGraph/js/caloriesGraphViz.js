var entries, caloriesData, sportsData, days;

function loadAllCalItems(){
	//parent.loadRecords("", {onSuccess: function(data){loadAllCalItemsSucc(data);}, onError: function(){loadAllCalItemsErr();}});
	if(parent.records!=undefined){
		loadAllCalItemsSucc(parent.records);
	} else {
		loadAllCalItemsErr();
	}
}

function loadAllCalItemsSucc(data) {
	//if(data.values.records.size==0){
	if(data.size==0){
		$("#chart_div").css("display", "none");
	}
	$("#chart_div").css("display", "block");
	var calories = 0, sportsCal = 0, caloriesToday = 0, sportsToday = 0;
	caloriesData = [];
	sportsData = [];
	days = [];
	//$.each(data.values.records, function(i, item) {
	$.each(data, function(i, item) {
		if(item.app=="caloriesApp"){
			if(item.cal!=undefined){
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
						if(item.descr.indexOf("BMR") !== -1 || item.name.indexOf("BMR") !== -1){
							sportsData[0] = [date, 0, parseInt(item.cal), parseInt(item.cal)];
						} else {
							sportsData[0] = [date, parseInt(item.cal), 0, parseInt(item.cal)];
						}
					} else {
						for(var i=0;i<sportsData.length;i++){
							if(sportsData[i][0]==date){
								if(item.descr.indexOf("BMR") !== -1 || item.name.indexOf("BMR") !== -1){
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
							if(item.descr.indexOf("BMR") !== -1 || item.name.indexOf("BMR") !== -1){
								sportsData[i] = [date, 0, parseInt(item.cal), parseInt(item.cal)];
							} else {
								sportsData[i] = [date, parseInt(item.cal), 0, parseInt(item.cal)];
							}
						}
					}
				} else {
					calories = calories + parseInt(item.cal);
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
	//data.values.records.reverse();
	//entries = data.values.records;
	entries = data;
	$.each(entries, function(i, item) {
		if(item.app=="caloriesApp"){
			var date = new Date(item.timedate);
			if(moment().isSame(date, 'day')){
				if(item.mealtype=="sports"){
					sportsToday = sportsToday + parseInt(item.cal);
				} else {
					caloriesToday = caloriesToday + parseInt(item.cal);
				}
			}
		}
	});
	
	$("#totalConsumption").html(calories+"cal");
	$("#todayConsumption").html(caloriesToday+"cal");
	$("#totalBurned").html(sportsCal+"cal");
	$("#todayBurned").html(sportsToday+"cal");
	if(sportsCal!=null){
		$("#totalRatio").html((Math.ceil(calories/sportsCal * 100) / 100)+"");
	} else {
		$("#totalRatio").html("0");
	}
	if(sportsToday!=0){
		$("#todayRatio").html((Math.ceil(caloriesToday/sportsToday * 100) / 100)+"");
	} else {
		$("#todayRatio").html("0");
	}
	$("#totalDays").html(days.length+"d");
}
function loadAllCalItemsErr() {
	$("#chart_div").css("display", "none");
}

function drawVisualization() {
	setTimeout(function(){
	    var data;
	    var chart;
	    var options;
	    if(caloriesData!=undefined && caloriesData.length>0){
	    	caloriesData.reverse();
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
	    		length = caloriesData.length;
			    data = google.visualization.arrayToDataTable([
			      ['Day', 'Breakfast', 'Lunch', 'Dinner', 'Snack', 'Avg'],
			      caloriesData[length-7],
			      caloriesData[length-6],
			      caloriesData[length-5],
			      caloriesData[length-4],
			      caloriesData[length-3],
			      caloriesData[length-2],
			      caloriesData[length-1]
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
		} else {
			$("#chart_div").html("no data");
		}
		if(sportsData!=undefined && sportsData.length>0) {
	    	sportsData.reverse();
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
	    		length = sportsData.length;
			    data = google.visualization.arrayToDataTable([
			      ['Day', 'Sport', 'BMR', 'Total'],
			      sportsData[length-7],
			      sportsData[length-6],
			      sportsData[length-5],
			      sportsData[length-4],
			      sportsData[length-3],
			      sportsData[length-2],
			      sportsData[length-1]
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
		} else {
			$("#chartSports_div").html("no data");
		}
	}, 1000);
}