'use strict';


angular.module('app').directive('execSummary', function () {


	return {
		restrict: 'E',
		replace: true,
		templateUrl: 'partials/exec/summary.html',
		controller: 'ExecSummaryController'
	}
});
