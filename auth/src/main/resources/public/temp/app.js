"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var entcore_1 = require("entcore/entcore");
var activationController_1 = require("./activationController");
var forgotController_1 = require("./forgotController");
var resetController_1 = require("./resetController");
var loginController_1 = require("./loginController");
entcore_1.routes.define(function ($routeProvider) {
    $routeProvider
        .when('/id', {
        action: 'actionId'
    })
        .when('/password', {
        action: 'actionPassword'
    })
        .otherwise({
        redirectTo: '/'
    });
});
entcore_1.ng.controllers.push(activationController_1.activationController);
entcore_1.ng.controllers.push(forgotController_1.forgotController);
entcore_1.ng.controllers.push(resetController_1.resetController);
entcore_1.ng.controllers.push(loginController_1.loginController);

//# sourceMappingURL=app.js.map
