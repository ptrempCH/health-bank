var entries, totCal=0, calData, totDist=0, distData, totTime=0, timeData, avgSpeed=0, avgSpeedData, avgHR=0, avgHRData, totEle=0, eleData, days;

function loadStatistics(){
	if(parent.records!=undefined){
		loadStatisticsSucc(parent.records);
	} else {
		loadStatisticsErr();
	}
}

function loadStatisticsSucc(data) {
	if(data.length==0){
		loadStatisticsErr();
		return false;
	}
	calData = [];
	distData = [];
	timeData = [];
	avgSpeedData = [];
	avgHRData = [];
	eleData = [];
	days = [];
	$.each(data, function(i, item) {
		if(item.appID=="523ffbe5c3b875ac86c11c98"){
			var date = moment(item.timedate).format("DD-MM-YYYY");
			days[i] = date;
			if(item.cal!=undefined && +item.cal!=NaN){ 
				totCal += +item.cal;
				calData[i] = +item.cal; 
			} else { calData[i] = 0; }

			if(item.distance!=undefined && +item.distance!=NaN){ 
				totDist += +item.distance;
				distData[i] = +item.distance; 
			} else { distData[i] = 0; }

			if(item.time!=undefined && item.time.length>0){ 
				a = +item.time.substring(0,item.time.indexOf(":"));
				b = +item.time.substring(item.time.indexOf(":")+1)/60;
				totTime += a+b;	
				timeData[i] = a+b; 
			} else { timeData[i] = 0; }

			if(item.avgSpeed!=undefined && +item.avgSpeed!=NaN){ 
				avgSpeed += +item.avgSpeed;
				avgSpeedData[i] = +item.avgSpeed; 
			} else { avgSpeedData[i] = 0; }

			if(item.avgHeartRate!=undefined && +item.avgHeartRate!=NaN){ 
				avgHR += +item.avgHeartRate;
				avgHRData[i] = +item.avgHeartRate; 
			} else { avgHRData[i] = 0; }

			tmp = [];
			if(item.elevationGain!=undefined && +item.elevationGain!=NaN){
				totEle += +item.elevationGain;
				tmp[0] = +item.elevationGain;
			} else { tmp[0] = 0; }
			if(item.elevationLoss!=undefined && +item.elevationLoss!=NaN){
				tmp[1] = +item.elevationLoss;
			} else { tmp[1] = 0; }
			eleData[i] = tmp;
		}
	});
	entries = data;
	
	$("#totalDistance").html((Math.round(totDist * 100) / 100)+" km");
	$("#totalTime").html((Math.round(totTime * 100) / 100)+" min");
	$("#avgSpeed").html((Math.round((avgSpeed/days.length) * 100) / 100)+" km/h");
	$("#totalElevation").html((Math.round(totEle * 100) / 100)+" m");
	$("#totalCalories").html((Math.round(totCal * 100) / 100)+" cal");
}

function loadStatisticsErr() {
	$("#cal_chart").html("No data available");
	$("#distance_chart").html("No data available");
	$("#avgSpeed_chart").html("No data available");
	$("#elevation_chart").html("No data available");
	$("#avgHeartRate_chart").html("No data available");
	loadCarousel();
}

var loadedCarousel = false;
function loadCarousel(){
	if(loadedCarousel){
		return false;
	}
	loadedCarousel = true;
	setTimeout(function(){
	    $("#owl-carousel").owlCarousel({
  			navigation : true, // Show next and prev buttons
		    slideSpeed : 300,
		    paginationSpeed : 400,
		    singleItem:true,
		    mouseDrag : true,
		    touchDrag : true,
		    autoHeight : true
  		});
	}, 1000);
}

function drawVisualization() {
    if(days!=undefined && days.length>0){
    	// reverse
    	days.reverse();
    	calData.reverse();
    	distData.reverse();
    	avgSpeedData.reverse();
    	eleData.reverse();
    	avgHRData.reverse();

    	// cal
    	tmp = [];
    	tmp[0] = ['Day', 'Calories']
    	$.each(days, function(i, item){
    		tmp[i+1] = [days[i], calData[i]];
    	});

	    new google.visualization.LineChart(document.getElementById("cal_chart")).
	    	draw(google.visualization.arrayToDataTable(tmp), {
	    		curveType: "function",
                width: 500, height: 250,
                title : 'Your Calories Consumption',
                vAxis: {title: "Calories"},
	      		hAxis: {title: "Day"}
	      	});

	    // distance
	    tmp = [];
    	tmp[0] = ['Day', 'Distance']
    	$.each(days, function(i, item){
    		tmp[i+1] = [days[i], distData[i]];
    	});

	    new google.visualization.LineChart(document.getElementById("distance_chart")).
	    	draw(google.visualization.arrayToDataTable(tmp), {
	    		curveType: "function",
                width: 500, height: 250,
                title : 'Total Distance Travelled',
                vAxis: {title: "km"},
	      		hAxis: {title: "Day"}
	      	});

	    // avgSpeed
	    tmp = [];
    	tmp[0] = ['Day', 'Avg. Speed']
    	$.each(days, function(i, item){
    		tmp[i+1] = [days[i], avgSpeedData[i]];
    	});

	    new google.visualization.LineChart(document.getElementById("avgSpeed_chart")).
	    	draw(google.visualization.arrayToDataTable(tmp), {
	    		curveType: "function",
                width: 500, height: 250,
                title : 'Your Average Speed',
                vAxis: {title: "km/h"},
	      		hAxis: {title: "Day"}
	      	});

	    // elevation
	    tmp = [];
    	tmp[0] = ['Day', 'Elevation Gain', 'Elevation Loss']
    	$.each(days, function(i, item){
    		tmp[i+1] = [days[i], eleData[i][0], eleData[i][1]];
    	});

	    new google.visualization.LineChart(document.getElementById("elevation_chart")).
	    	draw(google.visualization.arrayToDataTable(tmp), {
	    		curveType: "function",
                width: 500, height: 250,
                title : 'Elevation Gain',
                vAxis: {title: "m"},
	      		hAxis: {title: "Day"}
	      	});

	    // avg heart rate
	    tmp = [];
    	tmp[0] = ['Day', 'Avg. Heart Rate']
    	$.each(days, function(i, item){
    		tmp[i+1] = [days[i], avgHRData[i]];
    	});

	    new google.visualization.LineChart(document.getElementById("avgHeartRate_chart")).
	    	draw(google.visualization.arrayToDataTable(tmp), {
	    		curveType: "function",
                width: 500, height: 250,
                title : 'Your Average Heart Rate',
                vAxis: {title: "Beat / min"},
	      		hAxis: {title: "Day"}
	      	});

		loadCarousel();
	} else {
		loadStatisticsErr();
	}
}