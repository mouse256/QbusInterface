
# OpenHab configuration

## Requirements
* Install Mqtt Binding
* Install JavaScript transformation add-on

## Configuration files
`/etc/openhab2/transform/255to100.js`
```(function(i) {
	return Math.round(100 * (parseInt(i) / 255));
})(input)
```

`/etc/openhab2/transform/100to255.js`
```(function(i) {
	return Math.round(255 * (parseInt(i) / 100));
})(input)
```

`/etc/openhab2/things/qbus.things`
Download from `127.0.0.1:8096/qbus/openhab/things`

`/etc/openhab2/items/qbus.items`
Download from `127.0.0.1:8096/qbus/openhab/items`

