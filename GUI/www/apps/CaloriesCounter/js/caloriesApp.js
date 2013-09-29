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

	parent.addRecord(parent.id, encodeURIComponent(descr), "Calories App Entry", values, {onSuccess: function(data){addCalEntrySucc(data, type);}, onError: function(message){addCalEntryErr(message);}});
}

function addCalEntrySucc(data, type) {
	$("#calApp-feedback").html("Added the new records successfully.");
	$("#calApp-feedback").css("color", "green");
	$("#cancelButton").click();
}
function addCalEntryErr(message) {
	$("#calApp-feedback").html(message);
	$("#calApp-feedback").css("color", "red");
}

function setUpHelpDialog(){
	$("#dialog-overlay, #calDialog").show("slow");
	$("#calDialogCloseButton, #dialog-overlay").click(function(){
		$("#dialog-overlay, #calDialog").hide("slow");
		return false;
	});
	$("#calDialogBackButton").hide();
	$("#calDialogBackButton").click(function(){
		loadCategories();
		$("#calDialogBackButton").hide();
		return false;
	});
	loadCategories();
}

function loadCategories(){
	$("#calDialogList").html("");
	$.each(caloriesData, function(key, value){
		$("#calDialogList").append('<li value="'+key+'">'+key+'</li>');
	});
	$("#calDialogList li").click(function(){
		loadCategoryValues($(this).attr("value"));
		return false;
	});
	$("#calDialogHeader span").html('Choose a category');
}

function loadCategoryValues(id){
	$("#calDialogList").html("");	
	$("#calDialogHeader span").html('Choose an item of '+id);
	$.each(caloriesData, function(key, value){
		if(key==id){
			$.each(value, function(i, item){
				$("#calDialogList").append('<li value="'+item.name+'" cal="'+item.calories+'">'+item.name+'</li>');
			});
		}
		
	});
	$("#calDialogList li").click(function(){
		$("#descr").val($(this).attr("value"));
		$("#cal").val($(this).attr("cal"));
		$("#calDialogCloseButton").click();
		return false;
	});
	$("#calDialogBackButton").show();
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


/**
* Data
**/

var caloriesData = {
	"fruits": 
		[
			{
				"name" : "Apple",
				"calories" : "116"
			},
			{
				"name" : "Apricots",
				"calories" : "17"
			},
			{
				"name" : "Banana",
				"calories" : "105"
			},
			{
				"name" : "Berries (cup)",
				"calories" : "83"
			},
			{
				"name" : "Cherries (cup)",
				"calories" : "74"
			},
			{
				"name" : "Grapes (Cup)",
				"calories" : "62"
			},
			{
				"name" : "Grapefruit",
				"calories" : "104"
			},
			{
				"name" : "Kiwi",
				"calories" : "46"
			},
			{
				"name" : "Mango",
				"calories" : "135"
			},
			{
				"name" : "Melons (cup)",
				"calories" : "60"
			},
			{
				"name" : "Nectarine",
				"calories" : "59"
			},
			{
				"name" : "Orange/Clementine/Mandarine",
				"calories" : "62"
			},
			{
				"name" : "Papaya",
				"calories" : "60"
			},
			{
				"name" : "Peaches",
				"calories" : "38"
			},
			{
				"name" : "Pears",
				"calories" : "96"
			},
			{
				"name" : "Pineapple (cup)",
				"calories" : "78"
			},
			{
				"name" : "Tomato",
				"calories" : "16"
			}
		],
	"bread" : 
		[
			{
				"name" : "Bagel",
				"calories" : "354"
			},
			{
				"name" : "Baguette",
				"calories" : "138"
			},
			{
				"name" : "Hamburger/Hotdog bread",
				"calories" : "120"
			},
			{
				"name" : "Mixed-grain bread",
				"calories" : "69"
			},
			{
				"name" : "Rye bread",
				"calories" : "83"
			},
			{
				"name" : "Sandwich tbread",
				"calories" : "70"
			},
			{
				"name" : "Toast",
				"calories" : "72"
			},
			{
				"name" : "White bread",
				"calories" : "67"
			},
			{
				"name" : "Yeasted bread",
				"calories" : ""
			}
		],
	"vegetables":
		[
			{
				"name" : "Broccoli (cup)",
				"calories" : "27"
			},
			{
				"name" : "Carrot",
				"calories" : "16"
			},
			{
				"name" : "Cauliflower (3 flowerets)",
				"calories" : "12"
			},
			{
				"name" : "Corn (cup)",
				"calories" : "124"
			},
			{
				"name" : "Cucumber",
				"calories" : "46"
			},
			{
				"name" : "Lettuce (cup)",
				"calories" : "8"
			},
			{
				"name" : "Mushrooms (cup)",
				"calories" : "44"
			},
			{
				"name" : "Onions",
				"calories" : "28"
			},
			{
				"name" : "Peas (cup)",
				"calories" : "62"
			},
			{
				"name" : "Potatoe",
				"calories" : "144"
			}
		],
	"meat" :
		[
			{
				"name" : "Beef (100g)",
				"calories" : "186"
			},
			{
				"name" : "Chicken breast",
				"calories" : "141"
			},
			{
				"name" : "Ham (sliced 100g)",
				"calories" : "164"
			},
			{
				"name" : "Lamb loin (100g)",
				"calories" : "202"
			},
			{
				"name" : "Pork (100g)",
				"calories" : "144"
			},
			{
				"name" : "Sausage pork (100g)",
				"calories" : "345"
			},
			{
				"name" : "Turkey breast (100g)",
				"calories" : "105"
			}
		],
	"fish" : 
		[
			{
				"name" : "Cod (100g)",
				"calories" : "105"
			},
			{
				"name" : "Haddock (100g)",
				"calories" : "112"
			},
			{
				"name" : "Halibut (100g)",
				"calories" : "140"
			},
			{
				"name" : "Mussle (piece)",
				"calories" : "7"
			},
			{
				"name" : "Octopus (100g)",
				"calories" : "164"
			},
			{
				"name" : "Salmon (100g)",
				"calories" : "206"
			},
			{
				"name" : "Shrimp (100g)",
				"calories" : "119"
			},
			{
				"name" : "Tuna (100g)",
				"calories" : "116"
			}			
		],
	"pasta" : 
		[
			{
				"name" : "Macaroni (100g)",
				"calories" : "158"
			},
			{
				"name" : "Penne (100g)",
				"calories" : "268"
			},
			{
				"name" : "Rice white (100g)",
				"calories" : "130"
			},
			{
				"name" : "Rice wild (100g)",
				"calories" : "101"
			},
			{
				"name" : "Spaghetti (100g)",
				"calories" : "158"
			}
		],
	"drinks" : 
		[
			{
				"name" : "Beer (1dl)",
				"calories" : "43"
			},
			{
				"name" : "Cocktail (1dl)",
				"calories" : "231"
			},
			{
				"name" : "Coffee",
				"calories" : "5"
			},
			{
				"name" : "Coke/Fanta/Sprite/... (1dl)",
				"calories" : "40"
			},
			{
				"name" : "Coke/Fanta/Sprite/... diet (1dl)",
				"calories" : "1"
			},
			{
				"name" : "Energy drink (1dl)",
				"calories" : "44"
			},
			{
				"name" : "Ice Tea (1dl)",
				"calories" : "36"
			},
			{
				"name" : "Water (1dl)",
				"calories" : "0"
			},
			{
				"name" : "Wine red (1dl)",
				"calories" : "85"
			},
			{
				"name" : "Wine white (1dl)",
				"calories" : "82"
			}
			
		]
}