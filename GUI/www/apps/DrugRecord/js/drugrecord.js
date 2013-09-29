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
    var manufacturer = $("#manufacturer").val();
    if(name==undefined || name.length<1 || descr==undefined || descr.length<1 || manufacturer==undefined || manufacturer.length<1){
        $("#addRec-feedback").html("Please fill out all the form fields with a (*).");
        $("#addRec-feedback").css("color", "red");
        return false;
    } 
    var prescribed = ($("#prescribed").val()!=undefined || $("#prescribed").val()=="")?$("#prescribed").val():"";
    var bought = ($("#bought").val()!=undefined || $("#bought").val()=="")?$("#bought").val():"";
    var nrPills = ($("#nrPills").val()!=undefined || $("#nrPills").val()=="")?$("#nrPills").val():"";
    var colour = ($("#colour").val()!=undefined || $("#colour").val()=="")?$("#colour").val():"";
    var values = "{ \"app\" : \""+parent.appData.name+"\", \"manufacturer\": \""+manufacturer+"\", \"prescribed\": \""+prescribed+"\", \"nrPills\": \""+
                    nrPills+"\", \"colour\": \""+colour+"\", \"bought\": \""+bought+"\" }";
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
    $("#manufacturer").val("");
    $("#prescribed").val("");
    $("#bought").val("");
    $("#nrPills").val("");
    $("#colour").val("");
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