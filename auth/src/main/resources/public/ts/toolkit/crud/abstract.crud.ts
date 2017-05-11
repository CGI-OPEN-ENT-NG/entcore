import { Mix, mixCasts, mixCast } from '../minicast'
import { Http, HttpResponse } from '../http.interface'

export interface CrudApi {
    create?: string | (() => string)
    sync?: string | (() => string)
    update?: string | (() => string)
    delete?: string | (() => string)
}

export abstract class AbstractCrud<T> {

    protected abstract http: Http

    constructor(
        protected api: CrudApi,
        protected model: T | T[],
        protected initialCast?: mixCast | Function,
        protected childrenCasts?: mixCasts,
        protected customMixin?: (payload: any) => void){}

    private parseApi(api: string | (() => string), parameters?: {}) {
        if(typeof api === 'function') {
            api = api()
        }

        return api.split(/(:[a-zA-Z0-9_.]+)/)
            .map((fragment : string) => {
                return fragment.charAt(0) === ':' ?
                    parameters && parameters[fragment.substr(1)] ||
                    this.model[fragment.substr(1)] ||
                    this[fragment.substr(1)] ||
                    fragment :
                    fragment
            }).join('')
    }

    private defaultMixin(payload: any): void {
        if(payload instanceof Array && this.model instanceof Array) {
            let model = this.model //fix type inference
            model.length = 0
            payload.forEach(item => {
                let instance = {} as T
                if(this.initialCast) {
                    if(this.initialCast instanceof Function) {
                        instance = new (this.initialCast as any)()
                    } else {
                        instance = new (this.initialCast.type as any)(...this.initialCast.deps)
                    }
                }
                Mix.extend(instance, item, this.childrenCasts)
                model.push(instance)
            })
        } else {
            Mix.extend(this.model, payload, this.childrenCasts)
        }
    }

    create(item?: T, opts: {} = {}) {
        if(!this.api.create) {
            throw '[Crud][Api] "create" route is undefined'
        }

        return this.http.post(this.parseApi(this.api.create, item), item || this.model, opts)
             .then((response: HttpResponse) => {
                if(this.model instanceof Array) {
                    this.model.push(item)
                }
                return response
             })
    }

    sync(opts: {}  = {}) : Promise<HttpResponse> {
        if(!this.api.sync) {
            throw '[Crud][Api] "sync" route is undefined'
        }

        return this.http.get(this.parseApi(this.api.sync), opts)
            .then((response: HttpResponse) => {
                (this.customMixin || this.defaultMixin).bind(this)(response.data)
                return response
            })
    }

    update(item?: T, opts: {} = {}) {
        if(!this.api.update) {
            throw '[Crud][Api] "update" route is undefined'
        }

        return this.http.put(this.parseApi(this.api.update, item), item || this.model, opts)
    }

    delete(item?: T, opts: {}  = {}) {
        if(!this.api.delete) {
            throw '[Crud][Api] "delete" route is undefined'
        }

        return this.http.delete(this.parseApi(this.api.delete, item), opts)
            .then((response: HttpResponse) => {
                if(this.model instanceof Array){
                    this.model.splice(this.model.indexOf(item), 1)
                }
                return response
            })
    }


}