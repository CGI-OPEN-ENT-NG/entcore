"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var entcore_1 = require("entcore/entcore");
exports.activationController = entcore_1.ng.controller('ActivationController', ['$scope', function ($scope) {
        $scope.template = entcore_1.template;
        $scope.lang = entcore_1.idiom;
        $scope.template.open('main', 'activation-form');
        $scope.user = {};
        $scope.phonePattern = new RegExp("^(00|\\+)?(?:[0-9] ?-?\\.?){6,14}[0-9]$");
        $scope.welcome = {};
        entcore_1.http().get('/auth/configure/welcome').done(function (d) {
            $scope.welcome.content = d.welcomeMessage;
            if (!d.enabled) {
                $scope.welcome.hideContent = true;
            }
            $scope.$apply();
        })
            .e404(function () {
            $scope.welcome.hideContent = true;
            $scope.$apply();
        });
        if (window.location.href.indexOf('?') !== -1) {
            if (window.location.href.split('login=').length > 1) {
                $scope.user.login = window.location.href.split('login=')[1].split('&')[0];
            }
            if (window.location.href.split('activationCode=').length > 1) {
                $scope.user.activationCode = window.location.href.split('activationCode=')[1].split('&')[0];
            }
        }
        entcore_1.http().get('/auth/context').done(function (data) {
            $scope.callBack = data.callBack;
            $scope.cgu = data.cgu;
            $scope.passwordRegex = data.passwordRegex;
            $scope.mandatory = data.mandatory;
            $scope.$apply('cgu');
        });
        $scope.identicalRegex = function (str) {
            if (!str)
                return new RegExp("^$");
            return new RegExp("^" + str.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "$");
        };
        $scope.refreshInput = function (form, inputName) {
            form[inputName].$setViewValue(form[inputName].$viewValue);
        };
        $scope.passwordComplexity = function (password) {
            if (!password)
                return 0;
            if (password.length < 6)
                return password.length;
            var score = password.length;
            if (/[0-9]+/.test(password) && /[a-zA-Z]+/.test(password)) {
                score += 5;
            }
            if (!/^[a-zA-Z0-9- ]+$/.test(password)) {
                score += 5;
            }
            return score;
        };
        $scope.translateComplexity = function (password) {
            var score = $scope.passwordComplexity(password);
            if (score < 12) {
                return entcore_1.idiom.translate("weak");
            }
            if (score < 20)
                return entcore_1.idiom.translate("moderate");
            return entcore_1.idiom.translate("strong");
        };
        $scope.activate = function () {
            var emptyIfUndefined = function (item) {
                return item ? item : "";
            };
            entcore_1.http().post('/auth/activation', entcore_1.http().serialize({
                login: $scope.user.login,
                password: $scope.user.password,
                confirmPassword: $scope.user.confirmPassword,
                acceptCGU: $scope.user.acceptCGU,
                activationCode: $scope.user.activationCode,
                callBack: $scope.callBack,
                mail: emptyIfUndefined($scope.user.email),
                phone: emptyIfUndefined($scope.user.phone)
            }))
                .done(function (data) {
                if (typeof data !== 'object') {
                    window.location.href = '/';
                }
                if (data.error) {
                    $scope.error = data.error.message;
                }
                $scope.$apply('error');
            });
        };
    }]);
exports.cguController = entcore_1.ng.controller('CGUController', ['$scope', 'template', function ($scope, template) {
        $scope.template = template;
        $scope.template.open('main', 'cgu-content');
    }]);

//# sourceMappingURL=activationController.js.map
