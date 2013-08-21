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
    if(name==undefined || name.length<1 || descr==undefined || descr.length<1 || descr=="Enter your description of the record here..."){
        $("#addRec-feedback").html("Please fill out all the form fields with a (*).");
        $("#addRec-feedback").css("color", "red");
        return false;
    } 
    var file = $("#file")[0].files[0];
    var cause = ($("#cause").val()!=undefined || $("#cause").val()=="")?$("#cause").val():"";
    var doctor = ($("#doctor").val()!=undefined || $("#doctor").val()=="")?$("#doctor").val():"";
    var values = "{ \"app\" : \"addRecord\", \"cause\": \""+cause+"\", \"doctor\": \""+doctor+"\" }";
    // do some test with it, if need be
    if(file.name!=undefined || icon.name.length>0){
        $("#values").attr("value", values);
        var formData = new FormData($('#addRec-form')[0]);
        parent.addRecordWithFile(formData, {
            onSuccess: function(data){
                addRecordCompleted(data);
            }, 
            onError: function(message){
              $("#addRec-feedback").html(message); 
              $("#addRec-feedback").css("color", "red");
            }
        });
    } else {
        parent.addRecord(
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
}

function clear() {
    $("#name").val("");
    $("#descr").val("");
    $("#cause").val("");
    $("#doctor").val("");
    $("#file").val("");
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