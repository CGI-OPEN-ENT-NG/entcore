"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var Selection = (function () {
    function Selection(arr) {
        this.arr = arr;
    }
    Selection.prototype.selectAll = function () {
        for (var i = 0; i < this.arr.length; i++) {
            this.arr[i].selected = true;
        }
    };
    Selection.prototype.select = function (filter) {
        for (var i = 0; i < this.arr.length; i++) {
            this.arr[i].selected = filter(this.arr[i]);
        }
    };
    Selection.prototype.deselect = function (filter) {
        for (var i = 0; i < this.arr.length; i++) {
            this.arr[i].selected = !filter(this.arr[i]);
        }
    };
    Selection.prototype.deselectAll = function () {
        for (var i = 0; i < this.arr.length; i++) {
            this.arr[i].selected = false;
        }
    };
    Selection.prototype.removeSelection = function () {
        var newArr = [];
        for (var i = 0; i < this.arr.length; i++) {
            if (this.arr[i].selected) {
                newArr.push(this.arr[i]);
            }
        }
        this.arr.splice(0, this.arr.length);
        for (var i = 0; i < newArr.length; i++) {
            this.arr.push(newArr[i]);
        }
    };
    Selection.prototype.updateSelected = function () {
        for (var i = 0; i < this.arr.length; i++) {
            var index = this.selectedElements.indexOf(this.arr[i]);
            if (this.arr[i].selected && index === -1) {
                this.selectedElements.push(this.arr[i]);
            }
            else if (!this.arr[i].selected && index !== -1) {
                this.selectedElements.splice(index, 1);
            }
        }
    };
    Object.defineProperty(Selection.prototype, "selected", {
        // a specific array is maintained to avoid references breaking all the time
        get: function () {
            this.updateSelected();
            return this.selectedElements;
        },
        enumerable: true,
        configurable: true
    });
    return Selection;
}());
exports.Selection = Selection;

//# sourceMappingURL=selection.js.map
