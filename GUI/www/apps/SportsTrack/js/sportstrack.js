function addRecordCompleted(data){
    $("#addRec-feedback").html(data.message+"<br/>To see all your records click <a id=\"seeAllRecsButton\">here</a>.");
    $("#addRec-feedback").css("color", "green");
    $("#seeAllRecsButton").click(function () {
        window.top.location = parent.WEB_URL+"spaces/spaces.html";
    });
}

function submit(){
    var name = $("#name").val();
    var descr = $("#descr").val();
    var distance = $("#distance").val();
    var time = $("#time").val();
    if(name==undefined || name.length<1 || descr==undefined || descr.length<1 || distance==undefined || distance.length<1 || time==undefined || time.length<1){
        $("#addRec-feedback").html("Please fill out all the form fields with a (*).");
        $("#addRec-feedback").css("color", "red");
        return false;
    } 
    var avgSpeed = ($("#avgSpeed").val()!=undefined || $("#avgSpeed").val()=="")?$("#avgSpeed").val():"";
    var elevationGain = ($("#elevationGain").val()!=undefined || $("#elevationGain").val()=="")?$("#elevationGain").val():"";
    var elevationLoss = ($("#elevationLoss").val()!=undefined || $("#elevationLoss").val()=="")?$("#elevationLoss").val():"";
    var cal = ($("#cal").val()!=undefined || $("#cal").val()=="")?$("#cal").val():"";
    var avgHeartRate = ($("#avgHeartRate").val()!=undefined || $("#avgHeartRate").val()=="")?$("#avgHeartRate").val():"";
    var values = "{ \"app\" : \""+parent.appData.name+"\", \"distance\": \""+distance+"\", \"time\": \""+time+"\", \"avgSpeed\": \""+
                    avgSpeed+"\", \"elevationGain\": \""+elevationGain+"\", \"elevationLoss\": \""+elevationLoss+"\", \"cal\": \""+cal+"\", \"avgHeartRate\": \""+avgHeartRate+"\" }";
    // do some test with it, if need be
    parent.addRecord(
        parent.id, 
        name, 
        descr, 
        values, 
        {
            onSuccess: function(data){
                addRecordCompleted(data);
            }, 
            onError: function(message){
                $("#addRec-feedback").html(message); 
                $("#addRec-feedback").css("color", "red");
            }
        }
    );
}

function clear() {
    $("#name").val("");
    $("#descr").val("");
    $("#distance").val("");
    $("#time").val("");
    $("#avgSpeed").val("");
    $("#elevationGain").val("");
    $("#elevationLoss").val("");
    $("#cal").val("");
    $("#avgHeartRate").val("");
}

function textAreaAdjust(o) {
    o.style.height = "1px";
    o.style.height = (25+o.scrollHeight)+"px";
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


var activities = [
    "Jogging",
    "Running",
    "Walking",
    "Hiking",
    "Mountainbiking",
    "Cycling",
    "Skating",
    "Cross-Country Skiing",
    "Rowing"
]