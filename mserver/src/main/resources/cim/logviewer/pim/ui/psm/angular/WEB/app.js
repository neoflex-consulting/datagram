
angular.module('logViewer.controllers', [
	'luegg.directives', 
	'ngTouch', 
	'ui.grid', 
	'ui.grid.selection', 
	'ui.grid.autoScroll',
	'ui.grid.cellNav'
])


angular.module('logViewer', ['logViewer.controllers'])