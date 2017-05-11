import http from 'axios'

import { AbstractCollection } from './abstract.collection'
import { CrudApi } from './abstract.crud'
import { Mix, Mixable, mixCasts, mixCast } from '../minicast'
import { Http } from '../http.interface'

export class Collection<T> extends AbstractCollection<T>{

    protected http: Http = http as any

    constructor(api: CrudApi, initialCast?: mixCast | Function, childrenCasts?: mixCasts){
        super(api, initialCast, childrenCasts)
    }

}