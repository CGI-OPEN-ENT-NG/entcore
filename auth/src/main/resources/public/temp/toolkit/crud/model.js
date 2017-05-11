"use strict";
var __extends = (this && this.__extends) || (function () {
    var extendStatics = Object.setPrototypeOf ||
        ({ __proto__: [] } instanceof Array && function (d, b) { d.__proto__ = b; }) ||
        function (d, b) { for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p]; };
    return function (d, b) {
        extendStatics(d, b);
        function __() { this.constructor = d; }
        d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
var axios_1 = require("axios");
var abstract_model_1 = require("./abstract.model");
var Model = (function (_super) {
    __extends(Model, _super);
    function Model(api, childrenCasts) {
        var _this = _super.call(this, api, childrenCasts) || this;
        _this.http = axios_1.default;
        return _this;
    }
    return Model;
}(abstract_model_1.AbstractModel));
exports.Model = Model;

//# sourceMappingURL=model.js.map
