import { AbstractCrud, CrudApi } from './abstract.crud'
import { Mix, Mixable, mixCasts, mixCast } from '../minicast'

export abstract class AbstractModel<T> extends AbstractCrud<T> implements Mixable {

    constructor(api: CrudApi, childrenCasts?: mixCasts) {
        super(api, null, null, childrenCasts)
        this.model = (this as any)
        this.customMixin = this.mixin
    }

    public mixin(data: Object) : void {
        if(!data || !(data instanceof Object)) {
            throw "[Crud][Collection] An Object payload is expected."
        }

        Mix.extend(this, data, this.childrenCasts)
    }

}