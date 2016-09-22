import { routes, ng } from 'entcore/entcore';
import { activationController } from './activationController';
import { forgotController } from './forgotController';
import { resetController } from './resetController';
import { loginController } from './loginController';

routes.define(function($routeProvider) {
	$routeProvider
		.when('/id', {
			action: 'actionId'
		})
		.when('/password', {
	  		action: 'actionPassword'
		})
		.otherwise({
		  	redirectTo: '/'
		})
});

ng.controllers.push(activationController);
ng.controllers.push(forgotController);
ng.controllers.push(resetController);
ng.controllers.push(loginController);