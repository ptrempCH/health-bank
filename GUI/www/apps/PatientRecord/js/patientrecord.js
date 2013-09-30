var patientID;


function addRecordCompleted(data){
    $("#addRec-feedback").html("Success! <br/>Entry added to users health record.");
    $("#addRec-feedback").css("color", "green");
    $("#seeAllRecsButton").click(function () {
        window.top.location = parent.WEB_URL+"spaces/spaces.html";
    });
}

function submit(){
    var name = $("#name").val();
    var descr = $("#descr").val();
    var patient = $("#patient").val();
    if(name==undefined || name.length<1 || descr==undefined || descr.length<1 || patient==undefined || patient.length<1 || patientID==undefined || patientID.length<1){
        $("#addRec-feedback").html("Please fill out all the form fields with a (*).");
        $("#addRec-feedback").css("color", "red");
        return false;
    } 
    var file = $("#file")[0].files[0];
    var cause = ($("#cause").val()!=undefined || $("#cause").val()=="")?$("#cause").val():"";
    var doctor = ($("#doctor").val()!=undefined || $("#doctor").val()=="")?$("#doctor").val():"";
    var treatment = ($("#treatment").val()!=undefined || $("#treatment").val()=="")?$("#treatment").val():"";
    var drugs = ($("#drugs").val()!=undefined || $("#drugs").val()=="")?$("#drugs").val():"";
    // do some test with it, if need be
    if(file!=undefined && file.name!=undefined){
        var values = "{ \"app\" : \"addRecord\", \"cause\": \""+cause+"\", \"doctor\": \""+doctor+"\", \"treatment\": \""+treatment+"\", \"drugs\": \""+drugs+"\", \"patient\": \""+patient+"\", \"userID\": \""+patientID+"\" }";
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
        var values = "{ \"app\" : \"addRecord\", \"cause\": \""+cause+"\", \"doctor\": \""+doctor+"\", \"treatment\": \""+treatment+"\", \"drugs\": \""+drugs+"\", \"patient\": \""+patient+"\" }";
        parent.addPatientRecord(
            parent.id, 
            name, 
            descr, 
            patientID,
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

function loadPatients(data) {
    parent.$("#recipientList").html("");
    if(data!=undefined && data!=""){
        $.each(data.values.users, function(i, item){
            parent.$("#recipientList").append("<li id=\""+jQuery.parseJSON(item.userId).$oid+"\" >"+item.firstname+" "+item.lastname+"</li><br>");
        });
    }
    parent.$("#recipientList li").click(function(){
        patientID = $(this).attr("id");
        $("#patient").val($(this).html());
        parent.$('#dialog-overlay').click();
    });
    parent.$("#usersearchbar").keyup(function(event){
        if(event.keyCode == 13){
            parent.$("#searchButton").click();
        }
    });
    parent.$("#searchButton").click(function(){
        query = parent.$("#usersearchbar").val();
        parent.queryUsers(query, {
            onSuccess: function(data){
                parent.$("#recipientList").html("");
                $.each(data.values.users, function(i, item){
                    if(item.userId.$oid!=parent.user._id.$oid){
                        if(item.companyname!=undefined){
                            parent.$("#recipientList").append("<li id=\""+item.userId.$oid+"\" >"+item.companyname+"</li><br>");
                        } else {
                            parent.$("#recipientList").append("<li id=\""+item.userId.$oid+"\" >"+item.firstname+" "+item.lastname+"</li><br>");
                        }
                    }
                });
                parent.$("#recipientList li").click(function(){
                    patientID = $(this).attr("id");
                    $("#patient").val($(this).html());
                    parent.$('#dialog-overlay').click();
                });
                if(parent.$("#recipientList").html()==""){
                    parent.$("#recipientList").html("No user found");
                }
            },
            onError: function(data){
                parent.$("#recipientList").html("Sorry, could not load the result. Why don't you try another query?")
            }
        });
    });
    parent.$("#circleUsersButton").click(function(){
        parent.queryUsersInCircles({onSuccess: function(data){loadPatients(data);}});
    });
    if(parent.$("#recipientList").html()==""){
        parent.$("#recipientList").html("No user found");
    }
}

function clear() {
    $("#name").val("");
    $("#descr").val("");
    $("#cause").val("");
    $("#doctor").val(patent.user.companyname);
    $("#patient").val("");
    $("#treatment").val("");
    $("#drugs").val("");
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