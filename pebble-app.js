// required modules
var UI = require('ui');
var Accel = require('ui/accel');
var Vibe = require('ui/vibe');
var Vector2 = require('vector2');

var distressMode = false;
var accelMode = false;

// Make a list of menu items
var options = [
  {
    title: "Safety First",
    subtitle: "Prompt distress signals"
  },
  {
    title: "Accelerometer",
    subtitle: "For fun"
  },
  {
    title: "Title",
    subtitle: "Subtitle"
  }
];

// Create the Menu, supplying the list of fruits
var mainMenu = new UI.Menu({
  sections: [{
    title: 'Main Menu',
    items: options
  }]
});

// Show the Menu
mainMenu.show();

// Show a card with clicked item details
var distressCard = new UI.Card({
    title: "Safety First",
    body: "Shake twice if in distress, calls emergency number."
});

var accelCard = new UI.Card({
    title: "Current Info",
    subtitle: '',
    body: "Current acceleration:"
});

var splashCard = new UI.Card({
    title: "Made by:",
    body: "Tiffany Chung, Alwin Hui, Tyler Lee, and Jeffrey Sham"
});

// Add a click listener for select button click
mainMenu.on('select', function(event) {
  if (options[event.itemIndex].title === "Safety First") {
      distressMode = true;
      accelMode = false;
      accelCard.hide();
      distressCard.show();
  }
  else if (options[event.itemIndex].title === "Accelerometer") {
      accelMode = true;
      distressMode = false;
      distressCard.hide();
      accelCard.show();
  }
  else {
      distressMode = false;
      accelMode = false;
      distressCard.hide();
      accelCard.hide();
      splashCard.show();
  }


});

var text2 = new UI.Text({
  position: new Vector2(0, 0),
  size: new Vector2(144, 168),
  text:'Are you in distress?',
  font:'GOTHIC_28_BOLD',
  color:'black',
  textOverflow:'wrap',
  textAlign:'center',
  backgroundColor:'white'
});

Accel.init();

Accel.on('tap', function(e) {
    if (distressMode) {
        console.log('Tap event on axis: ' + e.axis + ' and direction: ' + e.direction);
        //Pebble.showSimpleNotificationOnPebble("Are you in distress?", "Press select if safe.");
        Vibe.vibrate('long');
        distressCard.add(text2);
        
    }
});

// this doesn't really work
var data;
Accel.peek(function(e) {
  data = 'Current acceleration on axis are: X=' + e.accel.x + ' Y=' + e.accel.y + ' Z=' + e.accel.z;
  console.log(data);
  //accelCard.add(data);
});




