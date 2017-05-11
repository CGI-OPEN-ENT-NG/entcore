function mapToArray(map) {
    var result = [];

    map.forEach(function (item) {
        result.push(item);
    });
    return result;
}

export type mixCast = { type: Function, deps?: Array<any>, single? : boolean }
export type mixCasts = { [key: string]: mixCast | Function }

export interface Mixable {
    mixin(data: any)
}

export class Mix {
    static extend(obj, mixin, casts?: mixCasts) {
        for (var property in mixin) {
            let value = mixin[property]

            if(casts && casts[property] && value) {

                let castItem = casts[property]
                let cast : mixCast

                if(castItem instanceof Function){
                    cast = {
                        type: castItem,
                        deps: []
                    }
                } else {
                    cast = {
                        type: castItem.type,
                        single: castItem.single,
                        deps: castItem.deps ? castItem.deps : []
                    }
                }

                let doCast = (v) => {
                    let instance = new (cast.type as any)(...cast.deps)
                    if(instance.mixin)
                        instance.mixin(v)
                    else
                        Mix.extend(instance, v)
                    return instance
                }

                if(value instanceof Array && cast.single) {
                    obj[property] = []
                    value.forEach(v => {
                        obj[property].push(doCast(v))
                    })
                } else {
                    obj[property] = doCast(value)
                }
            }
            else if (!value || typeof value !== 'object' || value instanceof Array) {
                obj[property] = value;
            }
            else {
                if (obj[property] instanceof TypedArray) {
                    obj[property].load(value);
                }
                else {
                    if (!obj[property]) {
                        obj[property] = {};
                    }
                    this.extend(obj[property], value);
                }
            }
        }
    }

    static castAs(className, obj, params: any = {}) {
        var newObj = new (className as any)(params);
        this.extend(newObj, obj);
        return newObj;
    }

    static castArrayAs(className, arr: Array<any>, params: any[] = []) {
        var newArr = [];
        arr.forEach((item) => {
            newArr.push(
                Mix.castAs(className, item, params)
            );
        });
        return newArr;
    }
}

export class TypedArray<T> extends Array {
    className: any;
    private mixin: any;

    constructor(className: any, mixin: any = {}) {
        super();
        this.className = className;
        this.mixin = mixin;
    }

    push(...items: any[]): number {
        items.forEach((item) => {
            if (!(item instanceof this.className)) {
                item = Mix.castAs(this.className, item);
            }
            for (var prop in this.mixin) {
                item[prop] = this.mixin[prop]
            }

            Array.prototype.push.call(this, item);
        });

        return this.length;
    }

    load(data) {
        data.forEach((item) => {
            this.push(item);
        });
    }

    asArray() {
        return mapToArray(this);
    }

    toJSON() {
        return mapToArray(this);
    }
}