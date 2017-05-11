import { AbstractCrud, CrudApi } from './abstract.crud'
import { Mix, Mixable, mixCasts, mixCast } from '../minicast'

export abstract class AbstractCollection<T> extends AbstractCrud<T> implements Mixable {

    public data: T[] = []

    constructor(api: CrudApi, initialCast?: mixCast | Function, childrenCasts?: mixCasts) {
        super(api, null, initialCast, childrenCasts)
        this.model = this.data
        this.customMixin = this.mixin
    }

    public mixin(data: Array<any>) : void {
        if(!data || !(data instanceof Array)) {
            throw "[Crud][Collection] An Array payload is expected."
        }

        this.data.length = 0

        data.forEach(item => {
            let instance = {} as T
            if(this.initialCast) {
                if(this.initialCast instanceof Function) {
                    instance = new (this.initialCast as any)()
                } else {
                    instance = new (this.initialCast.type as any)(...this.initialCast.deps)
                }
            }
            Mix.extend(instance, item, this.childrenCasts)
            this.data.push(instance)
        })
    }

}